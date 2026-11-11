# 企业级大模型成本控制与治理系统 V1.0 · 分层架构

> 本文件对应代码路径：
> - 启动与装配：`src/main/java/com/example/milvusdemo/DemoApplication.java`、`src/main/java/com/example/ai/config/`
> - 业务编排：`src/main/java/com/example/ai/chat/service/AiChatService.java`
> - 领域模块：`src/main/java/com/example/ai/token/`（usage / pricing / budget / cost / analytics / agent / infra）、`src/main/java/com/example/ai/optimize/`、`src/main/java/com/example/ai/guard/`、`identity/`、`metrics/`、`common/`
> - 配置与迁移：`src/main/resources/application.yml`、`src/main/resources/db/migration/`、`src/main/resources/mapper/`、`src/main/resources/lua/`
> - 前端：`frontend/src/`
> - 原有 RAG 代码：`src/main/java/com/example/milvusdemo/`（保留未删）
>
> 本文只描述已存在的代码结构。改造前的缺口盘点见 `docs/audit/PROJECT_AUDIT.md`；验收范围与结论见 `docs/acceptance/FINAL_ACCEPTANCE_REPORT.md`。

## 1. 系统定位与边界

本项目由原 `spring-ai-milvus-demo`（Spring AI + Milvus + Tika 的 RAG 学习工程）升级，V1.0 完成「单次调用算得清」，V1.1 在此之上扩展为「**企业级大模型成本控制与治理系统**」。核心职责：

**说清楚钱花在哪 → 找出浪费 → 用技术手段压下来 → 按维度设限 → 失控前拦住 → 事后可复盘。**

平台边界：

- 承担：成本核算（含 RAG / Agent / 基础设施分摊）、成本优化（含收益口径分级）、九级预算、预算强制执行（原子预扣/限流/并发/幂等）、成本预警（阈值/速度/异常）、自动保护（五级 + 紧急开关 + 审计）、成本分析、10 页管理台。
- 不承担：真实多用户认证（当前为固定 Demo 身份）、真实供应商账单对账、按月分表、RAG 语义检索质量的端到端验证、基于机器学习的异常检测（只做可解释规则）。
- 不修改：原有 `com.example.milvusdemo` 的 RAG / Milvus / Tika 代码，一行未删，仅改为按需装配。

> **验收状态（实测补充）**：已完成 8091 实例的治理链路端到端验证（缓存命中零成本、七作用域预算、ACTUAL/SAVED 分口径账务、五档阈值预警、紧急开关自锁修复）。另用 `AI_PROVIDER=openai` + DeepSeek 兼容接口在 8092 实例完成真实模型调用，`usageStatus=REAL`、账单落库。**所有价格为 `DEMO-v1` 示例价，不是供应商真实报价，不构成真实计费对账验收**。详见 `docs/acceptance/TEST_REPORT.md`。

## 2. 分层架构

