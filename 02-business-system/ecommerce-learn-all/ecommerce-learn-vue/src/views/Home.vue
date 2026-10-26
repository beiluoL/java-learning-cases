<template>
  <div>
    <header class="topbar">
      <div class="container topbar-inner">
        <router-link to="/products" class="logo">🛒 电商学习项目</router-link>
        <nav class="nav">
          <router-link to="/products">商品</router-link>
          <router-link to="/seckill">秒杀</router-link>
          <router-link to="/cart">购物车</router-link>
          <router-link to="/orders">我的订单</router-link>
          <router-link v-if="user.role === 'ADMIN'" to="/admin/dashboard">后台</router-link>
        </nav>
        <div class="user-area">
          <router-link v-if="!user.token" to="/login" class="login-btn">登录 / 注册</router-link>
          <el-dropdown v-else>
            <span class="el-dropdown-link">
              {{ user.userInfo?.nickname || user.userInfo?.username }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goUser">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="user.role === 'ADMIN'" @click="goAdmin">后台管理</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    <main class="container">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useUserStore } from '../store/user'
import { useRouter } from 'vue-router'

const user = useUserStore()
const router = useRouter()

function logout() {
  user.logout()
  router.push('/login')
}
function goUser() {
  router.push('/user')
}
function goAdmin() {
  router.push('/admin/dashboard')
}
</script>

<style scoped>
.topbar {
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 10;
}
.topbar-inner {
  display: flex;
  align-items: center;
  height: 56px;
  gap: 24px;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
}
.nav {
  display: flex;
  gap: 18px;
  flex: 1;
}
.nav a {
  color: #333;
}
.nav a.router-link-active {
  color: var(--brand);
  font-weight: 600;
}
.login-btn {
  color: var(--brand);
}
.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
}
</style>
