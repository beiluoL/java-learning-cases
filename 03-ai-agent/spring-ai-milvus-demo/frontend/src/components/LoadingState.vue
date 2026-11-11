<script setup lang="ts">
withDefaults(defineProps<{ height?: string; text?: string }>(), {
  height: '160px',
  text: '加载中…',
})
</script>

<template>
  <div class="state" :style="{ minHeight: height }" role="status">
    <span class="spinner" aria-hidden="true" />
    <span class="state__text">{{ text }}</span>
  </div>
</template>

<style scoped>
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
}
.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--color-primary-soft);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.state__text {
  font-size: var(--font-sm);
  color: var(--color-text-muted);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
