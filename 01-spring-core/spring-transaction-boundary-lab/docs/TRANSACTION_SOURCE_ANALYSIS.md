# 源码级分析：`commit()` 为什么把返回值换成 `UnexpectedRollbackException`

## 0. 这份文档的证据从哪来

- 运行版本：**Spring Boot 3.5.16 → Spring Framework 6.2.19**（由 `spring-boot-dependencies:3.5.16` 固定），
  `spring-tx-6.2.19.jar` 与实际运行时的 `BOOT-INF/lib/` 一致（可自行 `unzip -l` 核对）。
- 源码获取（本文所有引用均来自这份真实源码，不是凭记忆）：

```bash
curl -O https://repo.maven.apache.org/maven2/org/springframework/spring-tx/6.2.19/spring-tx-6.2.19-sources.jar
curl -O https://repo.maven.apache.org/maven2/org/springframework/spring-jdbc/6.2.19/spring-jdbc-6.2.19-sources.jar
unzip -q spring-tx-6.2.19-sources.jar -d tx && unzip -q spring-jdbc-6.2.19-sources.jar -d jdbc
```

- **行号只对本版本有效**。Spring 的小版本迭代会移动行号，因此下文同时给出
  「类名 + 方法名 + 关键语句」，即使换版本也能按方法名定位；行号仅作为本版本的定位辅助。
- 文末「怎么自己验证」给出可复现的 grep 命令。

---

## 1. 调用链总览

```text
OrderController#pay
└─ paymentService.pay(orderId)                       ← 注入的是代理
   └─ CGLIB 代理 → TransactionInterceptor#invoke(112)
      └─ TransactionAspectSupport#invokeWithinTransaction(342)
         ├─ createTransactionIfNecessary(623)  ─────────────┐  ① 开启/加入事务
         │  ├─ transactionManager.getTransaction(txAttr)     │
         │  └─ prepareTransactionInfo(...)                   │
         ├─ invocation.proceedWithInvocation() ─────────────┤  ② 执行业务方法体
         │     └─ PaymentService#pay → AuditService 代理     │     （可以 return / catch）
         │        └─ TransactionInterceptor#invoke           │
         │           └─ invokeWithinTransaction              │
         │              ├─ createTransactionIfNecessary ─────┤     内层加入现有事务
         │              ├─ proceedWithInvocation()           │     内层业务体抛异常
         │              ├─ completeTransactionAfterThrowing(708)  ③ 内层：标记 rollback-only
         │              └─ throw ex ─────────────────────────┘
         ├─ finally { cleanupTransactionInfo(txInfo) }(388)  ④ 先把线程上的 TransactionInfo 摘掉
         ├─ commitTransactionAfterReturning(693) ────────────⑤ 再决定提交/回滚
         │  └─ transactionManager.commit(status)
         │     └─ AbstractPlatformTransactionManager#commit(735)
         │        └─ processRollback(status, true)(875) ─────⑥ 真实 ROLLBACK
         │           ├─ triggerBeforeCompletion
         │           ├─ doRollback(status)                    Connection.rollback()
         │           ├─ triggerAfterCompletion(STATUS_ROLLED_BACK)
         │           └─ throw new UnexpectedRollbackException ─⑦ 返回值被丢弃
         └─ return retVal  ← 只有没抛异常才走到这里
```

本实验的真实日志与这条链**逐行对得上**（见 `docs/logs/core-console.log`）：

```text
Getting transaction for [PaymentService.pay]
Creating new transaction with name [PaymentService.pay]
...
Participating in existing transaction
Getting transaction for [AuditService.saveAudit]
Completing transaction for [AuditService.saveAudit] after exception: java.lang.RuntimeException
Participating transaction failed - marking existing transaction as rollback-only
Setting JDBC transaction [...] rollback-only
...
Completing transaction for [PaymentService.pay]
Global transaction is marked as rollback-only but transactional code requested commit
Initiating transaction rollback
Rolling back JDBC transaction on Connection [...]
```

---

## 2. 内层：异常如何变成 rollback-only（不产生任何物理回滚）

`TransactionInterceptor` 捕获到业务异常后，交给父类：

