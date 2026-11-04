# javalearn · Java 学习与全栈实战仓库

> **一句话定位**：一个人的 Java 成长档案 —— 从「手写 IoC/AOP 看懂框架原理」，到「前后端分离业务系统」，再到「Spring AI / Milvus / RAG / Agent 全栈落地」。
>
> 16 个独立工程按**学习进阶顺序**分成 3 组，另有公共文档与基础设施目录。每个工程都能单独打开、单独运行，互不依赖。

---

## 🧭 分组导航

| 分组 | 放什么 | 为什么这么分 |
|---|---|---|
| **[`01-spring-core/`](./01-spring-core)** | 框架原理与源码级学习（纯后端，无前端） | 这是整条学习路线的**地基**：先手写一遍 IoC/AOP、MyBatis、SSM，再看 Spring Boot 只是"自动配置把地基包起来"，理解成本骤降 |
| **[`02-business-system/`](./02-business-system)** | 可跑起来的业务系统（含前后端分离全栈） | 原理会了要**落到业务**：权限、审批流、订单、支付、设备接入，这些是面试与工作实际遇到的东西 |
| **[`03-ai-agent/`](./03-ai-agent)** | AI / RAG / Agent 应用 | 建立在 01、02 之上：用 Spring Boot 承接 LLM，用向量库做检索，最终做成能对话的 Agent |
| **[`docs/`](./docs)** | 跨工程文档：系统设计方案、移动端 UI 设计资料 | 不属于任何单个工程，是**方法论沉淀**，与代码解耦 |
| **[`infra/`](./infra)** | 公共基础设施编排（Milvus / Attu） | 多个 AI 工程共用的中间件，抽出来避免每个工程各写一份 compose |

**编号只是排序，不是依赖**：`01` 组的工程不会 import `02` 组的代码，反之亦然。想直接跑 `03-ai-agent` 也没问题。

---

## 📦 全量项目总表

> 「怎么跑」优先写**双击脚本**（`start.command` / `start.bat`，macOS / Windows 双击即可）；没有脚本的写命令行。端口以各工程 `application.yml` 为准。

### 01-spring-core

| 工程 | 一句话简介 | 技术栈 | 类型 | 前端 | 怎么跑 |
|---|---|---|---|---|---|
| [spring-ioc-aop-learn](./01-spring-core/spring-ioc-aop-learn) | 手写迷你 IoC 容器 + AOP 框架，源码级理解 Spring 底层 | Java 17 · Maven · CGLIB 3.3 | 学习 | 否 | `./start.command` 或 `mvn compile exec:java` |
| [mybatis-source-learn](./01-spring-core/mybatis-source-learn) | 手写简化版 MyBatis（mini-mybatis），理解 SQL 映射与代理 | Java · Maven · H2 | 学习 | 否 | `./start.command` |
| [springboot-mybatis-learn](./01-spring-core/springboot-mybatis-learn) | Spring Boot + MyBatis 最小整合工程 | Spring Boot 2.6.13 · MyBatis · MySQL | 学习 | 否 | `mvn spring-boot:run`（:8080） |
| [ssm-learn](./01-spring-core/ssm-learn) | 传统 SSM（Spring + SpringMVC + MyBatis）企业级模板 | Spring 5 · SpringMVC · MyBatis · Maven | 学习 | 否 | `./start.command` |
| [spring-transaction-boundary-lab](./01-spring-core/spring-transaction-boundary-lab) | 事务边界实验：传播行为与事务失效场景可复现验证 | Spring Boot 3.5.16 · JPA | 学习 | 否 | `./start.command`（:8095） |

### 02-business-system

