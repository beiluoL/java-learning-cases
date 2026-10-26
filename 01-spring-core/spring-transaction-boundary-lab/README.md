# spring-transaction-boundary-lab

> **Q021-EXT｜Spring 事务代理返回边界**
> 一个可以真实跑起来、真实回滚、真实报错的实验工程，用来把下面这条链彻底钉死：
>
> **业务方法执行 → 内层事务代理 → rollback-only → 外层事务完成 → 真正回滚 → `UnexpectedRollbackException` → Controller 处理**

一句话结论先放这里：

> `return "SUCCESS"` 只代表**业务方法体结束**，不代表事务结束。
> 事务代理在拿到返回值**之后**才决定 COMMIT 还是 ROLLBACK；
> 如果共享事务已被标记 `rollback-only`，它会执行**真实 ROLLBACK** 并抛
> `UnexpectedRollbackException` 把返回值顶掉 —— 所以 Controller 进 catch，日志 C 永不执行。

---

## 一、技术栈与运行环境

| 项 | 版本 / 说明 |
|---|---|
| JDK | 17（实测 Corretto 17.0.14） |
| Spring Boot | 3.5.16（父 POM 固定） |
| Spring Framework | **6.2.19**（源码分析以此版本为准） |
| 持久层 | Spring JDBC + `JdbcTemplate`（无 JPA、无 MyBatis） |
| 数据库 | H2 **内存库**（无需安装 MySQL；`DB_CLOSE_DELAY=-1`） |
| 事务管理器 | Spring Boot 自动装配的 `JdbcTransactionManager`（`DataSourceTransactionManager` 子类），**真实连接、真实 ROLLBACK** |
| 测试 | JUnit 5 + `@SpringBootTest` + MockMvc + AssertJ |
| 构建 | Maven + **Maven Wrapper**（`./mvnw`，无需本机装 Maven） |
| 端口 | **8095**（8080 常被其它工程占用，故避开） |

> 全部使用真实 AOP 代理、真实事务管理器、真实数据库连接。
> **没有** Mock 事务、**没有**在 Controller 里手工 `throw new UnexpectedRollbackException()`、
> **没有**手工 `setRollbackOnly()` 造假、**没有**伪造日志。

---

## 二、目录结构（实际交付）

```text
spring-transaction-boundary-lab/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/maven-wrapper.properties
├── start.command / stop.command / restart.command     # macOS 双击一键启停
├── start.bat     / stop.bat     / restart.bat         # Windows 双击一键启停
├── README.md
├── docs/
│   ├── EXECUTION_FLOW.md                  # 第三关：真实执行流程 + 日志 + DB 状态 + 生活类比
│   ├── TRANSACTION_SOURCE_ANALYSIS.md     # 第四关：6.2.19 源码调用链（真实代码引用）
│   ├── INTERVIEW_TRAINING.md              # 第五关答案区：30s/90s/2-3min + 3 道变式题
│   ├── ACCEPTANCE_REPORT.md               # 验收报告（构建/测试/HTTP/DB 实测结果）
│   └── logs/                              # 三个场景的原始证据（trace / console / db）
│       ├── core-trace.txt   core-console.log   core-db.json
│       ├── test04-trace.txt test04-console.log test04-db.json
│       └── test05-trace.txt test05-console.log test05-db.json
└── src/
    ├── main/
    │   ├── java/com/example/txlab/
    │   │   ├── TransactionLabApplication.java     # 启动类（显式 @EnableTransactionManagement）
    │   │   ├── controller/
    │   │   │   ├── OrderController.java           # 日志 C / 日志 D 的所在，三个场景入口
    │   │   │   └── LabInspectionController.java   # 观察接口：轨迹 / DB 状态 / 重置
    │   │   ├── service/
    │   │   │   ├── PaymentService.java            # 核心：外层事务 + 日志 A + 日志 B（catch）
    │   │   │   ├── AuditService.java              # 核心：内层 REQUIRED，写审计后抛真实异常
    │   │   │   ├── SelfHealingAuditService.java   # 对照 Test04：内层自己 catch
    │   │   │   ├── RequiresNewAuditService.java   # 对照 Test05：内层 REQUIRES_NEW
    │   │   │   └── PaymentComparisonService.java  # 两个对照场景的外层事务方法
    │   │   ├── repository/
    │   │   │   ├── OrderRepository.java           # orders 表（不加 @Transactional）
    │   │   │   └── AuditRepository.java           # audit_log 表（不加 @Transactional）
    │   │   ├── support/
    │   │   │   └── LabDataResetter.java           # 数据重置（刻意不开事务，保证真落库）
    │   │   └── trace/
    │   │       ├── TraceRecorder.java             # 轻量执行轨迹记录器（带自增序号）
    │   │       ├── TransactionTrace.java          # 事务状态探针 + 事务同步回调
    │   │       └── TxSnapshot.java                # 事务状态快照（含字段语义说明）
    │   └── resources/
    │       ├── application.yml                    # H2 / 连接池 / 事务日志级别
    │       ├── schema.sql                         # 建表 + 中文 COMMENT
    │       └── data.sql                           # 3 个 PENDING 订单
    └── test/java/com/example/txlab/
        └── TransactionBoundaryIntegrationTest.java # Test01–Test05（不加 @Transactional）
```

