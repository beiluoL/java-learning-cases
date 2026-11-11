# AI Token Cost Platform · API 契约（前后端共同基准）

> 本文是前端与后端的**唯一契约**。前端必须只通过这些接口取数，禁止写死统计值。
> 基础路径：后端 `http://localhost:8091`，前端 dev server `http://localhost:5174`（Vite 代理 `/api` → 8091）。
> 时间统一使用 ISO 8601 UTC，例如 `2026-11-01T00:00:00Z`。金额一律为**字符串**（BigDecimal 精度），Token 为整数。

## 统一响应

```json
{ "code": 0, "message": "success", "data": {}, "traceId": "..." }
```

`code = 0` 表示成功；非 0 时 `message` 为可直接展示的错误文案。常见错误码：
`40001` 参数校验失败、`40301` 预算不足被拦截、`40401` 资源不存在、`40901` 重复请求、`50001` 系统异常、`50301` Redis 不可用（安全失败）。

分页响应固定结构：

```json
{ "code": 0, "data": { "records": [], "total": 0, "page": 1, "size": 20 } }
```

## 一、AI 问答（业务链路）

### POST /api/ai/chat

请求：

```json
{ "conversationId": "conv-001", "message": "Redis 缓存穿透是什么？", "useRag": true }
```

响应 `data`：

```json
{
  "requestId": "req-...",
  "traceId": "...",
  "answer": "...",
  "model": "供应商返回的真实模型名",
  "provider": "openai | mock | local",
  "usage": { "inputTokens": 1200, "outputTokens": 500, "totalTokens": 1700,
             "cachedInputTokens": 0, "reasoningTokens": 0, "usageStatus": "REAL" },
  "cost": { "inputCost": "0.0012", "outputCost": "0.0023", "totalCost": "0.0035",
            "currency": "USD", "pricingVersion": "DEMO-v1" },
  "budget": { "reserved": "0.0100", "settled": "0.0035", "remaining": "..." },
  "latencyMs": 812
}
```

`usageStatus` 取值：`REAL`（供应商返回）、`ESTIMATED`（估算）、`UNAVAILABLE`（供应商未提供）、`LOCAL`（本地模型）、`MOCK`（模拟调用，禁止计入真实账单口径）。

> 真实调用口径补充：`data.model` 取供应商实际返回的模型名，可能与请求配置不同（实测 DeepSeek 返回 `deepseek-flash`）；账单按该实际模型名匹配价格，缺价格时标 `UNPRICED` 并告警（别名价格由 `V3__add_provider_alias_demo_pricing.sql` 补充）。`usageStatus=REAL` 只表示 Token 来自供应商真实返回；`pricingVersion=DEMO-v1` 等价格均为示例价，**不是供应商真实报价**，成本金额不构成真实财务结算口径。

## 二、成本分析

所有统计接口都接受 `startTime` / `endTime`（ISO UTC），后端强制限制最大查询跨度 92 天、分页最大 100 条。

| 方法 | 路径 | data 结构 |
|---|---|---|
| GET | `/api/admin/ai/cost/overview` | `{totalCost, totalTokens, inputTokens, outputTokens, cachedInputTokens, requestCount, avgCostPerRequest, successRate, budgetUsageRate}` |
| GET | `/api/admin/ai/cost/trend?granularity=DAY` | `[{bucket, totalCost, inputTokens, outputTokens, requestCount}]`；`granularity` = `HOUR` / `DAY` / `MONTH` |
| GET | `/api/admin/ai/cost/models` | `[{provider, modelName, requestCount, inputTokens, outputTokens, totalCost, avgCost, costRatio}]` |
| GET | `/api/admin/ai/cost/scenes` | `[{businessScene, requestCount, totalTokens, totalCost, costRatio}]` |
| GET | `/api/admin/ai/cost/top-models?limit=10` | `[{name, value}]`，`value` 为成本字符串 |
| GET | `/api/admin/ai/cost/top-users?limit=10` | `[{name, value}]` |
| GET | `/api/admin/ai/cost/recent-requests?limit=10` | 调用明细数组，字段同下方 `usage/page` 的 record |
| GET | `/api/admin/ai/cost/alerts?limit=10` | `[{id, alertType, budgetType, message, severity, createdAt}]` |

### GET /api/admin/ai/usage/page

