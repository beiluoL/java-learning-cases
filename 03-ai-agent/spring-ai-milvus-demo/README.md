# 企业级大模型成本控制与治理平台

一个可本地完整跑通的 **LLM 成本治理平台**：把「钱花在哪 → 找浪费 → 压下来 → 设限 → 拦住 → 复盘」做成闭环，
同时保留原有的 **Spring AI + Milvus RAG** 学习示例（代码一行未删，按开关装配）。

> 技术栈：Java 17 / Spring Boot 3.4.5 / MyBatis / MySQL 8 / Redis 8 / Flyway / Vue3 + TS + Vite / Spring AI 1.0.0 GA / Milvus 2.5.27

---

## 0. 平台概览

| 项 | 值 |
|---|---|
| 后端端口 | **8091**（8080 属于工作区其他项目，脚本不会 kill 它） |
| 前端 | `frontend/`（Vue3 + TS + Vite，dev 端口 **5174**，代理 `/api` → 8091），**10 个页面 / 3 个导航组** |
| 数据库 | MySQL 8（Docker，宿主 **13307**），Flyway 管理 V1~V5 迁移，**13 张业务表** |
| 预算控制 | Redis 8（本机 6379），4 个 Lua 脚本做原子多作用域预扣 |
| 模型 | 默认 `AI_PROVIDER=mock`（无 Key 可启动）；`openai` 走 OpenAI 兼容接口（含 DeepSeek，已实测） |
| 管理接口 | `/api/admin/**`，本机免令牌；配置 `AI_ADMIN_TOKEN` 后需 `X-Admin-Token` |
| 测试 | **72 项**：48 单测 + 24 集成（真实 Redis / 真实 MySQL） |
| 核心文档 | `docs/audit/PROJECT_AUDIT.md`、`docs/architecture/AI_COST_ARCHITECTURE.md`、`docs/api/AI_COST_API.md`、`docs/acceptance/FINAL_ACCEPTANCE_REPORT.md` |

### 0.1 八大能力

| # | 能力 | 关键实现 | 前端页面 |
|---|---|---|---|
| 1 | **成本核算** | 三段计费（input/output/cached）、`CostKind` 三口径（`ACTUAL`/`ALLOCATED`/`SAVED`）、追加式账本 | 成本构成 |
| 2 | **成本优化** | 8 条策略（Prompt/上下文压缩、RAG 上下文限制、模型路由、响应缓存、Embedding 去重、输出上限、Prompt 缓存），收益分 `MEASURED`/`COUNTERFACTUAL`/`ESTIMATED` 三口径 | 优化中心 |
| 3 | **预算管理（九级）** | `GLOBAL`/`USER`/`MODEL`/`DAILY`/`MONTHLY`/`SCENE`/`KNOWLEDGE_BASE`/`AGENT`/`REQUEST` | 预算管理 |
| 4 | **预算执行** | Lua 原子预扣（1e-8 整数运算）、幂等、过期回收、100 并发不超支 | — |
| 5 | **成本预警** | 19 种告警类型、**五档阈值 50/70/85/95/100%**、消费速度、6 条异常检测、去重/确认/自动恢复 | 预警中心 |
| 6 | **自动保护** | 五级（`NORMAL`/`WARNING`/`LIMIT`/`SEVERE`/`BLOCKED`）、5 条种子规则、模型降档、紧急开关 + 审计 | 系统保护策略 |
| 7 | **成本分析** | 构成拆解、用户/模型排行、月度预测、日耗尽 ETA、五维度日汇总、分页与跨度限制 | 成本总览 / Token 分析 / 模型成本 |
| 8 | **Dashboard** | 3 组导航 10 页，全部走真实接口，无写死统计值 | 请求追踪（运维） |

### 0.2 一轮请求的完整链路

