# 后端编码规范（Java / Spring Boot）

> 文档编号：ENG-BE-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 规范基线：**《阿里巴巴 Java 开发手册》（黄山版）** + P3C 规则集  
> 技术栈：Java 17 / Spring Boot 3.x / MyBatis / Redis  
> 强制力：**违反「必须」条款的代码评审不通过；「建议」条款需在评审中说明理由**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md)  
> 相关文档：[`DIRECTORY_STRUCTURE.md`](./DIRECTORY_STRUCTURE.md) · [`COMMENT_STANDARDS.md`](./COMMENT_STANDARDS.md) · [`DATABASE_CODING_STANDARDS.md`](./DATABASE_CODING_STANDARDS.md)

---

## 0. 本规范的取舍原则

阿里巴巴手册是**通用**基线，本项目在少数条款上有**明确的、有理由的偏离**。
遇到冲突时按下列优先级判断，不要机械套用手册：

| 优先级 | 原则 | 说明 |
|---:|---|---|
| 1 | 不改变业务行为 | 任何「规范化」都不得改变计算结果、金额口径、并发语义 |
| 2 | 可被工具检查 | 能写成 Checkstyle/Spotless 规则的，优先于口头约定 |
| 3 | 与本项目既有约定一致 | 例：Service 不建接口、金额对外用 `String` |
| 4 | 阿里手册 | 默认遵循 |

**已登记的偏离**（不是违规，不要「修正」它们）：

| 条款 | 手册说法 | 本项目做法 | 理由 |
|---|---|---|---|
| Service 接口 | 建议「接口 + 实现」 | 18 个 Service 均为无接口 class | 无 ≥2 实现、无 SPI、无 AOP 增强需求；空接口只增加跳转成本 |
| 金额对外类型 | 未规定 | Controller 返回 `String` 形式的金额 | JS `Number` 只有 53 位精度，`DECIMAL(20,8)` 必须转字符串 |
| 枚举 | 建议 `enum` | `common/AiEnums` 集中放枚举常量 | 枚举参与数据库字符串映射，集中放置便于全局检索 |
| VO 容器 | 建议独立文件 | `common/Views` 内联 12 个 record | 4 个域共用的统计契约，拆散会波及全部 Controller；已冻结扩容 |

---

## 1. 命名规约

### 1.1 必须

| 元素 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写，单词间无分隔符，单数 | `com.example.ai.budget.service` |
| 类 | `UpperCamelCase` | `BudgetPolicyService` |
| 方法 | `lowerCamelCase`，动词开头 | `reserveBudget` `applyCreateDefaults` |
| 变量 | `lowerCamelCase`，语义完整 | `remainingAmount`（不用 `ra`、`tmp`） |
| 常量 | `UPPER_SNAKE_CASE` + `static final` | `DEFAULT_OVERRUN_POLICY` |
| 布尔 | `is` / `has` / `can` / `allow` / `enable` 开头，**禁止否定式** | `allowOverrun`（不用 `notBlock`） |
| 缩写 | 连续大写不超过 2 个字母，且不作为词首 | `AiChatService`（不用 `AIChatService`） |
| 泛型 | 单大写字母；集合用 `E`/`T`/`K`/`V` | `PageResult<T>` |

### 1.2 禁止

| 禁止 | 反例 | 正例 |
|---|---|---|
| 拼音命名 | `yusuan`、`chengben` | `budget`、`cost` |
| 无意义名 | `data`、`info`、`list`、`temp`、`obj` | `todayOverview`、`pendingReservations` |
| 单字母（循环变量 `i`/`j` 除外） | `BudgetPolicy p` | `BudgetPolicy policy` |
| 类型后缀冗余 | `String userNameString` | `String userName` |
| `Impl` 后缀但无接口 | `CostCalculatorImpl` | `CostCalculator` |
| 否定布尔 | `notEnabled`、`disableFlag` | `enabled` |
| 数字区分 | `getData1`、`getData2` | 按语义重命名 |

### 1.3 本项目已固化的命名模式

