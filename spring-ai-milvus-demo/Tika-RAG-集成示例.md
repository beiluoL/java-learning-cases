# Tika 接入 RAG 系统：最小示例工程

> 本示例是 `spring-ai-milvus-demo` 的**扩展模块**，演示如何用 Apache Tika 完成 RAG 的「文档摄取（Ingestion）」环节，
> 把任意格式文件变成可检索的向量块，存入前面已搭好的 Milvus。
>
> 配套文档：[Apache-Tika-使用说明.md](../Apache-Tika-使用说明.md)（Tika 本身的安装与命令）、[README.md](./README.md)（Milvus 与整体工程）。

---

## 1. 整体架构

```
                上传文件 (PDF/DOCX/HTML/MD/TXT …)
                          │
                          ▼
        ┌─────────────────────────────────────┐
        │   DocumentIngestionService (本模块)   │
        │   ① Tika 类型检测  (detect MIME)      │
        │   ② Tika 文本+元数据提取 (parseToString)│
        │   ③ 文本分块 (splitText, 带重叠)      │
        │   ④ VectorStore.add → 向量化 + 写 Milvus│
        └─────────────────────────────────────┘
                          │
                          ▼
                  Milvus (19530, 集合 java_learning_docs)
                          │
           用户提问 q ─────┤
                          ▼
        RagController.search/context → similaritySearch 召回 → 拼装上下文喂 LLM
```

要点：
- **Tika 负责“看懂文件”**：一份 PDF 和一个 TXT，对业务代码而言都是「InputStream」，Tika 统一解析成文本+元数据。
- **Spring AI 的 `VectorStore` 负责“存与查”**：底层是 Milvus 还是 PGVector，本模块代码完全不感知。
- **嵌入模型沿用 `LocalHashingEmbeddingModel`**（256 维本地哈希嵌入，零 API Key）。它足以演示整条链路；
  要做**语义级**检索，把它换成 OpenAI / Ollama / DeepSeek 等真实 EmbeddingModel 即可（见第 6 节）。

---

## 2. 依赖配置（已写入 pom.xml）

通过 `tika-bom` 统一版本（当前稳定版 **3.3.2**，需 Java 11+，本机 Java 17 满足）：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.apache.tika</groupId>
      <artifactId>tika-bom</artifactId>
      <version>3.3.2</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- 核心解析库 -->
  <dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
  </dependency>
  <!-- 常用解析器集合（PDF/Word/Excel/PPT/HTML/XML…），不含 OCR -->
  <dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
  </dependency>
</dependencies>
```

> ⚠️ 3.x 起解析器坐标从旧的 `tika-parsers` 改为 **`tika-parsers-standard-package`**，旧坐标在 3.x 已不存在。

另外在 `application.yml` 放宽上传上限（RAG 摄取 PDF/Word 常见几 MB）：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
```

---

## 3. 核心代码说明

### 3.1 摄取服务 `DocumentIngestionService`

四个步骤与架构图一一对应：

```java
// ① 类型检测
try (InputStream probe = file.getInputStream()) {
    detectedType = tika.detect(probe);          // 返回 MIME，如 text/plain、application/pdf
}
// ② 文本 + 元数据提取（共用一个 Metadata 对象）
Metadata metadata = new Metadata();
metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
try (InputStream in = file.getInputStream()) {
    fullText = tika.parseToString(in, metadata); // 解析后 fullText 是纯文本，metadata 被填满
}
// ③ 分块（递归字符切分，段间保留 overlap 重叠字符）
List<String> chunks = splitText(fullText, 400, 60);
// ④ 入库：VectorStore.add 内部调用 EmbeddingModel 把每个 chunk 向量化并写入 Milvus
for (int i = 0; i < chunks.size(); i++) {
    Map<String, Object> meta = new LinkedHashMap<>(baseMeta);
    meta.put("chunkIndex", i + 1);
    meta.put("chunkTotal", chunks.size());
    docs.add(new Document(chunks.get(i), meta));
}
vectorStore.add(docs);
```

元数据会同时写入 **Tika 自带字段**（加 `tika.` 前缀，如 `tika.title`、`tika.Author`）、以及业务标签
`source`（文件名）、`contentType`（MIME）、`pipeline=tika-ingest`。这些字段在 Attu 里可直接看到、用于区分来源。

### 3.2 检索 / 拼装上下文

