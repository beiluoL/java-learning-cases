<script setup lang="ts">
withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '',
  description: '',
})
</script>

<template>
  <section class="filter-bar panel">
    <div v-if="title || description" class="filter-bar__head">
      <span v-if="title" class="filter-bar__title">{{ title }}</span>
      <span v-if="description" class="filter-bar__desc">{{ description }}</span>
    </div>
    <div class="filter-bar__body">
      <div class="filter-bar__fields"><slot /></div>
      <div v-if="$slots.actions" class="filter-bar__actions"><slot name="actions" /></div>
    </div>
  </section>
</template>

<style scoped>
.filter-bar {
  padding: var(--space-4) var(--space-5);
}
.filter-bar__head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.filter-bar__title {
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
}
.filter-bar__desc {
  font-size: var(--font-sm);
  color: var(--color-text-muted);
}
.filter-bar__body {
  display: flex;
  align-items: flex-end;
  gap: var(--space-4);
  flex-wrap: wrap;
}
.filter-bar__fields {
  flex: 1;
  min-width: 240px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-3) var(--space-4);
}
.filter-bar__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: 1px;
}

@media (max-width: 640px) {
  .filter-bar__fields {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }
}
</style>
