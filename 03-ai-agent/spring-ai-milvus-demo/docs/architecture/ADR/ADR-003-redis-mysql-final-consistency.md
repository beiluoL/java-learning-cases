# ADR-003：Redis 与 MySQL 的最终一致策略（拒绝声称强一致）

> 状态：已接受（治理平台 V1.0 基线约束）
> 记录日期：2026-11-11
>
> 对应代码路径：
> - `src/main/java/com/example/ai/token/budget/TokenBudgetService.java`（`reserve` / `settle` / `release` / `cleanupExpired`）
> - `src/main/java/com/example/ai/token/budget/BudgetReconciler.java`（过期清理 60s、对账 300s）
> - `src/main/resources/lua/budget_{reserve,settle,release,cleanup}.lua`
> - `src/main/java/com/example/ai/token/cost/CostLedgerService.java`（追加式账务）
> - `src/main/java/com/example/ai/token/budget/BudgetSettledEvent.java`（结算后事件）

## 背景

预算治理有一个绕不开的矛盾：

- **并发正确性**要求「校验 + 扣减」是一个原子动作，否则 100 个并发请求会同时看到「还剩 1 块钱」然后一起通过；
- **审计完整性**要求每次变动都有可查询、可对账、可回溯的持久记录。

单靠 MySQL 做并发校验（`SELECT ... FOR UPDATE` + 条件 UPDATE）能保证正确性，但把行锁压在热点行上，吞吐会崩；单靠 Redis 做持久化，则丢失审计能力且不满足「真实数据库持久化」的要求。

因此必须双写。而**双写必然不同步**——这是事实，不是可以靠代码规避的缺陷。

## 决策

明确采用「**Redis 是并发控制真源，MySQL 是审计与账单持久层，二者不使用同一事务，追求最终一致**」，并把这一点写进代码注释、文档与验收口径，禁止任何地方声称强一致。

具体顺序与保障：

### 1. 预扣（reserve）

```
Lua（单脚本内）：
  校验 requestId 幂等
  逐个作用域比较 used + reserved + amount > limit
  全部通过 → 一次性 INCRBY 所有 used/reserved
  任一不通过 → 不扣任何作用域（杜绝部分成功）
→ 之后才写 MySQL：ai_budget_reservation（幂等）+ ai_budget_transaction（每个作用域一条）
```

- **Redis 先行**：一旦 Lua 返回成功，额度就已经真实占用。MySQL 写入失败只会丢审计，不会导致超支。
- **MySQL 后写**：`recordTx()` 用 `txNo = TYPE-requestId-budgetType-targetId` 保证幂等，`DuplicateKeyException` 直接吞掉。
- 写 MySQL 失败时**不阻断主流程**，但必须写一条 `RECONCILE_DIFF` 告警留痕——「静默丢审计」是不可接受的。

### 2. 结算（settle）

用 `SETTLE` 或 `UNKNOWN` 状态，按**实际成本**调整 used：

- 幂等：Lua 内检查 reservation 状态，已结算直接返回 `changed=false`；
- `actual > reserved` 时额外写 `SETTLE_OVERRUN` 告警（说明预估模型低估了）；
- 结算成功后发布 `BudgetSettledEvent`，由旁路监听器触发阈值预警——用**事件**而不是直接调用，是为了避免 `TokenBudgetService` 与 `BudgetThresholdAlertService` 之间形成构造期循环依赖（后者要读 `TokenBudgetService.status()` 才能算使用率）。

### 3. 释放 / 过期清理

- 调用方失败（且明确未计费）→ `release()` 归还全部预扣；
- 调用方超时/读失败（可能已计费）→ 不释放，按 `UNKNOWN` 保守结算；
- 进程崩溃留下的孤儿预扣 → `cleanupExpired()` 每 60s 扫描过期索引，Lua 原子扣回 `reserved` 并同步 MySQL 状态为 `EXPIRED`。

### 4. 对账

`BudgetReconciler.reconcile()` 每 5 分钟比对 Redis 当日 `used` 与 MySQL 当日账单合计：

- 差异 > 0.01 USD → 写 `RECONCILE_DIFF` 告警，附带两侧数值；
- **不自动修正**。理由是：自动修正会在「Redis 对但 MySQL 慢写了」的场景下把正确值改错。差异必须由人判断落库延迟还是真丢账。

### 5. 金额精度

- Redis 中一律存 **1e-8 USD 的整数**（`MoneyScale.toUnits / fromUnits`），避免 Lua 浮点误差；
- MySQL 中一律 `DECIMAL(20,8)`；
- 两层都不出现 `double`。

### 6. Cluster 同槽

所有 Lua 涉及的 key 都带 `{budget}` hashtag（`ai:{budget}:used:...`）。未来迁 Redis Cluster 时，同一脚本操作的 key 落在同一个 slot，不会触发 `CROSSSLOT`。

## 一致性等级声明（写进验收口径）

| 场景 | 一致性 |
|---|---|
| 预算是否超额 | **强一致**（Redis 单脚本原子） |
| 账单金额 | **强一致**（MySQL 单行幂等写入，幂等键 requestId） |
| Redis used 与 MySQL 账单合计 | **最终一致**，窗口 ≤ 对账周期（5 分钟） |
| 告警是否即时 | **最终一致**，结算事件触发 ≈ 实时；定时兜底 ≤ 2 分钟 |
| 日汇总表 | **最终一致**，每小时重建 |

## 备选方案与放弃原因

| 方案 | 放弃原因 |
|---|---|
| 分布式事务（Seata / XA） | 单机 Demo 引入 XA 收益极低，且 Redis 不参与 XA；热点行锁吞吐代价大于一致性收益 |
| 只写 MySQL，用 `FOR UPDATE` 做并发控制 | 预算热行串行化，QPS 上不去；且无法做到「一次校验 7 个作用域再一次性扣减」的原子性 |
| Redis + 事务性 outbox 表保证审计必达 | 更严谨，但需要额外的 outbox worker 与重试状态机；当前以「失败即告警 + 对账兜底」替代，已在文档中标注为可演进项 |
| 对账差异自动修正 | 见上，会把「落库延迟」误判成「丢账」并改错正确值 |

## 影响与已知风险

**接受的风险**：Redis 与 MySQL 之间存在最长 5 分钟的数据可见性差异；对账告警需要人工判断；MySQL 完全不可用时预算照常工作但审计会积压。

**已拒绝的伪解法**：不把 Redis 计数器定期「刷」回 MySQL 覆盖账单（会破坏幂等与真实账单）；不在结算里重试到成功为止（会阻塞请求线程）。