| 工程 | 一句话简介 | 技术栈 | 类型 | 前端 | 怎么跑 |
|---|---|---|---|---|---|
| [erp-crm-system](./02-business-system/erp-crm-system) | ERP/CRM 综合系统：RBAC 权限 + 审批流引擎 + 进销存/HRM/CRM 模块 | Spring Boot 3.4.1 · MyBatis-Plus 3.5.5 · H2 / MySQL | 系统 | 否 | `mvn spring-boot:run`（:8090，默认 `h2` profile 免装库） |
| [video-stream-system](./02-business-system/video-stream-system) | 全球视频流系统后端设计落地：上传 → 转码 → 存储 → 播放 | Spring Boot 3.4.1 · Java 17 | 系统 | 否 | `mvn spring-boot:run`（:8080） |
| [spring-vue-learn](./02-business-system/spring-vue-learn) | Spring Boot + Vue3 最小可执行前后端分离骨架 | Spring Boot 3.4.1 · Vue 3 · Vite | 学习 | 是 | `./start.command`（前后端一键起） |
| [ecommerce-learn-all](./02-business-system/ecommerce-learn-all) | 电商全栈（合并式：`ecommerce-learn` 后端 + `ecommerce-learn-vue` 前端） | Spring Boot · MyBatis · Vue 3 · Vite | 系统 | 是 | `./start.command` |
| [iot-platform](./02-business-system/iot-platform) | 企业级 IoT 物联网 + 大数据学习平台 | Spring Boot 3.2.12 · Vue 3 | 系统 | 是 | `./start.command` |
| [finpay-scaffold](./02-business-system/finpay-scaffold) | 金融支付多模块脚手架：网关 + 业务 + 前端 | Spring Boot 3.2.5 · Gateway · Vue 3 | 系统 | 是 | 网关 `mvn -pl finpay-gateway spring-boot:run`（:8088）；业务（:8080）；前端 `cd finpay-web && npm run dev` |
| [mq-practice](./02-business-system/mq-practice) | MQ 实战三场景：秒杀削峰填谷 / 延迟消息超时关单 / 商品变更发布订阅 | Spring Boot 3.4.1 · RabbitMQ · MyBatis-Plus · Redis · Vue 3 | 系统 | 是 | `./start.command` 或 `start.bat`（自动拉起 RabbitMQ，默认 h2 免装库） |

### 03-ai-agent

| 工程 | 一句话简介 | 技术栈 | 类型 | 前端 | 怎么跑 |
|---|---|---|---|---|---|
| [milvus-java-minimal-demo](./03-ai-agent/milvus-java-minimal-demo) | Milvus Java 客户端最小示例（向量库「Hello World」）+ Attu 使用文档 | Java · Milvus Java SDK | 学习 | 否 | `./start.command`（需先起 Milvus） |
| [spring-ai-milvus-demo](./03-ai-agent/spring-ai-milvus-demo) | Spring AI + Milvus 最小 RAG 链路，业务只依赖 `VectorStore` 接口 | Spring Boot 3.4 · Spring AI 1.0 · Milvus 2.5 | 学习 | 否 | `./start.command`（需先起 Milvus） |
| [rag-doc-qa-system](./03-ai-agent/rag-doc-qa-system) | 完整 RAG 文档问答：上传 → Tika 解析切片 → 向量化 → 自然语言问答 | Spring Boot 3.4.5 · Spring AI · Milvus · Tika · Vue 3 | AI | 是 | `./start.command` |
| [ai-knowledge-base-all](./03-ai-agent/ai-knowledge-base-all) | AI 知识库（合并式，当前仅后端落地，前端待补） | Spring Boot 3.4.1 · Spring AI | AI | 否 | `cd ai-knowledge-base-all/ai-knowledge-base && mvn spring-boot:run`（:8080） |
| [customer-service-agent](./03-ai-agent/customer-service-agent) | 智能客服 Agent：`backend` + `frontend` + `agent-bridge` 三端协作 | Spring Boot 3.2.5 · Vue 3 · Node bridge · H2 | AI | 是 | `./start.command`（后端 :8080 / 前端 :5173） |

### docs / infra

| 目录 | 内容 |
|---|---|
| [`docs/技术方案/`](./docs/技术方案) | 7 篇系统实现技术方案（IM、分布式存储、搜索引擎、爬虫、电商、社交新闻、附近的人） |
| [`docs/系统设计/`](./docs/系统设计) | 系统设计方案库（短网址、全球视频流、打车），含索引 README |
| [`docs/移动端UI设计开源Skill清单.md`](./docs/移动端UI设计开源Skill清单.md) | 移动端 UI 设计开源 Skill 资料汇总 |
| [`infra/docker/`](./infra/docker) | Milvus standalone + Attu 编排（多个 AI 工程共用） |