```java
// TransactionAspectSupport#completeTransactionAfterThrowing（第 708 行，节选）
if (txInfo.transactionAttribute != null && txInfo.transactionAttribute.rollbackOn(ex)) {
    ...
    txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
```

`rollback()` → `processRollback(status, false)`。由于内层是 `REQUIRED` 且外层已有事务，
它走的是 **「Participating in larger transaction」** 分支：

```java
// AbstractPlatformTransactionManager#processRollback（第 899-913 行）
else {
    // Participating in larger transaction
    if (status.hasTransaction()) {
        if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
            if (status.isDebug()) {
                logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
            }
            doSetRollbackOnly(status);
        }
        ...
```

两个关键事实：

1. **`isNewTransaction()` 为 false → 这里没有 `doRollback(status)`，也就是没有 `Connection.rollback()`。**
2. `isGlobalRollbackOnParticipationFailure()` 默认 `true`（`AbstractPlatformTransactionManager` 的字段
   `globalRollbackOnParticipationFailure`，可用 `setGlobalRollbackOnParticipationFailure(false)` 改掉），
   所以"参与事务失败"默认会去污染共享事务。

`doSetRollbackOnly` 落到 JDBC 实现：

```java
// DataSourceTransactionManager#doSetRollbackOnly（第 367-373 行）
protected void doSetRollbackOnly(DefaultTransactionStatus status) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
    if (status.isDebug()) {
        logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() + "] rollback-only");
    }
    txObject.setRollbackOnly();
}

// DataSourceTransactionManager.DataSourceTransactionObject（第 509-516 行）
@Override
public void setRollbackOnly() {
    getConnectionHolder().setRollbackOnly();
}

@Override
public boolean isRollbackOnly() {
    return getConnectionHolder().isRollbackOnly();
}
```

**为什么一个标记能影响外层？** 因为内层 `REQUIRED` 拿到的 `DataSourceTransactionObject`
与外层是**同一个对象**（外层开启时已绑定到线程，内层 `doGetTransaction` 直接复用它），
而这个对象的 `rollbackOnly` 实际落在 `ConnectionHolder` 上。外层、内层共享同一个 `ConnectionHolder`，
所以内层贴的"作废"贴纸，外层一定看得见。

这也解释了我们轨迹里 `[008] resources=1`（连接数没变）和 `[013] rollbackOnly=true`（同一对象被标记）。

---

## 3. 外层：`commit()` 的五步判定（决定生死的那个方法）

业务方法体 return 之后，拦截器调用 `commitTransactionAfterReturning` → `commit(status)`：

```java
// AbstractPlatformTransactionManager#commit（第 735-761 行，完整判定逻辑）
public final void commit(TransactionStatus status) throws TransactionException {
    if (status.isCompleted()) {
        throw new IllegalTransactionStateException(
                "Transaction is already completed - do not call commit or rollback more than once per transaction");
    }

    DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
    if (defStatus.isLocalRollbackOnly()) {                       // ① 业务自己 setRollbackOnly()
        if (defStatus.isDebug()) {
            logger.debug("Transactional code has requested rollback");
        }
        processRollback(defStatus, false);
        return;
    }

    if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {   // ② 全局标记
        if (defStatus.isDebug()) {
            logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
        }
        processRollback(defStatus, true);                        // ← 本实验走这里
        return;
    }

    processCommit(defStatus);                                    // ③ 正常提交
}
```

三处必须理解准确：

- **`isLocalRollbackOnly()`**：只反映"当前这个 status 自己有没有被 `setRollbackOnly()`"（`AbstractTransactionStatus.rollbackOnly` 字段）。
  外层从没调用过它，所以这里恒 `false`。
- **`isGlobalRollbackOnly()`**：`DefaultTransactionStatus#isGlobalRollbackOnly`（第 185 行）

  ```java
  public boolean isGlobalRollbackOnly() {
      return (this.transaction instanceof SmartTransactionObject smartTransactionObject &&
              smartTransactionObject.isRollbackOnly());
  }
  ```

  它问的是**底层事务对象**（`DataSourceTransactionObject` → `ConnectionHolder`）有没有被标 rollback-only。
  内层贴的贴纸就落在这里 → **`true`**。
