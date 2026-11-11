# 测试报告（V2.0 成本控制与治理平台）

> ⚠️ **口径说明**：本报告是 **V2.0 验收时点（2026-11）的快照**，其中的 72 项测试、
> 包路径均为当时状态。2026-10 的工程规范治理（包重组 + 补测试）之后，
> **当前基线为 97 项测试（73 单元 + 24 集成）**，包路径也已按业务域重组。
> 现状请以 `./scripts/verify.sh` 的实际输出与
> [`docs/engineering/QUALITY_GATE.md`](../engineering/QUALITY_GATE.md) §9 为准。

> 本文件对应代码路径（**快照时点**，包路径见上方说明）：
> - 单测：`src/test/java/com/example/ai/**`（9 个类）、`src/test/java/com/example/milvusdemo/embed/LocalHashingEmbeddingModelTest.java`
> - 集成测试：`src/test/java/com/example/ai/budget/RedisBudgetIT.java`、`src/test/java/com/example/ai/integration/MysqlPersistenceIT.java`、`src/test/java/com/example/ai/integration/GovernancePersistenceIT.java`、`src/test/java/com/example/ai/protection/GovernanceRedisIT.java`
> - 构建配置：`pom.xml`（`integration` profile → maven-failsafe，包含 `**/*IT.java`）
> - 迁移脚本：`V1__init_ai_cost.sql`、`V2__seed_demo_data.sql`、`V3__add_provider_alias_demo_pricing.sql`、`V4__governance_schema.sql`、`V5__governance_seed.sql`
> - 前端：`frontend/package.json`、`frontend/src/`（10 个视图）
> - 实测脚本：`samples/verify-alert-levels.sh`、`samples/verify-alert-recovery.sh`
>
> 本报告只记录**本次已实测**的结果，未做的验证统一写入第 10 节，不补写推测数据。
>
> **V2.0 相对 V1.0 的变化**：测试规模从 11 单测 + 10 集成测试扩到 **48 单测 + 24 集成测试（合计 72 项）**；新增五档预算阈值预警、告警自动恢复、紧急开关全链路三组端到端实测（第 6、7 节）；前端从 6 页扩到 10 页并**补做了浏览器端回归**（第 9 节，V1.0 时「未做」）。

## 1. 验收范围与前提

| 项 | 本次取值 |
|---|---|
| RAG 开关 | `rag.enabled=false`（默认），**RAG 检索未做端到端验证**；RAG 相关代码未删 |
| 模型 | 默认 `ai.provider=mock`，`MockChatModel` + `MockUsageAdapter`（强制 `usageStatus=MOCK`）；另以 `AI_PROVIDER=openai` + DeepSeek 兼容接口在 8092 端口实例完成真实调用验收（见第 8 节） |
| 价格 | `DEMO-v1`，全部 `demo_flag=1`，**不是供应商真实报价** |
| 身份 | `DemoUserIdentityProvider` 固定 `demo-user`，未接入真实认证 |
| 持久化 | 真实 MySQL 8.0（宿主 13307）+ 真实 Redis（本机 6379，**集成测试用 DB 14 / DB 15 隔离，应用用 DB 0**） |
| 数据库版本 | `flyway_schema_history` 5 条 success（V1~V5），共 **13 张业务表** + 1 张历史表 |
| 前端 | 静态校验 + `scripts` 内 Chrome headless 浏览器回归（第 9 节） |
| JDK | **必须 Java 17**（`JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home`），见 1.1 |

### 1.1 环境陷阱：JDK 版本导致 Lombok 静默失效

本机 `mvn -v` 默认解析到 **Java 26.0.1**（Homebrew 升级）。**JDK 23 起 javac 默认关闭隐式注解处理**，Lombok 注解处理器不再自动运行，编译时大量「找不到符号 `getXxx()`」（`ModelPricingService`、`AiChatService` 等数百处），但报错信息完全不提 Lombok，极易误判为代码缺陷。

项目基线为 **Java 17**，所有 Maven 命令必须显式设 `JAVA_HOME`：

```bash
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
mvn -o -q clean compile   # 修复后无输出即成功
```

## 2. 后端构建与单元测试

命令：

```bash
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
mvn -B -Pintegration verify
```

结果：**BUILD SUCCESS，72 项测试全部通过 = 48 单元测试 + 24 集成测试**（13 个测试类）。默认构建跑纯单元测试，`integration` profile 由 failsafe 追加执行 `**/*IT.java`（真实 Redis / MySQL）。

