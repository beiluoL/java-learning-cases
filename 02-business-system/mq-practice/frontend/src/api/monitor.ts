import { get, post } from '@/api/request'
import type {
  ChaosConfig,
  DlqMessage,
  IdempotentLog,
  MessageLog,
  MqOverview,
  PageResult,
  QueueInfoView
} from '@/types'

/** 总览 */
export const fetchOverview = () => get<MqOverview>('/mq/overview')

/** 队列 */
export const fetchQueues = () => get<QueueInfoView[]>('/mq/queues')

/** 消息账本 */
export const fetchMessages = (params: {
  scene?: string
  status?: string
  page: number
  size: number
}) => get<PageResult<MessageLog>>('/mq/messages', { ...params })

/** 幂等记录 */
export const fetchIdempotentLogs = (params: { scene?: string; page: number; size: number }) =>
  get<PageResult<IdempotentLog>>('/mq/idempotent', { ...params })

/** 死信列表（只读） */
export const fetchDlq = (limit = 10) => get<DlqMessage[]>('/mq/dlq', { limit })

/** 死信回放 */
export const replayDlq = () => post<number>('/mq/dlq/replay')

/** 故障注入状态 */
export const fetchChaos = () => get<Record<string, ChaosConfig>>('/mq/chaos')

/** 设置故障注入 */
export const setChaos = (scene: string, enabled: boolean, failRate: number) =>
  post<Record<string, ChaosConfig>>('/mq/chaos', undefined, { scene, enabled, failRate })
