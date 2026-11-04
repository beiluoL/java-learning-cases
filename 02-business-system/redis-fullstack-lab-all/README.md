# Redis 全栈实战（Spring Boot 3 + Vue3 + Redis 7）

一个**克隆即跑、贴近生产**的全栈 Redis 实战教学项目。覆盖 **14 个 Redis 高并发与分布式实战场景**（10 个基础场景 + 4 个高并发进阶模块：秒杀库存 / 幂等处理 / AI 额度管理 / 延迟队列），每个场景都包含：完整可运行代码、项目目录结构、依赖配置与业务背景说明。

> 配套提示词（用于让 AI 生成本项目）：`../docs/redis-fullstack-project-prompt.md`

---

## 一、技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.4.x（Java 17） |
| 缓存 | Redis 客户端 Lettuce + Redisson | 7 / 3.30.x |
| ORM | MyBatis-Plus | 3.5.7 |
| 接口文档 | Knife4j（OpenAPI3） | 4.5.0 |
| 熔断 | Resilience4j | 2.2.0 |
| 数据库 | H2 内存库（默认）/ MySQL（可选） | — |
| 前端 | Vue 3 + Vite + TypeScript | 3.4 / 5.2 |
| 前端 UI | Element Plus + Axios + ECharts | — |

---

## 二、目录结构

```
redis-fullstack-lab-all/
├── docker-compose.yml          # Redis 7 + RedisInsight
├── README.md
├── start.command / stop.command  # macOS 一键启停（chmod +x）
├── start.bat / stop.bat         # Windows 一键启停
├── backend/                     # Spring Boot
│   ├── pom.xml
│   └── src/main/java/com/example/redislab/
│       ├── config/             # Redis/Redisson/Cors/Knife4j/PubSub 配置
│       ├── common/             # R 响应、全局异常、RedisKeys、StatService
│       ├── core/               # 锁抽象、限流注解+AOP、令牌桶、布隆过滤器
│       ├── entity/ mapper/ service/   # 商品/用户/库存 + MyBatis-Plus
│       ├── controller/         # DebugController（观测接口）
│       └── modules/            # 14 个场景模块
│           ├── cache/ session/ rank/ ratelimit/ pubsub/
│           ├── penetration/ breakdown/ avalanche/ lock/
│           └── seckill/ idempotent/ aiquota/ delayqueue/   # 高并发进阶 4 模块
│       └── resources/          # application.yml / application-mysql.yml / db/
└── frontend/                    # Vue3
    └── src/
        ├── api/redislab.ts      # 按模块封装的请求
        ├── components/          # DemoPanel / RedisStateCard
        ├── views/              # 13 个演练页（Lock 两实现共用一页）
        ├── store/ router/ utils/
```

---

## 三、快速开始

### 方式 A：一键脚本（推荐）

```bash
# macOS
chmod +x start.command stop.command
./start.command          # 启动 Redis + 后端 + 前端
./stop.command           # 精准停止（按 PID + 端口）

# Windows：双击 start.bat / stop.bat 即可
```

脚本会自动：`docker compose up -d` 起 Redis → 构建并后台运行后端 jar → `npm install` 并启动前端 vite。
启动后访问：
- 前端页面：http://localhost:5173
- 后端接口文档（Knife4j）：http://localhost:8080/doc.html

### 方式 B：手动

```bash
# 1. 起 Redis
docker compose up -d redis

# 2. 后端
cd backend
mvn spring-boot:run          # 默认 h2 profile；生产用 --spring.profiles.active=mysql

# 3. 前端
cd frontend
npm install
npm run dev
```

---

## 四、十四大场景速查

| # | 场景 | 后端路径 | Redis 结构 / 技术 |
|---|---|---|---|
| 1 | 缓存加速 | `modules/cache` | String + Cache-Aside + 自定义 `@RedisCacheable` |
| 2 | 分布式会话 | `modules/session` | Spring Session → Redis Hash |
| 3 | 排行榜 | `modules/rank` | ZSET（今日榜 + 总榜） |
| 4 | 限流控制 | `modules/ratelimit` | ZSET 滑动窗口（Lua）+ 固定窗口 + 令牌桶 |
| 5 | 发布订阅 | `modules/pubsub` | PUB/SUB channel + Stream 消费组 |
| 6 | 缓存穿透 | `modules/penetration` | 布隆过滤器 + 空值缓存 |
| 7 | 缓存击穿 | `modules/breakdown` | 互斥锁重建 / 逻辑过期 |
| 8 | 缓存雪崩 | `modules/avalanche` | TTL 随机抖动 + Caffeine 多级缓存 + 熔断 |
| 9 | 分布式锁（手写） | `modules/lock` | SET NX EX + Lua 解锁 + 看门狗 |
| 10 | 分布式锁（Redisson） | `modules/lock` | RLock（自动续期/可重入/RedLock） |
| 11 | 秒杀库存 | `modules/seckill` | Lua 原子扣减 + 用户限购 Set + List 异步削峰落库 + DB 唯一约束兜底 |
| 12 | 幂等处理 | `modules/idempotent` | Redis 一次性 Token（Lua）+ MySQL `biz_id` 唯一索引 |
| 13 | AI 额度管理 | `modules/aiquota` | 单 ZSET + Lua 双滑动窗口 RPM / TPM，超额降级 |
| 14 | 延迟队列 | `modules/delayqueue` | ZSet（score=到期时间）+ Lua 原子取出 + 重试/死信 |