> **一处小整理**：`src/test/java/com/example/ai/optimize/OptimizationStrategyTest.java` 里声明的类名其实是 `PromptOptimizerTest`（因非 `public` 才得以编译），文件名与类名不一致容易误读。已把文件重命名为 `PromptOptimizerTest.java`，内容零改动，重跑全量测试仍 72 项全绿。

单元测试 **48 个全绿**：

| 测试类 | 用例数 | 覆盖点 |
|---|---|---|
| `CostCalculatorTest` | 4 | 缓存 Token 是输入子集且不重复计费、缓存越界收敛、缺价格标 `UNPRICED` 零成本、`MoneyScale` 8 位精度往返 |
| `OpenAiUsageAdapterTest` | 4 | 从 metadata 提取 `REAL`、缓存 Token 从 `nativeUsage` 读取、无 Usage 标 `UNAVAILABLE` 不估算、Mock 强制 `MOCK` 不标 `REAL` |
| `LocalHashingEmbeddingModelTest` | 3 | 纯中文文本不产生零向量、字面相关文档优先、确定性与空文本处理（原 RAG 回归） |
| `SpendForecastServiceTest` | 3 | 按已过时间线性外推月成本、预测超月预算时才置 `willExceed`、日耗尽 ETA 按小时燃烧率推算 |
| `ModelRouterTest` | 6 | 简单问题走标准档、复杂问题走高能力档、保护等级强制降档到低价模型、缺价格回退默认模型、路由关闭保持默认、Agent 场景偏好标准档 |
| `PromptOptimizerTest` | 9 | Prompt 优化 5 例（重复指令行去重、连续空行折叠、语义内容不被破坏、空 Prompt 安全、稳定前缀对供应商缓存确定可复现）+ 估算器口径声明 + 上下文压缩 2 例（保留最近轮次丢弃过早、重复消息去重）+ RAG 优化 1 例（去重且尊重 Token 预算） |
| `BudgetThresholdAlertServiceTest` | 6 | 只报真正越过的档位、`used+reserved` 合计判定、已报档位不重复报、回落自动 `resolve`、零额度除零保护、非使用率规则过滤 |
| `CostProtectionEngineTest` | 4 | 自锁回归（`PROTECTION_ACTION` 不计入风险）、其它 CRITICAL 仍可升 SEVERE、预算阈值规则驱动档位并在 SEVERE 停机、紧急开关阻断与恢复 |
| `AnomalyDetectionServiceTest` | 9 | 6 条异常规则命中 / 静默两端 + Agent 死循环判 CRITICAL + findings 落告警中心 |

### 2.1 本批新增的单元测试要点

- **`BudgetThresholdAlertServiceTest.raisesOnlyThresholdsThatWereActuallyCrossed`**：使用率 72% 时只应产生 50% 与 70% 两条，不能把 85/95/100 一起报出。这条用例守住「多档位逐级上报」的核心语义。
- **`BudgetThresholdAlertServiceTest.includesReservedAmountWhenJudgingThreshold`**：`used=40%` + `reserved=30%` 合计 70% 必须报 70% 档。若只看 `used`，「钱已花完但请求还没结算」的窗口期会漏报。
- **`CostProtectionEngineTest.openProtectionActionAlertDoesNotKeepEngineAtSevere`**：保护引擎自己写入的 `PROTECTION_ACTION`(CRITICAL) 告警，不能在 `currentLevel()` 里被当作风险输入，否则 `emergency-stop` → `resume` 后等级永久卡在 SEVERE。这是真实缺陷的回归用例，详见 `docs/architecture/ADR/ADR-002-protection-self-lock.md`。
- **`CostProtectionEngineTest.otherOpenCriticalAlertStillEscalatesToSevere`**：验证上一条修复**没有过度放宽**——其它类型的未恢复 CRITICAL 仍必须升到 SEVERE。
- **`AnomalyDetectionServiceTest`** 的 6 条规则均按「命中 / 静默」两端各写用例，特别覆盖绝对额下限（小额不被误报为大额突增）与样本量门槛（错误率、缓存命中率在样本不足时不判定）。

### 2.2 Redis 状态化测试替身

`emergencyStopBlocksThenResumeRestoresNormal` 用 `StringRedisTemplate` 的 Mockito mock 时，`set()` 没有副作用，`currentLevel()` 读不到刚写入的 `"1"`，紧急开关的「开 → 关」语义根本测不出来。改为 `HashMap` + `doAnswer` 的有状态替身：

