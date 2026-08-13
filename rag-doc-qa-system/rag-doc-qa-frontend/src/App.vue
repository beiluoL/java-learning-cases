<template>
  <div class="page">
    <div class="container">
      <a-row :gutter="[24, 24]">
        <!-- 左侧：文档上传 -->
        <a-col :xs="24" :lg="10">
          <a-card class="panel" :bordered="false">
            <div class="panel-title">
              <folder-outlined class="title-icon" />
              <span>文档上传</span>
            </div>

            <a-upload-dragger
              v-model:fileList="fileList"
              name="file"
              :multiple="true"
              :before-upload="beforeUpload"
              :custom-request="customRequest"
              accept=".doc,.docx,.pdf,.txt,.md"
              class="upload-area"
            >
              <p class="ant-upload-drag-icon">
                <inbox-outlined />
              </p>
              <p class="ant-upload-text">点击或拖拽文件到此处上传</p>
              <p class="ant-upload-hint">
                支持的文件格式：DOC、DOCX、PDF、TXT、MD<br />
                最大文件大小：10MB
              </p>
            </a-upload-dragger>

            <div class="file-list">
              <div v-if="store.docList.length === 0" class="empty-tip">暂无文件</div>
              <div
                v-for="doc in store.docList"
                :key="doc.uid"
                class="file-item"
              >
                <div class="file-info">
                  <file-text-outlined class="file-icon" />
                  <span class="file-name" :title="doc.name">{{ doc.name }}</span>
                  <span class="file-size">{{ formatSize(doc.size) }}</span>
                </div>
                <div class="file-status">
                  <a-tag v-if="doc.status === 'pending'" color="default">待处理</a-tag>
                  <a-tag v-else-if="doc.status === 'processing'" color="processing">处理中</a-tag>
                  <a-tag v-else-if="doc.status === 'done'" color="success">已处理</a-tag>
                  <a-tag v-else color="error">失败</a-tag>
                  <close-outlined class="remove-icon" @click="store.removeDoc(doc.uid)" />
                </div>
              </div>
            </div>

            <div class="action-bar">
              <a-button @click="store.clearDocList">清空列表</a-button>
              <a-button type="primary" :loading="processing" @click="handleProcess">
                上传处理
              </a-button>
            </div>

            <div class="stats-bar">
              <div class="stat-item">
                <div class="stat-value">{{ store.totalDocs }}</div>
                <div class="stat-label">文档数</div>
              </div>
              <div class="stat-item">
                <div class="stat-value">{{ formatSize(store.totalBytes) }}</div>
                <div class="stat-label">总大小</div>
              </div>
            </div>
          </a-card>
        </a-col>

        <!-- 右侧：文档智能问答 -->
        <a-col :xs="24" :lg="14">
          <a-card class="panel chat-panel" :bordered="false">
            <div class="panel-title">
              <message-outlined class="title-icon" />
              <span>文档智能问答</span>
            </div>
            <div class="panel-subtitle">
              基于RAG的智能问答系统，支持文档内容理解与分析
            </div>

            <div ref="chatRef" class="chat-list">
              <div
                v-for="msg in store.messages"
                :key="msg.id"
                :class="['message', msg.role]"
              >
                <div class="message-bubble">
                  <div class="message-content">{{ msg.content }}</div>
                </div>
                <div class="message-time">{{ msg.time }}</div>
              </div>
              <div v-if="store.generating" class="message assistant">
                <div class="message-bubble">
                  <a-spin size="small" />
                </div>
              </div>
            </div>

            <div class="chat-actions">
              <a-button size="small" @click="store.clearMessages">清空对话</a-button>
              <a-button size="small" @click="store.exportMessages">导出对话</a-button>
            </div>

            <div class="chat-input-area">
              <a-textarea
                v-model:value="question"
                :rows="3"
                placeholder="请针对已上传的文档提问..."
                @keydown.enter.prevent="handleSend"
              />
              <a-button
                type="primary"
                class="send-btn"
                :loading="store.generating"
                @click="handleSend"
              >
                发送
              </a-button>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <!-- 底部状态栏 -->
    <div class="footer-status">
      <div class="status-left">
        <check-circle-outlined class="status-icon" />
        <span>已处理文档：{{ store.totalDocs }} | 字符数：{{ store.totalChars }}</span>
      </div>
      <div class="status-right">
        <span>已索引片段：{{ store.totalChunks }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import {
  FolderOutlined,
  InboxOutlined,
  FileTextOutlined,
  CloseOutlined,
  MessageOutlined,
  CheckCircleOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useRagStore } from '@/stores/rag'
import { chatStream } from '@/api'
import type { UploadProps } from 'ant-design-vue'

const store = useRagStore()
const fileList = ref<any[]>([])
const question = ref('')
const processing = ref(false)
const chatRef = ref<HTMLElement>()

const rawFiles = new Map<string, File>()

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const f = file as unknown as File
  const maxSize = 10 * 1024 * 1024
  if (f.size > maxSize) {
    message.error(`${f.name} 超过 10MB 限制`)
    return false
  }
  const ext = f.name.slice(f.name.lastIndexOf('.')).toLowerCase()
  const allowed = ['.doc', '.docx', '.pdf', '.txt', '.md']
  if (!allowed.includes(ext)) {
    message.error(`${f.name} 格式不支持`)
    return false
  }
  return true
}

