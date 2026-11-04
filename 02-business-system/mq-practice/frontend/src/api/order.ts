import { get, post } from '@/api/request'
import type { OrderView, PageResult, SeckillRequest, SeckillResponse } from '@/types'

export interface OrderQuery {
  status?: string
  keyword?: string
  userId?: number
  page: number
  size: number
}

/** 订单分页 */
export const fetchOrderPage = (query: OrderQuery) =>
  get<PageResult<OrderView>>('/orders', { ...query })

/** 订单详情（轮询排队结果） */
export const fetchOrderDetail = (orderNo: string) => get<OrderView>(`/orders/${orderNo}`)

/** 支付 */
export const payOrder = (orderNo: string) => post<OrderView>(`/orders/${orderNo}/pay`)

/** 取消 */
export const cancelOrder = (orderNo: string, reason = '用户主动取消') =>
  post<OrderView>(`/orders/${orderNo}/cancel`, undefined, { reason })

/** 创建演示订单（可指定超时秒数） */
export const createDemoOrder = (payload: SeckillRequest) =>
  post<SeckillResponse>('/orders/demo', payload)
