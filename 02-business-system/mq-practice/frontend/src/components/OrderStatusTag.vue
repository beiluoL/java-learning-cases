<template>
  <span class="tag" :class="style.cls">
    <span class="pulse" v-if="status === 'QUEUING'" :style="{ background: style.color }"></span>
    {{ style.text }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { OrderStatus } from '@/types'

const props = defineProps<{ status: string; text?: string }>()

/**
 * 状态 → 视觉映射。
 * 注意后端已返回 statusText，这里仍保留一份兜底，避免历史数据状态值变更时前端空白。
 */
const MAP: Record<OrderStatus, { text: string; cls: string; color: string }> = {
  QUEUING: { text: '排队中', cls: 'blue', color: '#3f5bd9' },
  UNPAID: { text: '待支付', cls: 'amber', color: '#b4740e' },
  PAID: { text: '已支付', cls: 'teal', color: '#0e9384' },
  CANCELLED: { text: '已取消', cls: '', color: '#8b929e' },
  FAILED: { text: '创建失败', cls: 'red', color: '#c53a2b' }
}

const style = computed(() => {
  const hit = MAP[props.status as OrderStatus]
  return hit ? { ...hit, text: props.text || hit.text } : { text: props.text || props.status, cls: '', color: '#8b929e' }
})
</script>

<style scoped>
.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  animation: breathe 1.1s ease-in-out infinite;
}

@keyframes breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.35;
    transform: scale(0.75);
  }
}
</style>
