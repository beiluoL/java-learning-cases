# Token Usage 采集设计

> 本文件对应代码路径：
> - 采集器：`src/main/java/com/example/ai/token/usage/TokenUsageCollector.java`
> - 适配策略：`src/main/java/com/example/ai/token/usage/adapter/ProviderUsageAdapter.java`、`OpenAiUsageAdapter.java`、`MockUsageAdapter.java`
> - 数据结构：`src/main/java/com/example/ai/token/usage/dto/UsageSnapshot.java`、`UsageRecord.java`、`entity/TokenUsage.java`
> - 枚举口径：`src/main/java/com/example/ai/common/AiEnums.java`
> - 成本拆解：`src/main/java/com/example/ai/token/cost/dto/CostBreakdown.java`、`CostCalculator.java`
> - Mock 模型：`src/main/java/com/example/ai/chat/model/MockChatModel.java`
> - 账单写入与查询：`src/main/resources/mapper/TokenUsageMapper.xml`
> - 表结构：`src/main/resources/db/migration/V1__init_ai_cost.sql`（`ai_token_usage`）
> - 单测：`src/test/java/com/example/ai/token/usage/OpenAiUsageAdapterTest.java`
> - 别名模型价格：`src/main/resources/db/migration/V3__add_provider_alias_demo_pricing.sql`
> - 真实调用实测：`docs/acceptance/TEST_REPORT.md` 第 6 节

## 1. 设计目标

1. **真实值优先**：Token 数只来自供应商响应元数据，绝不按输入/输出字符串长度估算。
2. **口径可区分**：真实、模拟、估算、缺失必须显式标记，避免把 Mock 或估算数据混入真实成本账单。
3. **供应商可扩展**：新增供应商只增加一个 `ProviderUsageAdapter` 实现，不改采集器与业务代码。
4. **账单可追溯**：每次调用落一条带 `request_id` 唯一键的明细，含 Usage 来源、请求状态、价格版本快照与成本拆解。

## 2. 采集架构

```
ChatResponse（供应商返回）
   │
   ├─ TokenUsageCollector.extract(provider, response)
   │     └─ 遍历 List<ProviderUsageAdapter>，取 supports(provider)==true 的实现
   │           ├─ OpenAiUsageAdapter   → provider = openai / deepseek
   │           ├─ MockUsageAdapter     → provider = mock
   │           └─ 无匹配适配器         → 兜底返回 UNAVAILABLE（全 0）
   │     └─ 输出 UsageSnapshot（中立结构，屏蔽供应商字段差异）
   │
   └─ TokenUsageCollector.collect(UsageRecord)
         ├─ CostCalculator.calculate()   → CostBreakdown（BigDecimal 三段计价）
         ├─ TokenUsageMapper.insert()    → ON DUPLICATE KEY UPDATE 幂等落库
         └─ UNPRICED 时写 UNKNOWN_USAGE 告警（0 成本，绝编造单价）
```

`AiChatController` 不统计 Token；`AiChatService` 只负责编排，把 `UsageSnapshot` 组装成 `UsageRecord` 交给采集器。Controller / Service 都不直接写账单表。

## 3. Usage 来源口径（AiEnums.UsageStatus）

| 取值 | 含义 | 当前代码中的产生位置 | 是否计入真实成本口径 |
|---|---|---|---|
| `REAL` | 供应商返回真实 Usage | `OpenAiUsageAdapter.extract()`：metadata 存在且 prompt/completion 非 0/0 | 是 |
| `CACHED` | 响应缓存命中，**没有发生模型调用** | `AiChatService` 缓存命中分支（直接返回上次答案并记 0 成本账单） | 否（成本为 0，但节省额记在 `SAVED` 口径） |
| `UNAVAILABLE` | 供应商未提供 / 元数据为空 / Spring AI 返回全 0 默认对象 / 无匹配适配器 | `OpenAiUsageAdapter`、`TokenUsageCollector.extract()` 兜底、`AiChatService.zeroSnapshot()`（预算拒绝与调用失败） | 否（Token 记 0，账单仍落库） |
| `MOCK` | 模拟调用 | `MockUsageAdapter.extract()` **强制标记** | 否；`TokenUsageMapper.xml` 的 `sumRealCost` 明确排除 `MOCK` |
| `ESTIMATED` | 估算值 | 枚举已声明，**当前代码未产生** | 否；未来若引入估算必须显式标记，禁止混入真实账单 |
| `LOCAL` | 本地模型 | 枚举已声明，**当前代码未产生** | 否 |

