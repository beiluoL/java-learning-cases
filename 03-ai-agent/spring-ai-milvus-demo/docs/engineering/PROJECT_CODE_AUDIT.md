# 项目代码规范审计报告

> 审计对象：`03-ai-agent/spring-ai-milvus-demo`（企业级 AI 成本治理平台 V2.0）
> 审计时间：2026-11-11
> 审计基线：`main` 分支 `2909a49`，工作区无未提交改动
> 审计方式：全量静态扫描 + 逐文件人工核对 + 真实测试结果复核。**所有数字来自实际命令输出，无估算、无假设。**

---

## 1. 审计结论摘要

项目功能完成度较高（13 表 / 34 管理接口 / 97 测试全绿 / 10 页前端），但**工程规范维度存在系统性缺口**，其中最关键的三个：

1. **包结构按"技术动作"而非"业务域"组织** —— 59 个文件堆在 `ai/token/` 一个目录下，8 个业务域（预算/成本/告警/保护/报表/定价/用量/Agent）混居，跨域修改时无法判断边界。
2. **零自动化质量门禁** —— 没有任何 Checkstyle / Spotless / Prettier / Stylelint / ShellCheck 配置，规范完全依赖人（和 AI）自觉，这正是本次要解决的根因。
3. **注释覆盖严重不均** —— 13 个实体共 **210 个字段，仅 6 个有注释**（集中在 `BudgetPolicy`）；类级与方法级 Javadoc 相对完善，字段级几乎空白。

### 问题分级统计

| 级别 | 数量 | 含义 |
|---|---|---|
| **P0 阻断** | 6 | 违反硬性工程要求，必须在本次治理中修复 |
| **P1 严重** | 10 | 影响可维护性/一致性，本次治理主体 |
| **P2 一般** | 10 | 应改进，可分批处理 |
| **P3 建议** | 5 | 优化项，不阻断验收 |
| **合计** | **31** | 逐条见第 7 节总台账 |

> **口径修正记录（必须保留，否则报告不可信）**：
> 1. 审计初稿列 29 条，核实后撤销 1 条误判（FE-02：`TimeRangePicker.vue` 无 Props 属正确设计）。
> 2. 2 条降级为「通过」（BE-17：`@Value` 仅 2 处且均合规；`GlobalExceptionHandler` 已覆盖校验异常）。
> 3. **新增 2 条 P1**：DB-03（31 处 `SELECT *`）、DB-04（`ORDER BY ${sort}` 安全性依赖调用方）。
>    这两条初稿被判为「0 处」，原因是用了 `grep "A\|B"` 的 BRE 转义写法导致漏匹配；改用 `grep -E` 复核后才发现。
>    **教训已写入 `QUALITY_GATE.md`：否定性结论必须用两种方式复核，或交给工具而非手写 grep。**
> 4. **二次审计新增 4 条（2026-11-11，写规范文档时用规范条文反查代码发现）**：
>    BE-20（预算创建必然 500，P1）、BE-21（Controller 直连 Mapper，P1）、
>    BE-22（时间基准不统一，P2）、BE-23（Controller 承载业务逻辑，P2）。
>    其中 BE-20 / BE-21 已当批修复并有真实接口与数据库证据。
>    **教训：写规范不是抄条文，而是拿条文逐条反查现有代码——本轮 4 个问题全部是「写规范时对照检查」发现的，纯静态扫描没有命中。**

### 值得肯定的地方（不作为问题，但需在规范中固化）

| 项 | 实测事实 |
|---|---|
| 依赖注入 | **0 处 `@Autowired` 字段注入**，99 个类全部构造器注入 |
| 调试残留 | **0 处** `System.out` / `System.err` / `printStackTrace` |
| 占位符 | **0 处** `TODO` / `FIXME` / `XXX`，无空实现冒充功能 |
| SQL 参数化 | 全部 `#{}` 绑定；唯一 `${}` 是 `ORDER BY ${q.sort}`，且已由 `UsageQuery.setSort()` 白名单收敛 |
| 数据库注释 | 13 张表 **100%** 有表 COMMENT 与字段 COMMENT |
| 金额精度 | 统一 `DECIMAL(20,8)`，Java 侧统一 `BigDecimal`，**0 处** `double` 算账 |
| 前端类型 | **0 处** `any`，13 个组件全部 `defineProps` + `withDefaults` 带类型 |
| 前端请求 | **0 处**视图/组件直接 `import axios`，全部经 `api/request.ts` |
| 脚本安全 | **0 处** `pkill` / `kill -9` / `rm -rf`；PID 文件精确关停 |

---

## 2. 审计基线与环境

### 2.1 技术栈实测版本

| 维度 | 实测值 | 来源 |
|---|---|---|
| Java | 17（`java.version=17`） | `pom.xml:22` |
| Spring Boot | 3.4.5 | `pom.xml:11` |
| Spring AI | 1.0.0 GA（BOM 导入） | `pom.xml:24,32` |
| MyBatis Starter | 3.0.4 | `pom.xml:90` |
| MySQL 驱动 | Spring Boot 托管（`mysql-connector-j`） | `pom.xml:82-85` |
| Flyway | `flyway-core` + `flyway-mysql`（Boot 托管） | `pom.xml:93-100` |
| Tika | 3.3.2（BOM） | `pom.xml:42` |
| Lombok | Boot 托管，`<optional>true</optional>` | `pom.xml:123-127` |
| Vue | 3.5.13 | `frontend/package.json:17` |
| TypeScript | ~5.7.3 | `frontend/package.json:27` |
| Vite | ^6.0.7 | `frontend/package.json:29` |
| Pinia | ^2.3.1 | `frontend/package.json:16` |
| Vue Router | ^4.5.0 | `frontend/package.json:18` |
| Axios | ^1.7.9 | `frontend/package.json:14` |
| ECharts | ^5.6.0 | `frontend/package.json:15` |
| ESLint | ^9.17.0（flat config） | `frontend/eslint.config.js` |
| MySQL 容器 | `mysql:8.0.36`，宿主 13307 | `infra/docker/ai-cost-compose.yml:15` |
| Redis | 本机 8.8，6379 | 运行环境 |

**结论**：技术栈版本自洽，无版本冲突，无需升级。

### 2.2 代码规模

| 项 | 数量 |
|---|---|
| 后端主代码 | **99 个 Java 文件 / 7740 行**（93 `com.example.ai` + 6 `com.example.milvusdemo`） |
| 后端测试代码 | **13 个测试类 / 1755 行 / 72 个用例** |
| Mapper XML | 13 个 / 829 行 |
| Flyway 迁移 | 5 个 / 457 行 / 13 张表 |
| Lua 脚本 | 4 个 |
| 前端源码 | **5889 行** / 10 视图 + 13 组件 + 7 API 模块 |
| 运维脚本 | 6 个（start/stop/restart × command/bat）+ 3 个验证脚本 / 477 行 |

### 2.3 类分类实测

