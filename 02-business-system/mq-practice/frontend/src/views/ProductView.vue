<template>
  <div>
    <div class="flow-strip">
      <div class="flow-cell">
        <div class="flow-label">发布方</div>
        <div class="flow-value">ProductService</div>
      </div>
      <div class="flow-arrow">─▶</div>
      <div class="flow-cell">
        <div class="flow-label">交换机</div>
        <div class="flow-value mono">mq.product.exchange (topic)</div>
      </div>
      <div class="flow-arrow">─▶</div>
      <div class="flow-cell split">
        <div class="branch">
          <div class="flow-value mono">mq.product.cache.queue</div>
          <div class="flow-label">CacheSyncConsumer → Redis</div>
        </div>
        <div class="branch">
          <div class="flow-value mono">mq.product.search.queue</div>
          <div class="flow-label">SearchIndexConsumer → 索引</div>
        </div>
      </div>
      <div class="flow-note">
        两个队列各自独立消费：缓存刷新快、索引写入慢（模拟 120ms），
        下面「一致性窗口」就是这段时差的具象化。
      </div>
    </div>

    <div v-if="lastChange" class="event-banner">
      <span class="tag violet">事件已发布</span>
      <span class="event-text">{{ lastChange.changeType }} · version {{ lastChange.version }}</span>
      <span class="event-id mono">messageId {{ lastChange.messageId }}</span>
      <span class="event-hint">{{ lastChange.message }}</span>
    </div>

    <div class="panel" style="margin-bottom: 16px">
      <div class="panel-head">
        <div class="panel-title"><span class="dot"></span>商品列表</div>
        <span class="panel-hint">点击行查看该商品的三视角数据 · 每 4 秒自动刷新快照</span>
      </div>
      <div class="panel-body" style="padding: 0">
        <el-table
          :data="products"
          v-loading="loading"
          highlight-current-row
          :current-row-key="selectedId ?? undefined"
          row-key="id"
          empty-text="暂无商品"
          @current-change="onRowChange"
        >
          <el-table-column label="商品" min-width="230">
            <template #default="{ row }">
              <div class="name">{{ row.name }}</div>
              <div class="sub mono">{{ row.productNo }}</div>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="112" align="right">
            <template #default="{ row }">
              <span class="num price">¥{{ row.price.toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="104">
            <template #default="{ row }">
              <span class="tag" :class="row.status === 1 ? 'teal' : ''">{{ row.statusText }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数据版本" width="104">
            <template #default="{ row }"><span class="mono">v{{ row.version }}</span></template>
          </el-table-column>
          <el-table-column label="库存" width="96" align="right">
            <template #default="{ row }"><span class="num">{{ row.availableStock }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="196" align="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openPriceDialog(row)">改价</el-button>
              <el-button
                v-if="row.status !== 1"
                link
                type="success"
                size="small"
                @click.stop="change(row, 'ONLINE')"
              >
                上架
              </el-button>
              <el-button v-else link type="warning" size="small" @click.stop="change(row, 'OFFLINE')">
                下架
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <span class="dot"></span>三视角快照
          <span v-if="snapshot" class="mono title-id">#{{ snapshot.id }} {{ snapshot.name }}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 10px">
          <span class="tag" :class="consistent ? 'teal' : 'amber'">
            {{ consistent ? '三方一致' : '最终一致性窗口' }}
          </span>
          <el-button size="small" text :loading="snapshotLoading" @click="store.refreshSnapshot()">
            刷新
          </el-button>
        </div>
      </div>
      <div class="panel-body">
        <div v-if="!snapshot" class="empty-state">在上方选择商品查看数据分布</div>
        <template v-else>
          <div class="tri-grid">
            <div class="tri-card">
              <div class="tri-head">
                <span class="tag blue">MySQL</span>
                <span class="tri-sub">事实来源</span>
              </div>
              <div class="kv"><span class="k">name</span><span class="v">{{ snapshot.name }}</span></div>
              <div class="kv"><span class="k">price</span><span class="v">¥{{ snapshot.price.toFixed(2) }}</span></div>
              <div class="kv"><span class="k">status</span><span class="v">{{ snapshot.statusText }}</span></div>
              <div class="kv"><span class="k">version</span><span class="v">{{ snapshot.version }}</span></div>
            </div>

            <div class="tri-card">
              <div class="tri-head">
                <span class="tag teal">Redis 缓存</span>
                <span class="tri-sub">TTL {{ ttlText }}</span>
              </div>
              <div v-if="snapshot.cacheJson" class="code-block" style="max-height: 190px">{{ pretty(snapshot.cacheJson) }}</div>
              <div v-else class="tri-empty">缓存为空（尚未消费，或商品已下架被主动删除）</div>
            </div>
          </div>

          <div class="tri-card" style="margin-top: 12px">
            <div class="tri-head">
              <span class="tag violet">检索索引（模拟 ES）</span>
              <span class="tri-sub">doc_version 用于丢弃乱序旧文档</span>
            </div>
            <div v-if="snapshot.indexJson" class="code-block" style="max-height: 230px">{{ pretty(snapshot.indexJson) }}</div>
            <div v-else class="tri-empty">索引文档不存在</div>
          </div>

          <div class="consistency">
            <span class="tag" :class="consistent ? 'teal' : 'amber'">
              {{ consistent ? '已收敛' : '收敛中' }}
            </span>
            <span class="consistency-text">{{ consistencyText }}</span>
          </div>
        </template>
      </div>
    </div>

    <el-dialog v-model="priceDialogVisible" title="修改价格并发布事件" width="420px">
      <div v-if="editing" class="dialog-body">
        <div class="dialog-product">{{ editing.name }}</div>
        <div class="sub" style="margin-bottom: 14px">当前价格 ¥{{ editing.price.toFixed(2) }}</div>
        <el-input-number v-model="newPrice" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        <div class="dialog-hint">
          提交后会同时：① 更新 MySQL 并把 version+1；② 向 topic 交换机发布事件。
          两个消费者随后各自更新 Redis 与索引。
        </div>
      </div>
      <template #footer>
        <el-button @click="priceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changing" @click="submitPrice">确认改价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useProductStore } from '@/store/product'
import type { ProductChangeType, ProductView } from '@/types'

const store = useProductStore()
const { products, loading, selectedId, snapshot, snapshotLoading, lastChange } = storeToRefs(store)

const priceDialogVisible = ref(false)
const editing = ref<ProductView | null>(null)
const newPrice = ref(0)
const changing = ref(false)

const cacheVersion = computed(() => {
  if (!snapshot.value?.cacheJson) return null
  try {
    return JSON.parse(snapshot.value.cacheJson).version as number
  } catch {
    return null
  }
})

const indexVersion = computed(() => {
  if (!snapshot.value?.indexJson) return null
  try {
    return JSON.parse(snapshot.value.indexJson).docVersion as number
  } catch {
    return null
  }
})

const ttlText = computed(() => {
  const ttl = snapshot.value?.cacheTtlSeconds
  if (ttl === null || ttl === undefined || ttl < 0) return '无'
  return ttl > 60 ? `${Math.floor(ttl / 60)} 分钟` : `${ttl} 秒`
})

/** 三方一致性：数据库 version、缓存 version、索引 docVersion 全部对齐 */
const consistent = computed(() => {
  const s = snapshot.value
  if (!s) return true
  if (s.status === 0) {
    // 下架：缓存应被删除，索引版本应跟上
    return cacheVersion.value === null && indexVersion.value === s.version
  }
  return cacheVersion.value === s.version && indexVersion.value === s.version
})

const consistencyText = computed(() => {
  const s = snapshot.value
  if (!s) return ''
  if (consistent.value) {
    return `MySQL / Redis / 索引均为 v${s.version}，最终一致性已收敛。`
  }
  const parts = [`MySQL v${s.version}`]
  parts.push(`Redis ${cacheVersion.value === null ? '无缓存' : `v${cacheVersion.value}`}`)
  parts.push(`索引 ${indexVersion.value === null ? '无文档' : `v${indexVersion.value}`}`)
  return `${parts.join(' · ')} —— 消费者正在追赶，稍等片刻再刷新。`
})

const pretty = (json: string) => {
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function onRowChange(row: ProductView | null) {
  if (row) store.select(row.id)
}

function openPriceDialog(row: ProductView) {
  editing.value = row
  newPrice.value = row.price
  priceDialogVisible.value = true
}

async function submitPrice() {
  if (!editing.value) return
  changing.value = true
  try {
    const result = await store.change({
      changeType: 'PRICE_CHANGED',
      price: newPrice.value,
      operator: 'console'
    })
    priceDialogVisible.value = false
    ElMessage.success(`已发布事件 v${result.version}，观察下方三方数据变化`)
  } catch {
    /* ignore */
  } finally {
    changing.value = false
  }
}

async function change(row: ProductView, type: ProductChangeType) {
  try {
    await store.select(row.id)
    const result = await store.change({ changeType: type, operator: 'console' })
    ElMessage.success(`${type === 'ONLINE' ? '上架' : '下架'}完成，事件 v${result.version} 已发布`)
  } catch {
    /* ignore */
  }
}

let timer: number | undefined

onMounted(async () => {
  await store.load()
  if (selectedId.value !== null) await store.select(selectedId.value)
  // 每 4 秒静默刷新快照，直观看到"最终一致"的收敛过程
  timer = window.setInterval(() => {
    if (!snapshotLoading.value) store.refreshSnapshot()
  }, 4000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.flow-strip {
  display: flex;
  align-items: stretch;
  gap: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.flow-cell {
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  min-width: 140px;
}

.flow-cell.split {
  display: flex;
  gap: 10px;
  background: transparent;
  border: none;
  padding: 0;
}

.branch {
  background: #f7f4fe;
  border: 1px solid #e5dbf8;
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}

.flow-label {
  font-size: 10.5px;
  color: var(--text-muted);
}

.flow-value {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 2px;
}

.flow-arrow {
  color: var(--text-muted);
  display: flex;
  align-items: center;
  font-size: 11px;
}

.flow-note {
  flex: 1 1 220px;
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.7;
  border-left: 1px dashed var(--border);
  padding-left: 14px;
  display: flex;
  align-items: center;
}

.event-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  background: linear-gradient(120deg, #f5f1fe, #fbf9ff);
  border: 1px solid #e5dbf8;
  border-radius: var(--radius-lg);
  padding: 11px 15px;
  margin-bottom: 16px;
  font-size: 12px;
}

.event-text {
  font-weight: 600;
}

.event-id {
  font-size: 10.5px;
  color: var(--violet);
  word-break: break-all;
}

.event-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.name {
  font-size: 13px;
  font-weight: 600;
}

.price {
  font-size: 13px;
  color: var(--danger);
  font-weight: 600;
}

.sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 1px;
}

.title-id {
  font-weight: 400;
  color: var(--text-muted);
  font-size: 11px;
}

.tri-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 12px;
}

.tri-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 13px;
  background: var(--surface);
}

.tri-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tri-sub {
  font-size: 10.5px;
  color: var(--text-muted);
}

.tri-empty {
  font-size: 11.5px;
  color: var(--text-muted);
  padding: 24px 0;
  text-align: center;
}

.consistency {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
}

.consistency-text {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.dialog-product {
  font-size: 14px;
  font-weight: 600;
}

.dialog-hint {
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--text-muted);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .tri-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
