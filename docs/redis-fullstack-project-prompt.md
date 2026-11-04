# 📌 使用方式（本段不是提示词正文，复制时请从下方分隔线开始）

把下面 `========== 提示词正文 ==========` 之后的全部内容，整体粘贴给任意 AI 编程助手（含 WorkBuddy / Cursor / Claude / ChatGPT 等），它即可据此生成一个**结构完整、克隆即跑、生产级可用的 Spring Boot + Vue3 + Redis 全栈实战项目**。

需求方画像（已写入提示词，AI 会自动按此风格交付）：Java 后端开发者，正在系统学习前端（Vue3），偏好讲师式、贴近生产的最佳实践，反感玩具 demo。

---

========== 提示词正文（以下内容整体复制给 AI） ==========

# 任务：生成一个生产级 Spring Boot + Vue3 + Redis 全栈实战项目

你是一名拥有 10 年经验的资深全栈架构师。请基于以下完整规格，创建一个**结构完整、可直接运行（clone-and-run）、代码贴近生产环境**的 Spring Boot 后端 + Vue3 前端 + Redis 全栈实战教学项目。所有代码必须真实可用，禁止占位符、禁止 TODO 留白、禁止"此处省略"。

---

## 一、技术栈与版本（严格锁定）

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Spring Boot | 3.4.x（Java 17） |
| 后端 | Redis 客户端 | Lettuce（spring-boot-starter-data-redis，开启 commons-pool2 连接池） |
| 后端 | ORM | MyBatis-Plus 3.5.x（或 Spring Data JPA，二选一，全文统一） |
| 后端 | 分布式锁 | Redisson 3.30.x（同时手写 SET NX EX 实现对比） |
| 后端 | 接口文档 | Knife4j 4.x（OpenAPI 3） |
| 后端 | 数据库 | H2 内存库（默认 profile，克隆即跑）；预留 MySQL profile |
| 前端 | 框架 | Vue 3 + Vite + TypeScript |
| 前端 | UI | Element Plus + Axios |
| 前端 | 图表 | ECharts（排行榜/限流可视化） |
| 基础设施 | 容器 | Docker Compose 启动 Redis 7（开启 AOF + 持久化） |

## 二、项目目录结构（必须严格遵循）

采用 `backend/` + `frontend/` 分离式结构（若置于已有工作区，根目录建议为 `<项目名>-all/`）：

```
redis-fullstack-lab/
├── docker-compose.yml                 # Redis 7 + 可选 RedisInsight
├── README.md                          # 每个场景的演示步骤、接口清单、Redis 命令示例
├── start.command / stop.command       # macOS 一键启动/停止（chmod +x）
├── start.bat / stop.bat               # Windows 一键启动/停止（chcp 65001 设 UTF-8）
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/example/redislab/
│       ├── RedisLabApplication.java
│       ├── config/                    # RedisConfig(序列化/连接池)、RedissonConfig、CorsConfig、Knife4jConfig
│       ├── common/                    # 统一响应 R、全局异常、常量、RedisKey 工具
│       ├── core/                      # 通用能力：分布式锁抽象、限流注解、BloomFilter 封装
│       └── modules/
│           ├── cache/                 # 场景1 缓存加速
│           ├── session/               # 场景2 分布式会话
│           ├── rank/                  # 场景3 排行榜
│           ├── ratelimit/             # 场景4 限流控制
│           ├── pubsub/                # 场景5 发布订阅
│           ├── penetration/           # 场景6 缓存穿透
│           ├── breakdown/             # 场景7 缓存击穿
│           ├── avalanche/             # 场景8 缓存雪崩
│           └── lock/                  # 场景9 + 10 分布式锁（手写 & Redisson）
│       └── resources/
│           ├── application.yml        # 默认 h2 profile
│           ├── application-mysql.yml  # 可选 mysql profile
│           └── db/                    # 建表 SQL + 演示种子数据
└── frontend/
    ├── package.json / vite.config.ts / tsconfig.json
    └── src/
        ├── api/redislab.ts            # 按模块封装的 Axios 请求
        ├── router/index.ts
        ├── views/                     # 每个场景一个独立页面
        │   ├── CacheView.vue
        │   ├── SessionView.vue
        │   ├── RankView.vue
        │   ├── RateLimitView.vue
        │   ├── PubSubView.vue
        │   ├── PenetrationView.vue
        │   ├── BreakdownView.vue
        │   ├── AvalancheView.vue
        │   └── LockView.vue
        ├── components/                # 复用组件（演示面板、Redis 状态卡）
        ├── store/                     # Pinia（会话/登录态）
        └── utils/request.ts           # Axios 实例（携带 cookie、拦截器）
```

