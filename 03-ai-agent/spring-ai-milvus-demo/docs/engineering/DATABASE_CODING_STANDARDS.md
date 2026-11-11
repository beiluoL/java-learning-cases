# 数据库与 SQL 编码规范

> 文档编号：ENG-DB-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 数据库：MySQL 8.0.36（容器 `ai-cost-mysql`，宿主机端口 `13307`）  
> 库名：`ai_cost` · 字符集 `utf8mb4` · 排序规则 `utf8mb4_0900_ai_ci` · 引擎 `InnoDB`  
> 迁移工具：Flyway（`src/main/resources/db/migration/V<n>__*.sql`）  
> 强制力：**违反「必须」条款的 SQL 不得合并**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md) · 相关：[`BACKEND_CODING_STANDARDS.md`](./BACKEND_CODING_STANDARDS.md)

---

## 0. 三条不可协商的原则

| 原则 | 含义 | 违反后果 |
|---|---|---|
| **金额只能是 `DECIMAL`** | 任何金额列 `DECIMAL(20,8)`，Java 侧 `BigDecimal` | 浮点误差会在成本累计中放大，账目对不上 |
| **注释与列同生共死** | 每张表、每个字段必须有 `COMMENT` | 半年后没人知道 `allow_overrun=0` 到底拦不拦 |
| **迁移只增不改** | 已执行的 `V<n>__*.sql` 一个字符都不能改 | 已上线环境的 schema 与代码不一致，Flyway 校验失败 |

---

## 1. 表命名

| 规则 | 内容 |
|---|---|
| 格式 | `snake_case`，全小写，单词间 `_` |
| 前缀 | 业务表统一 `ai_`（表示 AI 成本治理域） |
| 单复数 | **单数**（`ai_token_usage` 不是 `ai_token_usages`） |
| 长度 | ≤ 32 字符 |
| 禁止 | 拼音、缩写、`tbl_` / `tb_` 前缀、大写字母 |

**命名模式**（按表承载的语义选择）：

| 后缀 | 表承载什么 | 本项目实例 |
|---|---|---|
| `_policy` | 可配置的策略/规则 | `ai_budget_policy` `ai_cost_protection_rule` |
| `_usage` | 原始用量明细 | `ai_token_usage` |
| `_ledger` | 账务流水（只追加，不可改） | `ai_cost_ledger` |
| `_transaction` | 状态变更流水 | `ai_budget_transaction` |
| `_reservation` | 有生命周期的占位（预扣） | `ai_budget_reservation` |
| `_log` | 操作/决策日志 | `ai_cost_protection_log` `ai_cost_optimization_log` |
| `_trace` | 链路追踪 | `ai_agent_cost_trace` |
| `_summary` | 预聚合结果 | `ai_cost_daily_summary` |
| `_pricing` | 价格表 | `ai_model_pricing` |
| `_alert` | 告警 | `ai_cost_alert` |
| `_cost` | 成本项 | `ai_infrastructure_cost` |

---

## 2. 字段命名

| 规则 | 内容 |
|---|---|
| 格式 | `snake_case`，单数 |
| 布尔语义 | `is_` / `has_` / `allow_` 前缀，或 `_enabled` / `_flag` 后缀；**禁止否定式**（`not_blocked` ❌） |
| 时间字段 | `_at` 结尾表示时刻（`created_at`）；`_date` 结尾表示日期（`summary_date`）；`_start`/`_end` 表示区间 |
| 金额字段 | `_amount` / `_cost` / `_limit` / `_price` 结尾，**必须 `DECIMAL(20,8)`** |
| 计数/Token | `_count` / `_tokens` 结尾，`BIGINT` |
| 比率 | `_rate` / `_ratio` / `_threshold` 结尾，`DECIMAL(6,4)` 或 `DECIMAL(5,4)`，取值 0~1 |
| 外键逻辑字段 | `_id` 结尾（`user_id` `request_id`） |
| 枚举 | `VARCHAR(16/32)` + `COMMENT` 列出全部取值 |
| 禁止 | `type`（太泛，用 `alert_type` `entry_type`）、`status`（用 `request_status` `usage_status`）、`data`、`value`、`info` |

