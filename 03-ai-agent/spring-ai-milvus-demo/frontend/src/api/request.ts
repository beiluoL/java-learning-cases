import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import type { ApiEnvelope } from './types'

/** 可展示的业务错误：保留后端 code 与 traceId，便于错误态与排障 */
export class ApiError extends Error {
  readonly code: number
  readonly traceId: string

  constructor(message: string, code = -1, traceId = '') {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

/** code 对应的兜底文案（仅当后端 message 为空时使用） */
const ERROR_HINTS: Record<number, string> = {
  40001: '参数校验失败',
  40301: '预算不足，请求已被拦截',
  40401: '资源不存在',
  40901: '重复请求',
  50001: '系统异常',
  50301: 'Redis 不可用（安全失败）',
}

const instance: AxiosInstance = axios.create({
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

/** 最近一次请求的 traceId（含失败请求），统一留痕 */
let lastTraceId = ''
export function getLastTraceId(): string {
  return lastTraceId
}

/** 把 HTTP 层 / 网络层异常统一归一化为 ApiError */
instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiEnvelope<unknown>>) => {
    const payload = error.response?.data
    if (payload && typeof payload.code === 'number') {
      lastTraceId = payload.traceId || ''
      return Promise.reject(
        new ApiError(
          payload.message || ERROR_HINTS[payload.code] || `请求失败（code=${payload.code}）`,
          payload.code,
          payload.traceId || '',
        ),
      )
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError('请求超时，请稍后重试'))
    }
    if (!error.response) {
      return Promise.reject(new ApiError('无法连接后端服务，请确认 http://localhost:8091 已启动'))
    }
    return Promise.reject(new ApiError(`服务异常（HTTP ${error.response.status}）`, error.response.status))
  },
)

/**
 * 统一入口：解包 {code,message,data,traceId}。
 * code !== 0 抛 ApiError（message 可直接展示），成功只返回 data。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  let response: AxiosResponse<ApiEnvelope<T>>
  try {
    response = await instance.request<ApiEnvelope<T>>(config)
  } catch (err) {
    if (err instanceof ApiError) throw err
    throw new ApiError(err instanceof Error ? err.message : '网络请求失败')
  }

  const envelope = response.data
  if (!envelope || typeof envelope !== 'object' || typeof envelope.code !== 'number') {
    throw new ApiError('后端响应不符合契约（缺少 code 字段）')
  }
  lastTraceId = envelope.traceId || lastTraceId

  if (envelope.code !== 0) {
    throw new ApiError(
      envelope.message || ERROR_HINTS[envelope.code] || `请求失败（code=${envelope.code}）`,
      envelope.code,
      envelope.traceId || '',
    )
  }
  return envelope.data
}

/** 路径统一书写契约中的 /api/... 前缀，由 Vite 代理转发到 8091 */
export const http = {
  get<T>(url: string, params?: AxiosRequestConfig['params']): Promise<T> {
    return request<T>({ method: 'GET', url, params })
  },
  post<T>(url: string, data?: unknown): Promise<T> {
    return request<T>({ method: 'POST', url, data })
  },
  put<T>(url: string, data?: unknown): Promise<T> {
    return request<T>({ method: 'PUT', url, data })
  },
}
