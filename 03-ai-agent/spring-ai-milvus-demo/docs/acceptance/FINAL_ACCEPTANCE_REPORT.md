# 最终验收报告（V2.0 成本控制与治理平台）

> 本文件对应代码路径：
> - 启动与装配：`src/main/java/com/example/milvusdemo/DemoApplication.java`、`src/main/java/com/example/ai/config/RagConfiguration.java`
> - 治理模块：`src/main/java/com/example/ai/`（`token/{usage,pricing,budget,cost,analytics,agent,infra}`、`optimize`、`guard`、`chat`、`identity`、`metrics`、`config`、`common`），共 **93 个 Java 文件**（另有 6 个 `com.example.milvusdemo` RAG 文件，合计 99 个）
> - 迁移与脚本：`V1`~`V5` 五个 Flyway 迁移、4 个 Lua 脚本、**13 个 Mapper XML**
> - 前端：`frontend/src/views/`（10 页）、`router/index.ts`、`api/`、`constants.ts`
> - 一键脚本：`start/stop/restart` 的 `.command` 与 `.bat` 共 6 个
> - 测试：13 个测试类 / **72 项测试**
> - 详细证据：`docs/acceptance/TEST_REPORT.md`、`PERFORMANCE_REPORT.md`、`docs/audit/PROJECT_AUDIT.md`、`docs/api/AI_COST_API.md`
>
> **诚实口径声明**：真实供应商调用与真实 Usage 采集已通过；成本按 `DEMO-v1` **示例价**计算，**未声称真实财务结算通过**；Redis 与 MySQL 为**最终一致**，未声称强一致事务；RAG 检索端到端、Windows 脚本、前端组件级单测、性能压测**未完成**（见第 8 节）。

## 1. 版本与结论口径

V1.0 交付的是「成本可算、预算可控」的工程基线（5 表 / 6 页 / 21 项测试）。V2.0 在**不删除任何 RAG / Milvus 代码**的前提下，把它扩成八大模块联动的治理平台（13 表 / 10 页 / 72 项测试）。

| 前提 | 取值 |
|---|---|
| RAG | `rag.enabled=false`，**RAG 检索未做端到端验证**；代码一行未删 |
| 模型 | 默认 `ai.provider=mock`；另以 `AI_PROVIDER=openai` + DeepSeek 兼容接口在 **8092 端口实例**完成真实调用验收 |
| 价格 | `DEMO-v1`，全部 `demo_flag=1`，示例价 |
| 身份 | 固定 `demo-user`，未接入真实认证 |
| 存储 | 真实 MySQL 8.0（宿主 13307，容器 `ai-cost-mysql`）+ 真实 Redis 8.8（6379） |
| 数据库版本 | `flyway_schema_history` **5 条 success（V1~V5）**，**13 张业务表** |
| JDK | **Java 17**（JDK 23+ 会静默关闭注解处理导致 Lombok 失效，见 `TEST_REPORT.md` 1.1） |

三态定义：

- **通过**：有自动化测试或明确的启动、接口、页面实测证据支撑。
- **模拟验收**：在 Mock 模型、DEMO 价格、Demo 身份或代码走查下完成链路验证；不等同于真实报价结算、真实用户、真实浏览器环境。
- **未完成**：本次未做或未验证，不推测结果。

## 2. 八大模块逐项验收结论

### 2.1 成本核算（Cost Accounting）— **通过**

| 验收点 | 结论与证据 |
|---|---|
| 全 BigDecimal 三段计费（input / output / cached） | `CostCalculatorTest` 4 例；真实调用三段成本 `0.00000945 / 0.00002750 / 0.00003695` 自洽 |
| 缓存 Token 是输入子集、不重复计费 | `CostCalculatorTest` 覆盖缓存越界收敛 |
| **三口径分离**（`ACTUAL` 真实账单 / `ALLOCATED` 基础设施分摊 / `SAVED` 节省） | `CostKind` 枚举 + `CostLedgerService`；`GovernancePersistenceIT.ledgerIsAppendOnlyAndIdempotentByEntryNo` 验证追加式账务 |
| 分摊不计入 `apiCost`、节省不冲减成本 | `CostBreakdown` 分项返回，前端构成占比饼图**显式过滤 `SAVED`** |
| 缺价格标 `UNPRICED` 零成本、绝不套用相近模型价 | 真实调用首次因别名模型 `deepseek-flash` 缺价被正确标 `UNPRICED` 并告警（安全行为，非故障） |
| Agent 成本归集 | `AgentCostTracker` + `ai_agent_cost_trace`；`GovernancePersistenceIT.agentTraceIsUniquePerTask` |

