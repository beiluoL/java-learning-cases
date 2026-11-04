<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  aiquotaConfig, aiquotaConsume, aiquotaStress, aiquotaStatus, aiquotaReset
} from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const userId = ref('u1')
const rpm = ref(10)
const tpm = ref(1000)
const tokens = ref(120)
const times = ref(20)
const loading = ref(false)
const status = ref<Record<string, any>>({})
const last = ref<any>(null)
const stateRef = ref()
let timer: any = null

async function refresh() {
  try {
    const r = await aiquotaStatus(userId.value)
    status.value = r.data || {}
    stateRef.value?.refresh()
  } catch (e) { /* 后端未启动时静默 */ }
}

async function saveConfig() {
  await aiquotaConfig(userId.value, rpm.value, tpm.value)
  ElMessage.success(`已配置：${rpm.value} RPM / ${tpm.value} TPM`)
  await refresh()
}

async function consumeOnce() {
  const r = await aiquotaConsume(userId.value, tokens.value)
  last.value = r.data
  if (r.data?.degraded) ElMessage.warning('额度超限：已触发降级策略')
  await refresh()
}

async function stress() {
  loading.value = true
  const r = await aiquotaStress(userId.value, tokens.value, times.value)
  last.value = r.data
  loading.value = false
  ElMessage.success('突发流量模拟完成')
  await refresh()
}

async function reset() {
  await aiquotaReset(userId.value)
  last.value = null
  ElMessage.success('该用户额度窗口已清空')
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
        title="场景13：AI 额度管理（Lua 单 ZSET 双滑动窗口 RPM / TPM）"
        :desc="`大模型网关要同时限制 RPM（每分钟请求数）与 TPM（每分钟 token 数）。若拆成两个计数器分别判断，并发下会出现「A 过了 RPM、B 过了 TPM」的不一致。本项目用一段 Lua 在 Redis 单线程内原子完成「清窗口→统计 RPM/TPM→双维度校验→写入」。\n超额不是简单报错，而是返回降级信号（RPM 超限→排队/429；TPM 超限→降级小模型/截断上下文）。窗口 60s，过期自动清零。`"
      >
        <el-form inline>
          <el-form-item label="用户"><el-input v-model="userId" style="width: 120px" /></el-form-item>
          <el-form-item label="RPM"><el-input-number v-model="rpm" :min="1" :max="10000" /></el-form-item>
          <el-form-item label="TPM"><el-input-number v-model="tpm" :min="1" :max="10000000" /></el-form-item>
          <el-form-item><el-button type="primary" @click="saveConfig">保存额度配置</el-button></el-form-item>
        </el-form>
        <el-form inline>
          <el-form-item label="单次 tokens"><el-input-number v-model="tokens" :min="1" :max="100000" /></el-form-item>
          <el-form-item label="突发次数"><el-input-number v-model="times" :min="1" :max="200" /></el-form-item>
          <el-form-item>
            <el-button type="success" @click="consumeOnce">单次调用</el-button>
            <el-button type="danger" :loading="loading" @click="stress">突发流量模拟</el-button>
            <el-button @click="reset">清空窗口</el-button>
          </el-form-item>
        </el-form>

        <el-divider>当前 60s 窗口额度水位</el-divider>
        <div class="quota">
          <span class="lbl">RPM</span>
          <el-progress
            :percentage="Math.min(100, Number(status.rpmPercent || 0))"
            :status="Number(status.rpmPercent || 0) >= 90 ? 'exception' : undefined"
          />
          <span class="val">{{ status.rpmUsed || 0 }} / {{ status.rpmLimit || 0 }}</span>
        </div>
        <div class="quota">
          <span class="lbl">TPM</span>
          <el-progress
            :percentage="Math.min(100, Number(status.tpmPercent || 0))"
            :status="Number(status.tpmPercent || 0) >= 90 ? 'exception' : undefined"
          />
          <span class="val">{{ status.tpmUsed || 0 }} / {{ status.tpmLimit || 0 }}</span>
        </div>

        <el-alert
          v-if="last"
          :title="last.degraded ? '触发超额降级' : '调用结果'"
          :description="JSON.stringify(last)"
          :type="last.degraded ? 'warning' : 'success'"
          :closable="false"
          class="blk"
        />

        <el-divider>额度观测指标（3s 自动刷新）</el-divider>
        <el-table :data="Object.entries(status)" size="small" border max-height="260">
          <el-table-column prop="0" label="指标" />
          <el-table-column prop="1" label="值" width="140" />
        </el-table>
      </DemoPanel>
    </div>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
.left { flex: 1 1 620px; min-width: 420px; }
.quota { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.quota .lbl { width: 44px; font-weight: 700; }
.quota :deep(.el-progress) { flex: 1; }
.quota .val { width: 130px; text-align: right; font-size: 13px; color: #555; }
.blk { margin-top: 14px; word-break: break-all; }
</style>