```java
private final Map<String, String> redisStore = new HashMap<>();
when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0, String.class)));
doAnswer(inv -> { redisStore.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class)); return null; })
    .when(valueOps).set(anyString(), anyString());
```

另一处坑：`when(budgetService.status(anyString()))` 在 `identityProvider.currentUserId()` 返回 `null` 的路径上桩不命中，必须用 `any()`。

## 3. Redis 集成测试（真实 Redis，13 个）

两个测试类都连**本机真实 Redis**，不使用 Mockito 模拟：`RedisBudgetIT` 用 **DB 15**、`GovernanceRedisIT` 用 **DB 14**（应用本身用 DB 0，三者隔离），每个用例前 `flushDb`。

### 3.1 `RedisBudgetIT`（7 个）

| 用例 | 断言要点 |
|---|---|
| `normalReserveAndSettle` | 预扣 1.00 USD 后 `held=100000000`（1e-8 单位）；结算 0.30 USD 后 `used=30000000`、`held=0` |
| `sceneBudgetIsCheckedAtomicallyWithGlobal` | `SCENE` 作用域与 `GLOBAL` 在同一 Lua 脚本内一次性检查，不会出现「场景额度够但全局不够」却已占用场景额度的中间态 |
| `insufficientBudgetRejectsRequest` | 2.00 USD 额度下第二个 1.50 USD 请求被拒，`held` 保持 1.50 USD |
| `hundredConcurrentRequestsNeverExceedBudget` | 100 并发（32 线程池 + `CountDownLatch` 同时放行，30s 超时），5.00 USD 额度、每请求 1.00 USD，**恰好 5 个通过**，`held=500000000`、`used=0`、并发过程无异常 |
| `duplicateRequestIdAndDuplicateSettleAreIdempotent` | 重复 `requestId` 预扣不重复扣额度；重复结算 `changed=false` 且不重复扣费 |
| `releaseRestoresBudgetOnFailure` | 失败释放后 `held=0`、`used=0` |
| `expiredReservationIsCleanedUp` | TTL 设为 1s，等待 1.6s 后清理，`held` 被同步扣回 0（不能只靠 key TTL） |

对应实现：`TokenBudgetService` + `src/main/resources/lua/budget_{reserve,settle,release,cleanup}.lua`。

### 3.2 `GovernanceRedisIT`（6 个）

覆盖治理侧的 Redis 状态语义：

| 用例 | 断言要点 |
|---|---|
| `userMinuteWindowRejectsAfterLimit` | 用户维度分钟窗口超过限额后拒绝，窗口计数按分钟滚动 |
| `concurrencyIsReleasedAndNeverLeaks` | 并发名额在调用结束 / 异常路径都能释放，不泄漏 |
| `concurrentAcquiresNeverExceedModelConcurrency` | 多线程并发抢占时，模型维度并发上限不被突破 |
| `responseCacheIsolatesByUserModelAndPromptVersion` | 响应缓存键必须含「用户 + 模型 + Prompt 版本」，换任一维度都不复用 |
| `embeddingDedupSkipsSameContentAndModel` | Embedding 去重：同内容同模型直接跳过，不重复付费 |
| `rateLimiterFailsOpenButKeepsCountersSane` | 限流器 Redis 异常时**放行**（fail-open），且计数器不出现负数 / 溢出 |

## 4. MySQL 集成测试（真实 MySQL 8.0，11 个）

两者均连接 `127.0.0.1:13307/ai_cost`。

### 4.1 `MysqlPersistenceIT`（4 个）

5 张表与 `flyway_schema_history` 成功记录、`DEMO-v1` 价格全部 `demo_flag=1`、同一 `requestId` 重复写入被唯一约束拒绝且 `DECIMAL(20,8)` 的 `0.00345678` 精度不丢失、统计 SQL 带时间范围返回聚合值。

> **一次失败与修复过程（如实记录）**：首次执行时该测试出现失败，根因是**测试用例间数据残留** —— 4 个用例都向 `ai_token_usage` 插入同一个固定 `request_id`（`it-mysql-persistence-001`），`@AfterAll` 只在全部用例结束后清理一次。修复方式是在 `MysqlPersistenceIT` 增加 `@BeforeEach cleanTestRow()`，每个用例执行前先 `DELETE FROM ai_token_usage WHERE request_id = ?`。修复后重跑全绿。