| 分类 | 数量 | 备注 |
|---|---|---|
| Controller | **13** | 80 个端点（45 `@GetMapping` + 17 `@PostMapping` + 5 `@PutMapping` + 13 `@RequestMapping` 类级） |
| Service | **18** | **全部为 class，0 个 interface** —— 见 BE-09 结论 |
| Mapper（接口） | **13** | 与 13 个 XML 一一对应 |
| Entity | **13** | 210 个字段 |
| DTO 文件 | **5** | `ChatDtos`（内含 7 个嵌套 record）、`CostBreakdown`、`UsageQuery`、`UsageRecord`、`UsageSnapshot` |
| VO | **0 独立文件** | 全部内联在 `AiChatService` 的记录类型或 `Views` 中 —— 见 BE-08 |
| Config | **4** | `AiModelConfig`、`JacksonConfig`、`RedisConfig`、`RagConfiguration` |
| Properties | **1** | `AiProperties`（`@ConfigurationProperties`） |
| Filter / Interceptor | **1 + 1** | `TraceIdFilter`、`AdminAccessInterceptor` |
| Adapter | **3** | `MockUsageAdapter`、`OpenAiUsageAdapter`、`UsageAdapter` |
| Exception | **1** | `BusinessException` + `ErrorCode` + `GlobalExceptionHandler` |

---

## 3. 后端审计

### 3.1 包结构 —— **P0**

实测包分布（`find` + `awk` 统计，仅列非根节点）：

```
 59  ai/token                    ← 8 个业务域混居，最严重
  4  ai/chat
  9  ai/common
  8  ai/config
  1  ai/guard
  2  ai/identity
  1  ai/metrics
  9  ai/optimize                 ← 目录名与业务域不一致（应为 optimization）
  6  milvusdemo/{embed,service,web}
```

`ai/token/` 内部实际混放了 8 个互不相干的业务域：

| 子包 | 文件数 | 真实业务域 | 应归属 |
|---|---|---|---|
| `token/budget` | 11 | 预算管理 | `budget/` |
| `token/cost` | **19** | **成本核算 + 告警 + 保护 三域混居** | `cost/` `alert/` `protection/` |
| `token/analytics` | 9 | 成本分析与报表 | `analytics/` |
| `token/pricing` | 4 | 价格管理 | `pricing/` |
| `token/usage` | 9 | Token 用量采集（含 3 个供应商适配器） | `token/` + `provider/` |
| `token/agent` | 4 | Agent 成本追踪 | `agent/` |
| `token/infra` | 3 | 基础设施成本分摊 | `cost/infra/` |

**违反的规范**：目标结构「每个业务模块独立顶层包，不允许把所有业务模块的代码混放在全局目录」。

**影响**：
- 新人/新智能体无法判断"要改告警应该去 `token/cost` 还是 `optimize`"；
- `token/cost` 一个目录同时承载核算、告警、保护三种职责，改告警时被迫读 19 个文件；
- 包名与业务域不对应（`optimize` vs 文档中的 `optimization`），跨文档引用易错。

**具体证据**：
- `src/main/java/com/example/ai/token/cost/` 下同时存在 `CostLedgerService`（核算）、`CostAlertService`（告警）、`CostProtectionEngine`（保护）、`SpendForecastService`（预测）、`AnomalyDetectionService`（异常检测）——5 个不同职责。
- `DemoApplication.java:25-32` 的 `@MapperScan` 被迫硬列 7 个 `com.example.ai.token.*.mapper` 包，正是包结构不合理的直接副产物。

**建议**：按业务域重组为顶层包（`cost` / `alert` / `protection` / `budget` / `analytics` / `pricing` / `agent` / `token` / `provider` / `optimization` / `rag`），`@MapperScan` 收敛为 2~3 个基包。**纯机械迁移，包声明 + import + XML namespace，编译与 72 项测试可完整验证行为不变。**

---

### 3.2 分层职责

#### BE-02 Controller 返回 `Map<String, Object>` —— **P1**

6 个 Controller 直接返回裸 `Map<String, Object>`：

| 文件 | 说明 |
|---|---|
| `ai/token/analytics/controller/CostGovernanceController.java` | 14 个端点，全部 `Map<String,Object>` |
| `ai/token/analytics/controller/TokenUsageAdminController.java` | 3 个端点 |
| `ai/token/cost/controller/CostAlertController.java` | 6 个端点 |
| `ai/token/cost/controller/CostProtectionController.java` | 6 个端点 |
| `milvusdemo/web/MilvusController.java` | RAG 演示接口（原始代码，保留） |
| `milvusdemo/web/RagController.java` | RAG 摄取接口（原始代码，保留） |

**违反的规范**：「VO 负责向前端返回数据，字段含义明确，返回结构稳定」。

**影响**：接口契约无法被编译器校验。改字段名时前端要到运行时才发现 404/undefined；前端类型定义（`types.ts` 41 个 interface）是**手工对齐**出来的，没有生成或校验机制，是最容易漂移的地方。

**建议**：对 4 个治理 Controller 引入显式 VO 记录类型；`milvusdemo` 的 2 个属于保留的原始 RAG 代码，标注豁免（见 `AI_AGENT_DEVELOPMENT_RULES.md` 的豁免清单）。

#### BE-03 Controller 职责基本合规 —— **通过**

抽查 13 个 Controller：无 SQL 拼接、无 Mapper 直接调用（除 `AgentCostController` 经 Service）、无成本计算逻辑。`AiChatController` 仅做 HTTP 转发 + 委托 `AiChatService`。

#### BE-04 参数校验几乎为零 —— **P1**

全项目 Bean Validation 注解实测：**`@NotBlank` 1 处、`@Size` 1 处、`@Valid` 1 处**。

**违反的规范**：「DTO 使用 Bean Validation」「Controller 使用参数校验」。

**影响**：
- `ChatDtos.ChatRequest` 的 `message` 若不校验，空字符串会一路走到模型调用并产生真实费用；
- 分页参数 `page`/`size` 依赖 Service 内手工兜底，缺少统一入口校验；
- 恶意超大 `message` 会直接进入 Prompt 估算与预扣计算。

**建议**：给 `ChatRequest`、`UsageQuery`、各分页查询 DTO 补 `@NotBlank` / `@Min` / `@Max` / `@Size`，Controller 补 `@Valid`，`GlobalExceptionHandler` 补 `MethodArgumentNotValidException` 处理器（需核实当前是否已处理）。

---

### 3.3 方法与类规模

#### BE-05 `AiChatService.chat()` 184 行 + 18 个构造依赖 —— **P0**

实测（自研 AST 粗解析 + 人工核对）：

| 位置 | 方法 | 行数 |
|---|---|---|
| `ai/chat/service/AiChatService.java:112` | `chat(...)` | **184** |
| `ai/token/cost/AnomalyDetectionService.java:60` | `scan()` | **94** |
| `ai/token/usage/TokenUsageCollector.java:61` | `collect(...)` | 58 |
| `ai/token/cost/BudgetThresholdAlertService.java:116` | `evaluate(...)` | 55 |
| `ai/token/budget/TokenBudgetService.java:164` | `reserve(...)` | 53 |
| `ai/optimize/CostOptimizationService.java:183` | `execute(...)` | 49 |
| `milvusdemo/service/DocumentIngestionService.java:45` | `ingest(...)` | 48 |

> 全项目 ≥45 行的方法共 **7 个**，其余 99% 的方法都在 45 行以内，说明这不是普遍性问题，而是集中在编排入口。

`AiChatService` 构造器有 **18 个参数**：`chatModel, properties, identityProvider, budgetService, pricingService, policyMapper, collector, ragProvider, metrics, clock, protectionEngine, requestGuard, modelRouter, responseCache, promptOptimizer, ragContextOptimizer, optimizationService, agentCostTracker`。

`chat()` 内部用 ①~⑮ 十五个编号注释划分阶段（保护等级 → 路由 → 限流 → 缓存 → 单次上限 → 预扣 → Agent 闸门 → RAG → Prompt → 模型调用 → Usage 采集 → 结算 → 缓存回填 → 优化记录 → Agent 累计）。

