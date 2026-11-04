<script setup lang="ts">
import { ref } from 'vue'
import { ratelimitOrder, ratelimitProbe, ratelimitFixed, ratelimitToken } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const times = ref(20)
const result = ref<any>(null)
const stateRef = ref()

async function hit() {
  let pass = 0
  let block = 0
  const reqs: Promise<void>[] = []
  for (let i = 0; i < times.value; i++) {
    reqs.push(
      ratelimitOrder()
        .then(() => { pass++ })
        .catch(() => { block++ })
    )
  }
  await Promise.all(reqs)
  result.value = {
    pass,
    block,
    note: `限流 QPS=5，瞬时 ${times.value} 个请求中约 5 个放行、其余被拦截（429）`
  }
  ElMessage.success('压测完成')
  stateRef.value?.refresh()
}
async function probe() {
  await ratelimitProbe()
  ElMessage.info('探测接口不限流，始终放行')
  stateRef.value?.refresh()
}
async function fixed() {
  const r = await ratelimitFixed()
  result.value = r.data
  stateRef.value?.refresh()
}
async function token() {
  const r = await ratelimitToken()
  result.value = r.data
  stateRef.value?.refresh()
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景4：限流控制（滑动窗口 / 固定窗口 / 令牌桶）"
      :desc="`防刷、保护下游，秒杀/登录接口必备。默认接口用 ZSET 滑动窗口（Lua 原子）限流 QPS=5；另提供固定窗口（INCR+EXPIRE）与令牌桶（Lua）两种实现作为对比。下方面板可瞬时并发打一批请求，观察被拦截数。`"
    >
      <el-form inline>
        <el-form-item label="并发数"><el-input-number v-model="times" :min="1" :max="200" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="hit">并发打 /order（限流）</el-button>
          <el-button @click="probe">打 /probe（不限流）</el-button>
        </el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item label="对比方案">
          <el-button @click="fixed">固定窗口 /fixed</el-button>
          <el-button @click="token">令牌桶 /token</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="result" :title="'结果'" :description="JSON.stringify(result)" type="success" :closable="false" />
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