const customRequest: UploadProps['customRequest'] = (options) => {
  const { file, onSuccess } = options
  const f = file as unknown as File
  const doc = store.addPendingFile(f)
  rawFiles.set(doc.uid, f)
  onSuccess?.('ok')
}

async function handleProcess() {
  if (store.docList.length === 0) {
    message.warning('请先上传文件')
    return
  }
  processing.value = true
  try {
    const pending = store.docList.filter((d) => d.status === 'pending')
    if (pending.length === 0) {
      message.info('没有待处理的文件')
      return
    }
    await Promise.all(
      pending.map((doc) => {
        const file = rawFiles.get(doc.uid)
        if (!file) return Promise.resolve()
        return store.processFile(doc, file)
      })
    )
    message.success('文档处理完成')
  } finally {
    processing.value = false
  }
}

function handleSend() {
  const q = question.value.trim()
  if (!q) {
    message.warning('请输入问题')
    return
  }
  if (store.totalDocs === 0) {
    message.warning('请先上传并处理文档')
    return
  }
  store.addUserMessage(q)
  question.value = ''
  const assistantMsg = store.addAssistantPlaceholder()
  store.generating = true

  chatStream(
    q,
    (chunk) => {
      assistantMsg.content += chunk
      scrollToBottom()
    },
    () => {
      store.generating = false
      scrollToBottom()
    },
    (err) => {
      store.generating = false
      assistantMsg.content += `\n[请求失败：${err}]`
      message.error(err)
      scrollToBottom()
    }
  )
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(
  () => store.messages.length,
  () => scrollToBottom(),
  { flush: 'post' }
)

function formatSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
/* ============ 布局骨架 ============ */
.page {
  min-height: 100vh;
  background: linear-gradient(
    135deg,
    var(--color-bg-gradient-start) 0%,
    var(--color-bg-gradient-end) 100%
  );
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.container {
  flex: 1;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}

/* ============ 面板 ============ */
.panel {
  background: var(--color-panel-bg);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-panel);
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  padding: var(--space-6);
  overflow: hidden;
}

/* Ant Card 会把插槽内容包在 .ant-card-body 内，必须让它也参与 flex 列布局，
   内部子元素（文件列表 flex:1、底部操作栏）才能正确分布。 */
.panel :deep(.ant-card-body) {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0 !important;
}

.chat-panel {
  padding-bottom: 0;
}

.panel-title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  color: var(--color-title);
  display: flex;
  align-items: center;
  margin-bottom: var(--space-2);
}

.title-icon {
  color: var(--color-warning);
  margin-right: var(--space-2);
  font-size: var(--icon-size-lg);
}

.panel-subtitle {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-regular);
  line-height: var(--line-height-base);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-4);
}

/* ============ 上传区 ============ */
.upload-area {
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.upload-area :deep(.ant-upload-drag) {
  border-radius: var(--radius-md);
  border: 2px dashed var(--color-accent-border);
  background: var(--color-surface-subtle);
  transition: border-color var(--transition-base), background-color var(--transition-base);
}

.upload-area :deep(.ant-upload-drag:hover),
.upload-area :deep(.ant-upload-drag.ant-upload-drag-hover) {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
}

/* ============ 文件列表 ============ */
.file-list {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  margin-top: var(--space-4);
}

.empty-tip {
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-md);
  padding: var(--space-8) 0;
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3);
  background: var(--color-surface-subtle);
  border-radius: var(--radius-sm);
  margin-bottom: var(--space-2);
  transition: background-color var(--transition-base), box-shadow var(--transition-base),
    transform var(--transition-base);
}

