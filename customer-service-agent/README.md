# 智能客服 Agent（Java + Vue 全栈）

基于 **CodeBuddy Agent SDK** 构建的智能客服 Web 应用。满足：多轮对话、FAQ 知识库检索、自动意图识别（退款 / 查询订单 / 技术支持）、无法解决时自动转人工、对话历史持久化、管理后台（对话记录 + 满意度统计）。

> 本应用由 skill **`codebuddy-chat-web`** 启发而来：复用其 CodeBuddy Agent SDK 的集成模式（鉴权 + `query` 流式调用），但整体技术栈按要求落地为 **Java（Spring Boot）后端 + Vue 3 前端**，CodeBuddy 大模型能力通过可选的 Node 桥接服务接入。

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | **Java 17 + Spring Boot 3.2**（Web / JPA / H2）、SSE 流式推送 |
| 前端 | **Vue 3 + Vite + TypeScript + Element Plus + Pinia + ECharts** |
| 大模型 | CodeBuddy Agent（可选 Node 桥接 `@tencent-ai/agent-sdk`）；无 Key 时自动降级为本地规则引擎 |
| 持久化 | H2 文件库（可平滑切换 MySQL） |

---

## 架构

```
┌──────────────┐     SSE(text/event-stream)      ┌──────────────────────┐
│  Vue 3 前端   │ ──────────────────────────────▶│  Spring Boot 后端     │
│ (客服/管理)   │ ◀──────────────────────────────│  (意图/FAQ/Agent/持久)│
└──────────────┘     REST(会话/满意度/统计)       └──────────┬───────────┘
                                                            │ HTTP (可选)
                                                            ▼
                                                 ┌──────────────────────┐
                                                 │  Node 桥接服务        │
                                                 │  @tencent-ai/         │
                                                 │   agent-sdk(CodeBuddy)│
                                                 └──────────────────────┘
```

数据流：`用户输入 → 意图识别 → FAQ 检索打分 → (命中) LLM 生成 / (未命中·已知意图) 自动转人工 → SSE 分片推送 → 持久化`。

---

## 功能对照（需求 → 实现）

| 需求 | 实现位置 |
| --- | --- |
| 多轮对话 | `ConversationService`（会话 + 消息序列）、前端维护 `conversationId` 续聊 |
| FAQ 知识库检索 | `FaqService`（关键词 + 2-gram 重合度 + 权重打分），种子数据见 `FaqSeeder` |
| 自动识别意图 | `IntentService`：退款 / 查询订单 / 技术支持 / 未知 |
| 无法解决自动转人工 | `AgentService` 决策树：低分 / 未知意图 / 用户主动 → 生成工单 `EscalationTicket` |
| 对话历史持久化 | JPA + H2（`conversations` / `messages` 表），重启不丢 |
| 管理后台 | `AdminController`：对话记录检索 + 满意度统计（平均分 / 分布 / 按意图） |

---

## 目录结构

```
customer-service-agent/
├── backend/                 # Spring Boot 后端 (Maven)
│   ├── pom.xml
│   └── src/main/java/com/example/csagent/
│       ├── controller/      # Chat(SSE) / Session / Admin / Satisfaction / Health
│       ├── service/         # Intent / Faq / Agent(编排) / Llm(本地+CodeBuddy桥) / Escalation / Satisfaction / Conversation
│       ├── model/           # Conversation / Message / FaqItem / EscalationTicket / Satisfaction
│       ├── repository/      # JPA Repositories
│       ├── bootstrap/       # FAQ 种子数据
│       └── config/          # CORS / 业务配置
├── frontend/                # Vue 3 前端 (Vite)
│   └── src/
│       ├── views/           # CustomerChat(客服) / AdminDashboard(管理)
│       ├── components/      # MessageBubble / IntentBadge / SatisfactionDialog
│       └── api/client.ts    # REST + SSE(fetch+ReadableStream) 客户端
└── agent-bridge/            # 可选 CodeBuddy 桥接 (Node, 复刻 skill 的 SDK 集成)
    ├── server.js
    └── .env.example
```

---

## 运行

### 1. 后端（必需）
```bash
cd backend
export JAVA_HOME=/path/to/jdk17
mvn spring-boot:run        # 或 mvn package 后 java -jar target/*.jar
# 启动后监听 http://localhost:8080/api ，H2 文件库位于 backend/data/csagent.mv.db
```

### 2. 前端（必需）
```bash
cd frontend
npm install
npm run dev                # 开发：http://localhost:5173 （已配置 /api 代理到 8080）
# 或生产构建： npm run build && npm run preview  (http://localhost:4173)
```

### 3. CodeBuddy 大模型（可选）
默认使用**本地规则引擎**（意图 + FAQ 模板），无需任何 Key 即可完整体验。
若要接入真实 CodeBuddy 大模型：
```bash
cd agent-bridge
cp .env.example .env        # 填入 CODEBUDDY_API_KEY
npm install
npm start                   # 监听 http://localhost:3001
```
并在 `backend/src/main/resources/application.yml` 中开启桥接：
```yaml
app:
  codebuddy:
    enabled: true
    base-url: http://localhost:3001
```
> 桥接不可达 / 出错时，后端会自动降级回本地规则引擎，保证可用性。

---

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat/stream` | SSE 流式对话（事件：`init/intent/faq/escalate/text/done/error`） |
| GET  | `/api/sessions` | 会话列表（含消息数） |
| GET  | `/api/sessions/{id}` | 会话详情 |
| DELETE | `/api/sessions/{id}` | 删除会话 |
| POST | `/api/satisfaction` | 提交满意度 |
| GET  | `/api/admin/conversations` | 管理端对话记录（过滤+分页） |
| GET  | `/api/admin/stats/overview` | 概览统计 |
| GET  | `/api/admin/stats/satisfaction` | 满意度统计 |

---

## 备注
- 持久化默认 H2 文件库；切换 MySQL 仅需修改 `application.yml` 的 `spring.datasource` 与 `ddl-auto`。
- FAQ 知识库可在 `FaqSeeder` 中维护，或后续扩展为向量检索（Milvus / 本地 embedding）。
- 本工程为本地项目，仅保留本地 git，**不推送远程**。
