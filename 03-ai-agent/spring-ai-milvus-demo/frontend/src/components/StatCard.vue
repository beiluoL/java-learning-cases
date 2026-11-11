<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string
    sub?: string
    tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'
    loading?: boolean
  }>(),
  {
    sub: '',
    tone: 'primary',
    loading: false,
  },
)
</script>

<template>
  <div class="stat-card" :class="`stat-card--${tone}`">
    <div class="stat-card__head">
      <span class="stat-card__dot" />
      <span class="stat-card__label">{{ label }}</span>
    </div>
    <div v-if="loading" class="stat-card__skeleton" />
    <div v-else class="stat-card__value mono num" :title="value">{{ value }}</div>
    <div v-if="sub" class="stat-card__sub">{{ sub }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  --tone: var(--color-primary);
  position: relative;
  padding: var(--space-4) var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  min-width: 0;
}
.stat-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: var(--tone);
}
.stat-card--primary {
  --tone: var(--color-primary);
}
.stat-card--success {
  --tone: var(--color-success);
}
.stat-card--warning {
  --tone: var(--color-warning);
}
.stat-card--danger {
  --tone: var(--color-danger);
}
.stat-card--info {
  --tone: var(--color-info);
}
.stat-card--neutral {
  --tone: #9ca3af;
}

.stat-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.stat-card__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tone);
  opacity: 0.85;
}
.stat-card__label {
  font-size: var(--font-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}
.stat-card__value {
  margin-top: var(--space-3);
  font-size: var(--font-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stat-card__sub {
  margin-top: 6px;
  font-size: var(--font-xs);
  color: var(--color-text-muted);
}
.stat-card__skeleton {
  margin-top: var(--space-3);
  width: 62%;
  height: 27px;
  border-radius: var(--radius-sm);
  background: linear-gradient(90deg, #eef0f4 25%, #f7f8fa 50%, #eef0f4 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s linear infinite;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