新增类时按此表命名，保持全局一致（`Checkstyle` 已配置对应规则）：

| 后缀 | 含义 | 是否注入依赖 | 是否持事务 |
|---|---|---|:---:|
| `*Controller` | HTTP 入口 | 是 | 否 |
| `*Service` | 业务编排 | 是 | 是 |
| `*Mapper` | 数据访问 | —（接口） | 否 |
| `*Engine` | 状态机/判定引擎 | 是 | 否 |
| `*Router` | 策略选择 | 是 | 否 |
| `*Calculator` | 纯计算 | 否/极少 | 否 |
| `*Estimator` | 估算 | 否 | 否 |
| `*Optimizer` | 输入改写 | 否 | 否 |
| `*Compressor` | 上下文压缩 | 否 | 否 |
| `*Collector` | 采集落库 | 是 | 是 |
| `*Tracker` | 生命周期追踪 | 是 | 是 |
| `*Reconciler` | 对账补偿 | 是 | 是 |
| `*Adapter` | 外部系统适配 | 是 | 否 |
| `*Properties` | 配置绑定 | — | — |
| `*Event` | 领域事件 | — | — |

---

## 2. 方法设计

### 2.1 长度与参数

| 项 | 硬上限 | 软上限 | 处置 |
|---|---:|---:|---|
| 方法行数（不含 javadoc/空行） | **50** | 30 | 按「阶段」抽私有方法，或抽记录类型聚合参数 |
| 方法参数个数 | **7** | 4 | 超过则用 `record` 聚合（见 2.3） |
| 嵌套深度 | **3** | 2 | 提前 `return` / `continue` 消嵌套 |
| 类字段数 | **15** | 10 | 拆分职责 |
| 构造依赖数 | **10** | 7 | 拆分职责；>10 说明这个类做了太多事 |

**现状（2026-10-26 实测，350 个方法）**

```
> 50 行：2 个   TokenUsageCollector.collect (58) / BudgetThresholdAlertService.evaluate (55)
> 30 行：16 个
```

这 2 个超限方法已登记为技术债（`PROJECT_CODE_AUDIT.md` BE-06 同类），在 B6 批次处理。
**新增方法一律不得超过 50 行**，超过即评审不通过。

### 2.2 单一职责与命令查询分离

| 规则 | 说明 |
|---|---|
| 一个方法只做一件事 | 方法名里的「和」「并」「再」是坏味道 |
| 查询方法不产生副作用 | `getXxx` / `find` / `count` 不得写库、不得改状态 |
| 命令方法返回结果而非仅改状态 | `create()` 返回落库后的完整对象（含自增主键），便于调用方判断 |
| 提前返回优于嵌套 | `if (x == null) return ...;` 而非层层 `if (x != null) { ... }` |
| 空集合优于 `null` | 返回 `List.of()` 而不是 `null`，调用方无需判空 |

### 2.3 参数聚合：`record` 优先

参数 ≥5 个且高内聚时，用**私有 record** 聚合。这不是「为了少写参数」，而是让编译器帮你保证「这一组值总是同时存在」。

```java
// ❌ 13 个参数一路透传，任何一个传错位置编译器都不报错
private ChatResponseVO complete(String requestId, String traceId, String userId, String conversationId,
                                String agentId, String agentTaskId, String kbId, String scene,
                                String provider, RoutedStage routed, ReservationResult res, ...)

// ✅ 聚合成记录类型，字段名即文案，顺序错误无法通过编译
private record RequestScope(String requestId, String traceId, String userId, String conversationId,
                            String agentId, String agentTaskId, String kbId, String scene,
                            String provider, String message, boolean useRag) {
    static RequestScope of(ChatDtos.ChatRequest request, String userId, String provider) { ... }
}

private ChatResponseVO completeCall(RequestScope scope, RoutedStage routed, ...)
```

**约束**：`record` 只用于**方法间传递**，不得作为 Controller 返回类型（那需要独立 `vo` 文件，见 `DIRECTORY_STRUCTURE.md` 3.5）。

---

## 3. 类型与不可变性

### 3.1 类型选择