### 2.2 成本优化（Cost Optimization）— **通过**

| 验收点 | 结论与证据 |
|---|---|
| 8 条策略枚举（Prompt 压缩 / 上下文压缩 / RAG 上下文限制 / 模型路由 / 响应缓存 / Embedding 去重 / 输出 Token 限制 / Prompt 缓存） | `OptimizationStrategy` 枚举；`PromptOptimizerTest` 9 例 |
| **三口径收益**（`MEASURED` / `COUNTERFACTUAL` / `ESTIMATED`） | `MeasureKind` 枚举；`TokenEstimator.estimatorIsHeuristicNotBilling` 明确估算器不是账单 |
| 缓存命中率、Embedding 去重率真实可查 | `GovernanceRedisIT.responseCacheIsolatesByUserModelAndPromptVersion`、`embeddingDedupSkipsSameContentAndModel` |
| 模型路由按保护等级降档 | `ModelRouterTest.protectionLevelForcesLowCostModel` 等 6 例 |
| 策略试算（dry-run，不写库、只返回可省金额） | `POST /api/admin/cost-optimization/execute`；实测 `beforeTokens=36` → `afterTokens=18`、`savedTokens=18`、`measureKind=ESTIMATED`，响应自带 `qualityNote` 声明「dry-run 试算不代表已节省真实费用」 |
| 执行记录可追溯 | `ai_cost_optimization_log` + 前端执行记录表 |

### 2.3 预算管理（九级）— **通过**

| 验收点 | 结论与证据 |
|---|---|
| **九级作用域**：`GLOBAL` / `USER` / `MODEL` / `DAILY` / `MONTHLY` / `SCENE` / `KNOWLEDGE_BASE` / `AGENT` / `REQUEST` | `AiEnums.BudgetType` 9 个取值；`GovernancePersistenceIT.budgetPolicySupportsSceneAndAgentScopes` |
| 多作用域一次性原子检查 | `RedisBudgetIT.sceneBudgetIsCheckedAtomicallyWithGlobal`——不出现「场景够、全局不够」却已占用场景额度的中间态 |
| 预算策略读写 | `/api/admin/budget/*` 5 个接口（分页、状态、详情、改额度、流水），前端「预算管理」页 |
| 预算调整留痕 | `ai_budget_transaction`（`RESERVE`/`SETTLE`/`RELEASE`/`EXPIRE`/`ADJUST`） |

### 2.4 预算执行（Budget Enforcement）— **通过**

| 验收点 | 结论与证据 |
|---|---|
| Redis Lua 原子预扣（1e-8 精度整数运算） | 4 个 Lua；`RedisBudgetIT.normalReserveAndSettle` |
| 幂等：重复 `requestId` 不重复扣、重复结算不重复计费 | `RedisBudgetIT.duplicateRequestIdAndDuplicateSettleAreIdempotent` |
| **100 并发不超支** | `hundredConcurrentRequestsNeverExceedBudget`：5.00 USD 额度、每请求 1.00 USD，**恰好 5 个通过**，`held` 精确等于 5.00 USD |
| 失败释放、过期清理（不只靠 key TTL） | `releaseRestoresBudgetOnFailure`、`expiredReservationIsCleanedUp` |
| 超限拒绝并**落库** | `code=40301 预算不足`，被拒账单也写 `ai_token_usage` |
| 结算超预扣写 `SETTLE_OVERRUN` | `TokenBudgetService.settle()`；代码路径完整，分支未逐一单独实测 |
| Redis↔MySQL 对账 | `BudgetReconciler`（5min，差异 >0.01 USD 写 `RECONCILE_DIFF`），**未主动制造差异验证** |

### 2.5 成本预警（Cost Alert）— **通过（五档逐档实测）**

