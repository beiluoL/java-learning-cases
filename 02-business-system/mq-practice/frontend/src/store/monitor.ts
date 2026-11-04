import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchOverview } from '@/api/monitor'
import type { MqOverview, QueueInfoView } from '@/types'

/**
 * MQ 总览状态：多个页面都要读队列深度，集中到 store 避免各页面重复请求。
 */
export const useMonitorStore = defineStore('monitor', () => {
  const overview = ref<MqOverview | null>(null)
  const loading = ref(false)
  const lastUpdated = ref<string>('')

  const queues = computed<QueueInfoView[]>(() => overview.value?.queues ?? [])
  const dlqCount = computed(() => overview.value?.dlqCount ?? 0)
  const brokerAvailable = computed(() => overview.value?.brokerAvailable ?? false)

  /** 只读队列深度：总览页与监控页以不同频率调用 */
  async function loadOverview(silent = false) {
    if (!silent) loading.value = true
    try {
      overview.value = await fetchOverview()
      lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    } finally {
      loading.value = false
    }
  }

  function queueByName(name: string): QueueInfoView | undefined {
    return queues.value.find((q) => q.name === name)
  }

  return {
    overview,
    loading,
    lastUpdated,
    queues,
    dlqCount,
    brokerAvailable,
    loadOverview,
    queueByName
  }
})