| 场景 | 必须用 | 禁止用 | 原因 |
|---|---|---|---|
| 金额 | `BigDecimal` | `float` / `double` | 二进制浮点无法精确表示 0.1，成本系统会累积误差 |
| 金额对外传输 | `String`（`toPlainString()`） | `BigDecimal` 直接序列化 | JS `Number` 只有 53 位精度，`DECIMAL(20,8)` 会丢 |
| 金额入库 | `DECIMAL(20,8)` | `DOUBLE` | 同上 |
| 计数 / Token 数 | `long` | `int` | Token 量大，`int` 会溢出 |
| 比率 | `double`（0~1） | 百分比整数 | 前端统一格式化 |
| 时间戳 | `LocalDateTime`（UTC） | `Date` / `long` / `Timestamp` | 见 4.2 |
| 日期 | `LocalDate` | `String` | |
| 不可变数据载体 | `record` | `@Data` class | |
| 需 Spring 绑定/校验的入参 | `class` + `@Data` | `record` | 需 setter 与注解 |

**金额运算的精度纪律**

```java
// 必须显式指定 scale 与舍入模式，禁止无参 divide
BigDecimal avg = total.divide(count, 6, RoundingMode.HALF_UP);

// 比较金额用 compareTo，禁止 equals
if (a.compareTo(b) > 0) { ... }        // ✅
if (a.equals(b)) { ... }                // ❌ 1.0 与 1.00 不 equals
```

### 3.2 集合

| 规则 | 说明 |
|---|---|
| 小集合用 `List.of()` / `Set.of()` / `Map.of()` | 不可变，防止意外修改 |
| **`Set.of(...).contains(null)` 抛 NPE** | 必须先判 `null`（本项目已因此修复 1 个缺陷，见 `UsageQuery.setSort`） |
| **`Map.of(...).getOrDefault(null, v)` 抛 NPE** | `Map.of` 不接受 `null` 键 |
| 遍历时删除元素用 `Iterator.remove` 或 `removeIf` | 禁止 `for-each` 内 `list.remove` |
| 返回空集合而非 `null` | `List.of()` |
| 大集合初始化指定容量 | `new ArrayList<>(expectedSize)` |

> ⚠️ **`Set.of` / `Map.of` 的 null 陷阱是本项目踩过的真实坑**：
> `Set.of(ALLOWED).contains(null)` 不是返回 `false`，而是抛 `NullPointerException`。
> 任何「用集合兜住 null」的写法都是错的，必须显式判空。

### 3.3 字符串

```java
// 判空统一用工具方法，禁止 == null || "".equals(x) 这种手写组合
if (value == null || value.isBlank()) { ... }

// 比较用常量在前，避免 NPE
if ("BLOCK".equals(policy)) { ... }     // ✅
if (policy.equals("BLOCK")) { ... }      // ❌ policy 为 null 时 NPE
```

---

## 4. Spring Boot 专项

### 4.1 依赖注入：只用构造器

| 规则 | 状态 |
|---|---|
| **禁止 `@Autowired` 字段注入** | 本项目 **0 处**，保持 |
| 单构造器不写 `@Autowired` | Spring 4.3+ 自动识别 |
| `final` 修饰所有注入字段 | 保证不可变 |
| 参数 ≥8 个时考虑拆分职责 | 不要用 `@Lazy` 掩盖设计问题 |

```java
// ✅ 唯一允许的形式
private final BudgetPolicyMapper policyMapper;
private final Clock clock;

public BudgetPolicyService(BudgetPolicyMapper policyMapper, BudgetTransactionMapper txMapper, Clock clock) {
    this.policyMapper = policyMapper;
    this.txMapper = txMapper;
    this.clock = clock;
}
```

### 4.2 时间：必须用注入的 `Clock`

**这是本项目已发生过的真实缺陷（BE-22）。**

```java
// ❌ 用本机时区（UTC+8），而库里存的是 UTC → 查询窗口整体偏移 8 小时，静默漏行
LocalDateTime to = LocalDateTime.now().plusMinutes(1);

// ✅ 用注入的 Clock（Bean 定义为 Clock.systemUTC()）
LocalDateTime to = LocalDateTime.now(clock).plusMinutes(1);
```

