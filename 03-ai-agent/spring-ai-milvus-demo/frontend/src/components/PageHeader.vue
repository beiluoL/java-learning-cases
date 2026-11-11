<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>(), { description: '', eyebrow: '' })
</script>

<template>
  <div class="page-header">
    <div>
      <p v-if="eyebrow" class="page-header__eyebrow">{{ eyebrow }}</p>
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions"><slot name="actions" /></div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}
.page-header__eyebrow {
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}
.page-header__title {
  margin-top: 3px;
  font-size: var(--font-xl);
  font-weight: var(--font-weight-semibold);
  letter-spacing: -0.02em;
}
.page-header__desc { margin-top: 4px; font-size: var(--font-sm); color: var(--color-text-muted); }
.page-header__actions { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
</style>
