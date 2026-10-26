# 真实执行流程（EXECUTION_FLOW）

> 本文所有「轨迹」与「日志」都是本机**真实运行**产物，不是示意。
> 原始文件见 `docs/logs/`：
>
> | 文件 | 内容 |
> |---|---|
> | `core-trace.txt` | 核心场景的 17 条执行事件（本工程 TraceRecorder 记录） |
> | `core-console.log` | 核心场景的框架/业务日志（Spring 事务管理器 + 拦截器 + JDBC） |
> | `core-db.json` | 核心场景请求**之后**的数据库真实状态 |
> | `test04-*` / `test05-*` | 两个对照场景的同类证据 |
>
> 复现方式见 README「第二关：运行实验」。

---

## 一、一句话结论

**`return "SUCCESS"` 只是「业务方法体结束」，不是「事务结束」。**

`@Transactional` 方法被调用时，真正执行的是 `TransactionInterceptor#invoke` 这条环绕通知：

```text
开启事务 → 执行业务方法体（其中可以 return、可以 catch 异常）
        → 拿到返回值之后，再决定 COMMIT / ROLLBACK
        → 事务完成（可能在这里抛异常，把返回值丢掉）
        → 才把返回值交给调用方
```

本实验里，业务方法体正常 return 了，但事务代理随后发现共享事务已被标记 `rollback-only`，
于是它执行**真实 ROLLBACK**，并抛 `UnexpectedRollbackException` 取代返回值。
Controller 的 `try` 块因此进入 `catch`：**日志 C 永远不执行，日志 D 执行。**

---

## 二、15 个步骤与证据对照表

下表的「证据」列为真实观测点，可直接在 `docs/logs/core-trace.txt` 与 `core-console.log` 里对上。

| # | 发生的事 | 代码位置 | 真实证据（事件号 / 日志） |
|---|---|---|---|
| 1 | Controller 调用 **事务代理**（不是裸对象） | `OrderController#pay` | trace `[002]` |
| 2 | 外层事务开启：拿连接、关自动提交 | `TransactionInterceptor` 调 `getTransaction` | 日志 `Creating new transaction with name [PaymentService.pay]` / `Acquired Connection [...]` / `Switching JDBC Connection [...] to manual commit`；trace `[003]` `newTx=true` |
| 3 | 订单 UPDATE 执行成功，**未提交** | `PaymentService#pay` → `OrderRepository#updateStatus` | 日志 `UPDATE orders ... → 影响 1 行（尚未提交）`；trace `[005] [006]` |
| 4 | 内层 `REQUIRED` **加入**外层事务（没有第二次 BEGIN） | `AuditService#saveAudit` 代理 | 日志 `Participating in existing transaction`；trace `[008]` `newTx=false, resources=1`（连接没变） |
| 5 | 审计 INSERT 执行成功，**未提交** | `AuditRepository#insert` | 日志 `INSERT INTO audit_log ... → 影响 1 行（尚未提交）`；trace `[009] [010]` |
| 6 | 内层方法抛出 `RuntimeException` | 同上 | 日志 `准备抛出 RuntimeException`；trace `[011]` |
| 7 | 异常先经过**内层事务代理** | `TransactionAspectSupport#completeTransactionAfterThrowing` | 日志 `Completing transaction for [AuditService.saveAudit] after exception: java.lang.RuntimeException: 模拟审计业务异常` |
| 8 | 共享事务被标记 **rollback-only**（只是打标记，**没有** `Connection.rollback()`） | `AbstractPlatformTransactionManager#processRollback` 的「参与事务」分支 → `doSetRollbackOnly` | 日志 `Participating transaction failed - marking existing transaction as rollback-only` / `Setting JDBC transaction [...] rollback-only` |
| 9 | 异常才传到外层 `catch` | `PaymentService#pay` | 日志 `日志B：外层捕获审计异常`；trace `[012]` `rollbackOnly=true` |
| 10 | 日志 A 打印，方法体 `return "SUCCESS"` | 同上 | 日志 `日志A：准备返回 SUCCESS`；trace `[014]` |
| 11 | **外层事务代理开始处理事务完成**（此时才判提交还是回滚） | `TransactionAspectSupport#commitTransactionAfterReturning` → `commit()` | 日志 `Completing transaction for [PaymentService.pay]` + `Global transaction is marked as rollback-only but transactional code requested commit`；trace `[015]` `beforeCompletion` |
| 12 | **真实数据库 ROLLBACK** | `commit()` 判定 → `processRollback(status, true)` → `doRollback` | 日志 `Initiating transaction rollback` / `Rolling back JDBC transaction on Connection [...]`；trace `[016]` `afterCompletion(status=ROLLED_BACK(1))`，同连接回读 `orders.status=PENDING` |
| 13 | 抛出 `UnexpectedRollbackException`（**紧跟 afterCompletion 之后**） | `processRollback` 末尾 | 异常消息 `Transaction rolled back because it has been marked as rollback-only` |
| 14 | Controller `catch` 打印日志 D | `OrderController#pay` | 日志 `日志D：UnexpectedRollbackException`；trace `[017]` |
| 15 | 日志 C **不执行** | `OrderController#pay` | trace 中不存在「日志C」事件（Test02 断言此处为空） |

