import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types'

/**
 * Axios 封装。
 *
 * 关键约定：<b>响应拦截器里把 Result<T> 拆包，业务代码直接拿到 T</b>。
 * 这样做的好处是调用处不需要写 `res.data.data`，也不用在每个地方判断 code。
 * 代价是请求函数的返回类型需要显式断言（见下方 get/post）。
 */
const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

instance.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error)
)

instance.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const body = response.data
    // 后端恒返回 HTTP 200 + 业务码，这里只认 code
    if (body && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data as never
      }
      ElMessage.error(body.message || `请求失败（code=${body.code}）`)
      return Promise.reject(new Error(body.message || 'business error'))
    }
    return response.data as never
  },
  (error) => {
    const status = error?.response?.status
    const message =
      error?.response?.data?.message ||
      (status ? `HTTP ${status}` : '网络异常，请确认后端已启动（http://localhost:8080）')
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/** GET，直接返回业务数据 */
export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return instance.get(url, { params }) as unknown as Promise<T>
}

/** POST，直接返回业务数据 */
export function post<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  return instance.post(url, data, { params }) as unknown as Promise<T>
}

export default instance
