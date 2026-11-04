<script setup lang="ts">
defineProps<{ title: string; desc: string }>()
</script>

<template>
  <el-card class="panel" shadow="never">
    <template #header>
      <span class="title">{{ title }}</span>
    </template>
    <el-alert :title="'业务背景'" :description="desc" type="info" :closable="false" class="desc" />
    <div class="content">
      <slot />
    </div>
  </el-card>
</template>

<style scoped>
.panel { margin-bottom: 16px; }
.title { font-size: 16px; font-weight: 700; }
.desc { margin-bottom: 16px; white-space: pre-line; }
.content { margin-top: 8px; }
</style>
