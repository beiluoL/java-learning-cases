# 数据库设计

> 本文件对应代码路径：
> - DDL：`src/main/resources/db/migration/V1__init_ai_cost.sql`（5 张表、索引、COMMENT）
> - 数据迁移：`src/main/resources/db/migration/V2__seed_demo_data.sql`（DEMO-v1 价格与默认预算策略）、`V3__add_provider_alias_demo_pricing.sql`（别名模型补价）
> - Flyway 配置：`src/main/resources/application.yml`（`spring.flyway`）、`pom.xml`（`flyway-core` / `flyway-mysql`）
> - 连接与连接池：`application.yml`（`spring.datasource`）、`infra/docker/ai-cost-compose.yml`
> - 实体与 SQL：`src/main/java/com/example/ai/token/*/entity/`、`src/main/resources/mapper/*.xml`
> - 金额刻度：`src/main/java/com/example/ai/common/MoneyScale.java`；时间处理：`src/main/java/com/example/ai/config/JacksonConfig.java`、`AiModelConfig.java`（`Clock.systemUTC()`）
> - 集成验证：`src/test/java/com/example/ai/token/usage/MysqlPersistenceIT.java`
>
> 逐字段字典见 `docs/database/TABLE_DICTIONARY.md`（严格以 V1 DDL 为准）。

## 1. 总体约定（V1 DDL 头部注释）

| 约定 | 取值 | 理由 |
|---|---|---|
| 引擎 / 字符集 | `InnoDB` / `utf8mb4` | 事务、行锁与中文兼容 |
| 金额 | `DECIMAL(20,8)`，统一 USD | 8 位小数与 `MoneyScale.SCALE=8`、Redis 1e-8 整数单位一致；禁止 float/double |
| Token | `BIGINT` | 累计 Token 可能超过 int 范围 |
| 时间 | `DATETIME(3)`，一律 UTC | 毫秒精度；全局统一时区，避免跨时区统计错位 |
| 注释 | 所有表与关键字段带 `COMMENT` | 便于生成数据字典与运维排查 |
| 幂等 | `request_id` 唯一约束 | 同一请求不重复计费、不重复写预扣流水 |

## 2. 五张表概览

| 表 | 用途 | 主键 | 唯一约束 | 主要索引 |
|---|---|---|---|---|
| `ai_model_pricing` | 模型价格及历史版本 | `id` | `uk_pricing_version(provider, model_name, version)` | `idx_pricing_lookup`、`idx_pricing_effective` |
| `ai_token_usage` | AI 调用明细与成本账单 | `id` | `uk_usage_request(request_id)` | `idx_usage_created`、`idx_usage_user_scene`、`idx_usage_model`、`idx_usage_status_scene` |
| `ai_budget_policy` | 预算策略配置 | `id` | `uk_budget_scope(budget_type, target_id)` | `idx_budget_enabled` |
| `ai_budget_reservation` | 预算预扣 / 结算 / 异常状态流水 | `id` | `uk_reservation_request(request_id)` | `idx_reservation_status_expire`、`idx_reservation_user` |
| `ai_cost_alert` | 预算预警与成本异常事件 | `id` | — | `idx_alert_created`、`idx_alert_type` |

表之间的关联是**逻辑关联**（通过 `request_id` / `pricing_id` 等字段），未建数据库外键：账单写入高频，外键会增加锁竞争，且 Redis 与 MySQL 为最终一致，跨存储的完整性由应用层幂等与对账保证。

## 3. 逐表设计要点

### ai_model_pricing

- 价格按「供应商 + 模型名 + 版本」唯一，`effective_from` / `effective_to` 定义生效区间（UTC），`enabled` 控制启停，`demo_flag` 标记示例价。
- `ModelPricingMapper.xml` 的 `findEffective` 以 `model_name + model_type` 命中、`effective_from <= at < effective_to`、`enabled=1`，并优先精确 `provider`（兼容 DeepSeek 走 OpenAI 兼容接口等场景），取 `LIMIT 1`。
- `update` 只允许改价格字段、`effective_to`、`enabled`、`demo_flag`，**禁止改 `provider` / `model_name` / `version`**，保证历史版本可追溯。
- V2 种子共 6 条 `DEMO-v1` 价格（deepseek ×2、openai、mock、local ×2）；V3 追加 1 条别名模型 `openai/deepseek-flash` 的 `DEMO-v1` 价格（供应商实际返回的模型名，见第 8、9 节）。全部 `demo_flag=1`，**不是供应商真实报价**，禁止据此做真实财务结算。

