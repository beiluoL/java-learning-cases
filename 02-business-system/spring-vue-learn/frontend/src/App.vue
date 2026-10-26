<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="app-header">
    <h1>Spring Boot + Vue3 学习框架</h1>
    <nav>
      <RouterLink to="/">首页</RouterLink>
    </nav>
  </header>
  <main>
    <RouterView />
  </main>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 16px 24px;
  border-bottom: 1px solid #eee;
}
.app-header h1 {
  font-size: 18px;
  margin: 0;
}
.app-header nav a {
  margin-right: 12px;
  color: #1677ff;
  text-decoration: none;
}
</style>
