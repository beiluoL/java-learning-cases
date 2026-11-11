<script setup lang="ts">
import { computed } from 'vue'
import { toPercentNumber } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    value?: number | string
    color?: string
    showValue?: boolean
    label?: string
  }>(),
  {
    value: 0,
    color: '',
    showValue: true,
    label: '',
  },
)

const pct = computed(() => Math.min(100, Math.max(0, toPercentNumber(props.value))))
const tone = computed(() => {
  if (props.color) return props.color
  if (pct.value >= 90) return 'var(--color-danger)'
  if (pct.value >= 70) return 'var(--color-warning)'
  return 'var(--color-primary)'
})
</script>

<template>
  <div class="progress">
    <div v-if="label || showValue" class="progress__meta">
      <span v-if="label">{{ label }}</span>
      <span v-if="showValue" class="mono">{{ pct.toFixed(2) }}%</span>
    </div>
    <div
      class="progress__track"
      role="progressbar"
      :aria-valuenow="pct"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <div class="progress__fill" :style="{ width: pct + '%', background: tone }" />
    </div>
  </div>
</template>

<style scoped>
.progress {
  width: 100%;
}
.progress__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-bottom: 6px;
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
}
.progress__track {
  width: 100%;
  height: 8px;
  border-radius: var(--radius-full);
  background: #eef0f4;
  overflow: hidden;
}
.progress__fill {
  height: 100%;
  border-radius: var(--radius-full);
  transition: width 420ms ease;
}
</style>
