# Java 学习案例汇总 · java-learning-cases

> 一个持续积累的 **Java 后端学习案例仓库**：从「手写迷你 Spring 理解 IoC/AOP 底层」，
> 到「Spring AI + Milvus 的 RAG 实战」，每个案例都带**详细中文注释**与**可运行代码**，
> 适合作为「看懂原理 → 动手实践」的学习资料与面试作品集。

---

## 📚 仓库内容一览

| 模块 | 简介 | 技术栈 |
|------|------|--------|
| [spring-ioc-aop-learn](./spring-ioc-aop-learn) | 手写迷你 IoC 容器 + AOP 框架，源码级理解 Spring 底层 | Java 17 / Maven / CGLIB 3.3 |
| [milvus-java-minimal-demo](./milvus-java-minimal-demo) | 最小 Milvus Java 客户端示例，演示向量库基础操作 | Java / Milvus Java SDK |
| [spring-ai-milvus-demo](./spring-ai-milvus-demo) | Spring Boot + Spring AI + Milvus 最小 RAG 链路 | Java 17 / Spring Boot 3.4 / Spring AI 1.0 / Milvus 2.5 |
| [rag-doc-qa-system](./rag-doc-qa-system) | 完整 RAG 文档智能问答系统（Vue3 前端 + Spring Boot 后端） | Vue3/TS/Vite/AntD + Spring Boot 3.4 / Spring AI / Milvus / Tika |

> 公共基础设施（Milvus / Attu 部署）见 [`docker/`](./docker) 目录。
> **MyBatis 源码学习（`mybatis-source-learn`）暂未纳入本仓库。**

---

## 🧩 模块索引

### 1. Spring IoC & AOP 手写学习 — `spring-ioc-aop-learn`
从零手写一套迷你 IoC 容器与 AOP 框架，不依赖 Spring 本身，直观演示 **控制反转** 与 **面向切面编程** 的核心原理。
- 每一处设计都对应 Spring 真实抽象：`BeanFactory` / `BeanDefinition` / `BeanPostProcessor` / `AdvisedSupport` / `MethodInterceptor` 等。
- 动态代理同时演示 **JDK 动态代理** 与 **CGLIB 代理**，含二者对比图解。
- 📁 图解文档：`docs/*.svg`（IoC 容器流程、AOP 代理流程、代理对比、整合流程）。
- ▶️ 运行：`mvn compile exec:java`（已通过 `.mvn/jvm.config` 配好 CGLIB 所需的 `--add-opens`）。

### 2. Milvus Java 客户端 — `milvus-java-minimal-demo`
最小可运行的 Milvus Java 客户端示例，演示连接 Milvus、集合/向量基础操作，作为上手向量库的「Hello World」。
- 📁 配套文档：
  - [`Milvus-Java-客户端操作文档.md`](./milvus-java-minimal-demo/Milvus-Java-客户端操作文档.md) — Java SDK 操作详解。
  - [`Attu使用说明.md`](./milvus-java-minimal-demo/Attu使用说明.md) — Milvus 官方可视化管理工具 Attu 的使用。

### 3. Spring AI + Milvus RAG 示例 — `spring-ai-milvus-demo`
一个最小、可零额外依赖运行的 **Spring Boot + Spring AI + Milvus** 案例，演示「文本 → 向量 → 存入向量库 → 相似度检索（RAG 核心）」链路。
- 关键点：**业务代码只认 `VectorStore` 接口**，底层换 Milvus / PGVector 不改业务代码。
- 自定义 `LocalHashingEmbeddingModel`（256 维）演示 Embedding 原理。
- 📁 配套资料：`samples/`（上手笔记与引导）、[`Tika-RAG-集成示例.md`](./spring-ai-milvus-demo/Tika-RAG-集成示例.md)。
- ▶️ 运行：先起 Milvus（`docker/milvus-standalone-docker-compose.yml`），再 `mvn spring-boot:run`。

### 4. RAG 文档智能问答系统 — `rag-doc-qa-system`
基于检索增强生成（RAG）的完整文档问答系统：上传 DOC/DOCX/PDF/TXT/MD → 解析切片 → 向量化存入 Milvus → 自然语言问答。

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue 4 + Pinia |
| 后端 | Spring Boot 3.4 + Spring AI 1.0 |
| 向量库 | Milvus 2.5+（Spring AI Milvus Starter） |
| 文档解析 | Apache Tika |
| 模型 | OpenAI 兼容接口（默认 SiliconFlow，可切换 DeepSeek / OpenAI） |

- 📁 配套文档：
  - [`RAG-数据加载与切片技术方案分析.md`](./rag-doc-qa-system/RAG-数据加载与切片技术方案分析.md) — 切片/检索策略设计。
  - [`Apache-Tika-使用说明.md`](./rag-doc-qa-system/Apache-Tika-使用说明.md) — 文档解析组件说明。
- ▶️ 运行：后端 `mvn spring-boot:run`；前端 `cd rag-doc-qa-frontend && npm install && npm run dev`。

---

## 🐳 公共基础设施：Milvus 部署 — `docker/`
| 文件 | 作用 |
|------|------|
| `milvus-standalone-docker-compose.yml` | 启动 Milvus standalone（etcd + MinIO + Milvus） |
| `attu-compose.yml` | 启动 Attu 可视化管理界面 |

```bash
cd docker
docker compose -f milvus-standalone-docker-compose.yml up -d
docker compose -f milvus-standalone-docker-compose.yml ps   # 等 milvus-standalone 状态 healthy
```
> ⚠️ `docker/volumes/`（运行数据，约 150M）已统一忽略，不入库。

---

## 🔧 环境约定
- **JDK 17+** / **Maven 3.6+** / **Node 18+**（仅前端模块需要）
- **密钥一律通过环境变量注入**，不入库：
  - `SILICONFLOW_API_KEY`（Embedding）
  - `DEEPSEEK_API_KEY`（对话生成）
  - 本地 Milvus 默认账号 `root` / 密码 `milvus`（仅教学用）
- 依赖与构建产物（`target/`、`node_modules/`、`dist/`、`docker/volumes/`）已由根 `.gitignore` 统一忽略。

---

## 📌 仓库约定
- 仅提交**源码、文档、配置、部署文件**；不提交依赖与运行产物。
- 智能体编程残留（`.workbuddy/`）、IDE 配置（`.idea/`）、系统文件（`.DS_Store`）已忽略。
- 子项目各自保留独立 `README.md` 与 `pom.xml`，可单独 `git clone` 后独立运行。
- `mybatis-source-learn`（MyBatis 源码学习）暂未纳入本仓库，后续按需添加。