### ai_token_usage

- 每次 AI 调用一条明细，含 Usage 三段 Token、来源口径 `usage_status`、请求状态 `request_status`、耗时、三段成本与合计、价格版本快照。
- `uk_usage_request(request_id)` 配合 `TokenUsageMapper.xml` 的 `ON DUPLICATE KEY UPDATE request_id = request_id` 实现幂等：重复写入不新增、也不改写历史账单。
- 4 组索引分别服务：时间范围统计、按用户 + 场景、按供应商 + 模型、按请求状态 + Usage 状态。`CostAnalyticsMapper.xml` 的所有统计 SQL 都带 `created_at` 范围，命中 `idx_usage_created`。
- 默认不存完整 Prompt（保护敏感内容）。

### ai_budget_policy

- `budget_type ∈ {GLOBAL, USER, MODEL, DAILY, MONTHLY}`，`target_id` 默认 `__ALL__`；`uk_budget_scope` 保证一个作用域只有一条策略。
- 额度字段 `daily_limit` / `monthly_limit` / `per_request_limit` 与 Token 上限 `max_input_tokens` / `max_output_tokens` 分开；`allow_overrun` 默认 0（严格拦截）。
- V2 默认 3 条策略：GLOBAL（日 50 / 月 1000）、DAILY:demo-user（日 5.00）、MONTHLY:demo-user（月 100.00）。日预算 5.00 USD 是 `RedisBudgetIT` 100 并发用例的额度基准。

### ai_budget_reservation

- 记录预扣流水的状态机 `RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED`（见 `docs/architecture/BUDGET_CONTROL_DESIGN.md`）。
- `uk_reservation_request(request_id)` 保证同一请求只有一条流水；`idx_reservation_status_expire(status, expire_at)` 直接服务「查过期预扣」的定时清理。
- `settled_amount` 可空，未结算时为 NULL；`expire_at` 为 UTC 过期时间。

### ai_cost_alert

- 告警类型 `BUDGET_EXCEEDED / COST_SPIKE / SETTLE_OVERRUN / UNKNOWN_USAGE / REDIS_FAILURE / RECONCILE_DIFF`，`severity` 默认 `WARN`。
- `message` 明确「不含敏感 Prompt 与密钥」；`request_id` 可空（如系统级对账告警）。

## 4. 索引设计（以 V1 DDL 为准）

| 索引 | 表 | 列 | 服务场景 |
|---|---|---|---|
| `idx_pricing_lookup` | pricing | `provider, model_name, model_type, enabled, effective_from` | 按模型查生效价 |
| `idx_pricing_effective` | pricing | `effective_from, effective_to` | 生效区间过滤 |
| `idx_usage_created` | usage | `created_at` | 全部统计接口的 92 天内范围扫描 |
| `idx_usage_user_scene` | usage | `user_id, business_scene, created_at` | 按用户 / 场景筛选 |
| `idx_usage_model` | usage | `provider, model_name, created_at` | 按模型聚合 |
| `idx_usage_status_scene` | usage | `request_status, usage_status, created_at` | 成功 / 失败 / MOCK 口径过滤 |
| `idx_budget_enabled` | policy | `enabled, budget_type` | 查启用策略 |
| `idx_reservation_status_expire` | reservation | `status, expire_at` | 过期预扣清理 |
| `idx_reservation_user` | reservation | `user_id, created_at` | 按用户查流水 |
| `idx_alert_created` | alert | `created_at` | 最近告警 |
| `idx_alert_type` | alert | `alert_type, created_at` | 按类型查告警 |

## 5. 金额精度