参数：`page`（默认 1）、`size`（默认 20，最大 100）、`userId`、`modelName`、`businessScene`、`requestStatus`、`usageStatus`、`minCost`、`maxCost`、`startTime`、`endTime`、`sort`（默认 `createdAt,DESC`）。

`records[]` 字段：`requestId, traceId, userId, conversationId, businessScene, provider, modelName, modelType, inputTokens, outputTokens, cachedInputTokens, totalTokens, usageStatus, requestStatus, latencyMs, totalCost, currency, pricingVersion, createdAt`。

> 默认**不返回完整 Prompt**，保护敏感内容。

### GET /api/admin/ai/usage/{requestId}

返回明细 + `costBreakdown`（成本计算过程）：`{pricingVersion, inputPrice, outputPrice, cachedInputPrice, normalInputTokens, cachedInputTokens, outputTokens, inputCost, cachedCost, outputCost, totalCost}`，以及预算预扣/结算状态。

## 三、预算管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/ai/budgets` | 策略列表 |
| GET | `/api/admin/ai/budgets/{id}` | 策略详情 |
| POST | `/api/admin/ai/budgets` | 新增 |
| PUT | `/api/admin/ai/budgets/{id}` | 修改 |
| GET | `/api/admin/ai/budgets/status` | 实时状态（含 Redis 预扣） |

策略字段：`id, budgetType(GLOBAL|USER|MODEL|DAILY|MONTHLY), targetId, dailyLimit, monthlyLimit, perRequestLimit, maxInputTokens, maxOutputTokens, allowOverrun, enabled, currency, remark`。

状态字段：`budgetType, targetId, limitAmount, usedAmount, reservedAmount, remainingAmount, usageRate`。

## 四、模型价格管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/ai/pricing` | 当前生效价格 |
| POST | `/api/admin/ai/pricing` | 新增价格版本 |
| PUT | `/api/admin/ai/pricing/{id}` | 修改 / 停用 |
| GET | `/api/admin/ai/pricing/history?modelName=` | 历史版本 |

字段：`id, provider, modelName, modelType(CHAT|EMBEDDING|RERANK), inputPrice, outputPrice, cachedInputPrice, currency, version, effectiveFrom, effectiveTo, enabled, demoFlag`。

> 初版价格统一 USD，全部带 `demoFlag = true`，标记为 DEMO 示例价，不代表供应商真实报价。

## 五、运维

`GET /actuator/health`、`GET /actuator/info`、`GET /actuator/metrics`（含 `ai.request.total`、`ai.budget.rejected`、`ai.cost.amount` 等自定义指标）。

## 六、比率口径

- `costRatio`（`/cost/models`、`/cost/scenes`）、`usageRate`（`/budgets/status`）、`successRate` 及 `budgetUsageRate`（`/cost/overview`）后端统一返回 **0~1 的小数**（如 `0.35` 表示 35%），前端自行换算为百分比，不要再按百分数解析。见 `Views.java`、`CostAnalyticsService.ratio()`、`TokenBudgetService.status()`。
- `top-users.value`（同 `top-models.value`）是**成本金额字符串**（USD 金额，如 `"1.23450000"`），不是比率、也不是 JSON 数字；`name` 为用户名 / 模型名。见 `Views.TopItem`。
- 统一响应顶层的 `traceId`：`POST /api/ai/chat` 取自响应 VO，必有值；其它接口取 MDC 当前值，**可能为空字符串**（例如被 `AdminAccessInterceptor` 拒绝的响应）。排障以该字段为准。见 `AiChatController`、`ApiResponse.ok()`、`TraceContext.current()`。

---

# 治理平台 V2 接口（新增）

> 与上面 V1 接口**并行存在**：`/api/admin/ai/**` 全部保留不做破坏性修改，V2 走新的短路径。
> 建议新页面统一使用 V2；V1 仅为向后兼容保留。

## 七、V2 新增错误码

| code | 含义 | 触发点 |
|---|---|---|
| `40302` | 保护等级阻断 | `CostProtectionEngine` 判定 `BLOCKED`，或紧急开关生效 |
| `42901` | 触发限流 | `AIRequestProtectionService` 用户窗口 / 并发槽位超限 |

`40301`（预算不足）与 `40302`（保护阻断）**语义不同**，前端应分别提示：前者是「这个作用域没钱了」，后者是「全局/紧急状态下禁止付费调用」。

## 八、AI 问答（V2 响应扩展）

