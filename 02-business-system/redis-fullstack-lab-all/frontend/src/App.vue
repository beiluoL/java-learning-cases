<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const active = computed(() => route.path)
</script>

<template>
  <el-container class="app-container">
    <el-aside width="220px" class="aside">
      <div class="logo">Redis 全栈实战</div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item index="/cache"><el-icon><Histogram /></el-icon>缓存加速</el-menu-item>
        <el-menu-item index="/session"><el-icon><User /></el-icon>分布式会话</el-menu-item>
        <el-menu-item index="/rank"><el-icon><Trophy /></el-icon>排行榜</el-menu-item>
        <el-menu-item index="/ratelimit"><el-icon><Switch /></el-icon>限流控制</el-menu-item>
        <el-menu-item index="/pubsub"><el-icon><ChatDotRound /></el-icon>发布订阅</el-menu-item>
        <el-menu-item index="/penetration"><el-icon><Aim /></el-icon>缓存穿透</el-menu-item>
        <el-menu-item index="/breakdown"><el-icon><Lightning /></el-icon>缓存击穿</el-menu-item>
        <el-menu-item index="/avalanche"><el-icon><Warning /></el-icon>缓存雪崩</el-menu-item>
        <el-menu-item index="/lock"><el-icon><Lock /></el-icon>分布式锁</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-container { height: 100vh; }
.aside { background: #001529; color: #fff; }
.logo { height: 60px; line-height: 60px; text-align: center; font-size: 18px; font-weight: 700; color: #fff; border-bottom: 1px solid #333; }
.menu { background: #001529; border-right: none; }
.menu :deep(.el-menu-item) { color: #ccc; }
.menu :deep(.el-menu-item.is-active) { background: #1890ff; color: #fff; }
.main { background: #f0f2f5; padding: 16px; overflow-y: auto; }
</style>
