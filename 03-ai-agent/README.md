# 03-ai-agent · AI / RAG / Agent

> **这一组放什么**：把 LLM 接进 Java 后端的所有形态 —— 从"会操作向量库"到"完整的 RAG 问答产品"，再到"有工具调用链路的客服 Agent"。
>
> **为什么单独成组**：AI 应用的共同底座是**向量检索 + 上下文拼装**，差别只在工程完整度。单独成组可以把 Milvus、Spring AI、Tika 这些共用件看清楚，而不是散落在业务工程里。

---

## 组内工程

| 工程 | 定位 | 技术栈 | 前端 | 启动方式 |
|---|---|---|---|---|
| [milvus-java-minimal-demo](./milvus-java-minimal-demo) | 向量库「Hello World」：连接 Milvus、建集合、增删查向量 | Java · Milvus Java SDK | 否 | `./start.command` / `start.bat`（需先起 Milvus） |
| [spring-ai-milvus-demo](./spring-ai-milvus-demo) | 最小 RAG 链路：文本 → 向量 → 入库 → 相似度检索。业务只依赖 `VectorStore` 接口，换库不改代码；含自定义 `LocalHashingEmbeddingModel`（256 维）演示 Embedding 原理 | Spring Boot 3.4 · Spring AI 1.0 · Milvus 2.5 | 否 | `./start.command`（需先起 Milvus） |
| [rag-doc-qa-system](./rag-doc-qa-system) | 完整 RAG 文档问答产品：上传 DOC/DOCX/PDF/TXT/MD → Tika 解析切片 → 向量化 → 自然语言问答 | Spring Boot 3.4.5 · Spring AI · Milvus · Tika · Vue 3 / AntD / Pinia | 是 | `./start.command` / `start.bat` |
| [ai-knowledge-base-all](./ai-knowledge-base-all) | AI 知识库（合并式目录，当前仅后端 `ai-knowledge-base` 落地，前端待补） | Spring Boot 3.4.1 · Spring AI | 否 | `cd ai-knowledge-base-all/ai-knowledge-base && mvn spring-boot:run`（:8080） |
| [customer-service-agent](./customer-service-agent) | 智能客服 Agent：`backend`（Spring Boot）+ `frontend`（Vue）+ `agent-bridge`（Node）三端协作，含 FAQ 匹配、意图识别、满意度、会话管理 | Spring Boot 3.2.5 · Vue 3 · Node bridge · H2 | 是 | `./start.command`（后端 :8080 / 前端 :5173） |

---

## 建议阅读顺序

```text
milvus-java-minimal-demo    ← 先会用向量库：连接、集合、向量增删查
        ↓
spring-ai-milvus-demo       ← 再看 Spring AI 把它包成 VectorStore，业务与实现解耦
        ↓
rag-doc-qa-system           ← 完整的 RAG 产品：解析、切片、检索、生成、前端
        ↓
customer-service-agent      ← 最后做 Agent：多端协作 + 工具调用 + 会话状态
```

---

## 共用基础设施：Milvus

`milvus-java-minimal-demo`、`spring-ai-milvus-demo`、`rag-doc-qa-system` 都要连 Milvus。用仓库根部的公共编排启动，**不要各工程自己再起一份**：

```bash
cd ../infra/docker
docker compose -f milvus-standalone-docker-compose.yml up -d
docker compose -f milvus-standalone-docker-compose.yml ps    # 等 milvus-standalone 变 healthy
docker compose -f attu-compose.yml up -d                     # 可选：Attu GUI，http://127.0.0.1:9530
```

- Milvus 端口 `19530`（gRPC）/ `9091`（metrics）；默认账号 `root` / `milvus`（教学用途）。
- 详见 [`infra/docker/`](../infra/docker) 与 `milvus-java-minimal-demo` 内的《Attu 使用说明》。

---

## 注意事项

- **密钥走环境变量**：`SILICONFLOW_API_KEY`（Embedding）、`DEEPSEEK_API_KEY`（对话生成）。代码里只允许出现环境变量名。
- `customer-service-agent` 本地跑会生成 H2 运行库 `data/csagent.mv.db`，属于运行数据，**已被忽略、不入库**。
- 前端工程（`rag-doc-qa-system`、`customer-service-agent`）的 `node_modules` 体积大（合计约 2.8G），不入库，克隆后 `npm install` 重装。
- 模型可切换：`rag-doc-qa-system` 默认 SiliconFlow，可切 DeepSeek / OpenAI（OpenAI 兼容接口）。

返回 [仓库总览](../README.md)
