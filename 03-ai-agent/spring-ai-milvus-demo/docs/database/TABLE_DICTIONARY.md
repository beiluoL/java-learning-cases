# 表字段字典

> 本文件对应代码路径：`src/main/resources/db/migration/V1__init_ai_cost.sql`（**逐字段以该 DDL 为准**）。
> 数据迁移见 `V2__seed_demo_data.sql`、`V3__add_provider_alias_demo_pricing.sql`（均只插入数据，**不改表结构**，故本字典仍以 V1 DDL 为准）。设计说明见 `docs/database/DATABASE_DESIGN.md`。
>
> 说明：`可空` 列取自 DDL 的 `NOT NULL` / `NULL`；`默认值` 为 DDL 中显式声明的 `DEFAULT`，未声明记为「—」。
> 全部表 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`；金额 `DECIMAL(20,8)`、Token `BIGINT`、时间 `DATETIME(3)` 且为 UTC。

---

## 1. ai_model_pricing — AI 模型价格及历史版本

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 否 | — | 主键 |
| `provider` | VARCHAR(64) | 否 | — | 模型供应商，如 openai / deepseek / mock / local |
| `model_name` | VARCHAR(128) | 否 | — | 模型名称（以供应商返回值为准） |
| `model_type` | VARCHAR(32) | 否 | `'CHAT'` | 模型类型：CHAT / EMBEDDING / RERANK |
| `input_price` | DECIMAL(20,8) | 否 | — | 每百万普通输入 Token 单价 |
| `output_price` | DECIMAL(20,8) | 否 | — | 每百万输出 Token 单价 |
| `cached_input_price` | DECIMAL(20,8) | 否 | `0` | 每百万缓存命中输入 Token 单价 |
| `currency` | VARCHAR(8) | 否 | `'USD'` | 结算货币，初版统一 USD |
| `version` | VARCHAR(64) | 否 | — | 价格版本，账单保存快照 |
| `effective_from` | DATETIME(3) | 否 | — | 生效时间(UTC) |
| `effective_to` | DATETIME(3) | 是 | — | 失效时间(UTC)，NULL 表示长期有效 |
| `enabled` | TINYINT | 否 | `1` | 是否启用：1 启用 0 停用 |
| `demo_flag` | TINYINT | 否 | `1` | 是否示例价格：1 表示 DEMO 报价，非供应商真实价格 |
| `created_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间(UTC) |
| `updated_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间(UTC) |

索引：`PRIMARY KEY (id)`；`UNIQUE KEY uk_pricing_version (provider, model_name, version)`；
`KEY idx_pricing_lookup (provider, model_name, model_type, enabled, effective_from)`；
`KEY idx_pricing_effective (effective_from, effective_to)`。

---

## 2. ai_token_usage — AI 调用明细与成本账单

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 否 | — | 主键 |
| `request_id` | VARCHAR(64) | 否 | — | 请求唯一 ID，幂等键 |
| `trace_id` | VARCHAR(64) | 是 | — | 链路追踪 ID |
| `user_id` | VARCHAR(64) | 否 | — | 用户 ID（服务端身份，不信任前端传值） |
| `conversation_id` | VARCHAR(64) | 是 | — | 会话 ID |
| `business_scene` | VARCHAR(32) | 否 | — | 业务场景：CHAT / RAG / EMBEDDING / RERANK / QUERY_REWRITE / DOCUMENT_SUMMARY / AGENT |
| `provider` | VARCHAR(64) | 否 | — | 模型供应商 |
| `model_name` | VARCHAR(128) | 否 | — | 供应商实际返回的模型名 |
| `model_type` | VARCHAR(32) | 否 | — | 模型类型：CHAT / EMBEDDING / RERANK |
| `input_tokens` | BIGINT | 否 | `0` | 输入 Token（含缓存命中部分） |
| `output_tokens` | BIGINT | 否 | `0` | 输出 Token |
| `total_tokens` | BIGINT | 否 | `0` | 总 Token |
| `cached_input_tokens` | BIGINT | 否 | `0` | 缓存命中的输入 Token（input_tokens 的子集，不重复计费） |
| `reasoning_tokens` | BIGINT | 否 | `0` | 推理 Token（按供应商规则，默认不单独计费） |
| `usage_status` | VARCHAR(16) | 否 | — | Usage 来源：REAL / ESTIMATED / UNAVAILABLE / LOCAL / MOCK |
| `request_status` | VARCHAR(16) | 否 | — | 请求状态：SUCCESS / FAILED / BUDGET_REJECTED / UNKNOWN |
| `latency_ms` | BIGINT | 否 | `0` | 模型调用耗时(毫秒) |
| `input_cost` | DECIMAL(20,8) | 否 | `0` | 普通输入成本 |
| `cached_input_cost` | DECIMAL(20,8) | 否 | `0` | 缓存输入成本 |
| `output_cost` | DECIMAL(20,8) | 否 | `0` | 输出成本 |
| `total_cost` | DECIMAL(20,8) | 否 | `0` | 总成本 |
| `currency` | VARCHAR(8) | 否 | `'USD'` | 结算货币 |
| `pricing_version` | VARCHAR(64) | 是 | — | 计费时命中的价格版本快照 |
| `pricing_id` | BIGINT | 是 | — | 计费时命中的价格记录 ID 快照 |
| `error_code` | VARCHAR(64) | 是 | — | 失败错误码 |
| `created_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3)` | 调用时间(UTC) |

索引：`PRIMARY KEY (id)`；`UNIQUE KEY uk_usage_request (request_id)`；
`KEY idx_usage_created (created_at)`；`KEY idx_usage_user_scene (user_id, business_scene, created_at)`；
`KEY idx_usage_model (provider, model_name, created_at)`；
`KEY idx_usage_status_scene (request_status, usage_status, created_at)`。

---

## 3. ai_budget_policy — 预算策略配置

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 否 | — | 主键 |
| `budget_type` | VARCHAR(24) | 否 | — | 预算类型：GLOBAL / USER / MODEL / DAILY / MONTHLY |
| `target_id` | VARCHAR(128) | 否 | `'__ALL__'` | 预算对象：用户 ID / 模型名 / __ALL__ |
| `daily_limit` | DECIMAL(20,8) | 是 | — | 每日最大费用 |
| `monthly_limit` | DECIMAL(20,8) | 是 | — | 每月最大费用 |
| `per_request_limit` | DECIMAL(20,8) | 是 | — | 单次请求最大费用 |
| `max_input_tokens` | BIGINT | 是 | — | 单次请求最大输入 Token |
| `max_output_tokens` | BIGINT | 是 | — | 单次请求最大输出 Token |
| `allow_overrun` | TINYINT | 否 | `0` | 是否允许超额：0 严格拦截 1 允许并告警 |
| `enabled` | TINYINT | 否 | `1` | 是否启用预算控制 |
| `currency` | VARCHAR(8) | 否 | `'USD'` | 结算货币 |
| `remark` | VARCHAR(255) | 是 | — | 备注 |
| `created_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间(UTC) |
| `updated_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间(UTC) |

索引：`PRIMARY KEY (id)`；`UNIQUE KEY uk_budget_scope (budget_type, target_id)`；
`KEY idx_budget_enabled (enabled, budget_type)`。

---

## 4. ai_budget_reservation — 预算预扣、结算与异常状态流水

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 否 | — | 主键 |
| `request_id` | VARCHAR(64) | 否 | — | 请求唯一 ID |
| `budget_type` | VARCHAR(24) | 否 | — | 预算类型 |
| `target_id` | VARCHAR(128) | 否 | — | 预算对象 |
| `user_id` | VARCHAR(64) | 否 | — | 发起用户 |
| `reserved_amount` | DECIMAL(20,8) | 否 | — | 预扣金额 |
| `settled_amount` | DECIMAL(20,8) | 是 | — | 实际结算金额 |
| `status` | VARCHAR(16) | 否 | — | RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED |
| `expire_at` | DATETIME(3) | 是 | — | 预扣过期时间(UTC) |
| `created_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间(UTC) |
| `updated_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` | 更新时间(UTC) |

