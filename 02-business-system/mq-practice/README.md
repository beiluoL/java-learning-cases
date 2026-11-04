# MQ 消息队列实战 · Spring Boot 3 + Vue 3 全栈

> 三个真实业务场景，把 RabbitMQ 从「会发消息」练到「敢上生产」：
> **削峰填谷**、**延迟消息**、**发布订阅**，配套生产者确认、消费手动 ACK、双层幂等、重试队列、死信回放与可视化控制台。

---

## 一、这个项目解决什么问题

大多数 MQ 教程停在 `convertAndSend` + `@RabbitListener` 就被当成"学完了"。但真实故障是这些：

| 线上问题 | 本项目的做法 |
|---|---|
| 消息发出去了，但没进队列（路由键写错） | `publisher-returns=true` + `mandatory=true` + `ReturnsCallback`，把"NO_ROUTE"写进消息账本 |
| 消费者处理到一半崩了，消息却已经被 broker 删掉 | `acknowledge-mode: manual`，业务成功才 `basicAck` |
| 同一条消息被投递两次，订单扣了两次库存 | Redis `SETNX` + `mq_idempotent_log` 唯一键，**双层幂等** |
| 下游偶尔抖动，重试一次就好，但代码直接报错 | 重试队列（TTL 5s 退避）+ 超过 3 次进死信 |
| 消息反复失败，把数据库打崩 | 区分**可重试**（系统异常）与**不可重试**（业务失败） |
| 消息进了死信，没人知道，业务永远缺一笔 | 死信可视化 + 一键按原交换机/路由键回放 |
| 30 分钟未支付关单，写了个定时任务扫全表 | DLX + TTL 延迟消息，配一层兜底扫描（双保险） |

---

## 二、技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.4.1 · JDK 17 · Maven 多模块 |
| MQ | Spring AMQP · RabbitMQ 3.13（含 Management 控制台） |
| 存储 | MySQL 8.0（生产 profile）/ H2（默认，克隆即跑）· MyBatis-Plus 3.5.7 |
| 缓存 | Redis 7（Lettuce 连接池，Lua 原子预扣） |
| 前端 | Vue 3.5 · TypeScript · Vite 5 · Pinia · Element Plus · Axios |
| 文档 | springdoc OpenAPI 3（`/swagger-ui.html`） |

---

## 三、消息拓扑

```
场景一 · 削峰填谷
  浏览器 ──POST /api/seckill/orders──▶ SeckillService
                                        │ ① Redis Lua 原子预扣
                                        │ ② insert mq_order(status=QUEUING)
                                        │ ③ 发送消息（Confirm 回填账本）
                                        ▼
                              mq.order.exchange (direct)
                                        │ order.create
                                        ▼
                              mq.order.create.queue ──▶ OrderCreateConsumer（2~4 并发，手动 ACK）
                                        │                   ├ 幂等校验
                                        │                   ├ DB 条件更新扣库存
                                        │                   ├ CAS: QUEUING → UNPAID
                                        │                   └ 提交后投递延迟消息
                                        │ 失败
                                        ├──▶ mq.retry.exchange ──▶ *.retry.queue（TTL 5s）──▶ 回投
                                        └──▶ 超过 3 次 ──▶ mq.dlx.exchange ──▶ mq.dlq.queue

场景二 · 延迟消息
  OrderCreateConsumer ──(afterCommit)──▶ mq.order.delay.exchange
                                              │ order.delay / order.delay.short
                                              ▼
                              ┌───────────────┴────────────────┐
                     mq.order.delay.queue        mq.order.delay.short.queue
                     （长延迟 >60s，无消费者）      （短延迟 ≤60s，演示用）
                              └───────────────┬────────────────┘
                                              │ TTL 到期 → 死信
                                              ▼
                                     mq.order.cancel.exchange ──▶ mq.order.cancel.queue
                                                                        │
                                                                        ▼
                                                          OrderCancelConsumer
                                                          ├ 回查状态（已支付则跳过）
                                                          └ CAS: UNPAID → CANCELLED + 回补库存

场景三 · 发布订阅
  ProductService ──▶ mq.product.exchange (topic) ──▶ product.changed
                              ├──▶ mq.product.cache.queue  ──▶ CacheSyncConsumer   （写/删 Redis）
                              └──▶ mq.product.search.queue ──▶ SearchIndexConsumer （写检索索引）
                                   两个队列各自独立消费、独立重试、独立死信
```

