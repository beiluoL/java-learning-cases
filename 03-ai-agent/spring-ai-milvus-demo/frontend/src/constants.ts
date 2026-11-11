/**
 * 纯 UI 常量：以下取值全部来自 AI_COST_API.md 契约中明确列出的枚举，
 * 或前端展示所需的配色/文案映射。它们不是统计数据，绝不会进入统计卡片与图表。
 */

export type BadgeTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'primary' | 'demo'

/** usageStatus 契约枚举 */
export const USAGE_STATUS_OPTIONS = [
  { value: 'REAL', label: 'REAL · 供应商真实返回' },
  { value: 'ESTIMATED', label: 'ESTIMATED · 估算' },
  { value: 'UNAVAILABLE', label: 'UNAVAILABLE · 供应商未提供' },
  { value: 'LOCAL', label: 'LOCAL · 本地模型' },
  { value: 'MOCK', label: 'MOCK · 模拟调用（不计入真实账单）' },
]

/** budgetType 契约枚举 */
export const BUDGET_TYPE_OPTIONS = [
  { value: 'GLOBAL', label: 'GLOBAL · 全局' },
  { value: 'USER', label: 'USER · 用户' },
  { value: 'MODEL', label: 'MODEL · 模型' },
  { value: 'DAILY', label: 'DAILY · 每日' },
  { value: 'MONTHLY', label: 'MONTHLY · 每月' },
]

/** modelType 契约枚举 */
export const MODEL_TYPE_OPTIONS = [
  { value: 'CHAT', label: 'CHAT' },
  { value: 'EMBEDDING', label: 'EMBEDDING' },
  { value: 'RERANK', label: 'RERANK' },
]

/** trend 接口的 granularity 契约枚举 */
export const GRANULARITY_OPTIONS = [
  { value: 'HOUR', label: '按小时' },
  { value: 'DAY', label: '按天' },
  { value: 'MONTH', label: '按月' },
]

/** 分页大小（契约：最大 100） */
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

/** 契约：初版价格统一 USD */
export const DEFAULT_CURRENCY = 'USD'

export function usageStatusTone(status: string): BadgeTone {
  switch (status) {
    case 'REAL': return 'success'
    case 'ESTIMATED': return 'warning'
    case 'LOCAL': return 'info'
    case 'MOCK': return 'demo'
    default: return 'neutral'
  }
}

/** requestStatus / severity 契约未给枚举，按常见语义做展示映射（不改变字段值） */
export function requestStatusTone(status: string): BadgeTone {
  const s = (status || '').toUpperCase()
  if (/SUCCESS|OK|SUCCEEDED/.test(s)) return 'success'
  if (/FAIL|ERROR|TIMEOUT|EXCEPTION/.test(s)) return 'danger'
  if (/REJECT|BLOCK|LIMIT|OVERRUN/.test(s)) return 'warning'
  return 'neutral'
}

export function severityTone(severity: string): BadgeTone {
  const s = (severity || '').toUpperCase()
  if (/CRIT|HIGH|FATAL|ERROR/.test(s)) return 'danger'
  if (/WARN|MEDIUM/.test(s)) return 'warning'
  if (/INFO|LOW/.test(s)) return 'info'
  return 'neutral'
}

/* ==========================================================================
   成本治理平台（V2）枚举 —— 与后端 AiEnums 一一对应
   ========================================================================== */

/** AlertType：后端 AiEnums.AlertType 全量清单 */
export const ALERT_TYPE_OPTIONS = [
  { value: 'BUDGET_THRESHOLD', label: 'BUDGET_THRESHOLD · 预算阈值越线' },
  { value: 'BUDGET_EXCEEDED', label: 'BUDGET_EXCEEDED · 预算不足被拒' },
  { value: 'FORECAST_EXHAUST', label: 'FORECAST_EXHAUST · 预测将超支' },
  { value: 'SPEND_VELOCITY', label: 'SPEND_VELOCITY · 消费速度突增' },
  { value: 'SETTLE_OVERRUN', label: 'SETTLE_OVERRUN · 实际高于预扣' },
  { value: 'UNKNOWN_USAGE', label: 'UNKNOWN_USAGE · Usage 未知' },
  { value: 'REDIS_FAILURE', label: 'REDIS_FAILURE · Redis 异常' },
  { value: 'RECONCILE_DIFF', label: 'RECONCILE_DIFF · 对账差异' },
  { value: 'COST_SPIKE', label: 'COST_SPIKE · 成本突增' },
  { value: 'ANOMALY_USER_SPIKE', label: 'ANOMALY_USER_SPIKE · 单用户成本突增' },
  { value: 'ANOMALY_TOKEN_SPIKE', label: 'ANOMALY_TOKEN_SPIKE · 单次 Token 突增' },
  { value: 'ANOMALY_ERROR_RATE', label: 'ANOMALY_ERROR_RATE · 错误率突升' },
  { value: 'ANOMALY_CACHE_HIT_DROP', label: 'ANOMALY_CACHE_HIT_DROP · 缓存命中率下降' },
  { value: 'ANOMALY_NIGHT_CALL', label: 'ANOMALY_NIGHT_CALL · 夜间异常调用' },
  { value: 'ANOMALY_AGENT_LOOP', label: 'ANOMALY_AGENT_LOOP · Agent 循环' },
  { value: 'ANOMALY_RETRY', label: 'ANOMALY_RETRY · 重试异常' },
  { value: 'PROTECTION_ACTION', label: 'PROTECTION_ACTION · 保护动作' },
  { value: 'RATE_LIMITED', label: 'RATE_LIMITED · 触发限流' },
  { value: 'CIRCUIT_OPEN', label: 'CIRCUIT_OPEN · 熔断打开' },
]

