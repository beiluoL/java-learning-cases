import { get, post } from '@/api/request'
import type { ProductView, SeckillRequest, SeckillResponse, StressTestResult } from '@/types'

/** 秒杀商品列表 */
export const fetchSeckillProducts = () => get<ProductView[]>('/seckill/products')

/** 提交秒杀订单（立即返回排队凭证） */
export const submitSeckillOrder = (payload: SeckillRequest) =>
  post<SeckillResponse>('/seckill/orders', payload)

/** 并发压测 */
export const runStressTest = (productId: number, quantity = 1, concurrency = 50) =>
  post<StressTestResult>('/seckill/stress', undefined, { productId, quantity, concurrency })

/** 重置演示库存 */
export const resetStock = (productId: number) =>
  post<void>('/seckill/stock/reset', undefined, { productId })
