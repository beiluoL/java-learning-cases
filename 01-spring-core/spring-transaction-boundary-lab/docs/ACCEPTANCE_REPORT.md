# 验收报告（ACCEPTANCE_REPORT）

> 生成时间：2026-10-26 23:2x（Asia/Shanghai）
> 原则：**只写真实执行过的结果**。未在目标平台执行过的（Windows 脚本）会明确标注。

---

## 1. 运行环境

| 项 | 实测值 |
|---|---|
| 操作系统 | macOS 15.7.7（aarch64） |
| JDK | OpenJDK 17.0.14（Amazon Corretto 17.0.14.7.1） |
| Maven | 本机 3.8.3；构建使用 **Maven Wrapper 3.9.16**（`./mvnw`） |
| Spring Boot | 3.5.16 |
| Spring Framework | **6.2.19**（`BOOT-INF/lib/spring-tx-6.2.19.jar` 实查） |
| 数据库 | H2 2.3.232 内存库 `jdbc:h2:mem:txlab` |
| 端口 | 8095（8080 被本机其它进程占用，已避开） |

---

## 2. Maven 构建

```text
$ ./mvnw clean test
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.653 s
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  3.543 s
```

```text
$ ./mvnw clean package
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: .../target/spring-transaction-boundary-lab.jar
[INFO] BUILD SUCCESS
-rw-r--r--  26M  target/spring-transaction-boundary-lab.jar
```

结论：**构建成功**，可执行 jar 可独立运行（`java -jar ... --server.port=8095`）。

---

## 3. 自动化测试（5/5）

| 用例 | 断言重点 | 耗时 | 结果 |
|---|---|---|---|
| `test01_injectedBeansAreRealTransactionProxies` | 注入对象是 CGLIB 事务代理；advisor 链含 `TransactionInterceptor`；外层 `newTx=true`、内层 `newTx=false` | 0.44s | ✅ |
| `test02_logOrderAndRealRollbackPosition` | 日志 A 有 / 日志 C 无 / 日志 D 是 `UnexpectedRollbackException`；`日志A < beforeCompletion < afterCompletion(ROLLED_BACK) < 日志D`；`beforeCommit` 不出现；afterCompletion 时回读 `PENDING` | 0.011s | ✅ |
| `test03_databaseStateIsReallyRolledBack` | 请求前 `PENDING`/0 行；请求后 `PENDING`/0 行（全新 SQL 查询） | 0.017s | ✅ |
| `test04_exceptionCaughtInsideInnerMethod` | 内层自捕获 → 外层 `COMMITTED`、订单 `PAID`、审计 1 行、日志 C 有 | 0.008s | ✅ |
| `test05_requiresNewInnerTransaction` | 内层 `newTx=true`；外层 `COMMITTED`、订单 `PAID`、审计 0 行；`rollbackOnly=false` | 0.011s | ✅ |

测试类未加 `@Transactional`，所有数据库断言都在请求结束后用独立查询获得。

> 已刻意验证的失败修复过程：首次运行时有 2 个用例失败（一是断言用的搜索串撞上了"注册同步回调"那条事件的文案；
> 二是 `日志B` 只写了 slf4j 日志未写入轨迹）。修正后重跑全绿，**没有放宽断言来凑通过**。

---

## 4. Controller 实际请求结果（curl 实测）

```text
$ curl -i -X POST 'http://localhost:8095/api/lab/pay?orderId=1'
HTTP/1.1 500
Content-Type: text/plain;charset=UTF-8
Content-Length: 27

UnexpectedRollbackException
```

```text
$ curl -X POST 'http://localhost:8095/api/lab/pay/self-caught?orderId=2'
HTTP=200  body=SUCCESS

$ curl -X POST 'http://localhost:8095/api/lab/pay/requires-new?orderId=3'
HTTP=200  body=SUCCESS
```

---

## 5. 真实事务日志与时间顺序

核心场景 `docs/logs/core-console.log`（真实控制台输出；仅过滤掉 `TraceRecorder - TRACE |` 这类与轨迹文件重复的打印行，其余原样）：

