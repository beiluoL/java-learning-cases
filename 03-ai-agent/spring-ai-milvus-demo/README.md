# Spring AI + Milvus 学习示例

一个最小的、可零依赖运行的 **Spring Boot + Spring AI + Milvus** 案例，用来理解
「文本 → 向量 → 存入向量数据库 → 相似度检索（RAG 的核心）」这条链路。

> 技术栈：Java 17 / Spring Boot 3.4.5 / Spring AI 1.0.0 GA / Milvus 2.5.27（Docker）

---

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
cd docker
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
> 更完整的 Attu 图文教程见仓库根目录 **`Attu使用说明.md`**。

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
cd spring-ai-milvus-demo
mvn spring-boot:run
```

应用启动时会自动在 Milvus 中创建集合 `java_learning_docs`。

### 3.1 写入示例文档

```bash
curl -X POST http://localhost:8080/api/milvus/seed
# => 已写入 5 条文档到集合 java_learning_docs
```

### 3.2 相似度检索（核心演示）

```bash
curl "http://localhost:8080/api/milvus/search?q=向量数据库怎么用&topK=3"
```

返回结果会按“与查询语义最接近”排序，例如会优先返回包含
「Milvus 是开源的向量数据库…」「向量检索的核心是计算余弦相似度…」的文档。

### 3.3 直观看「文本 → 向量」

```bash
curl "http://localhost:8080/api/milvus/embed?text=Java面向对象编程"
# => {"dimension":256,"first10":[...],"norm":1.0}
```

`norm` 恒为 1.0（做了 L2 归一化），意味着可以用“向量点积”直接等价于“余弦相似度”。

### 3.4 把 Tika 接进 RAG：文档摄取（扩展模块）

本工程额外提供了一个 **RAG 文档摄取** 示例：用 Apache Tika 把 PDF/Word/HTML/MD/TXT 等任意格式文件，
解析成纯文本 + 元数据，分块后写入同一个 Milvus 集合，完成“可检索知识库”的入库环节。

```bash
# 用项目自带的样例文件试（samples/ 下有 txt/html/md 三个样例）
curl -F file=@samples/spring-boot-intro.txt http://localhost:8080/api/rag/ingest
curl -F file=@samples/milvus-guide.html   http://localhost:8080/api/rag/ingest
curl -F file=@samples/rag-notes.md        http://localhost:8080/api/rag/ingest

# 召回 / 拼装上下文（喂给 LLM 用）
curl "http://localhost:8080/api/rag/search?q=Milvus%20向量索引&topK=3"
curl "http://localhost:8080/api/rag/context?q=RAG%20怎么减少幻觉&topK=3"
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
├── pom.xml                     # 依赖：web + spring-ai-milvus + tika-core + tika-parsers-standard-package
├── src/main/resources/application.yml   # 连接 Milvus + 集合/维度/索引 + 上传大小限制
├── samples/                    # 供 /api/rag/ingest 上传演示的样例文件（txt/html/md）
├── Tika-RAG-集成示例.md        # 【RAG】Tika 接入 RAG 的完整图文教程
└── src/main/java/com/example/milvusdemo
    ├── DemoApplication.java            # 启动类（自动装配 MilvusVectorStore）
    ├── embed
    │   └── LocalHashingEmbeddingModel.java   # 本地“教学用”EmbeddingModel（无需 API Key）
    ├── service
    │   ├── MilvusLearningService.java         # 写入文档 / 相似度检索 / 向量化演示
    │   └── DocumentIngestionService.java      # 【RAG】Tika 解析 + 分块 + 入库 + 召回
    └── web
        ├── MilvusController.java              # Milvus 基础演示 REST 接口
        └── RagController.java                 # 【RAG】摄取/检测/检索/上下文接口
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
- **Milvus 没起来**：应用启动会连 Milvus；若 Milvus 没 healthy，会启动失败——先确认 `docker compose ps` 状态。
- **集合已存在**：重复 `seed` 是“追加”而非“覆盖”，多次写入会有重复文档。可在 Milvus 里 drop 集合后重来。