---

## 四、目录结构

```
mq-practice/
├── backend/                                  # ← 父工程目录（Maven 多模块聚合）
│   ├── pom.xml                               #   mq-practice-parent
│   ├── mq-practice-common/                   #   公共契约：Result / 异常 / 消息体 / 枚举 / MQ 常量
│   └── mq-practice-backend/                  #   业务 + MQ 基础设施
│       └── src/main/java/com/example/mq/
│           ├── config/                       #   RabbitMqConfig（拓扑/确认/ACK）、Redis、MyBatis-Plus
│           ├── mq/                           #   MessageSender / IdempotentGuard / 消费者模板 / 延迟发布抽象
│           ├── consumer/                     #   4 个消费者
│           ├── service/                      #   秒杀、订单、商品、监控（接口 + impl）
│           ├── controller/                   #   4 组 REST 接口
│           ├── entity/ mapper/ dto/ job/     #   数据层、视图对象、定时兜底任务
│           └── resources/                    #    application.yml / db / lua / mapper xml
├── frontend/                                 # Vue 3 + TS + Vite
│   └── src/{api,views,components,store,router,types,layout,styles}
├── sql/
│   ├── 01-schema-mysql.sql                   # MySQL 8.0 生产版建表（含注释/索引/字符集）
│   └── 02-data-mysql.sql                     # 商品/库存/索引种子数据
├── docker-compose.yml                        # RabbitMQ + MySQL + Redis
├── start.command / stop.command / restart.command     # macOS 双击
├── start.bat     / stop.bat     / restart.bat         # Windows 双击
└── README.md
```

---

## 五、快速开始

### 5.1 最简单：双击启动

| 系统 | 操作 |
|---|---|
| macOS | 双击 `start.command`（首次若提示"无法验证开发者"，右键 → 打开） |
| Windows | 双击 `start.bat` |

脚本会依次：检查 java/mvn/node → 拉起 RabbitMQ（Docker）→ 拉起 Redis（已有则复用）→ 编译后端并等待健康检查 → 安装前端依赖并启动 Vite → 打印全部访问地址。

关闭：双击 `stop.command` / `stop.bat`。
重启：双击 `restart.command` / `restart.bat`（只重启后端：`./restart.command backend`）。

> **端口被占用？** 脚本默认 8080/5173。若被其它项目占着，命令行覆盖即可：
> ```bash
> BACKEND_PORT=8081 FRONTEND_PORT=5174 ./start.command
> ```

### 5.2 手动启动

```bash
# 1) 中间件
docker compose up -d rabbitmq mysql redis
#   RabbitMQ 控制台 http://localhost:15672  guest/guest

# 2) 后端（默认 h2 profile，无需 MySQL）
cd backend && mvn -DskipTests install && mvn -pl mq-practice-backend spring-boot:run
#   想用 MySQL：先导入 sql/ 两个脚本，再 --spring.profiles.active=mysql

# 3) 前端
cd frontend && npm install && npm run dev
```

访问：

| 地址 | 说明 |
|---|---|
| http://localhost:5173 | 前端控制台（Vite 代理 `/api` → 8080） |
| http://localhost:8080/swagger-ui.html | 接口文档 |
| http://localhost:15672 | RabbitMQ 控制台 |

### 5.3 配置项

`backend/mq-practice-backend/src/main/resources/application.yml`