### 4.2 `GovernancePersistenceIT`（7 个）

V4/V5 新增表的读写与约束验证：

| 用例 | 断言要点 |
|---|---|
| `flywayAppliedGovernanceMigrations` | V4 / V5 在 `flyway_schema_history` 中均为 success |
| `governanceTablesExistWithComments` | 8 张新表存在，且表 / 列均有 COMMENT（字典可自动生成的前提） |
| `ledgerIsAppendOnlyAndIdempotentByEntryNo` | `cost_ledger` 追加式账务：同 `entry_no` 重复写入被拒，已成条目不被改写 |
| `protectionRulesAreSeededWithFiveLevels` | V5 种子的 5 条保护规则齐备，档位 1~5 无缺 |
| `budgetPolicySupportsSceneAndAgentScopes` | 预算策略可承载 `SCENE` 与 `AGENT` 两种新作用域 |
| `dailySummaryNeverExceedsDetailBilling` | 日汇总金额不超前于明细账单（汇总只能是明细的收敛，不能凭空多出） |
| `agentTraceIsUniquePerTask` | `agent_cost_trace` 按任务唯一，重复上报不产生第二行 |

## 5. 应用启动与接口联调（Mock）

| 验证项 | 实测结果 |
|---|---|
| 应用启动 | `health=UP`（`GET /actuator/health`），`rag.enabled=false` 下无需 Milvus |
| `POST /api/ai/chat`（Mock） | 返回完整 Usage / 成本 / 预算数据，账单落 MySQL |
| 管理接口 | **34 个管理接口全部 `code=0`**（31 个只读端点 + `POST /cost-alert/evaluate`、`POST /cost-alert/anomaly-scan`、`POST /cost/daily-summary/rebuild`），由 `samples/verify-admin-endpoints.sh` 一次跑完并断言，契约见 `docs/api/AI_COST_API.md` |
| 预算拦截 | 把日预算临时调小后，请求返回 **`code=40301 预算不足（DAILY:demo-user）`**；**被拒账单也落库** |
| 保护阻断 | 使用率 >100% 或紧急开关停用时返回 **`code=40302`** |
| 优化试算 | `POST /cost-optimization/execute` 为 **dry-run 纯函数试算**，实测：`beforeTokens=36` → `afterTokens=18`、`savedTokens=18`、`estimatedSavedCost=0.00000486`、`measureKind=ESTIMATED`，返回自带 `qualityNote`「dry-run 试算，仅用于评估优化空间，不代表已节省真实费用」 |

> 上述联调使用 Mock 模型与 DEMO 价格，MySQL / Redis 为真实实例。

## 6. 五档预算阈值预警实测（本批新增）

脚本：`samples/verify-alert-levels.sh`。做法是**真实占用 Redis 预算 + 真实修改 MySQL 的 `daily_limit`**，让同一笔已发生消耗在不同额度下呈现不同使用率，逐档验证 50/70/85/95/100% 的命中、动作等级与去重。

| 场景 | 额度（USD） | 实际使用率 | 实测产生的告警 |
|---|---|---|---|
| A | `0.00000800` | 80.00% | `50.00%` WARNING / `NOTIFY` + `70.00%` WARNING / `NOTIFY` |
| B | `0.00000660` | 96.97% | `85.00%` HIGH / `DOWNGRADE_MODEL` + `95.00%` CRITICAL / `DISABLE_AGENT` |
| C | `0.00000600` | 106.67% | `100.00%` BLOCKED / `BLOCK_REQUEST` |

补充断言：

| 验证点 | 实测结果 |
|---|---|
| 多档位逐级上报 | 80% 只报 50/70，**不**把 85/95/100 一起报出 |
| 去重 | 连续 3 次 `POST /api/admin/cost-alert/evaluate` 后仍是 **5 行告警 / `occurrence_count` 合计 5**，不新增行 |
| 保护等级联动 | 使用率 >100% 时 `/cost-protection/status` 返回 `level=BLOCKED`、`maxBudgetUsageRate=1.0` |
| 真实请求被拦 | 超限后 `POST /api/ai/chat` 返回 `code=40302`「保护等级 5：预算阻断或紧急开关生效，拒绝新的付费调用」 |

> **判别位机制**：告警指纹为 `type|targetType|targetId|level[|discriminator]`。50% 与 70% 两条保护规则的动作等级都是 `WARNING`，若指纹只到 `level` 就会被去重合并成一条，因此必须带 `"50.00%"` / `"70.00%"` 形式的判别位。

