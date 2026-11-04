import { get, post, put, del } from '@/utils/request'
import type { R } from '@/types'

// ============ 场景1 缓存加速 ============
export const cacheGet = (id: number) => get<any>(`/cache/product/${id}`)
export const cacheUpdate = (id: number, name: string, price: number) =>
  put<any>('/cache/product/' + id, { name, price })
export const cacheDelete = (id: number) => del<any>(`/cache/product/${id}`)
export const cacheDemo = () => get<string>('/cache/demo')

// ============ 场景2 分布式会话 ============
export const sessionLogin = (username: string, password: string) =>
  post<string>('/session/login', { username, password })
export const sessionMe = () => get<any>('/session/me')
export const sessionLogout = () => post<any>('/session/logout')
export const sessionRaw = () => get<any>('/session/raw')

// ============ 场景3 排行榜 ============
export const rankAdd = (member: string, score: number) =>
  post<any>('/rank/add', { member, score })
export const rankTop = (n = 10) => get<any>('/rank/top', { n })
export const rankTopTotal = (n = 10) => get<any>('/rank/top/total', { n })
export const rankOf = (member: string) => get<any>(`/rank/rank/${member}`)

// ============ 场景4 限流 ============
export const ratelimitOrder = () => get<string>('/ratelimit/order')
export const ratelimitProbe = () => get<string>('/ratelimit/probe')
export const ratelimitFixed = (key = 'demo') => get<any>('/ratelimit/fixed', { key })
export const ratelimitToken = (key = 'demo') => get<any>('/ratelimit/token', { key })

// ============ 场景5 发布订阅 ============
export const pubsubPublish = (from: string, text: string) =>
  post<any>('/pubsub/publish', { from, text })
export const pubsubConsume = () => get<any>('/pubsub/consume')

// ============ 场景6 缓存穿透 ============
export const penetrationGet = (id: number) => get<any>(`/penetration/product/${id}`)
export const penetrationAttack = (count = 20) => post<any>('/penetration/attack', { count })

// ============ 场景7 缓存击穿 ============
export const breakdownMutex = (id: number) => get<any>(`/breakdown/mutex/${id}`)
export const breakdownLogic = (id: number) => get<any>(`/breakdown/logic/${id}`)
export const breakdownExpireMutex = (id: number) => post<any>(`/breakdown/expire/mutex/${id}`)
export const breakdownExpireLogic = (id: number) => post<any>(`/breakdown/expire/logic/${id}`)

// ============ 场景8 缓存雪崩 ============
export const avalancheGet = (id: number, protect = true) =>
  get<any>(`/avalanche/product/${id}`, { protect })
export const avalanchePreheat = (protect: boolean) => post<any>('/avalanche/preheat', { protect })
export const avalancheStress = (times: number, protect: boolean) =>
  post<any>('/avalanche/stress', { times, protect })
export const avalancheRedisDown = (down: boolean) => post<any>('/avalanche/redis-down', { down })

// ============ 场景9/10 分布式锁 ============
export const lockDeduct = (productId = 1, impl = 'redis') =>
  post<any>('/lock/deduct', { productId, impl })
export const lockReset = (productId = 1, count = 100) =>
  post<any>('/lock/reset', { productId, count })

// ============ 调试/观测 ============
export const debugKeys = (pattern = '*') => get<string[]>('/debug/keys', { pattern })
export const debugGet = (key: string) => get<any>('/debug/get', { key })
export const debugStats = () => get<Record<string, number>>('/debug/stats')
export const debugReset = () => post<any>('/debug/reset')
