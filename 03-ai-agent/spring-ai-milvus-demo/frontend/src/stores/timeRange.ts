import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { TimeRange } from '@/api/types'
import { describeRange, resolveRange, type RangePreset } from '@/utils/time'

/**
 * 全局时间筛选：统计类页面共享同一范围，切换路由后保持一致。
 * revision 在每次筛选变更时递增，页面 watch 它即可触发所有接口重新取数。
 */
export const useTimeRangeStore = defineStore('timeRange', () => {
  const preset = ref<RangePreset>('last7')
  const customStart = ref('')
  const customEnd = ref('')
  const revision = ref(0)

  const range = computed<TimeRange>(() =>
    resolveRange(preset.value, customStart.value, customEnd.value),
  )
  const label = computed(() => describeRange(range.value))

  function setPreset(next: RangePreset) {
    preset.value = next
    revision.value += 1
  }

  function setCustom(start: string, end: string) {
    customStart.value = start
    customEnd.value = end
    preset.value = 'custom'
    revision.value += 1
  }

  return { preset, customStart, customEnd, revision, range, label, setPreset, setCustom }
})
