import axios from 'axios'
import type { R } from '@/types'

// 统一请求实例：baseURL=/api，由 Vite 代理转发到后端 8080；
// withCredentials=true 才能在跨域时携带 Cookie（分布式会话的前提）
const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

// 响应拦截：直接返回后端 R 结构 { code, msg, data }
instance.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const msg = error?.response?.data?.msg || error.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// 封装 get/post/put/del，让调用方直接拿到 R<T>（data 即业务数据）
export function get<T>(url: string, params?: any): Promise<R<T>> {
  return instance.get(url, { params }) as unknown as Promise<R<T>>
}
export function post<T>(url: string, params?: any): Promise<R<T>> {
  return instance.post(url, null, { params }) as unknown as Promise<R<T>>
}
export function put<T>(url: string, params?: any): Promise<R<T>> {
  return instance.put(url, null, { params }) as unknown as Promise<R<T>>
}
export function del<T>(url: string, params?: any): Promise<R<T>> {
  return instance.delete(url, { params }) as unknown as Promise<R<T>>
}

export default instance