- 数据库侧：`DECIMAL(20,8)`，8 位小数，USD。
- 计算侧：`CostCalculator` 全程 `BigDecimal`，单价除以 1_000_000 时中间保留 24 位精度（`INTERNAL_SCALE=24`）避免提前舍入，最终 `MoneyScale.normalize()` 统一 `setScale(8, HALF_UP)`。
- Redis 侧：`MoneyScale.toUnits()` 把金额 × 1e-8 转整数供 Lua 运算，`fromUnits()` 还原，避免 Lua 浮点误差；`MAX_BUDGET=10_000_000 USD` 保证整数可精确表示。
- 输出侧：`Views.java` 的统计 VO 金额字段为 `String`（`toPlainString()` / `Views.money()`），避免 JS 浮点精度丢失；比率单独用 `double`（0~1）。

## 6. UTC 时间

- 列类型统一 `DATETIME(3)`，`created_at` / `updated_at` 用 `CURRENT_TIMESTAMP(3)`（部分带 `ON UPDATE CURRENT_TIMESTAMP(3)`）。
- 应用侧 `AiModelConfig` 提供 `Clock.systemUTC()`；JDBC URL 带 `serverTimezone=UTC`；`ai-cost-compose.yml` 设 `TZ: UTC` 且 MySQL `--default-time-zone=+00:00`。
- 实体用 `LocalDateTime` 存 UTC 的「无时区」时间；`JacksonConfig` 兼容前端 ISO 8601（带 `Z`）反序列化，避免 400；`CostAnalyticsService.alerts()` 输出时再按 `ZoneOffset.UTC` 格式化为带时区字符串。
- 预算 DAILY / MONTHLY 的 Redis key 按 `LocalDate.now(clock)` / `YearMonth.now(clock)` 分键，跨天跨月自然重置，与 UTC 口径一致。

## 7. 唯一约束与幂等

| 约束 | 作用 |
|---|---|
| `uk_usage_request(request_id)` | 账单幂等；配合 `ON DUPLICATE KEY UPDATE request_id = request_id` |
| `uk_reservation_request(request_id)` | 预扣流水幂等；`persistReservation()` 捕获 `DuplicateKeyException` 忽略重复写 |
| `uk_pricing_version(provider, model_name, version)` | 同一价格版本不重复 |
| `uk_budget_scope(budget_type, target_id)` | 一个预算作用域只有一条策略 |

`MysqlPersistenceIT.requestIdUniqueConstraintAndDecimalPrecision()` 验证：同一 `requestId` 重复写入被唯一约束拒绝，且 `DECIMAL(20,8)` 的 `0.00345678` 读出后精度不丢失。

## 8. Flyway 版本策略

- 迁移脚本位于 `classpath:db/migration`，当前三版（**V1~V3**）：
  - `V1__init_ai_cost.sql`：建 5 张表、索引、COMMENT（**唯一改表结构的版本**）。
  - `V2__seed_demo_data.sql`：插入 6 条 DEMO 价格与 3 条默认预算策略（**不改表结构**）。
  - `V3__add_provider_alias_demo_pricing.sql`：为供应商返回的别名模型 `deepseek-flash` 补 1 条 `DEMO-v1` 价格（**不改表结构**）。
- **V3 的用途**：供应商实际返回的模型名可能与请求配置不同（实测请求 `AI_MODEL=deepseek-chat`、DeepSeek 返回 `deepseek-flash`），账单按 `actualModel` 记 `model_name`，价格表必须为该别名提供可计费版本。首次真实调用因缺该价格被 `CostCalculator` 正确标 `UNPRICED` 并告警（0 成本，防止用错价格的安全行为），补 V3 后第二次调用正常计费。V3 单价同样是 DEMO 示例价，**不是供应商真实报价**。
- `application.yml`：`enabled=true`、`baseline-on-migrate=true`、`validate-on-migrate=true`（结构漂移会校验失败）。
- 约定：已执行过的迁移脚本**只追加新版本、不回改**；需要变更结构时新增更高版本号，避免 checksum 校验失败。
- `MysqlPersistenceIT.flywayCreatedAllFiveTablesWithFlywayHistory()` 验证 5 张表存在且 `flyway_schema_history` 成功记录 ≥ 2 条；当前实测库中有 **1 / 2 / 3 三条成功记录**（V1 / V2 / V3）。