---

## 三、快速开始

### 方式 A：双击脚本（推荐，无需命令行）

| 系统 | 启动 | 关闭 | 重启 |
|---|---|---|---|
| macOS | 双击 `start.command` | 双击 `stop.command` | 双击 `restart.command` |
| Windows | 双击 `start.bat` | 双击 `stop.bat` | 双击 `restart.bat` |

- 启动后窗口保持打开并实时跟随 `backend.log`；关闭窗口即自动停服。
- 命令行也可精确控制范围：`./start.command restart backend`、`./stop.command backend -y`。
- 只按 PID / 端口精准关停，不会 `pkill`/`taskkill` 误伤其它项目。

### 方式 B：命令行

```bash
# 构建 + 跑全部测试
./mvnw clean test

# 启动（端口 8095）
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8095
# 或者
./mvnw package -DskipTests && java -jar target/spring-transaction-boundary-lab.jar --server.port=8095
```

### 初始化数据库

无需手工操作：启动时 `schema.sql` + `data.sql` 自动执行（H2 内存库）。
需要人为复位时：

```bash
curl -X POST http://localhost:8095/api/lab/reset
```

---

## 四、实验地图（接口一览）

| 方法 | 路径 | 场景 | 预期结果 |
|---|---|---|---|
| POST | `/api/lab/pay?orderId=1` | **核心**：内层 REQUIRED 抛异常，外层 catch 吞掉 | HTTP 500，body `UnexpectedRollbackException`；订单仍 `PENDING`、审计 0 行 |
| POST | `/api/lab/pay/self-caught?orderId=2` | Test04：内层自己 catch，异常不外抛 | HTTP 200 `SUCCESS`；订单 `PAID`、审计 1 行 |
| POST | `/api/lab/pay/requires-new?orderId=3` | Test05：内层 REQUIRES_NEW，异常外抛 | HTTP 200 `SUCCESS`；订单 `PAID`、审计 0 行 |
| GET | `/api/lab/trace` | 查看**上一次实验**的真实执行轨迹（17 条事件） | 纯文本 |
| GET | `/api/lab/db` | 查看两张表的真实数据 | JSON |
| POST | `/api/lab/reset` | 清空审计表 + 三个订单恢复 `PENDING` | 纯文本摘要 |

---

## 第一关：先预测结果（别急着跑）

在**运行之前**，把你自己的答案写下来（8 题，核心场景 `/api/lab/pay`）。
答案在 `docs/INTERVIEW_TRAINING.md` 附录 A，跑完再对。

1. 日志 A 是否打印？
2. 日志 C 是否打印？
3. 日志 D 是否打印？
4. Controller 是否收到 `SUCCESS`？
5. 订单 `PAID` 是否提交？
6. 审计日志是否提交？
7. `rollback-only` 在哪个阶段产生？
8. 真正回滚在什么阶段发生？

参考记录格式：

```text
1. ___   2. ___   3. ___   4. ___
5. ___   6. ___   7. ___   8. ___
我的额外疑问：____________________
```

---

## 第二关：运行实验

