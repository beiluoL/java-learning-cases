# 项目审计报告 · PROJECT_AUDIT

> 审计对象：`03-ai-agent/spring-ai-milvus-demo`
> 审计时点：改造为「企业级大模型成本控制与治理系统 V1.0」的起点（Batch 0）
> 审计方法：静态代码通读 + 实际启动 + 接口实测 + 数据库/Docker 端口核查。**未使用推测，所有结论都可复现。**

---

## 1. 审计结论摘要

| 维度 | 结论 |
|---|---|
| 技术栈 | Java 17 / Spring Boot 3.4.5 / Spring AI 1.0.0 GA / MyBatis 3.0.4 / Flyway / MySQL 8.0 / Redis / Vue3+TS+Vite。**不需要升级任何主版本**，治理能力可在现有版本上完成 |
| 已有能力 | 单次调用成本计量链路（Usage 采集 → 三段计价 → 落库 → 统计）已闭环；Redis Lua 预算预扣/结算/释放已具备 |
| 主要缺口 | 6 项（见第 4 节）：无优化引擎、无多级预算、无限流并发、无告警中心化、无自动保护、无 Agent 成本约束 |
| 可复用度 | 预算 Lua、价格版本、账务口径、统一响应、前端组件库均可直接复用，无需重写 |
| 不可触碰项 | 原 `com.example.milvusdemo` RAG 代码 6 个类、端口 8080（其他项目占用） |
| 环境坑 | 3 项已确认并已规避（见第 5 节）：Milvus/MinIO 9000 端口冲突、HTTP_PROXY 劫持、Maven 默认 JDK 漂移到 26 |

---

## 2. 改造前项目形态

### 2.1 代码规模

| 项 | 数量 | 说明 |
|---|---|---|
| Java 文件 | 99（改造前 62） | `com.example.ai` 93 个 + 原 RAG `com.example.milvusdemo` 6 个 |
| Mapper XML | 13（改造前 6） | SQL 全部集中在 `resources/mapper/`，Controller/Service 无裸 SQL |
| Lua 脚本 | 4 | `budget_reserve` / `budget_settle` / `budget_release` / `budget_cleanup` |
| Flyway 迁移 | 5（改造前 3） | `V1` 建表、`V2` DEMO 种子、`V3` 供应商别名补价、`V4` 治理扩展、`V5` 治理种子 |
| 数据库业务表 | 13（改造前 5） | 见 `docs/database/TABLE_DICTIONARY.md` |
| 测试类 | 13 | 9 单测 + 4 集成测试（真实 MySQL / 真实 Redis） |
| 前端视图 | 6（改造前 6） | 本次扩展为 10 |

### 2.2 改造前已有能力（可直接复用）

1. **成本计量闭环**：`ChatResponse.getMetadata().getUsage()` 取真实 Usage → `CostCalculator` 三段计价（普通输入 / 缓存输入 / 输出）→ `ai_token_usage` 落库，带 `pricingVersion` 快照。
2. **价格版本管理**：`ai_model_pricing` 按 `effective_from / effective_to` 取生效版本，改价不影响历史账单。
3. **Redis 原子预算**：单个 Lua 内校验多作用域再一次扣减，金额以 1e-8 USD 整数存储，规避 Lua 浮点误差。
4. **最终一致策略**：预扣 → 调用 → 幂等结算 → 幂等落库 → 定时对账（`BudgetReconciler`）。
5. **统一响应与异常**：`ApiResponse{code,message,data,traceId}` + `GlobalExceptionHandler`。
6. **前端组件库**：`DataTable / ChartCard / StatCard / AsyncState / FilterBar / Modal / ProgressBar / TimeRangePicker`，含加载态、错误态（带 traceId）、空态。

### 2.3 改造前技术债（已确认、本次一并处理）

| 编号 | 问题 | 影响 | 处理 |
|---|---|---|---|
| T1 | `CostAlertService.alert()` 只按 `type|target|level` 去重 | 50% 与 70% 两条保护规则 `protection_level` 都是 2，会被合并成一条，丢档位 | 增加判别位参数（第 9 位），旧指纹不变、历史告警不重复 |
| T2 | 保护引擎 `currentLevel()` 把自身写入的 `PROTECTION_ACTION` 告警当作风险输入 | 管理员 resume 后等级卡在 SEVERE，强制降级 + 禁用 Agent | 等级判定排除自身类型 + resume 时一并恢复该告警（ADR-002） |
| T3 | `Views.BudgetStatus.usageRate` 被 `Math.min(rate, 1d)` 截断 | 无法区分 100% 与 150%，预警文案会失真 | 阈值预警自行按 `used+reserved / limit` 计算真实比率，不依赖截断值 |
| T4 | 预算使用率不把「未结算预扣」计入 | 「已花完但还没结算」的时间窗会漏报 | 阈值预警统一用 `used + reserved` 判断越线 |

