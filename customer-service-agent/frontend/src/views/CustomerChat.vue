<template>
  <div class="chat-wrap">
    <div class="chat-toolbar">
      <div>
        <b>智能客服</b>
        <span v-if="conversationId" style="color:#8a9099; font-size:12px; margin-left:8px;">
          会话ID: {{ conversationId.slice(0, 8) }}…
        </span>
      </div>
      <div>
        <el-button size="small" @click="escalate">🧑‍💼 转人工</el-button>
        <el-button size="small" type="primary" plain @click="newChat">＋ 新对话</el-button>
      </div>
    </div>

    <div class="chat-body" ref="bodyRef">
      <div v-if="messages.length === 0" class="empty">
        <div style="text-align:center; color:#8a9099; margin-top:60px;">
          <div style="font-size:40px;">💬</div>
          <p>您好，我是智能客服助手，可以帮您处理退款、查询订单、技术问题。</p>
          <div style="display:flex; gap:8px; justify-content:center; flex-wrap:wrap; margin-top:16px;">
            <el-button v-for="q in suggestions" :key="q" size="small" round @click="input = q; send()">
              {{ q }}
            </el-button>
          </div>
        </div>
      </div>

      <MessageBubble
        v-for="(m, idx) in messages"
        :key="m.id"
        :message="m"
        :show-rate="idx === messages.length - 1 && m.role === 'assistant' && !m.escalated && m.content.length > 0"
        @rate="openRate"
      />

      <div v-if="loading" class="intent-line">智能客服正在输入…</div>
    </div>

    <div class="chat-input-bar">
      <div style="display:flex; gap:8px;">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="请输入您的问题，Enter 发送 / Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="loading" @click="send" style="width:90px;">发送</el-button>
      </div>
    </div>

    <SatisfactionDialog v-model="showSatisfaction" :conversation-id="conversationId || ''" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import MessageBubble from '../components/MessageBubble.vue';
import SatisfactionDialog from '../components/SatisfactionDialog.vue';
import { streamChat } from '../api/client';
import type { ChatMessage } from '../types';

const input = ref('');
const messages = ref<ChatMessage[]>([]);
const conversationId = ref<string | undefined>(undefined);
const loading = ref(false);
const showSatisfaction = ref(false);
const bodyRef = ref<HTMLElement | null>(null);
let currentAssistantId = '';
let cancelStream: (() => void) | null = null;

const suggestions = [
  '我要申请退款',
  '我的快递到哪了',
  'App 闪退打不开怎么办',
  '怎么联系人工客服'
];

const LABEL: Record<string, string> = {
  REFUND: '退款',
  ORDER_QUERY: '查询订单',
  TECH_SUPPORT: '技术支持',
  UNKNOWN: '未知'
};

function scrollBottom() {
  nextTick(() => {
    if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
  });
}

function send() {
  const text = input.value.trim();
  if (!text || loading.value) return;
  input.value = '';

  // 用户消息
  messages.value.push({
    id: 'u' + Date.now(),
    role: 'user',
    content: text
  });

  // 助手占位（流式填充）
  const aid = 'a' + Date.now();
  currentAssistantId = aid;
  messages.value.push({
    id: aid,
    role: 'assistant',
    content: '',
    streaming: true
  });
  loading.value = true;
  scrollBottom();

  cancelStream = streamChat(
    { conversationId: conversationId.value, message: text, forceEscalate: false },
    onEvent,
    onStreamError
  );
}

function escalate() {
  if (loading.value) return;
  const text = input.value.trim() || '请转人工';
  input.value = '';
  messages.value.push({ id: 'u' + Date.now(), role: 'user', content: text });
  const aid = 'a' + Date.now();
  currentAssistantId = aid;
  messages.value.push({ id: aid, role: 'assistant', content: '', streaming: true });
  loading.value = true;
  scrollBottom();
  cancelStream = streamChat(
    { conversationId: conversationId.value, message: text, forceEscalate: true },
    onEvent,
    onStreamError
  );
}

function onEvent(e: { type: string; data: any }) {
  const msg = messages.value.find((m) => m.id === currentAssistantId);
  switch (e.type) {
    case 'init':
      conversationId.value = e.data.conversationId;
      break;
    case 'intent':
      if (msg) {
        msg.intent = e.data.intent;
        msg.intentLabel = LABEL[e.data.intent] || e.data.label || '未知';
        msg.confidence = e.data.confidence;
        msg.matchedKeywords = e.data.matchedKeywords;
      }
      break;
    case 'faq':
      if (msg) msg.faqs = e.data;
      break;
    case 'escalate':
      if (msg) msg.escalated = true;
      break;
    case 'text':
      if (msg) msg.content += e.data;
      scrollBottom();
      break;
    case 'done':
      if (msg) msg.streaming = false;
      loading.value = false;
      break;
    case 'error':
      if (msg) { msg.streaming = false; msg.content = '⚠️ ' + (e.data?.message || '出错了'); }
      loading.value = false;
      ElMessage.error('对话出错：' + (e.data?.message || ''));
      break;
  }
}

function onStreamError(msgText: string) {
  loading.value = false;
  const m = messages.value.find((x) => x.id === currentAssistantId);
  if (m) { m.streaming = false; m.content = '⚠️ ' + msgText; }
  ElMessage.error(msgText);
}

function newChat() {
  if (cancelStream) cancelStream();
  messages.value = [];
  conversationId.value = undefined;
  loading.value = false;
  currentAssistantId = '';
}

function openRate() {
  if (!conversationId.value) return;
  showSatisfaction.value = true;
}
</script>
