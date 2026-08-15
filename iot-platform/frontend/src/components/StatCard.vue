<script setup lang="ts">
defineProps<{
  title: string;
  value: string | number;
  suffix?: string;
  color?: string;
  icon?: string;
}>();
</script>

<template>
  <el-card shadow="hover" class="stat-card">
    <div class="stat-row">
      <div class="stat-icon" :style="{ background: color || '#1677ff' }">
        <el-icon><component :is="icon || 'Odometer'" /></el-icon>
      </div>
      <div class="stat-body">
        <div class="stat-title">{{ title }}</div>
        <div class="stat-value">
          {{ value }}<span class="stat-suffix">{{ suffix }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.stat-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}
.stat-title {
  color: #8a94a6;
  font-size: 13px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}
.stat-suffix {
  font-size: 13px;
  color: #8a94a6;
  margin-left: 4px;
  font-weight: 400;
}
</style>
