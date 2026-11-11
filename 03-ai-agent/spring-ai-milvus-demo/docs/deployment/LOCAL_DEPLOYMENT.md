# 本地部署与运维手册

> 本文件对应代码路径：
> - 配置：`src/main/resources/application.yml`
> - MySQL 编排：`infra/docker/ai-cost-compose.yml`（仓库根目录 `infra/docker/`）
> - Milvus 编排：`infra/docker/milvus-standalone-docker-compose.yml`
> - 启停脚本：`start.command` / `stop.command` / `restart.command`（macOS）、`start.bat` / `stop.bat` / `restart.bat`（Windows）
> - 运行产物：`.run/backend.pid`、`.run/frontend.pid`、`.run/backend.log`、`.run/frontend.log`（脚本运行时生成）
> - 前端代理：`frontend/vite.config.ts`；安全拦截：`src/main/java/com/example/ai/config/AdminAccessInterceptor.java`
>
> 本文只描述本地开发环境；示例价格与 Demo 身份均不可用于生产。

## 1. 前置条件

| 组件 | 要求 | 说明 |
|---|---|---|
| JDK | **17（强制）** | `start.command` 优先使用本机 Corretto 17，找不到时回落到 `JAVA_HOME`。**手动执行 Maven 必须显式 `export JAVA_HOME`**，见 1.1 |
| Maven | 3.8+ | 脚本优先用本机 Maven，否则回落 `mvn` |
| Docker | 可运行 compose | 提供 MySQL 8.0；Milvus 仅在开启 RAG 时需要 |
| Node.js / npm | 支持 Vite 6 | 前端 `frontend/` 下需先 `npm install` |
| Redis | 本机 `127.0.0.1:6379` | 本机已运行 Redis 8.8，默认直接复用 |
| MySQL | Docker，宿主端口 **13307** | 由 `ai-cost-compose.yml` 提供，库 `ai_cost` |

### 1.1 必须显式指定 JDK 17

本机 `mvn -v` 可能解析到 Homebrew 装的 **JDK 26**。**JDK 23 起 javac 默认关闭隐式注解处理**，Lombok 注解处理器不再自动运行，编译会报数百处「找不到符号 `getXxx()`」，而错误信息**完全不提 Lombok**，极易误判为代码问题。

```bash
export JAVA_HOME=/Users/beiluo/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
mvn -v          # 应显示 Java version: 17.0.14
```

一键脚本内部已处理该回退，只有手动跑 Maven / IDE 终端时需要自己设。

## 2. 端口清单（冲突重点）

| 端口 | 用途 | 备注 |
|---|---|---|
| **8091** | 后端 | 8080 已被工作区其它项目（`blogagent-server`）长期占用，本平台固定 8091；脚本只检查 / 管理 8091 |
| **5174** | 前端 dev server | 5173 被其它项目占用；`vite.config.ts` 设 `strictPort: true`，被占用直接报错而非自动漂移 |
| **13307** | MySQL 宿主映射 | 容器内 3306；**不是 3306**，避免与其它项目冲突 |
| **6379** | Redis（本机） | 容器化备选为 6380（见 compose 注释），需同步改 `REDIS_PORT` |
| 19530 / 9091 | Milvus gRPC / 健康 | 仅 RAG 开启时需要 |
| 9000 / 9001 | Milvus 编排内 MinIO | **本机已被其它项目的 MinIO 占用**，开启 RAG 起 Milvus 时可能要调整端口映射 |

> 约定：脚本**绝不按端口杀进程**，不使用 `pkill` / `kill -9` / `lsof -ti`；`lsof` / `netstat` 仅用于**检查**占用，被占用时明确拒绝启动并退出。

## 3. 启动 MySQL 8.0（Docker）

在**仓库根目录**的 `infra/docker/` 下执行：

```bash
cd infra/docker
docker compose -f ai-cost-compose.yml up -d
docker compose -f ai-cost-compose.yml ps        # 等待 healthcheck 变 healthy
```

编排要点（`ai-cost-compose.yml`）：

- 镜像 `mysql:8.0.36`，容器名 `ai-cost-mysql`，独立 project name `ai-cost` 与网络 `ai-cost-net`。
- 端口 `"${MYSQL_PORT:-13307}:3306"`；库 `${MYSQL_DB:-ai_cost}`；root 密码 `${MYSQL_PASSWORD:-root}`。
- `TZ: UTC` + `--default-time-zone=+00:00`、`--character-set-server=utf8mb4`，与应用要求的 UTC / utf8mb4 一致。
- 数据卷 `./volumes/ai-cost-mysql`，`down` 保留数据、`down -v` 才删除。

