<template>
  <div>
    <div class="panel" style="margin-bottom: 16px">
      <div class="panel-body toolbar">
        <div class="filters">
          <el-select v-model="query.status" placeholder="全部状态" clearable size="default" style="width: 132px">
            <el-option label="排队中" value="QUEUING" />
            <el-option label="待支付" value="UNPAID" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="创建失败" value="FAILED" />
          </el-select>
          <el-input
            v-model="query.keyword"
            placeholder="订单号 / 商品名"
            clearable
            style="width: 220px"
            @keyup.enter="reload"
          />
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="store.load()">
            <el-icon><Refresh /></el-icon>
            <span style="margin-left: 4px">刷新</span>
          </el-button>
        </div>

        <div class="toolbar-right">
          <span class="panel-hint">自动刷新</span>
          <el-switch v-model="autoRefresh" size="small" />
          <div class="divider"></div>
          <el-select v-model="demoProductId" size="small" style="width: 168px">
            <el-option
              v-for="p in products"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
          <el-select v-model="demoDelay" size="small" style="width: 120px">
            <el-option label="10 秒后取消" :value="10" />
            <el-option label="30 秒后取消" :value="30" />
            <el-option label="60 秒后取消" :value="60" />
          </el-select>
          <el-button type="primary" size="small" :loading="creating" @click="createDemo">
            创建演示订单
          </el-button>
        </div>
      </div>
    </div>

    <div class="hint-strip">
      <div class="hint-item">
        <span class="tag amber">延迟消息</span>
        下单成功后投递一条 <code class="mono">TTL = delaySeconds</code> 的消息到
        <code class="mono">mq.order.delay.queue</code>，该队列无消费者，到期后自动死信到取消交换机。
      </div>
      <div class="hint-item">
        <span class="tag teal">状态互斥</span>
        取消前用 <code class="mono">UPDATE ... WHERE status='UNPAID'</code> 做 CAS；
        若用户已支付，取消直接跳过，绝不会误关已付款订单。
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title"><span class="dot"></span>订单列表</div>
        <span class="panel-hint">共 {{ total }} 条 · 每 3 秒自动校准倒计时</span>
      </div>
      <div class="panel-body" style="padding: 0">
        <el-table :data="records" v-loading="loading" style="width: 100%" empty-text="暂无订单">
          <el-table-column label="订单号" min-width="210">
            <template #default="{ row }">
              <div class="mono order-no" @click="copy(row.orderNo)">{{ row.orderNo }}</div>
              <div class="sub mono">uid {{ row.userId }}</div>
            </template>
          </el-table-column>

          <el-table-column label="商品" min-width="180">
            <template #default="{ row }">
              <div>{{ row.productName }}</div>
              <div class="sub">× {{ row.quantity }}</div>
            </template>
          </el-table-column>

          <el-table-column label="金额" width="112" align="right">
            <template #default="{ row }">
              <span class="num amount">¥{{ row.amount.toFixed(2) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="112">
            <template #default="{ row }">
              <OrderStatusTag :status="row.status" :text="row.statusText" />
            </template>
          </el-table-column>

          <el-table-column label="支付截止" width="130">
            <template #default="{ row }">
              <CountdownText
                :seconds="row.remainingSeconds"
                :active="row.status === 'UNPAID'"
                @done="store.load(true)"
              />
              <div v-if="row.status === 'UNPAID'" class="sub">{{ row.expireTime }}</div>
              <div v-else-if="row.cancelReason" class="sub">{{ row.cancelReason }}</div>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">
              <span class="sub mono">{{ row.createTime }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'UNPAID'">
                <el-button link type="primary" size="small" @click="handlePay(row)">支付</el-button>
                <el-button link type="danger" size="small" @click="handleCancel(row)">取消</el-button>
              </template>
              <span v-else class="sub">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pager">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="query.size"
          :current-page="query.page"
          @current-change="store.changePage"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import OrderStatusTag from '@/components/OrderStatusTag.vue'
import CountdownText from '@/components/CountdownText.vue'
import { useOrderStore } from '@/store/order'
import { fetchProducts } from '@/api/product'
import type { OrderView, ProductView } from '@/types'

const store = useOrderStore()
const { records, total, loading } = storeToRefs(store)
// 直接取 reactive 对象（不要用 storeToRefs，否则模板里要写 query.value.page）
const query = store.query

const autoRefresh = ref(true)
const products = ref<ProductView[]>([])
const demoProductId = ref<number>(2)
const demoDelay = ref(10)
const creating = ref(false)

let timer: number | undefined

const reload = () => {
  query.page = 1
  store.load()
}

function copy(text: string) {
  navigator.clipboard
    ?.writeText(text)
    .then(() => ElMessage.success('订单号已复制'))
    .catch(() => undefined)
}

async function handlePay(row: OrderView) {
  try {
    await store.pay(row.orderNo)
    ElMessage.success('支付成功；到点的取消消息会因状态不符而自动跳过')
  } catch {
    /* 拦截器已提示 */
  }
}

async function handleCancel(row: OrderView) {
  try {
    await ElMessageBox.confirm(
      `取消后库存会立即回补（含 Redis 预扣），确认取消订单 ${row.orderNo}？`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  try {
    await store.cancel(row.orderNo)
    ElMessage.success('已取消并回补库存')
  } catch {
    /* ignore */
  }
}

async function createDemo() {
  creating.value = true
  try {
    const response = await store.createDemo({
      productId: demoProductId.value,
      quantity: 1,
      delaySeconds: demoDelay.value
    })
    ElMessage.success(
      `已创建 ${response.orderNo}，${demoDelay.value} 秒后自动取消（可先点"支付"验证互斥）`
    )
  } catch {
    /* ignore */
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  await Promise.all([store.load(), fetchProducts().then((list) => (products.value = list))])
  if (products.value.length > 0 && !products.value.find((p) => p.id === demoProductId.value)) {
    demoProductId.value = products.value[0].id
  }
  timer = window.setInterval(() => {
    if (autoRefresh.value) store.load(true)
  }, 3000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 18px;
}

.filters,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.divider {
  width: 1px;
  height: 20px;
  background: var(--border);
  margin: 0 4px;
}

.hint-strip {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.hint-item {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 11px 14px;
  font-size: 11.5px;
  line-height: 1.75;
  color: var(--text-secondary);
}

.hint-item code {
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
  font-size: 10.5px;
}

.order-no {
  font-size: 11.5px;
  cursor: pointer;
  color: var(--text);
}

.order-no:hover {
  color: var(--primary);
}

.sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 1px;
}

.amount {
  font-weight: 600;
  color: var(--danger);
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 18px;
  border-top: 1px solid var(--border);
}

@media (max-width: 1080px) {
  .hint-strip {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