```bash
# 1) 启动（任选其一）
./start.command                                                  # macOS 双击等效
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8095

# 2) 复位数据（可选，启动时已自动初始化）
curl -X POST http://localhost:8095/api/lab/reset

# 3) 请求前看数据库
curl -s http://localhost:8095/api/lab/db | python3 -m json.tool

# 4) 调核心接口（观察 HTTP 状态码与响应体）
curl -i -X POST 'http://localhost:8095/api/lab/pay?orderId=1'

# 5) 看真实执行轨迹
curl -s http://localhost:8095/api/lab/trace

# 6) 请求后看数据库（订单应仍为 PENDING，审计 0 行）
curl -s http://localhost:8095/api/lab/db | python3 -m json.tool

# 7) 两个对照场景
curl -X POST http://localhost:8095/api/lab/reset
curl -i -X POST 'http://localhost:8095/api/lab/pay/self-caught?orderId=2'
curl -X POST http://localhost:8095/api/lab/reset
curl -i -X POST 'http://localhost:8095/api/lab/pay/requires-new?orderId=3'

# 8) 执行全部自动化测试
./mvnw test

# 9) 单独执行某个场景
./mvnw test -Dtest='TransactionBoundaryIntegrationTest#test01_injectedBeansAreRealTransactionProxies'
./mvnw test -Dtest='TransactionBoundaryIntegrationTest#test02_logOrderAndRealRollbackPosition'
./mvnw test -Dtest='TransactionBoundaryIntegrationTest#test03_databaseStateIsReallyRolledBack'
./mvnw test -Dtest='TransactionBoundaryIntegrationTest#test04_exceptionCaughtInsideInnerMethod'
./mvnw test -Dtest='TransactionBoundaryIntegrationTest#test05_requiresNewInnerTransaction'
```

5 个测试各自守什么：

| 测试 | 守的东西 |
|---|---|
| Test01 | 注入的是**真代理**（`AopUtils.isAopProxy` + advisor 链上有 `TransactionInterceptor`），且内层确实"加入"外层（`newTx=false`） |
| Test02 | 日志顺序：A 有、C 无、D 是 `UnexpectedRollbackException`；真实回滚在 A 之后 D 之前（`beforeCompletion` < `afterCompletion(ROLLED_BACK)` < 日志D） |
| Test03 | 数据库真实结果：请求后 `orders=1` 仍 `PENDING`、`audit_log` 0 行（全新查询，非 Mock） |
| Test04 | 内层自捕获 → `beforeCommit` 出现、`afterCompletion(COMMITTED)`、订单 `PAID`、审计 1 行 |
| Test05 | REQUIRES_NEW → 内层 `newTx=true`、外层 `COMMITTED`、订单 `PAID`、审计 0 行 |

> 测试类**刻意不加** `@Transactional`：否则测试框架自己的回滚会掩盖业务事务真实结果。

---

## 第三关：观察与解释

完整版（含 15 步对照表、原始日志、数据库状态、生活类比）见 **`docs/EXECUTION_FLOW.md`**。
这里只放两个必须亲眼看到的片段。

**片段一：业务 return 之后才决定生死**（真实日志，`docs/logs/core-console.log`）

```text
c.e.txlab.service.PaymentService     - 日志A：准备返回 SUCCESS
o.s.t.i.TransactionInterceptor       - Completing transaction for [PaymentService.pay]
o.s.j.support.JdbcTransactionManager - Global transaction is marked as rollback-only but transactional code requested commit
o.s.j.support.JdbcTransactionManager - Initiating transaction rollback
o.s.j.support.JdbcTransactionManager - Rolling back JDBC transaction on Connection [...]
c.e.txlab.controller.OrderController - 日志D：UnexpectedRollbackException
```

**片段二：内层只贴标记，不回滚**

```text
o.s.j.support.JdbcTransactionManager - Participating in existing transaction
o.s.j.support.JdbcTransactionManager - Participating transaction failed - marking existing transaction as rollback-only
o.s.j.support.JdbcTransactionManager - Setting JDBC transaction [...] rollback-only
```

生活类比（详见 EXECUTION_FLOW.md 第六节）：业务方法是**柜员**，事务代理是**复核员**。
柜员可以先说"办好了"（return），但复核员看到档案袋封面上的"作废"贴纸（rollback-only），
照样会把整袋材料作废（ROLLBACK），然后追出来告诉你"刚才那单其实作废了"（`UnexpectedRollbackException`）。

---

## 第四关：源码级理解

完整版见 **`docs/TRANSACTION_SOURCE_ANALYSIS.md`**。骨架（Spring Framework **6.2.19**）：

```text
TransactionInterceptor#invoke
└─ TransactionAspectSupport#invokeWithinTransaction
   ├─ createTransactionIfNecessary → getTransaction（开事务 / 加入事务）
   ├─ proceedWithInvocation（执行业务方法体，可 return、可 catch）
   ├─ [有异常] completeTransactionAfterThrowing → rollback() → 参与事务只 doSetRollbackOnly
   ├─ finally cleanupTransactionInfo
   └─ commitTransactionAfterReturning → commit()
      └─ !shouldCommitOnGlobalRollbackOnly() && isGlobalRollbackOnly()
         → processRollback(status, unexpected=true)
            → beforeCompletion → doRollback()（真实回滚）→ afterCompletion(ROLLED_BACK)
            → throw new UnexpectedRollbackException("Transaction rolled back because it has been marked as rollback-only")
```