---

## 3. 必须存在的字段

每张业务表都必须有以下字段（顺序固定放在最后几列）：

```sql
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)'
```

| 规则 | 内容 |
|---|---|
| 精度 | `DATETIME(3)`（毫秒），不用 `DATETIME`（秒级不够区分并发） |
| **时区** | 全链路 UTC。容器 TZ=UTC，JDBC `serverTimezone=UTC`，Java 用注入的 `Clock.systemUTC()` |
| `updated_at` | 带 `ON UPDATE CURRENT_TIMESTAMP(3)`，不要靠应用层维护 |
| 主键 | `id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY` |
| 纯追加流水表 | 可只保留 `created_at`（`ai_cost_ledger` 等） |
| 逻辑删除 | **不使用**。本项目用状态字段（`status`）表达，避免所有查询都要带 `deleted=0` |

---

## 4. 数据类型

| 语义 | 必须用 | 禁止用 | 原因 |
|---|---|---|---|
| 金额 | `DECIMAL(20,8)` | `FLOAT` / `DOUBLE` | 精度丢失，累计后账目不符 |
| Token 数 / 计数 | `BIGINT` | `INT`（大模型长上下文会溢出） | |
| 主键 | `BIGINT UNSIGNED AUTO_INCREMENT` | `VARCHAR(32)` UUID | UUID 作主键导致页分裂、索引膨胀 |
| 业务唯一键 | `VARCHAR(64)` + `UNIQUE` | — | `request_id` `task_id` `tx_no` |
| 枚举 | `VARCHAR(16/32)` | `TINYINT` + 魔法数字 | 可读性：`BLOCK` 一眼懂，`2` 要查文档 |
| 布尔 | `TINYINT(1)` + `COMMENT '0 否 1 是'` | `BOOLEAN`（MySQL 里本质就是 TINYINT） | |
| 大文本 | `TEXT` / `MEDIUMTEXT` | `VARCHAR(65535)` | |
| 短文本 | `VARCHAR(n)`，n 贴合业务上限 | `VARCHAR(255)` 一把梭 | |
| JSON | 谨慎；优先拆列 | 用 JSON 存关键查询字段 | 无法建索引、无法约束 |

**长度必须贴合业务**：

| 字段 | 长度 | 依据 |
|---|---|---|
| `request_id` / `trace_id` | `VARCHAR(64)` | `trc-` + 32 位 hex = 36 |
| `user_id` | `VARCHAR(64)` | 演示用户 / 外部 ID |
| `provider` / `model_name` | `VARCHAR(64)` | |
| `overrun_policy` | `VARCHAR(16)` | `BLOCK`/`DOWNGRADE`/`ALLOW` 最长 9 |
| `currency` | `VARCHAR(8)` | ISO 4217 最长 3 |

---

## 5. 注释（强制）

### 5.1 表注释

```sql
CREATE TABLE ai_budget_policy (
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算策略：按用户/模型/场景等作用域限额';
```

### 5.2 字段注释

**每个字段必须有 `COMMENT`**，且必须回答「这个值是什么语义」，而不是复述字段名：

```sql
-- ❌ 复述字段名，等于没写
allow_overrun TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许超额',

-- ✅ 说明取值语义
allow_overrun TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许超额：0 严格拦截 1 允许并告警',

-- ❌ 只说类型不说含义
alert_threshold DECIMAL(5,4) COMMENT '阈值',

-- ✅ 给出范围与单位
alert_threshold DECIMAL(5,4) COMMENT '预算使用率告警阈值（0~1），NULL 用全局默认',
```

### 5.3 枚举字段必须列出全部取值

```sql
overrun_policy VARCHAR(16) NOT NULL DEFAULT 'BLOCK'
    COMMENT '超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行',
```