应用侧 JDBC（`application.yml`）：

```
jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:13307}/${MYSQL_DB:ai_cost}?...&serverTimezone=UTC
```

**表结构无需手工建**：应用启动时 Flyway 按序执行 5 个迁移，`validate-on-migrate=true` 会校验结构漂移。

| 迁移 | 内容 |
|---|---|
| `V1__init_ai_cost.sql` | 建 5 张核心表（`ai_token_usage`、`ai_model_pricing`、`ai_budget_policy`、`ai_budget_reservation`、`ai_cost_alert`） |
| `V2__seed_demo_data.sql` | 6 条 `DEMO-v1` 价格 + 3 条默认预算策略 |
| `V3__add_provider_alias_demo_pricing.sql` | 为供应商别名模型 `deepseek-flash` 补 `DEMO-v1` 价格，**不改表结构** |
| `V4__governance_schema.sql` | **建 8 张治理表**（`ai_budget_transaction`、`ai_cost_ledger`、`ai_cost_protection_rule`、`ai_cost_protection_log`、`ai_cost_optimization_log`、`ai_cost_daily_summary`、`ai_agent_cost_trace`、`ai_infrastructure_cost`） |
| `V5__governance_seed.sql` | 5 条保护规则 + 九级预算策略 + 告警阈值种子 |

执行完成后 `ai_cost` 库共 **13 张业务表** + `flyway_schema_history`（5 条 success）。核对：

```bash
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ai_cost';"
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT version, success FROM ai_cost.flyway_schema_history;"   # 1~5 全部 success
```

> 容器内 `mysql` 客户端默认字符集可能不是 `utf8mb4`，中文注释显示成 `?????` 时加 `--default-character-set=utf8mb4` 再看。**这只是客户端显示问题**，`information_schema` 中表/列字符集确为 `utf8mb4` / `utf8mb4_0900_ai_ci`。

停止：`docker compose -f ai-cost-compose.yml down`（保留数据卷）。

## 4. Redis

- 默认复用本机 `brew` 启动的 Redis 8.8（`127.0.0.1:6379`），应用用 DB 0；`RedisBudgetIT` 用 DB 15、`GovernanceRedisIT` 用 DB 14 隔离。
- 验证：`redis-cli ping` → `PONG`。
- 容器化备选：`ai-cost-compose.yml` 中已注释掉的 `redis` 服务（映射 `6380:6379`），启用后需把应用 `REDIS_PORT` 改为 6380。
- Redis 不可用时预算预扣按**安全失败**处理：拒绝调用并返回 `50301`，不会放行未经预算检查的模型调用。

## 5. 环境变量清单

默认值取自 `application.yml` / `AiProperties.java` / `AdminAccessInterceptor.java`，均可通过环境变量覆盖。