三个最关键的事实（都有真实源码 + 运行日志双重支撑）：

1. `commit()` 里 `shouldCommitOnGlobalRollbackOnly()` 默认 `false`，
   所以"global rollback-only"会直接改写分支去 `processRollback(..., true)`。
2. global 标记存在 `ConnectionHolder` 上，内外层共享同一个 `DataSourceTransactionObject`，所以标记会"传染"。
3. `REQUIRED` 参与事务时 `isNewTransaction()=false`，`processRollback` 只打标记；`Connection.rollback()` 由外层执行。

---

## 第五关：闭卷面试

题目见下，**答案在 `docs/INTERVIEW_TRAINING.md`**（单独文档，避免没跑实验就先看答案）。

1. 30 秒回答：为什么方法体 `return "SUCCESS"` 了还会抛 `UnexpectedRollbackException`？
2. 90 秒回答：把 7 步时序讲清楚（含"标记"与"回滚"不是同一时刻）。
3. 2–3 分钟回答：讲到源码判定点（`commit()` 的两个条件、`processRollback` 两个分支）、
   两条 `UnexpectedRollbackException` 消息的区别、工程上如何避免。
4. 变式 1：`try/catch` 移到内层方法**内部**，结果如何？
5. 变式 2：内层改 `REQUIRES_NEW` 且异常外抛，谁回滚？审计还在吗？连接怎么变？
6. 变式 3：外层**不 catch**，异常自然抛出，为什么就不是 `UnexpectedRollbackException` 了？

---

## 五、常见坑（本工程实测踩过，写下来省你的时间）

1. **`org.springframework.transaction` 日志级别开了却看不到 `Creating new transaction`**
   `AbstractPlatformTransactionManager` 用 `LogFactory.getLog(getClass())` 取 logger，
   实际实现类是 `JdbcTransactionManager` → logger 名是 `org.springframework.jdbc.support.JdbcTransactionManager`。
   本工程已按实测结论配置（`application.yml`）。
2. **`Getting/Completing transaction for [...]` 在 Spring 6.2 是 TRACE 级**，不是 DEBUG。
3. **事务完成阶段探针读到 `null`**
   拦截器先 `finally { cleanupTransactionInfo(txInfo); }`，再 `commitTransactionAfterReturning(...)`；
   进入完成阶段后 `TransactionAspectSupport.currentTransactionStatus()` 会抛 `NoTransactionException`。
   `newTx=null / rollbackOnly=null` 是"读不到"，不是"标记被清空"。
4. **`beforeCommit` 在核心场景一次都不出现** —— 因为根本没走 commit 分支。
   如果你在 trace 里看到了它，说明实验被改坏了。
5. **`REQUIRES_NEW` 需要至少 2 条连接**，本工程 Hikari `maximum-pool-size=5`。
   池子只有 1 条时的现象是"等连接超时"，很容易误判。
6. **测试方法不要加 `@Transactional`**，否则测试框架的回滚会掩盖真实提交/回滚结果。
7. **8080 端口常被占用**，本工程固定 8095；脚本启动时用命令行参数强制端口（优先级高于环境变量）。

---

## 六、验收状态

| 项 | 结果 |
|---|---|
| Maven 构建 | ✅ `BUILD SUCCESS`（`./mvnw clean package`） |
| 自动化测试 | ✅ **5/5 通过**（`Tests run: 5, Failures: 0, Errors: 0`） |
| Controller 实际请求 | ✅ HTTP 500 / body `UnexpectedRollbackException`（curl 实测） |
| 真实事务日志 | ✅ 框架 DEBUG/TRACE 全链可见（`docs/logs/*-console.log`） |
| 回滚后数据库 | ✅ 订单仍 `PENDING`、审计 0 行（`docs/logs/*-db.json`） |
| 关键源码解释 | ✅ `docs/TRANSACTION_SOURCE_ANALYSIS.md`（6.2.19 真实代码） |
| 一键脚本 | ✅ macOS `.command` 实测（启动 8s 就绪、关闭后端口释放）；Windows `.bat` 已生成但**未在 Windows 上执行** |

详细证据与限制见 **`docs/ACCEPTANCE_REPORT.md`**。