## 三、全局工程要求（生产级，缺一不可）

1. **Redis 序列化**：Key 用 `StringRedisSerializer`；Value/Hash 用 `GenericJackson2JsonRedisSerializer`（带类名，防反序列化失败）；禁止 JDK 原生序列化。
2. **连接池**：Lettuce 配置 `max-active`/`max-idle`/`min-idle`/`timeout`，附注释说明调优依据。
3. **统一响应**：`R<T>` 封装 `{code, msg, data}`，全局异常处理器 `@RestControllerAdvice` 兜底。
4. **分布式锁抽象**：`LockService` 接口 + `RedisLockServiceImpl`（手写 SET NX EX + Lua 解锁 + 看门狗续期）+ `RedissonLockServiceImpl`，用策略模式切换，便于教学对比。
5. **前端工程化**：Vite 代理 `/api` → `http://localhost:8080`；`vite.config.ts` 配置跨域代理隐藏后端地址；Axios 开启 `withCredentials` 配合分布式会话。
6. **一键启动脚本**（必须生成，符合跨平台双击运行约定）：
   - `start.command`/`start.bat`：先 `docker compose up -d` 起 Redis，再启动 backend（Maven），最后启动 frontend（`npm install && npm run dev`），窗口常驻打印访问地址（前端 `http://localhost:5173`，后端 `http://localhost:8080/doc.html`）。
   - `stop.command`/`stop.bat`：按进程 PID 精准关停本项目前后端与 Redis 容器，**禁止宽泛 pkill/kill 误伤**，无运行时友好提示"无正在运行的服务"。
   - 脚本自包含、相互独立，幂等可重复执行。
7. **可观测性**：每个场景接口打印关键 Redis 命令与耗时；前端页面展示 Redis 当前 key/值（通过后端调试接口），让"缓存命中/未命中"肉眼可见。
8. **README**：每个场景给出①业务背景 ②Redis 数据结构选型与理由 ③演示步骤 ④关键 Redis 原生命令示例 ⑤常见坑与面试要点。

## 四、十大业务场景规格（每个都要有完整后端代码 + 前端页面 + 依赖 + 业务背景）

### 场景1：缓存加速（Cache-Aside）
- **背景**：商品详情页高并发读，直接打 DB 扛不住。
- **选型**：String（JSON）缓存热点对象；Hash 缓存对象多字段。
- **实现要点**：① 读：`cacheAsideGet(id)` 先查缓存，未命中查 DB 并回填（带随机 TTL 防雪崩）；② 写：更新 DB 后删除缓存（Cache-Aside 双写一致性，说明为什么"删"而非"更"）；③ 提供 `@RedisCacheable(key, ttl)` 自定义注解 + AOP 实现，对比 Spring `@Cacheable`。
- **交付**：`CacheController` 提供 get/update/delete 接口；`CacheView.vue` 展示命中率与缓存内容。

### 场景2：分布式会话（Session 共享）
- **背景**：多实例部署下，用户登录态不能绑定单机 Tomcat。
- **选型**：Spring Session Data Redis，会话存入 Redis（Hash 结构 `spring:session:sessions:*`）。
- **实现要点**：`@EnableRedisHttpSession(maxInactiveIntervalInSeconds=1800)`；登录接口写 Session，前端 Axios `withCredentials`；演示"停掉一个后端实例，另一个实例仍能识别登录态"。
- **交付**：`SessionController`（login/logout/me）；`SessionView.vue` 登录后展示当前 Session 在 Redis 中的原始结构。

