# RabbitMQ 可视化管理界面 · 选型与集成说明

> 本文回答三个问题：为什么选它、怎么接进来的、上线前还要补什么。
> 代码位置：`backend/.../controller/MqAdminController.java`、`frontend/src/views/AdminView.vue`。

---

## 一、选型调研：企业里到底在用什么

RabbitMQ 的可视化不像 Kafka 那样百花齐放（AKHQ / Kafdrop / CMAK / Offset Explorer）。原因是官方把
`rabbitmq_management` 做进了发行版，能力覆盖了 90% 的日常诉求，第三方方案反而长期得不到维护。

| 方案 | 代表 | 能力 | 典型场景 | 主要问题 |
|---|---|---|---|---|
| **官方 Management 插件**（选定） | `rabbitmq:*-management` 镜像，15672 | 队列/交换机/绑定/连接/通道、Get messages、Publish、Purge、策略、用户管理、HTTP API | 开发自测、值班排查、单条消息查看 | 无历史趋势、无告警、页面是英文、权限颗粒度粗 |
| 指标监控 | `rabbitmq_prometheus` + Prometheus + Grafana（官方大盘 10991） | 队列深度、速率、连接数、文件描述符等时序指标 + 告警 | 长期趋势、容量规划、值班告警 | 看不到消息体，不知道"哪条消息" |
| 链路追踪 | OpenTelemetry / SkyWalking / Jaeger | 生产到消费的端到端耗时、异常点 | 定位"慢在哪一环" | 采样有损，默认不含消息内容 |
| 商业 APM | Datadog / 阿里云 ARMS / 腾讯云 TMP | 开箱即用的指标 + 告警 + 拓扑 | 已有云监控体系的企业 | 成本高，私有化部署受限 |
| 客户端 GUI | Offset Explorer、MQ Assistant | 本地连 broker 看消息 | 临时排查 | 直连生产风险高，无审计 |
| **自建业务控制台**（本项目已具备） | 我们的 `/monitor` 页 | 消息账本、幂等、死信回放、故障注入 | 业务补偿、教学演示 | 需要开发维护 |

**结论：选「官方 Management 插件 + 项目内只读代理页」的组合。**

理由：

1. **官方插件是唯一稳定的一手数据源**。所有第三方工具本质都在调它的 HTTP API，直接用官方的不吃亏；
2. **前端不能直连 15672**。Management API 用 HTTP Basic，凭据进浏览器就等于公开；跨域也会被拦。
   所以必须由后端代理一层——这一步顺便解决了"公网只暴露一个端口"的问题；
3. **两套界面各有分工**：原生控制台功能全（建队列、改策略、看策略命中），适合运维；
   项目内的运维台只做**只读聚合 + 少量受控写操作**，适合开发/值班快速定位，且和业务页面同一个导航。

> 不选"再部署一个第三方 UI"的原因：多一个服务、多一套账号体系、多一个攻击面，换来的只是界面汉化。

---

## 二、集成后的数据流

```
浏览器 (5173)
   ├─ /admin 页面 ──► /api/mq/admin/**（本后端）──► Management API :15672 ──► RabbitMQ
   │                     服务端持有 Basic 凭据        （只读为主）
   │                     写操作校验 X-MQ-Admin-Token
   │                     队列前缀白名单（mq.*）
   │
   └─ 「原生控制台」按钮 ──► http://localhost:15672（另开标签页，需自行登录）
```

关键点：**浏览器永远拿不到 RabbitMQ 的账号密码**，也不会因跨域被拦。

---

## 三、集成步骤（已落地）

### 1）中间件：启用 management 插件

`docker-compose.yml` 用的是自带插件的镜像，无需额外安装：

```yaml
rabbitmq:
  image: rabbitmq:3.13-management       # 已内置 rabbitmq_management
  ports:
    - "5672:5672"                       # AMQP
    - "15672:15672"                     # 管理控制台 + HTTP API
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
```

如果用的是不自带插件的镜像，手动开启：

```bash
docker exec mq-practice-rabbitmq rabbitmq-plugins enable rabbitmq_management
```