| 变量 | 默认值 | 说明 |
|---|---|---|
| `AI_API_KEY` | 空 | **唯一**的 API Key 入口，**只走环境变量**，代码与配置文件无明文；`ai.provider=openai` 且为空时启动报 `IllegalStateException` |
| `AI_PROVIDER` | `mock` | `mock`（默认，无需 Key）/ `openai`（OpenAI 兼容接口，含 DeepSeek）；`openai` 已实测可用，见 5.1 |
| `AI_MODEL` | `deepseek-chat` | 请求使用的模型名；账单以供应商返回的 `actualModel` 为准（实测供应商返回 `deepseek-flash`，与请求值不同） |
| `AI_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容接口地址（实测取该值） |
| `AI_ADMIN_TOKEN` | 空 | 配置后 `/api/admin/**` 要求请求头 `X-Admin-Token` 常量时间匹配；**未配置时仅允许本机**（`127.0.0.1` / `::1`） |
| `AI_BUDGET_ENABLED` | `true` | 置 `false` 时 `reserve()` 返回 `DISABLED` 直接放行，仅供本地调试，生产不应关闭 |
| `AI_CACHE_ENABLED` | `true` | 响应缓存开关；关闭后可做「开缓存 vs 关缓存」成本对照 |
| `AI_PROMPT_VERSION` | `v1` | Prompt 版本号，**参与缓存键**；改 Prompt 必须同步改版本，否则会命中旧答案 |
| `AI_ROUTING_ENABLED` | `true` | 模型路由开关；关闭后一律用 `AI_MODEL` |
| `AI_ROUTING_L1_MODEL` / `L2` / `L3` | 空 | 三档路由的目标模型名；留空则回落到 `AI_MODEL` |
| `AI_RATE_LIMIT_ENABLED` | `true` | 限流 / 并发 / 熔断开关；Redis 异常时**放行**（fail-open），不影响可用性 |
| `RAG_ENABLED` | `false` | RAG / Milvus 可选装配开关，见第 8 节 |
| `MILVUS_INIT_SCHEMA` | `false` | 是否在启动时自动创建 Milvus 集合 |
| `MILVUS_USERNAME` / `MILVUS_PASSWORD` | `root` / `milvus` | Milvus 连接凭据（仅 RAG 开启时用） |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `127.0.0.1` / `13307` / `ai_cost` | 数据库连接 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `root` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 连接 |

其它固定约束（非环境变量）：

| 配置项 | 值 | 说明 |
|---|---|---|
| `ai.budget.reservation-ttl-seconds` | 300 | 预扣有效期，超时由 `budget_cleanup.lua` 回收 |
| `ai.analytics.max-range-days` | 92 | 查询跨度上限，超出直接拒绝 |
| `ai.analytics.max-page-size` | 100 | 分页上限 |
| `ai.protection.emergency-key` | `ai:{governance}:emergency-stop` | 紧急开关在 Redis 中的键 |
| `ai.routing.downgrade-at-level` | 3 | 保护等级 ≥3 时强制降档到 L1 低价模型 |
| `ai.routing.complex-char-threshold` | 240 | 判定「复杂问题」的字符阈值 |
| `ai.rate-limit.per-user-per-minute` / `per-model-concurrency` / `global-concurrency` | 30 / 8 / 32 | 限流与并发闸门 |
| `ai.optimization.*` | 见 yml | Prompt / 上下文压缩、RAG 上下文限制、输出 Token 上限、Embedding 去重，**每项都有独立开关**，便于做成本对照实验 |
| `ai.agent.max-steps` / `max-tool-calls` / `max-cost` | 8 / 16 / 0.20 | Agent 步数、工具调用、成本三重硬上限 |
| `ai.retry.max-attempts` / `retry-on-timeout` | 2 / `false` | **超时不重试**：无法确认供应商是否已计费，重试可能重复扣费 |
| Hikari | `maximum-pool-size=20 / minimum-idle=2 / connection-timeout=5000` | 连接池 |

> 前端当前**未注入 `X-Admin-Token` 请求头**（`frontend/src/api/request.ts`）。本地联调依赖「未配置 `AI_ADMIN_TOKEN` 时仅允许本机」这一路径；一旦配置了管理员令牌，需自行给前端补该请求头，否则管理接口会返回 403。

### 5.1 启用真实供应商（已实测）

默认 `mock` 无需 Key 即可启动；改用真实供应商时设置环境变量（`AI_API_KEY` 只从本机环境变量读取，切勿写入代码或配置文件）：

```text
AI_PROVIDER=openai
AI_MODEL=deepseek-chat
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=<取自本机环境变量>
```

本次验收在 **8092 端口的独立实例**上完成：`POST /api/ai/chat` 返回 `code=0`，供应商实际返回 `deepseek-flash`、`usageStatus=REAL`、`inputTokens=35 / outputTokens=25 / totalTokens=60`，答案为真实生成内容；成本 `inputCost=0.00000945` / `outputCost=0.00002750` / `totalCost=0.00003695`（USD、`pricingVersion=DEMO-v1`），预算 `reserved=0.00872000` / `settled=0.00003695`，账单落 MySQL 且 `usageStatus=REAL`。

- **别名模型与 V3**：供应商返回的 `deepseek-flash` 必须在 `ai_model_pricing` 有对应价格版本，否则首次调用会被正确标 `UNPRICED` 并告警（0 成本，防止用错价格的安全行为）。`V3__add_provider_alias_demo_pricing.sql` 已为该别名补 `DEMO-v1` 价格，应用启动由 Flyway 自动执行，无需手工 SQL。
- **口径提醒**：`usageStatus=REAL` 表示 Token 来自供应商真实返回，但单价全部是 `demo_flag=1` 的 **DEMO 示例价，不是供应商真实报价**，不能据此做真实财务结算 / 计费对账。
- 详见 `docs/acceptance/TEST_REPORT.md` 第 6 节。

## 6. 一键脚本（6 个）

| 平台 | 启动 | 停止 | 重启 |
|---|---|---|---|
| macOS | `start.command` | `stop.command` | `restart.command` |
| Windows | `start.bat` | `stop.bat` | `restart.bat` |

- 用法：`start.command [all|backend]`，双击默认 `all`（后端 + 前端）；`restart.command` 同参数，可加 `-y` 免暂停。
- 启动流程（`start.command`）：先精确停掉本项目残留实例 → 检查 8091 是否被占用（占用即拒绝） → `mvn -q -DskipTests package` → `java -jar target/*.jar` → 轮询就绪（最多 120s） → `npm run dev -- --port 5174 --strictPort`。前端 `node_modules` 不存在时**跳过并提示**，不静默失败。
- 精确关停：PID 写入 `.run/backend.pid` / `.run/frontend.pid`；`stop.command` 只对这两个 PID 发普通 `kill`（**不是 `kill -9`**），最多等 10s，仍未退出则提示人工处理并清理 PID 文件；`stop.bat` 按 `.run\*.pid` 用 `taskkill /PID` 终止，与 macOS 普通 `kill` 行为不同。
- 日志：`.run/backend.log`、`.run/frontend.log`；后端就绪后窗口 `tail -f` 跟踪日志，`Ctrl+C` 只退出日志跟踪，不停止服务。
- 脚本明确不管理外部共享服务（Redis / MySQL / Milvus）。

> Windows `.bat` 脚本**未在 macOS 实测**，见 `docs/acceptance/FINAL_ACCEPTANCE_REPORT.md`。

## 7. 端口占用与冲突处理

1. 8091 被占用：`start.command` 用 `lsof -nP -iTCP:8091 -sTCP:LISTEN` 打印占用进程后**拒绝启动**，不会终止该进程；`start.bat` 用 `netstat` + `findstr` 同理。
2. 5174 被占用：Vite `strictPort: true` 直接报错退出，不自动换端口。
3. 8080 / 5173 / 9000 / 9001 / 3306 属于其它项目或易冲突端口，本平台一律避开；**禁止**为了腾端口去杀别的进程。
4. 若占用来自本项目残留实例：先运行 `stop.command -y`（或 `stop.bat -y`）按 PID 文件精确停止，再启动。

## 8. RAG 开关

默认 `rag.enabled=false`（`RAG_ENABLED` 未设置），成本平台**不依赖 Milvus 即可独立启动**；`/api/milvus/**`、`/api/rag/**` 不装配，`useRag=true` 的问答降级为无知识库回答（记日志，不阻断）。

需要恢复 RAG 时：

```bash
cd infra/docker
docker compose -f milvus-standalone-docker-compose.yml up -d   # 注意 9000/9001 可能被其它项目 MinIO 占用，必要时改端口映射
```

然后设置 `RAG_ENABLED=true`（或 `rag.enabled=true`），需要自动建集合再设 `MILVUS_INIT_SCHEMA=true`，启动应用即可恢复 `RagConfiguration` 的 `@Import` 与 4 个 RAG Bean。决策背景与代价见 `docs/architecture/ADR/ADR-001-rag-optional-assembly.md`。

> **本次验收在 `rag.enabled=false` 下进行，RAG 检索未做端到端验证**，恢复路径本身也未在本次验收中实测。

## 9. 启动后自检

> 本机存在 `HTTP_PROXY` 环境变量，`curl` 访问 `localhost` 必须加 `--noproxy '*'`，否则会走代理导致连不上。

```bash
curl --noproxy '*' http://localhost:8091/actuator/health        # {"status":"UP"}

curl --noproxy '*' -X POST http://localhost:8091/api/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":"demo-user","message":"你好","scene":"CHAT"}'

# 成本分析三件套
curl --noproxy '*' http://localhost:8091/api/admin/cost/overview
curl --noproxy '*' http://localhost:8091/api/admin/cost/breakdown
curl --noproxy '*' http://localhost:8091/api/admin/cost/forecast

# 保护状态（level / maxBudgetUsageRate / modelRoutes）
curl --noproxy '*' http://localhost:8091/api/admin/cost-protection/status

# 手动触发阈值评估 / 异常检测扫描
curl --noproxy '*' -X POST http://localhost:8091/api/admin/cost-alert/evaluate
curl --noproxy '*' -X POST http://localhost:8091/api/admin/cost-alert/anomaly-scan
```

前端打开 `http://localhost:5174`，Vite 把 `/api` 代理到 `http://localhost:8091`。管理接口统一响应 `{code,message,data,traceId}`。

**10 个页面 / 3 个导航组**：

| 分组 | 页面 | 路由 |
|---|---|---|
| 分析 | 成本总览（首页） | `/dashboard/cost` |
| 分析 | 成本构成 | `/dashboard/cost/breakdown` |
| 分析 | Token 分析 | `/dashboard/token-usage` |
| 分析 | 模型成本 | `/dashboard/model-cost` |
| 治理 | 预算管理 | `/dashboard/budget` |
| 治理 | 优化中心 | `/dashboard/cost-optimization` |
| 治理 | 预警中心 | `/dashboard/cost-alerts` |
| 治理 | 系统保护策略 | `/dashboard/cost-protection` |
| 治理 | 价格管理 | `/dashboard/model-pricing` |
| 运维 | 请求追踪 | `/dashboard/ai-traces` |

旧路径 `/dashboard/ai-cost` → `/dashboard/cost`、`/dashboard/ai-requests` → `/dashboard/ai-traces` 已保留 redirect。

## 10. 治理功能演练脚本

`docs` 之外，项目 `samples/` 下有两个可重复执行的验证脚本，**用真实 Redis 占用 + 真实 MySQL 额度调整**触发分级，不是 Mock：

| 脚本 | 作用 | 副作用 |
|---|---|---|
| `samples/verify-admin-endpoints.sh` | 逐个调用 **34 个管理接口**（31 只读 + 3 操作）断言 `code=0`，最后打印保护等级 / 紧急开关 / 未恢复 CRITICAL 数 | **只读**（3 个 POST 中 `evaluate`/`anomaly-scan` 只做评估，`daily-summary/rebuild` 幂等重算汇总） |
| `samples/verify-alert-levels.sh` | 逐档触发 50/70/85/95/100% 阈值，验证告警等级、动作、去重、保护等级联动、超限请求被拒 | 临时把 `ai_budget_policy` 中 `SCENE/CHAT` 的 `daily_limit` 收紧到很小 |
| `samples/verify-alert-recovery.sh` | 验证告警自动恢复、紧急开关（缺原因拒绝 / 带原因停用 / 停用期阻断 / 恢复放行）、审计日志 | 结束时把额度恢复为 `2.00`，并解除紧急开关 |

```bash
# 前置：后端已在 8091 运行、MySQL 容器 healthy、Redis 可用
bash samples/verify-admin-endpoints.sh     # 建议每次改完后端先跑这个
bash samples/verify-alert-levels.sh        # 会改额度，按需执行
bash samples/verify-alert-recovery.sh      # 会改额度 + 开关，按需执行
```

两个脚本都会直接查 MySQL / Redis 打印中间状态。**执行前确认当前不是生产数据**；执行后如需还原，把 `ai_budget_policy` 的 `daily_limit` 改回期望值即可。

期望结果（本轮实测）：

```
80.00%  → 50.00% WARNING / NOTIFY  +  70.00% WARNING / NOTIFY
96.97%  → 85.00% HIGH / DOWNGRADE_MODEL  +  95.00% CRITICAL / DISABLE_AGENT
106.67% → 100.00% BLOCKED / BLOCK_REQUEST
连续 3 次 evaluate → 仍 5 行告警，occurrence_count 合计 5（不新增）
超限后 /api/ai/chat → code=40302
恢复后 5 档全部 RESOLVED、resolved_at 非空，/api/ai/chat → code=0
```

## 11. 常见故障

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 启动脚本提示 8091 被占用 | 其它进程或本项目残留实例占用 | 先看脚本打印的 `lsof`/`netstat` 结果；属本项目残留则 `stop.command -y`，否则换端口或停掉自己启动的进程，**不要 kill 别人的进程** |
| Flyway / 数据源连接失败 | MySQL 未起、端口写成 3306、库名 / 账号不符 | 确认 `docker compose -f ai-cost-compose.yml ps` healthy，连接串用 **13307**、库 `ai_cost` |
| 调用返回 `50301` | Redis 不可用，预算安全失败 | `redis-cli ping`；恢复 Redis 后重试；这是**预期保护行为**，不是放行 |
| 调用返回 `40301 预算不足（DAILY:demo-user）` | 日预算（默认 5.00 USD）被耗尽 | 通过 `/api/admin/budget` 调整或等待跨天分键自然重置；注意被拒账单也会落库 |
| 调用返回 `40302 保护等级 5：预算阻断或紧急开关生效` | 使用率 >100% 触发 `BLOCKED`，或紧急开关处于停用状态 | 查 `/api/admin/cost-protection/status`；若为紧急开关，调 `/cost-protection/resume` 恢复；若为预算用尽，调高额度后等 `@Scheduled` 扫描或手动 `POST /cost-alert/evaluate` |
| 紧急开关停用后仍报 `40001 必须填写停用原因` | `POST /emergency-stop` 未带 `reason` | 必须带 `{"reason":"..."}`，这是刻意的强制审计要求 |
| 恢复后保护等级停在 `SEVERE` 降不下来 | 历史版本的自锁缺陷 | 已修复（`ADR-002`）；若遇到旧库残留，调用 `resume` 会清理本引擎遗留的 `PROTECTION_ACTION` 告警 |
| `mvn compile` 报数百处「找不到符号 `getXxx()`」 | JDK 23+ 默认关闭注解处理，Lombok 静默失效 | 显式 `export JAVA_HOME=.../corretto-17.0.14/Contents/Home`，见 1.1 |
| `docker exec ... mysql` 中文显示为 `?????` | 容器内 mysql 客户端字符集 | 加 `--default-character-set=utf8mb4`；表和列确为 `utf8mb4`，不是数据问题 |
| `curl localhost` 连不上 | 本机 `HTTP_PROXY` 生效 | 加 `--noproxy '*'` |
| 管理接口 403 | 配置了 `AI_ADMIN_TOKEN` 但请求缺 `X-Admin-Token`，或非本机访问且未配置令牌 | 本机访问或补请求头；前端当前不自动注入该头 |
| `ai.provider=openai` 启动失败 | 未提供 `AI_API_KEY` | 配置环境变量，或改回默认 `mock` |
| `/api/rag/*` 404、问答无知识库上下文 | `rag.enabled=false`（默认） | 属预期降级；按第 8 节开启 RAG |
| 前端构建报 ECharts 大 chunk 警告 | 打包体积提示 | 构建仍成功，非错误；见测试报告 |
| 真实供应商调用 | `AI_PROVIDER=openai` + `AI_MODEL` + `AI_BASE_URL` + `AI_API_KEY`（Key 只走环境变量） | **已实测通过**（8092 端口实例、`usageStatus=REAL`、账单落库），见 5.1；若账单标 `UNPRICED`，说明价格表缺供应商返回的别名模型价格，需补价格版本（V3 已补 `deepseek-flash`） |
| 价格看起来不对 | DEMO-v1 全部 `demo_flag=1` | 示例价，非供应商真实报价；上线前按合同价替换并置 `demo_flag=0` |

## 12. 环境边界

- 身份为固定 `demo-user`（`DemoUserIdentityProvider`），未接入真实多用户认证；生产必须替换 `UserIdentityProvider` 实现。
- 价格为 DEMO 示例价（含 V3 别名模型）；真实供应商调用与真实 Usage 采集已通过验收，但成本仍按示例价计算，账单与预算均可正常演示，**不可用于真实财务结算 / 计费对账**。
- **前端已完成浏览器 DOM 级回归**（Chrome headless，6 个页面 `h1` / `loading` / 错误态 / 数据行数断言），但**未提供 Vitest 组件级测试**。
- 仍未完成：RAG 检索端到端（本轮未触碰 RAG）、Windows `.bat` 在 macOS 上的实测、性能压测、`DOWNGRADE_MODEL` 接真实模型验证降档后实际调用的是低价模型。
- Redis 与 MySQL 为**最终一致**（预算强一致、账单强一致、跨库最终一致 ≤5min），差异只告警不自动修正，见 `ADR-003`；按月分表未实现。
- 完整清单见 `docs/acceptance/FINAL_ACCEPTANCE_REPORT.md` 第 8 节。
