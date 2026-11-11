# ADR-001：RAG / Milvus 改为可选装配

> 状态：已接受（对应 V1.0 实现）
> 记录日期：2026-11-11（V1.0 文档整理）
>
> 本文件对应代码路径：
> - `src/main/java/com/example/milvusdemo/DemoApplication.java`（`exclude = MilvusVectorStoreAutoConfiguration.class`）
> - `src/main/java/com/example/ai/config/RagConfiguration.java`（`@Import` 恢复）
> - 4 个 RAG Bean 的条件注解：`src/main/java/com/example/milvusdemo/service/MilvusLearningService.java`、`DocumentIngestionService.java`、`web/MilvusController.java`、`web/RagController.java`
> - 开关配置：`src/main/resources/application.yml`（`rag.enabled`、`spring.ai.vectorstore.milvus.*`）
> - 降级逻辑：`src/main/java/com/example/ai/chat/service/AiChatService.java`（`retrieveContext()`）
> - Milvus 编排：`infra/docker/milvus-standalone-docker-compose.yml`（仓库根目录 `infra/docker/`）

## 背景

原工程 `spring-ai-milvus-demo` 是 Spring AI + Milvus + Tika 的 RAG 学习示例，引入 `spring-ai-starter-vector-store-milvus` 后，`MilvusVectorStoreAutoConfiguration` 会在**启动期强制连接 Milvus 并创建集合**（`collectionName=java_learning_docs`）。也就是说：只要 Milvus 未就绪，应用就无法启动。

V1.0 把它升级为「企业级 AI Token 成本管理平台」，新增目标带来两个约束：

1. 成本平台（身份 → 预算 → Usage → 成本 → 账单 → 统计）必须能**独立于向量库启动与验收**，其核心依赖只有 MySQL 与 Redis。
2. 本机 9000 / 9001 端口已被其它项目的 MinIO 占用，Milvus standalone 编排中的 MinIO 恰用这两个端口，起 Milvus 可能需要调整端口，进一步增加启动成本。

同时项目约定「原有 RAG / Milvus / Tika 代码不删除、不破坏」，不能简单移除 starter 依赖或删掉 RAG 类。

## 决策

采用「默认排除 + 条件恢复」的可选装配：

1. 启动类默认排除自动配置：
   `@SpringBootApplication(scanBasePackages = {"com.example"}, exclude = MilvusVectorStoreAutoConfiguration.class)`。
2. `RagConfiguration` 带 `@ConditionalOnProperty(prefix="rag", name="enabled", havingValue="true")`，在开关打开时通过 `@Import(MilvusVectorStoreAutoConfiguration.class)` 显式恢复自动配置。
3. 原 4 个依赖向量库的 Bean（`MilvusLearningService`、`DocumentIngestionService`、`MilvusController`、`RagController`）各加同样的 `@ConditionalOnProperty`，默认不装配，开关打开时整体恢复。
4. `application.yml` 默认 `rag.enabled: ${RAG_ENABLED:false}`，`spring.ai.vectorstore.milvus.initialize-schema` 默认 `false`（由 `MILVUS_INIT_SCHEMA` 控制）。
5. 问答链路对 RAG 做降级：`AiChatService` 通过 `ObjectProvider<DocumentIngestionService>` 取服务，取不到或检索抛异常时只记日志、返回空上下文，**不阻断问答**（`useRag=true` 时业务场景仍记为 `RAG`）。

`scanBasePackages = {"com.example"}` 同时覆盖原包 `com.example.milvusdemo` 与新包 `com.example.ai`；`@MapperScan` 显式列出 5 个新 mapper 包；`@EnableScheduling` 开启预算对账任务。

## 备选方案

| 方案 | 说明 | 未采用的理由 |
|---|---|---|
| A. 保持原样，启动前必须先起 Milvus | 不动代码，按原 README 流程先 `docker compose up` Milvus | 成本平台被向量库绑架；本机 9000/9001 被其它项目 MinIO 占用，需改端口；无法做「无外部依赖」的独立验收与构建 |
| B. 硬编码 / 删除：移除 milvus starter 依赖与 RAG 代码 | 从 `pom.xml` 去掉依赖、删掉 4 个 RAG 类 | 直接破坏原学习项目，违背「不删除、不破坏」约束；日后要恢复需重写代码，且 `README.md` / `Tika-RAG-集成示例.md` 全部失效 |
| C. 拆两套启动类 / Spring Profile | 用 profile 分别装配 RAG 版与成本版 | 两套配置与 Bean 定义重复，维护成本高；`scanBasePackages`、Mapper、定时任务都要分别维护 |
| D. **exclude + @Import + 条件装配（选定）** | 默认排除，开关打开时恢复 | 改动集中在一处启动类、一个配置类、4 个注解；原代码一行未删；默认零 Milvus 依赖，恢复路径明确 |

## 代价

- 默认状态下 `/api/milvus/**`、`/api/rag/**` 端点不装配；`useRag=true` 的问答会降级为「无知识库回答」，调用方看到的是无检索上下文的结果。
- 多一个配置项 `rag.enabled` 与一份 ADR 需要维护；排查「RAG 端点 404」时需先确认开关状态。
- 开启 RAG 仍需先启动 Milvus（含 etcd / MinIO），且可能需调整 9000 / 9001 端口以避免与其它项目冲突。
- `README.md`（docs 目录之外）已由主开发者更新：新增 V1.0 平台章节、端口 / 启动说明与 RAG 可选装配说明，并修正「应用启动会连 Milvus」的旧描述；该不一致已消除，本 ADR 保留作为决策记录。

## 恢复方式（如何重新启用 RAG）

1. 在仓库根目录的 `infra/docker/` 启动 Milvus：
   `docker compose -f milvus-standalone-docker-compose.yml up -d`，等待健康检查变绿（Milvus gRPC 19530、健康 9091；MinIO 9000/9001 若被占用需先调整端口映射）。
2. 打开开关（二选一）：环境变量 `RAG_ENABLED=true`，或配置 `rag.enabled=true`。
3. 需要自动建集合时设 `MILVUS_INIT_SCHEMA=true`（默认 `false`）。
4. 启动应用：`RagConfiguration` 生效 → `@Import` 恢复 `MilvusVectorStoreAutoConfiguration` → 4 个 RAG Bean 装配 → `/api/milvus/**`、`/api/rag/**` 端点恢复，`DocumentIngestionService` 可用。
5. 回归原 RAG 示例：`POST /api/milvus/seed`、`GET /api/milvus/search`、`POST /api/rag/ingest`、`GET /api/rag/context`（见根目录 `README.md` 与 `Tika-RAG-集成示例.md`）。

## 后果与验收说明

- 成本平台在 `rag.enabled=false` 下可独立启动、独立验收，这是 V1.0 的默认运行形态。
- **本次验收在 `rag.enabled=false` 下进行，RAG 检索未做端到端验证**；仅验证了「RAG 未装配时问答链路可降级、不阻断」。恢复路径本身亦未在本次验收中实测。
- 该决策不改变成本计算、预算控制与账单口径；`useRag=true` 只影响 `business_scene` 记为 `RAG` 与 Prompt 是否附带检索上下文。
