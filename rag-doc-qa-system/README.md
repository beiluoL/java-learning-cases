# RAG 文档智能问答系统

基于检索增强生成（Retrieval-Augmented Generation, RAG）的文档智能问答系统。支持用户上传 DOC/DOCX/PDF/TXT/MD 文档，系统自动解析、切片、向量化并存储到 Milvus，随后可针对文档内容进行自然语言问答。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue 4 + Pinia |
| 后端 | Spring Boot 3.4 + Spring AI 1.0 |
| 向量库 | Milvus 2.5+（通过 Spring AI Milvus Starter） |
| 文档解析 | Apache Tika |
| 向量/聊天模型 | OpenAI 兼容接口（默认 SiliconFlow，可切换 DeepSeek / OpenAI 等） |

---

## 项目结构

```
rag-doc-qa-system/
├── rag-doc-qa-backend/          # Spring Boot 后端
│   ├── src/main/java/com/example/ragdocqa/
│   │   ├── RagDocQaApplication.java
│   │   ├── config/              # 配置类（RagProperties、CORS）
│   │   ├── controller/          # REST 接口
│   │   ├── dto/                 # 请求/响应 DTO
│   │   ├── parser/              # 文档解析
│   │   └── service/             # 切片、RAG 检索与生成服务
│   ├── src/main/resources/application.yml
│   └── pom.xml
│
└── rag-doc-qa-frontend/         # Vue3 前端
    ├── src/
    │   ├── api/                 # HTTP / SSE 请求封装
    │   ├── components/          # 可复用组件
    │   ├── stores/              # Pinia 状态管理
    │   ├── types/               # TypeScript 类型
    │   ├── App.vue              # 主界面
    │   └── main.ts
    ├── index.html
    ├── package.json
    └── vite.config.ts
```

---

## 前置条件

1. **JDK 17+**
2. **Maven 3.8+**
3. **Node.js 22+**（推荐使用 managed 版本）
4. **Milvus 2.5+** 已运行（本机 Docker 默认端口 `19530`）
5. **LLM API Key**：SiliconFlow / DeepSeek / OpenAI 任选一个

> 若使用 SiliconFlow，请注册并创建 API Key：https://cloud.siliconflow.cn

---

## 快速启动

### 1. 启动 Milvus（如未启动）

```bash
cd /Users/beiluo/Documents/alProject/javalearn/docker
docker compose -f milvus-standalone-docker-compose.yml up -d
```

### 2. 配置后端 API Key

本项目默认使用 **SiliconFlow 做 Embedding** + **DeepSeek 做对话生成**，需要在环境变量中配置两个 Key：

```bash
export SILICONFLOW_API_KEY=你的_SiliconFlow_Key
export DEEPSEEK_API_KEY=你的_DeepSeek_Key
```

然后启动后端即可。你也可以直接编辑 `rag-doc-qa-backend/src/main/resources/application.yml` 中的对应字段。

> 如果不想用 DeepSeek，可以把 `llm.deepseek.enabled` 设为 `false`，并让 `spring.ai.openai.*` 同时承担 embedding 和 chat（此时只需一个 Key）。

### 3. 启动后端

```bash
cd rag-doc-qa-backend
mvn spring-boot:run
```

后端默认运行在 http://127.0.0.1:8080

### 4. 启动前端

```bash
cd rag-doc-qa-frontend
npm install
npm run dev
```

前端默认运行在 http://127.0.0.1:8010

---

## 核心接口

### 上传并处理文档

```http
POST /api/documents/upload
Content-Type: multipart/form-data

file: <文件>
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "docName": "示例.pdf",
    "chunkCount": 12,
    "charCount": 8543
  }
}
```

### 流式问答

```http
POST /api/chat
Content-Type: application/json

{
  "question": "文档主要讲了什么？"
}
```

返回 `text/event-stream`，按 token 流式输出答案。

---

## RAG 核心流程

1. **文档解析**：Apache Tika 自动识别 DOC/DOCX/PDF/TXT/MD，提取纯文本。
2. **文本切片**：按 `chunk-size`（默认 800 字符）+ `chunk-overlap`（默认 100 字符）滑动窗口切片。
3. **向量化**：调用 Embedding 模型将每个 chunk 转为 1024 维向量。
4. **向量存储**：写入 Milvus 集合 `rag_doc_chunks`，附带 `doc_name` 与 `chunk_index` 元数据。
5. **检索生成**：用户提问 → 问题向量化 → Milvus Top-K 检索 → 拼接上下文 → LLM 流式生成答案。

---

## 切换模型提供商

### 默认方案：SiliconFlow Embedding + DeepSeek Chat

已在 `application.yml` 中配置好：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.siliconflow.cn      # Embedding
      api-key: ${SILICONFLOW_API_KEY}
      chat:
        enabled: false
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5

llm:
  deepseek:
    enabled: true
    base-url: https://api.deepseek.com          # Chat
    api-key: ${DEEPSEEK_API_KEY}
    model: deepseek-chat
```

### 单 Key 方案：SiliconFlow 同时承担 Embedding + Chat

```yaml
spring:
  ai:
    openai:
      base-url: https://api.siliconflow.cn
      api-key: ${SILICONFLOW_API_KEY}
      chat:
        enabled: true
      embedding:
        options:
          model: BAAI/bge-large-zh-v1.5
      chat:
        options:
          model: deepseek-ai/DeepSeek-V3

llm:
  deepseek:
    enabled: false
```

### OpenAI

```yaml
spring:
  ai:
    openai:
      base-url: https://api.openai.com
      api-key: ${OPENAI_API_KEY}
      chat:
        enabled: true
      embedding:
        options:
          model: text-embedding-3-small
      chat:
        options:
          model: gpt-4o-mini

llm:
  deepseek:
    enabled: false
```

> 注意：切换 embedding 模型时，请同步修改 `spring.ai.vectorstore.milvus.collection.dimension` 为对应维度。

---

## 构建生产包

### 后端

```bash
cd rag-doc-qa-backend
mvn clean package -DskipTests
java -jar target/rag-doc-qa-backend-1.0.0-SNAPSHOT.jar
```

### 前端

```bash
cd rag-doc-qa-frontend
npm run build
```

产物位于 `dist/`，可部署到 Nginx 或静态服务器。

---

## 常见问题

**Q1：后端启动报错 `api-key must not be null`**  
A：请在 `application.yml` 或环境变量中配置 LLM API Key。

**Q2：上传大 PDF 解析慢**  
A：Tika 解析扫描版 PDF 依赖 OCR，建议先转换为文本型 PDF 或 TXT。

**Q3：问答结果与文档无关**  
A：检查 embedding 模型与 chat 模型是否正常工作；可尝试提高 `app.rag.top-k` 或减小 `chunk-size`。

---

## 后续可扩展

- 多轮对话历史整合
- 文档删除 / 重新索引
- 支持图片 / 扫描件 OCR
- 答案引用来源高亮
- 用户会话隔离与权限
