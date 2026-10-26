# infra · 公共基础设施

> **这一组放什么**：多个工程共用的中间件编排文件。目前只有 `docker/`（Milvus + Attu）。
>
> **为什么单独成组**：`03-ai-agent` 下有 3 个工程都要连 Milvus。如果每个工程各带一份 compose，会变成"三份配置三个版本、数据卷互相打架"。抽到 `infra/` 下，**一个仓库只有一套 Milvus**。

---

## 目录结构

```text
infra/
└── docker/
    ├── milvus-standalone-docker-compose.yml   # etcd + MinIO + Milvus standalone
    ├── attu-compose.yml                       # Attu 可视化管理界面（可选）
    ├── start.command / stop.command           # macOS 双击启停
    ├── start.bat / stop.bat                   # Windows 双击启停
    └── volumes/                               # 运行数据（约 150M，已忽略不入库）
```

---

## 快速启动

**方式一：双击脚本**（推荐）

- macOS：双击 `start.command` / `stop.command`
- Windows：双击 `start.bat` / `stop.bat`

**方式二：命令行**（在 `infra/docker` 目录下执行）

```bash
docker compose -f milvus-standalone-docker-compose.yml up -d
docker compose -f milvus-standalone-docker-compose.yml ps     # 等 milvus-standalone 状态 healthy
docker compose -f attu-compose.yml up -d                      # 可选：Attu GUI
```

## 端口与访问

| 服务 | 端口 | 用途 |
|---|---|---|
| Milvus | `19530` | gRPC 客户端连接（各 AI 工程都连这里） |
| Milvus | `9091` | metrics / 健康检查 |
| Attu | `9530` → 容器 `3000` | 浏览器 `http://127.0.0.1:9530` |

默认账号 `root` / `milvus`（仅本地教学用途）。

---

## 注意事项

- `volumes/` 是**运行数据**（向量库落盘），约 150M，由根 `.gitignore` 的 `**/volumes/` 忽略，**不入库**。删掉只是丢失已写入的集合数据，不影响代码。
- `attu-compose.yml` 复用 Milvus 创建的 `milvus` 网络，`MILVUS_URL` 直接写容器名 `milvus-standalone:19530`——**必须先起 Milvus 再起 Attu**。
- 脚本只在**本目录内**操作容器，用 `dirname $0` 定位自身，移动目录后仍可用；不依赖任何绝对路径。
- 这些容器是**本机共享服务**，不是某个工程的私有依赖。停掉后 `03-ai-agent` 下所有连 Milvus 的工程都会失效。

返回 [仓库总览](../README.md)
