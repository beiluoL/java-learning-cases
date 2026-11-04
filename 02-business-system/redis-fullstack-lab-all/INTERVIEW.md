# Redis 全栈实战 · 面试问答版

> 配套于 `README.md`（快速上手 / 原生命令 / 场景速查）。本文件以 **常见面试题 + 标准答案** 的形式，系统梳理本项目的架构、技术选型、核心实现与高并发难点攻关，便于快速回顾与对外展示。
>
> 建议阅读顺序：**一（总览）→ 三（场景逐题）→ 四（难点专题）→ 五（压测实证）**。所有答案均对应仓库内真实代码，括号内给出关键文件路径，可边看边跳转源码。

---

## 一、项目架构与整体设计

### Q1：请一句话介绍这个项目，并画出整体架构。

**A：** 一个「克隆即跑、贴近生产」的 **Spring Boot 3 + Vue3 + Redis 7** 全栈实战项目，覆盖主流业务里最常见的 **10 个 Redis 真实场景**（缓存加速、分布式会话、排行榜、限流、发布订阅、缓存穿透/击穿/雪崩、手写/Redisson 分布式锁）。每个场景都提供「可运行后端接口 + 可交互前端演练页 + 实时观测卡片」，让 Redis 效果**肉眼可见**而非停留在概念。

分层架构：

```
┌─────────────────────────┐         ┌──────────────────────────┐
│  前端 Vue3 + Vite + TS    │  /api   │  后端 Spring Boot 3 (Java17) │
│  (5173, 9 个演练页)       │ ──────▶ │  Controller → Service       │
│  Element Plus + ECharts   │ 代理    │  core/(锁/限流/布隆/缓存切面) │
│  SSE 实时推送             │ ◀────── │  modules/(10 场景)          │
└─────────────────────────┘         └────────────┬─────────────┘
                                                  │ Lettuce(模板) / Redisson(锁·布隆)
                                                  ▼
                                        ┌──────────────────┐
                                        │  Redis 7 (6379)   │  ← docker-compose 一键起
                                        │  + RedisInsight   │
                                        └──────────────────┘
                                                  ▲
                                H2 内存库(默认) / MySQL(可选) ← MyBatis-Plus
```

### Q2：前后端是如何联通的？联调时踩过什么坑？

**A：** 联通链路分三层：

1. **协议与路径**：前端统一走 `axios` 封装的 `request.ts`，所有请求加前缀 `/api`；后端 Controller 全部挂在 `/api/**` 下（`@RequestMapping("/api/ratelimit")` 等）。
2. **开发期代理（Vite）**：前端 Vite dev server 在 `5173`，通过 `vite.config` 的 `server.proxy` 把 `/api` 反向代理到 `http://localhost:8080`，所以浏览器访问的是同源 `5173`，**天然规避跨域**做日常联调。
3. **生产/真实跨域（CORS）**：`config/CorsConfig.java` 显式放行前端开发源；若前后端不同源部署，靠它而非代理。

**联调时真实踩坑（已修复，值得讲）：**
- **限流返回 HTTP 200 而非 429**：`@RestControllerAdvice` 里返回 `R.fail(429, ...)` 默认仍是 HTTP 200，压测脚本只能靠业务码判断，极易误判。修复办法是给三个异常处理器加 `@ResponseStatus`（限流→`TOO_MANY_REQUESTS`、参数错→`BAD_REQUEST`、兜底→`INTERNAL_SERVER_ERROR`），让 **HTTP 状态码与业务码一致**（`common/GlobalExceptionHandler.java`）。
- **端口被环境变量覆盖**：本机存在 harness 注入的 `SERVER__PORT=51520`，经 Spring relaxed binding 覆盖 `server.port: 8080`；用 `--server.port=8080` 命令行参数（最高优先级）压回 8080。正常用户环境无此变量，不影响。
- **SSE 实时推送**：`/api/pubsub/stream` 用 SSE 把 Redis PUB/SUB 收到的消息实时推给前端，`RedisSubscriber` 监听到频道消息后通过 `MessageBroadcaster` 广播——实测前端能在发布瞬间收到 `event:msg`。

---

## 二、技术选型与理由

### Q3：为什么用 Spring Boot 3 + Java 17？和其他版本有什么坑？

