# Attu 使用说明文档（Milvus 官方可视化工具）

> 本文档目标：从零带你把 **Attu** 装起来、跑通、用起来。
> 最终效果：浏览器打开 **http://127.0.0.1:9530** 即可进入 Attu 管理界面，
> 且 Attu 后端连接的 Milvus 端口为 **19530**。

---

## 0. 先厘清三个端口（最重要，避免再踩 404）

很多人打开 Milvus 相关页面时一脸懵，根因是**没分清"谁连谁"**。记住下面这条链路：

```
浏览器
  │  HTTP，访问 http://127.0.0.1:9530
  ▼
Attu 管理界面（端口 9530）
  │  Attu 后端按 MILVUS_URL 配置去连
  ▼
Milvus（gRPC 端口 19530）   ← 不是网页！是给 SDK/程序连的
```

| 端口 | 协议 | 浏览器能直接打开吗 | 说明 |
|------|------|-------------------|------|
| `19530` | **gRPC** | ❌ 会 404 | Milvus 服务端口（Java/Python SDK 连这个）。浏览器直接访问 `http://127.0.0.1:19530/` 会返回 `404 page not found` —— **这是正常的，不是故障** |
| `9091` | HTTP | ⚠️ 仅 `/healthz` | Milvus 健康检查接口，访问 `http://127.0.0.1:9091/healthz` 返回 `OK`，但不是页面 |
| `9530` | HTTP | ✅ **Attu 网页** | 本文配置：浏览器打开 `http://127.0.0.1:9530` 才是能"看到" Milvus 的界面 |

**结论**：Milvus 本身**没有网页界面**。想用浏览器看集合/向量/检索结果，必须装 Attu。
`19530` 那个 404 是协议不对（浏览器发 HTTP，Milvus 只懂 gRPC），与本文配置无关。

---

## 1. Attu 是什么

Attu 是 Milvus 官方出品的可视化管理 GUI，提供：

- 数据库 / 集合（Collection）的查看与管理
- 集合 Schema、索引类型、度量类型的可视化
- 已存入的向量数据、文本字段的浏览
- 在线执行向量检索（相似度搜索）并看结果
- 用户 / 权限（RBAC）管理

它本质是一个 **Web 前端 + 一个后端代理**：前端跑在 9530，后端替你用 gRPC 去连 19530 的 Milvus。

---

## 2. 环境搭建（前置条件）

| 条件 | 要求 | 验证命令 |
|------|------|----------|
| Docker | 已安装并正在运行 | `docker info` 能看到 ServerVersion |
| Milvus | 已启动且 `milvus-standalone` 容器存在、健康 | `docker ps` 看到 `milvus-standalone` 状态 `Up (healthy)` |
| 端口 | Milvus 的 `19530`/`9091` 已映射到宿主机 | `curl http://127.0.0.1:9091/healthz` 返回 `OK` |
| 网络 | Attu 要与 Milvus 处于**同一 Docker 网络**（本文复用名为 `milvus` 的网络） | `docker network ls` 能看到 `milvus` |

> 本文环境示例：Milvus 2.5.27 standalone（etcd + minio + milvus 三容器），
> 由 `infra/docker/milvus-standalone-docker-compose.yml` 启动，网络名 `milvus`，
> 容器名 `milvus-standalone`。

若 Milvus 还没装，先执行：

```bash
cd docker
docker compose -f milvus-standalone-docker-compose.yml up -d
# 等约 1~2 分钟，直到 milvus-standalone 状态为 healthy
docker compose -f milvus-standalone-docker-compose.yml ps
```

---

## 3. 安装部署 Attu

有两种等价方式，**任选其一**。两者都实现了：
- 浏览器通过 **9530** 访问
- 后端通过 **MILVUS_URL=milvus-standalone:19530** 连接 Milvus

### 方式一：`docker run`（最简单，推荐）

```bash
docker run -d \
  --name attu \
  -p 9530:3000 \
  --network milvus \
  -e MILVUS_URL=milvus-standalone:19530 \
  zilliz/attu:v2.5.11
```

### 方式二：`docker-compose`（可复现，已提供文件）

文件已位于 `infra/docker/attu-compose.yml`：

```bash
cd docker
docker compose -f attu-compose.yml up -d      # 启动
# 以后停止：docker compose -f attu-compose.yml down
```

> 两种方式效果完全一致。方式一适合临时起；方式二适合写进部署脚本、反复使用。

---

## 4. 关键配置参数详解（如何让 9530 成功打开）

这是你最关心的"端口映射 / 启动参数"说明。逐条解释：

### 4.1 端口映射 `-p 9530:3000`