> **高并发进阶 4 模块**补齐「异步 / 协调 / 一致性」维度：秒杀验证「Lua 原子扣减无超卖」、幂等验证「并发 20 次只有 1 次生效」、额度验证「RPM/TPM 双维度原子校验」、延迟队列验证「到期自动关单且支付不误关」。每个模块都提供 `/status` 结构化指标接口与独立「重置演示」能力（`StatService.resetByPrefix`）。

每个页面右上角的「Redis 实时观测」卡片，可看到 `cache:hit / ratelimit:blocked / penetration:bloom_block / lock:deduct_ok / seckill:success / aiquota:tpm_block / delayqueue:closed` 等计数，让效果肉眼可见。

---

## 五、Redis 原生命令示例（配合调试）

```bash
# 查缓存
GET cache:product:1
# 排行榜
ZREVRANGE rank:day:20261104 0 9 WITHSCORES
# 限流窗口
ZRANGE ratelimit:window:order 0 -1 WITHSCORES
# 会话
KEYS spring:session:sessions:*
# 分布式锁
SET lock:stock:1 <uuid> NX EX 30
# 发布订阅
SUBSCRIBE pubsub:demo
# 流
XREADGROUP GROUP cg c1 COUNT 10 STREAMS stream:demo >
# 秒杀库存 / 限购集合 / 异步削峰队列
GET seckill:stock:1
SMEMBERS seckill:users:1
LRANGE seckill:pending:orders 0 -1
# 幂等 token
KEYS idempotent:*
# AI 额度窗口（member = uuid|tokens）
ZRANGE aiquota:window:u1 0 -1 WITHSCORES
# 延迟队列（score 即到期时间戳）
ZRANGE delayqueue:pending 0 -1 WITHSCORES
```

---

## 六、面试要点

- **缓存三件套**：穿透（布隆/空值）、击穿（互斥锁/逻辑过期）、雪崩（随机 TTL/多级缓存/高可用）。
- **Cache-Aside 为什么「删」而非「更新」缓存**？并发下更新缓存会出现写序错乱，删除后下次读自动重建，最终一致且简单。
- **分布式锁四要素**：互斥、不死锁（过期）、不误删他人锁（UUID+Lua）、可重入/自动续期（Redisson 看门狗）。
- **限流方案选型**：固定窗口（简单但有临界点双倍）、滑动窗口（精确）、令牌桶（支持突发）。
- **Session 共享**：Spring Session 把 HttpSession 序列化进 Redis，多实例天然共享。
- **秒杀为什么不超卖**：整段「限购校验 → 库存校验 → 扣减 → 入队」由 Lua 在 Redis 单线程原子执行；DB 唯一约束再兜一层；成功请求走 List 异步削峰，秒杀瞬间不打 DB。
- **幂等为什么双保险**：Redis token 只挡「同一 token」，挡不住「换 token 但同业务单号」，必须靠 MySQL `biz_id` 唯一索引。
- **RPM/TPM 为什么要单 Lua**：拆成两个计数器分别判断，并发下会出现「A 过了 RPM、B 过了 TPM」的不一致；单 ZSET + 单脚本一次原子判定。
- **延迟队列为什么要 Lua 取出**：「先 ZRANGEBYSCORE 再逐个 ZREM」在多消费者下会重复关单；用 Lua 一次完成「查到期 + 全部移除」。

> 想要更系统、按「常见面试题 + 标准答案」组织的版本（架构 / 选型 / 十四场景实现 / 高并发难点 / 模块缺口审计与优化路线 / 真实压测数据），见同级 **[INTERVIEW.md](./INTERVIEW.md)**。

---

## 七、说明

- 默认使用 H2 内存库（克隆即跑）；切 MySQL 修改 `application-mysql.yml` 的连接信息并用 `--spring.profiles.active=mysql`。
- 后端序列化统一用 `GenericJackson2JsonRedisSerializer`（带类名，防反序列化失败）；Key 用 String。
- 生产环境请为 Redis 设置密码、开启哨兵/集群，并为 DebugController 增加权限控制。