| 层 | 包 / 目录 | 职责 | 关键类 |
|---|---|---|---|
| 接入层 | `ai.chat.controller`、`token/*/controller`、`optimize/CostOptimizationController`、`token/agent/AgentCostController` | HTTP 入参校验、统一响应封装；业务不写在这里 | `AiChatController`、`CostGovernanceController`、`TokenUsageAdminController`、`BudgetAdminController`、`CostAlertController`、`CostProtectionController`、`CostOptimizationController`、`AgentCostController`，以及保留的旧 `CostAnalyticsController` / `BudgetController` / `PricingController` |
| 过滤器 / 拦截器 | `ai.config` | traceId 注入、`/api/admin/**` 权限保护 | `TraceIdFilter`、`AdminAccessInterceptor`、`WebConfig` |
| 业务编排层 | `ai.chat.service` | 一次问答的 15 步链路与失败补偿 | `AiChatService` |
| 优化引擎 | `ai.optimize` | 在不改语义的前提下降低单位成本 | `PromptOptimizer`、`ContextCompressor`、`RagContextOptimizer`、`ResponseCacheService`、`EmbeddingCacheService`、`ModelRouter`、`TokenEstimator`、`CostOptimizationService` |
| 防护层 | `ai.guard` | 限流、并发槽位 | `AIRequestProtectionService` |
| 领域服务层 | `ai.token.*` | Usage、定价、成本、预算、告警、保护、预测、异常、统计 | `TokenUsageCollector`、`CostCalculator`、`ModelPricingService`、`TokenBudgetService`、`BudgetReconciler`、`CostLedgerService`、`CostAlertService`、`BudgetThresholdAlertService`、`CostProtectionEngine`、`SpendForecastService`、`AnomalyDetectionService`、`AgentCostTracker`、`InfrastructureCostService`、`DailySummaryService`、`CostAnalyticsService`、`CostGovernanceService` |
| 策略 / 适配层 | `token/usage/adapter` | 屏蔽供应商 Usage 字段差异 | `ProviderUsageAdapter`、`OpenAiUsageAdapter`、`MockUsageAdapter` |
| 基础设施层 | `token/*/mapper` + `resources/mapper/*.xml`、`resources/lua/*.lua` | SQL 集中管理、Redis 原子脚本 | 13 个 Mapper XML、4 个 Lua 脚本 |
| 持久化 | MySQL 8.0、Redis | 账单/流水/策略/价格/告警/日志的持久化；预算并发计数 | **13 张业务表**，见 `docs/database/DATABASE_DESIGN.md` |
| 可观测 | `ai.metrics`、`Actuator` | 自定义指标与健康检查 | `AiMetrics`（含 cacheHits / protectionBlocked / rateLimited / routeDowngrades） |
| 前端 | `frontend/src/views`、`api`、`router` | **10 个管理页面**，全部走真实 API | `router/index.ts`、`views/*.vue`、`api/governance.ts` |

统一响应结构由 `common/ApiResponse.java` 定义：`{code, message, data, traceId}`；`common/GlobalExceptionHandler.java` 保证异常也返回同一结构；`common/Views.java` 的统计 VO 统一把金额输出为字符串、比率输出为 0~1 小数。

## 3. 模块边界

`com.example.ai` 子包按业务能力划分，互不反向依赖：

```
com.example.ai
├── common       统一响应、错误码、枚举（ProtectionLevel/MeasureKind/CostKind/LedgerEntryType…）、分页、金额刻度、VO
├── config       属性、模型装配、Redis Lua 加载、RAG 开关、Web/安全配置
├── metrics      Micrometer 自定义指标
├── identity     用户身份入口（接口 + Demo 实现）
├── chat         问答入口、DTO、编排、Mock 模型
├── guard        限流窗口与并发槽位（fail-open）
├── optimize     Prompt/上下文/RAG 压缩、响应缓存、Embedding 去重、模型路由、收益试算
└── token
    ├── usage      采集器、供应商适配、账单实体与查询
    ├── pricing    价格版本、生效时间、CRUD
    ├── budget     九级预算策略、预扣流水、Lua 原子控制、对账、结算事件
    ├── cost       三段计价、成本拆解、追加式账务、告警中心、阈值预警、保护引擎、消费预测、异常检测
    ├── agent      Agent 任务级成本追踪与六道闸门
    ├── infra      基础设施成本登记与按请求量分摊
    └── analytics  统计查询、五维度日汇总、治理视图聚合
```

- 身份来源唯一：`UserIdentityProvider`，业务层不信任前端传入的 `userId`（`AiChatService.chat()` 只取服务端身份）。
- 价格来源唯一：`ai_model_pricing` 表，代码不硬编码单价；`ModelPricingService.findEffective()` 按调用时刻取生效版本。
- 账单写入唯一：`TokenUsageCollector.collect()`，Controller 不自己算 Token、不自己写库。
- 账务流水唯一：`CostLedgerService`，**只 INSERT**，冲正走 `ADJUST` 反向流水，绝不 UPDATE 历史金额。
- 预算并发控制唯一：`TokenBudgetService` 的 4 个 Lua 脚本，业务代码不直接读写预算计数 key。
- 保护等级唯一：`CostProtectionEngine.currentLevel()`，其它模块不得自行判断「是否该降级」。