索引：`PRIMARY KEY (id)`；`UNIQUE KEY uk_reservation_request (request_id)`；
`KEY idx_reservation_status_expire (status, expire_at)`；`KEY idx_reservation_user (user_id, created_at)`。

---

## 5. ai_cost_alert — 预算预警与成本异常事件

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 否 | — | 主键 |
| `request_id` | VARCHAR(64) | 是 | — | 关联请求 ID |
| `alert_type` | VARCHAR(32) | 否 | — | BUDGET_EXCEEDED / COST_SPIKE / SETTLE_OVERRUN / UNKNOWN_USAGE / REDIS_FAILURE / RECONCILE_DIFF |
| `budget_type` | VARCHAR(24) | 是 | — | 关联预算类型 |
| `target_id` | VARCHAR(128) | 是 | — | 关联预算对象 |
| `message` | VARCHAR(512) | 否 | — | 告警说明（不含敏感 Prompt 与密钥） |
| `amount` | DECIMAL(20,8) | 是 | — | 关联金额 |
| `severity` | VARCHAR(16) | 否 | `'WARN'` | INFO / WARN / ERROR |
| `created_at` | DATETIME(3) | 否 | `CURRENT_TIMESTAMP(3)` | 产生时间(UTC) |

索引：`PRIMARY KEY (id)`；`KEY idx_alert_created (created_at)`；`KEY idx_alert_type (alert_type, created_at)`。

