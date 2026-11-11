<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useTimeRangeStore } from '@/stores/timeRange'
import { RANGE_PRESETS, toLocalInput, type RangePreset } from '@/utils/time'

const store = useTimeRangeStore()
// 若全局筛选已经是「自定义」，切换页面后保持输入区展开，避免只高亮按钮却不显示输入框
const customOpen = ref(store.preset === 'custom')
const draftStart = ref('')
const draftEnd = ref('')

const isCustomActive = computed(() =>
  customOpen.value || store.preset === 'custom',
)

watch(customOpen, (open) => {
  if (!open) return
  const now = new Date()
  draftStart.value = store.customStart || toLocalInput(new Date(now.getTime() - 6 * 86400000))
  draftEnd.value = store.customEnd || toLocalInput(now)
}, { immediate: true })

function select(preset: RangePreset) {
  if (preset === 'custom') {
    // 仅展开输入区，点击「应用」后才真正提交并触发所有接口重新取数
    customOpen.value = true
    return
  }
  customOpen.value = false
  store.setPreset(preset)
}

function apply() {
  if (!draftStart.value || !draftEnd.value) return
  store.setCustom(draftStart.value, draftEnd.value)
}

function isActive(preset: RangePreset): boolean {
  if (preset === 'custom') return isCustomActive.value
  return !isCustomActive.value && store.preset === preset
}
</script>

<template>
  <div class="range-picker">
    <div class="range-picker__presets" role="group" aria-label="时间范围">
      <button
        v-for="p in RANGE_PRESETS"
        :key="p.value"
        type="button"
        class="range-picker__btn"
        :class="{ 'is-active': isActive(p.value) }"
        @click="select(p.value)"
      >{{ p.label }}</button>
    </div>

    <div v-if="customOpen" class="range-picker__custom">
      <input v-model="draftStart" type="datetime-local" class="input" aria-label="开始时间" />
      <span class="text-muted">至</span>
      <input v-model="draftEnd" type="datetime-local" class="input" aria-label="结束时间" />
      <button class="btn btn--primary btn--sm" type="button" @click="apply">应用</button>
    </div>

    <span class="range-picker__label mono">{{ store.label }}</span>
  </div>
</template>

<style scoped>
.range-picker { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.range-picker__presets {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: #eef0f4;
  border-radius: var(--radius-sm);
  flex-wrap: wrap;
}
.range-picker__btn {
  border: none;
  background: transparent;
  padding: 5px 11px;
  border-radius: var(--radius-sm);
  font-size: var(--font-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  white-space: nowrap;
  transition: background var(--transition), color var(--transition);
}
.range-picker__btn:hover { color: var(--color-primary); }
.range-picker__btn.is-active {
  background: var(--color-surface);
  color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}
.range-picker__custom { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
.range-picker__custom .input { width: 196px; }
.range-picker__label {
  margin-left: auto;
  font-size: var(--font-xs);
  color: var(--color-text-muted);
  white-space: nowrap;
}
@media (max-width: 640px) {
  .range-picker__custom .input { width: 160px; }
  .range-picker__label { margin-left: 0; }
}
</style>