## 9. 历史价格快照

- 价格表用 `version` + `effective_from` / `effective_to` 管理多版本；账单在计费时把命中的 `pricing_version` 与 `pricing_id` 写入 `ai_token_usage` 作为**快照**。
- 因此后续调价、停用旧版本、新增版本都**不影响历史账单成本**；`usage/{requestId}` 详情接口返回 `costBreakdown` 时直接读账单快照，不重新按当前价格计算。
- 别名模型（如 `deepseek-flash`）同样需要独立的价格版本记录；价格表缺该记录时账单标 `UNPRICED` 并告警，不会退化到套用相近模型的价格。
- 当前全部价格为 `DEMO-v1` 且 `demo_flag=1`（含 V3 的别名模型），明确为示例价，上线前必须由运维按合同价替换并置 `demo_flag=0`。真实供应商调用（`usageStatus=REAL`）账单同样按示例价计费，**不构成真实报价口径的财务结算验收**。

---

# 附：治理平台 V1.0 数据库扩展（13 张业务表）

> 对应迁移：`V4__governance_schema.sql`（3 张既有表扩列 + 8 张新表）、`V5__governance_seed.sql`（种子数据）。
> 逐字段字典见 `docs/database/TABLE_DICTIONARY.md` 的「附：治理平台 V1.0 新增结构」。
> 本地实测：`flyway_schema_history` 共 5 条成功记录（V1~V5），业务表 13 张 + Flyway 元表 1 张。

## 10. 迁移后的表清单

| 分类 | 表 | 说明 |
|---|---|---|
| 账单与成本 | `ai_token_usage` | 调用明细（V4 扩列：agent/kb/cache/retry/route） |
| | `ai_cost_ledger` | **新增** 追加式账务流水，三口径分列 |
| | `ai_cost_daily_summary` | **新增** 五维度日汇总 |
| | `ai_infrastructure_cost` | **新增** 基础设施成本登记与分摊 |
| 价格 | `ai_model_pricing` | 价格版本（未改动） |
| 预算 | `ai_budget_policy` | 策略（V4 扩列：请求数/并发/Agent 上限/告警阈值/超额策略） |
| | `ai_budget_reservation` | 预扣状态（未改动） |
| | `ai_budget_transaction` | **新增** 每作用域一条变更流水 |
| 告警与保护 | `ai_cost_alert` | 告警中心（V4 扩 11 列：等级/状态/指纹/次数/确认/恢复） |
| | `ai_cost_protection_rule` | **新增** 保护规则 |
| | `ai_cost_protection_log` | **新增** 保护动作审计 |
| 优化与 Agent | `ai_cost_optimization_log` | **新增** 优化执行记录 |
| | `ai_agent_cost_trace` | **新增** Agent 任务级成本轨迹 |

## 11. 关键设计决策

### 11.1 追加式账务（`ai_cost_ledger`）而不是 UPDATE 账单

账务流水只 INSERT，`entry_no` 唯一（`LEDGER-{requestId}` / `SAVE-{requestId}`）保证幂等。冲正走 `ADJUST` 反向流水，**任何情况下不改历史金额**。

理由：成本平台最怕的不是算错，而是**算错了却没有痕迹**。追加式账务让「某笔钱为什么被记成这样」永远可回放；UPDATE 会把这个能力永久丢掉。

`cost_kind` 三值强制分列：
- `ACTUAL` —— 供应商真实账单，对应 `entry_type=MODEL_CALL`；
- `ALLOCATED` —— 基础设施分摊，**不参与** `apiCost` 合计；
- `SAVED` —— 节省额，`amount` 为正但**不计入**成本，只用于优化收益统计。

`/cost/breakdown` 分别返回 `apiCost` / `allocatedCost` / `savedCost` 三个独立字段，前端不得相加。

### 11.2 预算流水“每作用域一条”（`ai_budget_transaction`）

一次请求会同时命中若干作用域（DAILY / MONTHLY / USER / MODEL / GLOBAL / SCENE / KNOWLEDGE_BASE / AGENT，实测最多 7 个累计型 + REQUEST 单次上限）。如果只写一条汇总流水，事后无法回答「哪一级被扣了多少」。