## 7. 告警自动恢复与紧急开关实测（本批新增）

脚本：`samples/verify-alert-recovery.sh`。

### 7.1 阈值告警自动恢复

| 验证点 | 实测结果 |
|---|---|
| 回落恢复 | 把额度调回正常值后，5 档告警全部变为 `RESOLVED`，`resolved_at IS NOT NULL` |
| 恢复后放行 | 恢复后 `POST /api/ai/chat` 返回 `code=0` |
| 恢复来源 | 恢复由 `BudgetThresholdAlertService` 的 `@Scheduled` 扫描 + 事件触发两条路径均可产生，不是手工改库 |

### 7.2 紧急开关全链路

| 步骤 | 实测结果 |
|---|---|
| 缺原因调用 | `POST /api/admin/cost-protection/emergency-stop` 不带 `reason` → `code=40001`「必须填写停用原因」 |
| 带原因停用 | 返回成功，`/cost-protection/status` 的 `level=BLOCKED` |
| 停用期请求 | `POST /api/ai/chat` → `code=40302`，被真实阻断 |
| 恢复 | `POST /api/admin/cost-protection/resume` → `level=NORMAL`（**修复前会永久卡在 SEVERE**） |
| 恢复后请求 | `POST /api/ai/chat` → `code=0`，放行 |
| 审计日志 | `EMERGENCY_STOP 1→5`、`RESUME 5→1` 双语记录正确落库 |

> **保护自锁缺陷（真实功能缺陷，非测试问题）**：`resume` 后 `level` 停留在 `SEVERE`。根因是保护引擎为记录审计而写入的 `PROTECTION_ACTION`(CRITICAL) 告警，被 `currentLevel()` 的 `countOpenByLevel(CRITICAL)` 当成外部风险输入，自己把自己锁住。修复两处：① `currentLevel()` 改用 `countOpenByLevelExcluding(CRITICAL, "PROTECTION_ACTION")`；② `resume()` 末尾调用 `resolveOpenOf(PROTECTION_ACTION, "GLOBAL", "__ALL__")` 清理自身遗留告警。完整分析（含 4 个放弃的备选方案）见 `docs/architecture/ADR/ADR-002-protection-self-lock.md`。

## 8. 真实供应商调用验收（已实测）

以独立实例（端口 **8092**）启动，环境变量：

```text
AI_PROVIDER=openai
AI_MODEL=deepseek-chat
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=<取自本机环境变量 DEEPSEEK_API_KEY>
```

调用 `POST /api/ai/chat` 返回 `code=0`，实测结果：

| 项 | 实测值 |
|---|---|
| 供应商实际返回模型名 | `deepseek-flash`（与请求的 `deepseek-chat` 不同，账单按实际模型名记） |
| `usageStatus` | `REAL` |
| Token | `inputTokens=35` / `outputTokens=25` / `totalTokens=60` |
| 答案 | 供应商真实生成内容 |
| 成本 | `inputCost=0.00000945`、`outputCost=0.00002750`、`totalCost=0.00003695`，货币 USD，`pricingVersion=DEMO-v1` |
| 预算 | `reserved=0.00872000` / `settled=0.00003695` |
| 账单 | 已落 MySQL，`usageStatus=REAL` |

**首次调用与 V3 迁移**：供应商返回的别名模型 `deepseek-flash` 在价格表中无对应记录，首次真实调用被 `CostCalculator` 正确标为 `UNPRICED` 并写告警（0 成本，绝不套用相近模型的价格）——这是**防止用错价格的安全行为，不是故障**。随后新增 `V3__add_provider_alias_demo_pricing.sql` 为 `openai/deepseek-flash/CHAT` 补 `DEMO-v1` 价格（input 0.27 / output 1.10 / cached 0.027），第二次调用即按三段价格正常计费。

> **口径边界**：这是「真实供应商调用 + 真实 Usage 采集」验收；所用价格为 `DEMO-v1` **示例价，不是供应商真实报价**，因此**不能宣称真实财务结算 / 计费对账验收通过**。见 `docs/architecture/TOKEN_USAGE_DESIGN.md`、`docs/database/DATABASE_DESIGN.md`。

## 9. 前端验证（10 页）

### 9.1 静态校验

| 命令 | 结果 |
|---|---|
| `npm install` | 通过 |
| `npm run type-check`（`vue-tsc --noEmit`） | 通过（无输出） |
| `npm run lint` | 通过（exit 0） |
| `npm run build` | 通过，10 个视图 chunk 齐全 |