.file-item:hover {
  background: var(--color-accent-soft);
  box-shadow: var(--shadow-hover);
}

.file-item:active {
  transform: scale(0.995);
}

.file-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  overflow: hidden;
}

.file-icon {
  color: var(--color-accent);
  font-size: var(--icon-size-sm);
  flex-shrink: 0;
}

.file-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.file-size {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

.file-status {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.remove-icon {
  color: var(--color-text-tertiary);
  cursor: pointer;
  font-size: var(--icon-size-sm);
  transition: color var(--transition-base), transform var(--transition-base);
}

.remove-icon:hover {
  color: var(--color-danger);
  transform: scale(1.15);
}

.remove-icon:active {
  transform: scale(0.9);
}

/* ============ 操作栏 / 统计栏 ============ */
.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.stats-bar {
  display: flex;
  justify-content: space-around;
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-tight);
  color: var(--color-accent);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-top: var(--space-1);
}

/* ============ 聊天区 ============ */
.chat-list {
  flex: 1;
  min-height: 160px;
  overflow-y: auto;
  background: var(--color-surface-subtle);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  margin-bottom: var(--space-3);
}

.message {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-4);
}

.message.user {
  align-items: flex-end;
}

.message.assistant {
  align-items: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  line-height: var(--line-height-base);
  font-size: var(--font-size-base);
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .message-bubble {
  background: var(--color-accent);
  color: #fff;
  border-bottom-right-radius: var(--space-1);
}

.message.assistant .message-bubble {
  background: #fff;
  color: var(--color-title);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--space-1);
}

.message-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--space-1);
}

.chat-actions {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.chat-input-area {
  display: flex;
  gap: var(--space-3);
  align-items: stretch;
}

.chat-input-area :deep(.ant-input) {
  border-radius: var(--radius-md);
  resize: none;
  transition: border-color var(--transition-base), box-shadow var(--transition-base);
}

.chat-input-area :deep(.ant-input:focus),
.chat-input-area :deep(.ant-input-focused) {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
}

.send-btn {
  border-radius: var(--radius-md);
  width: 80px;
  height: auto;
  background: var(--color-success);
  border-color: var(--color-success);
  transition: background-color var(--transition-base), border-color var(--transition-base),
    transform var(--transition-base);
}

.send-btn:hover {
  background: var(--color-success-strong);
  border-color: var(--color-success-strong);
}

.send-btn:active {
  transform: scale(0.97);
}

/* ============ 按钮通用交互反馈 ============ */
:deep(.ant-btn) {
  transition: color var(--transition-base), background-color var(--transition-base),
    border-color var(--transition-base), transform var(--transition-base),
    box-shadow var(--transition-base);
}

:deep(.ant-btn:not(.ant-btn-primary)):hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

:deep(.ant-btn:focus-visible) {
  outline: 2px solid var(--color-accent);
  outline-offset: 1px;
}

:deep(.ant-btn:active) {
  transform: scale(0.98);
}

/* ============ 底部状态栏 ============ */
.footer-status {
  max-width: 1400px;
  width: 100%;
  margin: var(--space-4) auto 0;
  background: rgba(31, 41, 55, 0.85);
  color: #fff;
  border-radius: var(--radius-sm);
  padding: var(--space-3) var(--space-4);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-md);
  backdrop-filter: blur(4px);
}

.status-left,
.status-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.status-icon {
  font-size: var(--icon-size-sm);
}

/* ============ 响应式适配 ============ */
@media (max-width: 991px) {
  .panel {
    height: auto;
    min-height: 50vh;
  }
  .file-list {
    max-height: 40vh;
  }
  .chat-list {
    max-height: 50vh;
  }
}

@media (max-width: 768px) {
  .page {
    padding: var(--space-4);
  }
  .panel {
    padding: var(--space-4);
    border-radius: var(--radius-md);
  }
  .footer-status {
    font-size: var(--font-size-sm);
    padding: var(--space-2) var(--space-4);
  }
  .stat-value {
    font-size: var(--font-size-lg);
  }
  .message-bubble {
    max-width: 88%;
  }
}

@media (max-width: 480px) {
  .status-right {
    display: none;
  }
  .send-btn {
    width: 64px;
  }
}
</style>