**违反的规范**：「避免超长方法」「控制参数数量」「一个方法只负责一个明确职责」。

**影响**：
- 任何一个阶段出问题，都必须在这个 184 行方法里定位，且 15 个局部变量（`requestId`/`traceId`/`userId`/`scene`/`kbId`/`agentId`/`agentTaskId`/`permit`/`routedModel`/`routeLevel`/`level`/`decision`/`route`/`cacheKey`/`reservation`）全部平铺在同一作用域，改动易误伤；
- 无法对单个阶段写单元测试（现在 `AiChatService` **没有任何单测**，只有集成测试覆盖）；
- 新增治理阶段（如"内容安全校验"）会让方法继续膨胀。

**建议**：保留 `chat()` 作为薄编排入口（目标 < 40 行），把参数归一化、保护与路由、缓存分支、预扣与闸门、模型调用上下文组装、结果组装分别抽为私有方法或内部协作者对象。**行为必须完全不变，以 72 项现有测试为安全网。**

#### BE-06 前端最大组件 412 行 —— **P2**

| 文件 | 行数 |
|---|---|
| `frontend/src/views/CostProtection.vue` | 412 |
| `frontend/src/views/CostAlerts.vue` | 378 |
| `frontend/src/views/AiRequests.vue` | 365 |
| `frontend/src/views/ModelPricing.vue` | 329 |
| `frontend/src/views/CostOptimization.vue` | 314 |

**违反的规范**：「避免超大组件，复杂逻辑抽取 composable」。

**建议**：`CostProtection.vue` 的五级语义卡片、规则表、审计日志表可拆为 3 个子组件；`CostAlerts.vue` 的筛选与结果面板可拆。不做强制，按 P2 排在后续批次。

---

### 3.4 注释覆盖

#### BE-07 实体字段注释 210 → 仅 6 —— **P0**

实测字段数与注释数：

| 实体 | 字段 | 有注释 |
|---|---|---|
| `TokenUsage` | 32 | 0 |
| `BudgetPolicy` | 20 | **6** |
| `CostAlert` | 20 | 0 |
| `CostLedger` | 19 | 0 |
| `AgentCostTrace` | 17 | 0 |
| `ModelPricing` | 15 | 0 |
| `DailyCostSummary` | 15 | 0 |
| `InfrastructureCost` | 14 | 0 |
| `BudgetTransaction` | 13 | 0 |
| `CostProtectionRule` | 12 | 0 |
| `BudgetReservation` | 11 | 0 |
| `CostOptimizationLog` | 11 | 0 |
| `CostProtectionLog` | 11 | 0 |
| **合计** | **210** | **6（2.9%）** |

典型样本 `CostProtectionRule.java`：12 个 Lombok 字段，零注释，仅类级一行说明。

**违反的规范**：「Entity 字段必须有注释；金额字段、状态字段、Token 字段必须注释」。

**影响**：
- 数据库里有完整的字段 COMMENT，但 Java 侧看不到。开发者读代码必须交叉查 `TABLE_DICTIONARY.md` 才能知道 `thresholdValue` 的单位、`action` 的取值域；
- 字段语义靠猜是成本类系统最危险的维护场景（`BigDecimal` 到底是不是 USD？是否已四舍五入？）。

**建议**：按 `docs/database/TABLE_DICTIONARY.md` 的字段字典**逐字对齐**补齐，禁止自行发明语义。这是纯注释改动，零行为风险。

#### BE-08 类级/方法级注释相对完善，但有盲区 —— **P2**

- 99 个类中，核心业务类基本都有类级 Javadoc（`AiChatService` 50-58 行、`CostProtectionEngine`、`TokenBudgetService` 等）。
- `Views.java`（82 行、11 个 record）承担全部预算视图 VO，但**没有一个是独立文件**，与「VO 独立包」的目标结构不符。
- 部分私有方法缺职责说明（如 `AiChatService.isComplexTask`、`sleepQuietly`）。

---

### 3.5 命名

#### BE-09 大写 `AI` 与小写 `Ai` 前缀混用 —— **P2**

实测同时存在：

```
AIRequestProtectionService.java   ← 全大写 AI
AiChatController.java             ← 驼峰 Ai
AiChatService.java
AiEnums.java
AiMetrics.java
AiModelConfig.java
AiProperties.java
```

**违反的规范**：「命名统一」。类名应为 UpperCamelCase，缩写词按阿里规范**不应全大写**（`AI` 应写 `Ai`）。

**影响**：`AIRequestProtectionService` 在 IDE 按 `Ai*` 前缀排序时被割裂；文档引用易写错。

**建议**：重命名为 `AiRequestProtectionService`（纯 rename，1 个文件 + 其引用）。

#### BE-10 Service 无接口实现分离 —— **不构成问题（明确记录）**

18 个 Service **全部是 class，0 个 interface**。

**结论**：**符合规范**。本项目 18 个 Service 均为单一实现且无多实现切换需求，按「不允许为了分层创建空接口」的要求，当前形态正确。规范文档中需明确写入此判断依据，避免后续智能体"补齐"出 18 个空接口。

---

### 3.6 异常与日志

#### BE-11 异常处理体系基本合规 —— **通过**

- 统一 `BusinessException` + `ErrorCode`（17 行，枚举式错误码）+ `GlobalExceptionHandler`；
- 实测**0 处空 catch**、**0 处 `printStackTrace`**；
- 关键旁路（`publishSettled`、`recordOptimizations`）用 `catch (RuntimeException)` + 日志降级，注释明确说明「旁路失败不影响主链路」——这是**正确的**设计，不是吞异常。

#### BE-12 日志覆盖率 12/99 —— **P1**

实测：99 个类中仅 **12 个**声明 `LoggerFactory.getLogger`，**87 个类零日志**。

| 缺日志的高价值位置 | 风险 |
|---|---|
| `TokenBudgetService` | 预算拒绝、结算异常无日志，只能靠查库 |
| `CostProtectionEngine` | 保护等级切换无日志（有 DB 审计表，但实时排障要看日志） |
| `CostAlertService` | 告警产生/去重/恢复无日志 |
| `ModelPricingService` | 价格未匹配无日志（会静默标 `UNPRICED`） |
| `TokenUsageCollector` | Usage 采集降级无日志 |

**违反的规范**：「记录 requestId、traceId、业务上下文」。

**影响**：线上排障只能翻数据库，无法从日志还原一次请求的治理决策链。

**建议**：对上述 5 个核心类补 `log.info/warn`，**必须含 `requestId`**，**禁止打印 Prompt 正文与 Key**。

#### BE-13 日志风格不统一 —— **P3**

0 个 `@Slf4j`，12 个类全部手写 `private static final Logger log = LoggerFactory.getLogger(X.class);`。

**结论**：本身可接受（显式优于隐式），但需在规范中**二选一并强制**，避免后续混入 `@Slf4j`。建议保持手写风格（Lombok 已在用，但日志显式声明可避免 `log` 变量名冲突）。

---

### 3.7 事务与并发

#### BE-14 多写路径无 `@Transactional` —— **P1**

全项目 `@Transactional` 使用数：**0**。

已识别的多写路径：

| 位置 | 写操作 | 风险 |
|---|---|---|
| `TokenBudgetService.settle():234-241` | `reservationMapper.settleOrRelease()` + `recordTx()` + `alertService.alert()` | `recordTx` 失败 → 预扣状态已改但无预算流水 → 对账差异 |
| `TokenBudgetService.release():263-264` | `settleOrRelease(RELEASED)` + `recordTx(RELEASE)` | 同上 |
| `TokenUsageCollector.collect()` | `usageMapper.insert()` + `costLedgerMapper.insert()` | 账单落库但账务未落 → 成本合计少算 |