---

## 附：V1~V3 迁移与数据（V2 / V3 均不改表结构）

- `V1__init_ai_cost.sql`：建 5 张表（本字典逐字段以该 DDL 为准）。
- `V2__seed_demo_data.sql`：
  - `ai_model_pricing`：6 条 `DEMO-v1` 价格（deepseek-chat、deepseek-reasoner、gpt-4o-mini、mock-chat-model、local-chat-model、local-hashing-embedding），全部 `demo_flag=1`，**DEMO 示例价，非供应商真实报价**。
  - `ai_budget_policy`：3 条默认策略 —— `GLOBAL/__ALL__`（日 50 / 月 1000 / 单次 1.00）、`DAILY/demo-user`（日 5.00 / 单次 0.10）、`MONTHLY/demo-user`（月 100.00 / 单次 0.10），均 `enabled=1`、`allow_overrun=0`、`currency=USD`。
- `V3__add_provider_alias_demo_pricing.sql`：`ai_model_pricing` 追加 1 条 `DEMO-v1` 价格 —— `openai/deepseek-flash/CHAT`，`input_price=0.27` / `output_price=1.10` / `cached_input_price=0.027`，`demo_flag=1`。用途：供应商实际返回的模型名（`deepseek-flash`）与请求配置（`deepseek-chat`）不同，账单按实际模型名记 `model_name`，价格表须为该别名提供可计费版本；缺该记录时账单会正确标 `UNPRICED` 并告警。仍为示例价。
- 当前 `flyway_schema_history` 有 1 / 2 / 3 三条成功记录。

---

# 附：治理平台 V1.0 新增结构（Flyway V4 / V5）

