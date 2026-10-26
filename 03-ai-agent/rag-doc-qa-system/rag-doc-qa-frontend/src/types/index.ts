export interface DocFile {
  uid: string
  name: string
  size: number
  status: 'pending' | 'processing' | 'done' | 'error'
  chunkCount?: number
  charCount?: number
  response?: UploadResponse
}

export interface UploadResponse {
  code: number
  message: string
  data?: {
    docName: string
    chunkCount: number
    charCount: number
  }
}

export interface ChatMessage {
  id: string
  role: 'assistant' | 'user'
  content: string
  time: string
}