**现状**：13 张业务表 **100% 有表注释与字段注释**，这是本项目做得最好的部分之一，必须保持。

---

## 6. 索引

### 6.1 必须

| 规则 | 内容 |
|---|---|
| 主键 | 自增 `id` |
| 业务唯一键 | 必须建 `UNIQUE`，靠数据库兜住并发重复（不靠应用层先查再插） |
| 查询条件 | `WHERE` 中高选择性的列建索引 |
| **复合索引的最左前缀** | 等值列在前，范围列（时间）在后 |
| 时间维度 | 高频按时间过滤的表，复合索引末位放 `created_at` |

### 6.2 命名

| 类型 | 前缀 | 示例 |
|---|---|---|
| 唯一索引 | `uk_` | `uk_budget_scope` |
| 普通索引 | `idx_` | `idx_usage_user_scene` |
| 外键（本项目不用物理外键） | `idx_fk_` | — |

### 6.3 禁止

| 禁止 | 原因 |
|---|---|
| 在 `VARCHAR(255)` 上建不加前缀的索引 | 超 3072 字节键长限制 |
| 给低选择性单列建索引（如 `status` 只有 3 值） | 优化器通常不走，白白写放大 |
| 一个表建 >6 个索引 | 写入放大，每个 INSERT 都要维护 |
| 索引列上做函数运算（`WHERE DATE(created_at) = ?`） | 索引失效，改为范围查询 |

### 6.4 现有索引清单（13 表实测，作为新增索引的参照）

| 表 | 唯一键 | 复合索引 |
|---|---|---|
| `ai_agent_cost_trace` | `uk_agent_task(task_id)` | `idx_agent_trace_status(status,started_at)`、`idx_agent_trace_user(user_id,started_at)` |
| `ai_budget_policy` | `uk_budget_scope(budget_type,target_id)` | `idx_budget_enabled(enabled,budget_type)` |
| `ai_budget_reservation` | `uk_reservation_request(request_id)` | `idx_reservation_status_expire(status,expire_at)`、`idx_reservation_user(user_id,created_at)` |
| `ai_budget_transaction` | `uk_budget_tx_no(tx_no)` | `idx_budget_tx_request(request_id)`、`idx_budget_tx_scope(budget_type,target_id,created_at)` |
| `ai_cost_alert` | — | `idx_alert_fingerprint(fingerprint,status)`、`idx_alert_status_level(status,alert_level,created_at)`、`idx_alert_type(alert_type,created_at)`、`idx_alert_created(created_at)` |
| `ai_cost_daily_summary` | `uk_daily_summary(summary_date,dimension,dimension_key)` | `idx_daily_summary_date(summary_date)` |
| `ai_cost_ledger` | `uk_ledger_entry(entry_no)` | `idx_ledger_type(entry_type,occurred_at)`、`idx_ledger_user(user_id,occurred_at)`、`idx_ledger_scene(business_scene,occurred_at)`、`idx_ledger_occurred(occurred_at)` |
| `ai_cost_optimization_log` | — | `idx_optimization_request(request_id)`、`idx_optimization_strategy(strategy,created_at)` |
| `ai_cost_protection_log` | — | `idx_protection_log_action(action,created_at)`、`idx_protection_log_created(created_at)` |
| `ai_cost_protection_rule` | `uk_protection_rule_code(rule_code)` | `idx_protection_rule_level(protection_level,enabled)` |
| `ai_infrastructure_cost` | — | `idx_infra_period(period_start,period_end)`、`idx_infra_type(resource_type,period_start)` |
| `ai_model_pricing` | `uk_pricing_version(provider,model_name,version)` | `idx_pricing_lookup(provider,model_name,model_type,enabled,effective_from)`、`idx_pricing_effective(effective_from,effective_to)` |
| `ai_token_usage` | `uk_usage_request(request_id)` | `idx_usage_user_scene(user_id,business_scene,created_at)`、`idx_usage_model(provider,model_name,created_at)`、`idx_usage_status_scene(request_status,usage_status,created_at)`、`idx_usage_created(created_at)`、`idx_usage_agent(agent_id,created_at)`、`idx_usage_kb(knowledge_base_id,created_at)` |