配套 `RequestStatus`（6 个取值，其中 5 个当前有生产者）：

| 取值 | 产生位置 |
|---|---|
| `SUCCESS` | 正常调用完成（含缓存命中，二者靠 `usageStatus` 区分） |
| `FAILED` | 调用失败且可确定未计费 → `release()` 归还预扣 |
| `BUDGET_REJECTED` | 预算不足 `40301`，**被拒账单也落库** |
| `PROTECTION_BLOCKED` | 保护等级 5（预算阻断或紧急开关）`40302` |
| `UNKNOWN` | 超时 / 连接中断等**不确定失败**，按预扣金额保守计入，等人工对账 |
| `RATE_LIMITED` | 枚举已声明，**当前未产生**：限流在 `AIRequestProtectionService` 抛出 `42901`，早于账单记录 |

> 因此「账单条数」不等于「成功调用数」，统计时须按 `request_status` 过滤。

> 口径纪律：`AiEnums.UsageStatus` 的注释明确「真实值必须与估算/模拟严格区分」；`sumAllCost`（对账口径，含 MOCK）与 `sumRealCost`（真实账单口径，排除 MOCK）是两条独立 SQL，不能混用。

## 4. 供应商适配

### OpenAiUsageAdapter（openai / deepseek）

- 读取 `ChatResponse.getMetadata().getUsage()` 的 `getPromptTokens()` / `getCompletionTokens()` / `getTotalTokens()`，模型名取 `getMetadata().getModel()`（以供应商返回值为准，不信任配置里的模型名）。
- 缓存命中 Token 从 `getNativeUsage()` 按以下优先级尽力提取：`prompt_tokens_details.cached_tokens` → 顶层 `cached_tokens` → 顶层 `prompt_cache_hit_tokens`（DeepSeek 等兼容接口把缓存命中 Token 放在该字段）；解析失败返回 0。
- 缓存 Token 是输入的子集，做双向收敛 `cached = min(max(cached, 0), input)`，防止越界值造成重复计费。
- **关键防伪造**：Spring AI 在未携带 Usage 时会返回全 0 的默认对象，代码把 `usage == null`、字段为 `null`、或 `prompt==0 && completion==0` 一律判为 `UNAVAILABLE`，绝不把 0/0 包装成 `REAL`。

### MockUsageAdapter（mock）

- 读 `MockChatModel` 的固定 Usage（32 / 16 / 48），但**强制** `usageStatus = MOCK`，使模拟数据在对账与真实成本口径中能被明确排除。

### 扩展方式

新增供应商时实现 `ProviderUsageAdapter`（`supports(String provider)` + `extract(ChatResponse)`）并注册为 Spring Bean 即可；`TokenUsageCollector` 通过构造器注入 `List<ProviderUsageAdapter>` 自动发现，采集链路无需改动。

### 供应商返回别名模型与 UNPRICED 安全行为（实测）