`POST /api/ai/chat` 请求新增可选字段：`knowledgeBaseId`、`agentId`、`agentTaskId`。响应 `data` 在原结构上新增：

```json
{
  "routeLevel": "L1 | L2 | L3",
  "cacheHit": false,
  "cacheSavedCost": "0",
  "protectionLevel": "NORMAL | WARNING | LIMIT | SEVERE | BLOCKED",
  "optimization": {
    "promptTokensBefore": 55, "promptTokensAfter": 55,
    "savedTokens": 0, "savedCost": "0", "measureKind": "ESTIMATED"
  }
}
```

`cacheHit=true` 时 `cost.totalCost = "0"`、`pricingVersion = "CACHE_HIT"`、`usageStatus = "CACHED"`，同时写一条 `SAVED` 账务流水。

## 九、成本治理分析 `/api/admin/cost`

| 方法 | 路径 | 参数 | data 结构 |
|---|---|---|---|
| GET | `/overview` | `startTime`、`endTime` | 同 V1 `cost/overview` |
| GET | `/trend` | `startTime`、`endTime`、`granularity` | 同 V1 `cost/trend` |
| GET | `/breakdown` | `startTime`、`endTime` | `{apiCost, allocatedCost, savedCost, items[{entryType, costKind, entryCount, amount, costRatio}]}` |
| GET | `/models` | `startTime`、`endTime` | 同 V1 `cost/models` |
| GET | `/scenes` | `startTime`、`endTime` | 同 V1 `cost/scenes` |
| GET | `/users` | `startTime`、`endTime`、`limit`(默认20) | `[{userId, requestCount, totalCost, costRatio}]` |
| GET | `/optimization` | — | `{strategies[], summary{measuredSavedCost, counterfactualSavedCost, estimatedSavedCost, cache{hit,miss,hitRate}, embedding{generated,skipped,skipRate}, strategies[]}}` |
| GET | `/forecast` | — | `{monthly{period,currentCost,projectedCost,budgetLimit,usageRate,willExceed,exhaustionEtaHours,rolling24hCost,rollingProjectedCost,basis}, dailyExhaustionEtaHours, infrastructureTotal}` |
| GET | `/daily-summary` | `dimension`(默认 ALL)、`from`、`to` | 日汇总行数组 |
| POST | `/daily-summary/rebuild` | `day`(可选) | 重建结果 |
| GET | `/infrastructure` | — | 基础设施成本登记行 |
| POST | `/infrastructure` | body: InfrastructureCost | 新增 |
| PUT | `/infrastructure/{id}` | body: InfrastructureCost | 修改 |
| POST | `/infrastructure/allocate` | `periodStart`、`periodEnd` | 分摊结果 |

**`costKind` 三口径不可混用**：`ACTUAL`=供应商真实账单、`ALLOCATED`=基础设施分摊（**不是** API 账单）、`SAVED`=节省（**不进**成本合计）。`apiCost` 只累加 `ACTUAL`，`allocatedCost` 只累加 `ALLOCATED`，两者不合并。

## 十、请求追踪 `/api/admin/token-usage`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 参数与 V1 `usage/page` 一致；record 新增 `agentId, knowledgeBaseId, cacheHit, cacheSavedCost, retryCount, routeLevel` |
| GET | `/statistics` | 按小时聚合的 Token 序列 |
| GET | `/{requestId}` | 明细 + `costBreakdown` + 预算预扣状态 |

## 十一、预算管理 V2 `/api/admin/budget`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 分页；`budgetType` 扩展为 `GLOBAL / DAILY / MONTHLY / USER / MODEL / SCENE / KNOWLEDGE_BASE / AGENT / REQUEST` 九级 |
| GET | `/status` | 实时状态（含 Redis 预扣），参与保护等级判定 |
| GET | `/{id}` | 详情 |
| POST | `/` | 新增 |
| PUT | `/{id}` | 修改 |
| GET | `/transactions` | 预算变更流水（RESERVE / SETTLE / RELEASE / EXPIRE，每作用域一条，`txNo` 幂等） |

策略新增字段：`maxRequests, maxConcurrency, maxAgentSteps, maxToolCalls, alertThreshold, overrunPolicy`。