**A：** 两个硬理由 + 一个坑：
- **Java 17 是 LTS**，语言特性（record、密封类、文本块）写起来更简洁；Spring Boot 3 **最低要求 Java 17**。
- **Spring Boot 3 全面转向 Jakarta**：包名从 `javax.*` 变成 `jakarta.*`。这是一个**真实踩坑点**——旧版 Knife4j 依赖 `javax.servlet`，在 Boot 3 下启动直接 `NoClassDefFoundError: javax/servlet/Filter`。本项目必须用 **`knife4j-openapi3-jakarta-spring-boot-starter`**（jakarta 变种）才能起来。

### Q4：Redis 客户端为什么同时用 Lettuce 和 Redisson？

**A：** 各取所长、不重复造轮子：
- **Lettuce**（Spring Data Redis 默认）：基于 Netty 的响应式、线程安全连接，适合做**通用模板操作**（String/Hash/ZSET/Stream、Lua 脚本执行）。本项目 `RedisTemplate`、所有 `@RedisCacheable`、限流 Lua、Stream 都用它。
- **Redisson**：提供了**开箱即用的分布式对象**——`RLock`（自动看门狗续期、可重入、RedLock）、`RBloomFilter`（基于 Bitmap 的布隆过滤器）。手写这些既容易出 bug 又啰嗦，所以分布式锁和布隆过滤器直接用它（`config/RedissonConfig.java`）。

> 面试加分点：两者**共享同一个 Redis 连接**，但 Redisson 自己维护连接池，配置上要注意不和 Lettuce 的 pool 设置打架。

### Q5：为什么默认用 H2 内存库？为什么用 MyBatis-Plus？

**A：**
- **H2 默认库**是为了「克隆即跑」——不用装 MySQL 就能把项目跑起来看效果；切生产只需 `application-mysql.yml` + `--spring.profiles.active=mysql`。教学项目把"环境门槛"降到最低。
- **MyBatis-Plus** 比裸 MyBatis 少写大量 CRUD XML，`ProductMapper.selectById(id)` 这种单表操作零 SQL；同时保留写复杂 SQL 的能力，平衡了"快"和"可控"。

### Q6：你的限流和缓存注解是怎么落地的？为什么不直接用现成框架？

**A：** 用 **Spring AOP + 自定义注解**，手写目的是"把原理讲透"：

- **缓存**：`@RedisCacheable(key, ttl)` + `RedisCacheableAspect`（Around 切面）——先查 Redis，命中直接返回；未命中执行原方法并写回（`core/RedisCacheableAspect.java`）。这就是 Spring `@Cacheable` 的"手写复刻"，让学习者看清切面机制。
- **限流**：`@RateLimit(key, qps, windowSeconds)` + `RateLimitAspect`——拦截到方法就调 `SlidingWindowRateLimiter.tryAcquire`，被限流抛 `RateLimitException`，再由全局异常处理器映射成 429（`core/ratelimit/RateLimitAspect.java` + `common/GlobalExceptionHandler.java`）。

> 强调：生产可以直接用 Spring `@Cacheable` + Redis、或 GateWay/Sentinel 限流；本项目"手写"是**教学取舍**，不是反对用现成方案。

---

## 三、十大场景核心实现（逐题）

> 每题结构：**业务问题 → 技术方案 → 关键代码点 → 易错点**。

### 场景 1 · 缓存加速（Cache-Aside）

**Q：为什么用 Cache-Aside（旁路缓存）而不是 Read/Write-Through？更新时为什么"删缓存"而非"更新缓存"？**

**A：**
- **Cache-Aside 模式**：读时先查缓存，未命中回源 DB 并回填；写时**先更新 DB，再删除缓存**（本项目 `@RedisCacheable` 是读路径，写路径配合删除/过期）。
- **为什么删而非更新**：并发下"更新缓存"会出现写序错乱（线程 A 写 DB→线程 B 写 DB→B 写缓存→A 写缓存，最终缓存是 A 的旧值）。而"删除缓存"让下一次读自动重建，最终一致且实现简单。代价是删除后短暂读穿透一次 DB，可用延迟双删/订阅 binlog 优化。

**关键代码**：`core/RedisCacheableAspect.java` 的 `around`：命中 `redisTemplate.opsForValue().get(key)` 直接返回；未命中 `pjp.proceed()` 后 `set(key, result, ttl)`。

