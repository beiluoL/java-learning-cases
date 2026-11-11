# 预算控制设计（Redis Lua 原子预扣）

> 本文件对应代码路径：
> - 核心服务：`src/main/java/com/example/ai/token/budget/TokenBudgetService.java`
> - 定时兜底：`src/main/java/com/example/ai/token/budget/BudgetReconciler.java`
> - 脚本加载：`src/main/java/com/example/ai/config/RedisConfig.java`
> - 4 个 Lua 脚本：`src/main/resources/lua/budget_reserve.lua`、`budget_settle.lua`、`budget_release.lua`、`budget_cleanup.lua`
> - 金额刻度：`src/main/java/com/example/ai/common/MoneyScale.java`
> - 状态与告警枚举：`src/main/java/com/example/ai/common/AiEnums.java`
> - 失败编排：`src/main/java/com/example/ai/chat/service/AiChatService.java`（`isUncertainFailure()`）
> - 策略 / 流水表：`V1__init_ai_cost.sql` 的 `ai_budget_policy`、`ai_budget_reservation`；`src/main/resources/mapper/BudgetPolicyMapper.xml`、`BudgetReservationMapper.xml`
> - 集成测试：`src/test/java/com/example/ai/token/budget/RedisBudgetIT.java`

## 1. 一致性定位（先说清楚不是什么）

Redis 是**并发控制的真源**，MySQL 是**审计流水与账单的持久层**，二者**不是同一本地事务、不是强一致**。整体策略是最终一致：

```
Redis 原子预扣 → 调用模型 → Redis 幂等结算 → MySQL 幂等落库 → 定时对账补偿
```

任何文档或代码都不应声称「Redis 与 MySQL 在同一事务内强一致」。补偿机制是 `BudgetReconciler`（清理 + 对账）与告警 `RECONCILE_DIFF`。

## 2. 预算作用域与 key 设计

`AiEnums.BudgetType` 声明 **9 级作用域**，但它们的执行位置不同——**8 级进 Lua 预扣，第 9 级（`REQUEST`）在预估阶段单独拦截**：

| 作用域 | targetId | 额度来源（`limitOf`） | 周期分键 | 执行位置 |
|---|---|---|---|---|
| `DAILY` | userId | `daily_limit` | `LocalDate.now(UTC)` | Lua 预扣 |
| `SCENE` | scene（`CHAT`/`RAG`/`AGENT`…） | `daily_limit ?? monthly_limit` | `LocalDate.now(UTC)` | Lua 预扣 |
| `MONTHLY` | userId | `monthly_limit` | `YearMonth.now(UTC)` | Lua 预扣 |
| `USER` | userId | `monthly_limit ?? daily_limit` | `ALL` | Lua 预扣 |
| `MODEL` | modelName | `monthly_limit ?? daily_limit` | `ALL` | Lua 预扣 |
| `KNOWLEDGE_BASE` | knowledgeBaseId | `monthly_limit ?? daily_limit` | `ALL` | Lua 预扣 |
| `AGENT` | `__ALL__` | `monthly_limit ?? daily_limit` | `ALL` | Lua 预扣 |
| `GLOBAL` | `__ALL__` | `monthly_limit ?? daily_limit` | `ALL` | Lua 预扣 |
| `REQUEST` | — | `per_request_limit` | 无（单次） | **预估阶段拦截**，不进 Lua |

**为什么 `REQUEST` 不进 Lua**：它是「单次调用成本上限」，语义是「这一次最多能花多少」，用预扣-结算模型表达会很别扭（预扣额本身就等于上限，等于每次都要预扣满额）。放在调用前按 `TokenEstimator` 的预估成本直接比对更贴合语义，且省一次 Redis 往返。

- `applicableScopes(userId, modelName, scene, knowledgeBaseId)` 按需拼装：`SCENE` 仅当 `scene` 非空时加入，`KNOWLEDGE_BASE` 仅当有知识库 id 时加入，`AGENT` 仅当 `scene=AGENT` 时加入——**不相关的维度不会凭空产生一次 key 读写**。
- 全部作用域在**同一次 Lua 调用**里检查并占用，不存在「场景额度够但全局不够」却已占用场景额度的中间态（`RedisBudgetIT.sceneBudgetIsCheckedAtomicallyWithGlobal` 值守）。
- 策略来自 `ai_budget_policy`，`enabled != 1` 或额度 ≤ 0 的作用域跳过；全部为空时回落到配置的默认全局额度 `default-monthly-limit`。
- key 格式：`ai:{budget}:used:{type}:{target}:{period}` 与 `ai:{budget}:reserved:{type}:{target}:{period}`。
- **DAILY / SCENE / MONTHLY 按日期 / 月份分键，跨天、跨月新 key 自然从 0 开始，无需定时任务重置额度**；其它作用域周期为固定 `ALL`。
- 预扣明细为 Hash：`ai:{budget}:resv:{requestId}`；过期索引为 ZSET：`ai:{budget}:expire-index`。