```yaml
mq:
  practice:
    order-delay-seconds: 1800     # 订单支付超时（秒），前端可传更小值演示
    max-retry-count: 3            # 消费失败最大重试次数
    retry-delay-ms: 5000          # 重试队列 TTL（退避时长）
    idempotent-ttl-hours: 24      # Redis 幂等键有效期
    product-cache-ttl-minutes: 30 # 商品缓存 TTL
    delay-mode: dlx               # dlx（默认，零插件）| plugin（需安装延迟插件）
    idempotent-enabled: true      # 关闭后仅剩 DB 唯一键兜底（课堂对比用）
```

环境变量可覆盖中间件地址：`REDIS_HOST/REDIS_PORT`、`RABBITMQ_HOST/RABBITMQ_PORT`、`MYSQL_HOST/MYSQL_PORT`。

---

## 六、三个场景怎么玩

### 场景一：秒杀下单（削峰填谷）

1. 打开「秒杀下单」，点某个商品的「立即抢购」；
2. 右侧立刻出现排队凭证（**接口耗时通常 5~20ms**，首次请求含 JIT 与连接预热会到几百毫秒）；
3. 400ms 一次轮询，通常 1~2 轮后从 `QUEUING` 变为 `UNPAID`，并显示金额与支付截止时间；
4. 点「并发压测」选 100 并发：库存 20 件的商品，结果恒定是 **成功 20 / 售罄 80 / 库存 20→0**。

**为什么不会超卖？三道闸门**

| 闸门 | 位置 | 作用 |
|---|---|---|
| Lua 原子预扣 | `lua/seckill_stock.lua` | 把大部分无效流量挡在数据库外，无锁、无网络往返 |
| 条件更新 | `UPDATE mq_stock SET available=available-? WHERE available>=?` | 唯一事实来源，Redis 算错也不会超卖 |
| 状态 CAS | `UPDATE mq_order SET status='UNPAID' WHERE status='QUEUING'` | 重复消费不会重复扣减 |

### 场景二：30 分钟未支付自动取消（延迟消息）

1. 打开「订单与超时取消」，选商品 + 「10 秒后取消」，点「创建演示订单」；
2. 列表出现「待支付」，右侧倒计时开始走；
3. 什么都不做 → 约 10 秒后状态变「已取消」，库存自动回补（DB + Redis）；
4. 再建一单，在倒计时内点「支付」→ 到点的取消消息会因 `status != UNPAID` 而自动跳过。

**延迟消息的两种实现**（`mq.practice.delay-mode`）

| 模式 | 原理 | 取舍 |
|---|---|---|
| `dlx`（默认） | 消息投到无消费者的延迟队列，靠**消息级 TTL** 到期死信到取消交换机 | 零插件；但同队列不同 TTL 会**队头阻塞**（先入队的 30 分钟消息挡住后入队的 10 秒消息） |
| `plugin` | `x-delayed-message` 交换机 + `x-delay` 头，插件内部定时 | 支持任意延迟混用；需运维装插件 |

> 本项目已经按**延迟档位拆了两个队列**：`mq.order.delay.short.queue`（≤60s，演示用）与 `mq.order.delay.queue`（>60s）。
> 这就是 DLX+TTL 方案规避队头阻塞的标准做法 —— 同一队列内 TTL 量级接近，就不会互相长时间阻塞。
> 更彻底的做法是延迟插件，或每个档位（10s / 1m / 30m）各建队列。
> 另外还有 `OrderTimeoutReconcileJob`：每 60 秒扫一次"已过 `expire_time` 但仍 `UNPAID`"的订单，走同一套取消逻辑 —— 延迟消息可能丢，状态最终必须正确。

### 场景三：商品变更发布订阅

1. 打开「商品变更订阅」，选一行商品；
2. 点「改价」输入新价格 → 提交；
3. 观察「三视角快照」：MySQL 立刻是新版本，Redis 缓存与检索索引会在几百毫秒内追上（`最终一致性窗口`）；
4. 点「下架」：缓存被**主动删除**（下架商品不该还能被缓存读到），索引 `status` 置 0，`doc_version` 递增；
5. 两个队列在「MQ 监控台 → 队列」里各自有积压与消费者数，互不影响。

