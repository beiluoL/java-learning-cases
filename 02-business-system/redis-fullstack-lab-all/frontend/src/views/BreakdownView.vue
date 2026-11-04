<script setup lang="ts">
import { ref } from 'vue'
import { breakdownMutex, breakdownLogic, breakdownExpireMutex, breakdownExpireLogic } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const stateRef = ref()
const mutexResult = ref<any>(null)
const logicResult = ref<any>(null)

async function readMutex() {
  const r = await breakdownMutex(1)
  mutexResult.value = r.data
  stateRef.value?.refresh()
}
async function expireMutex() {
  await breakdownExpireMutex(1)
  ElMessage.warning('热点 key 已「过期」，再次并发读取将触发重建')
  stateRef.value?.refresh()
}
// 并发触发：先让热点 key 过期，再瞬发 20 个请求 —— 理想情况下只有 1 个线程回源重建
async function concurrentMutex() {
  await breakdownExpireMutex(1)
  const N = 20
  const reqs: Promise<void>[] = []
  for (let i = 0; i < N; i++) {
    reqs.push(breakdownMutex(1).then(() => {}).catch(() => {}))
  }
  await Promise.all(reqs)
  ElMessage.success(`已并发 ${N} 次，观察 breakdown:mutex:rebuild ≈ 1（仅一个线程回源）`)
  stateRef.value?.refresh()
}

async function readLogic() {
  const r = await breakdownLogic(1)
  logicResult.value = r.data
  stateRef.value?.refresh()
}
async function expireLogic() {
  await breakdownExpireLogic(1)
  ElMessage.warning('逻辑过期时间已置为失效，下次读取会异步重建并返回旧值（非阻塞）')
  stateRef.value?.refresh()
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景7：缓存击穿（热点 Key 重建）"
      :desc="`击穿 ≠ 穿透：是「某个爆款热点 key 过期瞬间」，海量并发一起回源 DB，把 DB 打挂。核心：只允许一个线程去重建缓存。方案A 互斥锁（其余自旋等待）；方案B 逻辑过期（异步重建、返回旧值，非阻塞）。点「并发触发」可看到 rebuild 仅 1 次。`"
    >
      <el-divider>方案 A：互斥锁重建</el-divider>
      <el-button type="primary" @click="readMutex">读取热点商品</el-button>
      <el-button @click="expireMutex">让热点 key 过期</el-button>
      <el-button type="warning" @click="concurrentMutex">并发触发重建（20 并发）</el-button>
      <el-alert v-if="mutexResult" :title="'读取结果'" :description="JSON.stringify(mutexResult)" type="success" :closable="false" />

      <el-divider>方案 B：逻辑过期（非阻塞）</el-divider>
      <el-button type="primary" @click="readLogic">读取热点商品</el-button>
      <el-button @click="expireLogic">让逻辑过期失效</el-button>
      <el-alert v-if="logicResult" :title="'读取结果'" :description="JSON.stringify(logicResult)" type="success" :closable="false" />
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