### 场景3：排行榜（ZSET）
- **背景**：游戏积分榜 / 商品销量榜 / 热搜榜，需实时排序且高并发更新。
- **选型**：`ZSET`（score=分数/销量，member=用户/商品 id）。
- **实现要点**：`ZADD`/`ZINCRBY` 加分；`ZREVRANGE` 取 TopN；`ZRANK` 查名次；实现"今日榜 + 总榜"（按天 key `rank:day:20261104` + 总榜 `rank:total`，每日批处理汇总）。
- **交付**：`RankController`（加分/查榜/查名次）；`RankView.vue` 用 ECharts 柱状图展示 Top10 + 输入自己的分数实时看排名。

### 场景4：限流控制（滑动窗口）
- **背景**：防刷、保护接口与下游，秒杀/登录接口必备。
- **选型**：`ZSET` 滑动窗口（score=当前毫秒时间戳，member=唯一 UUID，范围查询计数）为主；并附 `String` 固定窗口（`INCR`+`EXPIRE`）与令牌桶（Lua）两种实现作为对比。
- **实现要点**：`@RateLimit(key, qps, window)` 注解 + 拦截器/AOP；滑动窗口核心：`ZREMRANGEBYSCORE` 清过期 + `ZCARD` 计数 + `ZADD` 记录，整体用 Lua 保证原子性。
- **交付**：`RateLimitController`（一个被限流的接口 + 一个探测接口显示剩余配额）；`RateLimitView.vue` 可拖动 QPS 阈值、实时看到被拦截/放行的请求数。

### 场景5：发布订阅（Pub/Sub + Stream 对比）
- **背景**：群聊通知、订单状态广播、配置热更新。
- **选型**：`PUB/SUB` channel（演示实时广播）+ `STREAM`（演示可靠消息队列，带消费组 `XGROUP`）对比。
- **实现要点**：`RedisTemplate.convertAndSend` 发消息；`RedisMessageListenerContainer` + `MessageListenerAdapter` 收消息；Stream 用 `XADD`/`XREADGROUP` + 消费组，演示消息不丢。
- **交付**：`PubSubController`（publish）；后端 `@Component` 监听者把消息推给前端（SSE 或 WebSocket）；`PubSubView.vue` 双窗口模拟 A 发 B 收的实时聊天/通知。

### 场景6：缓存穿透（Bloom Filter + 空值缓存）
- **背景**：恶意刷不存在的 id（如 `-1`、超长随机串），请求绕过缓存直击 DB。
- **选型**：`Redisson RBloomFilter`（预热所有合法 id）+ `String` 缓存空对象（短 TTL）。
- **实现要点**：① 启动时把合法 id 批量放入布隆过滤器；② 查询先过布隆（不存在直接返回，绝不查 DB）；③ 布隆通过但 DB 无数据 → 缓存空值 `null` 防重复穿透；④ 说明布隆过滤器的误判率与重建策略。
- **交付**：`PenetrationController`（正常查询 + 一个"模拟攻击"按钮批量打不存在 id）；`PenetrationView.vue` 展示 DB 命中次数 vs 被布隆拦截次数。

### 场景7：缓存击穿（热点 Key 重建）
- **背景**：某个爆款 key 过期瞬间，海量并发同时回源 DB，把 DB 打挂。
- **选型**：`String` + 互斥锁重建 / 逻辑过期两种方案。
- **实现要点**：① **互斥锁方案**：缓存未命中时 `SETNX` 抢锁，抢到者查 DB 回填并释放锁，没抢到者短暂自旋等待；② **逻辑过期方案**：value 内嵌 `expire` 时间戳，不真过期，发现逻辑过期后异步线程重建（非阻塞），演示两者取舍。
- **交付**：`BreakdownController`（getHot/{id}，可手动让热点 key "过期"触发并发重建）；`BreakdownView.vue` 用并发计数器展示"只回源 1 次"。

