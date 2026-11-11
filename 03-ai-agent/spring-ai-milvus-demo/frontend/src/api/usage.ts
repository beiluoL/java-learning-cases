import { http } from './request'
import type { PageData, UsageDetail, UsageQuery, UsageRecord } from './types'

export const usageApi = {
  /** 只序列化非空参数，避免把 undefined / 空串发给后端 */
  page: (q: UsageQuery) => {
    const params: Record<string, string | number> = {
      page: q.page,
      size: q.size,
      sort: q.sort,
    }
    if (q.userId) params.userId = q.userId
    if (q.modelName) params.modelName = q.modelName
    if (q.businessScene) params.businessScene = q.businessScene
    if (q.requestStatus) params.requestStatus = q.requestStatus
    if (q.usageStatus) params.usageStatus = q.usageStatus
    if (q.minCost) params.minCost = q.minCost
    if (q.maxCost) params.maxCost = q.maxCost
    if (q.startTime) params.startTime = q.startTime
    if (q.endTime) params.endTime = q.endTime
    return http.get<PageData<UsageRecord>>('/api/admin/ai/usage/page', params)
  },

  detail: (requestId: string) =>
    http.get<UsageDetail>(`/api/admin/ai/usage/${encodeURIComponent(requestId)}`),
}