- **`shouldCommitOnGlobalRollbackOnly()`** 默认返回 `false`（第 1228 行），
  所以第一个条件 `!shouldCommitOnGlobalRollbackOnly()` 成立 → 进 ②。
  **这就是"业务已经 return 却提交不了"的判定点。**

> 顺带解答常见疑问：`isRollbackOnly()`（`AbstractTransactionStatus`，第 77 行）
> = `isLocalRollbackOnly() || isGlobalRollbackOnly()`，因此外层 catch 里读到的 `true` 来自"全局"这一半，
> 与 `Spring 探针` 里的 `rollbackOnly=true` 完全一致，不是伪造。

---

## 4. 真实回滚与异常抛出的严格顺序

`processRollback(defStatus, true)`（第 875 行起）：

```java
private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
    try {
        boolean unexpectedRollback = unexpected;
        try {
            triggerBeforeCompletion(status);                     // ← 同步回调 beforeCompletion
            ...
            else if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.debug("Initiating transaction rollback");
                }
                this.transactionExecutionListeners.forEach(listener -> listener.beforeRollback(status));
                rollbackListenerInvoked = true;
                doRollback(status);                              // ← 真实 Connection.rollback()
            }
            ...
        }
        ...
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);   // ← 回滚之后再回调
        ...
        // Raise UnexpectedRollbackException if we had a global rollback-only marker
        if (unexpectedRollback) {
            throw new UnexpectedRollbackException(
                    "Transaction rolled back because it has been marked as rollback-only");  // ← 最后才抛
        }
    }
    finally {
        cleanupAfterCompletion(status);
    }
}
```

真实顺序（与轨迹一一对应）：

```text
beforeCompletion                          → trace [015]
Initiating transaction rollback
Rolling back JDBC transaction on Connection [...]   ← 物理回滚
afterCompletion(STATUS_ROLLED_BACK=1)     → trace [016]（同连接回读已是 PENDING）
throw UnexpectedRollbackException         → trace [017]（Controller 日志D）
```

**也就是说：数据库先回滚，异常后抛出。** 所以"异常到达 Controller"这件事，
在时间上**晚于**"数据已经回滚"。这是本实验要求验证的第二个关键顺序。

## 5. 两条 `UnexpectedRollbackException` 消息，别混

| 消息 | 产生位置 | 触发条件 |
|---|---|---|
| `Transaction rolled back because it has been marked as rollback-only` | `processRollback`（第 938-939 行） | `commit()` 发现 global rollback-only，改用 `processRollback(status, true)`。**本实验观察到的就是这条** |
| `Transaction silently rolled back because it has been marked as rollback-only` | `processCommit`（第 767 行起的方法内） | 已经进了 `processCommit`，在 `doCommit` 之后才发现 global rollback-only（例如 `hasSavepoint()` 分支、或 `isFailEarlyOnGlobalRollbackOnly()` 场景） |

两条消息的差别提示的是"在哪个阶段发现"的差别，不是"严重程度"的差别。
面试里能说清这个区分，会明显区别于只背过结论的人。

---

## 6. 三个容易解释错的细节（都有实测支撑）

### 6.1 为什么内层参与事务时 `beforeCompletion` 不会重复触发？

```java
// AbstractPlatformTransactionManager#newTransactionStatus（第 561-570 行）
boolean actualNewSynchronization = newSynchronization &&
        !TransactionSynchronizationManager.isSynchronizationActive();
```

内层 `REQUIRED` 时线程上**已经**有激活的同步集合，所以 `actualNewSynchronization = false`。
而 `triggerBeforeCompletion/triggerAfterCompletion` 都以 `status.isNewSynchronization()` 为守卫，
因此这些回调**只由外层触发一次**。我们轨迹里 `[015]/[016]` 各出现一次，就是这条规则的实证。

### 6.2 为什么事务完成阶段 `newTx` / `rollbackOnly` 变成 `null`？

```java
// TransactionAspectSupport#invokeWithinTransaction（第 374-416 行，节选）
TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);
Object retVal;
try {
    retVal = invocation.proceedWithInvocation();
}
catch (Throwable ex) {
    completeTransactionAfterThrowing(txInfo, ex);
    throw ex;
}
finally {
    cleanupTransactionInfo(txInfo);          // ← 先摘掉线程上的 TransactionInfo
}
...
commitTransactionAfterReturning(txInfo);     // ← 之后才 commit
```