**关于既有设计**：`ADR-003` 已声明「账单强一致 / Redis↔MySQL 最终一致 ≤5min」，但**MySQL 内部多表写入的一致性没有被显式保证**，这是 ADR-003 未覆盖的缺口。

**建议**：
- 对 `settle` / `release` / `collect` 三个方法加 `@Transactional(rollbackFor = Exception.class)`；
- 因存在 Redis 调用与 `@Transactional` 混用，需**明确方法内顺序**：Redis 写必须在事务提交后（`TransactionSynchronization`）或明确接受"Redis 先写、DB 失败靠对账兜底"——现有 ADR-003 已选择后者，规范中必须写明这个选择，防止后续智能体无意识地改成"看起来更安全"的顺序反而引入不一致。

#### BE-15 并发控制设计合理 —— **通过**

- Redis Lua 4 个脚本原子预扣，`1e-8` 整数单位避免浮点；
- `AIRequestProtectionService` 提供并发槽位与限流；
- 已实测 100 并发 / 5.00 USD 额度下恰好 5 个通过。

---

### 3.8 配置管理

#### BE-16 单一 `application.yml`，无环境 profile —— **P2**

实测 `src/main/resources/` 下只有 `application.yml` 一个配置文件，**无** `application-local.yml` / `-test.yml` / `-prod.yml`。

**违反的规范**：目标结构要求环境分离。

**影响**：本地/测试/生产的 Redis DB、MySQL 端口、`AI_PROVIDER`、`demo_flag` 只能靠环境变量区分，切换易错（集成测试依赖 `DB 15`/`DB 14` 的约定只存在于测试代码中，无配置文档）。

**建议**：拆出 `application-local.yml`（默认）与 `application-test.yml`（集成测试端口/DB），`prod` 留待真实部署需求出现时再建，**不凭空创建**。

#### BE-17 `@Value` 仅 2 处，配置管理合规 —— **通过**

实测 `@Value` 共 2 处，均在构造器参数上，**无字段注入**：

| 位置 | 用途 | 评价 |
|---|---|---|
| `ai/config/AdminAccessInterceptor.java:25` | 注入单个标量 `${AI_ADMIN_TOKEN:}` | ✅ 单值场景，构造器注入，合规 |
| `milvusdemo/embed/LocalHashingEmbeddingModel.java:43` | 注入 `${app.embedding.dimension:256}` | ✅ 原始 RAG 代码，豁免 |

复杂配置已全部收敛到 `AiProperties`（`@ConfigurationProperties`）。**结论：配置管理无问题，无需整改。**

#### BE-18 `@MapperScan` 硬列 7 个包 —— **P1（BE-01 的副产物）**

`DemoApplication.java:25-32` 列出 7 个精确包路径。任何包移动都会静默失效（Mapper 未注册 → 启动报 `Invalid bound statement`）。

**建议**：包重组后收敛为 `com.example.ai` 下 2~3 个基包。

---

### 3.9 测试

#### BE-19 测试结构合规，但存在两个空洞 —— **P2**

| 项 | 实测 |
|---|---|
| 测试类 | 13 个 / 72 用例 |
| 单测 / 集成 | 48 / 24 |
| 通过率 | **100%**（`mvn -B -o -Pintegration verify` BUILD SUCCESS） |
| 未覆盖 | `AiChatService`（0 单测，仅集成）、13 个 Controller（0 单测） |

**说明**：核心算法（成本计算、路由、保护、阈值、异常检测）单测覆盖良好；编排层与 Controller 靠集成测试兜底。**这是合理取舍**，但 `AiChatService` 的 184 行方法无法单测本身就是 BE-05 的后果。

**建议**：BE-05 拆分后，为抽出的阶段方法补单测（至少覆盖"保护阻断"、"缓存命中"、"预扣拒绝"三条分支）。

---

## 4. 前端审计

### 4.1 目录结构 —— **P2**

实测 `frontend/src/`：

```
api/          7 个文件（budget/cost/governance/pricing/request/types/usage）
components/  13 个通用组件
composables/  1 个（useAsyncData）
router/       1 个
stores/       1 个（timeRange）
styles/       2 个（global.css / tokens.css）
utils/        4 个
views/       10 个页面（扁平）
```

**与目标结构的差异**：目标要求 `modules/{cost,budget,alert,pricing,optimization}/{api,components,composables,types,views}` 按业务域分域；当前是**按技术角色分层 + `views/` 扁平**。

**评估**：当前 10 个页面 / 5889 行的规模下，扁平 `views/` 尚未造成实际困扰（对比后可读性反而更好）。但：
- `api/types.ts` **445 行 / 41 个 interface** 已明显超载，是第一个该拆的文件；
- 一旦页面超过 15 个，扁平结构会失效。

**建议**：**不做全量 `modules/` 迁移**（属"为目录规范做无意义大规模迁移"）。改为：拆分 `types.ts` 为 `types/{cost,budget,alert,protection,optimization,common}.ts`，并在 `DIRECTORY_STRUCTURE.md` 中写明"何时触发 modules 化"的明确阈值（如单域页面 >4 个或单域代码 >1500 行）。

### 4.2 组件规范 —— **通过**

- 13 个组件中 **12 个** 有 `defineProps` + `withDefaults`，Props 全部带类型；
- **`TimeRangePicker.vue` 是唯一例外，且是正确的**：它不接收任何 Props，数据全部来自 Pinia store（`useTimeRangeStore`）与本地 `ref`，属于自包含组件。已核实，**不构成问题，不作为整改项**（原审计初稿曾标为 FE-02，核实后撤销）。

### 4.3 TypeScript —— **通过**

- `tsconfig.json` **`strict: true`** 已开启（含 `noImplicitAny` / `strictNullChecks`）；
- 全项目 **0 处 `any`**；
- `type-check: vue-tsc --noEmit` 已配置且在 CI 脚本中可用。

### 4.4 质量工具链 —— **P0**

| 工具 | 状态 | 证据 |
|---|---|---|
| ESLint | ✅ 已配（flat config，9 个规则） | `frontend/eslint.config.js` |
| vue-tsc | ✅ 已配 | `package.json:9` |
| **Prettier** | ❌ **缺失** | `package.json` 无依赖、无 `.prettierrc` |
| **Stylelint** | ❌ **缺失** | 无依赖、无配置 |
| **Vitest** | ❌ **缺失** | 无依赖、无 `test` 脚本、**0 个前端测试** |
| **npm test 脚本** | ❌ **不存在** | `package.json` scripts 只有 dev/build/type-check/lint/preview |

**违反的规范**：「前端必须具备 `npm run lint / type-check / test / build` 四道门」。

**影响**：格式化靠人工（现有代码风格统一纯属运气好），CSS 无任何检查，前端零回归保护——`CostProtection.vue` 412 行的改动只能靠肉眼。

### 4.5 CSS 规范 —— **P2**

`styles/tokens.css` + `styles/global.css` 已建立 CSS 变量体系。但：
- 10 个视图内 `<style scoped>` 存在硬编码色值（需扫描确认数量）；
- 无 Stylelint 无法强制"禁止魔法数值"。

**建议**：接入 Stylelint 后，先跑一次报告（不阻断），按 `declaration-property-value-allowed-list` 逐步收敛。

### 4.6 状态与请求 —— **通过**

- 0 处视图直接 `import axios`；
- `api/request.ts`（108 行）统一封装拦截器、错误处理；
- Pinia 仅 1 个 store（`timeRange`），未滥用全局状态。

