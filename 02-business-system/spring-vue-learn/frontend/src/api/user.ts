import axios from 'axios'
import type { User } from '@/types/user'

// 统一封装 /api 请求（baseURL 通过 vite proxy 转发到后端 8080）
const api = axios.create({
  baseURL: '/api',
})

export function listUsers() {
  return api.get<User[]>('/users').then((res) => res.data)
}

export function createUser(payload: { username: string; email: string }) {
  return api.post<User>('/users', payload).then((res) => res.data)
}