因此每个作用域单独一条，`tx_no = TYPE-requestId-budgetType-targetId` 做幂等。`tx_type` 取值 `RESERVE / SETTLE / RELEASE / EXPIRE`，且**余额字段刻意留空**——并发下写快照余额会误导，实时余额统一由 `/budget/status` 从 Redis 读。

### 11.3 告警中心化（`ai_cost_alert` 扩 11 列）

去重键 `fingerprint`，形如 `BUDGET_THRESHOLD|BUDGET|CHAT|WARNING|85.00%`。同键未恢复（`OPEN`/`ACKNOWLEDGED`）只累加 `occurrence_count` 并刷新 `last_seen_at`，不新增行。

判别位（末尾那段）是必需的：50% 与 70% 两条规则的 `alert_level` 都是 `WARNING`，仅靠「类型+对象+等级」会被合并成一条，丢掉档位区分。

状态流转 `OPEN → ACKNOWLEDGED → RESOLVED`；**恢复（RESOLVED）不等于删除**，`resolved_at` 有值、记录保留，审计可查。

### 11.4 日汇总表（`ai_cost_daily_summary`）

`dimension` 五维度：`ALL / MODEL / USER / SCENE / KNOWLEDGE_BASE`，`dimension_key` 为对应取值（`ALL` 维度用 `__ALL__`）。

由 `DailySummaryService` 每小时第 5 分钟重建当日，也可 `POST /cost/daily-summary/rebuild` 手动触发。集成测试断言 **汇总金额 ≤ 明细合计**（`dailySummaryNeverExceedsDetailBilling`）而不是严格相等——因为汇总是定时快照，存在滞后，断言相等会在时序上伪失败。

### 11.5 Agent 轨迹唯一性（`ai_agent_cost_trace`）

`uk_agent_task(task_id)` 保证一个 Agent 任务只有一条轨迹，`steps / tool_calls / total_tokens / total_cost` 在原行上累加（这是唯一允许 UPDATE 金额聚合的表，因为它是「聚合视图」而非账单）；`stop_reason` 记录被哪道闸门拦下（`MAX_STEPS` / `MAX_TOOL_CALLS` / `MAX_TOKENS` / `MAX_COST` / `MAX_DURATION` / `LOOP_DETECTED`）。

## 12. 种子数据（V5）

| 内容 | 条数 | 说明 |
|---|---|---|
| 保护规则 | 5 | 50%→L2 NOTIFY、70%→L2 NOTIFY、85%→L3 DOWNGRADE_MODEL、95%→L4 DISABLE_AGENT、100%→L5 BLOCK_REQUEST |
| 场景预算 | 2 | `SCENE/CHAT`、`SCENE/RAG`（DEMO 额度） |
| Agent / 知识库预算 | 2 | `AGENT/__ALL__`、`KNOWLEDGE_BASE/default` |
| 基础设施成本 | 3 | 自建 Milvus / MySQL / Redis 月度成本（DEMO 金额） |
| 日汇总回填 | 若干 | `INSERT ... SELECT` 从真实账单回填，不编造数据 |

## 13. 集成测试覆盖

`GovernancePersistenceIT`（7 用例，真实 MySQL）：
1. `flywayMigratedAllGovernanceVersions` —— V1/V4/V5 全部 success；
2. `allGovernanceTablesExistWithDecimalMoney` —— 8 张新表存在且金额列为 `DECIMAL`；
3. `ledgerEntryIsIdempotentByEntryNo` —— 同 `entry_no` 重复插入不新增行、金额不被改写；
4. `protectionRulesCoverAllFiveLevels` —— 保护规则覆盖 ≥4 个等级；
5. `sceneAndAgentBudgetsAreSeeded` —— 场景/Agent 预算种子存在；
6. `dailySummaryNeverExceedsDetailBilling` —— 汇总不超明细；
7. `agentTaskTraceIsUniqueByTaskId` —— Agent 任务唯一。

`MysqlPersistenceIT`（4 用例，真实 MySQL）：`requestId` 唯一约束、`DECIMAL(20,8)` 精度不丢等。