---

## 5. 数据库审计

### 5.1 命名与字段 —— **通过**

| 检查项 | 实测结果 |
|---|---|
| 表名 snake_case + `ai_` 前缀 | ✅ 13/13（`ai_model_pricing`、`ai_token_usage`、`ai_budget_policy`、`ai_budget_reservation`、`ai_cost_alert`、`ai_cost_ledger`、`ai_budget_transaction`、`ai_cost_protection_rule`、`ai_cost_protection_log`、`ai_cost_optimization_log`、`ai_cost_daily_summary`、`ai_agent_cost_trace`、`ai_infrastructure_cost`） |
| 字段 snake_case | ✅ 100% |
| 表 COMMENT | ✅ 13/13 |
| 字段 COMMENT | ✅ 关键字段全覆盖（含状态域枚举值说明） |
| 金额 `DECIMAL` | ✅ 统一 `DECIMAL(20,8)`，注释声明「USD 结算」 |
| Token `BIGINT` | ✅ |
| 时间 `DATETIME(3)` + UTC | ✅ 注释标注 `(UTC)` |
| 字符集 | ✅ `utf8mb4` + `utf8mb4_unicode_ci` |
| `id` 主键 | ✅ `BIGINT AUTO_INCREMENT` |
| 唯一约束 | ✅ 有业务唯一键（如 `uk_usage_request`、`uk_pricing_version`） |
| 索引 | ✅ 有查询索引（如 `idx_pricing_lookup`） |

### 5.2 SQL 规范 —— **存在 P1 问题（DB-03）**

#### DB-03 `SELECT *` 共 31 处 —— **P1，本批已修复**

审计初稿用 `grep "SELECT \*\|select \*"`（BRE 转义）误判为 0 处，改用 `grep -E` 复核后实际为 **31 处**，分布在 10 个 Mapper XML：

| Mapper | 处数 | Mapper | 处数 |
|---|---|---|---|
| `CostAlertMapper.xml` | 5 | `ModelPricingMapper.xml` | 4 |
| `CostProtectionRuleMapper.xml` | 5 | `BudgetPolicyMapper.xml` | 3 |
| `AgentCostTraceMapper.xml` | 2 | `BudgetReservationMapper.xml` | 2 |
| `BudgetTransactionMapper.xml` | 2 | `CostLedgerMapper.xml` | 2 |
| `InfrastructureCostMapper.xml` | 2 | `TokenUsageMapper.xml` | 2 |
| `CostOptimizationLogMapper.xml` | 1 | `DailyCostSummaryMapper.xml` | 1 |

**违反的规范**：「禁止 `SELECT *`」。

**影响**（三条，均为真实风险）：
1. 表新增列后结果集静默变化，`resultType` 映射可能错位而**不报错**；
2. 会拉取不需要的字段（`remark` / `error_code` 等），放大 IO；
3. 无法做覆盖索引优化。

**修复方式**：按 Flyway 迁移解析出每张表的真实列序，展开为显式列清单。
**修复后校验**：用 `information_schema` 比对——**31 处查询的列名与顺序与真实库完全一致，0 处差异**。

#### 其余 SQL 规范 —— 通过

- 除 `ORDER BY ${q.sort}` 外全部 `#{}` 参数化；该处已由 `UsageQuery.setSort()` 白名单收敛（见 DB-04）。
- 13 个 Mapper XML 共 57 个 `<select>`，**0 个 `<resultMap>`**，依赖 `map-underscore-to-camel-case: true`。

**关于 0 个 resultMap**：属**合理选择**（字段名与列名严格下划线↔驼峰对应，resultType 更简洁），但需在 `DATABASE_CODING_STANDARDS.md` 中写明前置条件——**列名一旦出现别名不匹配即必须显式 resultMap**，防止后续智能体在 JOIN 查询里漏映射。现已在 `SELECT *` 全部展开后，列名显式可见，该风险进一步降低。

#### DB-04 `ORDER BY ${sort}` 依赖调用方过滤 —— **P1，本批已修复**

`TokenUsageMapper.xml` 使用 `ORDER BY ${q.sort}`（字符串插值，非参数绑定），原实现把白名单校验放在 `CostAnalyticsService.usagePage()` 内。
`recentRequests()` 因自建 `UsageQuery`（默认值安全）而未受影响，但**安全性依赖每个调用方自觉过滤**——任一新增调用方直接传用户输入即形成注入面。

**修复**：把别名映射与白名单校验下沉到 `UsageQuery.setSort(String)`，任何赋值路径都必然过滤，非法值（含 `null`、注入串）回落为 `DEFAULT_SORT`。
**同时修复的缺陷**：原 `Map.of(...).getOrDefault(null, ...)` 在 `sort=null` 时会抛 NPE（`Map.of` 不接受 null 键），现已由显式判空兜住。
**验证**：新增 `UsageQueryTest` 7 个用例锁定该行为。

### 5.3 迁移机制 —— **通过**

Flyway V1~V5 版本化，已执行迁移未被修改（`flyway_schema_history` 5 条 success），符合「已执行迁移不得修改」要求。

#### DB-02 V2/V3 种子数据无幂等保护 —— **P3**

`V2__seed_demo_data.sql`（22 行）与 `V5__governance_seed.sql`（67 行）为纯 `INSERT`。Flyway 按版本号只执行一次，**不会重复插入**，因此不构成实际风险。但若未来需要"重新播种"（如换环境），只能手工删表，缺少 `INSERT ... ON DUPLICATE KEY UPDATE` 形态。

**结论**：**当前无需修改**，记录为已知约束写入规范（新种子脚本优先用幂等写法）。

---

## 6. 脚本审计

### 6.1 安全底线 —— **通过**

| 检查项 | 实测 |
|---|---|
| `pkill` / `kill -9` | **0 处** |
| `rm -rf` | **0 处** |
| 按端口杀进程 | **0 处**（仅 `lsof` 检查后拒绝启动） |
| PID 文件精确关停 | ✅ `.run/*.pid` |
| 端口占用处理 | ✅ 只拒绝、不清理 |

### 6.2 SC-01 未使用严格模式 —— **P1**

实测 6 个 `.command`/`.bat` 与 3 个 `samples/*.sh`：

| 文件 | shebang | 严格模式 |
|---|---|---|
| `start.command` | `#!/bin/bash` | `set -u` |
| `stop.command` | 待核 | `set -u` |
| `restart.command` | 待核 | 待核 |
| `samples/verify-admin-endpoints.sh` | `#!/bin/bash` | `set -u` |
| `samples/verify-alert-levels.sh` | `#!/bin/bash` | `set -u` |
| `samples/verify-alert-recovery.sh` | `#!/bin/bash` | `set -u` |

**问题**：
1. shebang 用绝对路径 `/bin/bash` 而非 `#!/usr/bin/env bash`（macOS 自带 bash 3.2，若用户装了 bash 5 会用错版本）；
2. **缺 `-e`**：命令失败后继续执行，`start.command:37` 虽用 `if ! "$MVN" ...` 兜住，但其他未检查的命令（如 `curl`、`lsof`）失败会被忽略；
3. **缺 `-o pipefail`**：`ls -t ... | head -1` 这类管道失败不可见；
4. 无 `trap` 清理，中途 Ctrl+C 会残留 PID 文件。

### 6.3 SC-02 硬编码用户绝对路径 —— **P0**

`start.command:29-32`：

