# 面试训练（INTERVIEW_TRAINING）

> 本文件是**答案区**。建议先按 README「第一关」把 8 个问题写下来，跑完实验再回来对照。

---

## 一、30 秒版（结论 + 一句话机制）

> 因为 `@Transactional` 是环绕通知，业务方法 `return` 只是方法体结束，事务提交/回滚发生在**代理拿到返回值之后**。
> 内层 `REQUIRED` 方法抛异常时，内层事务代理会把**共享事务**标记为 `rollback-only`（只打标记，不回滚），
> 外层即使 catch 掉异常并 `return "SUCCESS"`，外层代理在提交前发现这个标记，就会执行真实 ROLLBACK，
> 并抛 `UnexpectedRollbackException` 取代返回值 —— 所以 Controller 收到的是异常，日志 C 不会打印。

---

## 二、90 秒版（加上时序与判定条件）

1. Controller 调的是**代理**，不是裸对象。代理先 `getTransaction`：拿连接、关自动提交、开事务。
2. 外层业务体改订单（未提交），再调 `AuditService`。`AuditService` 也是代理，`REQUIRED` 时走
   `Participating in existing transaction`：**没有第二次 BEGIN**，内外共用同一个 `DataSourceTransactionObject`
   和同一个 `ConnectionHolder`，所以内层 `isNewTransaction()=false`。
3. 内层写审计后抛 `RuntimeException`。异常先被**内层事务代理**捕获，判定 `rollbackOn(ex)=true`，
   于是调 `rollback()`；因为是"参与已有事务"，只执行 `doSetRollbackOnly`，把 rollback-only 打在 `ConnectionHolder` 上，
   然后异常继续外抛。**这一步没有 `Connection.rollback()`。**
4. 外层 catch 吞掉异常，此时共享事务 `rollbackOnly` 已经是 `true`。业务打印日志 A，`return "SUCCESS"`。
5. 返回值回到代理，代理调 `commit(status)`。`commit()` 先看 `isLocalRollbackOnly()`（false，外层没自己设过），
   再看 `!shouldCommitOnGlobalRollbackOnly() && isGlobalRollbackOnly()` —— 这里为 `true`，
   于是改调 `processRollback(status, true)`。
6. `processRollback` 里 `beforeCompletion` → `doRollback()`（真实 `Connection.rollback()`）→
   `afterCompletion(STATUS_ROLLED_BACK)` → 最后才 `throw new UnexpectedRollbackException(
   "Transaction rolled back because it has been marked as rollback-only")`。
7. 这个异常把返回值**替换掉**，Controller 进 catch 打日志 D，日志 C 永不执行。数据库里订单仍是 PENDING、审计表 0 行。

---

## 三、2–3 分钟版（源码 + 工程判断）

在前面 90 秒的基础上，补四层：

**① 判定点在哪一行代码**
`AbstractPlatformTransactionManager#commit`：
`if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) { processRollback(defStatus, true); return; }`。
`shouldCommitOnGlobalRollbackOnly()` 默认 `false`；`isGlobalRollbackOnly()` 在
`DefaultTransactionStatus` 里判断"底层事务对象是不是 `SmartTransactionObject` 且 `isRollbackOnly()`"，
对 JDBC 来说就是 `DataSourceTransactionObject` → `ConnectionHolder.isRollbackOnly()`。
所以内层贴的标记能被外层读到，靠的是**共享同一个 `ConnectionHolder`**。

**② 标记是"参与事务失败"时的默认行为**
`processRollback` 的参与分支条件 `status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()`，
后者默认 `true`（`setGlobalRollbackOnParticipationFailure(false)` 可关）。
**注意：关掉它只是"让发起者自己决定"，并不等于内层可以独立回滚。**

**③ 异常消息区分两阶段**
本场景是 `commit()` 阶段就发现标记 → 走 `processRollback(..., true)` → 消息是
`Transaction rolled back because it has been marked as rollback-only`。
如果已经进入 `processCommit`、在 `doCommit` 前后才发现标记，消息是
`Transaction silently rolled back because it has been marked as rollback-only`。