### 2）后端：配置代理

`application.yml`：

```yaml
mq:
  practice:
    admin:
      enabled: true
      base-url: ${MQ_ADMIN_BASE_URL:http://localhost:15672}   # 服务端访问地址
      console-url: ${MQ_ADMIN_CONSOLE_URL:http://localhost:15672}  # 浏览器跳转地址
      username: ${MQ_ADMIN_USERNAME:guest}                    # 只读账号优先
      password: ${MQ_ADMIN_PASSWORD:guest}
      vhost: ${MQ_ADMIN_VHOST:/}
      timeout-ms: 3000
      write-enabled: ${MQ_ADMIN_WRITE_ENABLED:true}           # 生产置 false
      write-token: ${MQ_ADMIN_WRITE_TOKEN:mq-practice}        # 留空 = 拒绝所有写操作
```

> `base-url` 与 `console-url` 分开，是因为部署拓扑经常不同：
> 后端可能在容器网络里访问 `http://rabbitmq:15672`，而用户浏览器要走 `http://10.0.0.5:15672`。

### 3）后端：代理层

| 文件 | 职责 |
|---|---|
| `config/MqAdminProperties.java` | 配置绑定 |
| `mq/admin/RabbitManagementClient.java` | RestClient 封装：Basic 认证、vhost 编码（`/` → `%2F`）、超时、错误翻译 |
| `service/MqAdminService.java` + `impl` | JSON → 视图对象；写操作口令与白名单校验 |
| `controller/MqAdminController.java` | `/api/mq/admin/**` 七个只读 + 两个写接口 |

### 4）前端：运维台页面

路由 `/admin`，接口封装在 `src/api/admin.ts`，页面 `src/views/AdminView.vue`：

- **队列**：就绪 / 未确认 / 消费者 / 发投速率 / TTL / DLX，行点击查看消息，支持清空（受控）；
- **交换机**：类型、持久化、入出速率、参数（含 `x-delayed-type`）；
- **绑定**：`source → routingKey → destination`，排查"发出去没人收"的第一现场；
- **消费者**：`prefetch`、是否手动 ACK、`activity` 状态、来源主机；
- **连接**：用户、对端、通道数、客户端版本；
- **投递测试**：手发一条消息，返回 `routed` 判断绑定是否命中。

### 5）访问方式

| 入口 | 地址 | 说明 |
|---|---|---|
| 项目内运维台 | http://localhost:5173/admin | 只读为主，数据和原生控制台同源 |
| 原生控制台 | http://localhost:15672 | guest / guest（仅本地） |
| 运维台 API | http://localhost:8080/api/mq/admin/status | 可在 Swagger 里调试 |
| Swagger | http://localhost:8080/swagger-ui.html | 分组「RabbitMQ 运维台」 |

---

## 四、安全认证与权限控制

### 4.1 本项目已实现的三层控制

| 层 | 措施 | 位置 |
|---|---|---|
| 网络 | 浏览器不直连 15672；后端只暴露 `/api/mq/admin/**` | 架构 |
| 凭据 | RabbitMQ 账号只存在后端配置/环境变量 | `MqAdminProperties` |
| 应用 | 写操作要求 `X-MQ-Admin-Token`；`write-enabled` 总开关；队列前缀白名单 | `MqAdminServiceImpl#guardWrite` |

写操作是** fail-closed** 的：开关关闭、口令为空、口令不匹配，一律 403。

### 4.2 RabbitMQ 侧的权限模型（生产必做）

RabbitMQ 的权限 = **用户 tag + vhost 权限**两层：

```bash
# 1. 应用账号：能读写业务 vhost
docker exec mq-practice-rabbitmq rabbitmqctl add_user mq_app '强密码'
docker exec mq-practice-rabbitmq rabbitmqctl set_user_tags mq_app management
docker exec mq-practice-rabbitmq rabbitmqctl set_permissions -p / mq_app ".*" ".*" ".*"

# 2. 只读监控账号：只能看，不能改（给运维台用）
docker exec mq-practice-rabbitmq rabbitmqctl add_user mq_monitor '强密码'
docker exec mq-practice-rabbitmq rabbitmqctl set_user_tags mq_monitor monitoring
docker exec mq-practice-rabbitmq rabbitmqctl set_permissions -p / mq_monitor "^$" "^$" ".*"

# 3. 禁用默认账号远程登录（容器内本地仍可用）
docker exec mq-practice-rabbitmq rabbitmqctl delete_user guest
```