```java
// RAG 召回：query -> 向量化 -> Milvus 近邻搜索
List<Document> hits = vectorStore.similaritySearch(
        SearchRequest.builder().query(query).topK(topK).build());

// RAG 拼装：把召回 chunk 拼成可喂给 LLM 的上下文
StringBuilder sb = new StringBuilder();
for (Document d : hits) {
    sb.append("[来源: ").append(d.getMetadata().get("source")).append("]\n")
      .append(d.getText()).append("\n\n");
}
return sb.toString();   // 这个字符串直接作为 system/user 上下文发给大模型即可
```

---

## 4. REST 端点（RagController）

| 方法 | 路径 | 说明 | 示例 |
|---|---|---|---|
| POST | `/api/rag/ingest` | 上传文件 → Tika 解析 → 切块 → 入库 | `curl -F file=@samples/rag-notes.md http://localhost:8080/api/rag/ingest` |
| POST | `/api/rag/detect` | 仅检测 MIME 类型，不入库 | `curl -F file=@samples/milvus-guide.html http://localhost:8080/api/rag/detect` |
| GET  | `/api/rag/search` | 召回最相关 chunk 列表 | `curl "http://localhost:8080/api/rag/search?q=Milvus%20索引&topK=3"` |
| GET  | `/api/rag/context` | 把召回结果拼成 LLM 上下文 | `curl "http://localhost:8080/api/rag/context?q=RAG%20为什么减少幻觉&topK=3"` |

---

## 5. 端到端跑通（前提：Milvus 已起）

```bash
# 0) 启动 Milvus + Attu（见 README 第 2 节），浏览器开 http://127.0.0.1:9530 可看数据
cd /Users/beiluo/Documents/alProject/javalearn/docker
docker compose -f milvus-standalone-docker-compose.yml up -d
docker run -d --name attu -p 9530:3000 --network milvus \
  -e MILVUS_URL=milvus-standalone:19530 zilliz/attu:v2.5.11

# 1) 启动本工程
cd /Users/beiluo/Documents/alProject/javalearn/spring-ai-milvus-demo
mvn spring-boot:run
# 浏览器开 http://localhost:8080

# 2) 摄取样例文档（项目自带 samples/ 目录下有 txt/html/md 三个样例）
curl -F file=@samples/spring-boot-intro.txt http://localhost:8080/api/rag/ingest
curl -F file=@samples/milvus-guide.html   http://localhost:8080/api/rag/ingest
curl -F file=@samples/rag-notes.md        http://localhost:8080/api/rag/ingest

# 3) 检索（验证 Tika 摄取真的进了 Milvus）
curl "http://localhost:8080/api/rag/search?q=Milvus%20向量索引&topK=3"

# 4) 拼装给 LLM 的上下文
curl "http://localhost:8080/api/rag/context?q=RAG%20怎么减少幻觉&topK=3"
```

完成后打开 Attu（http://127.0.0.1:9530），进入 `java_learning_docs` 集合，
能看到带 `pipeline=tika-ingest` 元数据的一批 chunk，以及它们各自的 `source`、`contentType`、`tika.*` 字段。

---

## 6. 进阶：换成“语义级”嵌入

`LocalHashingEmbeddingModel` 是按词频哈希的**确定性非语义**向量——它演示了链路，但召回更接近“关键词重叠”。
要获得真正的语义召回，把嵌入模型换成真实大模型即可（业务代码无需改动，因为 `VectorStore` 只依赖 `EmbeddingModel` 接口）：

```java
// 例：用 OpenAI（需 spring-ai-starter-model-openai + 配置 api-key）
@Bean
public EmbeddingModel embeddingModel() {
    return new OpenAiEmbeddingModel(OpenAiApi.builder()
            .apiKey(System.getenv("OPENAI_API_KEY")).build());
}
```

注意：换真实嵌入后，向量维度会变（OpenAI text-embedding-3-small 是 1536 维），
需要**新建一个集合**或把 `application.yml` 的 `embeddingDimension` 改为对应维度并重新 `initialize-schema`。

---

## 7. 小结与排错

- **Tika 报 `java.lang.NoClassDefFoundError`**：多半是只引了 `tika-core` 没引 `tika-parsers-standard-package`——
  前者只有检测/抽取 API，真正的解析器在后者里。
- **上传大文件 413**：调大 `spring.servlet.multipart.max-file-size`。
- **中文乱码**：`parseToString` 默认按文件声明的编码解析；若源文件缺编码声明，可改用 `AutoDetectParser` 并显式 `ParseContext` 指定 `EncodingDetector`。
- **召回不准**：先确认嵌入模型是否语义级（见第 6 节），再调分块大小（第 3 节 `splitText(文本, 400, 60)`）。