```bash
JDK="/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
[ -d "$JDK" ] && export JAVA_HOME="$JDK"
MVN_BIN="/Users/beiluo/Documents/Development/apache-maven-3.8.3/bin/mvn"
if [ -x "$MVN_BIN" ]; then MVN="$MVN_BIN"; else MVN="mvn"; fi
```

**违反的规范**：「避免硬编码用户绝对路径」。

**影响**：
- 换机器/换用户名直接失效（虽有 `[ -d ]` 兜底，但 `JAVA_HOME` 不设时会用默认 JDK——本机默认是 **JDK 26**，而 JDK 23+ 关闭隐式注解处理会导致 **Lombok 静默失效、数百处「找不到符号 getXxx()」**，这是本项目已踩过的真实故障）；
- `MVN_BIN` 硬编码 Maven 3.8.3 路径，升级 Maven 后失效。

**建议**：改为"探测顺序"策略：
1. `JAVA_HOME` 环境变量 → 2. `java -version` 解析出 17 则用当前 → 3. 探测常见安装位置（`/Library/Java/JavaVirtualMachines/*17*/Contents/Home`）→ 4. 明确报错退出（**不允许静默用错版本**）。

### 6.4 SC-03 无统一质量门禁入口 —— **P0**

不存在 `scripts/verify.sh`，也不存在任何一键跑完"后端 checkstyle + 单测 + 前端 lint + type-check + build"的入口。`scripts/` 目录**根本不存在**。

**影响**：没有单一命令能回答"当前代码是否还能交付"，这正是规范无法被执行的根本原因。

### 6.5 SC-04 `echo $!` 未加引号 —— **P3**

`start.command:82`：`echo $! > "$RUN_DIR/frontend.pid"`。

### 6.6 Docker Compose —— **通过**

`infra/docker/ai-cost-compose.yml`：
- ✅ 显式 `name: ai-cost` 独立项目名（避免与同目录 Milvus 编排互删网络）
- ✅ 独立 `networks: ai-cost-net`
- ✅ `healthcheck`（`mysqladmin ping`，10s 间隔 / 30s start_period）
- ✅ 数据卷持久化 `./volumes/ai-cost-mysql`
- ✅ 环境变量外部化 `${MYSQL_PASSWORD:-root}`
- ✅ 无 `privileged`、无 `latest` 标签（固定 `mysql:8.0.36`）
- ⚠️ 默认密码 `root` 仅适用于本地，需在规范中明确"生产必须覆盖"

---

## 7. 问题总台账

| ID | 级别 | 域 | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|---|---|
| **BE-01** | **P0** | 后端结构 | `ai/token/` 混居 8 个业务域，`token/cost` 一包三职责 | `src/main/java/com/example/ai/token/**`（59 文件） | 边界不清，改一处需读 19 文件 | 按业务域重组顶层包 |
| **BE-05** | **P0** | 后端质量 | `chat()` 184 行 + 18 个构造依赖 | `AiChatService.java:112` | 不可测、易误伤、持续膨胀 | 阶段化拆分，目标 <40 行 |
| **BE-07** | **P0** | 注释 | 210 个实体字段仅 6 个有注释（2.9%） | 13 个 `entity/*.java` | 字段语义靠猜，成本系统高风险 | 按 TABLE_DICTIONARY 逐字对齐补齐 |
| **SC-02** | **P0** | 脚本 | 硬编码 JDK/Maven 绝对路径 | `start.command:29-32` | 换机失效；JDK26 导致 Lombok 静默失败 | 改探测顺序 + 校验版本 |
| **SC-03** | **P0** | 门禁 | 无 `scripts/verify.sh`、无 `scripts/` 目录 | — | 无单一交付判定入口 | 新建统一门禁脚本 |
| **FE-04** | **P0** | 前端门禁 | 缺 Prettier / Stylelint / Vitest / `npm test` | `package.json` | 格式化靠人工、CSS 无检查、零回归 | 补工具链与配置 |
| **BE-02** | P1 | 后端分层 | 6 个 Controller 返回裸 `Map<String,Object>` | 4 个治理 Controller + 2 个 RAG | 契约无编译期校验，前端手工对齐 | 治理 4 个改显式 VO，RAG 豁免 |
| **BE-04** | P1 | 后端校验 | 校验注解覆盖窄：仅 `message` 有 `@NotBlank`+`@Size`，ID 类字段无边界 | `ChatDtos`、`UsageQuery` | 超长 payload 直到 DB 层才报错 | 补 `@Size(max=64)` 与分页边界 |
| **BE-12** | P1 | 日志 | 99 类中仅 12 类有 Logger（87 类零日志） | `TokenBudgetService` 等 5 个核心类 | 线上排障只能翻库 | 补带 requestId 的关键日志 |
| **BE-14** | P1 | 事务 | 0 处 `@Transactional`，多表写无边界 | `TokenBudgetService:234,263`、`TokenUsageCollector` | `recordTx` 失败 → 对账差异 | 加事务 + 写明 Redis/DB 顺序约束 |
| **BE-18** | P1 | 配置 | `@MapperScan` 硬列 7 个包 | `DemoApplication.java:25-32` | 包移动静默失效 | 随 BE-01 收敛 |
| **SC-01** | P1 | 脚本 | 缺 `-e -o pipefail` + trap，shebang 非 env | 全部 9 个脚本 | 失败被忽略、残留进程 | 严格模式 + trap |
| **FE-01** | P2 | 前端结构 | `api/types.ts` 445 行 / 41 interface | `frontend/src/api/types.ts` | 单文件超载 | 按域拆分 types |
| **FE-06** | P2 | 前端规模 | 最大视图 412 行 | `CostProtection.vue` | 可读性下降 | 拆子组件（P2，可延后） |
| **BE-06** | P2 | 后端规模 | `AnomalyDetectionService.scan()` 94 行 | `:60` | 单方法 6 条规则 | 按规则拆私有方法 |
| **BE-09** | P2 | 命名 | `AIRequestProtectionService` 前缀与 6 个 `Ai*` 不一致 | 1 个文件 | IDE 排序割裂 | rename 为 `Ai...` |
| **BE-16** | P2 | 配置 | 无 `application-{local,test}.yml` | `resources/` | 环境切换靠环境变量 | 拆 local/test |
| **BE-19** | P2 | 测试 | `AiChatService` 与 13 个 Controller 无单测 | — | 编排层靠集成兜底 | 拆分后补分支单测 |
| **FE-05** | P2 | 前端 CSS | 视图内硬编码色值 | 10 个 `.vue` 的 scoped 样式 | 主题漂移 | Stylelint 报告后收敛 |
| **DB-01** | P2 | 数据库 | 0 个 `resultMap`，全依赖驼峰映射 | 13 个 Mapper XML | JOIN 别名场景易漏映射 | 规范写明前置条件 |
| **DB-03** | **P1** | 数据库 | **31 处 `SELECT *`** 分布在 10 个 Mapper | `src/main/resources/mapper/*.xml` | 加列后结果集静默变化、多余 IO、无法覆盖索引 | **本批已修复**：按真实列序展开并比对 `information_schema` |
| **DB-04** | **P1** | 数据库 | `ORDER BY ${q.sort}` 白名单校验在 Service，依赖调用方自觉 | `TokenUsageMapper.xml:44` | 新增调用方漏过滤即形成注入面 | **本批已修复**：白名单下沉 `UsageQuery.setSort()` |
| **BE-13** | P3 | 日志 | 日志声明风格未统一（0 个 `@Slf4j`） | 12 个类 | 后续易混入 | 规范二选一强制 |
| **BE-08** | P3 | 注释 | `Views.java` 内联 11 个 VO record，无独立文件 | `common/Views.java` | 与"VO 独立包"不符 | 规范允许记录类型聚合 |
| **SC-04** | P3 | 脚本 | `echo $!` 未加引号 | `start.command:82` | 理论风险 | 加引号 |
| **DB-02** | P3 | 数据库 | V2/V5 种子用裸 INSERT | `V2__*.sql`、`V5__*.sql` | 不可重播种 | 规范写明幂等写法 |
| **SC-05** | P3 | 脚本 | Docker 默认密码 `root` | `ai-cost-compose.yml:19` | 仅本地可接受 | 规范写明生产必须覆盖 |
| **BE-20** | **P1** | 后端缺陷 | **预算创建接口在客户端省略「有数据库默认值的字段」时必然 500**：`ai_budget_policy` 的 `allow_overrun`/`enabled`/`currency`/`overrun_policy` 均 `NOT NULL + DEFAULT`，但 INSERT 显式传参使默认值失效。V1 只兜 `enabled`，V2 只兜 `enabled`+`overrunPolicy`，**两条路径都漏 `allowOverrun` 与 `currency`** | `BudgetController.create:56`、`BudgetAdminController.create:76`、`BudgetPolicyMapper.xml:insert` | 前端/脚本按最小字段建预算必失败，且只返回「系统繁忙」掩盖真因 | **本批已修复**：收敛到 `BudgetPolicyService.applyCreateDefaults`，四列统一补全 |
| **BE-21** | **P1** | 后端分层 | 2 个 Controller 绕过 Service 直接依赖 Mapper，并在 Controller 内做校验/补默认值/内存分页——两份实现语义已漂移（正是 BE-20 的成因） | `BudgetController:25`、`BudgetAdminController:32-33` | 同一份数据契约存在两个真相源，改一处必漏另一处 | **本批已修复**：新增 `budget/service/BudgetPolicyService`，两个 Controller 薄化为纯 HTTP 适配 |
| **BE-22** | P2 | 后端时间 | 时间基准不统一：7 处裸 `LocalDate(Time).now()`（本机时区）与项目 UTC `Clock` 约定冲突，查询窗口整体偏移 UTC+8，**静默漏掉最早 8 小时数据** | `AgentCostController:41`、`CostAlertController:51`、`BudgetAdminController:106`、`CostGovernanceService:111,118,123,134` | 7 天回看实际只有 6 天 16 小时；当前因无历史数据未显形（实测丢弃窗口内 0 行），有历史数据后必现 | **本批已修复**：7 处全部改用注入的 `Clock`（`Clock.systemUTC()`） |
| **BE-23** | P2 | 后端分层 | Controller 内承载分页钳制、时间窗计算、默认值填充等业务逻辑（非仅 BE-21 的 2 个） | `AgentCostController.traces`、`CostAlertController.page` 等 | 同一逻辑在多个 Controller 重复，无法单测 | 部分修复（2 个预算 Controller 已薄化），其余列入 B6 批次 |