| 规则 | 内容 |
|---|---|
| 唯一时间源 | `config/AiModelConfig#clock()` → `Clock.systemUTC()` |
| 禁止 | `LocalDate.now()` / `LocalDateTime.now()` / `Instant.now()` / `System.currentTimeMillis()`（除非明确需要墙钟做 latency 测量） |
| 数据库时区 | JDBC URL 固定 `serverTimezone=UTC`，容器 TZ=UTC，**全链路 UTC** |
| 对外展示 | 由前端格式化成本地时区，后端不转换 |
| 可测性 | 注入 `Clock.fixed(...)` 即可断言时间窗，无需 `Thread.sleep` |

> **例外**：`AiChatService` 中用 `System.currentTimeMillis()` 测量单次调用耗时（latency）——
> 这是**时长**而非**时刻**，用 `Clock` 需要额外处理，允许保留但要写注释说明。

### 4.3 事务

**现状：0 处 `@Transactional`（BE-14，P1 待治理）。**

新增代码规则：

| 规则 | 内容 |
|---|---|
| 多表写必须加事务 | 只写单表可不加 |
| 注解位置 | 加在 `public` 方法上；**禁止加在 `private`/同类内部调用**（Spring AOP 不生效） |
| 只读查询 | `@Transactional(readOnly = true)` 可提示优化器，但不强制 |
| **Redis 与 DB 不同事务** | 必须写注释说明顺序与补偿，禁止声称「强一致」 |

**Redis + DB 混合写的标准写法**（本项目 `TokenBudgetService` 的模式）：

```
① Redis 原子预扣（Lua 脚本，成功即占额度）
② 调模型
③ 幂等结算（Redis）
④ 幂等落库（DB，失败由 Reconciler 对账补偿）
```

必须显式声明：**最终一致，不是强一致**。任何声称强一致的注释都是错的。

### 4.4 配置

| 规则 | 内容 |
|---|---|
| 前缀绑定 | 用 `@ConfigurationProperties(prefix = "ai")` + `@Data` 类，禁止散落 `@Value` |
| 例外 | 单个非业务配置可用 `@Value`（本项目仅 2 处，均合规） |
| profile | `application.yml` 唯一入口；环境差异用环境变量 `${MYSQL_HOST:127.0.0.1}` |
| 密钥 | **禁止写入配置文件**，只用环境变量；`.env` 必须 gitignore |
| 默认值 | 必须给出可本地直接启动的默认值（克隆即跑） |

### 4.5 组件与扫描

| 规则 | 内容 |
|---|---|
| 启动类位置 | `com.example.milvusdemo.DemoApplication`（历史原因，**不要移动**） |
| Mapper 扫描 | `@MapperScan(basePackages = "com.example.ai", annotationClass = Mapper.class)` |
| **新增 Mapper 必须标 `@Mapper`** | 否则运行期才报 `Invalid bound statement` |
| 禁止 `@ComponentScan` 二次声明 | 会与启动类配置冲突 |

---

## 5. 异常处理

### 5.1 分类与使用

| 场景 | 用法 |
|---|---|
| 业务可预期失败（参数错、资源不存在、额度不足） | `throw BusinessException.invalid(...)` / `.notFound(...)` / `.budgetRejected(...)` |
| 不可恢复的技术故障 | 让 `RuntimeException` 冒泡，由 `GlobalExceptionHandler` 兜底 |
| 明确的外部调用失败 | 捕获后转 `BusinessException`，并保留原始异常作为 cause |

```java
// ✅ 唯一允许的业务异常构造方式（避免手写 new BusinessException(500, "xxx")）
throw BusinessException.notFound("预算策略不存在: " + id);

// ✅ 需要保留栈时传 cause
throw new BusinessException(ErrorCode.INTERNAL, "调用模型失败", e);
```

### 5.2 禁止项