### Cluster 同槽

所有 key 共用 `{budget}` hashtag（前缀 `ai:{budget}:`），因此一次 Lua 涉及的 `resvHash`、`expire-index`、各 `used` / `reserved` key 在 Redis Cluster 下落在同一槽，脚本可原子执行。`budget_cleanup.lua` 内部按 `'ai:{budget}:resv:' .. rid` 拼出的 key 同样带该 hashtag。

### 金额单位

`MoneyScale.toUnits()` 把 USD 金额按 `1e-8` 换算为整数（`DECIMAL(20,8)` 刻度一致），Lua 全程整数运算，规避浮点误差；`UNIT = 100_000_000`，`MAX_BUDGET = 10_000_000 USD` 保证整数小于 Lua number 的精确表示范围。`fromUnits()` 反向还原。

## 3. Lua 原子多作用域预扣

`budget_reserve.lua` 由 `TokenBudgetService.reserve()` 调用，`KEYS` 依次为 `resvHash`、`expire-index`、以及每个作用域的 `used_i` / `reserved_i`；`ARGV` 携带 `requestId`、`amount`、`nowSec`、`ttlSec`、`userId`、`scopeCount`，以及每个作用域的 `limit`、`budgetType`、`targetId`。

```
第一遍（只校验，不扣减）
  for 每个作用域 i:
      used_i = GET(usedKey_i) or 0
      held_i = GET(reservedKey_i) or 0
      if used_i + held_i + amount > limit_i:
          return {0, 'INSUFFICIENT', '作用域:对象', 剩余额度}   // 整体拒绝，零扣减
第二遍（全部通过后一次性扣减）
  for 每个作用域 i:
      INCRBY reservedKey_i, amount
  HSET resvHash 明细（含各作用域完整 used/reserved key，供结算与清理精确还原）
  ZADD expire-index, now+ttl, requestId
  return {1, 'RESERVED', scopeCount, amount}
```

关键点：**「先校验所有作用域、再一次性扣减」在同一个 Lua 内完成，Lua 执行期间不会被其它命令插入**，因此不存在「部分作用域扣成功、部分失败」的中间态，也不存在并发超额。对应 `RedisBudgetIT.hundredConcurrentRequestsNeverExceedBudget()` 的 100 并发验证。

## 4. 状态机（AiEnums.ReservationStatus）

| 状态 | 写入方 | 触发条件 |
|---|---|---|
| `RESERVED` | `budget_reserve.lua` | 预扣成功，同时写入 ZSET 过期索引 |
| `SETTLED` | `budget_settle.lua`（Java 传 `SETTLED`） | 正常结算，`unknown=false` |
| `UNKNOWN` | `budget_settle.lua`（Java 传 `UNKNOWN`） | 超时 / 连接中断，无法确认供应商是否已收费，按预扣金额保守计入 |
| `RELEASED` | `budget_release.lua` | 调用失败（非不确定失败）或请求被拒绝，归还全部预扣 |
| `EXPIRED` | `budget_cleanup.lua` | 预扣过期，由定时任务清理 |

```
                 ┌──────────────────────────────┐
                 │          RESERVED            │
                 └──────┬────┬────┬─────┬───────┘
        settle(ok) ─────┘    │    │     └─ cleanup(过期) ─→ EXPIRED
                             │    └─ settle(unknown) ─────→ UNKNOWN
                             └─ release() ───────────────→ RELEASED
                             │
                             └─ settle(ok) ──────────────→ SETTLED

   所有终态均不可逆：再次 settle / release 返回 ALREADY_<状态>，不重复扣减。
```

- `budget_settle.lua` 仅当 `status == 'RESERVED'` 才推进：`INCRBY reserved_i, -reserved`、`INCRBY used_i, actual`、`ZREM expire-index`。实际成本高于预扣时仍按实际计入，由 Java 侧写 `SETTLE_OVERRUN` 告警。
- `budget_release.lua` 仅当 `status == 'RESERVED'` 才归还：`INCRBY reserved_i, -reserved`，不增加 `used`，并 `ZREM expire-index`。
- `budget_cleanup.lua` 仅处理 `status == 'RESERVED'` 且过期的明细，逐个作用域扣回 `reserved` 总额，再置 `EXPIRED`、`ZREM expire-index`。