> 注意步骤 8 与 9 的先后：**rollback-only 是在异常经过内层代理时就标记好的，早于外层 catch**。
> 代码里 `【步骤8验证】` 那一条是「回读验证」，不是「此刻才标记」。

---

## 三、真实执行轨迹（原始输出，`docs/logs/core-trace.txt`）

```text
================ 真实执行轨迹：核心场景 POST /api/lab/pay?orderId=1 ================
[001] RUN         ===== 开始实验：核心场景 POST /api/lab/pay?orderId=1 =====
[002] CONTROLLER  【步骤1】Controller 调用 paymentService.pay(1)：注入进来的是 Spring 事务代理，不是裸对象
[003] TX_OUTER    【步骤2】进入 PaymentService.pay()：外层事务已由事务代理开启（newTx=true 表示这条物理事务归本方法所有）  ⇢ tx{active=true, sync=true, newTx=true, rollbackOnly=false, resources=1, name=...PaymentService.pay}
[004] TX_SYNC     已注册事务完成同步回调（用于捕获 beforeCompletion / afterCompletion 的真实时机）
[005] SQL         执行 UPDATE orders SET status='PAID' WHERE id=1 → 影响 1 行（事务未提交）
[006] TX_OUTER    【步骤3】订单 UPDATE 已经执行，但事务还没有提交（此刻回滚，这行改动会消失）
[007] TX_OUTER    【步骤4】准备调用 auditService.saveAudit()：目标是 AuditService 的 Spring 代理
[008] TX_INNER    【步骤4/5】进入 AuditService.saveAudit()：内层事务代理已加入外层事务（newTx=false 表示没有新开物理事务）  ⇢ tx{active=true, sync=true, newTx=false, rollbackOnly=false, resources=1, name=...PaymentService.pay}
[009] SQL         执行 INSERT INTO audit_log(order_id=1, action='PAY_AUDIT') → 影响 1 行（事务未提交）
[010] TX_INNER    【步骤5】审计 INSERT 已执行，事务仍未提交（同一条连接里能看到，其他连接看不到）
[011] EXCEPTION   【步骤6】内层方法即将抛出 RuntimeException（真实业务异常，不是伪造）
[012] TX_OUTER    【步骤9】日志B：外层 catch 捕获 RuntimeException，异常被吞掉，方法体继续往下执行  ⇢ tx{... rollbackOnly=true ...}
[013] ROLLBACK_ONLY 【步骤8验证】读共享事务状态：rollbackOnly=true 说明内层拦截器已经标记过，但此时数据库并没有回滚，订单 UPDATE 仍在未提交状态
[014] RETURN      【步骤10】日志A 已打印，方法体即将 return "SUCCESS"（业务逻辑到这里就结束了）  ⇢ tx{... rollbackOnly=true ...}
[015] TX_SYNC     【步骤11】beforeCompletion：外层事务进入完成阶段（此刻业务方法早已 return 完毕）  ⇢ tx{... newTx=null, rollbackOnly=null ...}
[016] TX_SYNC     【步骤12】afterCompletion(status=ROLLED_BACK(1))：数据库层操作已执行完毕；此刻用同一条（已结束的）连接回读 → orders.status=PENDING
[017] CONTROLLER  【步骤14】日志D 执行：Controller 捕获 org.springframework.transaction.UnexpectedRollbackException，message=Transaction rolled back because it has been marked as rollback-only
==================================================================
```