| 禁止 | 原因 |
|---|---|
| `catch (Exception e) { }`（空 catch） | 吞异常，线上无法定位。本项目 **0 处**，保持 |
| `e.printStackTrace()` | 输出到 stdout，不进日志系统。本项目 **0 处** |
| `catch (Exception e) { throw new RuntimeException("error"); }` | 丢失原始栈与异常类型 |
| 用异常做流程控制 | 高频路径上异常构造开销大 |
| 在 `finally` 中 `return` 或 `throw` | 会吞掉 try 块的异常 |
| 把 `BusinessException` 记成 ERROR 并打全栈 | 业务失败是预期分支，打 WARN + message 即可 |

### 5.3 资源关闭

优先 try-with-resources，禁止依赖 `finalize`：

```java
try (InputStream in = new FileInputStream(file)) {
    ...
}
```

`permit` / `lock` 这类**必须成对释放**的资源，用 `try-finally` 包住整个使用范围：

```java
PermitHolder permit = new PermitHolder();
try {
    permit.set(requestGuard.acquire(userId, model));
    return doWork();
} finally {
    requestGuard.release(permit.get(), userId);   // 必须能在 permit 为 null 时安全执行
    TraceContext.clear();                          // ThreadLocal 必须清理
}
```

> **`ThreadLocal` 铁律**：`TraceContext.put()` 之后必须 `TraceContext.clear()`，
> 且 `clear` 放在 `finally` 里。线程池复用会泄漏上一个请求的 traceId。

---

## 6. 日志

### 6.1 声明

| 规则 | 内容 |
|---|---|
| 唯一方式 | `private static final Logger log = LoggerFactory.getLogger(Xxx.class);` |
| 禁止 | `@Slf4j`（本项目 **0 处**，保持一致，不要混用两种风格） |
| 现状 | 100 个类中 **12 个**有 Logger（BE-12，待补） |

**为什么不用 `@Slf4j`？** 这不是对错问题，而是「一致性」问题：项目已有 12 个类手工声明，
混入 Lombok 会让日志初始化方式出现两种，检索与重构都要考虑两种情况。
新代码沿用 `LoggerFactory`，若将来决定统一迁移到 `@Slf4j`，应一次性全改并登记 ADR。

### 6.2 级别

| 级别 | 使用场景 | 示例 |
|---|---|---|
| `ERROR` | 需要人工介入的技术故障 | 数据库连接失败、Lua 脚本执行异常 |
| `WARN` | 可自愈但需关注的异常 | 缓存未命中降级、预算预扣失败后拒绝请求 |
| `INFO` | 关键业务状态变更 | 预算结算、告警触发、保护等级提升 |
| `DEBUG` | 排障细节 | Prompt 组装结果、路由决策依据 |
| `TRACE` | 逐帧数据 | **生产禁用** |

### 6.3 写法

```java
// ✅ 占位符，参数延迟求值
log.info("预算结算完成 requestId={} cost={} remaining={}", requestId, cost, remaining);

// ❌ 字符串拼接：即使日志级别不输出也会构造字符串
log.info("预算结算完成 requestId=" + requestId + " cost=" + cost);

// ❌ 输出完整 Prompt / 用户输入（含 PII 与业务机密）
log.debug("prompt={}", fullPrompt);

// ✅ 只打长度或摘要
log.debug("promptLength={} hash={}", prompt.length(), prompt.hashCode());
```

### 6.4 敏感信息红线

| 禁止入日志 | 替代 |
|---|---|
| API Key / Token / 密码 | 只打长度或前 4 位：`keyPrefix=sk-1a2b len=51` |
| 完整用户输入 / Prompt | 打长度、hash |
| 用户手机号 / 邮箱 | 脱敏：`138****8888` |
| 数据库连接串（含密码） | 只打 host:port/db |

---

## 7. 常量与魔法值