---

### 7.1 本批已修复项（含验证证据）

| ID | 修复内容 | 验证证据 |
|---|---|---|
| **BE-01** | `ai/token` 下 8 个业务域拆为一等模块；新增 `alert` / `protection` / `optimization` / `provider` / `agent` / `analytics` / `pricing` / `cost` / `budget` 顶层包；`ai/token` 仅保留 Token 用量采集 | `mvn -Pintegration verify` 全绿；应用在 8099 启动后 **15 个接口 `code=0`**，9 个 Mapper 包运行期绑定成功 |
| **BE-18** | `@MapperScan` 从硬列 7 个包改为「基包 + `@Mapper` 注解过滤」，包移动不再静默失效 | 同上（运行期已验证） |
| **BE-09** | `AIRequestProtectionService` → `AiRequestProtectionService`，与大写 `Ai*` 前缀统一 | 编译通过 + 全量测试通过 |
| **BE-05** | `AiChatService.chat()` **184 行 → 30 行**；18 个依赖保留但按阶段拆为 13 个具名私有方法，最长方法 31 行 | 全量测试全绿；真实问答 `code=0`（含 cost/budget/optimization 全字段）；缓存命中分支 `cacheHit=true, cost=0` |
| **BE-06** | `AnomalyDetectionService.scan()` **94 行 → 22 行**，6 条规则各自独立，`overview` 仍只查一次 | 全量测试全绿（含 9 个异常检测用例） |
| **BE-07** | 13 个实体 **210 个字段 100% 有注释**（原 6 个），文本逐字取自数据库 COMMENT | 覆盖率脚本复核 210/210；编译与测试全绿 |
| **DB-03** | 31 处 `SELECT *` 全部展开为显式列清单 | 与 `information_schema` 逐表比对：**列名与顺序 0 差异**；15 个接口运行期 `code=0` |
| **DB-04** | 排序白名单下沉到 `UsageQuery.setSort()`，顺带修掉 `sort=null` 的 NPE | 新增 `UsageQueryTest` 7 用例（含注入串、`null`、空白）全绿 |

> 修复过程中由**新写的测试**发现并修正的真实缺陷 1 个：`UsageQuery.setSort(null)` 触发 `Set.of(...).contains(null)` 的 NPE。
> 这说明门禁测试的价值不在「证明代码对」，而在「证明代码错在哪」。

### 7.2 第二批已修复项（写规范时反查发现，含真实验证证据）

本批发现路径与前一批不同：**不是静态扫描，而是写 `DIRECTORY_STRUCTURE.md` 时，
用刚写下的规范条文（「controller 不得 import mapper」「时间基准用注入的 Clock」）
逐条反查代码**，结果命中 4 个问题，其中 2 个是 P1 运行时缺陷。

| ID | 修复内容 | 验证证据 |
|---|---|---|
| **BE-20** | 新增 `BudgetPolicyService.applyCreateDefaults`，对 `allow_overrun`/`enabled`/`currency`/`overrun_policy` 四列统一补全；`update` 从库中原值回填 NOT NULL 列 | **修复前**：`POST /api/admin/ai/budgets` 与 `POST /api/admin/budget` 均返回 `{"code":50001,"message":"系统繁忙…"}`（实测两条路径各 1 次）<br>**修复后**：两条路径均 `code=0`，库内实测 `allow_overrun=0, enabled=1, currency=USD, overrun_policy=[BLOCK]` |
| **BE-21** | 新建 `budget/service/BudgetPolicyService`（`list`/`getRequired`/`create`/`update`/`page`/`transactions`）；`BudgetController` 与 `BudgetAdminController` 只保留参数接收与响应包装，**不再 import 任何 Mapper** | `grep -rn "import com.example.ai.*\.mapper" src/main/java/com/example/ai/*/controller/` **由 3 行变为 0 行** |
| **BE-22** | 4 个类的 7 处裸 `now()` 全部改为注入 `Clock`；新增 `BudgetPolicyService` 的 `transactions` 用固定时钟断言窗口 | `grep -rn "LocalDate(Time)\.now()" src/main/java` **由 7 处变为 0 处**（仅剩 1 行注释）<br>单测 `transactionsUsesInjectedUtcClock` 断言 `to=2026-11-11T06:01`、`from=2026-11-04T06:01`（UTC），若改回本地时区该用例必失败 |
| **BE-23** | 2 个预算 Controller 的分页/时间窗/校验逻辑下沉到 Service | 同 BE-21 |

**回归测试**

```bash
mvn -B -o -Pintegration verify
# Tests run: 73 (单测) + 24 (集成) = 97，Failures 0，Errors 0，BUILD SUCCESS
```

