<script setup lang="ts">
import { computed } from 'vue';
import type { AnalysisAlert } from '@/api/types';

const props = defineProps<{
  alerts: AnalysisAlert[];
}>();

const levelMeta: Record<string, { type: 'info' | 'warning' | 'danger'; label: string }> = {
  INFO: { type: 'info', label: '提示' },
  WARN: { type: 'warning', label: '警告' },
  CRITICAL: { type: 'danger', label: '严重' },
};

const list = computed(() => props.alerts);
</script>

<template>
  <div class="alert-list">
    <el-empty v-if="list.length === 0" description="暂无告警" :image-size="60" />
    <div v-for="alert in list" :key="alert.alertId" class="alert-item">
      <el-tag :type="levelMeta[alert.level]?.type || 'info'" size="small" effect="dark">
        {{ levelMeta[alert.level]?.label || alert.level }}
      </el-tag>
      <div class="alert-body">
        <div class="alert-msg">{{ alert.message }}</div>
        <div class="alert-meta">
          {{ alert.deviceCode }} · {{ alert.metric }} = {{ alert.value }} · {{ alert.time }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.alert-list {
  max-height: 360px;
  overflow-y: auto;
}
.alert-item {
  display: flex;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f0f0;
}
.alert-body {
  flex: 1;
}
.alert-msg {
  font-size: 13px;
  color: #1f2d3d;
}
.alert-meta {
  font-size: 12px;
  color: #8a94a6;
  margin-top: 2px;
}
</style>
