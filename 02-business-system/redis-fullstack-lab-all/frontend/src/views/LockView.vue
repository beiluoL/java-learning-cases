<script setup lang="ts">
import { ref } from 'vue'
import { lockDeduct, lockReset } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const impl = ref<'redis' | 'redisson'>('redis')
const concurrentCount = ref(100)
const result = ref<any>(null)
const stateRef = ref()

async function concurrent() {
  // 先重置库存为 100，并清空统计
  await lockReset(1, 100)
  stateRef.value?.refresh()
  const N = concurrentCount.value
  const reqs: Promise<any>[] = []
  for (let i = 0; i < N; i++) {
    reqs.push(lockDeduct(1, impl.value).then((r) => r.data).catch(() => null))
  }
  const results = await Promise.all(reqs)
  const last = results.filter(Boolean).pop()
  result.value = last
  ElMessage.success(`${N} 并发扣减完成，最终剩余库存应为 ${100 - Math.min(N, 100)}，无超卖`)
  stateRef.value?.refresh()
}
async function reset() {
  await lockReset(1, 100)
  result.value = null
  ElMessage.success('库存已重置为 100')
  stateRef.value?.refresh()
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景9 + 10：分布式锁扣库存（手写锁 vs Redisson）"
      :desc="`集群下抢券/扣库存，单机锁失效，需跨进程互斥。同一套扣库存逻辑，可切换「手写 Redis 锁（SET NX EX + Lua 解锁 + 看门狗）」与「Redisson 锁（自动续期/可重入）」。并发 100 次扣减应把库存从 100 精确扣到 0，无超卖。切换 impl 看两种实现表现一致。`"
    >
      <el-form inline>
        <el-form-item label="锁实现">
          <el-radio-group v-model="impl">
            <el-radio value="redis">手写 Redis 锁</el-radio>
            <el-radio value="redisson">Redisson 锁</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="并发数">
          <el-input-number v-model="concurrentCount" :min="1" :max="500" />
        </el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item>
          <el-button type="primary" @click="concurrent">并发扣减</el-button>
          <el-button @click="reset">重置库存(100)</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="result" :title="'最终结果'" :description="JSON.stringify(result)" type="success" :closable="false" />
      <el-alert
        title="正确性判据"
        description="无论用手写锁还是 Redisson，并发 100 次后应看到 lock:deduct_ok=100，库存最终为 0（不出现负数 = 无超卖）"
        type="info"
        :closable="false"
      />
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