```
保护等级判定 → 模型路由(L1/L2/L3) → 限流/并发闸门 → 响应缓存
   → 单次上限 → 预算预扣(Redis Lua) → Agent 闸门 → RAG 检索
   → Prompt/上下文压缩 → 模型调用 → Usage 采集 → 结算(MySQL)
   → 优化收益回填 → 成本账本 → 日汇总 → 告警/阈值评估
```

### 0.3 五档阈值与五级保护

| 使用率 | 告警等级 | 保护动作 | 保护等级 |
|---|---|---|---|
| 50% | WARNING | NOTIFY | — |
| 70% | WARNING | NOTIFY | — |
| 85% | HIGH | DOWNGRADE_MODEL | 3 LIMIT |
| 95% | CRITICAL | DISABLE_AGENT | 4 SEVERE |
| 100% | BLOCKED | BLOCK_REQUEST | 5 BLOCKED |

> 实测记录（真实 Redis 占用 + 真实 MySQL 额度调整）：80% → 50%+70%；96.97% → 85%+95%；106.67% → 100%；
> 连续 3 次评估不重复建行；超限后 `/api/ai/chat` 返回 `40302`；恢复后 5 档全部 `RESOLVED` 且请求放行。
> 详见 `docs/acceptance/TEST_REPORT.md` 第 6、7 节。

### 0.4 RAG 仍是可选装配

`spring-ai-starter-vector-store-milvus` 会在启动期强制连 Milvus，因此默认 `rag.enabled=false`，
成本平台可独立启动；设 `RAG_ENABLED=true` 并先起 Milvus，`RagConfiguration` 会恢复原有 4 个 RAG Bean
与 `/api/milvus/**`、`/api/rag/**` 端点（**原代码一行未删**）。决策见 `docs/architecture/ADR/ADR-001-rag-optional-assembly.md`。

### 0.5 快速启动

```bash
# 1. 起 MySQL（仓库根 infra/docker/）
cd ../../infra/docker && docker compose -f ai-cost-compose.yml up -d
# 2. 回项目根，双击或执行一键脚本
./start.command          # macOS；Windows 双击 start.bat
# 3. 打开前端
open http://localhost:5174
```

一键脚本 `start/stop/restart` 的 `.command`（macOS）与 `.bat`（Windows）共 6 个：PID 文件精确关停，
端口占用只拒绝不清理，**不含 `pkill` / `kill -9` / 按端口杀进程**。详细部署与故障排查见
`docs/deployment/LOCAL_DEPLOYMENT.md`。

启动后建议先跑一遍自检（只读，34 个管理接口全部断言 `code=0`）：

```bash
bash samples/verify-admin-endpoints.sh
```

> ⚠️ 手动跑 Maven 必须先 `export JAVA_HOME=.../corretto-17.0.14/Contents/Home`：
> JDK 23+ 默认关闭注解处理，Lombok 会静默失效并报数百处「找不到符号 `getXxx()`」。

---

# 附录：原始 RAG 学习示例（代码保留，按开关装配）

> 以下 1~6 节是最初的「文本 → 向量 → Milvus → 相似度检索」学习教程，**代码全部保留**。
> 默认 `rag.enabled=false` 时这些端点不装配，需要时按第 2 节起 Milvus 并设 `RAG_ENABLED=true`。

## 1. 你能学到什么

| 概念 | 在本项目里的体现 |
|------|------------------|
| **Embedding（嵌入）** | `LocalHashingEmbeddingModel`：把文本变成 256 维向量 |
| **VectorStore（统一抽象）** | `MilvusVectorStore`（Spring AI 自动装配），业务代码只依赖 `VectorStore` 接口 |
| **Milvus 集合** | `java_learning_docs`（类似关系库的“表”） |
| **相似度检索** | `vectorStore.similaritySearch(SearchRequest)` → 余弦最近邻 |

> 💡 关键点：**业务代码只认 `VectorStore` 接口**。今天底层是 Milvus，明天换成 PGVector，
> 只要换一个 starter 依赖，业务代码一行都不用改——这就是 Spring AI 抽象的价值。

