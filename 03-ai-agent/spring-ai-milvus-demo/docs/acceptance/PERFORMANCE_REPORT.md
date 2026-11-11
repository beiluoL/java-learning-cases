# 性能报告

> 本文件对应代码路径：
> - 并发用例：`src/test/java/com/example/ai/token/budget/RedisBudgetIT.java`（`hundredConcurrentRequestsNeverExceedBudget`）
> - 预算脚本：`src/main/resources/lua/budget_reserve.lua`；服务：`src/main/java/com/example/ai/token/budget/TokenBudgetService.java`
> - 统计约束：`src/main/java/com/example/ai/token/analytics/service/CostAnalyticsService.java`、`src/main/java/com/example/ai/config/AiProperties.java`
> - 连接池与超时：`src/main/resources/application.yml`、`frontend/src/api/request.ts`
> - 指标定义：`src/main/java/com/example/ai/metrics/AiMetrics.java`
> - 定时任务：`src/main/java/com/example/ai/token/budget/BudgetReconciler.java`
>
> **本报告只记录已实测的性能相关事实与代码中的配置基线。未做压测的部分一律写明「未做压测」，不提供任何 QPS、TPS、延迟、P95/P99 等推测数字。**

## 1. 已实测：100 并发预算预扣（并发正确性，非压测）

唯一带并发数字的实测来自 `RedisBudgetIT.hundredConcurrentRequestsNeverExceedBudget`，连接真实 Redis（DB 15）：

| 项 | 实测值 |
|---|---|
| 并发请求数 | 100 |
| 执行方式 | `Executors.newFixedThreadPool(32)` + `CountDownLatch` 同时放行 |
| 超时上限 | `done.await(30, TimeUnit.SECONDS)`（用例在 30s 内完成） |
| 额度 / 单请求预扣 | 5.00 USD / 1.00 USD |
| 通过数 | **恰好 5 个** |
| 预扣总额 | `held = 500000000`（1e-8 USD 单位，即 5.00 USD），**不超过上限** |
| `used` | 0 |
| 异常 | 并发过程中 `failure` 为空 |

该用例验证的是**并发场景下 Redis Lua 预扣的正确性（不超额、无部分成功）**，**不是吞吐量或延迟压测**：没有记录耗时、没有统计 QPS、没有递增负载。不得据此推导系统容量。

## 2. 已实现：Redis Lua 单次原子（设计事实，未测量）

- `budget_reserve.lua` 在**一个 Lua 脚本内**先校验全部作用域（`DAILY/MONTHLY/USER/MODEL/GLOBAL` 中命中的那些），任一不足即整体拒绝且零扣减；全部通过后再一次性 `INCRBY` 扣减。Lua 执行期间不会被其它命令插入，因此不存在「部分作用域扣成功」的中间态。
- 金额为 1e-8 USD 整数单位，Lua 全程整数运算；所有 key 带 `{budget}` hashtag，保证 Cluster 同槽。
- 结算 / 释放 / 过期清理同样各自在单个 Lua 内完成。

以上是**代码结构与原子性保证**，`RedisBudgetIT` 从行为上验证了它；**未做脚本执行耗时、单次 Lua 延迟、Redis CPU / 内存占用的测量**。

## 3. 已实现：分页与查询跨度限制（防护设计，非性能数据）

| 约束 | 值 / 机制 | 代码位置 |
|---|---|---|
| 最大查询跨度 | `ai.analytics.max-range-days = 92`；`range()` 用 `ChronoUnit.DAYS` 校验，超限抛 `40001` | `CostAnalyticsService.range()`、`AiProperties` |
| 分页上限 | `max-page-size = 100`；`usagePage()` 把 `size` 夹到 `[1,100]`，`limit` 同理 `bound()` | `CostAnalyticsService` |
| 排序白名单 | `ALLOWED_SORT = {created_at DESC, created_at ASC, total_cost DESC, total_cost ASC, latency_ms DESC}`；不在白名单回退默认 `created_at DESC` | `CostAnalyticsService` |
| SQL 范围 | `CostAnalyticsMapper.xml` 所有统计 SQL 带 `created_at >= from AND created_at < to`，命中 `idx_usage_created` | `mapper/*.xml` |
| 注入防护 | `ORDER BY ${q.sort}` 使用拼接，但取值已被白名单夹住 | `TokenUsageMapper.xml` + Service 白名单 |

这些是**防止统计接口拖垮数据库的保护性设计**；本次**未测量**这些限制下的实际查询耗时、执行计划或索引命中率。

## 4. 配置基线（配置值，不是实测性能）

| 配置 | 值 | 来源 |
|---|---|---|
| Hikari `maximum-pool-size` | 20 | `application.yml` |
| Hikari `minimum-idle` | 2 | `application.yml` |
| Hikari `connection-timeout` | 5000 ms | `application.yml` |
| Hikari `validation-timeout` | 3000 ms | `application.yml` |
| Redis `timeout` / `connect-timeout` | 3s / 2s | `application.yml` |
| 前端 Axios `timeout` | 20000 ms | `frontend/src/api/request.ts` |
| 预算预扣 TTL | 300 s | `application.yml` / `AiProperties` |
| 过期清理调度 | 每 60 s（`fixedDelay=60000`，`initialDelay=30000`） | `BudgetReconciler.cleanup()` |
| 对账调度 | 每 5 min（`fixedDelay=300000`，`initialDelay=60000`） | `BudgetReconciler.reconcile()` |
| 对账差异阈值 | 0.01 USD（超过写 `RECONCILE_DIFF`） | `BudgetReconciler.TOLERANCE` |

`AiMetrics` 注册了 `ai.request.total`、`ai.budget.rejected`、`ai.budget.redis.failure`、`ai.cost.alert`、`ai.request.latency`、`ai.cost.amount` 等指标（Actuator `health/info/metrics` 已暴露）；本次验收**只确认了 `health=UP` 与指标定义存在，未读取各指标的统计值**，因此这些指标不构成性能数据。

## 5. 明确「未做压测」的部分

以下内容本次**均未做压测 / 未测量**，不提供任何数字：

- 未使用 JMeter、wrk、ab、Gatling、k6 等任何压测工具；**无 QPS、TPS、并发用户数下的吞吐量数据**。
- **无平均延迟、P50 / P95 / P99、最大延迟数据**；`/api/ai/chat` 的 `latencyMs` 与 `ai.request.latency` Timer 虽在代码中，但本次未采集分布统计。
- 未做长时间稳定性 / 容量测试，未测大数据量下账单表的查询耗时、执行计划与慢查询。
- 未测 MySQL 连接池在饱和下的行为；未测 Redis 在高并发持续负载下的 CPU / 内存。
- 未测 Redis Cluster（同槽仅为 key 设计保证，未在 Cluster 环境实测）。
- 未测真实供应商网络调用延迟（真实供应商调用已成功完成一次，但本次未记录其耗时，也未做延迟 / 吞吐压测）。
- 未测前端页面渲染性能、首屏时间（浏览器端到端回归未做）。
- 未测 Windows `.bat` 脚本环境下的启动耗时与资源占用（未在 macOS 实测）。
- 未测流式调用（V1 未实现流式端点）。

## 6. 结论

本次验收在性能方面**仅获得一项并发正确性结论**：真实 Redis 上 100 并发、5.00 USD 额度下恰好 5 个请求通过且预扣总额不超上限，无并发异常。其余均为原子性设计、限制防护与配置基线。

**系统吞吐能力、延迟分布、容量上限均未做压测，性能容量结论未得出**；上线前需另行补充压测与慢查询分析。