---

### 场景 2 · 分布式会话（Spring Session）

**Q：多实例部署下，登录态怎么共享？**

**A：** 引入 `spring-session-data-redis` 后，HttpSession 的读写被**透明地重定向到 Redis**（存为 Hash：`spring:session:sessions:<sid>`）。任意实例都能读到同一份会话，天然支持水平扩展。本项目 `modules/session/SessionController.java` 提供 `login/me/logout`，前端登录后刷新即可看到会话落到 Redis。

**易错点（真实踩坑）**：Spring Session 把会话属性**默认用 Java 原生序列化**进 Redis，实体必须 `implements Serializable`，否则 `login` 直接 `Cannot serialize`。本项目的 `AppUser` 已加 `implements Serializable`。

---

### 场景 3 · 排行榜（ZSET）

**Q：实时排行榜用 Redis 什么结构？怎么保证又快又准？**

**A：** **Sorted Set（ZSET）**。`ZADD` 加分、`ZREVRANGE key 0 9 WITHSCORES` 取 Top10、`ZINCRBY` 增量更新——全部 O(log N)，百万级数据毫秒出榜。本项目 `modules/rank/RankController.java` 维护「今日榜 + 总榜」两个 ZSET，演练页可实时看到分数变动与排名。布隆/限流之外的**第三类高频结构**。

---

### 场景 4 · 限流控制（三种算法对比）

**Q：限流有哪几种算法？你项目里实现了哪些、各用在什么场景？**

**A：** 本项目实现并对比了三种：

| 算法 | 实现 | 特点 / 适用 | 代码 |
|---|---|---|---|
| 固定窗口 | `INCR + EXPIRE` | 简单，但**窗口临界点会双倍放行**（如 0.9s 和 1.1s 各放 100 但都在 1s 窗口内） | `RateLimitController.fixed` |
| 滑动窗口 | **ZSET + Lua** | 精确统计「最近 N 秒」，无临界点问题；本项目 `@RateLimit` 默认用它 | `core/ratelimit/SlidingWindowRateLimiter.java` |
| 令牌桶 | **Hash + Lua** | 按固定速率发令牌，**能容忍瞬时突发**（桶里积攒的令牌），适合"平时低频、偶发尖峰" | `core/ratelimit/TokenBucketRateLimiter.java` |

**关键代码点（滑动窗口 Lua）**：`ZREMRANGEBYSCORE` 清掉窗口外记录 → `ZCARD` 计数 → `>= limit` 返回 0（拦截）否则 `ZADD`+`PEXPIRE` 放行。整段用 Lua 在 Redis 单线程内原子执行，**避免并发竞态**。

> 面试追问：固定窗口的"双倍放行"具体怎么发生的？答：窗口是 [0,1s)、[1s,2s)… 若限制 100/s，攻击者可在 0.99s 打满 100、1.01s 又打满 100，这 200 个请求其实在 0.01s 内都"看起来合法"。滑动窗口用"当前时刻往前推 1s"的连续窗口，杜绝此问题。

---

### 场景 5 · 发布订阅（PUB/SUB + Stream + SSE）

**Q：Redis 做消息队列有几种方式？你为什么同时用了 PUB/SUB 和 Stream？**

**A：**
- **PUB/SUB**：`convertAndSend` 实时广播，但**无持久化、无消费者就丢**——适合"在线通知"这类可丢的场景。
- **Stream + 消费组**：`XGROUP CREATE` + `XREADGROUP` + `XACK`，**可靠、可重放、能 ack、支持多消费者负载均衡**，是真·消息队列。本项目 `publish` 同时写频道（实时）和 Stream（可靠），`consume` 用消费组 `cg/c1` 读并 ack（`modules/pubsub/PubSubController.java`）。
- **前端实时**：`RedisSubscriber` 监听到 PUB/SUB 消息后，通过 `SseController` 的 SSE 通道 `/api/pubsub/stream` 推给浏览器。

**易错点（真实踩坑）**：`XGROUP CREATE` 在 Stream 不存在时会报 `NOGROUP`。修复：用带 `ReadOffset` 的重载 `createGroup(channel, ReadOffset.from("0"), GROUP)`，等价 `XGROUP CREATE ... MKSTREAM`，**Stream 不存在也一并创建**，消除"先启动后发布"的 NOGROUP 500。