```text
JdbcTransactionManager - Creating new transaction with name [PaymentService.pay]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
JdbcTransactionManager - Acquired Connection [... conn0 ...] for JDBC transaction
JdbcTransactionManager - Switching JDBC Connection [... conn0 ...] to manual commit
TransactionInterceptor  - Getting transaction for [PaymentService.pay]
OrderRepository         - JDBC: UPDATE orders SET status='PAID' WHERE id=1 → 影响 1 行（尚未提交，可被回滚）
JdbcTransactionManager - Participating in existing transaction
TransactionInterceptor  - Getting transaction for [AuditService.saveAudit]
AuditRepository         - JDBC: INSERT INTO audit_log(order_id=1, action='PAY_AUDIT') → 影响 1 行（尚未提交，可被回滚）
AuditService            - AuditService.saveAudit 业务校验失败，准备抛出 RuntimeException(orderId=1)
TransactionInterceptor  - Completing transaction for [AuditService.saveAudit] after exception: java.lang.RuntimeException: 模拟审计业务异常
JdbcTransactionManager - Participating transaction failed - marking existing transaction as rollback-only
JdbcTransactionManager - Setting JDBC transaction [... conn0 ...] rollback-only
PaymentService          - 日志B：外层捕获审计异常，异常类型=java.lang.RuntimeException
PaymentService          - 日志A：准备返回 SUCCESS
TransactionInterceptor  - Completing transaction for [PaymentService.pay]
JdbcTransactionManager - Global transaction is marked as rollback-only but transactional code requested commit
JdbcTransactionManager - Initiating transaction rollback
JdbcTransactionManager - Rolling back JDBC transaction on Connection [... conn0 ...]
JdbcTransactionManager - Releasing JDBC Connection [... conn0 ...] after transaction
OrderController         - 日志D：UnexpectedRollbackException
```

执行轨迹（`docs/logs/core-trace.txt`，节选关键 6 条，完整 17 条见文件）：

```text
[003] TX_OUTER      【步骤2】外层事务已开启                ⇢ newTx=true
[008] TX_INNER      【步骤4/5】内层加入外层事务（无新事务）  ⇢ newTx=false, resources=1
[012] TX_OUTER      【步骤9】日志B：外层 catch 捕获异常     ⇢ rollbackOnly=true
[014] RETURN        【步骤10】日志A 已打印，即将 return     ⇢ rollbackOnly=true
[016] TX_SYNC       【步骤12】afterCompletion(ROLLED_BACK)  ⇢ 回读 orders.status=PENDING
[017] CONTROLLER    【步骤14】日志D：UnexpectedRollbackException
```

**结论**：标记（step 8）早于业务 return；真实回滚与 `afterCompletion` 晚于业务 return、早于 Controller 捕获异常。

---

## 6. 回滚后的数据库验证（真实查询）

| 场景 | `orders` 最终状态 | `audit_log` | 证据文件 |
|---|---|---|---|
| 核心（orderId=1） | `(1,PENDING) (2,PENDING) (3,PENDING)` | 空（0 行） | `docs/logs/core-db.json` |
| Test04（orderId=2） | `(2,PAID)`，其余 PENDING | 1 行 `PAY_AUDIT_SELF_CAUGHT` | `docs/logs/test04-db.json` |
| Test05（orderId=3） | `(3,PAID)`，其余 PENDING | 空（0 行，内层独立事务已回滚） | `docs/logs/test05-db.json` |

Test05 的连接证据（`docs/logs/test05-console.log`）：

```text
Creating new transaction ... [payWithRequiresNewAudit]              ← conn0
Suspending current transaction, creating new transaction with name [RequiresNewAuditService.saveAudit]
Acquired Connection [... conn1 ...] for JDBC transaction            ← 第二条连接
Rolling back JDBC transaction on Connection [... conn1 ...]         ← 只回滚 conn1
Resuming suspended transaction after completion of inner transaction
Committing JDBC transaction on Connection [... conn0 ...]           ← 外层照常提交
```

---

## 7. 源码核对结果

已下载并逐段核对 `spring-tx-6.2.19-sources.jar` / `spring-jdbc-6.2.19-sources.jar`，
确认下列方法与本项目观察到的日志一一对应（详见 `docs/TRANSACTION_SOURCE_ANALYSIS.md`）：

| 结论 | 源码位置（6.2.19） |
|---|---|
| `commit()` 在 global rollback-only 时改走回滚 | `AbstractPlatformTransactionManager#commit`（第 735 行；判定在第 750-755 行） |
| `shouldCommitOnGlobalRollbackOnly()` 默认 `false` | 同文件，第 1228 行 |
| global 标记来自底层事务对象 | `DefaultTransactionStatus#isGlobalRollbackOnly`（第 185 行） |
| 参与事务失败只标记不回滚 | `processRollback` 参与分支（第 901-906 行），`doSetRollbackOnly`（`DataSourceTransactionManager` 第 367 行） |
| 标记落在 `ConnectionHolder` | `DataSourceTransactionObject#setRollbackOnly/isRollbackOnly`（第 509-516 行） |
| 真实回滚 → afterCompletion → 抛 URE | `processRollback`（第 875 行起；`doRollback` 第 897 行；`triggerAfterCompletion` 第 930 行；抛异常第 936-940 行） |
| `REQUIRES_NEW` 挂起 + 新事务 | `handleExistingTransaction`（第 445-455 行） |
| 参与事务不新建同步集合 | `newTransactionStatus`（第 561-570 行，`actualNewSynchronization`） |
| 完成阶段读不到 `TransactionStatus` | `TransactionAspectSupport#invokeWithinTransaction`（第 388 行 `cleanupTransactionInfo`，第 416 行 `commitTransactionAfterReturning`） |