| 验收点 | 结论与证据 |
|---|---|
| **19 种告警类型** | `AiEnums.AlertType`；前端 `ALERT_TYPE_OPTIONS` 19 项 |
| **五档使用率阈值 50/70/85/95/100%** | 见下表逐档实测 |
| 告警等级与动作联动 | 50/70→`WARNING`/`NOTIFY`；85→`HIGH`/`DOWNGRADE_MODEL`；95→`CRITICAL`/`DISABLE_AGENT`；100→`BLOCKED`/`BLOCK_REQUEST` |
| 消费速度异常 | `SpendForecastService.checkVelocity`（近 1h > 近 24h 均值 ×3 且 >0.01 USD）→ `SPEND_VELOCITY` |
| 6 条异常检测规则 | `AnomalyDetectionServiceTest` 9 例（命中 / 静默两端 + Agent 死循环判 CRITICAL + findings 落告警中心） |
| 去重 / 确认 / 恢复 | 指纹 `type\|targetType\|targetId\|level[\|discriminator]`；去重由 `BudgetThresholdAlertServiceTest` 与脚本实测双重覆盖（见第 6 节「连续 3 次 evaluate」） |
| 手动触发 | `POST /api/admin/cost-alert/evaluate`、`POST /api/admin/cost-alert/anomaly-scan`，前端「预警中心」有按钮与结果面板 |

**五档阈值实测结果**（真实 Redis 占用 + 真实 MySQL 额度调整，脚本 `samples/verify-alert-levels.sh`）：

| 额度（USD） | 实际使用率 | 实测产生的告警 |
|---|---|---|
| `0.00000800` | 80.00% | `50.00%` WARNING/NOTIFY + `70.00%` WARNING/NOTIFY |
| `0.00000660` | 96.97% | `85.00%` HIGH/DOWNGRADE_MODEL + `95.00%` CRITICAL/DISABLE_AGENT |
| `0.00000600` | 106.67% | `100.00%` BLOCKED/BLOCK_REQUEST |

去重：连续 3 次 `evaluate` 后仍 **5 行 / `occurrence_count` 合计 5**，不新增行。超限后真实问答返回 `code=40302`。

### 2.6 自动保护（Automatic Protection）— **通过（修复一处真实缺陷）**

| 验收点 | 结论与证据 |
|---|---|
| **五级保护**：`NORMAL(1)` / `WARNING(2)` / `LIMIT(3)` / `SEVERE(4)` / `BLOCKED(5)` | `AiEnums.ProtectionLevel`；`GovernancePersistenceIT.protectionRulesAreSeededWithFiveLevels` |
| 规则驱动（`budget_usage` 等 5 条种子规则） | 保护页实测渲染 `budget_usage` 等 5 条规则，当前档位 `NORMAL`、`L5` |
| 模型路由按档位下发 | `/cost-protection/status` 返回 `modelRoutes` 档位表 |
| **紧急开关**（原因必填、停用即阻断、可恢复） | 见第 7 节全链路实测 |
| 审计日志 | `ai_cost_protection_log`，`EMERGENCY_STOP 1→5` / `RESUME 5→1` 双语记录 |
| **保护自锁缺陷** | 已修复（`countOpenByLevelExcluding` + `resume()` 清理自身告警），`CostProtectionEngineTest` 4 例值守，见 `ADR-002` |

> **本轮发现的唯一真实功能缺陷**：`emergency-stop` → `resume` 后等级永久卡在 `SEVERE`。原因是引擎自己写入的 `PROTECTION_ACTION`(CRITICAL) 告警被 `currentLevel()` 当成风险输入。修复同时保留「其它类型 CRITICAL 仍可升 SEVERE」的用例，防止过度放宽。

### 2.7 成本分析（Cost Analytics）— **通过**

| 验收点 | 结论与证据 |
|---|---|
| 成本构成（`ACTUAL`/`ALLOCATED`/`SAVED` 三分项） | `GET /api/admin/cost/breakdown`，前端「成本构成」页 6 张 StatCard + 2 个饼图 + 4 张表 |
| 用户 / 模型排行 | `GET /api/admin/cost/users`、`/by-model` |
| 月度预测与日耗尽 ETA | `SpendForecastServiceTest` 3 例 |
| 日汇总（五维度） | `DailySummaryService` + `ai_cost_daily_summary`；`GovernancePersistenceIT.dailySummaryNeverExceedsDetailBilling` 守住「汇总不超前于明细」 |
| 分页 / 排序白名单 / 92 天跨度限制 | `CostAnalyticsService.usagePage` 的 `sortAliases`；`GET /api/admin/token-usage/page` |
| **34 个管理接口** | 31 个只读 + 3 个操作端点，由 `samples/verify-admin-endpoints.sh` 一次跑完，**全部 `code=0`（34 通过 / 0 失败）** |
### 2.8 Dashboard（10 页 / 3 组）— **通过（含浏览器回归）**

