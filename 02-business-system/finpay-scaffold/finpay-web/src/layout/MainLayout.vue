<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider breakpoint="lg" collapsible>
      <div class="logo">FinPay</div>
      <a-menu :selectedKeys="[route.name as string]" theme="dark" mode="inline" @click="onMenu">
        <a-menu-item key="dashboard"><span>概览</span></a-menu-item>
        <a-menu-item key="transactions"><span>交易查询</span></a-menu-item>
        <a-menu-item key="reconcile"><span>对账管理</span></a-menu-item>
        <a-menu-item key="audit"><span>审计日志</span></a-menu-item>
        <a-menu-item key="channels"><span>渠道配置</span></a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <span class="title">{{ title }}</span>
        <a-button type="link" @click="logout">退出 ({{ auth.username }})</a-button>
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const title = computed(() => (route.meta.title as string) || 'FinPay')

function onMenu(e: any) {
  router.push({ name: e.key })
}
function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.logo { color: #fff; font-weight: 700; font-size: 20px; text-align: center; padding: 16px 0; letter-spacing: 2px; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; }
.title { font-weight: 600; font-size: 16px; }
.content { margin: 16px; padding: 16px; background: #fff; border-radius: 8px; min-height: 280px; }
</style>
