/**
 * 全部类型严格按 docs/api/AI_COST_API.md 契约定义，禁止自行发明字段。
 * 约定：金额一律为字符串（BigDecimal 精度），Token 为整数，时间为 ISO 8601 UTC。
 */

/** 统一响应包装（由 request.ts 解包，业务层只拿到 data） */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

/** 分页响应固定结构 */
export interface PageData<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 所有统计接口共用的时间范围参数（ISO UTC） */
export interface TimeRange {
  startTime: string
  endTime: string
}

/** 比率类字段（costRatio / usageRate / successRate）后端可能给 number 或字符串 */
export type Ratio = number | string

/** ---------------- 二、成本分析 ---------------- */

export interface CostOverview {
  totalCost: string
  totalTokens: number
  inputTokens: number
  outputTokens: number
  cachedInputTokens: number
  requestCount: number
  avgCostPerRequest: string
  successRate: Ratio
  budgetUsageRate: Ratio
}

export interface TrendPoint {
  bucket: string
  totalCost: string
  inputTokens: number
  outputTokens: number
  requestCount: number
}

export interface ModelCost {
  provider: string
  modelName: string
  requestCount: number
  inputTokens: number
  outputTokens: number
  totalCost: string
  avgCost: string
  costRatio: Ratio
}

export interface SceneCost {
  businessScene: string
  requestCount: number
  totalTokens: number
  totalCost: string
  costRatio: Ratio
}

/** top-models / top-users：value 为成本字符串 */
export interface TopItem {
  name: string
  value: string
}

export interface AlertItem {
  id: string
  alertType: string
  budgetType: string
  message: string
  severity: string
  createdAt: string
}

/** usageStatus 取值（契约定义） */
export type UsageStatus = 'REAL' | 'ESTIMATED' | 'UNAVAILABLE' | 'LOCAL' | 'MOCK'

/** usage/page 的 record，以及 recent-requests 明细 */
export interface UsageRecord {
  requestId: string
  traceId: string
  userId: string
  conversationId: string
  businessScene: string
  provider: string
  modelName: string
  modelType: string
  inputTokens: number
  outputTokens: number
  cachedInputTokens: number
  totalTokens: number
  usageStatus: UsageStatus | string
  requestStatus: string
  latencyMs: number
  totalCost: string
  currency: string
  pricingVersion: string
  createdAt: string
}

/** usage/{requestId} 返回的成本计算过程 */
export interface CostBreakdown {
  pricingVersion: string
  inputPrice: string
  outputPrice: string
  cachedInputPrice: string
  normalInputTokens: number
  cachedInputTokens: number
  outputTokens: number
  inputCost: string
  cachedCost: string
  outputCost: string
  totalCost: string
}

/** 详情 = 明细 + costBreakdown + 预算预扣/结算状态（预算段字段以后端返回为准，按原样展示） */
export interface UsageDetail extends UsageRecord {
  costBreakdown?: CostBreakdown
  budget?: Record<string, unknown>
}

/** usage/page 查询参数 */
export interface UsageQuery {
  page: number
  size: number
  userId?: string
  modelName?: string
  businessScene?: string
  requestStatus?: string
  usageStatus?: string
  minCost?: string
  maxCost?: string
  startTime?: string
  endTime?: string
  sort: string
}

/** ---------------- 三、预算管理 ---------------- */

export type BudgetType = 'GLOBAL' | 'USER' | 'MODEL' | 'DAILY' | 'MONTHLY'

export interface BudgetPolicy {
  id: string
  budgetType: BudgetType | string
  targetId: string
  dailyLimit: string
  monthlyLimit: string
  perRequestLimit: string
  maxInputTokens: number
  maxOutputTokens: number
  allowOverrun: boolean
  enabled: boolean
  currency: string
  remark: string
}

export interface BudgetStatus {
  budgetType: string
  targetId: string
  limitAmount: string
  usedAmount: string
  reservedAmount: string
  remainingAmount: string
  usageRate: Ratio
}

/** ---------------- 四、模型价格管理 ---------------- */

export type ModelType = 'CHAT' | 'EMBEDDING' | 'RERANK'

export interface Pricing {
  id: string
  provider: string
  modelName: string
  modelType: ModelType | string
  inputPrice: string
  outputPrice: string
  cachedInputPrice: string
  currency: string
  version: string
  effectiveFrom: string
  effectiveTo: string
  enabled: boolean
  demoFlag: boolean
}

/* ==========================================================================
   五、成本治理平台（V2 契约）
   与 /api/admin/cost/**、/api/admin/cost-alert/** 等治理接口一一对应。
   口径约定（后端强制，前端不得混用）：
   - costKind = ACTUAL    供应商真实账单
   - costKind = ALLOCATED 基础设施分摊，不是真实 API 账单
   - costKind = SAVED     节省额，不计入成本合计
   - measureKind = MEASURED 实测 / COUNTERFACTUAL 反推 / ESTIMATED 估算
   ========================================================================== */

