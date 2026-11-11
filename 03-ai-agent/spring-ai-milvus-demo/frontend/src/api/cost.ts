import { http } from './request'
import type {
  AlertItem,
  CostOverview,
  ModelCost,
  SceneCost,
  TimeRange,
  TopItem,
  TrendPoint,
  UsageRecord,
} from './types'

export type Granularity = 'HOUR' | 'DAY' | 'MONTH'

/** 所有请求都携带 startTime / endTime，时间筛选变化时每个接口都会重新取数 */
export const costApi = {
  overview: (r: TimeRange) =>
    http.get<CostOverview>('/api/admin/ai/cost/overview', { ...r }),

  trend: (r: TimeRange, granularity: Granularity = 'DAY') =>
    http.get<TrendPoint[]>('/api/admin/ai/cost/trend', { ...r, granularity }),

  models: (r: TimeRange) =>
    http.get<ModelCost[]>('/api/admin/ai/cost/models', { ...r }),

  scenes: (r: TimeRange) =>
    http.get<SceneCost[]>('/api/admin/ai/cost/scenes', { ...r }),

  topModels: (r: TimeRange, limit = 10) =>
    http.get<TopItem[]>('/api/admin/ai/cost/top-models', { ...r, limit }),

  topUsers: (r: TimeRange, limit = 10) =>
    http.get<TopItem[]>('/api/admin/ai/cost/top-users', { ...r, limit }),

  recentRequests: (r: TimeRange, limit = 10) =>
    http.get<UsageRecord[]>('/api/admin/ai/cost/recent-requests', { ...r, limit }),

  alerts: (r: TimeRange, limit = 10) =>
    http.get<AlertItem[]>('/api/admin/ai/cost/alerts', { ...r, limit }),
}
