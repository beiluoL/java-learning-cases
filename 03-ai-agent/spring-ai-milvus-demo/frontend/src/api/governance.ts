import { http } from './request'
import type {
  AgentCostTrace,
  AnomalyFinding,
  CostAlertRow,
  CostBreakdownView,
  CostForecast,
  OptimizationExecuteResult,
  OptimizationLog,
  OptimizationOverview,
  PageData,
  ProtectionLog,
  ProtectionRule,
  ProtectionStatus,
  StrategyMeta,
  ThresholdHit,
  TimeRange,
  UsageDetail,
  UsageRecord,
  UserCost,
} from './types'

/**
 * 成本治理平台 API（V2）。
 *
 * 与旧 /api/admin/ai/** 的区别：
 * - 旧接口保留给已有页面，不做破坏性改动；
 * - 本模块对应新治理接口，覆盖成本构成、优化中心、预警中心、保护策略、请求追踪。
 */
export const governanceApi = {
  /* ---------------- 成本治理分析 ---------------- */

  breakdown: (r?: TimeRange) =>
    http.get<CostBreakdownView>('/api/admin/cost/breakdown', r ? { ...r } : undefined),

  users: (r?: TimeRange, limit = 20) =>
    http.get<UserCost[]>('/api/admin/cost/users', { ...(r ?? {}), limit }),

  forecast: () => http.get<CostForecast>('/api/admin/cost/forecast'),

  optimization: () => http.get<OptimizationOverview>('/api/admin/cost/optimization'),

  /** 日汇总（五维度聚合表，由定时任务或手动 rebuild 生成） */
  dailySummary: (dimension = 'ALL') =>
    http.get<Record<string, unknown>[]>('/api/admin/cost/daily-summary', { dimension }),

  rebuildDailySummary: () =>
    http.post<Record<string, unknown>>('/api/admin/cost/daily-summary/rebuild'),

  /* ---------------- 请求追踪 ---------------- */

  usagePage: (params: {
    page: number
    size: number
    userId?: string
    modelName?: string
    businessScene?: string
    requestStatus?: string
    usageStatus?: string
    minCost?: string
    maxCost?: string
    sort?: string
    startTime?: string
    endTime?: string
  }) => http.get<PageData<UsageRecord>>('/api/admin/token-usage/page', params),

  usageDetail: (requestId: string) =>
    http.get<UsageDetail>(`/api/admin/token-usage/${encodeURIComponent(requestId)}`),

  /* ---------------- 成本优化 ---------------- */

  strategies: () => http.get<StrategyMeta[]>('/api/admin/cost-optimization/strategies'),

  executeOptimization: (strategy: string, text: string) =>
    http.post<OptimizationExecuteResult>('/api/admin/cost-optimization/execute', { strategy, text }),

  optimizationHistory: (strategy: string | undefined, page: number, size: number) =>
    http.get<PageData<OptimizationLog>>('/api/admin/cost-optimization/history', {
      ...(strategy ? { strategy } : {}),
      page,
      size,
    }),

  /* ---------------- 预警中心 ---------------- */

  alerts: (params: {
    page: number
    size: number
    alertType?: string
    alertLevel?: string
    status?: string
    targetType?: string
    days?: number
  }) => http.get<PageData<CostAlertRow>>('/api/admin/cost-alert/page', params),

  acknowledgeAlert: (id: number) =>
    http.post<CostAlertRow>(`/api/admin/cost-alert/${id}/acknowledge`),

  resolveAlert: (id: number) => http.post<CostAlertRow>(`/api/admin/cost-alert/${id}/resolve`),

  /** 手动触发阈值评估：正常由结算事件 + 定时扫描驱动，这里用于运维即时验证 */
  evaluateThresholds: () =>
    http.post<{ thresholdHits: ThresholdHit[] }>('/api/admin/cost-alert/evaluate'),

  anomalyScan: () => http.post<{ findings: AnomalyFinding[] }>('/api/admin/cost-alert/anomaly-scan'),

  /* ---------------- 自动保护 ---------------- */

  protectionStatus: () => http.get<ProtectionStatus>('/api/admin/cost-protection/status'),

  protectionRules: () => http.get<ProtectionRule[]>('/api/admin/cost-protection/rules'),

  updateProtectionRule: (
    ruleCode: string,
    payload: {
      level?: number
      threshold?: number
      action?: string
      priority?: number
      enabled?: number
      remark?: string
    },
  ) => http.put<ProtectionRule>(`/api/admin/cost-protection/rules/${ruleCode}`, payload),

  emergencyStop: (operator: string, reason: string) =>
    http.post<ProtectionStatus>('/api/admin/cost-protection/emergency-stop', { operator, reason }),

  resume: (operator: string, reason: string) =>
    http.post<ProtectionStatus>('/api/admin/cost-protection/resume', { operator, reason }),

  protectionLogs: () => http.get<ProtectionLog[]>('/api/admin/cost-protection/logs'),

  /* ---------------- Agent 成本 ---------------- */

  agentTraces: (params: { page: number; size: number; userId?: string; status?: string; days?: number }) =>
    http.get<PageData<AgentCostTrace>>('/api/admin/agent/traces', params),
}
