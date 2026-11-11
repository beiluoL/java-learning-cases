# 注释规范

> 文档编号：ENG-CM-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 适用范围：Java / TypeScript / Vue / SQL / Shell 全语言  
> 强制力：**「必须」条款不满足的代码评审不通过**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md)

---

## 0. 核心立场：注释解释「为什么」，不解释「是什么」

```java
// ❌ 复述代码，零信息量
// 设置 id 为 null
policy.setId(null);

// ✅ 解释「为什么必须这样做」
// 调用方可能传入 id 触发非预期更新，必须清空后由数据库自增生成
policy.setId(null);
```

判断标准：**把注释删掉，读者会不会少知道一件事？**
如果注释只是把代码翻译成中文，删掉——它会随代码腐化，然后变成误导。

---

## 1. 三类注释，三种职责

| 类型 | 回答的问题 | 位置 | 是否强制 |
|---|---|---|---|
| **类注释** | 这个类在整个系统里承担什么角色 | 类型声明上方 | 必须（公开类型） |
| **方法注释** | 契约：输入、输出、副作用、失败条件 | 方法上方 | 必须（公开 API） |
| **行内注释** | **为什么**这样写（尤其是"看起来多余"的地方） | 语句上方 | 仅在有非显然决策时 |

---

## 2. Java 类注释

### 2.1 必须写的情况

| 类型 | 理由 |
|---|---|
| 所有 `public` 类 / 接口 / 枚举 / record | 是他人理解系统的入口 |
| 所有 `@Service` / `@RestController` / `@Configuration` | Spring 组件，需说明职责边界 |
| 所有 `@Mapper` 接口 | 说明对应哪张表 |
| 所有 `entity` | 说明对应表名与业务含义 |

### 2.2 模板

```java
/**
 * 预算策略的增删改查与变更流水查询。
 *
 * <p>为什么需要这个类：两个预算 Controller 原本各自注入 Mapper 并各自实现了一遍
 * 「校验 + 补默认值 + 分页」，两份实现的默认值补全不一致，导致其中一个接口必然写库失败。
 * 收敛到本 Service 后，两条 HTTP 路径共享同一份字段契约。</p>
 */
@Service
public class BudgetPolicyService {
```

**要素**：一句话职责 + 空行 + `<p>` 段落说明「为什么存在 / 边界在哪」。

### 2.3 实体类

```java
/** 预算策略：GLOBAL / USER / MODEL / DAILY / MONTHLY / SCENE / KNOWLEDGE_BASE / AGENT / REQUEST。 */
@Data
public class BudgetPolicy {
    /** 主键 */
    private Long id;
    /** 超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行。 */
    private String overrunPolicy;
```

**字段注释必须来自数据库 `COMMENT`，逐字一致。** 否则代码与数据库两套说法，
排障时要翻两个地方。

**当前状态**：13 个实体共 **210 个字段，100% 有注释**，且已与 `information_schema.COLUMNS`
的 `COLUMN_COMMENT` 逐字核对。这是本项目的标杆，新实体必须照此执行。

---

## 3. Java 方法注释

### 3.1 必须写的情况

| 情况 | 理由 |
|---|---|
| `public` 方法 | 对外契约 |
| 有副作用的方法 | 调用方必须知道会改什么 |
| 抛异常的方法 | 调用方必须知道要 catch 什么 |
| 参数/返回值语义不显然 | 如「金额是字符串」「比率是 0~1」 |
| **看起来可以简化但不能简化的方法** | 防止后人"优化"掉关键逻辑 |

### 3.2 不必写的情况

| 情况 | 理由 |
|---|---|
| `toString` / `equals` / `hashCode` 覆写 | 语义固定 |
| 简单 getter / setter | 字段注释已足够 |
| `@Override` 且父类注释完整 | 重复 |
| 私有方法且名字已完全表达意图 | 名字即契约 |

### 3.3 必须写清的四件事

```java
/**
 * 补全新建时的 NOT NULL 列默认值。
 *
 * <p><b>缺陷背景（本方法存在的原因）：</b>
 * Mapper 的 INSERT 把所有列都写成显式占位符，因此 Java 侧传 null 会真的插入 NULL，
 * 数据库列默认值不生效，MySQL 严格模式直接拒绝：Column 'overrun_policy' cannot be null。</p>
 *
 * <p>修复前 /api/admin/ai/budgets 只补了 enabled，
 * /api/admin/ai/budgets 与 /api/admin/budget 两条路径的新建都会 500。</p>
 */
private void applyCreateDefaults(BudgetPolicy policy) {
```