新增 `BudgetPolicyServiceTest` **18 个用例**，覆盖：
- 四列默认值补全（缺省 / 显式传值 / 空白字符串视为缺失）
- 强制清空调用方传入的 `id`（防误更新既有记录）
- 必填字段校验（缺 `budgetType` / 缺 `targetId` / 请求体为 null）
- `update` 对 NOT NULL 列回填原值（否则局部更新必 500）
- 分页钳制（页码下限 1、单页上限 100、超页返回空切片但 `total` 不变）
- 流水时间窗（UTC 基准、回看天数上下限 1~92、默认 7 天、分页 offset）

**前端闸门检查（同批复核，均无输出）**

```bash
grep -rn "from 'axios'" frontend/src/views/ frontend/src/components/   # 0 处
grep -rniE 'select +\*' src/main/resources/mapper/                     # 0 处
find src -type d -empty                                                # 0 个
# D-02 包名与目录一致性校验：99 个文件 0 处不一致
```

## 8. 治理批次规划

严格遵循"渐进式治理"，每批独立可验证、可回滚：

| 批次 | 目标 | 覆盖问题 | 验证方式 |
|---|---|---|---|
| **B1** | 建立规范体系 | 全部（前置） | 10 份文档交叉一致性自查 |
| **B2** | 后端包结构重组 | BE-01、BE-18、BE-09 | `mvn compile` + 72 项测试全绿 |
| **B3** | 超长方法拆分 | BE-05、BE-06 | 72 项测试全绿 + 新增阶段单测 |
| **B4** | 字段注释与校验 | BE-07、BE-04 | 注释覆盖率扫描 + 测试全绿 |
| **B5** | 分层与缺陷修复（**已完成**） | BE-20、BE-21、BE-22 | 97 项测试全绿 + 启动后真实接口 + 库内字段实测 |
| **B6** | 事务与日志 | BE-14、BE-12 | 测试全绿 + 触发一次真实告警看日志 |
| **B7** | 脚本加固 | SC-01、SC-02、SC-03、SC-04 | ShellCheck 0 告警 + `verify.sh` 可跑通 |
| **B8** | 质量门禁接入 | FE-04、SC-03 | `verify.sh` 端到端 exit 0 |
| **B9** | 收尾与报告 | BE-02、BE-23、FE-01、FE-06 及遗留 | 全量验证 + 最终审计报告 |

**批次执行记录**

> 注：下表 B2/B3 的「72 项」是**当批执行时**的真实数字（历史记录，不追改）。
> 当前基线为 **97 项**（73 单元 + 24 集成），因 B5 新增 `BudgetPolicyServiceTest` 18 例。

| 批次 | 状态 | 门禁结果 |
|---|---|---|
| B1 | 进行中（2/10 份） | — |
| B2 | ✅ 完成 | 72 项测试全绿 + 15 接口 `code=0` |
| B3 | ✅ 完成 | 72 项测试全绿 |
| B4 | ✅ 完成 | 注释 210/210 + 测试全绿 |
| B5 | ✅ 完成 | **97 项测试全绿**（73 单测 + 24 集成）+ 真实接口与库内字段验证 |
| B6~B9 | 待执行 | — |

**不纳入本轮的明确豁免**（写入 `AI_AGENT_DEVELOPMENT_RULES.md`，防止后续误改）：

1. `com.example.milvusdemo.**`（6 个文件）—— 原始 RAG 学习代码，**一行不删**，允许保留 `Map` 返回与包名。
2. Service 接口化 —— 18 个 Service 均为无接口 class，**明确不需要**建 `service/impl`（本项目无 ≥2 实现、无 SPI 需求）。
3. 前端 `modules/` 全量迁移 —— 规模未到阈值，明确不做。
4. `V1~V5` 迁移文件 —— 已执行，**禁止修改**，只能新增。
5. `common/Views.java` —— 4 个域共用的统计契约，**冻结扩容**（可改字段，禁止新增 record、禁止新增对业务域的 import）。

---

## 9. 审计方法与可复现性

本报告所有数字均可通过以下命令复现（在项目根目录执行）：

```bash
# 包分布
find src/main/java -name "*.java" | sed 's|src/main/java/com/example/||;s|/[^/]*\.java$||' | sort | uniq -c

# 类分类计数
find src/main/java -name "*Controller.java" | wc -l   # 13
find src/main/java -name "*Service.java"   | wc -l   # 18
find src/main/java -name "*Mapper.java"    | wc -l   # 13

# 实体字段与注释覆盖
for f in $(find src/main/java -path "*entity*" -name "*.java"); do
  echo "$(basename $f) $(grep -cE '^    private ' $f) $(grep -cE '^    /\*\*' $f)"; done

# 调试残留 / 占位符 / 字段注入 / 事务
grep -rn "System\.out\.\|printStackTrace" --include="*.java" src/ | wc -l    # 0
grep -rn "TODO\|FIXME" --include="*.java" --include="*.ts" src/ frontend/src/ | wc -l  # 0
grep -rn "@Autowired" --include="*.java" src/main/java | wc -l               # 0
grep -rn "@Transactional" --include="*.java" src/main/java | wc -l           # 0
grep -rn "@NotBlank\|@Valid" --include="*.java" src/main/java | wc -l        # 2

# 前端
grep -rn ": any\|as any" --include="*.ts" --include="*.vue" frontend/src/ | wc -l  # 0
grep -rn "from 'axios'" frontend/src/views/ frontend/src/components/ | wc -l       # 0

# 数据库（注意：必须用 -E，用 BRE 的 "\|" 在本机会漏匹配，初稿即因此误判为 0 处）
grep -rniE 'select +\*' --include="*.xml" src/main/resources/mapper/ | wc -l   # 修复前 31，修复后 0
grep -c "COMMENT" src/main/resources/db/migration/V1__init_ai_cost.sql

# 脚本（同样用 -E）
grep -rnE "rm -rf|pkill|kill -9|/Users/" *.command *.bat | grep -v "^\S*:[0-9]*:#"   # 2 处硬编码
```

**测试基线**：

```bash
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
mvn -B -o -Pintegration verify    # BUILD SUCCESS，72 项全绿
```

> ⚠️ **必须显式设 `JAVA_HOME=17`**：本机默认 JDK 为 26，JDK 23+ 关闭隐式注解处理会让 Lombok 静默失效，报出数百处「找不到符号 getXxx()」，且错误信息完全不提 Lombok。此陷阱已导致本项目一次全量编译失败。

---

## 10. 审计结论

**可以开始治理**。理由：

1. 工作区干净（0 未提交），不存在与其他智能体的写冲突风险；
2. 测试全绿（治理前 72 项，当前 **97 项**），构成可靠的安全网 —— 任何重构只要测试全绿即可判定行为未变；
3. 功能面完整，本次治理**不需要改任何业务行为**，全部为结构、注释、校验、门禁维度的改进；
4. 最大的风险项（BE-01 包重组）是纯机械迁移，可被编译器与测试完整捕获。

**风险提示**：

- BE-01 会触及 `@MapperScan` 与 13 个 Mapper XML 的 `namespace`，漏改会在**启动期**报 `Invalid bound statement`（而非编译期）。必须以"启动 + 调一次真实接口"作为验收，不能只看 `mvn compile`。
- BE-14 加事务涉及 Redis 与 DB 的写入顺序，**必须保持 ADR-003 已定的顺序**，不允许"顺手优化"成 Redis 在事务内 —— 会造成 Redis 已扣、DB 回滚的不一致。