---

## 3. 原 RAG 能力保留情况

| 类 | 处置 | 说明 |
|---|---|---|
| `DemoApplication` | 保留 + 扩展 | `scanBasePackages={"com.example"}`、`@MapperScan` 显式列包、`@EnableScheduling`、`exclude = MilvusVectorStoreAutoConfiguration` |
| `LocalHashingEmbeddingModel` | 保留，未改动 | 本地哈希向量，不依赖外部服务 |
| `DocumentIngestionService` | 保留 + 加 `@ConditionalOnProperty(rag.enabled)` | Tika 文档摄取 |
| `MilvusLearningService` | 保留 + 同上 | Milvus 读写 |
| `MilvusController` / `RagController` | 保留，未改动 | HTTP 入口 |

**没有删除任何一行 RAG 代码**。默认 `rag.enabled=false` 让成本平台不依赖 Milvus 即可启动；设置 `rag.enabled=true` 时 `RagConfiguration` 用 `@Import` 恢复全部 Bean。详见 `docs/architecture/ADR/ADR-001-rag-optional-assembly.md`。

---

## 4. 能力缺口清单（本次改造的靶子）

审计发现 V1.0 只解决「单次调用算了多少钱」，完全没有回答「钱花在哪、能不能少花、失控怎么拦」。逐项缺口：

| # | 缺口 | 缺失的具体能力 | 本次落地 |
|---|---|---|---|
| G1 | **成本核算不全** | 只有 Chat 模型成本；无 Embedding / Rerank / 图片 / 音频；无 RAG Pipeline 与 Agent 成本归因；无基础设施成本分摊 | 追加式账务 `ai_cost_ledger`（ACTUAL/ALLOCATED/SAVED 三口径）、`ai_infrastructure_cost` 按请求量分摊、`ai_agent_cost_trace` 任务级聚合 |
| G2 | **无成本优化** | 没有任何降低单位成本的机制 | `com.example.ai.optimize`：Prompt 优化、上下文压缩、RAG 片段优化、响应缓存、Embedding 去重、模型路由，收益按 MEASURED/COUNTERFACTUAL/ESTIMATED 分列 |
| G3 | **预算只有 5 级** | 缺 SCENE / KNOWLEDGE_BASE / AGENT / REQUEST 级预算，无法按业务维度设限 | 扩到 9 级；`applicableScopes()` 一次返回 7 个累计型作用域 + REQUEST 单次上限 |
| G4 | **无限流与并发控制** | 高并发可瞬间打穿预算 | `AIRequestProtectionService`：用户分钟/小时/天窗口 + 全局/模型并发槽位；限流 fail-open、预算 fail-closed |
| G5 | **告警不成体系** | 只有 `BUDGET_EXCEEDED` 等 4 类，无等级、无去重、无确认/恢复；`BUDGET_THRESHOLD` 与 `SPEND_VELOCITY` 枚举**已定义但无任何生产者** | `ai_cost_alert` 升级为告警中心（等级/状态/指纹/次数/确认/恢复）；补齐阈值预警、消费速度、6 条异常检测规则 |
| G6 | **无自动保护、Agent 无约束** | 成本失控时只能人工干预；Agent 可无限循环调用 | `CostProtectionEngine` 五级保护 + 紧急开关 + 审计日志；`AgentCostTracker` 六道闸门（步数/工具/Token/成本/时长/循环） |

---

## 5. 环境与端口审计（实测）

