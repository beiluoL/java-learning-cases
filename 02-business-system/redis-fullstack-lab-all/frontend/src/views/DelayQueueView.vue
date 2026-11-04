<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  delayCreate, delayPay, delayCancel, delayStatus, delayOrders, delayReset
} from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const userId = ref('u1')
const delaySeconds = ref(10)
const status = ref<Record<string, any>>({})
const orders = ref<any[]>([])
const now = ref(Date.now())
const last = ref<any>(null)
const stateRef = ref()
let timer: any = null

/** H2 / MySQL 返回的列名大小写可能不同，统一按多候选 + 忽略大小写取值 */
function pick(row: any, ...keys: string[]): any {
  for (const k of keys) {
    if (row[k] !== undefined) return row[k]
  }
  const lower = keys.map((k) => k.toLowerCase())
  for (const rk of Object.keys(row)) {
    if (lower.includes(rk.toLowerCase())) return row[rk]
  }
  return undefined
}

function fmt(ts: any) {
  if (ts == null) return '-'
  return new Date(Number(ts)).toLocaleTimeString('zh-CN', { hour12: false })
}

function remainText(row: any) {
  const state = String(pick(row, 'state', 'STATE'))
  if (state !== 'WAIT_PAY') return '-'
  const expireAt = Number(pick(row, 'expire_at', 'EXPIRE_AT'))
  const left = Math.max(0, Math.ceil((expireAt - now.value) / 1000))
  return left > 0 ? `${left}s 后自动关单` : '即将关单'
}

function tagType(state: any): string {
  switch (String(state)) {
    case 'WAIT_PAY': return 'warning'
    case 'PAID': return 'success'
    case 'CLOSED': return 'info'
    case 'CANCELLED': return 'info'
    case 'DEAD': return 'danger'
    default: return 'info'
  }
}

async function refresh() {
  try {
    const [s, o] = await Promise.all([delayStatus(), delayOrders()])
    status.value = s.data || {}
    orders.value = o.data || []
    now.value = Date.now()
    stateRef.value?.refresh()
  } catch (e) { /* 后端未启动时静默 */ }
}

async function create() {
  const r = await delayCreate(userId.value, delaySeconds.value)
  last.value = r.data
  ElMessage.success('订单已创建，倒计时开始')
  await refresh()
}

async function pay(row: any) {
  const r = await delayPay(String(pick(row, 'id', 'ID')))
  last.value = r.data
  if (r.data?.state === 'PAID') ElMessage.success('支付成功，不会被自动关单')
  else ElMessage.warning(r.data?.msg || '支付失败')
  await refresh()
}

async function cancel(row: any) {
  await delayCancel(String(pick(row, 'id', 'ID')))
  await refresh()
}

async function reset() {
  await delayReset()
  last.value = null
  ElMessage.success('演示数据已清空')
  await refresh()
}

onMounted(() => {
  refresh()
  // 1s 轮询：可实时看到「到期 → 自动关单」的状态跃迁
  timer = setInterval(refresh, 1000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="wrap">
    <div class="left">
      <DemoPanel
        title="场景14：延迟队列（订单超时自动关闭）"
        :desc="`补齐高并发里的「异步 / 延迟任务」维度：下单后 N 秒未支付自动关单并回补库存。用 Redis ZSet（score=到期时间戳）做调度，后台每 500ms 扫描一次。\n并发正确性：取出必须原子——用一段 Lua 一次完成「查到期订单 + 全部移除」，多消费者实例也不会重复关单。可靠性：关单失败自动重试 3 次，仍失败进死信 List，消息不静默丢失。\n演示：创建订单 → 盯着倒计时看它自动变 CLOSED；或提前点「支付」，观察它不会被关单。`"
      >
        <el-form inline>
          <el-form-item label="用户"><el-input v-model="userId" style="width: 120px" /></el-form-item>
          <el-form-item label="超时秒数"><el-input-number v-model="delaySeconds" :min="1" :max="600" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="create">创建待支付订单</el-button>
            <el-button @click="reset">清空演示</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="last"
          title="最近操作"
          :description="JSON.stringify(last)"
          type="info"
          :closable="false"
          class="blk"
        />

        <el-divider>订单状态（1s 自动刷新，观察超时自动关单）</el-divider>
        <el-table :data="orders" size="small" border max-height="280">
          <el-table-column label="订单号" width="205">
            <template #default="{ row }">{{ pick(row, 'id', 'ID') }}</template>
          </el-table-column>
          <el-table-column label="用户" width="90">
            <template #default="{ row }">{{ pick(row, 'user_id', 'USER_ID') }}</template>
          </el-table-column>
          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <el-tag :type="tagType(pick(row, 'state', 'STATE'))" size="small">
                {{ pick(row, 'state', 'STATE') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到期时间" width="110">
            <template #default="{ row }">{{ fmt(pick(row, 'expire_at', 'EXPIRE_AT')) }}</template>
          </el-table-column>
          <el-table-column label="倒计时" min-width="130">
            <template #default="{ row }">{{ remainText(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="pay(row)">支付</el-button>
              <el-button size="small" @click="cancel(row)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="orders.length === 0" class="muted">暂无订单，先创建一个待支付订单</div>

        <el-divider>队列可观测性指标</el-divider>
        <el-table :data="Object.entries(status)" size="small" border max-height="240">
          <el-table-column prop="0" label="指标" />
          <el-table-column prop="1" label="值" width="130" />
        </el-table>
        <div class="tip">
          关注 <code>pending</code>（队列堆积）与 <code>avgCloseDelayMs</code>（消费延迟）；
          关单失败会进入 <code>retry</code> / <code>deadLetter</code>。
        </div>
      </DemoPanel>
    </div>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
.left { flex: 1 1 700px; min-width: 460px; }
.blk { margin-bottom: 12px; word-break: break-all; }
.muted { color: #999; font-size: 12px; margin-top: 6px; }
.tip { font-size: 12px; color: #666; margin-top: 10px; line-height: 1.7; }
code { background: #f0f0f0; padding: 1px 5px; border-radius: 4px; }
</style>