---

## 🚀 新手从哪开始

如果你要按最短路径把整个仓库跑通，建议这个顺序：

```text
① 看懂框架
   spring-ioc-aop-learn        ← 手写 IoC/AOP，理解 Spring 在干什么
        ↓
   mybatis-source-learn        ← 手写 MyBatis，理解 ORM 与动态代理
        ↓
   springboot-mybatis-learn    ← 看 Spring Boot 怎么把上面这些"自动配置"起来
        ↓
   ssm-learn → spring-transaction-boundary-lab
                               ← 传统 SSM 对比 + 事务传播/失效这类高频面试点

② 落到业务
   spring-vue-learn            ← 最小的前后端分离骨架，先跑通联调
        ↓
   erp-crm-system             ← 权限 + 审批流引擎，业务复杂度上一个台阶
        ↓
   iot-platform / finpay-scaffold
                               ← 多模块、网关、设备接入这类工程化形态

③ 接入 AI
   milvus-java-minimal-demo    ← 先会用向量库
        ↓
   spring-ai-milvus-demo      ← 再看 Spring AI 怎么把它包成 VectorStore
        ↓
   rag-doc-qa-system          ← 完整的 RAG 问答产品
        ↓
   customer-service-agent     ← 最后做能对话、有工具调用链路的 Agent
```

---

## 🐳 公共基础设施：Milvus

`03-ai-agent` 下的 Milvus 相关工程共用 `infra/docker/` 里的编排：

```bash
cd infra/docker
docker compose -f milvus-standalone-docker-compose.yml up -d    # etcd + MinIO + Milvus
docker compose -f milvus-standalone-docker-compose.yml ps       # 等 milvus-standalone 变 healthy
docker compose -f attu-compose.yml up -d                        # 可选：Attu 可视化管理界面
```

- Milvus 端口 `19530`（gRPC）/ `9091`（metrics）；Attu 浏览器访问 `http://127.0.0.1:9530`。
- `infra/docker/volumes/` 是运行数据（约 150M），已被忽略、不入库；删掉只是重新拉取镜像数据。

---

## 🔧 环境约定

- **JDK 17+**、**Maven 3.6+**；前端工程额外需要 **Node 18+**。`01-spring-core/springboot-mybatis-learn` 用的是 Spring Boot 2.6（JDK 8/11 也可）。
- **密钥一律走环境变量，绝不入库**：
  - `SILICONFLOW_API_KEY`（Embedding）
  - `DEEPSEEK_API_KEY`（对话生成）
- 本地 Milvus 默认账号 `root` / `milvus`（仅教学用途）。

---

## 📌 仓库约定

**入库的**：源码、文档、配置、部署编排（compose / 一键脚本）。

**不入库的**（见根 [`.gitignore`](./.gitignore)）：

| 类别 | 规则 |
|---|---|
| 依赖与构建产物 | `node_modules/`、`target/`、`dist/`、`.cache/`、`*.class`、`*.jar` |
| 运行态数据库 | `*.mv.db`、`*.trace.db`、`*.sqlite*`（H2 / SQLite 不是源码） |
| 一键脚本运行态 | `.run/`、`*.pid`、`**/logs/*` |
| 容器运行数据 | `**/volumes/` |
| 密钥 | `.env`、`.env.*`、`*.local` |
| IDE / 系统 / 智能体残留 | `.idea/`、`.vscode/`、`.DS_Store`、`.workbuddy/` |

**唯一例外**：`01-spring-core/spring-transaction-boundary-lab/docs/logs/*` 是事务实验的**交付证据日志**，由 `!**/docs/logs/*` 例外规则保留跟踪，不要当垃圾清掉。

**目录组织约定**：前后端分离工程统一用「`项目名/backend` + `项目名/frontend`」（分离式）或「`项目名-all/后端` + `项目名-all/前端`」（合并式）两种形态之一，不把前后端拆成两个互不相干的顶层目录。