---

## 7. SQL 编写

### 7.1 禁止 `SELECT *`

**这是本项目已修复的真实问题（DB-03，31 处）。**

```sql
-- ❌ 加一列就静默改变结果集；无法用覆盖索引；多余 IO
SELECT * FROM ai_token_usage WHERE user_id = #{userId}

-- ✅ 显式列出，并按索引顺序利于覆盖索引
SELECT id, request_id, user_id, provider, model_name, model_type,
       input_tokens, output_tokens, total_cost, latency_ms, created_at
FROM ai_token_usage WHERE user_id = #{userId}
```

**为什么必须显式**：本项目 31 处 `SELECT *` 的展开是靠 `information_schema.columns`
的 `ordinal_position` 逐表比对得出列名与顺序的，**0 差异**才敢改。
如果当时有新增列，`resultType` 自动映射会把新列映射进实体，行为静默变化。

**检测**：

```bash
grep -rniE 'select +\*' src/main/resources/mapper/     # 必须 0 行
```

### 7.2 参数绑定

| 写法 | 允许 | 说明 |
|---|---|---|
| `#{param}` | ✅ | 预编译绑定，防注入 |
| `#{param,jdbcType=VARCHAR}` | ✅ | `null` 值需显式 jdbcType |
| `${param}` | ❌ | 字符串拼接，注入面 |
| `${param}` 白名单后 | ⚠️ | 仅 `ORDER BY` / 表名等无法参数化的位置，**白名单必须在最小作用域内** |

**`ORDER BY ${sort}` 的正确做法**

```java
// ✅ 白名单下沉到参数的 setter，任何赋值路径都被过滤（本项目 DB-04 的修复方式）
public void setSort(String sort) {
    if (sort == null) {                    // 注意：Set.of(...).contains(null) 会抛 NPE
        this.sort = DEFAULT_SORT;
        return;
    }
    String mapped = SORT_ALIASES.getOrDefault(sort, sort);
    this.sort = ALLOWED_SORT.contains(mapped) ? mapped : DEFAULT_SORT;
}
```

**为什么放在 setter 而不是 Service**：在 Service 里过滤依赖「每个调用方都记得调」，
新增一个调用方就漏了；放在 setter 里，任何赋值路径都必然经过，**结构上无法绕过**。

### 7.3 分页

```sql
SELECT ... FROM ai_token_usage
WHERE user_id = #{userId} AND created_at BETWEEN #{from} AND #{to}
ORDER BY created_at DESC
LIMIT #{limit} OFFSET #{offset}
```

| 规则 | 内容 |
|---|---|
| `LIMIT` 必须有上限 | Controller/DTO 层钳制，本项目上限 100 |
| 深分页 | `OFFSET` > 10000 时改用「上次最大 id」游标分页 |
| `COUNT(*)` 与列表查询分离 | 不要用 `SQL_CALC_FOUND_ROWS`（MySQL 8 已废弃） |
| 排序必须确定 | `ORDER BY created_at DESC` 若时间可能重复，加 `, id DESC` 保证稳定 |

### 7.4 JOIN

| 规则 | 内容 |
|---|---|
| 最多 2 表 JOIN | 超过说明该建汇总表（本项目用 `ai_cost_daily_summary`） |
| JOIN 列必须有索引 | 否则退化为笛卡尔扫描 |
| 别名必须有意义 | `u` `p` 可接受；`a` `b` `t1` 不行 |
| 不建物理外键 | 由应用层保证一致性；外键在分库/批量导入时是负担 |

### 7.5 聚合

