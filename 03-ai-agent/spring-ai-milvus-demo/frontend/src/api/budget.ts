import { http } from './request'
import type { BudgetPolicy, BudgetStatus } from './types'

/** 新增 / 修改的请求体：策略字段去掉 id（PUT 时 id 走路径参数） */
export type BudgetPayload = Omit<BudgetPolicy, 'id'>

export const budgetApi = {
  list: () => http.get<BudgetPolicy[]>('/api/admin/ai/budgets'),

  detail: (id: string) => http.get<BudgetPolicy>(`/api/admin/ai/budgets/${encodeURIComponent(id)}`),

  status: () => http.get<BudgetStatus[]>('/api/admin/ai/budgets/status'),

  create: (payload: BudgetPayload) => http.post<BudgetPolicy>('/api/admin/ai/budgets', payload),

  update: (id: string, payload: BudgetPayload) =>
    http.put<BudgetPolicy>(`/api/admin/ai/budgets/${encodeURIComponent(id)}`, payload),
}