- 账单 `model_name` 取 `ChatResponse.getMetadata().getModel()`（`actualModel`），**不是请求配置的 `AI_MODEL`**。实测请求 `deepseek-chat`、DeepSeek 兼容接口实际返回 `deepseek-flash`。
- 价格表必须为别名模型提供独立的可计费版本，否则 `CostCalculator` 找不到生效价格，账单被标 `UNPRICED`、按 0 成本记录并写 `UNKNOWN_USAGE` 告警。**这是防止用错价格的安全行为，不是故障**：绝不会退化到套用相近模型（如 `deepseek-chat`）的价格，也绝不编造单价。
- 本次真实调用的处置：首次调用因缺 `deepseek-flash` 价格正确标 `UNPRICED` 并告警；新增 `V3__add_provider_alias_demo_pricing.sql` 为该别名补 `DEMO-v1` 价格后，第二次调用按三段价格正常计费（`inputCost=0.00000945` / `outputCost=0.00002750` / `totalCost=0.00003695`）。V3 单价仍是 DEMO 示例价。
- `usageStatus=REAL` 只表示 Token 来自供应商真实返回；**不表示成本金额是供应商真实报价**。

## 5. 流式调用为何不做假统计

- 当前 V1 只使用同步 `chatModel.call(prompt)`，`AiChatService` 没有流式端点；`MockChatModel` 只实现 `call()`，其类注释明确「流式调用不提供 Usage，因此明确拒绝，避免假装已准确统计」。
- 流式响应的 Usage 通常由供应商在流末尾单独返回（且往往依赖显式开启 usage 统计选项）。在拿不到可靠的流末 Usage 汇总之前，任何「按已输出字符数 / 分块数反推 Token」的做法都是估算，会污染成本账单。
- 因此设计约定：**流式若接入，必须在拿到可核实的真实 Usage 后再采集；否则标 `UNAVAILABLE`，绝不按字符串长度伪造**。本次验收未涉及流式调用，也未做相关端到端验证。

## 6. 字段表

### 6.1 UsageSnapshot（适配层输出，`token/usage/dto/UsageSnapshot.java`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `inputTokens` | long | 输入 Token（含缓存命中部分） |
| `outputTokens` | long | 输出 Token |
| `totalTokens` | long | 总 Token（供应商不返回时取 input+output） |
| `cachedInputTokens` | long | 缓存命中输入 Token，输入子集，不重复计费 |
| `reasoningTokens` | long | 推理 Token，默认不单独计费 |
| `usageStatus` | String | 见第 3 节口径表 |
| `actualModel` | String | 供应商实际返回的模型名 |
| `provider` | String | 供应商标识 |

### 6.2 UsageRecord（采集入参，`token/usage/dto/UsageRecord.java`）

在 `UsageSnapshot` 之上补充：`requestId`（幂等键）、`traceId`、`userId`（服务端身份）、`conversationId`、`businessScene`、`modelName`、`modelType`、`requestStatus`、`latencyMs`、`errorCode`。共 17 个字段，全部为采集器构造 `TokenUsage` 实体所用。

### 6.3 CostBreakdown（成本拆解，`token/cost/dto/CostBreakdown.java`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `pricingVersion` | String | 命中价格版本快照；缺价格时为 `UNPRICED` |
| `pricingId` | Long | 命中价格记录 ID 快照 |
| `inputPrice` / `outputPrice` / `cachedInputPrice` | BigDecimal | 每百万 Token 单价 |
| `normalInputTokens` / `cachedInputTokens` / `outputTokens` | long | 三段计价用的 Token 数（`normal = input - cached`） |
| `inputCost` / `cachedCost` / `outputCost` / `totalCost` | BigDecimal | 三段成本与合计，保留 8 位小数 |
| `currency` | String | 结算货币，初版统一 USD |

### 6.4 账单表关键字段（`ai_token_usage`，V1 建表 + V4 扩展）