| 分组 | 页面 |
|---|---|
| 分析 | 成本总览（首页 `/dashboard/cost`）、成本构成、Token 分析、模型成本 |
| 治理 | 预算管理、优化中心、预警中心、系统保护策略、价格管理 |
| 运维 | 请求追踪 |

- 静态门禁：`type-check` 无输出、`lint` exit 0、`build` 成功且 10 个视图 chunk 齐全。
- 浏览器回归（Chrome headless `--dump-dom`，6 页）：`h1` 全对、`loading=0`、`phaseErr=0`、真实数据 `rows=8~18` / `badges=9~31`。
- 旧路径 `/dashboard/ai-cost`、`/dashboard/ai-requests` 保留 redirect。

## 3. 数据库：13 表 / 5 迁移

| 迁移 | 内容 |
|---|---|
| `V1__init_ai_cost.sql` | 5 张核心表（`ai_token_usage`、`ai_model_pricing`、`ai_budget_policy`、`ai_budget_reservation`、`ai_cost_alert`） |
| `V2__seed_demo_data.sql` | 6 条 `DEMO-v1` 价格 + 3 条默认预算策略 |
| `V3__add_provider_alias_demo_pricing.sql` | 为别名模型 `deepseek-flash` 补 `DEMO-v1` 价格，**不改表结构** |
| `V4__governance_schema.sql` | 8 张新表 |
| `V5__governance_seed.sql` | 5 条保护规则 + 九级预算策略 + 告警阈值种子 |

13 张表：`ai_token_usage`、`ai_model_pricing`、`ai_budget_policy`、`ai_budget_reservation`、`ai_budget_transaction`、`ai_cost_alert`、`ai_cost_ledger`、`ai_cost_protection_rule`、`ai_cost_protection_log`、`ai_cost_optimization_log`、`ai_cost_daily_summary`、`ai_agent_cost_trace`、`ai_infrastructure_cost`。

字典（含逐字段 / 索引）见 `docs/database/TABLE_DICTIONARY.md`，由 `information_schema` 脚本生成，非手写。

## 4. 测试总览

**72 项全通过 = 48 单测 + 24 集成测试**（13 个测试类）。

| 类型 | 数量 | 明细 |
|---|---|---|
| 单元测试 | 48 | `CostCalculator`4、`OpenAiUsageAdapter`4、`LocalHashingEmbeddingModel`3、`SpendForecastService`3、`ModelRouter`6、`PromptOptimizer`9、`BudgetThresholdAlertService`6、`CostProtectionEngine`4、`AnomalyDetectionService`9 |
| Redis 集成 | 13 | `RedisBudgetIT`7、`GovernanceRedisIT`6 |
| MySQL 集成 | 11 | `MysqlPersistenceIT`4、`GovernancePersistenceIT`7 |

逐用例断言见 `docs/acceptance/TEST_REPORT.md` 第 2~4 节。

## 5. V1.0 十四项验收标准回归