## 5. 幂等

| 层面 | 机制 | 代码位置 |
|---|---|---|
| 重复预扣 | `resvHash` 已存在 → 返回 `DUPLICATE`，零扣减 | `budget_reserve.lua` 第 6-8 行 |
| 重复结算 | `status != RESERVED` → 返回 `ALREADY_<st>`，`changed=false` | `budget_settle.lua` 第 8-11 行 |
| 重复释放 | 同上 | `budget_release.lua` 第 7-10 行 |
| 重复清理 | 仅处理 `RESERVED` 明细并 `ZREM` | `budget_cleanup.lua` |
| MySQL 流水 | `uk_reservation_request(request_id)` 唯一约束；`persistReservation()` 捕获 `DuplicateKeyException` 忽略 | `TokenBudgetService.persistReservation()` |
| 账单 | `ON DUPLICATE KEY UPDATE request_id = request_id` | `TokenUsageMapper.xml` |
| 策略变更后结算 | 结算作用域从 `resvHash` 明细还原完整 key（`scopesFromRedis()`），不重新按当前策略计算，避免错位 | `TokenBudgetService.scopesFromRedis()` |

`RedisBudgetIT.duplicateRequestIdAndDuplicateSettleAreIdempotent()` 验证：重复 `requestId` 不重复扣额度、重复结算不重复扣费。

## 6. 过期清理（不能只靠 TTL）

- 预扣 Hash 本身不靠 key TTL 兜底：如果只让 key 过期，`reserved` 计数不会同步扣回，额度会被永久占用。
- 因此预留时 `ZADD expire-index (now+ttl, requestId)`；`BudgetReconciler.cleanup()`（`initialDelay=30000, fixedDelay=60000`，即每 60s）调用 `budget_cleanup.lua`，按 `ZRANGEBYSCORE -inf now LIMIT 0 200` 取过期项，**在 Lua 内原子扣回各作用域 `reserved` 总额**、置 `EXPIRED`、`ZREM`。
- Java 侧随后用 `reservationMapper.findExpired(now, 200)` 把 MySQL 流水同步置 `EXPIRED`（Lua 返回值与 MySQL 状态分别处理，两者最终一致）。
- `budget_cleanup.lua` 的返回值 `{cleaned, expiredCount}` 供 `metrics` / 排查使用；释放失败时 `TokenBudgetService.release()` 也会写 `REDIS_FAILURE` 告警，由本任务兜底。

## 7. 最终一致与对账

`BudgetReconciler.reconcile()`（`initialDelay=60000, fixedDelay=300000`，即每 5min）：

1. 取当日 `ai:{budget}:used:DAILY:{userId}:{today}` 的 Redis 值，经 `MoneyScale.fromUnits()` 还原为 USD。
2. 取 MySQL `sumAllCost(userId, 当日 00:00 ~ 次日 00:00)`（`TokenUsageMapper.xml`：当日 `request_status='SUCCESS'` 的全部账单，含 MOCK，与 Redis 当日 used 同口径）。
3. 差额 `|Redis - MySQL| > 0.01 USD` 时写 `RECONCILE_DIFF`（ERROR 级）告警，附带两侧数值与差额。

如实边界：**当前对账只覆盖 `DAILY` 作用域与当前身份用户（Demo 固定 `demo-user`）的当日消耗**，不是所有作用域的全量对账；`TOLERANCE = 0.01` USD 是告警阈值，不是自动修正。差异依赖人工复核，不自动改写任一侧数据。

## 8. 异常与安全失败策略

