<template>
  <div>
    <!-- 用户消息 -->
    <div v-if="message.role === 'user'" class="msg-row user">
      <div class="avatar user">🙂</div>
      <div class="bubble">{{ message.content }}</div>
    </div>

    <!-- 助手消息 -->
    <div v-else class="msg-row bot">
      <div class="avatar bot">🤖</div>
      <div>
        <div class="intent-line" v-if="message.intentLabel">
          <IntentBadge :intent="message.intent" :label="message.intentLabel" :confidence="message.confidence" />
          <span v-if="message.matchedKeywords" style="margin-left:6px;">命中关键词：{{ message.matchedKeywords }}</span>
        </div>
        <div class="bubble">
          {{ message.content }}<span v-if="message.streaming" class="thinking">▋</span>
        </div>

        <!-- 命中 FAQ 参考 -->
        <div class="faq-ref" v-if="message.faqs && message.faqs.length">
          <el-collapse>
            <el-collapse-item title="📚 参考知识库条目" name="faq">
              <div v-for="f in message.faqs" :key="f.id" style="margin-bottom:8px;">
                <div style="font-weight:600;">Q：{{ f.question }}</div>
                <div style="color:#4e5969; margin-top:2px;">A：{{ f.answer }}</div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 转人工提示 -->
        <div class="escalate-banner" v-if="message.escalated">
          🧑‍💼 已为您转接人工客服，请稍候坐席接入。
        </div>

        <!-- 满意度评价按钮 -->
        <div style="margin:10px 0 0 46px;" v-if="showRate && !message.escalated">
          <el-button size="small" @click="$emit('rate')">✅ 评价本次服务</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import IntentBadge from './IntentBadge.vue';
import type { ChatMessage } from '../types';

defineProps<{ message: ChatMessage; showRate?: boolean }>();
defineEmits<{ rate: [] }>();
</script>
