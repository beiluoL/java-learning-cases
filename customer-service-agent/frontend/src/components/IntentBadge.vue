<template>
  <span class="intent-badge" :style="{ background: colorBg, color: colorText, borderColor: colorText }">
    {{ label }}
    <template v-if="confidence !== undefined">· {{ Math.round(confidence * 100) }}%</template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { IntentType } from '../types';

const props = defineProps<{
  intent?: IntentType | string;
  label?: string;
  confidence?: number;
}>();

const MAP: Record<string, { bg: string; text: string; def: string }> = {
  REFUND: { bg: '#fff7e6', text: '#d46b08', def: '退款' },
  ORDER_QUERY: { bg: '#e6f4ff', text: '#1677ff', def: '查询订单' },
  TECH_SUPPORT: { bg: '#f6ffed', text: '#389e0d', def: '技术支持' },
  UNKNOWN: { bg: '#fafafa', text: '#8a9099', def: '未知' }
};

const info = computed(() => MAP[props.intent || 'UNKNOWN'] || MAP.UNKNOWN);
const colorBg = computed(() => info.value.bg);
const colorText = computed(() => info.value.text);
const label = computed(() => props.label || info.value.def);
</script>

<style scoped>
.intent-badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  border: 1px solid;
  line-height: 18px;
}
</style>