## 十二、预警中心 `/api/admin/cost-alert`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/page` | 参数 `page,size,alertType,alertLevel,status,targetType,days`(1~92，默认7) |
| GET | `/{id}` | 详情 |
| POST | `/{id}/acknowledge` | 确认（OPEN → ACKNOWLEDGED） |
| POST | `/{id}/resolve` | 恢复（→ RESOLVED） |
| POST | `/evaluate` | **手动触发预算阈值评估**，返回 `{thresholdHits:[{budgetType,targetId,threshold,usageRate,alertLevel,action,raised,recovered}]}` |
| POST | `/anomaly-scan` | **手动触发异常检测**，返回 `{findings:[{rule,level,targetType,targetId,message,currentValue,baselineValue}]}` |

告警 record 字段：`id, requestId, alertType, alertLevel, targetType, budgetType, targetId, message, amount, thresholdValue, currentValue, severity, status, occurrenceCount, fingerprint, lastSeenAt, acknowledgedAt, acknowledgedBy, resolvedAt, createdAt`。

`alertLevel`：`INFO / WARNING / HIGH / CRITICAL / BLOCKED`；`status`：`OPEN / ACKNOWLEDGED / RESOLVED`。
去重键 = `alertType|targetType|targetId|alertLevel[|discriminator]`，同键未恢复告警只累加 `occurrenceCount`。

## 十三、自动保护 `/api/admin/cost-protection`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/status` | `{level, levelCode, emergencyStopped, routes[{level,model,pricingReady,description}], maxBudgetUsageRate, openCriticalAlerts}` |
| GET | `/rules` | 保护规则（`conditionType` / `thresholdValue`(0~1) / `protectionLevel`(1~5) / `action` / `priority` / `enabled`） |
| PUT | `/rules/{ruleCode}` | 改等级/阈值/动作/优先级/开关/备注 |
| POST | `/emergency-stop` | body `{operator, reason}`，`reason` 必填，否则 `40001` |
| POST | `/resume` | body `{operator, reason}`；同时恢复本次停用写下的 `PROTECTION_ACTION` 告警 |
| GET | `/logs` | 审计日志（`action, fromLevel, toLevel, operator, reason, detail, createdAt`） |

## 十四、成本优化 `/api/admin/cost-optimization`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/strategies` | `[{strategy, displayName, trigger, measureKind, qualityNote, enabled}]` |
| POST | `/execute` | body `{strategy, text}`；只对纯函数策略开放试算，返回 `{strategy, beforeTokens, afterTokens, savedTokens, estimatedSavedCost, measureKind, qualityNote, detail}` |
| GET | `/history` | 参数 `strategy`、`page`、`size`；优化执行记录 |

`measureKind` 三口径：`MEASURED`（实测，来自真实流水）/ `COUNTERFACTUAL`（真实用量 × 真实价格反推）/ `ESTIMATED`（估算，不写账单）。

## 十五、Agent 成本 `/api/admin/agent`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/traces` | 参数 `page,size,userId,status,days`；任务级成本轨迹 |
| GET | `/traces/{taskId}` | 单任务详情 |

轨迹字段：`id, taskId, agentId, requestId, userId, conversationId, steps, toolCalls, inputTokens, outputTokens, totalTokens, totalCost, maxSteps, status, stopReason, startedAt, finishedAt`。

## 十六、前端路由与页面对照（10 页）

| 路由 | 页面 | 主要数据来源 |
|---|---|---|
| `/dashboard/cost` | 成本总览 | V1 `/cost/*` |
| `/dashboard/cost/breakdown` | 成本构成 | V2 `/cost/breakdown`、`/users`、`/forecast`、`/daily-summary` |
| `/dashboard/token-usage` | Token 分析 | V1 `/cost/*` |
| `/dashboard/model-cost` | 模型成本 | V1 `/cost/*`、`/pricing` |
| `/dashboard/budget` | 预算管理 | V1 `/ai/budgets`、V2 `/budget/status` |
| `/dashboard/cost-optimization` | 优化中心 | V2 `/cost-optimization/*`、`/cost/optimization` |
| `/dashboard/cost-alerts` | 预警中心 | V2 `/cost-alert/*` |
| `/dashboard/cost-protection` | 系统保护策略 | V2 `/cost-protection/*` |
| `/dashboard/model-pricing` | 价格管理 | V1 `/ai/pricing` |
| `/dashboard/ai-traces` | 请求追踪 | V2 `/token-usage/*` |

旧路由 `/dashboard/ai-cost` → `/dashboard/cost`、`/dashboard/ai-requests` → `/dashboard/ai-traces` 已配重定向。
