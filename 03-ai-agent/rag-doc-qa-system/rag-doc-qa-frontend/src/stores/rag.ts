import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { ChatMessage, DocFile } from '@/types'
import { uploadDocument } from '@/api'

export const useRagStore = defineStore('rag', () => {
  // 文档列表
  const docList = ref<DocFile[]>([])
  // 对话消息
  const messages = ref<ChatMessage[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '您好！我是文档智能助手。请先上传文档，然后就可以针对文档内容提问了。',
      time: formatTime(new Date())
    }
  ])
  // 是否正在生成回答
  const generating = ref(false)

  const totalDocs = computed(() => docList.value.filter((d) => d.status === 'done').length)
  const totalBytes = computed(() => docList.value.reduce((sum, d) => sum + d.size, 0))
  const totalChars = computed(() =>
    docList.value.reduce((sum, d) => sum + (d.charCount || 0), 0)
  )
  const totalChunks = computed(() =>
    docList.value.reduce((sum, d) => sum + (d.chunkCount || 0), 0)
  )

  function formatTime(date: Date): string {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  function genId(): string {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  }

  function addPendingFile(file: File): DocFile {
    const doc: DocFile = {
      uid: genId(),
      name: file.name,
      size: file.size,
      status: 'pending'
    }
    docList.value.push(doc)
    return doc
  }

  async function processFile(doc: DocFile, file: File) {
    doc.status = 'processing'
    try {
      const res = await uploadDocument(file)
      doc.response = res
      if (res.code === 200 && res.data) {
        doc.status = 'done'
        doc.chunkCount = res.data.chunkCount
        doc.charCount = res.data.charCount
      } else {
        doc.status = 'error'
      }
    } catch (e: any) {
      doc.status = 'error'
      doc.response = { code: 500, message: e.message || '上传失败' }
    }
  }

  function clearDocList() {
    docList.value = []
  }

  function removeDoc(uid: string) {
    const idx = docList.value.findIndex((d) => d.uid === uid)
    if (idx >= 0) {
      docList.value.splice(idx, 1)
    }
  }

  function addUserMessage(content: string) {
    messages.value.push({
      id: genId(),
      role: 'user',
      content,
      time: formatTime(new Date())
    })
  }

  function addAssistantPlaceholder(): ChatMessage {
    const msg: ChatMessage = {
      id: genId(),
      role: 'assistant',
      content: '',
      time: formatTime(new Date())
    }
    messages.value.push(msg)
    return msg
  }

  function appendToLastAssistant(chunk: string) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.content += chunk
    }
  }

  function clearMessages() {
    messages.value = [
      {
        id: 'welcome',
        role: 'assistant',
        content: '您好！我是文档智能助手。请先上传文档，然后就可以针对文档内容提问了。',
        time: formatTime(new Date())
      }
    ]
  }

  function exportMessages() {
    const text = messages.value
      .map((m) => `[${m.time}] ${m.role === 'user' ? '我' : '助手'}：${m.content}`)
      .join('\n\n')
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `对话记录_${new Date().toLocaleDateString('zh-CN')}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }

  return {
    docList,
    messages,
    generating,
    totalDocs,
    totalBytes,
    totalChars,
    totalChunks,
    addPendingFile,
    processFile,
    clearDocList,
    removeDoc,
    addUserMessage,
    addAssistantPlaceholder,
    appendToLastAssistant,
    clearMessages,
    exportMessages
  }
})