- 格式：`-p <宿主机端口>:<容器内部端口>`
- `3000` 是 **Attu 容器内部默认监听的端口**（Attu 镜像写死监听 3000，改不了容器侧）。
- `9530` 是你想从**本机浏览器**访问的端口，可以自定义，本文固定为 9530。
- 含义：把宿主机的 9530 流量转发到 Attu 容器的 3000。

> 想要别的访问端口？只要改前面那个数即可，例如 `-p 8088:3000` 就改走 8088。
> **但本文档统一用 9530**，后续操作都基于 9530。

### 4.2 接入同一网络 `--network milvus`

- Attu 必须用**和 Milvus 相同的 Docker 网络**，否则它"找不到"Milvus 容器。
- 本文 Milvus 由官方 compose 创建的网络名为 `milvus`，所以这里写 `--network milvus`。
- 用 `docker-compose` 方式时，文件里 `networks: milvus: external: true` 表示"复用已存在的 milvus 网络"，不要新建。

### 4.3 后端连接 Milvus 地址 `-e MILVUS_URL=milvus-standalone:19530`

这是 **Attu 后端去连 Milvus 的地址**，正是你要求的"指向 19530"：

- `milvus-standalone`：Milvus standalone 容器的名字（在同一 Docker 网络内可被 DNS 解析）。
- `19530`：Milvus 的 **gRPC 服务端口**（SDK/Attu 都连这个，不是 9091，也不是 9530）。
- 因为 Attu 和 Milvus 在同一网络，用容器名即可；**不需要**写 `127.0.0.1` 或宿主机 IP。

> 若 Attu 与 Milvus **不在**同一网络（例如 Attu 跑在别的机器/别的网络），则 `MILVUS_URL`
> 要写成 Milvus 宿主可达的地址，例如 `host.docker.internal:19530` 或 `宿主机IP:19530`。
> 本文同网络场景，直接用 `milvus-standalone:19530` 最稳。

### 4.4 镜像版本 `zilliz/attu:v2.5.11`

- Attu 大版本要匹配 Milvus 大版本。Milvus 2.5.x 对应 Attu 2.5.x。
- 本文 Milvus 2.5.27 → 用 `zilliz/attu:v2.5.11`（2.5 系列均可）。

---

## 5. 验证安装是否成功

```bash
# 1) 看容器状态，端口应为 0.0.0.0:9530->3000
docker ps --filter name=attu
# 期望：attu   Up   ...   0.0.0.0:9530->3000/tcp

# 2) 浏览器请求 9530，期望返回 200
curl -s -o /dev/null -w "http_code=%{http_code}\n" http://127.0.0.1:9530/
# 期望：http_code=200

# 3) 确认 Attu 后端连的是 milvus-standalone:19530
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' attu | grep -i milvus
# 期望：MILVUS_URL=milvus-standalone:19530
```

三项都符合，说明 9530 能开、且后端指向 19530 配置正确。

---

## 6. 打开 Attu 管理界面（操作步骤）

1. 打开浏览器，访问 **http://127.0.0.1:9530**
2. 进入后会看到连接页（Connect）：
   - 因为部署时已通过 `MILVUS_URL` 配好地址，页面上 Milvus 地址通常已预填 `milvus-standalone:19530`。
   - 直接点 **Connect**（连接）即可进入。
3. 若需手动填写：
   - **Address** 填：`milvus-standalone:19530`（同网络场景）
   - 若 Attu 与 Milvus 不同网络，则填：`host.docker.internal:19530`
   - 未设鉴权（本文默认）时，Token 留空。
4. 连接成功后进入主界面，左侧是数据库 / 集合导航。

> 连不上怎么办：见第 9 节"常见问题"。绝大多数情况是 Attu 与 Milvus 不在同一网络，
> 或 Milvus 没真正 healthy。

---

## 7. Attu 具体操作步骤（界面玩法）

进入主界面后，结合本项目（Spring AI 已写入 `java_learning_docs` 集合）演示：

### 7.1 查看集合（Collection）
- 左侧导航选择默认数据库 `default`，即可看到集合列表，包含 `java_learning_docs`。
- 点集合名进入详情。

### 7.2 查看 Schema（结构）
- 在集合详情中查看字段（Field）：
  - `id`：主键
  - `content` / `text`：原始文本（业务写入的文档内容）
  - `vector`：256 维向量字段（本项目 `embeddingDimension: 256`）
- 查看索引：索引类型 `IVF_FLAT`、度量类型 `COSINE`（与 `application.yml` 配置一致）。