## 4. 完整业务链路（15 步）

以 `POST /api/ai/chat` 为例，链路全部位于 `AiChatService.chat()`：

```
HTTP 请求
  │  TraceIdFilter 写入 MDC traceId
  ▼
AiChatController（@Valid 校验） → ApiResponse 统一封装
  ▼
AiChatService.chat()
  ├─ ① identityProvider.currentUserId()            // 服务端身份，忽略前端 userId
  ├─ ② protectionEngine.evaluate(scene)            // 等级 5 直接拒（40302），Agent 场景按等级停
  ├─ ③ modelRouter.route(...)                      // 复杂→L3 / 简单→L2；保护等级≥3 强制 L1
  ├─ ④ requestProtection.acquire(...)              // 限流（分钟/小时/天）+ 全局/模型并发槽位；失败 fail-open
  ├─ ⑤ responseCache.lookup(...)                   // 命中 → 零模型调用零成本，标 CACHED + SAVED 流水，直接返回
  ├─ ⑥ perRequestLimit 校验                        // 单次成本上限，超过直接拒
  ├─ ⑦ budgetService.reserve(...)                  // Lua 原子预扣 7 个作用域；不足 → 40301
  ├─ ⑧ agentCostTracker.guard(...)                 // 仅 AGENT 场景：步数/工具/Token/成本/时长/循环
  ├─ ⑨ RAG 检索 + ragContextOptimizer.optimize()   // 片段去重、碎片过滤、Token 预算截断
  ├─ ⑩ contextCompressor + promptOptimizer         // 多轮压缩、空白归一、重复行删除
  ├─ ⑪ chatModel.call(prompt)（有限重试）           // 只重试 ConnectException；超时不重试（可能已计费）
  ├─ ⑫ collector.extract() → collect()             // 读真实 Usage → 三段计价 → 幂等落 MySQL + ACTUAL 流水
  ├─ ⑬ budgetService.settle(实际成本)               // Lua 幂等结算；超预扣写 SETTLE_OVERRUN；发布结算事件
  ├─ ⑭ responseCache.store(...)                    // 回填缓存 + 记 SAVED 流水（仅当真实产生节省）
  ├─ ⑮ costOptimizationService.log(...)            // 记录优化收益，标 MEASURED/COUNTERFACTUAL/ESTIMATED
  └─ ⑯ 组装 ChatResponseVO（routeLevel/cacheHit/cacheSavedCost/protectionLevel/optimization/usage/cost/budget）

后台（@EnableScheduling）
  BudgetReconciler.cleanup()          每 60s   ：Lua 清理过期预扣
  BudgetReconciler.reconcile()        每 300s  ：Redis 与 MySQL 当日消耗对账，差异 >0.01 USD 写 RECONCILE_DIFF
  BudgetThresholdAlertService.sweep() 每 120s  ：预算使用率越线/回落扫描（另有结算事件触发）
  AnomalyDetectionService.scan()      每 600s  ：6 条可解释异常规则
  SpendForecastService.monthly()      每 1800s ：超支预测 + 消费速度突增
  DailySummaryService.rebuild()       每小时 5 分：五维度日汇总重建
```

失败策略分离（写代码时不可混用）：

| 组件 | Redis/依赖异常时的行为 | 原因 |
|---|---|---|
| 限流 / 并发槽位 | **fail-open**（放行） | 限流是保护措施，不该因为限流器故障导致全站不可用 |
| 预算预扣 / 结算 | **fail-closed**（拒绝调用） | 未经预算检查的付费调用不可接受 |
| 告警写入 | 吞异常 + 记日志 | 观测失败不能反过来让主链路失败 |

一致性口径：Redis 是并发控制的真源，MySQL 是审计流水与账单持久层，两者**不是同一本地事务强一致**，通过「先预扣 → 调用 → 幂等结算 → 幂等落库 → 定期对账」达到最终一致。详见 `docs/architecture/ADR/ADR-003-redis-mysql-final-consistency.md`。

