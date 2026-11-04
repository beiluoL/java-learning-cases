import { get, post } from '@/api/request'
import type { ProductChangeRequest, ProductChangeResult, ProductView } from '@/types'

/** 商品列表 */
export const fetchProducts = () => get<ProductView[]>('/products')

/** 商品三视角快照 */
export const fetchProductSnapshot = (id: number) => get<ProductView>(`/products/${id}`)

/** 变更商品并发布事件 */
export const changeProduct = (id: number, payload: ProductChangeRequest) =>
  post<ProductChangeResult>(`/products/${id}/change`, payload)
