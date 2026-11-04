<script setup lang="ts">
import { ref } from 'vue'
import { avalanchePreheat, avalancheStress, avalancheRedisDown } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const stateRef = ref()
const times = ref(200)
const redisDown = ref(false)
const runs = ref<any[]>([])

async function preheat(protect: boolean) {
  await avalanchePreheat(protect)
  ElMessage.success(protect ? '已预热：随机 TTL（打散失效）' : '已预热：固定 TTL（同一时刻集体失效 → 雪崩）')
  stateRef.value?.refresh()
}
async function stress(protect: boolean) {
  const before = await (await import('@/api/redislab')).debugStats()
  const beforeDb = (before.data as any)?.['avalanche:db'] || 0
  await avalancheStress(times.value, protect)
  const after = await (await import('@/api/redislab')).debugStats()
  const afterDb = (after.data as any)?.['avalanche:db'] || 0
  runs.value.unshift({
    mode: protect ? '防护（多级缓存+随机TTL）' : '无防护（固定TTL）',
    times: times.value,
    dbHits: afterDb - beforeDb
  })
  ElMessage.success('压测完成，查看下方对比')
  stateRef.value?.refresh()
}
async function toggleDown() {
  redisDown.value = !redisDown.value
  await avalancheRedisDown(redisDown.value)
  ElMessage.warning(redisDown.value ? '已模拟 Redis 宕机：防护模式会熔断降级返回兜底数据' : '已恢复 Redis')
  stateRef.value?.refresh()
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景8：缓存雪崩（集中失效 / Redis 宕机）"
      :desc="`大量 key 同一时刻集体失效，或 Redis 宕机，瞬时请求全压到 DB。防护组合拳：① TTL 随机抖动打散失效；② 本地 Caffeine + Redis 多级缓存扛洪峰；③ Resilience4j 熔断，Redis 不可用时降级返回兜底。分别跑「无防护」与「防护」压力测试，对比 DB 被打中的次数。`"
    >
      <el-form inline>
        <el-form-item label="压测请求数"><el-input-number v-model="times" :min="10" :max="2000" /></el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item label="预热">
          <el-button @click="preheat(true)">预热(防护/随机TTL)</el-button>
          <el-button @click="preheat(false)">预热(无防护/固定TTL)</el-button>
        </el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item label="压测">
          <el-button type="danger" @click="stress(false)">压力测试(无防护)</el-button>
          <el-button type="primary" @click="stress(true)">压力测试(防护)</el-button>
        </el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item>
          <el-button @click="toggleDown">切换 Redis 宕机模拟（{{ redisDown ? '已宕机' : '正常' }}）</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="runs" border size="small" v-if="runs.length">
        <el-table-column prop="mode" label="模式" />
        <el-table-column prop="times" label="请求数" width="90" />
        <el-table-column prop="dbHits" label="打到DB次数" width="120" />
      </el-table>
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