三个关键读点：

1. **`[008] newTx=false`**：内层确实进了事务，但没开新物理事务 —— 它是"搭车"，不是"另开车"。
2. **`[013] rollbackOnly=true`** 出现在 `[014] 日志A` **之前**：标记发生在返回之前；而数据库**还没**回滚。
3. **`[015] newTx=null, rollbackOnly=null`** 不是"标记消失了"，而是**读不到**了：
   `TransactionInterceptor` 在 `invokeWithinTransaction` 里先 `finally { cleanupTransactionInfo(txInfo); }`，
   再 `commitTransactionAfterReturning(txInfo)`。等进入事务完成阶段，线程上已经没有
   `TransactionInfo`，`TransactionAspectSupport.currentTransactionStatus()` 会抛
   `NoTransactionException`，所以探针只能填 `null`（`isActualTransactionActive()` 仍为 true）。
   这正是 README「常见坑」里专门写的一条。

---

## 四、真实框架日志（原始输出节选，`docs/logs/core-console.log`）

```text
o.s.j.support.JdbcTransactionManager - Creating new transaction with name [PaymentService.pay]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
o.s.j.support.JdbcTransactionManager - Acquired Connection [HikariProxyConnection@1269396565 wrapping conn0: ...] for JDBC transaction
o.s.j.support.JdbcTransactionManager - Switching JDBC Connection [...] to manual commit
o.s.t.i.TransactionInterceptor       - Getting transaction for [PaymentService.pay]
c.e.txlab.repository.OrderRepository - JDBC: UPDATE orders SET status='PAID' WHERE id=1 → 影响 1 行（尚未提交，可被回滚）
o.s.j.support.JdbcTransactionManager - Participating in existing transaction
o.s.t.i.TransactionInterceptor       - Getting transaction for [AuditService.saveAudit]
c.e.txlab.repository.AuditRepository - JDBC: INSERT INTO audit_log(order_id=1, action='PAY_AUDIT') → 影响 1 行（尚未提交，可被回滚）
c.e.txlab.service.AuditService       - AuditService.saveAudit 业务校验失败，准备抛出 RuntimeException(orderId=1)
o.s.t.i.TransactionInterceptor       - Completing transaction for [AuditService.saveAudit] after exception: java.lang.RuntimeException: 模拟审计业务异常
o.s.j.support.JdbcTransactionManager - Participating transaction failed - marking existing transaction as rollback-only
o.s.j.support.JdbcTransactionManager - Setting JDBC transaction [HikariProxyConnection@... conn0 ...] rollback-only
c.e.txlab.service.PaymentService     - 日志B：外层捕获审计异常，异常类型=java.lang.RuntimeException
c.e.txlab.service.PaymentService     - 日志A：准备返回 SUCCESS
o.s.t.i.TransactionInterceptor       - Completing transaction for [PaymentService.pay]
o.s.j.support.JdbcTransactionManager - Global transaction is marked as rollback-only but transactional code requested commit
o.s.j.support.JdbcTransactionManager - Initiating transaction rollback
o.s.j.support.JdbcTransactionManager - Rolling back JDBC transaction on Connection [HikariProxyConnection@... conn0 ...]
o.s.j.support.JdbcTransactionManager - Releasing JDBC Connection [...] after transaction
c.e.txlab.controller.OrderController - 日志D：UnexpectedRollbackException
```

请注意这两行的**位置关系**，它们就是全部答案：