**乱序保护**：消息带 `version`，消费者发现"已存版本 > 消息版本"就丢弃，避免迟到的旧消息把新价格覆盖回去。

---

## 七、可靠性设计逐条对照

### 7.1 生产者：消息不丢

```yaml
spring.rabbitmq:
  publisher-confirm-type: correlated   # ① 到交换机 → 回调 ack/nack
  publisher-returns: true              # ② 路由不到队列 → 回调
  template.mandatory: true             #   ② 必须配合它才生效
```

每次发送都会：写 `mq_message_log`（SENDING）→ 发送并带 `CorrelationData(messageId)` → 回调更新为 `CONFIRMED / RETURNED / FAILED`。监控页的「消息账本」就是这个闭环的可视化。

> 只监听 confirm 是不够的：confirm 只证明"到了交换机"，路由键写错时消息会被静默丢弃。

### 7.2 消费者：处理完才算消费

```yaml
listener.simple:
  acknowledge-mode: manual      # 手动 ACK
  prefetch: 5                   # 预取，兼顾吞吐与公平
  default-requeue-rejected: false
  retry.enabled: false          # 重试完全由「重试队列」实现，避免两套机制打架
```

`AbstractMqConsumer` 统一了四条消息去向：

| 情况 | 动作 |
|---|---|
| 处理成功 | `basicAck` |
| 业务性失败（`BusinessRejectException`） | `basicReject(false)` → 死信，**不重试** |
| 系统异常 | 回投重试队列 + `basicAck` 原消息 |
| 重试次数耗尽 | `basicReject(false)` → 死信 |

### 7.3 幂等：两层

```
① Redis SETNX  mq:idem:{SCENE}:{bizKey}   TTL 24h      → 挡住 99% 重复，微秒级
② INSERT mq_idempotent_log(biz_key UNIQUE)             → Redis 挂了也只会成功一条
```

状态语义（很多人踩的坑）：**成功**保留锁 → 后续重复直接丢弃；**失败**删除 Redis 键并置 `status=3` → 允许重试队列再次投递。否则"重试 3 次"会被幂等自己挡死。

### 7.4 重试与死信

- 重试队列：`x-message-ttl=5000` + `x-dead-letter-exchange=""`（默认交换机）+ `x-dead-letter-routing-key=<业务队列名>`。
  用默认交换机回投是刻意的：**精准回到那一个队列**，不会因为 topic 扇出给所有订阅者。
- 死信队列：业务队列统一带 `x-dead-letter-exchange=mq.dlx.exchange`，最终落到 `mq.dlq.queue`。
- 死信查看用 `basicGet(autoAck=false)` + `basicNack(requeue=true)`，**看得见但不会消费掉**；回放按 `x-death` 里的原交换机/路由键重投。

### 7.5 事务与消息的顺序

```java
@Transactional
public CreateOutcome createOrder(...) {
    // 扣库存 + CAS 落单（数据库事务内）
    AfterCommit.run(() -> delayPublisher.publish(cancelMsg, delay));  // 提交后才发 MQ
}
```

`@Transactional` 只包住数据库操作，**ACK 必须在事务提交之后**（在 consumer 里做）。如果把 ack 写进事务方法，一旦提交失败，消息已删、业务已回滚，就真丢了。

---