---

### 场景 6 · 缓存穿透（布隆过滤器 + 空值缓存）

**Q：缓存穿透是什么？你用什么方案防？**

**A：** 穿透 = 查询**根本不存在**的数据（恶意刷 `-1`、超长随机串），缓存和 DB 都没有，每次都打到 DB。两道防线：

1. **布隆过滤器（第一道，拦截绝大多数）**：预热所有合法 id 进 `RBloomFilter`，查询前先问"存不存在"——`false` 一定不存在，直接返回，**绝不查 DB**（`core/bloom/BloomFilterService.java`）。注意它有**误判率 fpp**（本项目 `tryInit(1000, 0.01)`），可能把不存在的判为存在，但**绝不会把存在的判为不存在**。
2. **空值缓存（第二道）**：布隆说"可能存在"但 DB 真没有时，缓存一个 `__NULL__` 标记（短 TTL 60s），避免同一非法 key 反复回源（`modules/penetration/PenetrationService.java`）。

> 数据印证：`attack(1000)` 用负数 id 批量打，统计显示 `penetration:bloom_block` ≈ 1000、`penetration:db` ≈ 0——请求基本被布隆拦在 DB 之外。

---

### 场景 7 · 缓存击穿（互斥锁 / 逻辑过期）

**Q：击穿和穿透有什么区别？怎么解？**

**A：** **击穿 ≠ 穿透**：击穿是**某个爆款热点 key 过期的瞬间**，海量并发同时发现缓存没了，一起回源把 DB 打挂（数据是存在的，只是缓存刚好失效）。核心是"**只允许一个线程去重建缓存**"。本项目给两种方案（`modules/breakdown/BreakdownService.java`）：

- **方案 A 互斥锁重建**：未命中抢分布式锁，抢到的回源，其余**自旋短暂等待**缓存被重建。简单直接，但重建期间请求被阻塞。
- **方案 B 逻辑过期**：value 内嵌过期时间戳，key **不真过期**；发现逻辑过期后**异步线程重建**，当前请求**返回旧值（不阻塞）**。体验更好，但实现复杂、有短暂脏读。

> 观测点：并发打 `getHotMutex` 时，`breakdown:mutex:rebuild` 计数应≈1（说明只有一个线程真正回源），证明锁生效。

---

### 场景 8 · 缓存雪崩（随机 TTL + 多级缓存 + 熔断）

**Q：雪崩和击穿又有什么区别？你的"组合拳"是什么？**

**A：** **雪崩 = 大量 key 在同一时刻集体失效（或 Redis 宕机）**，瞬时请求全压 DB。本项目三招组合（`modules/avalanche/AvalancheService.java`）：

1. **TTL 随机抖动**：过期时间 = 基础值 + 随机增量（`RedisKeys.ttlWithJitter(300, 60)`），把失效时刻**打散**，避免集体过期。
2. **多级缓存**：本地 **Caffeine（L1，纳秒级）+ Redis（L2）**，绝大多数请求在进程内命中，连 Redis 都不碰，扛住瞬时洪峰。
3. **熔断降级**：Redis 不可用时，Resilience4j 熔断直接返回**兜底数据**（`fallbackProduct`），保护 DB 不被冲垮。

**对照实验**：`loadProtected`（带防护）vs `loadUnprotected`（固定 TTL、无熔断）——后者在"同时失效"时会让大量请求同时落到 `avalanche:db`，前者被 L1/L2 吸收。

---

### 场景 9 · 分布式锁（手写）

**Q：手写一个生产可用的 Redis 分布式锁，要考虑哪几件事？**

**A：** 四要素，本项目 `core/lock/RedisLockServiceImpl.java` 全部覆盖：

1. **互斥**：加锁用**单条** `SET key uuid NX EX lease`——原子操作，避免"先 `SETNX` 再 `EXPIRE`"两步之间进程崩溃导致死锁。
2. **不死锁（过期时间）**：`EX` 保证即使持锁进程挂了，锁也会自动释放。
3. **不误删他人锁**：value 用 **UUID**，解锁用 **Lua**（`if get(key)==token then del(key)`），只有持有者能删，杜绝 A 删了 B 的锁。
4. **不提前过期（看门狗）**：后台定时线程在锁过期前 1/3 周期用 Lua 续期（`pexpire`），**防止业务没跑完锁就没了**（Redisson 也是这套思路）。

