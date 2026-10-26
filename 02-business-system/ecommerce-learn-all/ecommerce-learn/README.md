# 电商与零售场景 · Spring Boot 企业级学习项目

> 一个**企业级结构、便于二次开发**的电商/零售学习模板，覆盖商品、订单、购物车、用户中心、后台管理与秒杀。
> 后端 Spring Boot 3.3 + Java 17，前端 Vue 3 + Vite + Element Plus。
> 技术点包含：**JWT 鉴权、Redis 缓存、高并发秒杀（Redis + Lua 原子扣减）、分布式事务（Seata）、支付接口对接（模拟网关）**。

---

## 1. 技术栈

| 层 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.3.4 / Java 17 |
| 持久层 | MyBatis-Plus 3.5.7（雪花 ID、逻辑删除、分页、乐观锁） |
| 缓存 / 中间件 | Redis 7（缓存、秒杀库存、Lua、分布式锁） |
| 安全 | JWT（jjwt 0.12）+ 自定义拦截器（无状态鉴权） |
| 接口文档 | Knife4j 4.5（OpenAPI3，**jakarta** starter 适配 Spring Boot 3） |
| 分布式事务 | Seata 2.0（AT 模式，可选启用） |
| 数据库 | MySQL 5.7+（Connector/J 8.4.0，兼顾 5.7 与 8.0） |
| 前端 | Vue 3 + Vite 5 + Pinia + Vue Router + Element Plus + Axios |

---

## 2. 目录结构（后端）

```
ecommerce-learn/
├── pom.xml
├── docker-compose.yml          # 一键拉起 MySQL / Redis / Seata
├── Dockerfile
└── src/main/
    ├── java/com/ecommerce/learn/
    │   ├── EcommerceLearnApplication.java
    │   ├── common/             # 通用层：Result/PageResult、异常、常量、RedisUtil、雪花ID
    │   ├── config/             # MyBatisPlus / Redis / CORS / Async / WebMvc / 密码编码器
    │   ├── security/           # JwtTokenProvider / JwtInterceptor / UserContext(ThreadLocal)
    │   └── module/             # 业务模块（垂直拆分，互不耦合）
    │       ├── user/           # 注册、登录、JWT、个人中心
    │       ├── product/        # SPU/SKU、分类（树+缓存）、品牌、检索、后台管理
    │       ├── cart/           # 购物车
    │       ├── order/          # 订单创建、支付、取消、发货、确认收货
    │       ├── pay/            # 支付抽象（模拟支付宝/微信）+ 回调
    │       ├── seckill/        # 秒杀（Lua 原子扣减 + 异步下单 + 分布式事务演示）
    │       └── admin/          # 后台看板统计
    └── resources/
        ├── application.yml / application-dev.yml
        ├── db/schema.sql       # 建表 + 演示数据
        ├── lua/seckill.lua     # 秒杀原子扣减脚本
        └── mapper/             # MyBatis XML（按需扩展）
```

每一层职责清晰：**controller（入参校验/路由）→ service（业务逻辑/事务）→ mapper（数据访问）**，
实体继承 `BaseEntity`（id/createdAt/updatedAt/deleted 自动填充 + 逻辑删除），统一返回 `Result<T>`。

---

## 3. 快速运行

### 3.0 一键启动（推荐，macOS 双击运行）
项目根目录提供两个脚本，双击即可运行。**脚本默认对接本机已运行的 MySQL(3306) / Redis(6379)**（不依赖 docker）：
- `start.command` —— 先探活 MySQL/Redis → 清理旧实例 → 启动后端(8080) + 前端(5173)，并在控制台打印访问地址。
- `stop.command`  —— 干净关闭前后端进程（不动本机 MySQL/Redis）。

> 兼容性说明：脚本执行时会 `unset SERVER__PORT`（避免某些沙箱环境注入的变量把 `server.port` 覆盖成随机端口），并显式指定 `--server.port=8080`。
> 若不想用脚本，也可按下面 3.2~3.3 手动启动。

### 3.1 准备数据库（已导入可跳过）
`schema.sql` 已随本项目在本机 MySQL 导入（库名 `ecommerce_learn`）。如需重建：
```bash
mysql -uroot -p -h127.0.0.1 -P3306 < src/main/resources/db/schema.sql
```
> 本机 MySQL 为 5.7，已把 `mysql-connector-j` 降到 **8.4.0**（Connector/J 9.x 已不支持 5.7）。
> 想用 docker 也可：`docker compose up -d` 会拉起 mysql/redis/seata。

### 3.2 启动后端
```bash
mvn spring-boot:run
# 或：mvn package && java -jar target/ecommerce-learn.jar
```
- 接口前缀：`http://localhost:8080/api`
- 接口文档（Knife4j）：`http://localhost:8080/doc.html`

### 3.3 启动前端
```bash
cd ecommerce-learn-vue
npm install
npm run dev                     # http://localhost:5173 （Vite 已代理 /api → 8080）
```

### 3.4 体验流程
1. 浏览器打开 `http://localhost:5173` → 注册账号 → 自动登录。
2. 进入「商品」浏览/搜索，点进详情加入购物车或直接购买。
3. 购物车结算 → 模拟支付（前端直接回调 `/api/pay/notify` 标记已支付）。
4. 「我的订单」「秒杀」体验下单与高并发抢购。
5. **进入后台**：把该用户角色改为 `ADMIN`：
   ```sql
   UPDATE t_user SET role='ADMIN' WHERE username='你的用户名';
   ```
   刷新后导航出现「后台」，可看数据看板、管理商品/订单/秒杀。

