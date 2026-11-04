<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">MQ</div>
        <div>
          <div class="brand-title">消息队列实战</div>
          <div class="brand-sub">Spring Boot 3 · Vue 3</div>
        </div>
      </div>

      <div class="nav-group-label">业务场景</div>
      <div
        v-for="item in scenarioRoutes"
        :key="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="go(item.path)"
      >
        <span class="idx">{{ item.idx }}</span>
        <span>{{ item.label }}</span>
      </div>

      <div class="nav-group-label">运维</div>
      <div
        v-for="item in opsRoutes"
        :key="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
        @click="go(item.path)"
      >
        <span class="idx">{{ item.idx }}</span>
        <span>{{ item.label }}</span>
      </div>

      <div class="sidebar-footer">
        <div>RabbitMQ 控制台</div>
        <a href="http://localhost:15672" target="_blank" rel="noreferrer">localhost:15672 ↗</a>
        <div style="margin-top: 8px">API 文档</div>
        <a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noreferrer">
          /swagger-ui.html ↗
        </a>
      </div>
    </aside>

    <main class="app-main">
      <header class="app-header">
        <div>
          <div class="page-title">{{ current.meta.title }}</div>
          <div class="page-subtitle">{{ current.meta.subtitle }}</div>
        </div>
        <div style="display: flex; align-items: center; gap: 10px">
          <span class="tag" :class="brokerOk ? 'teal' : 'red'">
            <span
              class="dot"
              :style="{
                width: '6px',
                height: '6px',
                borderRadius: '50%',
                background: brokerOk ? '#0e9384' : '#c53a2b'
              }"
            ></span>
            {{ brokerOk ? 'RabbitMQ 已连接' : 'RabbitMQ 未连接' }}
          </span>
          <span class="tag">{{ delayMode === 'plugin' ? '延迟插件' : 'DLX + TTL' }}</span>
        </div>
      </header>

      <div class="app-content">
        <router-view v-slot="{ Component }">
          <keep-alive :max="3">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get } from '@/api/request'
import type { MqOverview } from '@/types'

const route = useRoute()
const router = useRouter()

const scenarioRoutes = [
  { path: '/seckill', label: '秒杀下单', idx: '01' },
  { path: '/orders', label: '订单与超时取消', idx: '02' },
  { path: '/products', label: '商品变更订阅', idx: '03' }
]

const opsRoutes = [
  { path: '/overview', label: '总览与拓扑', idx: '—' },
  { path: '/monitor', label: 'MQ 监控台', idx: '—' }
]

const current = computed(() => route)
const isActive = (path: string) => route.path === path
const go = (path: string) => router.push(path)

const brokerOk = ref(true)
const delayMode = ref('dlx')
let timer: number | undefined

/** 头部展示 broker 连通性 —— 这是排查"消息没发出去"的第一现场 */
async function ping() {
  try {
    const data = await get<MqOverview>('/mq/overview')
    brokerOk.value = data.brokerAvailable
    delayMode.value = data.delayMode
  } catch {
    brokerOk.value = false
  }
}

onMounted(() => {
  ping()
  timer = window.setInterval(ping, 15000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>