## 八、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/seckill/products` | 秒杀商品（含 DB 库存 + Redis 预扣余额） |
| POST | `/api/seckill/orders` | 提交秒杀（立即返回排队凭证） |
| POST | `/api/seckill/stress?productId=&concurrency=` | 并发压测，验证不超卖 |
| POST | `/api/seckill/stock/reset?productId=` | 重置演示库存（DB + Redis） |
| GET | `/api/orders?status=&keyword=&page=&size=` | 订单分页 |
| GET | `/api/orders/{orderNo}` | 订单详情（前端轮询用） |
| POST | `/api/orders/{orderNo}/pay` | 支付（CAS） |
| POST | `/api/orders/{orderNo}/cancel?reason=` | 主动取消 + 回补库存 |
| POST | `/api/orders/demo` | 创建演示订单（可指定 `delaySeconds`） |
| GET | `/api/products` | 商品列表 |
| GET | `/api/products/{id}` | 三视角快照（DB / Redis / 索引） |
| POST | `/api/products/{id}/change` | 上架 / 下架 / 改价 + 发布事件 |
| GET | `/api/mq/overview` | 总览：队列深度 + 消息统计 + 订单分布 |
| GET | `/api/mq/queues` | 队列列表 |
| GET | `/api/mq/messages` | 消息账本（Confirm/Return 结果） |
| GET | `/api/mq/idempotent` | 幂等记录 |
| GET | `/api/mq/dlq` | 查看死信（只读） |
| POST | `/api/mq/dlq/replay` | 死信回放 |
| GET/POST | `/api/mq/chaos` | 故障注入开关（教学用，生产必删） |

---

## 九、数据库表

| 表 | 作用 | 关键约束 |
|---|---|---|
| `mq_product` | 商品，事件源 | `uk_product_no`；`version` 乐观锁 |
| `mq_stock` | 库存 | `uk_product_id`；扣减用条件更新 |
| `mq_order` | 订单 | `uk_order_no`；`idx_status_expire` 给兜底扫描用 |
| `mq_idempotent_log` | 消费幂等账本 | **`uk_biz_key`** ← 最后一道防线 |
| `mq_message_log` | 发送账本（本地消息表雏形） | `uk_message_id` |
| `mq_search_index` | 模拟 ES 文档 | `uk_product_id`；`doc_version` 防乱序 |

默认 `h2` profile 启动时自动执行 `resources/db/schema.sql` + `data.sql`（与 MySQL 脚本列结构一致，去掉了 H2 不兼容语法）；生产用 `sql/01-schema-mysql.sql`。

---

## 十、故障注入：把重试链路走一遍

1. 「MQ 监控台 → 故障注入」，把 `PRODUCT_CACHE_SYNC` 打开、失败概率 100%；
2. 回「商品变更订阅」改一次价格；
3. 观察：消费失败 → `mq.product.cache.retry.queue` 出现消息 → 5 秒后回投 → 再失败 …… 3 次后进 `mq.dlq.queue`；
4. 关闭故障注入 → 「死信」页签 → 「一键回放」；
5. 缓存被写入，`最终一致性` 恢复。

这条链路覆盖了消费端 80% 的线上问题形态。

---

## 十一、常见问题

**Q：启动报 `Connection refused: localhost:5672`？**
RabbitMQ 没起来：`docker compose up -d rabbitmq`。应用本身不会因此启动失败（`missing-queues-fatal: false`），broker 恢复后会自动重连并补声明拓扑。

**Q：端口 8080 / 5173 被别的项目占了？**
两个脚本都支持端口覆盖，且**只会杀自己项目的进程**：
```bash
BACKEND_PORT=8081 FRONTEND_PORT=5174 ./start.command
```

**Q：`docker compose up mysql` 报 3306 被占用？**
本机已装 MySQL。换端口即可：`MYSQL_PORT=3307 docker compose up -d mysql`（后端必须设同一个 `MYSQL_PORT`）。

**Q：为什么 Redis 的库存和数据库库存会短暂不一致？**
秒杀下单先扣 Redis（挡流量），消费端再扣 MySQL（事实）。订单取消时两边都回补。极端情况下 Redis 扣成功但 DB 不足 → 订单置 `FAILED` 并回滚 Redis，这是有意设计的降级路径。

**Q：为什么延迟队列里一直有消息？**
那是还没到期的"取消闹钟"，属正常驻留。监控页「总积压」包含它，已在提示里标注。

**Q：重启后端后，旧订单的"自动取消"没生效 / 死信里出现"订单不存在"？**
H2 是内存库，重启后订单表清空；但 RabbitMQ 里的延迟消息仍在（队列持久化）。旧消息到期后找不到订单，就会按设计进死信。
演示前先清空延迟队列即可：

