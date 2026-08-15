<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { useRealtimeStore } from '@/stores/realtime';
import { connectRealtime, disconnectRealtime } from '@/composables/useStomp';

const route = useRoute();
const realtimeStore = useRealtimeStore();
const pageTitle = computed(() => (route.meta.title as string) || 'IoT 大数据平台');
const connected = computed(() => realtimeStore.connected);

onMounted(() => {
  connectRealtime();
});

onUnmounted(() => {
  disconnectRealtime();
});
</script>

<template>
  <el-container class="app-container">
    <el-aside width="220px" class="app-aside">
      <div class="brand">
        <span class="brand-logo">IoT</span>
        <span class="brand-text">大数据平台</span>
      </div>
      <el-menu router :default-active="route.path" class="app-menu" background-color="#0f1b2d" text-color="#c5d2e0" active-text-color="#ffffff">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><span>实时数据看板</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Cpu /></el-icon><span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/analysis">
          <el-icon><TrendCharts /></el-icon><span>实时分析</span>
        </el-menu-item>
        <el-menu-item index="/opinion">
          <el-icon><ChatDotRound /></el-icon><span>舆情监控</span>
        </el-menu-item>
        <el-menu-item index="/visualization">
          <el-icon><DataLine /></el-icon><span>数据可视化</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <el-tag :type="connected ? 'success' : 'danger'" effect="dark" size="small">
            {{ connected ? '实时通道已连接' : '实时通道断开' }}
          </el-tag>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-container {
  height: 100vh;
}
.app-aside {
  background: #0f1b2d;
  color: #fff;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  font-weight: 700;
}
.brand-logo {
  background: #1677ff;
  color: #fff;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 16px;
}
.brand-text {
  font-size: 16px;
  color: #fff;
}
.app-menu {
  border-right: none;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
}
.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}
.app-main {
  background: #f0f2f5;
  padding: 16px;
}
</style>