| 要素 | 本例中的体现 |
|---|---|
| 做什么 | 「补全 NOT NULL 列默认值」 |
| 为什么存在 | 「INSERT 显式占位符使数据库默认值不生效」 |
| 不这样会怎样 | 「MySQL 严格模式拒绝，接口 500」 |
| 约束/陷阱 | 「两条路径都漏了两个字段」 |

### 3.4 参数与返回值文档

参数 ≤3 且含义明显时可省略 `@param`，改用行内说明。
参数多或类型有歧义时**必须**逐项说明：

```java
/**
 * 预算变更流水。
 *
 * @param days 回看天数，取值 1~92，null 时默认 7 天
 * @return 分页结果，total 为过滤后总数（不随页码变化）
 */
public PageResult<BudgetTransaction> transactions(int page, int size, String budgetType,
                                                 String targetId, Long days) {
```

---

## 4. 行内注释：只写「非显然决策」

### 4.1 必须写行内注释的 6 类情况

```java
// ① 反直觉的写法（看起来能简化，实际不能）
// Set.of(...).contains(null) 抛 NPE，必须先判空；这里不能合并成一行
if (sort == null) { this.sort = DEFAULT_SORT; return; }

// ② 绕过某个已知缺陷
// macOS bash 3.2 会把紧跟变量的中文字节并入变量名，必须写 ${VAR}
echo "[INFO] JAVA_HOME=${JAVA_HOME}（主版本 17）"

// ③ 顺序/时序耦合
// 必须先读原值再注册 trap，否则中途失败时恢复的是被改过的值
ORIGINAL_LIMIT="$(read_limit)"

// ④ 性能/语义上的刻意选择
// 沿用内存过滤而非 SQL 分页：这是人工维护的配置表（7 行），改 SQL 分页属于超范围改造
List<BudgetPolicy> all = policyMapper.list().stream()

// ⑤ 外部系统的隐含约束
// 5173 被其他项目占用，本平台固定 5174，端口冲突时直接报错而非漂移
port: 5174, strictPort: true

// ⑥ 已知的技术债与豁免理由
// 存量容忍：Views 是 4 个域共用的统计契约，冻结扩容（禁止新增 record）
```

### 4.2 禁止的行内注释

| 禁止 | 反例 |
|---|---|
| 复述代码 | `// i 加 1` |
| 注释掉的死代码 | `// policy.setId(id);` |
| 过期信息 | `// 这里调用旧接口` （接口已改） |
| 署名与日期 | `// 张三 2024-01-01 添加`（git 已有） |
| `TODO` 冒充功能 | `// TODO 实现` —— 违反执行纪律，禁止合入 |
| 抱怨 | `// 这段代码是历史遗留，别问我` |
| 分隔装饰 | `// ========== 以下是核心逻辑 ==========` |

---

## 5. TypeScript / Vue 注释

### 5.1 导出的函数必须有 JSDoc

```ts
/**
 * 金额格式化：千分位 + 保留 6~8 位小数，全程不经过 parseFloat。
 * 后端返回 DECIMAL(20,8) 的字符串，转 Number 会丢有效数字。
 */
export function formatMoney(value: unknown, minDecimals = 6, maxDecimals = 8): string {
```

### 5.2 文件头注释

模块级说明放在文件顶部，回答「这个文件负责什么」：

```ts
/**
 * 统一格式化：金额按字符串处理避免 BigDecimal 精度丢失，Token 为整数，
 * 时间统一从 ISO UTC 转成本地时区展示。
 */
```

### 5.3 组件注释

| 位置 | 内容 |
|---|---|
| `<script setup>` 顶部 | 组件职责、使用场景 |
| Props | 每个 prop 的语义与取值范围（用 `/** */`） |
| Emits | 事件触发时机 |
| 复杂 `computed` | 为什么这样派生 |

```vue
<script setup lang="ts">
/**
 * 异步三态容器：统一 loading / error / empty 的展示与重试。
 * 所有需要远程取数的卡片都应包一层，避免每个页面重复写 v-if 三兄弟。
 */
const props = withDefaults(defineProps<{
  /** 是否加载中；true 时渲染 LoadingState */
  loading?: boolean
  /** 错误信息；非空时渲染 ErrorState 并提供重试 */
  error?: string | null
}>(), { loading: false, error: null })
</script>
```

### 5.4 CSS 注释

只在**非显然**处写：magic number 的来源、浏览器兼容 hack、层级原因。

```css
/* 481px 起切换为双列：低于此宽度 StatCard 的最小宽度 220px 会挤成单列 */
@media (min-width: 481px) { ... }
```

---

## 6. SQL / 迁移脚本注释

### 6.1 表与字段

