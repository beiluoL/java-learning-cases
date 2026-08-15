<script setup lang="ts">
import { computed } from 'vue';
import type { DeviceMetricSnapshot } from '@/api/types';

const props = defineProps<{
  snapshots: DeviceMetricSnapshot[];
}>();

const statusMeta: Record<string, { color: string; label: string }> = {
  NORMAL: { color: '#52c41a', label: '正常' },
  WARNING: { color: '#faad14', label: '预警' },
  ABNORMAL: { color: '#ff4d4f', label: '异常' },
};

const items = computed(() =>
  props.snapshots.map((s) => ({
    ...s,
    meta: statusMeta[s.status] || statusMeta.NORMAL,
  })),
);
</script>

<template>
  <div class="metric-grid">
    <el-empty v-if="items.length === 0" description="暂无实时指标" />
    <div v-for="item in items" :key="item.deviceCode + item.metric" class="metric-cell">
      <div class="metric-head">
        <span class="metric-name">{{ item.deviceCode }} / {{ item.metric }}</span>
        <el-tag :color="item.meta.color" size="small" effect="dark">{{ item.meta.label }}</el-tag>
      </div>
      <div class="metric-value">
        {{ Number(item.currentValue).toFixed(2) }}<span class="metric-unit">{{ item.unit }}</span>
      </div>
      <div class="metric-stats">
        <span>均值 {{ Number(item.avg).toFixed(1) }}</span>
        <span>最大 {{ Number(item.max).toFixed(1) }}</span>
        <span>最小 {{ Number(item.min).toFixed(1) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.metric-cell {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.metric-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.metric-name {
  font-size: 13px;
  color: #5a6577;
}
.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}
.metric-unit {
  font-size: 12px;
  color: #8a94a6;
  margin-left: 4px;
}
.metric-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #8a94a6;
}
</style>