| 场景 | 处理 | 代码位置 |
|---|---|---|
| 预扣阶段 Redis 异常（`DataAccessException`） | **安全失败：拒绝调用**。`metrics.redisFailure()` + `REDIS_FAILURE` 告警 + 抛 `50301 REDIS_UNAVAILABLE`「预算服务不可用，已安全拒绝本次调用」；付费模型未经预算检查不得放行 | `TokenBudgetService.reserve()` |
| 预算不足 | `Lua` 返回 `INSUFFICIENT` → 记 `BUDGET_REJECTED` 账单 + `BUDGET_EXCEEDED` 告警 + 抛 `40301`（被拒账单也落库） | `reserve()` + `AiChatService.record()` |
| 超时 / 连接中断（不确定失败） | `isUncertainFailure()` 沿 cause 链识别 `TimeoutException` / `SocketTimeoutException` / `ResourceAccessException` → `settle(预扣金额, unknown=true)` 按 `UNKNOWN` 保守计入，账单 `request_status=UNKNOWN`，等待人工对账；**不简单释放** | `AiChatService` |
| 其它调用失败 | `release()` 归还全部预扣，账单 `request_status=FAILED` | `AiChatService` |
| 实际成本 > 预扣 | 按实际计入 `used`，写 `SETTLE_OVERRUN`（ERROR）告警，差额 `actual - reserved`，标记需复核 | `settle()` |
| 结算阶段 Redis 异常 | 写 `REDIS_FAILURE` 告警返回 `changed=false, REDIS_FAILURE`，不抛异常，留待人工对账 | `settle()` |
| 释放阶段 Redis 异常 | 写 `REDIS_FAILURE` 告警，由过期清理任务兜底 | `release()` |
| 清理阶段 Redis 异常 | 记 `redisFailure()` 并返回 0，下一轮重试 | `cleanupExpired()` |
| 预算开关关闭 | `ai.budget.enabled=false` 时 `reserve()` 返回 `DISABLED` 直接放行（本地调试用，生产不应关闭） | `reserve()` |

告警类型集中于 `AiEnums.AlertType`（当前 19 种），其中与预算直接相关的有：`BUDGET_EXCEEDED`、`BUDGET_THRESHOLD`、`SETTLE_OVERRUN`、`SPEND_VELOCITY`、`FORECAST_EXHAUST`、`UNKNOWN_USAGE`、`REDIS_FAILURE`、`RECONCILE_DIFF`、`COST_SPIKE`；均落入 `ai_cost_alert` 表，`message` 不含敏感 Prompt 与密钥。

### 8.1 与预警 / 保护模块的衔接

预扣-结算本身只看「够不够」，**「快不够了」由另两个模块负责**，避免把预警逻辑混进 Lua：

- **结算事件解耦**：`TokenBudgetService.settle()` 成功后发布 `BudgetSettledEvent(requestId, userId, actual)`，由 `BudgetThresholdAlertService` 的 `@EventListener` 消费并做 5s 去抖后再评估阈值。
  - **为什么用事件**：阈值服务需要读 `budgetService.status()` 算使用率，直接构造注入会形成 `TokenBudgetService ↔ BudgetThresholdAlertService` 的循环依赖。事件在分发时才解析监听器，依赖链断开；且 `publishSettled()` 用 `try/catch` 吞掉旁路异常，**告警失败绝不影响结算结果**。
- **补充定时扫描**：`@Scheduled(fixedDelay=120000, initialDelay=90000)` 兜住「没有新请求但额度被下调 / 时间跨天」等事件不会触发的场景。
- **判定口径**：`committed = used + reserved`，不是只看 `used`。否则「钱已花完、请求还没结算」的窗口期会漏报。
- **保护联动**：使用率越线后由 `CostProtectionEngine` 换算成 1~5 级保护等级，`≥3` 时强制模型降档，`5` 时拒绝新的付费调用（`40302`）。

## 9. 已验证与未验证

- 已验证：`RedisBudgetIT` **7 个**真实 Redis 集成测试（本机 DB 15，与运行应用 DB 0 隔离），含 100 并发在 5.00 USD 额度下恰好 5 个通过、预扣总额不超上限、重复 requestId 幂等、重复结算不重复扣费、失败释放、过期清理、**`SCENE` 与 `GLOBAL` 同脚本原子检查**。
- 已端到端实测：五档阈值 50/70/85/95/100% 逐档命中、连续 3 次评估不重复建行、超限请求被 `40302` 拒绝、额度恢复后 5 档告警全部 `RESOLVED` 且请求放行。脚本 `samples/verify-alert-levels.sh`、`samples/verify-alert-recovery.sh`，结果见 `docs/acceptance/TEST_REPORT.md` 第 6、7 节。
- 未验证：Redis Cluster 同槽仅为 key 设计保证，未在 Cluster 环境实测；对账差异（`RECONCILE_DIFF`）未主动制造场景验证；未做压力测试，不提供 QPS / 延迟数字，见 `docs/acceptance/PERFORMANCE_REPORT.md`。
- 一致性等级与放弃的备选方案见 `docs/architecture/ADR/ADR-003-redis-mysql-final-consistency.md`。