> 来源：`src/main/resources/db/migration/V4__governance_schema.sql`（结构扩展 + 8 张新表）、`V5__governance_seed.sql`（保护规则 / 场景预算 / 基础设施成本种子）。
> 本附录由 `information_schema` 实际结构导出生成，与线上库一致。V1 那 5 张表的原字段字典见上文第 1~5 节，此处只列**新增字段**。
> 全部新表同样 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`；金额 `DECIMAL(20,8)`、Token `BIGINT`、时间 `DATETIME(3)` 且为 UTC。

## 6. 既有表的结构扩展

**`ai_token_usage` 新增 6 个字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `agent_id` | varchar(64) | Agent 标识（AGENT 场景的任务归属） |
| `knowledge_base_id` | varchar(64) | 知识库标识（RAG 成本归因） |
| `cache_hit` | tinyint | 响应缓存命中：1 命中（未真实调用模型，成本按 0 记账） |
| `cache_saved_cost` | decimal(20,8) | 缓存命中节省成本（基于该请求上次真实账单，**非新增账单**） |
| `retry_count` | int | 模型调用重试次数 |
| `route_level` | varchar(8) | 模型路由等级：L1 低成本 / L2 常规 / L3 高能力 |

**`ai_budget_policy` 新增 6 个字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| `max_requests` | int | 周期内最大请求数（NULL 表示不限） |
| `max_concurrency` | int | 最大并发请求数（NULL 表示不限） |
| `max_agent_steps` | int | Agent 任务最大执行步数 |
| `max_tool_calls` | int | Agent 任务最大工具调用次数 |
| `alert_threshold` | decimal(5,4) | 预算使用率告警阈值（0~1） |
| `overrun_policy` | varchar(16) | 超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行 |

**`ai_cost_alert` 新增 11 个字段（升级为告警中心）**

| 字段 | 类型 | 说明 |
|---|---|---|
| `alert_level` | varchar(16) | 等级：INFO / WARNING / HIGH / CRITICAL / BLOCKED |
| `target_type` | varchar(24) | 对象类型：GLOBAL / USER / MODEL / SCENE / AGENT / KNOWLEDGE_BASE |
| `threshold_value` | decimal(20,8) | 触发阈值（预算金额或比率） |
| `current_value` | decimal(20,8) | 触发时实际值 |
| `status` | varchar(16) | 状态：OPEN / ACKNOWLEDGED / RESOLVED |
| `occurrence_count` | int | 相同指纹累计发生次数（去重合并） |
| `fingerprint` | varchar(160) | 去重指纹（`类型\|对象类型\|对象\|等级[\|判别位]`） |
| `last_seen_at` | datetime(3) | 最近一次发生时间(UTC) |
| `acknowledged_at` | datetime(3) | 确认时间(UTC) |
| `acknowledged_by` | varchar(64) | 确认人 |
| `resolved_at` | datetime(3) | 恢复时间(UTC) |

> `fingerprint` 的判别位是为预算阈值预警新增的：50% 与 70% 两条规则的 `alert_level` 都是 WARNING，仅靠「类型+对象+等级」会被去重成一条，必须带阈值判别位。见 `docs/architecture/ADR/ADR-002-protection-self-lock.md`。

## 7~14. 新增 8 张表

## 7. ai_cost_ledger — 追加式账务流水（ACTUAL / ALLOCATED / SAVED 三口径）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `entry_no` | varchar(80) | 否 | — | 账务流水号，幂等键 |
| `entry_type` | varchar(24) | 否 | — | 类型：MODEL_CALL / EMBEDDING / RERANK / AGENT / INFRA_ALLOCATED / CACHE_SAVING / ADJUST |
| `cost_kind` | varchar(16) | 否 | — | 口径：ACTUAL 真实账单 / ALLOCATED 分摊 / SAVED 节省（不计入成本合计） |
| `request_id` | varchar(64) | 是 | — | 关联请求 ID |
| `trace_id` | varchar(64) | 是 | — | 链路追踪 ID |
| `user_id` | varchar(64) | 是 | — | 用户 ID |
| `agent_id` | varchar(64) | 是 | — | Agent 标识 |
| `knowledge_base_id` | varchar(64) | 是 | — | 知识库标识 |
| `business_scene` | varchar(32) | 是 | — | 业务场景 |
| `provider` | varchar(64) | 是 | — | 供应商 |
| `model_name` | varchar(128) | 是 | — | 模型名 |
| `quantity` | bigint | 是 | — | 计费量（Token 数或调用次数） |
| `amount` | decimal(20,8) | 否 | 0.00000000 | 金额（正数为成本/节省，冲正为负数） |
| `currency` | varchar(8) | 否 | USD | 货币 |
| `pricing_version` | varchar(64) | 是 | — | 价格版本快照 |
| `remark` | varchar(255) | 是 | — | 备注（不含敏感内容） |
| `occurred_at` | datetime(3) | 否 | — | 业务发生时间(UTC) |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 入账时间(UTC) |

索引：`idx_ledger_occurred`(occurred_at)；`idx_ledger_scene`(business_scene,occurred_at)；`idx_ledger_type`(entry_type,occurred_at)；`idx_ledger_user`(user_id,occurred_at)；`PRIMARY`(id) UNIQUE；`uk_ledger_entry`(entry_no) UNIQUE

## 8. ai_budget_transaction — 预算变更流水（RESERVE / SETTLE / RELEASE / EXPIRE，每作用域一条）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `tx_no` | varchar(80) | 否 | — | 流水号，幂等键 |
| `request_id` | varchar(64) | 是 | — | 关联请求 ID |
| `budget_type` | varchar(24) | 否 | — | 预算类型 |
| `target_id` | varchar(128) | 否 | — | 预算对象 |
| `user_id` | varchar(64) | 是 | — | 用户 |
| `tx_type` | varchar(16) | 否 | — | RESERVE / SETTLE / RELEASE / EXPIRE / ADJUST |
| `amount` | decimal(20,8) | 否 | 0.00000000 | 本次变更金额（正=占用，负=归还） |
| `balance_before` | decimal(20,8) | 是 | — | 变更前已用金额（含预扣） |
| `balance_after` | decimal(20,8) | 是 | — | 变更后已用金额（含预扣） |
| `currency` | varchar(8) | 否 | USD | 货币 |
| `remark` | varchar(255) | 是 | — | 备注 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 发生时间(UTC) |

索引：`idx_budget_tx_request`(request_id)；`idx_budget_tx_scope`(budget_type,target_id,created_at)；`PRIMARY`(id) UNIQUE；`uk_budget_tx_no`(tx_no) UNIQUE

## 9. ai_cost_protection_rule — 自动成本保护规则（条件 + 等级 + 动作 + 优先级）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `rule_code` | varchar(64) | 否 | — | 规则编码 |
| `rule_name` | varchar(128) | 否 | — | 规则名称 |
| `protection_level` | tinyint | 否 | — | 生效保护等级 1~5 |
| `condition_type` | varchar(32) | 否 | — | 触发条件：BUDGET_USAGE_RATE / DAILY_COST / ERROR_RATE / CACHE_HIT_RATE / MANUAL |
| `threshold_value` | decimal(20,8) | 是 | — | 条件阈值（比率为 0~1） |
| `action` | varchar(32) | 否 | — | 动作：NOTIFY / DOWNGRADE_MODEL / LIMIT_CONCURRENCY / DISABLE_AGENT / BLOCK_REQUEST / EMERGENCY_STOP |
| `priority` | int | 否 | 100 | 优先级，数字越小越优先 |
| `enabled` | tinyint | 否 | 1 | 是否启用 |
| `remark` | varchar(255) | 是 | — | 说明 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间(UTC) |
| `updated_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 更新时间(UTC) |