构建产物关键 chunk：`CostBreakdown 7.97kB`、`CostOptimization 9.72kB`、`CostAlerts 11.03kB`、`CostProtection 12.07kB`、`AiRequests 11.59kB`；仅有 ECharts 大 chunk 体积警告，非错误。

### 9.2 浏览器回归（本批补做）

用 Chrome headless `--dump-dom` 抓取渲染后 DOM 断言（Playwright 未安装），对 6 个页面回归：

| 断言 | 结果 |
|---|---|
| 页面 `h1` 标题正确 | 6/6 通过 |
| 加载态残留（`loading` 未清） | 0 |
| 加载失败提示（`phaseErr`） | 0 |
| 真实数据渲染行数 | `rows=8~18` |
| 渲染徽标数 | `badges=9~31` |
| 保护页关键状态 | `NORMAL` / `L5` / `EMERGENCY_STOP` / `budget_usage` 5 条规则全部呈现 |

### 9.3 页面清单与导航

`frontend/src/router/index.ts` 定义 **10 个页面 / 3 个导航组**，全部通过 `src/api/*.ts` 走真实接口，`constants.ts` 只放契约枚举与展示映射，**无写死统计值**。

| 分组 | 页面 | 文件 |
|---|---|---|
| 分析 | 成本总览（首页 `/dashboard/cost`） | `views/AiCostOverview.vue` |
| 分析 | 成本构成 | `views/CostBreakdown.vue` |
| 分析 | Token 分析 | `views/TokenUsage.vue` |
| 分析 | 模型成本 | `views/ModelCost.vue` |
| 治理 | 预算管理 | `views/Budget.vue` |
| 治理 | 优化中心 | `views/CostOptimization.vue` |
| 治理 | 预警中心 | `views/CostAlerts.vue` |
| 治理 | 系统保护策略 | `views/CostProtection.vue` |
| 治理 | 价格管理 | `views/ModelPricing.vue` |
| 运维 | 请求追踪 | `views/AiRequests.vue` |

旧路径 `/dashboard/ai-cost`、`/dashboard/ai-requests` 保留 redirect 兼容。

## 10. 未完成 / 未覆盖的验证

- 真实报价口径的财务结算 / 计费对账：未完成。真实供应商调用与真实 Usage 采集已通过，但成本按 `DEMO-v1` 示例价计算。
- RAG 检索端到端：本次在 `rag.enabled=false` 下验收，**未验证**；本机 Milvus 的 MinIO 9000 端口被其它项目占用。
- Windows `start.bat` / `stop.bat` / `restart.bat`：未在 macOS 实测。
- Vue Vitest 用例：未提供前端单元测试；第 9.2 节为 DOM 断言，不是组件级测试。
- 性能压测：未做，无 QPS / 延迟数据，见 `docs/acceptance/PERFORMANCE_REPORT.md`。
- 多用户真实认证、按月分表、真实价格均未接入 / 未实现。
- 告警「确认（ACKNOWLEDGED）」状态有接口与页面操作，但**未做「确认后是否仍重复告警」的实测**（去重只验证了 OPEN 状态）。
- 保护规则的 `DOWNGRADE_MODEL` 只验证了路由档位下发（`/cost-protection/status` 的 `modelRoutes`），**未接真实模型验证降档后实际调用的是低价模型**。

## 11. 命令汇总

```bash
# 后端（含单测 + Redis/MySQL 集成测试）
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
mvn -B -Pintegration verify              # 72 项：48 单测 + 24 集成
mvn -o -q -DskipITs test                # 仅单测

# 前端（在 frontend/ 目录）
npm install
npm run type-check
npm run lint
npm run build

# 五档阈值预警 / 恢复与紧急开关实测（在项目根目录）
bash samples/verify-admin-endpoints.sh   # 34 个管理接口全部 code=0（只读，建议先跑这个）
bash samples/verify-alert-levels.sh      # 会临时收紧额度
bash samples/verify-alert-recovery.sh    # 会临时收紧额度 + 开关紧急开关

# 前置基础设施
docker compose -f ../../infra/docker/ai-cost-compose.yml up -d   # MySQL 13307
redis-cli -p 6379 ping                                          # 本机 Redis

# 接口调用注意：本机有 HTTP_PROXY，curl 必须显式绕开
curl --noproxy '*' http://localhost:8091/actuator/health
```
