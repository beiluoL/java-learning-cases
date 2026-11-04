<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { debugStats, debugKeys, debugGet } from '@/api/redislab'
import { ElMessage } from 'element-plus'

// 全局观测卡片：实时展示后端 StatService 计数，以及 Redis 里的 key / 值，
// 让「缓存命中 / 限流拦截 / 回源次数」等效果肉眼可见。
const stats = ref<Record<string, number>>({})
const pattern = ref('*')
const keys = ref<string[]>([])
const selectedKey = ref('')
const keyValue = ref<any>(null)

async function refresh() {
  const res = await debugStats()
  stats.value = res.data || {}
}
async function listKeys() {
  const res = await debugKeys(pattern.value)
  keys.value = res.data || []
}
async function viewKey(k: string) {
  selectedKey.value = k
  const res = await debugGet(k)
  keyValue.value = res.data
}

onMounted(() => {
  refresh()
  listKeys()
})

defineExpose({ refresh })
</script>

<template>
  <el-card class="state" shadow="never">
    <template #header>
      <div class="head">
        <span>Redis 实时观测</span>
        <el-button size="small" @click="refresh">刷新计数</el-button>
      </div>
    </template>

    <el-table :data="Object.entries(stats)" size="small" max-height="220" border>
      <el-table-column prop="0" label="指标" />
      <el-table-column prop="1" label="次数" width="90" />
    </el-table>

    <el-divider>Key 浏览器</el-divider>
    <div class="row">
      <el-input v-model="pattern" size="small" placeholder="key pattern，如 cache:* " style="width: 220px" />
      <el-button size="small" type="primary" @click="listKeys">列出 keys</el-button>
    </div>
    <div class="keys">
      <el-tag
        v-for="k in keys"
        :key="k"
        class="key-tag"
        :type="selectedKey === k ? 'primary' : 'info'"
        @click="viewKey(k)"
      >{{ k }}</el-tag>
      <span v-if="keys.length === 0" class="muted">暂无 key（先执行上面的演示操作）</span>
    </div>
    <pre v-if="keyValue" class="kv">{{ JSON.stringify(keyValue, null, 2) }}</pre>
  </el-card>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; }
.row { display: flex; gap: 8px; margin-bottom: 8px; }
.keys { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0; max-height: 160px; overflow: auto; }
.key-tag { cursor: pointer; }
.kv { background: #f6f8fa; padding: 8px; border-radius: 4px; font-size: 12px; max-height: 240px; overflow: auto; }
.muted { color: #999; font-size: 12px; }
</style>