| # | 验收项 | V1.0 结论 | V2.0 结论 | 变化说明 |
|---|---|---|---|---|
| 1 | 分层架构、模块边界、启动装配 | 通过 | **通过** | 新增 `optimize` / `guard` / `token.agent` / `token.infra` 四个包，仍在统一 `scanBasePackages` 下 |
| 2 | 真实 Usage 采集与口径区分 | 通过 | **通过** | `UsageStatus` 从 4 值扩到 6 值（新增 `LOCAL` / `CACHED`） |
| 3 | 成本计算（三段 / 缓存子集 / UNPRICED / 版本快照） | 通过 | **通过** | 增加三口径 `CostKind` 分账 |
| 4 | 价格管理、生效时间与历史快照 | 通过 | **通过** | 未改机制，V3 补价路径已验证 |
| 5 | 预算 Lua 原子多作用域预扣、幂等 | 通过 | **通过（扩到九级）** | 作用域 5 → 9；`RedisBudgetIT` 6 → 7 例 |
| 6 | 结算 / 释放 / UNKNOWN 保守结算 / SETTLE_OVERRUN | 模拟验收 | **模拟验收** | 主路径覆盖，异常分支仍未逐一实测 |
| 7 | 过期预扣清理与 Redis-MySQL 对账 | 模拟验收 | **模拟验收** | 清理有用例；对账未制造差异验证。一致性等级已写入 `ADR-003` |
| 8 | 身份与接口安全 | 通过 | **通过** | 管理接口从 13 个扩到 **34 个**（31 只读 + 3 操作），全部 `code=0` |
| 9 | 可观测（`AiMetrics` + Actuator） | 模拟验收 | **模拟验收** | 指标已注册，未逐项读取统计值 |
| 10 | 数据库表结构 / Flyway / 精度 / 索引 / COMMENT | 通过 | **通过（5 表 → 13 表）** | `GovernancePersistenceIT` 新增 7 例，含 COMMENT 校验 |
| 11 | 启动 + 账单落库 + 管理接口 `code=0` + 预算拦截 | 模拟验收 | **通过** | 34 个管理接口全通；新增 `40302` 保护阻断实测 |
| 12 | 前端静态校验 + 页面全走真实 API | 通过（浏览器回归未做） | **通过（含浏览器回归）** | 6 页 → 10 页；补做 6 页 DOM 断言 |
| 13 | RAG 可选装配与默认独立启动 | 模拟验收 | **模拟验收** | 本轮未触碰 RAG，`rag.enabled=false` 启动仍正常 |
| 14 | 真实模型调用 / 真实 Token 采集 / 费用计算 | 通过（DEMO 价） | **通过（DEMO 价）** | 未变；成本口径仍是示例价 |

汇总：**通过 11 项、模拟验收 3 项、未完成 0 项**（14 项内；V1.0 为通过 9 / 模拟 5）。升级的两项是第 11、12 项。

## 6. V2.0 修改与新增文件清单

### 6.1 新增后端模块

| 包 | 关键类 |
|---|---|
| `optimize` | `CostOptimizationService`、`CostOptimizationController`、`ModelRouter`、`PromptOptimizer`、`ContextCompressor`、`RagContextOptimizer`、`ResponseCacheService`、`EmbeddingCacheService`、`TokenEstimator` |
| `guard` | `AIRequestProtectionService`（限流 / 并发 / 熔断） |
| `token.agent` | `AgentCostTracker`、`AgentCostController`、`AgentCostTrace` + Mapper |
| `token.infra` | `InfrastructureCostService`、`InfrastructureCost` + Mapper |
| `token.cost`（扩） | `CostLedgerService`、`CostProtectionEngine`、`BudgetThresholdAlertService`、`AnomalyDetectionService`、`SpendForecastService`、`CostProtectionController`、6 个新 entity/Mapper |
| `token.analytics`（扩） | `CostGovernanceService`、`DailySummaryService`、`CostGovernanceController`、`TokenUsageAdminController`、`DailyCostSummary` + Mapper |
| `token.budget`（扩） | `BudgetSettledEvent`（事件解耦）、`BudgetTransaction`、`BudgetAdminController` |

### 6.2 关键修改

| 文件 | 改动 |
|---|---|
| `TokenBudgetService.java` | 构造函数注入 `ApplicationEventPublisher`；`settle()` 结算后发布 `BudgetSettledEvent`（`try/catch` 吞掉旁路异常，不影响结算结果） |
| `CostAlertService.java` | 新增带 `discriminator` 的 9 参 `alert(...)` 重载（原 8 参委托传 `null`，历史指纹不变）、`countOpenByLevelExcluding`、`resolveOpenOf` |
| `CostProtectionEngine.java` | `currentLevel()` 排除 `PROTECTION_ACTION`；`resume()` 清理自身遗留告警（**缺陷修复**） |
| `SpendForecastService.java` | 新增 `checkVelocity`（消费速度异常） |
| `CostAlertController.java` | 新增 `POST /evaluate`、`POST /anomaly-scan` |
| `CostAlertMapper.java` / `.xml` | 新增 `listOpenByTypeAndTarget`、`countOpenByLevelExcluding` |
| `AiEnums.java` | `BudgetType` 5→9、`AlertType` 7→19、`UsageStatus` 4→6、新增 `ProtectionLevel`/`OptimizationStrategy`/`MeasureKind`/`CostKind`/`LedgerEntryType`/`BudgetTxType`/`RouteLevel` |