## 5. 八大能力与代码位置映射

| # | 能力 | 核心实现 | 对外接口 | 落地表 |
|---|---|---|---|---|
| 1 | **成本核算** | `CostCalculator`（三段计价）、`CostLedgerService`（追加式账务）、`RagContextOptimizer`（RAG Pipeline 成本）、`AgentCostTracker`（Agent 成本）、`InfrastructureCostService`（分摊） | `/api/admin/cost/breakdown`、`/models`、`/scenes`、`/users`、`/infrastructure` | `ai_cost_ledger`、`ai_agent_cost_trace`、`ai_infrastructure_cost`、`ai_token_usage` |
| 2 | **成本优化** | `PromptOptimizer`、`ContextCompressor`、`RagContextOptimizer`、`ResponseCacheService`、`EmbeddingCacheService`、`ModelRouter`、`CostOptimizationService` | `/api/admin/cost-optimization/strategies`、`/execute`、`/history`、`/api/admin/cost/optimization` | `ai_cost_optimization_log` |
| 3 | **预算管理** | `TokenBudgetService.applicableScopes()`（九级）、`BudgetPolicy` | `/api/admin/budget/page`、`/status`、`/transactions` | `ai_budget_policy`、`ai_budget_reservation`、`ai_budget_transaction` |
| 4 | **预算强制执行** | `budget_{reserve,settle,release,cleanup}.lua`、`BudgetReconciler`、`AIRequestProtectionService` | 无独立接口，由 `/api/ai/chat` 链路强制 | 同上 |
| 5 | **成本预警** | `BudgetThresholdAlertService`（50/70/85/95/100%）、`SpendForecastService.monthly()/checkVelocity()`、`AnomalyDetectionService`（6 规则）、`CostAlertService`（去重/确认/恢复） | `/api/admin/cost-alert/page`、`/{id}/acknowledge`、`/{id}/resolve`、`/evaluate`、`/anomaly-scan`、`/api/admin/cost/forecast` | `ai_cost_alert` |
| 6 | **自动保护** | `CostProtectionEngine`（五级 + 紧急开关 + 审计） | `/api/admin/cost-protection/status`、`/rules`、`/rules/{code}`、`/emergency-stop`、`/resume`、`/logs` | `ai_cost_protection_rule`、`ai_cost_protection_log` |
| 7 | **成本分析** | `CostGovernanceService`、`CostAnalyticsService`、`DailySummaryService`、`CostAnalyticsMapper.tokenStats/userCost` | `/api/admin/cost/overview`、`/trend`、`/daily-summary`、`/rebuild`、`/api/admin/token-usage/*` | `ai_cost_daily_summary` |
| 8 | **Dashboard** | `frontend/src/views` 10 页 | — | 只读 |

## 6. 技术选型理由

| 选型 | 版本 | 在本平台的理由 |
|---|---|---|
| Java / Spring Boot | 17 / 3.4.5 | 继承原工程基线；`record`、模式匹配简化 DTO；Actuator 原生提供 health/info/metrics |
| Spring AI | 1.0.0 GA | 统一 `ChatModel` 抽象；`ChatResponse.getMetadata().getUsage()` / `getModel()` 是真实 Usage 的唯一来源，避免按字符串长度伪造 Token |
| MyBatis | 3.0.4 | 统计 SQL 集中在 `mapper/*.xml`，便于按索引设计查询、`ORDER BY` 走白名单；SQL 不散落在 Controller/Service |
| Flyway | 随 Boot 管理 | 版本化迁移 `V1`（建表）/ `V2`（DEMO 种子数据）/ `V3`（供应商别名模型补价），`validate-on-migrate` 防止结构漂移 |
| MySQL 8.0 | Docker，宿主 13307 | `DECIMAL(20,8)` 保证金额精度、`BIGINT` 存 Token、`DATETIME(3)` 存 UTC 时间、唯一约束保证 `request_id` 幂等、COMMENT 便于生成数据字典 |
| Redis + Lua | 本机 6379 | 单次 Lua 内「先校验多个作用域、再一次性扣减」，杜绝并发超额与部分成功；金额转 1e-8 USD 整数规避 Lua 浮点误差 |
| Apache Tika | 3.3.2 | 原 RAG 工程的文档摄取能力保留，供 `rag.enabled=true` 时复用 |
| Vue3 + TS + Vite + Pinia + Vue Router + Axios + ECharts | — | 类型安全的管理台；Vite 代理 `/api` → 8091；`vue-tsc --noEmit` 做静态类型校验 |