```bash
for q in mq.order.delay.queue mq.order.delay.short.queue; do
  curl -s -u guest:guest -X DELETE "http://localhost:15672/api/queues/%2F/$q/contents"
done
```

**Q：改成 `plugin` 模式后启动报 `unknown exchange type 'x-delayed-message'`？**
先装插件：
```bash
docker exec mq-practice-rabbitmq rabbitmq-plugins enable rabbitmq_delayed_message_exchange
docker restart mq-practice-rabbitmq
```

---

## 十二、和真正的生产系统还差什么（诚实说明）

| 能力 | 本项目 | 生产建议 |
|---|---|---|
| 消息可靠性 | 本地消息表 + Confirm/Return | 加定时对账任务，把长期 `SENDING` 的消息重投或告警 |
| 延迟消息 | DLX + TTL（有队头阻塞限制） | 延迟插件，或按档位分队列 |
| 检索 | 用 `mq_search_index` 表模拟 ES | 真实 Elasticsearch + 索引别名切换 |
| 分布式锁 | Redis Lua 预扣 + DB 条件更新 | 跨服务扣减可引入 Redisson / Seata，但优先选"条件更新"这类无锁方案 |
| 可观测 | 业务表 + 控制台页面 | Micrometer + Prometheus + Grafana，队列深度/消费延迟/死信数告警 |
| 多实例 | 支持（幂等与锁不依赖单机内存） | 消费者扩缩容、镜像队列/Quorum 队列、按 vhost 隔离环境 |
| 安全 | guest/guest | 专用账号 + 最小权限 vhost，禁止 guest 远程登录 |

---

## 十三、面试速答（本项目可直接展开的点）

1. **如何保证消息不丢？** 生产端 confirm + return + 本地消息表；broker 持久化（durable queue + persistent message）；消费端手动 ACK + 幂等。
2. **如何保证不重复消费？** MQ 只能做"至少一次"，重复必然发生；靠消费端幂等（Redis SETNX + DB 唯一键），而不是指望 MQ。
3. **如何防止超卖？** 三层：Redis Lua 原子预扣（挡流量）、DB 条件更新（事实）、状态 CAS（防重复扣减）。不用分布式锁也能做到。
4. **延迟消息怎么做？** DLX + TTL（队头阻塞要注意）或延迟插件；消费时必须回查状态，消息只是闹钟。
5. **重试怎么设计？** 分可重试/不可重试；可重试走独立重试队列做退避（不要 `requeue=true` 原地打转）；超过次数进死信 + 人工补偿。
6. **为什么 ACK 要放在事务提交之后？** 否则提交失败时消息已删、业务已回滚，形成数据丢失。
7. **发布订阅比串行调用好在哪？** 故障隔离 + 独立伸缩 + 可新增订阅方而不改发布方；代价是最终一致性，需要版本号/幂等兜底。
8. **消息乱序怎么办？** 单队列单消费者可保序；多消费者下用版本号/状态机做"最后写入胜出"或丢弃旧消息，别指望顺序。

---

## 十四、验证记录（本机实测）

| 项 | 结果 |
|---|---|
| `mvn -DskipTests install`（多模块） | 通过 |
| `npm run build`（vue-tsc + vite） | 通过，无类型错误 |
| 100 并发抢 20 件库存 | 成功 20 / 售罄 80 / 库存 20→0，无超卖 |
| 异步落单 | QUEUING → UNPAID，端到端 ~30ms（预热后） |
| 延迟取消 | `delaySeconds=8`，8 秒后精确转为 CANCELLED 并回补库存（DB 与 Redis 均恢复） |
| 故障注入 → 重试 → 死信 → 回放 | 3 次退避后进 DLQ，回放后缓存写入成功 |
| MySQL 8.0 profile | 建表脚本 + 全链路（下单/延迟取消）通过 |
| macOS 启停脚本 | `start/stop/restart` 实测通过，含无 PID 文件时按签名精准停止 |