| 规则 | 内容 |
|---|---|
| 金额聚合用 `SUM(DECIMAL)` | 禁止 `SUM(DOUBLE)` |
| `AVG` 显式 `ROUND` | `ROUND(AVG(x), 8)` |
| `GROUP BY` 列必须在 `SELECT` 中 | 依赖 `ONLY_FULL_GROUP_BY`（MySQL 8 默认开启） |
| 时间分桶 | `DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00')`，且只在聚合报表中允许（不走索引，但报表可接受） |

---

## 8. 迁移（Flyway）

### 8.1 铁律

| 规则 | 内容 |
|---|---|
| 文件命名 | `V<n>__<snake_case_描述>.sql`（两个下划线） |
| **已执行的文件禁止修改** | 只能新增 `V6__`、`V7__`。改动已执行文件会导致 `flyway_schema_history` 校验和不匹配 |
| 序号连续 | 不跳号 |
| 一个迁移一个意图 | 不要在 `V6` 里既加表又改字段 |
| 必须可重复执行于空库 | 新环境从 `V1` 跑到 `Vn` 必须成功 |

### 8.2 现有迁移

| 文件 | 内容 |
|---|---|
| `V1__init_ai_cost.sql` | 建表（核心 8 表） |
| `V2__seed_demo_data.sql` | 演示数据 |
| `V3__add_provider_alias_demo_pricing.sql` | 价格别名 |
| `V4__governance_schema.sql` | 治理域扩展（保护规则、告警指纹、超额策略等） |
| `V5__governance_seed.sql` | 治理域种子 |

**新增一律 `V6__` 起。** 这 5 个文件已登记为豁免项，任何人都不得修改。

### 8.3 种子数据的写法

```sql
-- ✅ 幂等：可重复执行
INSERT INTO ai_budget_policy (budget_type, target_id, daily_limit, ...)
VALUES ('GLOBAL', '__ALL__', 100.00000000, ...)
ON DUPLICATE KEY UPDATE daily_limit = VALUES(daily_limit);

-- ❌ 裸 INSERT：第二次执行报主键/唯一键冲突，无法重播种
INSERT INTO ai_budget_policy (...) VALUES (...);
```

> 现状：`V2` / `V5` 使用了裸 `INSERT`（DB-02，P3）。
> 因已执行且只需执行一次，**不修改**；但新增种子脚本必须写成 `ON DUPLICATE KEY UPDATE`。

### 8.4 DDL 变更的安全顺序

```sql
-- 加字段：必须给默认值或允许 NULL，否则已有行无法通过 NOT NULL 约束
ALTER TABLE ai_budget_policy
    ADD COLUMN overrun_policy VARCHAR(16) NOT NULL DEFAULT 'BLOCK'
    COMMENT '超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行';

-- 加索引：大表用 ALGORITHM=INPLACE, LOCK=NONE
ALTER TABLE ai_token_usage ADD INDEX idx_usage_x (col), ALGORITHM=INPLACE, LOCK=NONE;

-- 删字段/改类型：先发版停止读写该列，下个版本再删
```

---

## 9. MyBatis 映射约定

### 9.1 驼峰映射（本项目 0 个 `resultMap` 的前提）

```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true    # 必须开启
```

| 前提条件 | 说明 |
|---|---|
| 列名 `snake_case` ↔ 字段 `lowerCamelCase` | 严格一一对应 |
| 查询列名与实体字段同名 | 别名必须显式 `AS` 成驼峰 |
| `SELECT *` 已归零 | 否则新增列会被自动映射进实体 |

### 9.2 何时必须写 `resultMap`

| 场景 | 原因 |
|---|---|
| 聚合查询返回 `Map` | 无实体可映射，但**禁止**作为 Controller 返回类型 |
| JOIN 出现同名列 | 别名冲突，自动映射会错 |
| 需要嵌套集合映射 | `<collection>` 无法用自动映射表达 |
| 列名与字段无法驼峰对应 | 如 `cnt` → `requestCount` |

### 9.3 Mapper 接口