见 `DATABASE_CODING_STANDARDS.md` §5：每表每字段 `COMMENT` 必须有，且说明取值语义。

### 6.2 迁移脚本

```sql
-- V4: 治理域扩展
-- 目的：为预算策略补齐超额处理策略与告警阈值，支撑 V2 治理看板
-- 影响：ai_budget_policy 新增 4 列，均为 NOT NULL + DEFAULT，存量行自动填充
-- 回滚：无（列有默认值，保留不影响旧代码）
ALTER TABLE ai_budget_policy
    ADD COLUMN overrun_policy VARCHAR(16) NOT NULL DEFAULT 'BLOCK'
    COMMENT '超额处理策略：BLOCK 阻断 / DOWNGRADE 降级 / ALLOW 记录并放行';
```

**必须写**：目的 / 影响范围 / 能否回滚。

### 6.3 XML Mapper

```xml
<!-- 预算策略列表：按作用域排序，供前端表格直接渲染，无需二次排序 -->
<select id="list" resultType="com.example.ai.budget.entity.BudgetPolicy">
```

---

## 7. Shell 脚本注释

见 `SCRIPT_CODING_STANDARDS.md` §2：必须的 7 字段头部（用途 / 用法 / 参数 / 环境变量 /
执行前提 / **副作用** / 失败处理）。

函数级：一句话说明 + 副作用。

```bash
# 精确停止单个服务。优先 SIGTERM，等待 10 秒；不升级 kill -9。
# 副作用：删除对应 PID 文件；进程未退出时保留 PID 文件并返回 1。
stop_one() {
```

---

## 8. 语言与文风

| 规则 | 内容 |
|---|---|
| 语言 | **简体中文**（本项目统一） |
| 术语 | 保留英文原词：`Redis` `Lua` `Mapper` `BigDecimal` `Prompt`，不硬译 |
| 人称 | 不用「我」「你」；用「调用方」「本方法」 |
| 时态 | 描述现状用现在时；描述历史缺陷用「修复前…」 |
| 标点 | 中文句号「。」，英文术语与数字间加空格（`JDK 17`、`3 个类`） |
| 长度 | 类注释 1~3 行；方法注释 ≤6 行；行内 ≤2 行 |
| 禁止 | 全大写强调（`必须`→**加粗**）、连续感叹号、表情符号 |

---

## 9. 注释质量的反模式

| 反模式 | 问题 | 正确做法 |
|---|---|---|
| 「同上」「见上文」 | 重构后指向失效 | 重新写清 |
| 「暂时这样，以后改」 | 无人知道何时该改 | 写清触发条件与登记位置 |
| 大段注释解释已删除的代码 | 噪音 | 删掉，git 有历史 |
| 注释与代码矛盾 | 比没注释更危险 | **改代码或改注释，不能不管** |
| 用注释代替重构 | 注释说「这个方法做了 5 件事」 | 拆成 5 个方法 |
| 每个字段都写「字段」 | 零信息 | 写业务语义 |

> **注释与代码不一致 = 缺陷。** 修改代码时必须同步检查相关注释，
> 评审时看到不一致直接打回。

---

## 10. 现状基线（2026-10-26）

| 维度 | 覆盖率 | 说明 |
|---|---:|---|
| 实体字段 | **210 / 210 = 100%** | 与数据库 `COMMENT` 逐字一致（精确统计） |
| 类级 | ≈ 71%（117/164） | 正则近似统计，含内部类与 record |
| 公开方法 | ≈ 30%（93/310） | 正则近似统计；多数简单 CRUD 方法无需 Javadoc |
| 脚本头部 7 字段 | 9 / 9 = 100% | 逐个人工核对 |

**统计方法（可复现，但为近似值）**

```bash
# 实体字段注释覆盖率（精确）
docker exec ai-cost-mysql mysql -uroot -proot -N -e \
  "SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA='ai_cost' AND COLUMN_COMMENT='' AND TABLE_NAME<>'flyway_schema_history';"
# 期望 0 行
```

> **口径说明**：类级与公开方法的数字来自正则扫描，会把内部类、record、
> `equals/hashCode` 等一并计入分母，因此**偏低**，不代表实际质量。
> 唯一精确的口径是实体字段（有数据库作为交叉验证源）。

---

## 11. 评审时的三条注释检查

1. **删掉这条注释，读者会少知道什么？** —— 答不出来就删。
2. **注释和代码说的是同一件事吗？** —— 不一致就打回。
3. **「为什么」写了吗？** —— 只有「是什么」的注释等于没写。

---

## 12. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。含 6 类必须写行内注释的场景、7 条注释反模式、口径说明 |
