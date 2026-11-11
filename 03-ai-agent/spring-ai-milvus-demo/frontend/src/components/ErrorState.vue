<script setup lang="ts">
withDefaults(defineProps<{ message?: string; traceId?: string; height?: string }>(), {
  message: '接口请求失败',
  traceId: '',
  height: '160px',
})
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state" :style="{ minHeight: height }" role="alert">
    <svg
      class="state__icon"
      width="34"
      height="34"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.6"
      stroke-linecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5v5" />
      <circle cx="12" cy="16.4" r="0.7" fill="currentColor" stroke="none" />
    </svg>
    <p class="state__title">数据加载失败</p>
    <p class="state__desc">{{ message }}</p>
    <p v-if="traceId" class="state__trace mono">traceId: {{ traceId }}</p>
    <button class="btn btn--sm" type="button" @click="emit('retry')">重新加载</button>
  </div>
</template>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  text-align: center;
}
.state__icon {
  color: var(--color-danger);
}
.state__title {
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
}
.state__desc {
  max-width: 460px;
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
  word-break: break-word;
}
.state__trace {
  font-size: var(--font-xs);
  color: var(--color-text-muted);
}
.state .btn {
  margin-top: var(--space-2);
}
</style>
