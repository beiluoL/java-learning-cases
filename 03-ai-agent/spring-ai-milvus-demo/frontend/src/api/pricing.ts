import { http } from './request'
import type { Pricing } from './types'

export type PricingPayload = Omit<Pricing, 'id'>

export const pricingApi = {
  list: () => http.get<Pricing[]>('/api/admin/ai/pricing'),

  create: (payload: PricingPayload) => http.post<Pricing>('/api/admin/ai/pricing', payload),

  update: (id: string, payload: PricingPayload) =>
    http.put<Pricing>(`/api/admin/ai/pricing/${encodeURIComponent(id)}`, payload),

  history: (modelName: string) =>
    http.get<Pricing[]>('/api/admin/ai/pricing/history', { modelName }),
}