---

## 2. 安装并启动 Milvus

Milvus 以 Docker 方式运行（官方 standalone 版，包含 etcd + MinIO + Milvus 三个容器）。

```bash
cd ../../infra/docker
docker compose -f milvus-standalone-docker-compose.yml up -d
```

等待健康检查变绿（约 1~2 分钟）：

```bash
docker compose -f milvus-standalone-docker-compose.yml ps
# 看到 milvus-standalone 状态为 healthy 即可
```

端口说明（**重要，很多人会踩这个坑**）：

| 端口 | 协议 | 能不能用浏览器打开 | 说明 |
|------|------|--------------------|------|
| `19530` | **gRPC** | ❌ 会 404 | Milvus 服务端口，Java/Python SDK 连这个。**不是网页**，浏览器直接访问会 `404 page not found` |
| `9091` | HTTP | ⚠️ 仅 `/healthz` | 健康检查/`metrics` 接口，返回 `OK`，但也不是页面 |
| `9530` | HTTP | ✅ **Attu 可视化页面** | 装了 Attu 后，浏览器开 `http://127.0.0.1:9530` 才是能“看到”Milvus 的网页 |

> 💡 **结论：Milvus 本身没有网页界面。** 想用浏览器看集合/向量/检索结果，要装官方可视化工具 **Attu**（见下方）。`19530` 那个 404 不是故障，是协议不对（它是 gRPC 端口）。
> 更完整的 Attu 图文教程见 **`03-ai-agent/milvus-java-minimal-demo/Attu使用说明.md`**（目录重组后的实际位置）。

### 2.1 安装 Attu（Milvus 网页可视化）

Attu 是官方 GUI，能直接看到集合、向量、检索结果。它和 Milvus 在同一个 Docker 网络里即可：

```bash
# 方式一：docker run（后端连 milvus-standalone:19530，浏览器走 9530）
docker run -d --name attu -p 9530:3000 \
  --network milvus \
  -e MILVUS_URL=milvus-standalone:19530 \
  zilliz/attu:v2.5.11

# 方式二：docker-compose（文件已提供：infra/docker/attu-compose.yml）
docker compose -f infra/docker/attu-compose.yml up -d
```

启动后浏览器访问 **http://127.0.0.1:9530** → 直接能连上（已通过 `MILVUS_URL` 配好）。
里面可以看到本项目建的 `java_learning_docs` 集合、已写入的文档和向量。

停止 Attu：`docker stop attu && docker rm attu`

停止 / 清理：

```bash
docker compose -f milvus-standalone-docker-compose.yml down          # 停容器（数据保留在卷里）
docker compose -f milvus-standalone-docker-compose.yml down -v       # 连数据卷一起删
```

---

## 3. 运行本示例

```bash
cd 03-ai-agent/spring-ai-milvus-demo
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
RAG_ENABLED=true mvn spring-boot:run
```

`RAG_ENABLED=true` 是必须的（默认 `false` 时这些端点不装配）。应用启动时会自动在 Milvus 中创建集合 `java_learning_docs`。

### 3.1 写入示例文档

```bash
curl -X POST http://localhost:8091/api/milvus/seed
# => 已写入 5 条文档到集合 java_learning_docs
```

### 3.2 相似度检索（核心演示）

```bash
curl "http://localhost:8091/api/milvus/search?q=向量数据库怎么用&topK=3"
```

返回结果会按“与查询语义最接近”排序，例如会优先返回包含
「Milvus 是开源的向量数据库…」「向量检索的核心是计算余弦相似度…」的文档。

### 3.3 直观看「文本 → 向量」

```bash
curl "http://localhost:8091/api/milvus/embed?text=Java面向对象编程"
# => {"dimension":256,"first10":[...],"norm":1.0}
```