/** AlertLevel：等级越高动作越强 */
export const ALERT_LEVEL_OPTIONS = [
  { value: 'INFO', label: 'INFO · 观察' },
  { value: 'WARNING', label: 'WARNING · 提醒' },
  { value: 'HIGH', label: 'HIGH · 高危' },
  { value: 'CRITICAL', label: 'CRITICAL · 严重' },
  { value: 'BLOCKED', label: 'BLOCKED · 阻断' },
]

/** AlertStatus：OPEN → ACKNOWLEDGED → RESOLVED */
export const ALERT_STATUS_OPTIONS = [
  { value: 'OPEN', label: 'OPEN · 未处理' },
  { value: 'ACKNOWLEDGED', label: 'ACKNOWLEDGED · 已确认' },
  { value: 'RESOLVED', label: 'RESOLVED · 已恢复' },
]

/** 告警对象类型（后端写入的 targetType 取值） */
export const ALERT_TARGET_TYPE_OPTIONS = [
  { value: 'BUDGET', label: 'BUDGET · 预算作用域' },
  { value: 'USER', label: 'USER · 用户' },
  { value: 'GLOBAL', label: 'GLOBAL · 全局' },
  { value: 'AGENT', label: 'AGENT · Agent 任务' },
]

/** 保护等级 → 语义文案（后端 ProtectionLevel 1~5） */
export const PROTECTION_LEVELS = [
  { code: 1, name: 'NORMAL', label: '正常放行', desc: '不干预，只记录成本' },
  { code: 2, name: 'WARNING', label: '成本警告', desc: '仅通知与记录，请求照常放行' },
  { code: 3, name: 'LIMIT', label: '优先低成本', desc: '优先走 L1 低成本模型，仍放行' },
  { code: 4, name: 'SEVERE', label: '强制降级', desc: '强制低成本模型，并暂停 Agent' },
  { code: 5, name: 'BLOCKED', label: '阻断付费调用', desc: '拒绝一切新的付费调用' },
]

/** 保护动作（后端 rule.action 取值） */
export const PROTECTION_ACTION_OPTIONS = [
  { value: 'NOTIFY', label: 'NOTIFY · 仅通知' },
  { value: 'DOWNGRADE_MODEL', label: 'DOWNGRADE_MODEL · 降级模型' },
  { value: 'DISABLE_AGENT', label: 'DISABLE_AGENT · 暂停 Agent' },
  { value: 'BLOCK_REQUEST', label: 'BLOCK_REQUEST · 阻断请求' },
]

export function alertLevelTone(level: string): BadgeTone {
  switch ((level || '').toUpperCase()) {
    case 'BLOCKED': return 'danger'
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'WARNING': return 'warning'
    case 'INFO': return 'info'
    default: return 'neutral'
  }
}

export function alertStatusTone(status: string): BadgeTone {
  switch ((status || '').toUpperCase()) {
    case 'OPEN': return 'danger'
    case 'ACKNOWLEDGED': return 'warning'
    case 'RESOLVED': return 'success'
    default: return 'neutral'
  }
}

export function protectionLevelTone(level: number): BadgeTone {
  if (level >= 5) return 'danger'
  if (level >= 4) return 'danger'
  if (level >= 3) return 'warning'
  if (level >= 2) return 'warning'
  return 'success'
}

/** 收益口径 → 徽标色调 */
export function measureKindTone(kind: string): BadgeTone {
  switch ((kind || '').toUpperCase()) {
    case 'MEASURED': return 'success'
    case 'COUNTERFACTUAL': return 'info'
    case 'ESTIMATED': return 'warning'
    default: return 'neutral'
  }
}

/** 成本口径 → 徽标色调 */
export function costKindTone(kind: string): BadgeTone {
  switch ((kind || '').toUpperCase()) {
    case 'ACTUAL': return 'primary'
    case 'ALLOCATED': return 'info'
    case 'SAVED': return 'success'
    default: return 'neutral'
  }
}
