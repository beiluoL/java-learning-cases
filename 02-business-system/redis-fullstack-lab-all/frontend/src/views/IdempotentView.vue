<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  idempotentToken, idempotentSubmit, idempotentReplay,
  idempotentStatus, idempotentRecords, idempotentReset
} from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const token = ref('')
const bizId = ref('BIZ-1001')
const amount = ref(10)
const times = ref(20)
const last = ref<any>(null)
const status = ref<Record<string, any>>({})
const records = ref<any[]>([])
const stateRef = ref()
const recordColumns = computed(() => (records.value.length ? Object.keys(records.value[0]) : []))

async function refresh() {
  const [s, r] = await Promise.all([idempotentStatus(), idempotentRecords()])
  status.value = s.data || {}
  records.value = r.data || []
  stateRef.value?.refresh()
}

async function getToken() {
  const r = await idempotentToken()
  token.value = r.data.token
  last.value = null
  ElMessage.success('已领取一次性 token，请用它提交')
  await refresh()
}

async function submitOnce() {
  if (!token.value) return ElMessage.warning('请先领取 token')
  const r = await idempotentSubmit(token.value, bizId.value, amount.value)
  last.value = r.data
  await refresh()
}

/** 并发重复请求模拟：N 个完全相同的请求同时发起，只有 1 次生效 */
async function replay() {
  if (!token.value) return ElMessage.warning('请先领取 token')
  const r = await idempotentReplay(token.value, bizId.value, amount.value, times.value)
  last.value = r.data
  ElMessage.success('并发重复请求模拟完成')
  await refresh()
}

/** 换新 token 但保持同一 bizId：验证 MySQL 唯一索引这道 Redis 覆盖不到的防线 */
async function newTokenSameBiz() {
  const r = await idempotentToken()
  const res = await idempotentSubmit(r.data.token, bizId.value, amount.value)
  last.value = {
    ...res.data,
    note: '已换新 token 但 bizId 不变 → 观察 DUPLICATE_DB（Redis token 挡不住，靠 MySQL 唯一索引兜底）'
  }
  await refresh()
}

async function reset() {
  await idempotentReset()
  token.value = ''
  last.value = null
  ElMessage.success('演示数据已清空')
  await refresh()
}

onMounted(refresh)
</script>

<template>
  <div class="wrap">
    <div class="left">
      <DemoPanel
        title="场景12：幂等处理（Redis 一次性 Token + MySQL 唯一索引）"
        :desc="`重复提交是支付/下单接口的高频事故。三道防线：① Redis token 用 Lua 原子「存在则删除」，并发 N 次同一 token 只有 1 次生效 ② 已消费标记区分「重复提交」与「token 过期/伪造」③ MySQL idempotent_record(biz_id) 唯一索引，挡住「换了新 token 但业务单号相同」的重复。\n演示顺序：领取 token → 首次提交 → 再点一次提交（重复）→ 并发重复模拟 → 换新 token 同 bizId。`"
      >
        <el-form inline>
          <el-form-item label="业务单号"><el-input v-model="bizId" style="width: 180px" /></el-form-item>
          <el-form-item label="金额"><el-input-number v-model="amount" :min="1" :max="99999" /></el-form-item>
          <el-form-item label="并发次数"><el-input-number v-model="times" :min="1" :max="200" /></el-form-item>
        </el-form>
        <el-form inline>
          <el-form-item>
            <el-button type="primary" @click="getToken">① 领取 token</el-button>
            <el-button type="success" @click="submitOnce">② 提交业务（可多点几次）</el-button>
            <el-button type="danger" @click="replay">③ 并发重复请求模拟</el-button>
            <el-button @click="newTokenSameBiz">④ 换新 token 同 bizId</el-button>
            <el-button @click="reset">清空演示</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="token"
          title="当前 Token"
          :description="token"
          type="info"
          :closable="false"
          class="blk"
        />
        <el-alert
          v-if="last"
          title="最近一次结果"
          :description="JSON.stringify(last)"
          :type="last.result === 'FIRST' ? 'success' : 'warning'"
          :closable="false"
          class="blk"
        />

        <el-divider>幂等观测指标</el-divider>
        <el-table :data="Object.entries(status)" size="small" border max-height="220">
          <el-table-column prop="0" label="指标" />
          <el-table-column prop="1" label="值" width="120" />
        </el-table>

        <el-divider>去重表记录（MySQL 唯一索引兜底证据）</el-divider>
        <el-table :data="records" size="small" border max-height="240">
          <el-table-column v-for="c in recordColumns" :key="c" :prop="c" :label="c" />
        </el-table>
        <div v-if="records.length === 0" class="muted">暂无记录：无论提交多少次，同一 bizId 只会写入一条</div>
      </DemoPanel>
    </div>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
.left { flex: 1 1 620px; min-width: 420px; }
.blk { margin-bottom: 12px; word-break: break-all; }
.muted { color: #999; font-size: 12px; margin-top: 6px; }
</style>