> 默认 `seata.enabled=false`，主流程使用本地事务即可离线运行；
> 若要体验分布式事务，启动 `docker compose` 中的 seata 并将 `application.yml` 的 `seata.enabled` 改为 `true`，
> 再把 `SeckillAsyncService` 上的 `@Transactional` 换成 `@GlobalTransactional`（代码注释已标注）。

### 3.5 本机运行已验证 · 踩坑与修复
本项目已在本机（macOS + MySQL 5.7 + Redis 7）**实测启动并跑通全链路**，过程中修复的关键点：
1. **Connector/J 版本**：Spring Boot 3.3 默认带 Connector/J 9.x，已不支持 MySQL 5.7；已在 `pom.xml` 固定 `mysql-connector-j.version=8.4.0`。
2. **Knife4j 命名空间**：Spring Boot 3 用 `jakarta.servlet`，必须用 `knife4j-openapi3-jakarta-spring-boot-starter`（非 `knife4j-openapi3-spring-boot-starter`，后者引用 `javax.servlet` 会启动失败）。
3. **context-path 不要和控制器前缀重复**：`application.yml` 未设置 `server.servlet.context-path`，控制器统一以 `/api/...` 作为类级前缀，避免 `/api/api/...` 双前缀导致 404。
4. **沙箱环境变量**：某些运行环境会注入 `SERVER__PORT=0`，经 Spring 宽松绑定覆盖 `server.port` 造成随机端口；`start.command` 已 `unset` 并显式指定 8080。

---

## 4. 核心设计讲解（学习重点）

### 4.1 JWT 无状态鉴权
- 登录成功后由 `JwtTokenProvider` 签发 `accessToken`（ HS256，含 userId/username/role ）。
- `JwtInterceptor` 拦截 `/api/**`，校验令牌并写入 `UserContext`（ThreadLocal）；
  `/api/admin/**` 额外要求 `role=ADMIN`。
- 白名单（免鉴权）：登录注册、支付回调、商品/分类查询、秒杀列表、Knife4j 文档。

### 4.2 Redis 缓存
- 商品详情 `product:detail:{spuId}`、分类树 `category:tree` 均缓存并设 TTL；
  写操作后删除对应缓存，保证一致性。
- `RedisUtil` 同时提供字符串/对象(JSON)/计数/分布式锁/Set/Hash 等通用操作。

### 4.3 高并发秒杀（防超卖）
1. 活动保存时把库存预热到 Redis。
2. 抢购时执行 `seckill.lua`：**原子地**判断库存、扣减、记录已购用户（防重复购买），一次 RT 内完成。
3. 扣减成功后**异步线程池**落库订单，不阻塞抢购主链路。
4. 订单落库时再对 DB 库存做「条件更新」兜底，彻底杜绝超卖。

### 4.4 分布式事务（Seata）
- 秒杀下单跨「库存」与「订单」两类资源，代码已用 `@Transactional` 标注本地事务。
- 注释中给出升级为 `@GlobalTransactional`（Seata AT 模式）的方法：引入 seata-server 即可实现跨服务全局回滚。

### 4.5 支付对接
- `PayService` 抽象支付渠道；当前为 `MockPayServiceImpl`（返回模拟支付链接/二维码）。
- 真实接入只需新增 `AlipayPayServiceImpl` / `WechatPayServiceImpl` 并校验回调签名与金额，
  回调统一走 `/api/pay/notify` → `OrderService.markPaid`（幂等）。

---

## 5. 主要接口一览

| 模块 | 方法 | 路径 |
| --- | --- | --- |
| 认证 | POST | `/api/auth/register` `/api/auth/login` `/api/auth/refresh` |
| 用户 | GET/PUT | `/api/user/profile` |
| 商品 | GET | `/api/products`（检索）、`/api/products/{id}`、`/api/categories`（树） |
| 后台商品 | POST/DELETE | `/api/admin/product/save`、`/api/admin/product/{id}` |
| 购物车 | POST/PUT/DELETE/GET | `/api/cart/*` |
| 订单 | POST/GET | `/api/orders/create`、`/api/orders/my`、`/api/orders/{id}/pay` |
| 支付 | POST | `/api/pay/notify`（公开回调） |
| 秒杀 | GET/POST | `/api/seckill/activity/list`、`/api/seckill/{id}/buy` |
| 后台 | GET/POST | `/api/admin/dashboard/stats`、`/api/admin/orders/list`、`/api/admin/seckill/save` |

> 完整字段与示例见 Knife4j 文档：`http://localhost:8080/doc.html`

---

## 6. 二次开发指引
- **新增业务模块**：在 `module/` 下复制一个现有模块（entity/mapper/service/controller），继承 `BaseEntity`、返回 `Result<T>` 即可。
- **接真实支付**：实现 `PayService` 的支付宝/微信实现类，替换 `MockPayServiceImpl` 的 Bean。
- **微服务拆分**：当前为单体，可把 `user/product/order/seckill` 拆为独立 Spring Boot 应用，
  用 `@GlobalTransactional` 串联跨服务事务（需 Seata）。
- **检索升级**：商品检索当前走 MySQL `LIKE`，量大时可替换为 Elasticsearch（接口层不变）。
- **可观测性**：生产建议接入 Spring Boot Actuator + Prometheus + 日志中心。

---

## 7. 后续可扩展
- 优惠券 / 积分 / 会员等级
- 商品评价与评分
- 消息队列（RabbitMQ/Kafka）解耦下单与库存
- 全链路压测（秒杀极限优化：令牌桶限流、库存分段）
- 灰度发布与容器化部署（K8s）