### 场景8：缓存雪崩（集中失效）
- **背景**：大量 key 同一时刻过期，或 Redis 宕机，请求全部压到 DB。
- **选型**：`String` + TTL 随机抖动 + 多级缓存（本地 Caffeine + Redis）+ 熔断降级。
- **实现要点**：① 基础 TTL = 固定值 + `ThreadLocalRandom` 随机增量，打散失效；② 本地 Caffeine 一级缓存扛瞬时洪峰；③ Resilience4j 熔断：Redis 不可用时降级返回兜底数据；④ 说明 Redis 高可用（哨兵/集群）是根本解法。
- **交付**：`AvalancheController`（批量预热 + 一键"让全部 key 同时过期"演示雪崩，再演示加随机 TTL 后平滑）；`AvalancheView.vue` 折线图对比"雪崩 vs 防护"下的 DB QPS 峰值。

### 场景9：分布式锁（手写 SET NX EX）
- **背景**：集群下抢券、扣库存，单机锁失效，需跨进程互斥。
- **选型**：`SET key uuid NX EX 30`（原子加锁）+ Lua 脚本原子解锁。
- **实现要点**：① 加锁用单条 `SET NX EX`，value=UUID 防误删他人锁；② 解锁用 Lua（`if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) end`）；③ 看门狗：后台定时线程在锁快过期时续期（可重入需记录持有计数）；④ 完整演示"误删他人锁""未续期导致锁提前释放"两个坑。
- **交付**：`LockController`（/deductStock 并发扣库存，用 JMeter/前端并发按钮压测）；`LockView.vue` 显示库存从 100 扣到 0 无超卖。

### 场景10：基于 Redisson 的分布式锁（生产首选）
- **背景**：手写锁在可重入、自动续期、公平、RedLock 多节点上有短板，Redisson 是事实标准。
- **选型**：Redisson `RLock`（可重入）、`RedissonRedLock`（多 master）、`RFairLock`。
- **实现要点**：① `RLock.lock()` 自动看门狗续期；② `tryLock(wait, lease)` 带等待与租约；③ 可重入计数；④ 对比手写锁，说明 Redisson 如何解决"续期""可重入""宕机死锁"。
- **交付**：与场景9 共用 `LockView.vue` 的"切换锁实现"开关，同一扣库存业务分别用手写锁与 Redisson 跑，呈现一致正确性但不同健壮性。

## 五、交付验收标准（AI 自检，全绿才算完成）

- [ ] `docker compose up -d` 后，后端 `mvn spring-boot:run` 无报错，Knife4j 文档可打开。
- [ ] 前端 `npm install && npm run dev` 可访问，10 个页面均能演示对应场景。
- [ ] 每个场景：有业务背景说明、有完整后端实现、有前端交互页、有 README 演示步骤。
- [ ] 10 个场景全部可独立运行并肉眼验证效果（命中率、排名、限流拦截、无超卖等）。
- [ ] 一键启动/停止脚本在 macOS 与 Windows 均可双击运行，且 stop 不误杀其他进程。
- [ ] 代码无 TODO、无占位、无编译错误；序列化、连接池、异常处理均已配置。
- [ ] README 含：技术栈表、目录树、10 场景速查表、Redis 原生命令示例、面试要点。

## 六、输出要求

1. 先输出**完整项目目录树**与**技术决策说明**（1 屏内）。
2. 再**按模块逐个**生成完整代码文件（每个文件内容完整，可直接落地）。
3. 最后给出 **README 模板**与**一键脚本**，并说明如何本地运行验证。
4. 全程使用中文注释 + 中文说明，关键代码段附"为什么这么做"的简短原理注解（讲师式）。

========== 提示词正文结束 ==========