> 易错点：解锁**必须走 Lua**，不能在应用层先 `get` 再 `del`——那是两步，并发下会误删。

### 场景 10 · 分布式锁（Redisson，生产首选）

**Q：有了手写锁，为什么还要 Redisson？**

**A：** 手写锁的痛点 Redisson 全包了（`core/lock/RedissonLockServiceImpl.java`）：
- **自动看门狗续期**：`tryLock` 不传 lease 默认 30s，业务未结束前自动续（手写要自己写线程）。
- **可重入**：同一线程多次加锁计数 +1，解锁需对应次数（`isHeldByCurrentThread` 校验）。
- **高可用**：支持 **RedLock**（多 master 加锁），单机 Redis 宕机也不易死锁。

本项目 `LockController.deduct` 通过 `impl=redis|redisson` 切换两种实现，共用同一套扣库存逻辑——并发压测时两者都做到"库存 100→0 无超卖"，但健壮性 Redisson 更优。

---

## 四、高并发难点攻关（专题）

### 专题 1：缓存三件套一句话区分

| 问题 | 触发 | 解法 | 本项目位置 |
|---|---|---|---|
| **穿透** | 查**不存在**的数据 | 布隆过滤器 + 空值缓存 | `modules/penetration` |
| **击穿** | **热点 key 过期瞬间**并发回源 | 互斥锁重建 / 逻辑过期 | `modules/breakdown` |
| **雪崩** | **大量 key 同时失效 / Redis 宕** | 随机 TTL + 多级缓存 + 熔断 | `modules/avalanche` |

### 专题 2：分布式锁的四个"不能"

- **不能**用 `SETNX`+`EXPIRE` 两步加锁（中间崩溃→死锁）→ 用 `SET ... NX EX` 原子。
- **不能**解锁前先 `get` 再 `del`（可能删别人）→ 用 Lua 比对 UUID 原子删除。
- **不能**不设过期（持锁进程挂了→永久死锁）→ 必须 `EX`。
- **不能**业务时长超过锁租约（锁没了别人进来）→ 看门狗续期 / Redisson 自动续。

### 专题 3：Redis 序列化踩坑（真实 500）

**A：** 缓存命中时抛 `LinkedHashMap cannot be cast to Product`。根因：`GenericJackson2JsonRedisSerializer` 把自定义 `ObjectMapper` 传进去时**默认会关掉类型化**，反序列化回的是 `LinkedHashMap`，强转实体即 `ClassCastException` → 500。
**修复**：在 `ObjectMapper` 上启用默认类型化——`activateDefaultTyping(LaissezFaireSubTypeValidator.instance, NON_FINAL, JsonTypeInfo.As.PROPERTY)`（`config/RedisConfig.java`），序列化时写入 `@class`，反序列化才能还原真实类型。

> 选型原则：**Key 用 `StringRedisSerializer`**（可读、方便 `redis-cli` 调试）；**Value/Hash 用 `GenericJackson2JsonRedisSerializer`**（带类名）；**严禁 JDK 原生序列化**（可读性差、有安全漏洞、跨语言不兼容）。

### 专题 4：为什么限流/解锁/计数都要用 Lua？

**A：** Redis 是**单线程执行 Lua 脚本**的，一段 Lua 在它那里是**原子操作**——不会被其他命令插入。像"滑动窗口清窗口→计数→写入"、"比对 token→删除"这类**读-判断-写**三步，一旦拆成多条命令就会有并发竞态（多个请求同时读到"还能放行"然后都放行）。用 Lua 把整段逻辑包起来，才能保证限流/解锁的正确性。

### 专题 5：缓存与 DB 一致性

**A：** 本项目用 **Cache-Aside（更新 DB + 删缓存）** 保证最终一致。进阶可聊：
- **先更新 DB 再删缓存** 的竞态（删缓存后、回填前另一读把旧值写回）→ 用**延迟双删**或**订阅 MySQL binlog（Canal）异步删缓存**兜底。
- 强一致场景（库存、余额）**不依赖缓存**，走分布式锁/数据库事务。

---

## 五、压测与性能验证（结合真实数据）

> 本项目不只是"能跑"，还配套了**可复用的压测脚本 + 性能监控图表**，把高并发效果量化出来。位置：`benchmark/`。