`norm` 恒为 1.0（做了 L2 归一化），意味着可以用“向量点积”直接等价于“余弦相似度”。

### 3.4 把 Tika 接进 RAG：文档摄取（扩展模块）

本工程额外提供了一个 **RAG 文档摄取** 示例：用 Apache Tika 把 PDF/Word/HTML/MD/TXT 等任意格式文件，
解析成纯文本 + 元数据，分块后写入同一个 Milvus 集合，完成“可检索知识库”的入库环节。

```bash
# 用项目自带的样例文件试（samples/ 下有 txt/html/md 三个样例）
curl -F file=@samples/spring-boot-intro.txt http://localhost:8091/api/rag/ingest
curl -F file=@samples/milvus-guide.html   http://localhost:8091/api/rag/ingest
curl -F file=@samples/rag-notes.md        http://localhost:8091/api/rag/ingest

# 召回 / 拼装上下文（喂给 LLM 用）
curl "http://localhost:8091/api/rag/search?q=Milvus%20向量索引&topK=3"
curl "http://localhost:8091/api/rag/context?q=RAG%20怎么减少幻觉&topK=3"
```

端点：`POST /api/rag/ingest`（上传入库）、`POST /api/rag/detect`（仅检测类型）、
`GET /api/rag/search`（召回 chunk）、`GET /api/rag/context`（拼 LLM 上下文）。
**完整讲解、代码说明与排错见 `Tika-RAG-集成示例.md`**（与本 README 同目录）。

> 💡 摄取进 Milvus 的 chunk 会自动带上 `pipeline=tika-ingest`、`source`（文件名）、`contentType`（MIME）、
> `tika.*`（标题/作者等）元数据，在 Attu 里能直接看到、用于区分来源。

---

## 4. 项目结构

```
spring-ai-milvus-demo
├── pom.xml                              # web + spring-ai-milvus + tika + mybatis + flyway + actuator
├── start/stop/restart.command|.bat      # 6 个一键脚本（PID 精确关停）
├── samples/                             # RAG 上传样例 + verify-alert-*.sh 演练脚本
├── src/main/resources
│   ├── application.yml                  # 端口/MySQL/Redis/Flyway/ai.* 全量配置
│   ├── db/migration/V1~V5*.sql          # 13 张表 + 种子数据
│   ├── lua/budget_{reserve,settle,release,cleanup}.lua
│   └── mapper/*.xml                     # 13 个 Mapper XML
├── frontend/                            # Vue3 + TS + Vite，10 个页面
│   └── src/{views,api,router,components,constants.ts}
├── docs/                                # 审计 / 架构 / API / 数据库 / 部署 / 验收
└── src/main/java/com/example
    ├── ai/                              # 【治理平台】93 个文件
    │   ├── common/                      # ApiResponse / AiEnums / MoneyScale / Views / PageResult
    │   ├── config/                      # AiProperties / AiModelConfig / AdminAccessInterceptor / RagConfiguration
    │   ├── chat/                        # AiChatService（16 步链路主干）
    │   ├── optimize/                     # ModelRouter / PromptOptimizer / ContextCompressor /
    │   │                                #   RagContextOptimizer / ResponseCacheService /
    │   │                                #   EmbeddingCacheService / TokenEstimator
    │   ├── guard/                       # AIRequestProtectionService（限流/并发/熔断）
    │   ├── token/usage/                 # TokenUsageCollector + ProviderUsageAdapter
    │   ├── token/pricing/               # ModelPricingService
    │   ├── token/budget/                # TokenBudgetService / BudgetReconciler / BudgetSettledEvent
    │   ├── token/cost/                  # CostCalculator / CostLedgerService / CostAlertService /
    │   │                                #   BudgetThresholdAlertService / AnomalyDetectionService /
    │   │                                #   SpendForecastService / CostProtectionEngine
    │   ├── token/analytics/             # CostAnalyticsService / CostGovernanceService / DailySummaryService
    │   ├── token/agent/                 # AgentCostTracker（步数/工具/成本三重上限）
    │   ├── token/infra/                 # InfrastructureCostService（分摊，不入 apiCost）
    │   ├── identity/                    # UserIdentityProvider + Demo 实现
    │   └── metrics/                     # AiMetrics
    └── milvusdemo/                      # 【原 RAG 示例】6 个文件，一行未删
        ├── DemoApplication.java         # 启动类
        ├── embed/LocalHashingEmbeddingModel.java
        ├── service/{MilvusLearningService,DocumentIngestionService}.java
        └── web/{MilvusController,RagController}.java
```