### 7.3 浏览已存入的数据
- 切到"数据（Data）"页签，可分页查看已写入的文档：每条记录含文本 + 其 256 维向量。
- 本项目先跑了 `POST /api/milvus/seed` 写入 5 条 Java/Spring/Milvus 学习文档，这里能直接看到。

### 7.4 执行向量检索（相似度搜索）
- 切到"向量检索（Search）"或"查询"页签。
- 输入查询向量或查询文本（部分版本支持直接输入文本，由服务端 embedding）。
- 设置 `topK`（如 3）、度量类型 `COSINE`。
- 点击搜索，返回按相似度（距离）由近到远排序的结果列表 —— 直观看到"语义最近"的文档排在最前。

### 7.5 用户 / 权限（可选）
- 若 Milvus 开启了鉴权，可在 Attu 的"用户管理"里建用户、配角色。
- 本文默认无鉴权，可跳过。

---

## 8. 与本项目的联动（推荐流程）

先让 Spring Boot demo 把数据写进 Milvus，再到 Attu 可视化查看：

```bash
# 终端 1：确保 Milvus + Attu 在跑
docker ps --filter name=milvus-standalone
docker ps --filter name=attu

# 终端 2：启动 Spring Boot 并写入示例数据
cd spring-ai-milvus-demo
mvn spring-boot:run
# 另开终端：
curl -X POST http://localhost:8080/api/milvus/seed
# => 已写入 5 条文档到集合 java_learning_docs
```

然后打开 **http://127.0.0.1:9530** → 进入 `java_learning_docs` 集合 → 在 Attu 里就能看到这 5 条数据及其向量。

---

## 9. 停止 / 重启 / 卸载

```bash
# 停止并删除 Attu 容器（数据在 Milvus 里，不会丢）
docker stop attu && docker rm attu

# 用 compose 方式起的，则：
docker compose -f infra/docker/attu-compose.yml down

# 想彻底清掉 Milvus + Attu（含数据卷）：
docker compose -f infra/docker/milvus-standalone-docker-compose.yml down -v
```

> Attu 自身不存储任何业务数据，它只是 Milvus 的"显示器"。删掉 Attu 容器毫无损失。

---

## 10. 常见问题（FAQ）

**Q1：浏览器打开 http://127.0.0.1:9530 提示无法连接 / 拒绝？**
- Attu 容器没起来：用 `docker ps --filter name=attu` 确认；若没有，回到第 3 节重新 `docker run`。
- 9530 端口被本机其他程序占用：`docker run` 会报 `port is already allocated`。换端口（改 `-p` 前面的数）或关掉占用程序。
- 防火墙/代理拦截：关闭相关代理后重试。

**Q2：Attu 能打开，但点 Connect 连不上 Milvus？**
- `MILVUS_URL` 写错：确认是 `milvus-standalone:19530`（同网络）。
- Attu 与 Milvus **不在同一网络**：确保都加了 `--network milvus`（或 compose 里 `networks: [milvus]` 且 `external: true`）。
- Milvus 没真正 healthy：`docker ps` 看 `milvus-standalone` 是否 `Up (healthy)`，未健康就等或重启它。

**Q3：为什么 http://127.0.0.1:19530 是 404？**
- 19530 是 **gRPC 端口**，不是网页。浏览器发 HTTP 请求过去，Milvus 用 gRPC 协议解析不了，返回 404。**这是正常现象**，不代表 Milvus 没装好。要"看"Milvus 请用本文的 9530（Attu）。

**Q4：想换访问端口（比如改成 8080）？**
- 只改 `-p` 前面的宿主端口即可，例如 `-p 8080:3000`。本文档统一用 9530。

**Q5：Attu 镜像拉不下来 / 想换版本？**
- 版本需与 Milvus 大版本匹配：Milvus 2.5.x → `zilliz/attu:v2.5.x`；Milvus 2.4.x → `zilliz/attu:v2.4.x`。
- 国内拉取慢可配置镜像加速器（Docker Desktop → Settings → Docker Engine → registry-mirrors）。

---

## 附：一键速查

```bash
# 启动 Milvus（若未起）
docker compose -f infra/docker/milvus-standalone-docker-compose.yml up -d

# 启动 Attu（9530，后端连 milvus-standalone:19530）
docker run -d --name attu -p 9530:3000 --network milvus \
  -e MILVUS_URL=milvus-standalone:19530 zilliz/attu:v2.5.11

# 浏览器访问
#   http://127.0.0.1:9530   ← Attu 管理界面
#   http://127.0.0.1:9091/healthz   ← Milvus 健康探针（返回 OK）

# 停止 Attu
docker stop attu && docker rm attu
```
