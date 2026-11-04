<template>
  <div class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :class="tone">
      {{ value }}
      <span v-if="unit" class="stat-unit">{{ unit }}</span>
    </div>
    <div v-if="hint" class="stat-hint">{{ hint }}</div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    unit?: string
    hint?: string
    tone?: 'default' | 'blue' | 'teal' | 'amber' | 'red'
  }>(),
  { tone: 'default' }
)
</script>

<style scoped>
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 15px 17px;
  box-shadow: var(--shadow-sm);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.01em;
}

.stat-value {
  font-size: 26px;
  font-weight: 650;
  line-height: 1.25;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.stat-value.blue {
  color: var(--primary);
}

.stat-value.teal {
  color: var(--teal);
}

.stat-value.amber {
  color: var(--amber);
}

.stat-value.red {
  color: var(--danger);
}

.stat-unit {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  margin-left: 2px;
}

.stat-hint {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-muted);
}
</style>
