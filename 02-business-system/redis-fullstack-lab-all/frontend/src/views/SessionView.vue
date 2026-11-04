<script setup lang="ts">
import { ref } from 'vue'
import { sessionLogin, sessionMe, sessionLogout, sessionRaw } from '@/api/redislab'
import { useSessionStore } from '@/store'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const store = useSessionStore()
const username = ref('alice')
const password = ref('123456')
const me = ref<any>(null)
const raw = ref<any>(null)
const stateRef = ref()

async function login() {
  const r = await sessionLogin(username.value, password.value)
  ElMessage.success((r.data as string) || 'ok')
  await loadMe()
}
async function loadMe() {
  const r = await sessionMe()
  me.value = r.data
  store.setUser(r.data)
}
async function showRaw() {
  const r = await sessionRaw()
  raw.value = r.data
}
async function logout() {
  await sessionLogout()
  me.value = null
  raw.value = null
  store.clear()
  ElMessage.success('已退出')
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景2：分布式会话（Spring Session 存 Redis）"
      :desc="`多实例部署下，用户登录态不能绑定单机 Tomcat。开启 @EnableRedisHttpSession 后，HttpSession 被序列化进 Redis（key: spring:session:sessions:<id>）。任意实例写入的会话其它实例都能读，前端无需感知后端有几台机器。可在两个浏览器标签登录，互相可见登录态。`"
    >
      <el-form inline>
        <el-form-item label="用户名"><el-input v-model="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="password" type="password" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="login">登录</el-button>
          <el-button @click="loadMe">查登录态 me</el-button>
          <el-button @click="showRaw">查看原始 Session</el-button>
          <el-button @click="logout">退出</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="me" :title="'当前登录用户 (me)'" :description="JSON.stringify(me)" type="success" :closable="false" />
      <el-alert v-if="raw" :title="'Session 原始信息（落在 Redis 中）'" :description="JSON.stringify(raw)" type="warning" :closable="false" />
      <el-alert v-if="!me" title="尚未登录" type="info" :closable="false" />
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