索引：`idx_protection_rule_level`(protection_level,enabled)；`PRIMARY`(id) UNIQUE；`uk_protection_rule_code`(rule_code) UNIQUE

## 10. ai_cost_protection_log — 保护动作与紧急开关审计日志

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `action` | varchar(32) | 否 | — | 动作：EMERGENCY_STOP / RESUME / LEVEL_CHANGE / DOWNGRADE / DISABLE_MODEL |
| `from_level` | tinyint | 是 | — | 变更前保护等级 |
| `to_level` | tinyint | 是 | — | 变更后保护等级 |
| `target_type` | varchar(24) | 是 | — | 作用对象类型：GLOBAL / PROVIDER / MODEL / AGENT / USER |
| `target_id` | varchar(128) | 是 | — | 作用对象 ID |
| `operator` | varchar(64) | 否 | — | 操作人（服务端身份或 system） |
| `reason` | varchar(255) | 否 | — | 操作原因（必填，可审计） |
| `detail` | varchar(512) | 是 | — | 补充说明 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 操作时间(UTC) |
| `restored_at` | datetime(3) | 是 | — | 恢复时间(UTC)，NULL 表示仍在生效 |

索引：`idx_protection_log_action`(action,created_at)；`idx_protection_log_created`(created_at)；`PRIMARY`(id) UNIQUE

## 11. ai_cost_optimization_log — 成本优化执行记录（收益标注口径）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `request_id` | varchar(64) | 是 | — | 关联请求 ID |
| `strategy` | varchar(32) | 否 | — | 策略：PROMPT_COMPRESSION / CONTEXT_COMPRESSION / RAG_CONTEXT_LIMIT / MODEL_ROUTING / RESPONSE_CACHE / EMBEDDING_DEDUP / OUTPUT_TOKEN_LIMIT / PROMPT_CACHING |
| `before_tokens` | bigint | 是 | — | 优化前 Token（估算口径见 measure_kind） |
| `after_tokens` | bigint | 是 | — | 优化后 Token |
| `saved_tokens` | bigint | 是 | — | 节省 Token |
| `saved_cost` | decimal(20,8) | 是 | — | 节省金额 |
| `measure_kind` | varchar(16) | 否 | — | 口径：MEASURED 实测 / COUNTERFACTUAL 真实用量反推 / ESTIMATED 启发式估算 |
| `quality_note` | varchar(255) | 是 | — | 质量影响说明（未评测必须写明） |
| `detail` | varchar(1024) | 是 | — | 执行明细（JSON 文本，不含敏感 Prompt 全文） |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 记录时间(UTC) |

索引：`idx_optimization_request`(request_id)；`idx_optimization_strategy`(strategy,created_at)；`PRIMARY`(id) UNIQUE