### Q：你的压测脚本怎么设计？为什么不用 JMeter / wrk？

**A：** 设计原则：
- **零依赖、可移植**：纯 Python 标准库（`urllib` + `concurrent.futures` 线程池 + 自写令牌桶限速），**任何装了 Python3 的机器直接跑**，不需要装 JMeter、不需要联网下 Chart.js。
- **贴近真实流量**：支持「突发流量」（高并发线程）和「平稳流量」（令牌桶限速到指定 QPS）两种模式。
- **离线监控图**：脚本用**纯 SVG** 现场绘制 QPS、响应时间分位、错误率/拒绝率、RT 分布四张图，打包成**自包含 HTML**（`report/*.html`），双击即可看，不依赖外网 CDN。
- **覆盖 6 个核心场景**：`ratelimit / lock / cache / rank / penetration / avalanche`，`--scenario all` 还能出**汇总对比报告**（`report/summary_*.html`）。

运行方式（任选）：
```bash
cd benchmark
python3 run_benchmark.py --scenario all --concurrency 50 --requests 600
# 或 macOS 双击 bench.sh / Windows 双击 bench.bat（内部已封装上述命令）
```

### Q：压测跑出了什么结论？哪些数字能证明你的设计是有效的？

**A：** 以下为**单次本地压测**（并发 50、每场景 600 请求、Redis 本机 + H2）的真实数据，**数值随机器/环境变化，仅供定性参考**，但**相对关系稳定**：

| 场景 | 整体 QPS | P95(ms) | P99(ms) | 关键信号 |
|---|---|---|---|---|
| cache（缓存读取） | ~3488 | **23.1** | 32.2 | Redis 读极快，RT 稳定在 20ms 级 |
| avalanche（雪崩防护） | ~3243 | **23.6** | 31.8 | 随机 TTL+多级缓存下与纯缓存接近 |
| rank（ZSET 排行） | ~2885 | 27.3 | 36.2 | 写分+查榜依旧毫秒级 |
| penetration（布隆防穿透） | ~2899 | 29.0 | 36.3 | 布隆拦截下 DB 几乎零压力 |
| ratelimit（限流） | ~1887 | 114.7 | 124.5 | **拒绝率 99.17%**，绝大多数请求被 429 拦下 |
| **lock（分布式锁扣库存）** | **~430** | **612.4** | **1060.6** | **锁竞争最剧烈，延迟比其他高一个数量级** |

**三个可以拿去讲的结论：**
1. **分布式锁是性能瓶颈**：`lock` 场景 P95=612ms、P99=1060ms，比其余场景（普遍 20~30ms）**慢 20~30 倍**。这正是"锁串行化了扣库存"的直接证据——高并发下锁竞争是必须优化点（分段锁、库存预热、异步扣减都是后续方向）。
2. **限流真的拦住了**：`ratelimit` 拒绝率 99.17%，证明滑动窗口限流在高并发下**有效保护后端**，把超额流量挡在门外（对应前面 429 的修复）。
3. **防护类场景没拖慢主路径**：`avalanche` 开了多级缓存+随机 TTL，QPS/P95 与纯 `cache` 几乎持平，说明防护"加得值"、开销可接受。

> 注意：以上 `ratelimit` 的高延迟主要来自"大量请求在窗口内被限流并返回"本身的开销叠加；生产限流常前置到网关，不占用业务线程。

---

## 六、生产级考量 / 你这个项目还有什么不足？

> 这道题考察"知道边界在哪"。诚实 + 有改进路线最佳。

**当前为教学项目，生产化需要补强：**
1. **安全**：Redis 应设密码、启用 ACL；`DebugController`（暴露 `StatService` 快照）需加权限控制，不能裸奔在外网。
2. **高可用**：Redis 单机有单点风险，生产应上**哨兵（Sentinel）或集群（Cluster）**；Redisson 的 RedLock 也依赖多 master。
3. **可观测性**：`StatService` 是**单实例内存计数**（`common/StatService.java`），仅供演示；生产应换成 **Micrometer + Prometheus + Grafana**，跨实例聚合。
4. **缓存一致性**：目前依赖 Cache-Aside 的最终一致，强一致场景要引入 binlog 订阅或分布式锁。
5. **压测脚本定位**：偏"单元级接口压测"，未做**全链路压测 / 施压机与 SUT 分离**；生产级建议用 JMeter/k6 + 独立施压机 + 监控大盘。
6. **持久化与容量**：Redis 需配置 RDB/AOF 持久化策略，避免重启丢缓存导致瞬时雪崩；热点 key 要有发现与拆分机制。

