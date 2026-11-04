<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  seckillInit, seckillFlash, seckillStatus, seckillOrders
} from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const stock = ref(100)
const concurrency = ref(100)
const loading = ref(false)
const status = ref<Record<string, any>>({})
const orders = ref<any[]>([])
const summary = ref<any>(null)
const stateRef = ref()
// H2 返回的列名大小写随环境不同，用动态列保证订单表一定有内容
const orderColumns = computed(() => (orders.value.length ? Object.keys(orders.value[0]) : []))
let timer: any = null

async function refresh() {
  try {
    const [s, o] = await Promise.all([seckillStatus(1), seckillOrders(1)])
    status.value = s.data || {}
    orders.value = o.data || []
    stateRef.value?.refresh()
  } catch (e) {
    // 后端未启动时静默，避免定时刷新刷出大量错误提示
  }
}

async function initStock() {
  await seckillInit(1, stock.value)
  ElMessage.success(`库存已预热为 ${stock.value}`)
  summary.value = null
  await refresh()
}

/** 前端真实并发：一次性打出 N 个抢购请求，验证 Lua 原子扣减不超卖 */
async function stress() {
  loading.value = true
  const tally: Record<string, number> = {}
  const reqs: Promise<void>[] = []
  for (let i = 0; i < concurrency.value; i++) {
    reqs.push(
      seckillFlash(1, 'sec_u_' + i)
        .then((r) => {
          const k = r.data?.result || 'ERROR'
          tally[k] = (tally[k] || 0) + 1
        })
        .catch(() => { tally['ERROR'] = (tally['ERROR'] || 0) + 1 })
    )
  }
  await Promise.all(reqs)
  summary.value = {
    total: concurrency.value,
    ...tally,
    note: '每个用户不同，成功数应恰好等于预热库存；其余全部被售罄快速失败拦截，无超卖'
  }
  loading.value = false
  ElMessage.success('秒杀压测完成')
  await refresh()
}

onMounted(() => {
  refresh()
  timer = setInterval(refresh, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="wrap">
    <div class="left">
      <DemoPanel
        title="场景11：秒杀库存（Lua 原子扣减 + 异步削峰 + DB 兜底）"
        :desc="`四层设计：① 用户限购 Set（一人一件）② 整段「校验→扣减→记录→入队」由 Lua 在 Redis 单线程内原子完成，杜绝超卖 ③ 成功请求先 LPUSH 进 Redis List，后台单线程慢慢落 MySQL，秒杀瞬间不打 DB ④ seckill_order(product_id,user_id) 唯一约束兜底。\n点击下方「并发抢购」会真实打出 N 个并发请求，观察成功数恰好等于库存、其余被售罄快速失败拦截。`"
      >
        <el-form inline>
          <el-form-item label="预热库存">
            <el-input-number v-model="stock" :min="1" :max="1000" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="initStock">库存预热 / 重置</el-button>
          </el-form-item>
        </el-form>
        <el-form inline>
          <el-form-item label="并发抢购数">
            <el-input-number v-model="concurrency" :min="1" :max="500" />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" :loading="loading" @click="stress">并发抢购（压力测试）</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="summary"
          title="压测结果"
          :description="JSON.stringify(summary)"
          type="success"
          :closable="false"
          class="blk"
        />

        <el-divider>实时指标（3s 自动刷新）</el-divider>
        <el-table :data="Object.entries(status)" size="small" border max-height="260">
          <el-table-column prop="0" label="指标" />
          <el-table-column prop="1" label="值" width="140" />
        </el-table>

        <el-divider>已异步落库订单（验证削峰最终一致）</el-divider>
        <el-table :data="orders" size="small" border max-height="240">
          <el-table-column v-for="c in orderColumns" :key="c" :prop="c" :label="c" />
        </el-table>
        <div v-if="orders.length === 0" class="muted">暂无订单（先预热库存并并发抢购）</div>
      </DemoPanel>
    </div>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
.left { flex: 1 1 620px; min-width: 420px; }
.blk { margin-bottom: 12px; }
.muted { color: #999; font-size: 12px; margin-top: 6px; }
</style>
