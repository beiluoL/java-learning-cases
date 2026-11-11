<script setup lang="ts">
withDefaults(defineProps<{ description?: string; height?: string }>(), {
  description: '所选时间范围内暂无数据',
  height: '160px',
})
</script>

<template>
  <div class="state" :style="{ minHeight: height }">
    <svg
      class="state__icon"
      width="42"
      height="42"
      viewBox="0 0 48 48"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linejoin="round"
      stroke-linecap="round"
      aria-hidden="true"
    >
      <path d="M5 15l7-9h24l7 9" />
      <path d="M5 15v23a2 2 0 002 2h34a2 2 0 002-2V15" />
      <path d="M5 15h12l3 6h8l3-6h12" />
    </svg>
    <p class="state__title">暂无数据</p>
    <p class="state__desc">{{ description }}</p>
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
  color: #c7ccd6;
}
.state__title {
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-secondary);
}
.state__desc {
  font-size: var(--font-sm);
  color: var(--color-text-muted);
}
</style>