常见 tag 的差别：

| tag | 能看到 | 能做 |
|---|---|---|
| `monitoring` | 全部队列/连接/通道（只读） | 不能改任何东西 |
| `management` | 自己 vhost 的队列 | 可建队列、发消息、purge |
| `policymaker` | 同 management | 额外能改策略/参数 |
| `administrator` | 全部 | 用户、vhost、权限、插件、关闭连接 |

> 运维台建议配 `mq_monitor`：即使有人拿到口令，也只能"看"和"投递/清空本项目队列"，
> 无法建用户、改策略、删队列。

### 4.3 生产环境 checklist

- [ ] `MQ_ADMIN_USERNAME` / `MQ_ADMIN_PASSWORD` 指向**专用只读账号**，不要用 guest；
- [ ] `MQ_ADMIN_WRITE_ENABLED=false`，或把 `write-token` 换成随机长串并只发给值班同学；
- [ ] 15672 **不要暴露公网**：`ports` 改成 `127.0.0.1:15672:15672`，或只在内网网段放行；
- [ ] 管理面走 HTTPS：RabbitMQ 配 TLS 或前置 Nginx 反代（`proxy_pass http://127.0.0.1:15672`）；
- [ ] 接 SSO：Nginx `auth_request` / OAuth2 Proxy，把"谁能打开运维台"交给公司账号体系；
- [ ] 开审计：`rabbitmq_event_exchange` 或网关访问日志，记录谁在什么时候 purge 了哪个队列；
- [ ] 消息体可能含手机号/订单号，**peek 到的内容不要贴到工单/群里**，必要时脱敏；
- [ ] 生产关闭"投递测试"入口（前端按 `status.writeEnabled` 隐藏即可）。

---

## 五、生产环境注意事项

**性能**

- Management 的统计数据来自 `collect_statistics_interval`（默认 5s），高频调用 `/api/queues` 不会更实时，
  只会增加 CPU。页面 5s 轮询已经够用，别改成 500ms。
- 队列数上千时 `/api/queues` 会变重，应按需分页或用 `/api/queues/{vhost}/{name}` 单查。

**稳定性**

- 运维台只做"看 + 受控写"，**不要把它做成批量操作台**：一次 purge 上百个队列是不可逆事故。
- 别用 `Get messages` 的 `ack` 模式看消息——那是真消费，会让业务丢消息。本项目强制 `ack_requeue_true`。

**可观测**

- 管理插件负责"点"，Prometheus 负责"线"。生产建议再开官方插件：

```bash
docker exec mq-practice-rabbitmq rabbitmq-plugins enable rabbitmq_prometheus
# 指标端点：http://localhost:15692/metrics
# Grafana 官方大盘：ID 10991（RabbitMQ-Overview）
```

**合规**

- 消息体可能包含 PII，peek 权限应视同数据导出权限管理；
- 生产控制台建议只读账号 + 堡垒机，操作留痕。

---

## 六、如何验证

### 6.1 接口层（30 秒）

```bash
# 1. 连接状态：ok=true、version=3.13.x
curl -s http://localhost:8080/api/mq/admin/status | jq '.data | {ok,version,queueCount,consumerCount}'

# 2. 队列列表：应看到 mq.order.create.queue 等，consumers=2
curl -s http://localhost:8080/api/mq/admin/queues | jq '.data[] | {name,ready,unacked,consumers}'

# 3. 绑定：mq.product.exchange 应有两条指向 cache/search 的绑定
curl -s http://localhost:8080/api/mq/admin/bindings | jq '.data[] | select(.source=="mq.product.exchange")'

# 4. 只读 peek：投一条秒杀单后，delay 队列应能"看"到消息
curl -s -X POST 'http://localhost:8080/api/mq/admin/queues/mq.order.delay.short.queue/peek?count=5' | jq '.data | length'

# 5. 写操作鉴权：不带口令应 403
curl -s -X POST http://localhost:8080/api/mq/admin/queues/mq.order.delay.queue/purge | jq '.code,.message'
# 6. 带正确口令应成功
curl -s -X POST http://localhost:8080/api/mq/admin/queues/mq.order.delay.queue/purge \
     -H 'X-MQ-Admin-Token: mq-practice' | jq '.data'
```