```text
c.e.txlab.service.PaymentService     - 日志A：准备返回 SUCCESS          ← 业务方法体结束
o.s.j.support.JdbcTransactionManager - Global transaction is marked ... ← 事务代理这时才做决定
o.s.j.support.JdbcTransactionManager - Initiating transaction rollback  ← 真实回滚
c.e.txlab.controller.OrderController - 日志D：UnexpectedRollbackException
```

---

## 五、数据库真实状态（`docs/logs/core-db.json`）

请求**前**（`data.sql` 初始化）：

```text
orders:      (1, PENDING) (2, PENDING) (3, PENDING)
audit_log:   （空）
```

请求**后**（接口返回 HTTP 500 `UnexpectedRollbackException`）：

```json
{
  "orders": [
    { "ID": 1, "STATUS": "PENDING" },
    { "ID": 2, "STATUS": "PENDING" },
    { "ID": 3, "STATUS": "PENDING" }
  ],
  "ordersCount": 3,
  "auditLogs": [],
  "auditLogCount": 0
}
```

订单 1 仍是 `PENDING`（UPDATE 被回滚），审计表仍是 0 行（INSERT 被同一次回滚撤销）。
**注意：审计 INSERT 并不是"被内层自己回滚掉的"，它与订单 UPDATE 一起被外层那一次 ROLLBACK 撤销。**

---

## 六、生活类比：一次"复核不通过"的柜台业务

把一次 `@Transactional` 调用想成**银行柜台办一笔业务**：

| 概念 | 类比 |
|---|---|
| 业务方法 `pay()` | 柜员在你的单子上逐项填写 |
| Spring AOP 代理 | 柜员背后的**复核员**：柜员办完要交给复核员盖章，这单才算生效 |
| 外层事务（REQUIRED，newTx=true） | 这单业务归属的**主档案袋**（一个物理事务、一条连接） |
| 内层 `REQUIRED` | 复核员把**同一份档案袋**拿给另一位柜员接着填 —— 没有另开档案袋（`newTx=false`） |
| 内层 `REQUIRES_NEW` | 另开**独立档案袋**（第二条连接），原档案袋先封存起来（`Suspending current transaction`） |
| rollback-only 标记 | 内层柜员填错后，在**主档案袋封面**贴一张红色"作废"贴纸（只贴纸，**没有**销毁档案里的材料） |
| 外层 `catch` | 主柜员看到贴纸，说"算了，这单我继续走完"，然后对你说「办好了，SUCCESS」 |
| `return "SUCCESS"` | 柜员口头告诉你「办好了」 |
| 事务完成阶段 | 复核员最后检查档案袋：**封面有作废贴纸 → 整袋材料作废** |
| 真实 ROLLBACK | 复核员把整袋材料销毁/回滚 |
| `UnexpectedRollbackException` | 复核员追出来告诉你：「刚才那单其实作废了」（你听到的"办好了"被撤销） |
| 日志 C | "你以为成功了"的记录 —— 永远不会写 |
| 日志 D | "实际失败了"的记录 —— 一定会写 |

一句话：**柜员（业务方法）可以先说"办好了"，但复核员（事务代理）有最终否决权，而且否决发生在你听到答复之后。**

`REQUIRES_NEW` 的类比则是：内层柜员**自己另开一个档案袋**办审计，办错了把自己的袋子作废，
主档案袋上**不会**被贴"作废"贴纸，所以主柜员最后照样能正常归档（外层提交）。

---

## 七、三种场景对照（全部真实跑过）