**④ 工程上怎么避免**
不是"catch 住就行"，要按语义选：
- 内层失败必须独立 → `REQUIRES_NEW`（独立物理事务，需 ≥2 条连接，注意池大小、注意它会让内层先提交）；
- 内层失败可忽略且不能污染外层 → 把异常在**方法内部**处理掉（异常不能穿过它的事务代理）；
- 业务上确实要"要么全成功" → 不要在外层吞异常，让它自然抛，由上层做补偿；
- 也可以显式 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` + 显式返回错误码，
  但那是"主动声明失败"，跟本场景"误以为成功"是两件事。

**⑤ 顺带说清两个易错点**
- 事务完成阶段 `TransactionAspectSupport.currentTransactionStatus()` 取不到（拦截器先 `cleanupTransactionInfo`
  再 commit），别把"读不到"当成"标记被清空"；
- 参与事务的内层不会重复触发 `beforeCompletion/afterCompletion`，因为
  `newTransactionStatus` 里 `actualNewSynchronization = newSynchronization && !isSynchronizationActive()`。

---

## 四、3 道变式题（改变捕获位置 / 传播行为）

### 变式 1：把 `try/catch` 从外层移到内层方法内部

```java
@Transactional(propagation = Propagation.REQUIRED)
public void saveAudit(Long orderId) {
    auditRepository.insert(orderId, "PAY_AUDIT");
    try {
        throw new RuntimeException("模拟审计业务异常");
    } catch (RuntimeException e) {
        log.warn("内层自己处理掉", e);
    }
    // 正常返回
}
```

**问**：外层还会回滚吗？订单和审计最终是什么状态？

**答**：不会回滚。异常从未穿过 `saveAudit` 的事务代理，内层代理看到的是"方法正常返回"，
不做 rollback-only 标记；外层 `commit()` 的 global 判定为 false → `processCommit` → 真实 COMMIT。
最终 `orders=PAID`、`audit_log` 有 1 行（审计 INSERT 随外层一起提交）。
**本工程 Test04 就是这道题的可运行版本，实测 HTTP 200 + 订单 PAID + 审计 1 行。**

---

### 变式 2：内层改 `REQUIRES_NEW`，异常仍然外抛并被外层 catch

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAudit(Long orderId) { ...; throw new RuntimeException("模拟审计业务异常"); }
```

**问**：谁回滚？外层能提交吗？审计记录还在吗？连接会发生什么？

**答**：只有内层那条独立物理事务回滚，外层不受影响。
`handleExistingTransaction` 先 `suspend()` 外层（连接解绑、暂存），再 `startTransaction()` 借**第二条连接**；
内层异常被内层代理 `rollback()`，回滚的是第二条连接；随后 `Resuming suspended transaction` 恢复外层，
外层 `commit()` 时 global 标记为 false，正常 COMMIT。
结果：`orders=PAID`、`audit_log` **0 行**。工程前提是连接池 ≥ 2 条连接，否则表现为"等连接超时"。
**本工程 Test05 实测：HTTP 200，conn1 回滚、conn0 提交，审计 0 行。**

---

### 变式 3：外层不 catch，让内层异常自然抛出（那为什么就不是 `UnexpectedRollbackException`？）

```java
@Transactional
public String pay(Long orderId) {
    orderRepository.updateStatus(orderId, "PAID");
    auditService.saveAudit(orderId);   // 不 try/catch
    return "SUCCESS";
}
```

**问**：异常类型是什么？谁回滚？Controller 收到什么？

**答**：Controller 收到的是**原始 `RuntimeException`**，不是 `UnexpectedRollbackException`。
链路：异常穿出内层代理（内层打 rollback-only）→ 继续穿出外层业务体 → 外层代理的
`completeTransactionAfterThrowing` 判定 `rollbackOn(ex)=true` → 直接 `rollback()` → 外层走
`processRollback(status, false)` 的 `isNewTransaction()` 分支，执行真实 `doRollback()`，
因为 `unexpectedRollback=false`，**不抛 `UnexpectedRollbackException`**，原异常继续外抛。
结果同样是全部回滚，但"谁触发回滚"和"最终异常类型"都变了：这是"如实失败"而不是"静默失败"。

> 一句话记住三者差别：
> - 异常穿出外层代理 → 原始异常（正常回滚）
> - 异常被外层吞掉但内层已标记 → `UnexpectedRollbackException`
> - 异常在内层就地消化 → 谁都不回滚，正常提交

---

## 五、可能的追问与红线