### 6.2 页面层（3 分钟）

1. 打开 `http://localhost:5173/admin`，顶部应显示 **RabbitMQ 3.13.x + 集群名 + 队列/消费者数量**；
2. 「队列」页签确认 `mq.order.create.queue` 的**消费者=2**、`mq.order.delay.*` 的**消费者=0**（设计如此）；
3. 去「秒杀」页下一单，回到「队列」：`mq.order.create.queue` 会短促出现 1 条 ready 后归零；
4. 点 `mq.order.delay.short.queue` 行 → 抽屉弹出消息体，含 `x-mq-scene=ORDER_CANCEL`；
5. 关掉抽屉，等延迟到期（10s）后队列归零 —— 说明"看"没有影响消息流转；
6. 改个价，去「绑定」确认 `mq.product.exchange` 的两条绑定；「消费者」页确认两个消费者 `ackRequired=true`、`prefetch=5`；
7. 右上角填口令 `mq-practice`，「投递测试」里向 `mq.product.exchange` 发一条 `product.changed`，返回"已路由"，商品页可见缓存/索引变化；
8. 把口令改成错的再点「清空」，应弹 403「运维口令不正确」。

### 6.3 故障注入联动（可选）

```bash
curl -X POST 'http://localhost:8080/api/mq/chaos?scene=PRODUCT_CACHE_SYNC&failRate=100'
# 改一次商品价格 → 运维台「队列」里 mq.product.cache.retry.queue 应出现消息，5s 后回投，
# 3 次后进 mq.dlq.queue；关闭注入后可在「监控台 → 死信」回放
```

---

## 七、常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `ok=false`，提示无法连接 | broker 未启动 / 端口不对 | `docker compose up -d rabbitmq`，确认 15672 可达 |
| 401 认证失败 | 账号密码错，或该账号无 management tag | `rabbitmqctl set_user_tags <user> monitoring` |
| 404 `Object Not Found` | vhost 配错（`/` 需编码成 `%2F`） | 检查 `mq.practice.admin.vhost` |
| 队列列表为空但 broker 有队列 | vhost 不是 `/` | 改成实际 vhost |
| 页面数据不刷新 | 浏览器时钟/轮询被拦截 | 看 Network 面板，确认 `/api/mq/admin/queues` 200 |
| 清空按钮灰掉 | 非 `mq.` 前缀队列（白名单保护） | 到原生控制台操作 |
| 消息显示 `redelivered=true` | `ack_requeue_true` 读一次就会让消息带上重投标记 | 属正常现象；判断"消费失败重投"要结合 `x-death` 与重投速率 |
| 手发消息后进死信 | 缺少 `__TypeId__`，消费端无法确定反序列化类型 | 在「投递测试」里选对消息类型，或给 `properties.headers.__TypeId__` 赋值 |

---

## 八、和业务监控台的分工

| 页面 | 视角 | 回答的问题 |
|---|---|---|
| `/monitor` MQ 监控台 | 业务侧 | 这条消息**发出去没有**、**处理过没有**、死信能不能回放 |
| `/admin` RabbitMQ 运维台 | Broker 侧 | 消息**堆在哪个队列**、谁在消费、绑定对不对、速率多少 |
| :15672 原生控制台 | Broker 侧（全功能） | 建队列/改策略/看用户/查完整消息属性 |

排查顺序建议：**先看 `/monitor` 判断业务是否生效 → 再看 `/admin` 判断卡在哪个队列 → 最后必要时进原生控制台做运维操作。**
