import { reactive, ref, shallowRef } from 'vue'
import { ApiError } from '@/api/request'

export function toErrorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return '未知错误，请稍后重试'
}

/**
 * 统一的异步数据容器：加载态 / 错误态 / 重试。
 * 用请求序号防止旧响应覆盖新请求（时间筛选快速切换时的竞态）。
 */
export function useAsyncData<T>(fetcher: () => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const errorTraceId = ref('')
  let seq = 0

  async function execute(): Promise<void> {
    const current = ++seq
    loading.value = true
    error.value = null
    errorTraceId.value = ''
    try {
      const result = await fetcher()
      if (current === seq) data.value = result
    } catch (err) {
      if (current === seq) {
        error.value = toErrorMessage(err)
        errorTraceId.value = err instanceof ApiError ? err.traceId : ''
      }
    } finally {
      if (current === seq) loading.value = false
    }
  }

  return { data, loading, error, errorTraceId, execute }
}

/**
 * 页面推荐入口：把 ref 容器交给 reactive 自动解包，
 * 模板里可直接写 resource.loading / resource.data，无需 .value。
 */
export function useResource<T>(fetcher: () => Promise<T>) {
  return reactive(useAsyncData(fetcher))
}

/** 统一空态判定：非加载、无错误、数据为空 */
export function isEmptyOf(resource: {
  loading: boolean
  error: string | null
  data: unknown
}): boolean {
  return !resource.loading && !resource.error && isEmpty(resource.data)
}

/** 数组/对象是否为空（用于空数据态判定） */
export function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined) return true
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value as object).length === 0
  return false
}