| 项 | 实测结果 | 规避方式 |
|---|---|---|
| 端口 8080 | 被**其他项目** `blogagent-server` 长期占用 | 本项目后端改用 **8091**；停止进程只用 `.run/*.pid` 或精确 `kill <pid>`，**禁止** `kill -9 $(lsof -ti tcp:8080)`、`pkill -f spring-boot:run` |
| 端口 5173 | 被其他项目占用 | 前端改用 **5174**（`--strictPort`） |
| 端口 3306 | 被 Docker 历史映射占用 | MySQL 容器映射到宿主 **13307** |
| Milvus / MinIO | MinIO 需要 9000，**已被其他项目占用**，Milvus 无法启动 | `@SpringBootApplication(exclude = MilvusVectorStoreAutoConfiguration.class)` + `rag.enabled=false`；RAG 代码零删除 |
| `HTTP_PROXY` | 沙箱代理会让 `curl localhost` 返回 502 | 所有验证命令加 `--noproxy '*'`；Java 启动加 `env -u HTTP_PROXY -u HTTPS_PROXY` |
| Maven 默认 JDK | Homebrew 升级后 `mvn` 默认跑在 **JDK 26**，而 JDK 23+ 关闭隐式注解处理 → Lombok 静默失效 → 大面积「找不到符号 getXxx()」 | 全流程固定 `JAVA_HOME=.../corretto-17.0.14/Contents/Home`；`start.command` 已内置该 JDK 探测 |
| MySQL 客户端字符集 | 容器内 `mysql` CLI 默认非 utf8mb4，中文审计日志显示为 `?`（**数据本身正确**） | 查询时加 `--default-character-set=utf8mb4` |

---

## 6. 开发约束（本次改造的硬边界）

1. **先审计再改**：本文件即审计产出，所有改动都能对应到第 4 节的某个缺口。
2. **保留 Milvus / RAG 能力**：见第 3 节，一行未删。
3. **不无依据升级**：Spring Boot 3.4.5 / Spring AI 1.0.0 GA 保持不动。
4. **不整项目重写**：以新增包 + 扩展既有类为主，`AiChatService` 是唯一被整体重写的类（从 8 步扩到 15 步），因链路本身必须重排。
5. **不删现有功能**：旧 `/api/admin/ai/**` 全部保留，新治理接口走 `/api/admin/cost/**` 等新路径并行提供。
6. **不破坏其他项目**：见第 5 节端口策略。
7. **不硬编码 Key**：只用环境变量 `AI_API_KEY` / `DEEPSEEK_API_KEY`。
8. **不造假数据**：无 Usage 记 `UNAVAILABLE`，无价格记 `UNPRICED`，估算记 `ESTIMATED`，分摊记 `ALLOCATED`，节省记 `SAVED`，四者互不冒充。

---

## 7. 审计发现的「假实现」风险点（自查清单）

以下是同类项目最容易糊弄的地方，本次逐条核对并给出结论：

| 风险点 | 改造前状态 | 结论 |
|---|---|---|
| 用字符串长度冒充 Token | 未出现，走 `Usage.getPromptTokens()` | 通过 |
| 估算成本标成实际成本 | 未出现，`usageStatus` 区分 REAL/ESTIMATED/MOCK | 通过 |
| 基础设施分摊算进 API 成本 | 改造前无分摊能力 | 新增后强制 `costKind=ALLOCATED` 单列，**不相加进 apiCost** |
| 用 Java `if` 代替原子预算 | 未出现，走 Lua | 通过 |
| Agent 无限调用模型 | 改造前无 Agent 链路 | 新增六道闸门，任一触顶即停止并写 `stopReason` |
| 无限重试模型请求 | 改造前无重试 | 新增只重试 `ConnectException`（明确未送达）；超时/读失败不重试（可能已计费） |
| 预算超限后继续付费调用 | 未出现 | 通过，且新增保护等级 5 再次拦截 |
| 告警枚举定义了但没生产者 | `BUDGET_THRESHOLD` / `SPEND_VELOCITY` **确认无生产者** | 本次补齐，见 `BudgetThresholdAlertService` 与 `SpendForecastService.checkVelocity()` |

---

## 8. 审计产出清单

| 产出 | 路径 |
|---|---|
| 本审计报告 | `docs/audit/PROJECT_AUDIT.md` |
| 分层架构（含治理模块） | `docs/architecture/AI_COST_ARCHITECTURE.md` |
| 预算控制设计 | `docs/architecture/BUDGET_CONTROL_DESIGN.md` |
| Usage 设计 | `docs/architecture/TOKEN_USAGE_DESIGN.md` |
| 架构决策记录 | `docs/architecture/ADR/ADR-001`、`ADR-002`、`ADR-003` |
| 数据库设计 | `docs/database/DATABASE_DESIGN.md`、`TABLE_DICTIONARY.md` |
| API 契约 | `docs/api/AI_COST_API.md` |
| 部署说明 | `docs/deployment/LOCAL_DEPLOYMENT.md` |
| 测试与验收 | `docs/acceptance/TEST_REPORT.md`、`FINAL_ACCEPTANCE_REPORT.md` |