## 12. ai_cost_daily_summary — 五维度日汇总表（由定时任务重建）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `summary_date` | date | 否 | — | 汇总日期(UTC) |
| `dimension` | varchar(16) | 否 | — | 维度：ALL / MODEL / SCENE / USER / AGENT |
| `dimension_key` | varchar(128) | 否 | __ALL__ | 维度取值 |
| `request_count` | bigint | 否 | 0 | 请求次数 |
| `success_count` | bigint | 否 | 0 | 成功次数 |
| `failed_count` | bigint | 否 | 0 | 失败次数 |
| `input_tokens` | bigint | 否 | 0 | 输入 Token |
| `output_tokens` | bigint | 否 | 0 | 输出 Token |
| `cached_tokens` | bigint | 否 | 0 | 缓存命中输入 Token |
| `total_tokens` | bigint | 否 | 0 | 总 Token |
| `total_cost` | decimal(20,8) | 否 | 0.00000000 | 总成本 |
| `cache_saved_cost` | decimal(20,8) | 否 | 0.00000000 | 缓存节省成本 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间(UTC) |
| `updated_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 更新时间(UTC) |

索引：`idx_daily_summary_date`(summary_date)；`PRIMARY`(id) UNIQUE；`uk_daily_summary`(summary_date,dimension,dimension_key) UNIQUE

## 13. ai_agent_cost_trace — Agent 任务级成本追踪（步数/工具/Token/成本聚合）

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `task_id` | varchar(64) | 否 | — | Agent 任务 ID，幂等键 |
| `agent_id` | varchar(64) | 否 | — | Agent 标识 |
| `request_id` | varchar(64) | 是 | — | 最近一次模型请求 ID |
| `user_id` | varchar(64) | 否 | — | 发起用户 |
| `conversation_id` | varchar(64) | 是 | — | 会话 ID |
| `steps` | int | 否 | 0 | 已执行步数 |
| `tool_calls` | int | 否 | 0 | 工具调用次数 |
| `input_tokens` | bigint | 否 | 0 | 累计输入 Token |
| `output_tokens` | bigint | 否 | 0 | 累计输出 Token |
| `total_tokens` | bigint | 否 | 0 | 累计总 Token |
| `total_cost` | decimal(20,8) | 否 | 0.00000000 | 累计成本 |
| `max_steps` | int | 是 | — | 步数上限 |
| `status` | varchar(16) | 否 | RUNNING | RUNNING / COMPLETED / STOPPED / FAILED |
| `stop_reason` | varchar(128) | 是 | — | 中止原因：STEP_LIMIT / COST_LIMIT / TOKEN_LIMIT / LOOP_DETECTED / MANUAL / ERROR |
| `started_at` | datetime(3) | 否 | — | 开始时间(UTC) |
| `finished_at` | datetime(3) | 是 | — | 结束时间(UTC) |

索引：`idx_agent_trace_status`(status,started_at)；`idx_agent_trace_user`(user_id,started_at)；`PRIMARY`(id) UNIQUE；`uk_agent_task`(task_id) UNIQUE

## 14. ai_infrastructure_cost — 基础设施成本登记与分摊状态

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | bigint | 否 | — | 主键 |
| `resource_type` | varchar(32) | 否 | — | 资源类型：SERVER / GPU / REDIS / MYSQL / MILVUS / OBJECT_STORAGE / NETWORK / MONITORING / LOCAL_MODEL |
| `resource_name` | varchar(128) | 否 | — | 资源名称 |
| `provider` | varchar(64) | 是 | — | 云厂商或 self-hosted |
| `period_start` | date | 否 | — | 计费周期开始 |
| `period_end` | date | 否 | — | 计费周期结束 |
| `amount` | decimal(20,8) | 否 | 0.00000000 | 周期总成本 |
| `currency` | varchar(8) | 否 | USD | 货币 |
| `allocation_method` | varchar(24) | 否 | ALLOCATED | METERED 直接计量 / ALLOCATED 按量分摊 / FIXED 固定成本 |
| `allocated_amount` | decimal(20,8) | 否 | 0.00000000 | 已分摊金额（不得超过 amount） |
| `request_count` | bigint | 是 | — | 分摊依据的请求量（用于计算单位分摊成本） |
| `demo_flag` | tinyint | 否 | 1 | 1 表示 DEMO 数据，非真实云账单 |
| `remark` | varchar(255) | 是 | — | 备注 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间(UTC) |

索引：`idx_infra_period`(period_start,period_end)；`idx_infra_type`(resource_type,period_start)；`PRIMARY`(id) UNIQUE