---

## 5. （进阶）换成“真实”嵌入模型（OpenAI）

本项目为了零依赖，用了一个本地哈希嵌入。真实场景请用大模型生成嵌入，例如 OpenAI：

1. 在 `pom.xml` 增加依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

2. 在 `application.yml` 增加：

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}            # 你的真实 Key
      embedding:
        options:
          model: text-embedding-3-small     # 1536 维
    vectorstore:
      milvus:
        embeddingDimension: 1536             # 必须与模型输出维度一致！
```

3. 把 `LocalHashingEmbeddingModel` 这个 Bean 移除（或加 `@Profile("local")`），
   让 Spring AI 自动装配的 `OpenAiEmbeddingModel` 生效即可。其余业务代码无需改动。

> 小贴士：不想用 OpenAI，也可以换 **Ollama**（本地模型，`spring-ai-starter-model-ollama`），
> 同样只需改依赖和配置，业务代码不动。

---

## 6. 常见坑

- **维度不一致**：`application.yml` 里的 `embeddingDimension` 必须等于 `EmbeddingModel` 输出维度，
  否则启动或插入时会报维度不匹配。
- **Milvus 没起来**：成本平台默认 `rag.enabled=false`，**不连 Milvus 也能启动**；只有显式 `RAG_ENABLED=true` 恢复 RAG 时才需要先确认 `docker compose ps` 中 Milvus 为 healthy。
- **集合已存在**：重复 `seed` 是“追加”而非“覆盖”，多次写入会有重复文档。可在 Milvus 里 drop 集合后重来。

---

## 7. 平台侧常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `mvn compile` 报数百处「找不到符号 `getXxx()`」 | JDK 23+ 默认关闭注解处理，Lombok 静默失效 | `export JAVA_HOME=.../corretto-17.0.14/Contents/Home` |
| 调用返回 `40301 预算不足` | 日预算耗尽 | 调高 `/api/admin/budget` 额度，或等跨天分键重置；被拒账单也会落库 |
| 调用返回 `40302 保护等级 5` | 使用率 >100% 或紧急开关停用 | 查 `/api/admin/cost-protection/status`；紧急开关用 `/cost-protection/resume` 恢复 |
| 调用返回 `50301` | Redis 不可用，预算安全失败（**预期保护行为，不是放行**） | `redis-cli ping` 恢复后重试 |
| 同一个阈值反复告警 / 五档被合并成一条 | 告警指纹缺判别位 | 已修：指纹含 `discriminator`（`"50.00%"` 形式），见 `docs/api/AI_COST_API.md` |
| 紧急开关恢复后等级卡在 `SEVERE` | 旧版本保护自锁缺陷 | 已修（`ADR-002`），`CostProtectionEngineTest` 4 例值守 |
| `curl localhost` 连不上 | 本机 `HTTP_PROXY` | 加 `--noproxy '*'` |
| 容器内 mysql 中文变 `?????` | 客户端字符集 | 加 `--default-character-set=utf8mb4`；表列确为 `utf8mb4` |
| 页面数据为空但接口有数据 | 前端代理未生效 | 确认 Vite `strictPort` 在 5174、`/api` 代理指向 8091 |

更多排查见 `docs/deployment/LOCAL_DEPLOYMENT.md` 第 11 节。