---

## 七、常见追问 · 速答清单（30 题）

1. **Redis 为什么快？** 内存操作 + 单线程避免锁竞争 + IO 多路复用（epoll）+ 高效数据结构。
2. **单线程为什么还这么快，为什么不多线程？** 瓶颈在内存和带宽不是 CPU；单线程无锁、无上下文切换；6.0+ 已引入多线程 IO。
3. **ZSET 底层？** 跳表（skiplist）+ 哈希表；范围查询靠跳表，单点查分靠哈希。
4. **缓存和 DB 先删缓存还是先更 DB？** 先更新 DB 再删缓存（Cache-Aside），失败可延迟双删兜底。
5. **布隆过滤器能删除吗？** 标准布隆不能；可用 Counting Bloom 或布谷鸟过滤器。
6. **Redisson 看门狗默认多久？** 默认锁 30s，看门狗每 1/3（10s）续一次，直到手动 unlock 或宕机。
7. **RedLock 有什么争议？** Martin Kleppmann 指出其依赖时钟假设有缺陷，生产视一致性要求权衡使用。
8. **Lua 脚本太长/阻塞会怎样？** 阻塞 Redis 单线程，拖慢整个实例——脚本要短、别做重 IO。
9. **Pipeline 和 Lua 区别？** Pipeline 批量发命令减网络往返（非原子）；Lua 保证原子执行。
10. **String 最大多大？** 512MB；大 value 会阻塞，建议拆。
11. **热 key 怎么办？** 本地缓存 + 多副本 + key 加随机后缀拆分。
12. **大 key 怎么办？** 拆分、分批删除（UNLINK 异步）、避免 hgetall 全量。
13. **缓存预热怎么做？** 上线前批量加载热点数据；本项目布隆/雪崩/库存都演示了 `preheat/reset`。
14. **限流放哪层？** 网关（全局）→ 应用（接口级）；本项目在应用层 AOP。
15. **令牌桶 vs 漏桶？** 令牌桶允许突发（攒令牌）；漏桶强制匀速（削峰）。
16. **滑动窗口比固定窗口好在哪？** 无临界点双倍放行。
17. **Session 共享除了 Redis 还能？** JWT（无状态，但注销难）、数据库（慢）。
18. **Spring Session 默认序列化？** 原生 Java 序列化——实体必须 `Serializable`（本项目踩过）。
19. **Stream 和 PUB/SUB 区别？** Stream 持久化、可消费组、可 ack、可重放；PUB/SUB 实时易丢。
20. **消费组 ack 了还要 xdel 吗？** 可不删，Pending 会被清；长期不 ack 会堆积 PEL。
21. **Redis 持久化 RDB vs AOF？** RDB 快照（恢复快、丢数据多）；AOF 追加（安全、文件大）；可混合。
22. **AOF 重写？** 压缩历史命令，bgrewriteaof 后台 fork 重写。
23. **主从复制原理？** 全量 RDB + 增量命令传播。
24. **哨兵做什么？** 监控、选主、通知客户端新主地址。
25. **集群怎么分片？** 16384 个 slot 哈希到不同节点；`CRC16(key)%16384`。
26. **缓存一致性最终一致怎么保证？** 删缓存 + 延迟双删 + binlog 订阅兜底。
27. **锁超时但业务没跑完？** 看门狗续期（手写或 Redisson）。
28. **可重入锁怎么实现？** 记录持有线程 + 重入计数（Redisson 用 Hash 结构 `lock:{key}:{threadId}:count`）。
29. **你项目里前端怎么连后端？** Vite 代理 `/api`→8080 + 后端 CorsConfig 放行；SSE 做实时推送。
30. **压测拒绝率 99% 说明什么？** 限流生效，超额流量被挡在门外，保护后端不被冲垮。

---

> 文档与代码同步原则：本文所列文件、方法名、参数均对应仓库当前实现；若后续重构，请同步更新本节引用路径，保持文档与代码长期一致。