---

## 8. 一键启停脚本

**macOS（已实测）**

```text
$ ./start.command
[ OK ] 后端已启动（PID 99653）
[ OK ] 后端就绪: http://localhost:8095        ← 实测约 8s
$ ./stop.command -y
[INFO] 停止后端进程 (PID 99653) ...
[ OK ] 后端已停止（端口 8095 已释放）
$ lsof -nP -iTCP:8095 -sTCP:LISTEN            ← 无输出，端口已释放
```

**Windows（未在 Windows 上执行，仅静态生成）**
`start.bat / stop.bat / restart.bat` 由同一套已验证模板生成（`crossplatform-startstop` 模式 B），
使用 `taskkill /PID` + `netstat` 端口定位 + `wmic`（限 `java.exe`）兜底；
本机为 macOS，**未实际双击验证**，请在 Windows 上首次运行时留意控制台输出。

两个脚本实现的约定：

- 启动前检测端口/进程，已在运行则**先停后起**；
- 启动后打印访问地址（`http://localhost:8095`）与日志路径；
- 关闭只按 **PID / 端口**、以及"仅限 java 进程"的签名兜底，**不使用裸 `pkill -f` / `taskkill /IM java.exe`**；
- 双击场景窗口保持（`tail -f` 跟随日志；Windows 末尾 `pause`）。

> 实测中发现并修正的两个真实缺陷（记录在此以便复现同类问题）：
> 1. 模板占位符 `SERVICES_ENTRIES=("")` 在 bash 里长度是 1，会被当成一个空服务去启动 →
>    无中间件项目必须写成 `SERVICES_ENTRIES=()`；
> 2. 原模板的 `pkill -f "$BACKEND_SIG"` 兜底会把命令行里恰好含该字符串的 shell 一起杀掉
>    → 已改为"先 `pgrep` 再校验 `ps` 首部是 java"的精确清理。

---

## 9. 交付清单

| 交付物 | 位置 | 状态 |
|---|---|---|
| 完整可运行工程 | `spring-transaction-boundary-lab/`（Java 源码 1331 行 + 测试） | ✅ |
| Maven 构建 | `./mvnw clean package` → `BUILD SUCCESS` | ✅ |
| 自动化测试 | 5 个场景，5/5 通过 | ✅ |
| Controller 实际请求结果 | HTTP 500 `UnexpectedRollbackException`（curl 实测） | ✅ |
| 真实事务日志与时间顺序 | `docs/logs/*-console.log`、`docs/logs/*-trace.txt` | ✅ |
| 回滚后数据库验证 | `docs/logs/*-db.json` | ✅ |
| 关键源码解释 | `docs/TRANSACTION_SOURCE_ANALYSIS.md` | ✅ |
| 中文学习手册 | `README.md`（五关）+ `docs/EXECUTION_FLOW.md` | ✅ |
| 三种面试标准话术 | `docs/INTERVIEW_TRAINING.md`（30s / 90s / 2–3min + 3 变式题） | ✅ |
| 最终验收报告 | 本文件 | ✅ |
| 一键启停脚本 | `start/stop/restart.command` + `.bat` | ✅（macOS 实测 / Windows 未执行） |

---

## 10. 已知限制（不影响本实验结论，但需要如实说明）

1. **数据库是 H2 而非 MySQL**：事务语义（BEGIN / COMMIT / ROLLBACK、`Connection.rollback()`）与 MySQL 一致，
   但隔离级别默认不同（H2 默认 `READ COMMITTED`，MySQL InnoDB 默认 `REPEATABLE READ`），
   本实验不依赖"另一个连接能否读到未提交数据"，因此结论不受影响。
2. **Windows 脚本未在 Windows 上执行**（本机 macOS）。
3. **单线程/单请求验证**：未覆盖并发下的 `rollback-only` 竞争、连接池耗尽等场景。
4. **未涉及分布式事务**（`JtaTransactionManager`、Seata 等），结论仅限本地单数据源事务。
5. 源码行号仅对 **6.2.19** 有效；换版本请按方法名重新定位（文档第 8 节给了 grep 命令）。