| 规则 | 内容 |
|---|---|
| 业务语义值必须常量化 | `"BLOCK"` / `"DOWNGRADE"` / `"ALLOW"` → `AiEnums.OverrunPolicy` 或 `private static final` |
| 枚举优于字符串 | 但**入库时**用 `name()` 字符串，保持可读 |
| 阈值必须有名 | `0.8` → `ALERT_THRESHOLD_DEFAULT` |
| 同一常量不得在多处定义 | 放在最合适的域，其他域 import |
| 允许的内联字面量 | `0` / `1` / `-1` / `""` / `true`/`false` 在明显语境下 |

```java
// ✅ 常量带上来源说明，让人知道改它会影响什么
/** 超额处理策略默认值，与数据库列默认值保持一致。改这里必须同步 V4 迁移。 */
private static final String DEFAULT_OVERRUN_POLICY = "BLOCK";
```

---

## 8. 并发

| 规则 | 内容 |
|---|---|
| 禁止裸 `new Thread()` | 用 Spring 的 `TaskExecutor` 或 `@Async` |
| 共享可变状态必须加锁或用原子类 | |
| **分布式锁必须有过期时间** | 否则死锁 |
| Redis 原子操作用 Lua | 禁止「先 GET 再 SET」两步 |
| `ConcurrentHashMap` 不用 `null` 值 | 会抛 NPE |
| 定时任务加分布式互斥 | 多实例部署时会重复执行（`BudgetReconciler` 已用 Redis 锁） |

---

## 9. 拒绝清单（评审直接打回）

| 编号 | 拒绝项 | 检测 |
|---|---|---|
| R-01 | `@Autowired` 字段注入 | `grep -n "@Autowired" ` |
| R-02 | `System.out` / `System.err` / `printStackTrace` | `grep -rn "System\.out\|printStackTrace"` |
| R-03 | 空 `catch` 块 | Checkstyle `EmptyCatchBlock` |
| R-04 | 裸 `LocalDateTime.now()` / `new Date()` | `grep -rn "LocalDate(Time)\.now()"` |
| R-05 | `double` / `float` 参与金额运算 | 人工评审 + `grep "double.*[Cc]ost\|[Cc]ost.*double"` |
| R-06 | `SELECT *`（含 XML） | `grep -rniE 'select +\*'` |
| R-07 | Controller 直接 import Mapper | `grep -rn "\.mapper\." */controller/` |
| R-08 | Controller 返回 `Map<String, Object>` | 人工评审（存量 9 处已登记） |
| R-09 | 方法 > 50 行 | Checkstyle `MethodLength` |
| R-10 | 硬编码密钥 / 绝对路径 | `grep -rn "/Users/\|sk-\|password="` |
| R-11 | `TODO` / `FIXME` 冒充未完成功能 | `grep -rn "TODO\|FIXME"` |
| R-12 | 中文包名 / 拼音类名 | Checkstyle `PackageName` / 人工评审 |

---

## 10. 现状与目标差距（2026-10-26 基线）

由 `scripts/verify.sh` 在每次提交时核对：

| 指标 | 当前 | 目标 | 状态 |
|---|---:|---:|---|
| 主代码类数 | 100 | — | — |
| 测试数 | 97（73 单测 + 24 集成） | 持续增长 | ✅ |
| `@Autowired` 字段注入 | 0 | 0 | ✅ |
| `System.out` / `printStackTrace` | 0 | 0 | ✅ |
| `TODO` / `FIXME` | 0 | 0 | ✅ |
| 空 catch | 0 | 0 | ✅ |
| `SELECT *` | 0 | 0 | ✅ |
| 裸 `now()` | 0 | 0 | ✅ |
| Controller → Mapper 直连 | 0 | 0 | ✅ |
| 方法 > 50 行 | 2 | 0 | ⚠️ B6 |
| `@Transactional` | 0 | 关键写路径覆盖 | ⚠️ B6 |
| 有 Logger 的类 | 12 / 100 | 关键路径全覆盖 | ⚠️ B6 |
| Controller 返回 `Map` | 9 | 0 | ⚠️ B9 |
| 校验注解 | 7 处 / 2 文件 | 所有入参 DTO | ⚠️ B6 |

---

## 11. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。基线数据来自 100 个类、350 个方法的实测扫描；含 4 项已登记的规范偏离 |