## 7. 与原 RAG 项目的关系

- **代码保留**：`com.example.milvusdemo` 下的 `DemoApplication`、`LocalHashingEmbeddingModel`、`DocumentIngestionService`、`MilvusLearningService`、`MilvusController`、`RagController` 全部保留。
- **扫描合并**：启动类改为 `@SpringBootApplication(scanBasePackages = {"com.example"})`，同时覆盖新旧包；`@MapperScan` 显式列出 5 个新 mapper 包；`@EnableScheduling` 开启对账任务。
- **RAG 可选装配**：`MilvusVectorStoreAutoConfiguration` 启动期会强制连接 Milvus，因此主类默认 `exclude`；`RagConfiguration` 在 `rag.enabled=true` 时 `@Import` 恢复；原 4 个 RAG Bean 加了 `@ConditionalOnProperty(prefix="rag", name="enabled", havingValue="true")`。详见 `docs/architecture/ADR/ADR-001-rag-optional-assembly.md`。
- **默认独立**：`application.yml` 中 `rag.enabled=false`，成本平台不依赖 Milvus 即可启动；`AiChatService.retrieveContext()` 用 `ObjectProvider` 取 RAG 服务，取不到时只记日志并降级为无知识库回答，不阻断问答。

> 说明：本次验收在 `rag.enabled=false` 下进行，**RAG 检索未做端到端验证**，仅验证了「未装配时问答链路可降级」。

## 8. 关键约束（写代码时不可突破）

1. 不按字符串长度估算 Token；无 Usage 一律 `UNAVAILABLE`。`TokenEstimator` 只用于同一次请求内的相对比较（预扣估算、压缩收益），**绝不作为账单依据**。
2. 金额计算全 `BigDecimal`，缓存输入是输入子集必须扣除；找不到价格标 `UNPRICED` 并告警，不编造单价。
3. 账单保存 `pricingVersion` / `pricingId` 快照，改价不影响历史。
4. Redis 异常按安全失败拒绝调用，不放行付费模型；限流器异常则放行（fail-open），两者不可混淆。
5. API Key 只走环境变量 `AI_API_KEY`；`/api/admin/**` 受 `AdminAccessInterceptor` 保护；统计接口强制最大 92 天跨度、分页最大 100、排序白名单；不记录完整 Prompt。
6. 示例价格全部 `demo_flag=1`（DEMO-v1），**不是供应商真实报价**，禁止据此做真实财务结算。
7. **口径不可混用**：`ACTUAL`（供应商真实账单）/ `ALLOCATED`（基础设施分摊）/ `SAVED`（节省，不入成本合计）三者分列；收益侧 `MEASURED` / `COUNTERFACTUAL` / `ESTIMATED` 三者分列。任何把估算当实测、把分摊当账单的写法都视为缺陷。
8. **保护等级只能收紧不能放宽**：任何模块不得绕过 `CostProtectionEngine.evaluate()` 的结论自行放行；降级后的模型仍需重新走预算预扣。
9. **Agent 不得无上限**：`AgentCostTracker` 六道闸门任一触顶即停止并写 `stopReason`，不允许「再试一次」。
10. **重试只重试明确未送达的失败**：仅 `ConnectException`；超时 / 读失败不重试（供应商可能已计费）。
11. 追加式账务只 INSERT，冲正走 `ADJUST` 反向流水，禁止 UPDATE 历史金额。
12. 告警去重必须带判别位：同「类型 + 对象 + 等级」但业务上属于不同事件的（如 50% 与 70% 两档），必须用 `discriminator` 区分，否则会被合并。
