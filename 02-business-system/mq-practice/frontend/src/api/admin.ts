import { get, post } from '@/api/request'
import type {
  AdminBinding,
  AdminConnection,
  AdminConsumer,
  AdminExchange,
  AdminQueue,
  AdminStatus,
  PeekedMessage,
  PublishResult
} from '@/types'

/**
 * RabbitMQ 运维台接口。
 *
 * 约定：所有读写都打在本项目后端 `/api/mq/admin/**`，由后端持 RabbitMQ 凭据转发，
 * 浏览器永远接触不到 15672 的账号密码。写操作额外带 X-MQ-Admin-Token。
 */

/** 运维口令存在 sessionStorage：关掉标签页就失效，避免长期留在磁盘上 */
const TOKEN_KEY = 'mq_admin_token'

export function getAdminToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function setAdminToken(token: string) {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

const writeHeaders = (): Record<string, string> => ({ 'X-MQ-Admin-Token': getAdminToken() })

export const fetchAdminStatus = () => get<AdminStatus>('/mq/admin/status')

export const fetchAdminQueues = () => get<AdminQueue[]>('/mq/admin/queues')

export const fetchAdminExchanges = () => get<AdminExchange[]>('/mq/admin/exchanges')

export const fetchAdminBindings = () => get<AdminBinding[]>('/mq/admin/bindings')

export const fetchAdminConsumers = () => get<AdminConsumer[]>('/mq/admin/consumers')

export const fetchAdminConnections = () => get<AdminConnection[]>('/mq/admin/connections')

/** 只读查看队列消息（后端用 ack_requeue_true，读完放回） */
export const peekQueue = (queue: string, count = 10) =>
  post<PeekedMessage[]>(`/mq/admin/queues/${encodeURIComponent(queue)}/peek`, undefined, { count })

/** 清空队列（写操作） */
export const purgeQueue = (queue: string) =>
  post<number>(`/mq/admin/queues/${encodeURIComponent(queue)}/purge`, undefined, undefined, writeHeaders())

/** 投递测试消息（写操作） */
export const publishMessage = (payload: {
  exchange: string
  routingKey: string
  payload: string
  messageType?: string
  source?: string
}) => post<PublishResult>('/mq/admin/publish', payload, undefined, writeHeaders())