| 观测点 | 核心场景（内层 REQUIRED + 异常外抛） | Test04（内层自己 catch） | Test05（内层 REQUIRES_NEW + 异常外抛） |
|---|---|---|---|
| 内层 `newTx` | `false`（加入外层） | `false`（加入外层） | `true`（独立物理事务，第二条连接） |
| 内层异常是否到达内层代理 | 是 | **否**（方法内部吃掉） | 是 |
| 内层代理是否标记 rollback-only | **是** | 否 | 否（只回滚自己那条连接） |
| 外层 catch 时 `rollbackOnly` | `true` | `false` | `false` |
| 框架日志关键行 | `marking existing transaction as rollback-only` | 无此日志 | `Suspending current transaction, creating new transaction` / `Resuming suspended transaction` |
| 外层事务完成 | `ROLLED_BACK(1)` | `COMMITTED(0)` | `COMMITTED(0)` |
| 订单 status 最终值 | `PENDING`（回滚） | `PAID`（提交） | `PAID`（提交） |
| audit_log 最终值 | 0 行（随外层回滚） | 1 行（随外层提交） | 0 行（内层自己的事务回滚） |
| Controller 收到 | `UnexpectedRollbackException`（HTTP 500） | `SUCCESS`（HTTP 200） | `SUCCESS`（HTTP 200） |
| 日志 A / C / D | A 有 · C 无 · D 有 | A 有 · C 有 · D 无 | A 有 · C 有 · D 无 |

Test05 的连接证据（`docs/logs/test05-console.log`）：

```text
Creating new transaction ... [payWithRequiresNewAudit]        ← 外层拿 conn0
Suspending current transaction, creating new transaction with name [RequiresNewAuditService.saveAudit]
Acquired Connection [... wrapping conn1 ...] for JDBC transaction   ← 内层拿到第二条连接 conn1
Initiating transaction rollback
Rolling back JDBC transaction on Connection [... conn1 ...]         ← 只回滚 conn1
Resuming suspended transaction after completion of inner transaction
Initiating transaction commit
Committing JDBC transaction on Connection [... conn0 ...]           ← 外层 conn0 正常提交
```

---

## 八、必须纠正的四个误解

### 误解 1：「内层 REQUIRED 抛异常 → 立刻回滚整个物理事务」

错。内层 `REQUIRED` 没有自己的物理事务，它能做的只有 `doSetRollbackOnly`（贴"作废"贴纸）。
`Connection.rollback()` 由**外层事务拥有者**在完成阶段执行。
证据：`[013]` 时 `rollbackOnly=true`，但 `[016]` 才看到 `ROLLED_BACK` 与 `PENDING` 回读结果。

### 误解 2：「外层 catch 了异常就没事了」

错。catch 只影响 `rollbackOn(ex)` 的判断（异常不再穿过外层代理，所以外层不会因"异常"回滚），
但**改变不了已经被打上的 rollback-only**。`commit()` 分支不认 ticket，只认贴纸。

### 误解 3：「日志 A 打印了并且 return SUCCESS，所以业务成功了」

错。日志 A 只能证明"业务方法体执行完"，不能证明"事务提交"。事务结果是完成阶段才产生的。

### 误解 4：「UnexpectedRollbackException 是内层抛出来的」

错。内层抛出的是你的 `RuntimeException`，而且已经被外层 catch 吞掉了。
`UnexpectedRollbackException` 是**外层事务管理器**在 `processRollback(status, true)` 末尾新建的，
消息是 `Transaction rolled back because it has been marked as rollback-only`。

---

## 九、一次性看懂"时间轴"

```text
t1  代理: 开事务（conn0, autocommit=false）
t2  业务: UPDATE orders → PAID                （未提交）
t3  代理(内层): 加入已有事务                    （newTx=false）
t4  业务: INSERT audit_log                    （未提交）
t5  业务: throw RuntimeException
t6  代理(内层): 捕获异常 → 标记 conn0 rollback-only → 异常继续外抛
t7  业务(外层): catch 吞掉异常                 （rollbackOnly 已是 true）
t8  业务(外层): log 日志A → return "SUCCESS"   ★ 返回值产生，但没人收到
t9  代理(外层): 拿到返回值 → 调 commit()
t10 事务管理器: 发现 global rollback-only → 改走 processRollback(status, unexpected=true)
t11 事务管理器: beforeCompletion 回调 → conn0.rollback()   ← 真实回滚
t12 事务管理器: afterCompletion(STATUS_ROLLED_BACK=1)
t13 事务管理器: throw UnexpectedRollbackException          ★ 返回值被丢弃
t14 Controller: catch → 日志D；日志C 永不执行
```

★ 两个 ★ 之间，就是**「事务代理返回边界」**：业务已经返回，但结果还没定。