> **为什么用事件而不是直接调用**：`BudgetThresholdAlertService` 需要读 `TokenBudgetService.status()` 算使用率，直接注入会形成构造期循环依赖。改用 `@EventListener` 后，事件在分发时才解析监听器，依赖链断开。备选方案（`@Lazy`、`ObjectProvider` 字段注入、拆公共查询服务）的取舍见 `ADR-002` 同目录的架构说明。

### 6.3 前端

新增 4 个视图：`CostBreakdown.vue`、`CostOptimization.vue`、`CostAlerts.vue`、`CostProtection.vue`；新增 `api/governance.ts`（约 22 个方法）、`api/types.ts` 追加约 20 个 interface；`router/index.ts` 重写为 10 页 / 3 组；`constants.ts` 追加 6 组枚举与 5 个 tone 映射；`App.vue` 品牌与导航分组更新；`AiRequests.vue` 改为「请求追踪」。

### 6.4 迁移 / Lua / Mapper

- 迁移：新增 `V4__governance_schema.sql`、`V5__governance_seed.sql`。
- Lua：仍是 4 个（`budget_reserve` / `settle` / `release` / `cleanup`），未新增。
- Mapper XML：6 → **13** 个（新增 `AgentCostTrace`、`CostOptimizationLog`、`CostProtectionRule`、`CostLedger`、`BudgetTransaction`、`DailyCostSummary`、`InfrastructureCost`）。

### 6.5 测试

新增 8 个测试类：`SpendForecastServiceTest`(3)、`ModelRouterTest`(6)、`PromptOptimizerTest`(9)、`BudgetThresholdAlertServiceTest`(6)、`CostProtectionEngineTest`(4)、`AnomalyDetectionServiceTest`(9)、`GovernancePersistenceIT`(7)、`GovernanceRedisIT`(6)；`RedisBudgetIT` 6 → 7 例。

### 6.6 文档

| 文档 | 状态 |
|---|---|
| `docs/audit/PROJECT_AUDIT.md` | **新增**（改造前形态、6 项已有能力、T1~T4 技术债、G1~G6 能力缺口、环境与端口审计、8 条开发约束、「假实现」风险自查 8 项） |
| `docs/architecture/ADR/ADR-002-protection-self-lock.md` | **新增**（自锁缺陷完整分析 + 4 个放弃的备选方案） |
| `docs/architecture/ADR/ADR-003-redis-mysql-final-consistency.md` | **新增**（预扣/结算/释放/对账四步顺序 + 一致性等级声明表 + 4 个放弃方案） |
| `docs/architecture/AI_COST_ARCHITECTURE.md` | 大改（八大能力映射表、链路 11→16 步、约束 6→12 条） |
| `docs/api/AI_COST_API.md` | 追加第七~十六章（V2 错误码 `40302`/`42901`、全部管理接口契约、前端 10 页路由对照） |
| `docs/database/TABLE_DICTIONARY.md` | 追加 168 行（3 表新增列 + 8 张新表逐字段字典 + 索引） |
| `docs/database/DATABASE_DESIGN.md` | 追加第十~十三章（13 表分类、5 条关键决策、V5 种子、集成测试覆盖清单） |
| `docs/acceptance/TEST_REPORT.md` | 重写为 V2.0 口径（72 项测试 + 五档预警 + 恢复/紧急开关 + 浏览器回归） |
| `docs/acceptance/FINAL_ACCEPTANCE_REPORT.md` | 本文件，重写为 V2.0 |

## 7. ADR 索引

