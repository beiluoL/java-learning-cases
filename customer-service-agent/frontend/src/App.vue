<template>
  <div>
    <header class="app-header">
      <div class="brand">🤖 智能客服 Agent</div>
      <nav class="nav">
        <router-link to="/">客服对话</router-link>
        <router-link to="/admin">管理后台</router-link>
      </nav>
    </header>
    <router-view />
  </div>
</template>

<script setup lang="ts">
</script>