| 字段 | 类型 | 说明 |
|---|---|---|
| `request_id` | VARCHAR(64) NOT NULL | 唯一约束 `uk_usage_request`，幂等键 |
| `trace_id` | VARCHAR(64) NULL | 链路追踪 ID |
| `user_id` | VARCHAR(64) NOT NULL | 服务端身份，不信任前端传值 |
| `input_tokens` / `output_tokens` / `total_tokens` / `cached_input_tokens` / `reasoning_tokens` | BIGINT | Token 数与子集口径 |
| `usage_status` | VARCHAR(16) | 见第 3 节（6 个取值） |
| `request_status` | VARCHAR(16) | 见第 3 节（6 个取值，5 个有生产者） |
| `input_cost` / `cached_input_cost` / `output_cost` / `total_cost` | DECIMAL(20,8) | 三段成本与合计 |
| `pricing_version` / `pricing_id` | VARCHAR(64) / BIGINT | 历史价格快照，改价不影响历史 |
| `created_at` | DATETIME(3) | 调用时间（UTC） |

**V4 新增的 6 列**（成本归因与治理所需，`ALTER TABLE` 不改原字段语义）：

| 新增字段 | 类型 | 说明 |
|---|---|---|
| `agent_id` | VARCHAR(64) NULL | Agent 标识，AGENT 场景的任务归属，用于 Agent 成本归集 |
| `knowledge_base_id` | VARCHAR(64) NULL | 知识库标识，RAG 成本按知识库归因 |
| `cache_hit` | TINYINT NOT NULL DEFAULT 0 | 响应缓存命中：1 表示**未真实调用模型**，成本按 0 记账 |
| `cache_saved_cost` | DECIMAL(20,8) NOT NULL DEFAULT 0 | 缓存命中的**节省额**（复用该请求上次真实账单），属 `SAVED` 口径，**不是新增账单** |
| `retry_count` | INT NOT NULL DEFAULT 0 | 模型调用重试次数（超时不重试，只重试连接建立失败） |
| `route_level` | VARCHAR(8) NULL | 模型路由等级 `L1`/`L2`/`L3`，用于验证降档是否真的生效 |

> 口径纪律：`cache_saved_cost` 是「本可以花但没花的钱」，**不能加进成本合计**；`total_cost` 才是实际消耗。两者混加会把节省变成支出。

完整字段字典见 `docs/database/TABLE_DICTIONARY.md`。

## 7. 采集 → 账单的落库口径

1. `CostCalculator` 全程 `BigDecimal`（中间保留 24 位精度），三段分别计价再求和；`cached_input_tokens` 从输入中扣除；`reasoning_tokens` 默认不单独计费；找不到生效价格返回 `UNPRICED` 零成本快照并告警。
2. `TokenUsageMapper.insert` 使用 `ON DUPLICATE KEY UPDATE request_id = request_id`：同一 `requestId` 重复写入不新增、也不改写历史账单，实现幂等。
3. 账单保存 `pricingVersion` / `pricingId` 快照，价格表后续改价不影响历史成本；`ModelPricingMapper.xml` 的 `findEffective` 按 `effective_from <= at < effective_to` 取生效版本。
4. 金额对外一律字符串（`Views.money()` / `toPlainString()`），比率一律 0~1 小数，避免 JS 精度丢失与百分比歧义。

## 8. 已知边界

- 真实供应商（DeepSeek 兼容接口）调用与真实 Usage 采集**已完成验收**：8092 端口实例返回 `usageStatus=REAL`、`35/25/60`、答案为真实生成内容、账单落 MySQL（详见 `docs/acceptance/TEST_REPORT.md` 第 6 节）。但所用价格为 `DEMO-v1` **示例价**，**不能据此宣称真实报价口径的财务结算 / 计费对账验收通过**。
- `OpenAiUsageAdapter.cachedTokens()` 已支持 `prompt_cache_hit_tokens`，但其方法注释仍只写 `prompt_tokens_details.cached_tokens`，注释未同步（不影响功能）。
- `ESTIMATED` / `LOCAL` 枚举与字段已预留但当前代码未产生，不要据此认为已有估算能力。
- 不记录完整 Prompt；账单只存结构化字段。
