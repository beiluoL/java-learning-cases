/* =====================================================================================
   接口契约类型定义
   —— 与后端 DTO 一一对应。后端字段调整时，先改这里，TS 会把所有受影响的位置报出来。
   ===================================================================================== */

/** 统一响应包装（后端 Result<T>） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
  success: boolean
}

/** 分页结果（后端 PageResult<T>） */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export type OrderStatus = 'QUEUING' | 'UNPAID' | 'PAID' | 'CANCELLED' | 'FAILED'

/** 商品视图：DB / Redis / 索引三视角 */
export interface ProductView {
  id: number
  productNo: string
  name: string
  price: number
  status: number
  statusText: string
  version: number
  totalStock: number | null
  availableStock: number | null
  redisStock: number | null
  cacheJson?: string | null
  cacheTtlSeconds?: number | null
  indexJson?: string | null
}

/** 秒杀受理结果 */
export interface SeckillResponse {
  orderNo: string
  status: OrderStatus | string
  message: string
  remainingStock: number | null
  costMs: number
  messageId: string
}

/** 秒杀下单入参 */
export interface SeckillRequest {
  productId: number
  quantity?: number
  userId?: number
  delaySeconds?: number
}

/** 订单视图 */
export interface OrderView {
  id: number
  orderNo: string
  userId: number
  productId: number
  productName: string
  quantity: number
  amount: number
  status: OrderStatus
  statusText: string
  createTime: string
  expireTime: string | null
  payTime: string | null
  cancelTime: string | null
  cancelReason: string | null
  delaySeconds: number
  remainingSeconds: number
}

/** 压测结果 */
export interface StressTestResult {
  total: number
  success: number
  soldOut: number
  failed: number
  durationMs: number
  avgCostMs: number
  stockBefore: number
  stockAfter: number
  successOrderNos: string[]
  conclusion: string
}

/** 商品变更 */
export type ProductChangeType = 'ONLINE' | 'OFFLINE' | 'PRICE_CHANGED' | 'INFO_UPDATED'

export interface ProductChangeRequest {
  changeType: ProductChangeType
  price?: number
  name?: string
  operator?: string
}

export interface ProductChangeResult {
  product: ProductView
  changeType: string
  version: number
  messageId: string
  message: string
}

/** 队列指标 */
export interface QueueInfoView {
  name: string
  messageCount: number
  consumerCount: number
  role: string
}

/** 总览 */
export interface MqOverview {
  queues: QueueInfoView[]
  totalSent: number
  confirmedCount: number
  returnedCount: number
  failedCount: number
  idempotentRecords: number
  orderQueuing: number
  orderUnpaid: number
  orderPaid: number
  orderCancelled: number
  orderFailed: number
  dlqCount: number
  idempotentEnabled: boolean
  delayMode: string
  brokerAvailable: boolean
  brokerMessage: string
  timestamp: number
}

/** 消息账本 */
export interface MessageLog {
  id: number
  messageId: string
  exchangeName: string
  routingKey: string
  scene: string
  bizKey: string
  payload: string
  status: 'SENDING' | 'CONFIRMED' | 'RETURNED' | 'FAILED'
  retryCount: number
  errorMsg: string | null
  costMs: number | null
  createTime: string
}

/** 幂等记录 */
export interface IdempotentLog {
  id: number
  bizKey: string
  scene: string
  messageId: string
  status: number
  payload: string
  createTime: string
}

/** 死信 */
export interface DlqMessage {
  index: number
  messageId: string
  originalExchange: string
  originalRoutingKey: string
  reason: string
  deathCount: number
  scene: string
  bizKey: string
  payload: string
}

/** 故障注入配置 */
export interface ChaosConfig {
  scene: string
  enabled: boolean
  failRate: number
}

/* =====================================================================================
   RabbitMQ 运维台（Broker 侧，经后端代理 Management API）
   ===================================================================================== */

/** 连接状态与集群概览 */
export interface AdminStatus {
  enabled: boolean
  ok: boolean
  baseUrl: string
  consoleUrl: string
  version: string
  clusterName: string
  erlangVersion: string
  vhost: string
  username: string
  writeEnabled: boolean
  queueCount: number
  exchangeCount: number
  consumerCount: number
  connectionCount: number
  totalMessages: number
  readyMessages: number
  unackedMessages: number
  message: string
}

/** 队列（Broker 视角） */
export interface AdminQueue {
  name: string
  vhost: string
  type: string
  state: string
  durable: boolean
  autoDelete: boolean
  exclusive: boolean
  messages: number
  ready: number
  unacked: number
  consumers: number
  publishRate: number
  deliverRate: number
  ackRate: number
  redeliverRate: number
  messageBytes: number
  arguments: Record<string, unknown>
  messageTtlMs: number | null
  managedByProject: boolean
}

export interface AdminExchange {
  name: string
  vhost: string
  type: string
  durable: boolean
  autoDelete: boolean
  internal: boolean
  publishInRate: number
  publishOutRate: number
  arguments: Record<string, unknown>
}

export interface AdminBinding {
  source: string
  destination: string
  destinationType: string
  routingKey: string
  arguments: Record<string, unknown>
}

export interface AdminConsumer {
  queue: string
  consumerTag: string
  ackRequired: boolean
  prefetchCount: number
  activityStatus: string
  exclusive: boolean
  channel: string
  peerHost: string
  connectionName: string
}

export interface AdminConnection {
  name: string
  user: string
  peerHost: string
  state: string
  channels: number
  recvRate: number
  sendRate: number
  protocol: string
  ssl: boolean
  clientInfo: string
}

/** 从队列里 peek 出来的一条消息（只读，读完放回） */
export interface PeekedMessage {
  index: number
  exchange: string
  routingKey: string
  redelivered: boolean
  messageCount: number
  payload: string
  payloadBytes: number
  contentType: string
  headers: Record<string, unknown>
}

export interface PublishResult {
  routed: boolean
  note: string
}