`cleanupTransactionInfo` 之后，`TransactionAspectSupport.currentTransactionStatus()` 会抛
`NoTransactionException`，探针只能填 `null`。**这不是"标记被清空"，而是"这个 API 此时读不到了"**；
此时 `TransactionSynchronizationManager.isActualTransactionActive()` 仍为 `true`，连接仍绑定在线程上。

### 6.3 `REQUIRES_NEW` 为什么必须两条连接？

`handleExistingTransaction`（第 445 行起）里：

```java
if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
    if (debugEnabled) {
        logger.debug("Suspending current transaction, creating new transaction with name [" + ...);
    }
    SuspendedResourcesHolder suspendedResources = suspend(transaction);
    try {
        return startTransaction(definition, transaction, false, debugEnabled, suspendedResources);
    }
    ...
```

`suspend()` 把外层连接从线程解绑并暂存，`startTransaction()` 向池子**再借一条连接**。
所以 `spring.datasource.hikari.maximum-pool-size` 至少 2（本工程给 5），
否则现象是"等连接超时"，很容易被误判成事务问题。

---

## 7. 这个设计为什么是必要的

`rollback-only` 是**"不能靠返回值表达"的信息**。考虑本实验：业务方法 catch 了内层异常并
`return "SUCCESS"`，这个返回值**无法**表达"共享事务已被判死刑"。

如果事务管理器在这种情况下仍然 commit：

- 内层已经声明过"我要回滚"，结果它的数据（审计 INSERT）被提交 → **破坏事务语义**；
- 或者为了惩罚业务而静默失败，调用方拿到"SUCCESS"却什么都没写 → **更危险**。

所以 Spring 选择：**回滚 + 抛 `UnexpectedRollbackException`**，宁可让调用方炸掉，也不给它一个假的成功。

反过来，如果业务确实希望"内层失败不影响外层"，正确做法是两选一：

1. 内层改 `REQUIRES_NEW`（独立事务，脏数据只留在内层）——见 Test05；
2. 内层自己处理掉异常，不让它穿过事务代理——见 Test04。

**注意区分**：Test04 里异常是"在方法内部被吃掉"；如果异常已经穿出内层代理再被外层 catch，
就已经晚了（rollback-only 已经贴上）。这正是本实验最容易被忽略的一步。

---

## 8. 怎么自己验证（可复制）

```bash
# 1) 确认实际使用的版本
unzip -l target/spring-transaction-boundary-lab.jar | grep spring-tx

# 2) 下载源码并定位关键方法
curl -sO https://repo.maven.apache.org/maven2/org/springframework/spring-tx/6.2.19/spring-tx-6.2.19-sources.jar
unzip -oq spring-tx-6.2.19-sources.jar -d /tmp/tx-src
grep -n "public final void commit(TransactionStatus"            /tmp/tx-src/org/springframework/transaction/support/AbstractPlatformTransactionManager.java
grep -n "private void processRollback("                          /tmp/tx-src/org/springframework/transaction/support/AbstractPlatformTransactionManager.java
grep -n "Transaction rolled back because it has been marked"    /tmp/tx-src/org/springframework/transaction/support/AbstractPlatformTransactionManager.java
grep -n "actualNewSynchronization"                               /tmp/tx-src/org/springframework/transaction/support/AbstractPlatformTransactionManager.java

# 3) 用运行日志反查：开启事务相关 DEBUG/TRACE 后重跑一次接口
curl -X POST 'http://localhost:8095/api/lab/pay?orderId=1'
grep -E "JdbcTransactionManager|TransactionInterceptor" backend.log
```

日志级别说明（很多教程写错）：`AbstractPlatformTransactionManager` 用
`LogFactory.getLog(getClass())` 取 logger，实际实现类是 `JdbcTransactionManager`，
所以 **logger 名是 `org.springframework.jdbc.support.JdbcTransactionManager`**，
只开 `org.springframework.transaction` 是**看不到** `Creating new transaction` /
`marking existing transaction as rollback-only` 这些行的。
另外 `Getting/Completing transaction for [...]` 在 6.2.x 是 **TRACE** 级，不是 DEBUG 级。
本工程 `application.yml` 已按实测结论配好。
