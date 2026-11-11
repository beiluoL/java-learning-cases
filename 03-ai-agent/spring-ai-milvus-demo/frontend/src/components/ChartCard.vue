<script setup lang="ts">
import AsyncState from './AsyncState.vue'

withDefaults(defineProps<{
  title: string
  subtitle?: string
  loading?: boolean
  error?: string | null
  errorTraceId?: string
  empty?: boolean
  emptyText?: string
  height?: string
  bodyPadding?: boolean
}>(), {
  subtitle: '',
  loading: false,
  error: null,
  errorTraceId: '',
  empty: false,
  emptyText: '所选时间范围内暂无数据',
  height: '280px',
  bodyPadding: false,
})

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="panel chart-card">
    <header class="panel__header">
      <div>
        <h3 class="panel__title">{{ title }}</h3>
        <p v-if="subtitle" class="panel__subtitle">{{ subtitle }}</p>
      </div>
      <div class="chart-card__actions"><slot name="actions" /></div>
    </header>
    <div class="chart-card__body" :class="{ 'chart-card__body--padded': bodyPadding }">
      <AsyncState
        :loading="loading"
        :error="error"
        :error-trace-id="errorTraceId"
        :empty="empty"
        :empty-text="emptyText"
        :min-height="height"
        @retry="emit('retry')"
      >
        <slot />
      </AsyncState>
    </div>
  </section>
</template>

<style scoped>
.chart-card { display: flex; flex-direction: column; min-width: 0; }
.chart-card__actions { display: flex; align-items: center; gap: var(--space-2); }
.chart-card__body { flex: 1; min-width: 0; padding: var(--space-2) var(--space-2) var(--space-3); }
.chart-card__body--padded { padding: var(--space-5); }
</style>
