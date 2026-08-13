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
                  <file-text-outlined />
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
        <check-circle-outlined />
        <span>已处理文档：{{ store.totalDocs }} | 字符数：{{ store.totalChars }}</span>
      </div>
      <div class="status-right">
        <span>已处理文档：{{ store.totalDocs }} | 字符数：{{ store.totalChars }}</span>
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
.page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.container {
  flex: 1;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}

.panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.chat-panel {
  padding-bottom: 0;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.title-icon {
  color: #fbbf24;
  margin-right: 8px;
  font-size: 20px;
}

.panel-subtitle {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
}

.upload-area {
  border-radius: 12px;
  background: #f8fafc;
}

.upload-area :deep(.ant-upload-drag) {
  border-radius: 12px;
  border: 2px dashed #c7d2fe;
  background: #f8fafc;
}

.file-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 16px;
}

.empty-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 24px 0;
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 8px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.file-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #374151;
}

.file-size {
  color: #9ca3af;
  font-size: 12px;
}

.file-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.remove-icon {
  color: #9ca3af;
  cursor: pointer;
  font-size: 12px;
}

.remove-icon:hover {
  color: #ef4444;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.stats-bar {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #3b82f6;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  background: #f8fafc;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}

.message {
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}

.message.user {
  align-items: flex-end;
}

.message.assistant {
  align-items: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .message-bubble {
  background: #3b82f6;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-bubble {
  background: #fff;
  color: #1f2937;
  border: 1px solid #e5e7eb;
  border-bottom-left-radius: 4px;
}

.message-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
}

.chat-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.chat-input-area {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.chat-input-area :deep(.ant-input) {
  border-radius: 12px;
  resize: none;
}

.send-btn {
  border-radius: 12px;
  width: 80px;
  height: auto;
  background: #22c55e;
  border-color: #22c55e;
}

.send-btn:hover {
  background: #16a34a;
  border-color: #16a34a;
}

.footer-status {
  max-width: 1400px;
  width: 100%;
  margin: 16px auto 0;
  background: rgba(31, 41, 55, 0.85);
  color: #fff;
  border-radius: 8px;
  padding: 10px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
