<script setup lang="ts">
import { computed } from 'vue';
import type { OpinionVO } from '@/api/types';

const props = defineProps<{
  opinions: OpinionVO[];
}>();

const sentimentMeta: Record<string, { type: 'success' | 'danger' | 'info'; label: string }> = {
  POSITIVE: { type: 'success', label: '正面' },
  NEGATIVE: { type: 'danger', label: '负面' },
  NEUTRAL: { type: 'info', label: '中性' },
};

const list = computed(() => props.opinions);
</script>

<template>
  <div class="opinion-feed">
    <el-empty v-if="list.length === 0" description="暂无舆情" :image-size="60" />
    <div v-for="item in list" :key="item.id" class="opinion-item">
      <div class="opinion-head">
        <el-tag :type="sentimentMeta[item.sentiment]?.type || 'info'" size="small" effect="dark">
          {{ sentimentMeta[item.sentiment]?.label || item.sentiment }}
        </el-tag>
        <span class="opinion-topic">#{{ item.topic }}</span>
        <span class="opinion-source">{{ item.source }}</span>
      </div>
      <div class="opinion-content">{{ item.content }}</div>
      <div class="opinion-meta">热度 {{ item.hot }} · {{ item.author }} · {{ item.publishTime }}</div>
    </div>
  </div>
</template>

<style scoped>
.opinion-feed {
  max-height: 360px;
  overflow-y: auto;
}
.opinion-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f0f0f0;
}
.opinion-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.opinion-topic {
  font-size: 12px;
  color: #1677ff;
}
.opinion-source {
  font-size: 12px;
  color: #8a94a6;
  margin-left: auto;
}
.opinion-content {
  font-size: 13px;
  color: #1f2d3d;
}
.opinion-meta {
  font-size: 12px;
  color: #8a94a6;
  margin-top: 4px;
}
</style>