| 追问 | 回答要点 |
|---|---|
| `rollback-only` 到底存在哪？ | JDBC 场景在 `ConnectionHolder` 上（`DataSourceTransactionObject.setRollbackOnly()` 委托过去），内外层共享同一个 holder，所以标记"传染"。 |
| 为什么不是"内层立即回滚整个物理事务"？ | `REQUIRED` 参与事务时 `isNewTransaction()=false`，`processRollback` 走的是"参与分支"，只 `doSetRollbackOnly`；`Connection.rollback()` 由事务拥有者（外层）执行。 |
| 事务拥有者是谁？ | 第一个真正 `doBegin` 的那一层（本实验是 `PaymentService.pay`），它负责 commit/rollback 与连接释放。 |
| 只有 `RuntimeException` 才标记吗？ | 默认规则 `rollbackOn` 对 `RuntimeException`/`Error` 为 true；受检异常默认不回滚（也不标记），可用 `@Transactional(rollbackFor=...)` 改。 |
| 外层加了 `@Transactional(readOnly=true)` 会怎样？ | 会有 `IllegalTransactionStateException`（`isValidateExistingTransaction` 时才校验）；默认不校验，但语义上仍不安全 —— 不要用它绕问题。 |
| 用 `TransactionSynchronization.afterCompletion` 能看到什么？ | 能看到 `status=1(ROLLED_BACK)`，且此时物理回滚**已执行完毕**；本工程在回调里回读数据库得到 `PENDING` 作为证据。 |
| 面试官问"你怎么验证的"？ | 说明打开的是 `org.springframework.jdbc.support.JdbcTransactionManager` 的 DEBUG（logger 取的是实现类）与 `org.springframework.transaction.interceptor` 的 TRACE，并用 `afterCompletion` 时间点对齐数据库回读结果。 |

**红线（不要这么说）**

- ❌「`UnexpectedRollbackException` 是内层抛的」——内层抛的是你的业务异常，且已被吞掉。
- ❌「`return` 之前事务已经提交了」——恰恰相反，提交/回滚在 `return` 之后由代理决定。
- ❌「catch 住内层异常就不会回滚」——要看异常有没有穿过内层的事务代理。
- ❌「`REQUIRES_NEW` 就是给内层加个 savepoint」——不是，它是挂起外层 + 新物理事务；savepoint 对应 `NESTED`。
- ❌ 编造行号或"我记得是第 900 多行"——换版本就变，说方法名 + 关键语句最稳。

---

## 附录 A：第一关 8 问答案（核心场景 `POST /api/lab/pay?orderId=1`）

| # | 问题 | 答案 | 依据 |
|---|---|---|---|
| 1 | 日志 A 是否打印？ | **打印** | 业务方法体确实执行到 `log.info("日志A...")` 才 return；`core-console.log` 有该行 |
| 2 | 日志 C 是否打印？ | **不打印** | 代理没把返回值交回，Controller 进 catch；Test02 断言 trace 中无「日志C」 |
| 3 | 日志 D 是否打印？ | **打印** | catch 分支记录 `e.getClass().getSimpleName()` = `UnexpectedRollbackException` |
| 4 | Controller 是否收到 SUCCESS？ | **没有** | HTTP 500，body = `UnexpectedRollbackException` |
| 5 | 订单 PAID 是否提交？ | **没有，回滚** | 请求后 `orders.id=1` 仍 `PENDING`（`core-db.json`） |
| 6 | 审计日志是否提交？ | **没有** | `auditLogCount=0` |
| 7 | rollback-only 在哪个阶段产生？ | **内层异常穿出 `AuditService` 事务代理时**（外层 catch 之前） | 日志 `marking existing transaction as rollback-only` 出现在 `日志B` 之前；trace `[012] rollbackOnly=true` |
| 8 | 真正回滚在什么阶段发生？ | **外层事务完成阶段**：`commit()` 发现 global rollback-only → `processRollback(..., true)` → `doRollback()` | 日志 `Initiating transaction rollback` 出现在 `日志A` 之后；trace `[016] ROLLED_BACK` |

---

## 附录 B：把答案压成一张口述卡

```text
代理开事务 → 业务改数据（未提交）→ 内层 REQUIRED 加入同一事务（newTx=false）
→ 内层抛异常 → 内层代理只贴 rollback-only（不回滚）→ 异常外抛 → 外层 catch 吞掉
→ 日志A + return SUCCESS → 代理拿到返回值 → commit() 发现 global rollback-only
→ 改走 processRollback(unexpected=true) → 真实 ROLLBACK → afterCompletion(ROLLED_BACK)
→ 抛 UnexpectedRollbackException（返回值被丢弃）→ Controller 日志D，日志C 永不执行
数据库结论：订单 PENDING、审计 0 行。
```