/** 成本构成明细项 */
export interface BreakdownItem {
  entryType: string
  costKind: string
  entryCount: number
  amount: string
  costRatio: Ratio
}

/** 成本构成总览：三类口径分开给出，禁止相加冒充真实账单 */
export interface CostBreakdownView {
  apiCost: string
  allocatedCost: string
  savedCost: string
  items: BreakdownItem[]
}

export interface UserCost {
  userId: string
  requestCount: number
  totalCost: string
  costRatio: Ratio
}

/** 消费预测（月度） */
export interface ForecastMonthly {
  period: string
  currentCost: string
  projectedCost: string
  budgetLimit: string
  usageRate: Ratio
  willExceed: boolean
  exhaustionEtaHours: string
  rolling24hCost: string
  rollingProjectedCost: string
  basis: string
}

export interface CostForecast {
  dailyExhaustionEtaHours: string
  infrastructureTotal: number
  monthly: ForecastMonthly
}

/** 优化策略元数据（说明触发条件与收益口径） */
export interface StrategyMeta {
  strategy: string
  displayName: string
  trigger: string
  measureKind: string
  qualityNote: string
  enabled: boolean
}

/** 优化策略的实际效果统计 */
export interface StrategyStat {
  strategy: string
  displayName: string
  executionCount: number
  beforeTokens: number
  afterTokens: number
  savedTokens: number
  savedCost: string
  measureKind: string
  qualityNote: string
  lastExecutedAt: string
}

export interface CacheStat {
  hit: number
  miss: number
  hitRate: Ratio
}

export interface EmbeddingStat {
  generated: number
  skipped: number
  skipRate: Ratio
}

/** 优化收益汇总：三口径分开，避免把估算当实测 */
export interface OptimizationSummary {
  measuredSavedCost: string
  counterfactualSavedCost: string
  estimatedSavedCost: string
  cache: CacheStat
  embedding: EmbeddingStat
  strategies: StrategyStat[]
}

export interface OptimizationOverview {
  strategies: StrategyMeta[]
  summary: OptimizationSummary
}

/** 优化执行记录 */
export interface OptimizationLog {
  id: number
  requestId: string | null
  strategy: string
  beforeTokens: number | null
  afterTokens: number | null
  savedTokens: number | null
  savedCost: string | null
  measureKind: string
  qualityNote: string
  detail: string
  createdAt: string
}

/** 单次优化试算结果 */
export interface OptimizationExecuteResult {
  strategy: string
  beforeTokens: number
  afterTokens: number
  savedTokens: number
  estimatedSavedCost: string
  measureKind: string
  qualityNote: string
  detail: string
}

/** 保护规则 */
export interface ProtectionRule {
  id: number
  ruleCode: string
  ruleName: string
  protectionLevel: number
  conditionType: string
  thresholdValue: number | null
  action: string
  priority: number
  enabled: number
  remark: string
  createdAt: string
  updatedAt: string
}

/** 模型路由档位 */
export interface ProtectionRoute {
  level: string
  model: string
  pricingReady: boolean
  description: string
}

export interface ProtectionStatus {
  level: string
  levelCode: number
  emergencyStopped: boolean
  routes: ProtectionRoute[]
  maxBudgetUsageRate: Ratio
  openCriticalAlerts: number
}

/** 保护动作审计日志 */
export interface ProtectionLog {
  id: number
  action: string
  fromLevel: number
  toLevel: number
  targetType: string
  targetId: string
  operator: string
  reason: string
  detail: string
  createdAt: string
  restoredAt: string | null
}

/** 告警中心记录（V2：含等级、状态、阈值、指纹、确认与恢复时间） */
export interface CostAlertRow {
  id: number
  requestId: string | null
  alertType: string
  alertLevel: string
  targetType: string | null
  budgetType: string | null
  targetId: string | null
  message: string
  amount: string | null
  thresholdValue: string | null
  currentValue: string | null
  severity: string
  status: string
  occurrenceCount: number
  fingerprint: string
  lastSeenAt: string
  acknowledgedAt: string | null
  acknowledgedBy: string | null
  resolvedAt: string | null
  createdAt: string
}

/** 阈值评估 / 异常扫描的命中项 */
export interface ThresholdHit {
  budgetType: string
  targetId: string
  threshold: string
  usageRate: string
  alertLevel: string
  action: string
  raised: boolean
  recovered: boolean
}

export interface AnomalyFinding {
  rule: string
  level: string
  targetType: string
  targetId: string
  message: string
  currentValue: string
  baselineValue: string
}

/** Agent 成本轨迹 */
export interface AgentCostTrace {
  id: number
  taskId: string
  agentId: string
  requestId: string
  userId: string
  conversationId: string
  steps: number
  toolCalls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  totalCost: string
  maxSteps: number
  status: string
  stopReason: string | null
  startedAt: string
  finishedAt: string | null
}

