<script setup lang="ts">
import LoadingState from './LoadingState.vue'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'

withDefaults(
  defineProps<{
    loading?: boolean
    error?: string | null
    errorTraceId?: string
    empty?: boolean
    emptyText?: string
    minHeight?: string
  }>(),
  {
    loading: false,
    error: null,
    errorTraceId: '',
    empty: false,
    emptyText: '所选时间范围内暂无数据',
    minHeight: '160px',
  },
)

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="async-state" :style="{ minHeight }">
    <LoadingState v-if="loading" :height="minHeight" />
    <ErrorState
      v-else-if="error"
      :height="minHeight"
      :message="error"
      :trace-id="errorTraceId"
      @retry="emit('retry')"
    />
    <EmptyState v-else-if="empty" :height="minHeight" :description="emptyText" />
    <slot v-else />
  </div>
</template>

<style scoped>
.async-state {
  width: 100%;
}
</style>