| 规则 | 内容 |
|---|---|
| 必须标注 `@Mapper` | 否则 `@MapperScan` 扫不到，运行期报 `Invalid bound statement` |
| 参数 ≥2 个必须 `@Param` | 否则 MyBatis 按 `param1/param2` 绑定，易错 |
| `namespace` 必须等于接口全限定名 | |
| 不在接口写 `default` 业务方法 | 逻辑归 Service |
| 方法名语义化 | `pageByUserAndScene` 而非 `query1` |

---

## 10. 事务与锁

| 规则 | 内容 |
|---|---|
| 多表写必须事务 | 见 `BACKEND_CODING_STANDARDS.md` 4.3 |
| 唯一键兜住并发重复 | 不依赖「先 SELECT 再 INSERT」，直接 INSERT 并 `catch DuplicateKeyException` |
| 悲观锁 `SELECT ... FOR UPDATE` | 必须走主键或唯一索引，否则锁表 |
| 批量更新分批 | 单次 ≤ 1000 行，避免长事务与主从延迟 |
| 禁止长事务包裹外部调用 | 模型调用可能 30s+，必须在事务外 |

**本项目的 Redis + DB 一致性口径**（必须理解，不可简化）：

```
Redis：并发控制的真源（Lua 原子预扣/结算/释放）
MySQL：审计流水与账单的持久层
二者不是同一事务 → 最终一致，由 BudgetReconciler 定期对账
```

任何注释或文档不得声称这两者是强一致。

---

## 11. 拒绝清单

| 编号 | 拒绝项 | 检测方式 |
|---|---|---|
| S-01 | `SELECT *` | `grep -rniE 'select +\*' src/main/resources/mapper/` |
| S-02 | `${}` 拼接（白名单外） | `grep -rn '\${' src/main/resources/mapper/` |
| S-03 | 金额用 `FLOAT`/`DOUBLE` | `grep -niE '(amount\|cost\|price\|limit).*(double\|float)'` |
| S-04 | 缺 `COMMENT` 的表/字段 | `information_schema` 比对 |
| S-05 | 表名/字段名非 `snake_case` | `grep -nE '[A-Z]' ` 建表语句 |
| S-06 | 物理外键 | `grep -in "FOREIGN KEY"` |
| S-07 | 修改已执行的 `V<n>__*.sql` | `git diff --name-only` 命中 migration 目录即拦 |
| S-08 | 裸 `INSERT` 种子（不可重播种） | 人工评审 |
| S-09 | 无 `LIMIT` 的分页查询 | 人工评审 |
| S-10 | `WHERE` 中列上做函数运算 | 人工评审 + 慢查询日志 |

---

## 12. 现状核对（2026-10-26 实测）

| 指标 | 当前 | 目标 |
|---|---:|---:|
| 业务表数 | 13 | — |
| 全部 `InnoDB` | 13 / 13 | 13 / 13 ✅ |
| 全部 `utf8mb4` | 13 / 13 | 13 / 13 ✅ |
| 表注释覆盖 | 13 / 13 | 100% ✅ |
| 字段注释覆盖 | 100% | 100% ✅ |
| `SELECT *` | 0 | 0 ✅ |
| 非法 `${}` | 0 | 0 ✅ |
| 物理外键 | 0 | 0 ✅ |
| 唯一键覆盖 | 每表均有 | ✅ |
| 金额列 `DECIMAL(20,8)` | 全部 | ✅ |

复现命令：

```bash
# 表与引擎
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='ai_cost';"

# 索引清单
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX), NON_UNIQUE
   FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='ai_cost'
   GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE ORDER BY TABLE_NAME, INDEX_NAME;"

# 缺注释的字段（必须 0 行）
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA='ai_cost' AND (COLUMN_COMMENT='' OR COLUMN_COMMENT IS NULL);"
```

---

## 13. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。索引清单与表结构来自 `information_schema` 实测；含 10 项拒绝条款 |