| 编号 | 主题 | 结论 |
|---|---|---|
| `ADR-001` | RAG 可选装配 | `@ConditionalOnProperty` 按需装配，`rag.enabled=false` 时无需 Milvus |
| `ADR-002` | 保护自锁 | 风险判定排除保护引擎自身告警 + `resume()` 清理；4 个备选方案（只清理不改判定、只改判定不清理、把 `PROTECTION_ACTION` 从 CRITICAL 降到 HIGH、加 `source='ENGINE'` 独立字段）均放弃，理由见文档 |
| `ADR-003` | Redis ↔ MySQL 最终一致 | 明确声明「预算强一致 / 账单强一致 / Redis↔MySQL 最终一致 ≤5min」；放弃分布式事务、纯 MySQL 行锁、outbox、自动修正差异 |

## 8. 未完成事项与风险清单

| # | 事项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 真实报价口径的财务结算 / 对账 | **未完成** | 真实调用已通过，但成本按 `DEMO-v1` 示例价计算 |
| 2 | RAG 检索端到端 | **未完成** | 本轮未触碰；本机 Milvus 的 MinIO 9000 端口被其它项目占用 |
| 3 | Windows `.bat` 脚本实测 | **未完成** | 未在 macOS 实测 |
| 4 | Vue 组件级单测（Vitest） | **未完成** | 第 2.8 节为 DOM 断言，不是组件测试 |
| 5 | 性能压测 | **未做** | 无 QPS / 延迟数据，见 `PERFORMANCE_REPORT.md` |
| 6 | `DOWNGRADE_MODEL` 实际降档验证 | **未完成** | 只验证路由档位下发，未接真实模型确认调用的是低价模型 |
| 7 | 告警「确认」后是否仍重复告警 | **未完成** | 去重只验证了 `OPEN` 状态 |
| 8 | 对账差异场景 | **未完成** | 未主动制造 Redis↔MySQL 差异验证 `RECONCILE_DIFF` |
| 9 | 示例价格 `DEMO-v1` | **风险** | 全部 `demo_flag=1`，禁止用于真实财务结算；上线前按合同价替换并置 `demo_flag=0` |
| 10 | 多用户真实认证 | **风险** | 固定 `demo-user`，生产必须替换 `UserIdentityProvider`，不允许前端传 `userId` |
| 11 | 按月分表 | **未实现** | 单表 + 索引 + 92 天跨度限制 |
| 12 | Redis 与 MySQL 一致性 | **已知边界** | 最终一致、非强一致；差异 >0.01 USD 只告警不自动修正（见 `ADR-003`） |
| 13 | 流式调用 | **未完成** | 只实现同步 `chatModel.call()`，不做假统计 |
| 14 | 管理令牌与前端 | **已知限制** | 前端未注入 `X-Admin-Token`；配置 `AI_ADMIN_TOKEN` 后需自行补请求头 |

## 9. 结论

V2.0 在 **`rag.enabled=false` + 真实 MySQL/Redis + DEMO 价格 + 固定 Demo 身份** 前提下，把 V1.0 的「成本可算、预算可控」扩成**八大模块联动的治理平台**，并完成端到端验证：

- **能力面**：13 张表 / 5 个迁移 / 13 个 Mapper XML / 99 个 Java 文件 / 10 个前端页面 / **34 个管理接口**；九级预算作用域、19 种告警、五级保护、8 条优化策略全部落地并有种子数据。
- **验证面**：`mvn -B -Pintegration verify` **72 项测试全绿**；**34 个管理接口 `code=0`（0 失败）**；五档阈值 50/70/85/95/100% 逐档实测命中且去重正确；告警自动恢复 5 档全部 `RESOLVED`；紧急开关「缺原因拒绝 → 停用阻断 → 恢复放行 → 审计留痕」全链路通过；前端静态门禁 + 6 页浏览器 DOM 断言通过。
- **质量面**：本轮定位并修复**一处真实功能缺陷**（保护等级自锁），补了 2 个方向相反的回归用例防止过度放宽，并写了 `ADR-002` 记录取舍；一致性边界写成 `ADR-003`，不含糊其辞。
- **诚实边界**：DEMO 示例价、Demo 身份、Redis 最终一致、RAG 未端到端、无压测数据——这些都在第 8 节逐条列出，**不宣称生产可用、不宣称真实财务结算通过**。

本版本定位为**可运行、可核对、可演练的企业级成本治理工程基线**；上生产前必须替换真实价格与真实身份、补齐压测与组件级测试。
