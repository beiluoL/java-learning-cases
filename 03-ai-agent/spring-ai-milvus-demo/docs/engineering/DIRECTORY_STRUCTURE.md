# 目录结构与分层职责规范

> 文档编号：ENG-DIR-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 适用范围：`spring-ai-milvus-demo` 全仓库（后端 / 前端 / 脚本 / 文档）  
> 强制力：**违反本规范的代码评审不通过，门禁不通过**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md)

---

## 0. 为什么需要这份文档

本项目在 2026-10 的代码审计中发现一个核心问题（`PROJECT_CODE_AUDIT.md` 中 BE-01，P0 级）：

> `com/example/ai/token/` 一个包内混居了 **8 个互不相干的业务域**，共 19 个文件；
> 其中 `token/cost/` 子包又同时承担「成本计算」「保护引擎」「预算预留」三种职责。

后果是：任何智能体（或人）想改「预算」逻辑，需要先弄明白 `token/cost/` 下 19 个文件各属于哪个域；
新增一个功能时无从判断该放哪里，于是随手塞进最近的包，包结构持续腐化。

**本规范的目的：把「一个文件该放在哪个目录」从主观判断变成可机械执行的规则。**

任何新增文件，必须能在第 7 节的决策树中走出唯一答案。走不出唯一答案，说明规则有缺口，
应提交规范修订，而不是「找个看起来差不多的地方放下」。

---

## 1. 顶层目录职责

仓库根目录只允许出现下列条目。新增根级目录必须走第 10 节的变更流程。

| 目录 / 文件 | 职责 | 允许增长 | 说明 |
|---|---|---|---|
| `src/main/java/` | 后端主代码 | ✅ | 唯一 Java 主源根 |
| `src/main/resources/` | 配置、SQL 迁移、Mapper XML、Lua 脚本 | ✅ | 见第 4 节 |
| `src/test/java/` | 后端测试代码 | ✅ | 唯一 Java 测试源根 |
| `frontend/` | Vue3 + TS 前端工程 | ✅ | 独立 `package.json`，见第 5 节 |
| `scripts/` | **工程门禁与自动化脚本** | ✅ | 新建目录，见第 6.1 节 |
| `samples/` | 端到端验证脚本 + 演示素材 | ✅ | 见第 6.2 节 |
| `docs/` | 全部文档 | ✅ | 见第 6.3 节 |
| `pom.xml` | Maven 构建描述 | 谨慎 | 依赖变更需评审 |
| `start/stop/restart.command` | macOS 双击启停 | ❌ 不新增 | 命名固定，见 6.4 |
| `start/stop/restart.bat` | Windows 双击启停 | ❌ 不新增 | 命名固定，见 6.4 |
| `README.md` | 项目入口说明 | ✅ | |
| `target/` | Maven 构建产物 | — | **禁提交**（`.gitignore` 覆盖） |

### 1.1 禁止出现的根级条目

| 禁止项 | 原因 | 正确位置 |
|---|---|---|
| `tmp/` `temp/` `bak/` `old/` `test123/` | 临时产物污染仓库 | 删除；需要实验性验证写进 `src/test/` |
| `*.log` `*.pid` `nohup.out` | 运行期产物 | `.run/`（已 gitignore） |
| 单文件散落的 `xxx.sql` | 无法确定执行顺序 | `src/main/resources/db/migration/V<n>__*.sql` |
| `JavaLearn/` `demo2/` 等并列工程 | 一个仓库一个工程 | 另开仓库 |
| `.env` `.env.local` （含真实密钥） | 密钥泄漏 | 环境变量 / `.env.example` 模板 |

---

## 2. 后端源码目录

### 2.1 源根与包根

```
src/main/java/
└── com/example/
    ├── ai/              ← 主业务上下文（99 个主代码文件中的 93 个）
    └── milvusdemo/      ← 遗留教学上下文（6 个文件，冻结，见 2.6）
```

**硬约束**

| 约束 | 内容 |
|---|---|
| 包根 | `com.example.ai`，新增业务代码一律在此之下 |
| 组件扫描 | `@SpringBootApplication(scanBasePackages = {"com.example.ai", "com.example.milvusdemo"})` |
| Mapper 扫描 | `@MapperScan(basePackages = "com.example.ai", annotationClass = Mapper.class)` |
| 启动类位置 | `com.example.milvusdemo.DemoApplication`（历史原因保留，**不要再移动**） |

> ⚠️ **Mapper 扫描的坑（已在 BE-18 修复）**
>
> 旧写法是硬列 7 个包名 `basePackages = {"com.example.ai.token.cost.mapper", ...}`。
> 一旦有人移动某个 `mapper` 包，编译和启动都正常，只在**运行期查询时**抛
> `Invalid bound statement (not found)`，极难定位。
>
> 现行写法用 `annotationClass = Mapper.class` 做全包扫描：新增业务域时
> **无需修改启动类**，只要 Mapper 接口带 `@Mapper` 注解就会被绑定。
>
> **新增 Mapper 接口必须标注 `@Mapper`**，否则不会被扫描到。

### 2.2 业务域总表（一等模块）

`com.example.ai` 下的一级子包 = 一个**业务域**。业务域之间是平级关系，不允许某个域作为另一个域的子包。

| 包 | 业务职责（一句话） | 文件数 | 对外 Controller |
|---|---|---:|---|
| `chat` | 对话主链路：请求编排、Prompt 组装、模型调用 | 4 | `AiChatController` |
| `token` | Token 用量采集与落库（**只管采集，不管成本**） | 6 | — |
| `cost` | 成本计算、成本账本、基础设施成本分摊、花费预测 | 9 | — |
| `budget` | 预算策略、预扣/结算/释放、对账 | 11 | `BudgetController` `BudgetAdminController` |
| `protection` | 成本保护引擎、请求级限流与拒绝、紧急停用 | 6 | `CostProtectionController` |
| `optimization` | 省钱手段：模型路由、Prompt 压缩、缓存、Token 估算 | 11 | `CostOptimizationController` |
| `pricing` | 模型价格表与价格版本管理 | 4 | `PricingController` |
| `alert` | 告警产生、阈值判断、异常检测、告警中心 | 6 | `CostAlertController` |
| `analytics` | 统计分析、日报聚合、治理看板 | 9 | `CostAnalyticsController` `CostGovernanceController` `TokenUsageAdminController` |
| `agent` | Agent 多步成本追踪 | 4 | `AgentCostController` |
| `provider` | 上游厂商用量适配器（OpenAI / Mock） | 3 | — |
| `common` | 跨域基础设施：统一响应、异常、枚举、金额精度、Trace | 9 | — |
| `config` | Spring 配置类、拦截器、过滤器 | 8 | — |
| `identity` | 当前用户身份提供者 | 2 | — |
| `metrics` | Micrometer 指标埋点 | 1 | — |

**域边界铁律**

1. **禁止跨域直接访问对方的 `entity` / `mapper`**。
   需要对方数据时，调用对方的 `service`，或由本域 Mapper 写自己的 SQL。
2. **禁止跨域继承**。域之间只有「组合 / 注入」关系，没有「父子」关系。
3. **`common` 不得反向依赖任何业务域**。
   > 现状例外（技术债，已知）：`common/Views.java` import 了
   > `cost.dto.CostBreakdown` 与 `token.entity.TokenUsage`。
   > 这是历史遗留的「集中 VO 容器」。修订方向见 3.4 节，
   > **新增代码不得扩大这个例外**。
4. 两个域都要用的逻辑 → 下沉到 `common`；只被一个域用 → 留在该域，**不要提前下沉**。

### 2.3 业务域内部的标准分层

一个业务域内部，只允许下列子包：

```
<domain>/
├── controller/     ← HTTP 入口。只做参数接收、校验、调 service、包装响应
├── dto/            ← 入参对象（Request/Query/Command）+ 出参对象（VO）
├── entity/         ← 数据库表映射实体（与表一一对应）
├── mapper/         ← MyBatis Mapper 接口（XML 在 resources/mapper/）
├── service/        ← 业务逻辑（class，不强制接口）
└── vo/             ← 出参视图对象（**新建域推荐**，见 3.3）
```

**不是每个域都需要全部 6 个包。** 只建实际有文件的包，禁止建空目录占位。

| 层 | 必须 | 禁止 |
|---|---|---|
| `controller` | 参数校验注解、调 service、返回 `ApiResponse<T>` | 写业务逻辑、直接调 Mapper、拼 SQL |
| `dto` | `record` 或带校验注解的 class、字段注释 | 塞业务方法 |
| `entity` | 与表字段一一对应、`@Data`、每个字段 `/** 中文注释 */` | 塞业务方法、跨表 JOIN 字段（除非明确是视图） |
| `mapper` | `@Mapper` 注解、方法名语义化 | 在接口里写 default 业务逻辑 |
| `service` | 单一职责、方法 ≤ 50 行 | 直接返回 `Map<String,Object>`（见 BE-02） |
| `vo` | 金额用 `String`、比率用 `double`(0~1) | 暴露 entity 全字段（除非确有必要） |

### 2.4 层的依赖方向

**只允许自上而下依赖，禁止逆向和跨层跳级。**

```
controller  →  service  →  mapper  →  (DB)
     │            │
     └────────────┴────→  dto / vo / entity / common
```

| 依赖 | 允许 | 说明 |
|---|:---:|---|
| `controller` → `service` | ✅ | 唯一正常入口 |
| `controller` → `mapper` | ❌ | 违反分层，必须经 service |
| `service` → `mapper` | ✅ | |
| `service` → `controller` | ❌ | 反向依赖 |
| `mapper` → `service` | ❌ | 反向依赖 |
| 任意层 → `common` | ✅ | `common` 是叶子 |
| `common` → 任意业务域 | ❌ | 见 2.2 铁律 3 |

自检命令（应为空输出）：

```bash
# controller 不得 import mapper
grep -rn "import com.example.ai.*.mapper" src/main/java/com/example/ai/*/controller/
```

### 2.5 全局共享包的特殊规则

这四个包不遵循 2.3 的标准分层，因为它们没有「域」的概念。

| 包 | 允许放什么 | 禁止放什么 |
|---|---|---|
| `common` | `ApiResponse` `PageResult` `BusinessException` `ErrorCode` `AiEnums` `MoneyScale` `TraceContext` `Views` `GlobalExceptionHandler` | 任何带 `@Service` 的业务逻辑、任何 `@RestController` |
| `config` | `@Configuration`、`@Bean`、`HandlerInterceptor`、`Filter`、`@ConfigurationProperties` | 业务判断逻辑（出现 `if (成本 > 预算)` 这类就说明放错了） |
| `identity` | 用户身份抽象与实现 | 权限判断（那是 `config/AdminAccessInterceptor`） |
| `metrics` | Micrometer `Counter`/`Timer` 封装 | 业务指标的计算口径（口径在各自域的 service 里） |

### 2.6 遗留上下文 `com.example.milvusdemo` —— 冻结区

| 项 | 规定 |
|---|---|
| 文件 | `DemoApplication` `embed/` `service/` `web/` 共 6 个文件 |
| 状态 | **冻结（frozen）**：不重构、不迁移、不补注释、不套用本规范的全部规则 |
| 允许的改动 | 仅限「不改就无法编译/启动」的适配性修改 |
| 原因 | 这是 Milvus + Spring AI 的教学示例，与 AI 成本治理主业务无耦合；重构它只有风险没有收益 |
| 新增代码 | **禁止**放进 `com.example.milvusdemo` |

> 启动类 `DemoApplication` 留在该包是刻意的：移动它会改变 Spring Boot 的默认扫描根，
> 属于高风险低收益操作，已列入 `CODE_STANDARDS.md` 第 4 节「看似冗余实则有因」清单。

---

## 3. 分层命名的落位规则

### 3.1 按类名后缀决定落位

新增一个 Java 类时，先看类名后缀，再查此表。**这是最常用的判断方式。**

| 类名后缀 | 落位 | 示例 |
|---|---|---|
| `*Controller` | `<domain>/controller/` | `BudgetController` |
| `*Request` `*Query` `*Command` `*Form` | `<domain>/dto/` | `UsageQuery` |
| `*VO` `*View` `*Item` `*Status` `*Detail` | `<domain>/dto/` 或 `<domain>/vo/` | `ChatDtos.ChatResponseVO` |
| `*Service` | `<domain>/service/` | `TokenBudgetService` |
| `*Engine` `*Router` `*Optimizer` `*Estimator` `*Calculator` `*Compressor` `*Tracker` `*Reconciler` `*Collector` `*Interceptor` `*Filter` | `<domain>/` 根（无状态工具/组件） | `CostProtectionEngine` |
| `*Adapter` | `<domain>/adapter/` | `provider/adapter/OpenAiUsageAdapter` |
| `*Mapper` | `<domain>/mapper/` | `BudgetPolicyMapper` |
| 无后缀、与表同名 | `<domain>/entity/` | `BudgetPolicy` |
| `*Properties` `*Config` | `config/` | `AiProperties` |
| `*Event` | `<domain>/` 根（发布方所在域） | `budget/BudgetSettledEvent` |
| `Ai*` 前缀 | 保持 `Ai` 前缀（不用 `AI`） | `AiChatService` `AiMetrics` |

> **`*Engine` / `*Router` 这类为什么放域根而不放 `service/`？**
> 因为它们是无状态的纯计算组件，不注入 Mapper、不持事务。
> 把它们和「有持久化职责的 Service」混在一个包，会让人误以为可以在这里写 SQL。
> 域根位置 = 「本域的基础设施组件」，`service/` = 「本域的业务编排」。

### 3.2 内部类 vs 独立文件

| 场景 | 做法 |
|---|---|
| 一个 DTO 里多个强关联的 record（请求 + 响应） | 合并到一个 `XxxDtos` 文件，用嵌套 record `ChatDtos.ChatRequest` / `ChatDtos.ChatResponseVO` |
| 跨域共享的统计 VO（≥2 个域使用） | 放 `common/Views.java`（**存量，不新增**，见 3.4） |
| Service 私有辅助类型（只在本次调用链用） | 写成 Service 内部的 `private record`，如 `AiChatService.RequestScope` |
| 对外暴露的返回类型（Controller 返回值） | 必须是可寻址的类型，**不得是 Service 内部 record**（见 3.5） |

### 3.3 `dto` 与 `vo` 的区分

| | `dto/` | `vo/` |
|---|---|---|
| 方向 | **入参**为主（Request/Query） | **出参**为主 |
| 校验注解 | 有（`@NotNull` `@Size`） | 无 |
| 可变性 | class（Spring 绑定需要 setter）或 record | record（不可变） |
| 现状 | 各域普遍已有 | 多数域尚未建，出参暂用 `common/Views` 或 entity |

**规定**：新建业务域时，出参对象放 `<domain>/vo/`；
已有域继续用 `common/Views` 或 `dto/` 内的 VO 不算违规（存量容忍），
但**新增出参优先放本域 `vo/`**，不再往 `common/Views` 里加。

### 3.4 `common/Views.java` 的收敛路径

现状：`Views` 聚合了 12 个 record（`Overview` `TrendPoint` `ModelCost` `SceneCost` `TopItem`
`BudgetStatus` `UsageDetail` `AlertItem` `BreakdownItem` `UserCost` `CostBreakdownView`）
+ 3 个静态转换方法。它同时被 `analytics` / `budget` / `alert` / `cost` 四个域使用，
并反向 import 了 `cost.dto.CostBreakdown` 和 `token.entity.TokenUsage`。

**处置**：标记为**冻结扩容**。

- ✅ 允许：修改现有 record 的字段（如加注释、加字段）
- ✅ 允许：保留 3 个静态方法 `decimal` / `longOf` / `money`
- ❌ 禁止：新增 record 到 `Views`
- ❌ 禁止：新增对业务域的 import

理由：`Views` 是 4 个域共用的「统计口径契约」，一次性拆散会波及全部 Controller 与前端类型定义，
风险远大于收益。用「冻结扩容」把债务锁在原地，让新代码走正确的路。

### 3.5 已知待治理项（本规范登记，不在本批修复）

| 编号 | 问题 | 现状 | 目标 |
|---|---|---|---|
| BE-02 | 9 个接口返回 `ApiResponse<Map<String, Object>>` | `CostGovernanceController` / `CostOptimizationController` / `CostProtectionController` 等 | 改为显式 VO record |
| BE-02b | `CostOptimizationService.StrategyInfo` `ExecuteResult`、`InfrastructureCostService.AllocationResult` 定义在 Service 内部并作为 Controller 返回类型 | 类型不可独立寻址，前端无法生成类型 | 提到 `<domain>/vo/` |

**新增接口一律不得返回 `Map<String, Object>`。** 存量按 `PROJECT_CODE_AUDIT.md` 批次治理。

---

## 4. `src/main/resources` 目录

```
src/main/resources/
├── application.yml              ← 唯一配置入口（含 profile 分段）
├── db/migration/                ← Flyway 迁移脚本，只增不改
│   ├── V1__init_ai_cost.sql
│   ├── V2__seed_demo_data.sql
│   ├── V3__add_provider_alias_demo_pricing.sql
│   ├── V4__governance_schema.sql
│   └── V5__governance_seed.sql
├── lua/                         ← Redis 原子脚本（4 个：reserve/settle/release/cleanup）
└── mapper/                      ← MyBatis XML（13 个，与 mapper 接口一一对应）
```

| 目录 | 规则 |
|---|---|
| `db/migration/` | 文件名 `V<序号>__<snake_case_描述>.sql`。**已执行的迁移文件禁止修改**，只能新增 `V6__`、`V7__`。命名 `V1~V5` 为存量，新文件按序递增 |
| `lua/` | 一个脚本一个文件，文件名 = 用途（`budget_reserve.lua`）。脚本必须原子、幂等、带 `KEYS`/`ARGV` 显式取参 |
| `mapper/` | 文件名 = `<实体名>Mapper.xml`，`namespace` 必须与接口全限定名一致。SQL 禁止 `SELECT *`（见 DB-03） |
| `application.yml` | 只有一个。拆分 profile 用 `application-<profile>.yml`，**不再新增**（见 BE-16，待治理） |

**禁止**：把 `.sql` `.lua` `.xml` 放在 `src/main/java/` 下；把测试数据放 `src/main/resources`。

---

## 5. 前端目录 `frontend/`

```
frontend/
├── src/
│   ├── api/            ← HTTP 层：一个后端域一个文件
│   ├── components/     ← 可复用展示组件（无业务语义）
│   ├── composables/    ← 组合式函数（可复用逻辑）
│   ├── router/         ← 路由表
│   ├── stores/         ← Pinia 状态
│   ├── styles/         ← 全局样式与设计令牌
│   ├── utils/          ← 纯函数工具
│   ├── views/          ← 页面级组件（与路由一一对应）
│   ├── App.vue
│   ├── main.ts
│   └── constants.ts
├── eslint.config.js
├── vite.config.ts
├── tsconfig.json
├── env.d.ts
├── index.html
└── package.json
```

### 5.1 各目录职责

| 目录 | 放什么 | 禁止 |
|---|---|---|
| `api/` | 每个文件封装一个后端域的接口调用；`request.ts` 是 axios 实例与拦截器 | 业务计算、组件状态 |
| `components/` | 纯展示、靠 props 驱动的组件（`StatCard` `DataTable` `Modal` `ProgressBar`） | 直接调 `api/`、直接读 `stores/` |
| `composables/` | `useXxx` 函数，封装有状态逻辑（`useAsyncData`） | 返回 JSX/模板 |
| `router/` | 路由定义、懒加载 | 权限校验的具体实现（放 `api/` 拦截器或 `composables`） |
| `stores/` | Pinia store，跨页面共享状态 | 组件局部状态（用 `ref`/`reactive`） |
| `styles/` | `tokens.css`（CSS 变量）+ `global.css` | 组件私有样式（写 `<style scoped>`） |
| `utils/` | 无副作用的纯函数（`format.ts` `time.ts` `chart.ts` `table.ts`） | 任何 `import` 组件或 store |
| `views/` | 与路由一一对应的页面组件 | 被其他组件 import（复用请上提到 `components/`） |

### 5.2 前端分层依赖方向

```
views  →  components  →  (props / emit)
  │           │
  ├──────→  composables  →  api  →  request.ts
  ├──────→  stores
  └──────→  utils
```

| 禁止的依赖 | 原因 |
|---|---|
| `views/*` → `axios` 直连 | 绕过 `request.ts` 的统一错误处理与 baseURL |
| `components/*` → `api/*` | 组件不再纯展示，无法复用与单测 |
| `utils/*` → `stores/*` | 纯函数被状态污染 |
| `api/*` → `views/*` | 反向依赖 |

自检命令（应为空输出）：

```bash
# 视图层不得直连 axios
grep -rn "from 'axios'" frontend/src/views/ frontend/src/components/
```

### 5.3 文件规模上限

| 文件类型 | 软上限 | 现状超限项 |
|---|---:|---|
| `views/*.vue` | 400 行 | `CostProtection.vue` 412 行（BE-06 待拆） |
| `components/*.vue` | 300 行 | — |
| `api/types.ts` | 400 行 | 445 行（FE-01 待拆） |
| `api/*.ts`（单域） | 300 行 | — |

新建文件超过软上限，必须在评审时说明理由或拆分。

---

## 6. 脚本与文档目录

### 6.1 `scripts/` —— 工程门禁（本批新建）

```
scripts/
├── verify.sh            ← 统一门禁入口，CI 与本地共用
├── check-backend.sh     ← Maven 编译 + 测试 + Checkstyle + Spotless
├── check-frontend.sh    ← ESLint + Prettier + Stylelint + vue-tsc + Vitest
└── check-scripts.sh     ← ShellCheck + bash -n
```

| 规则 | 内容 |
|---|---|
| 入口唯一 | 所有人（含智能体）执行质量检查**只允许**跑 `scripts/verify.sh` |
| 退出码 | 0 = 通过，非 0 = 失败。任一子检查失败即整体失败 |
| 幂等 | 可重复执行，不产生副作用、不改源码 |
| 无硬编码路径 | 用 `$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)` 定位仓库根 |

### 6.2 `samples/` —— 端到端验证与演示素材

| 文件 | 类型 | 说明 |
|---|---|---|
| `verify-admin-endpoints.sh` | 验证脚本 | 15 个管理接口连通性 |
| `verify-alert-levels.sh` | 验证脚本 | 告警分级触发（改额度→恢复） |
| `verify-alert-recovery.sh` | 验证脚本 | 告警恢复 + 兜底 resume |
| `milvus-guide.html` `rag-notes.md` `spring-boot-intro.txt` | 演示素材 | RAG 演示用的知识库源文件 |

**`samples/` 与 `scripts/` 的区别**

| | `scripts/` | `samples/` |
|---|---|---|
| 目的 | 判断「代码合不合格」 | 判断「系统跑起来对不对」 |
| 是否需要服务在跑 | 否（前端/静态检查）/ 部分是（测试） | **是**（需 MySQL + Redis + 后端） |
| 是否进 CI | 是 | 否（人工/按需） |
| 失败含义 | 代码质量不达标 | 环境或功能异常 |

**验证脚本的强制要求**（见 `SCRIPT_CODING_STANDARDS.md`）：
必须带 `trap` 兜底恢复被改动的系统状态，否则中途失败会把环境留在脏状态。

### 6.3 `docs/`

```
docs/
├── engineering/         ← 工程规范（本目录）
│   ├── CODE_STANDARDS.md              （总入口）
│   ├── DIRECTORY_STRUCTURE.md         （本文档）
│   ├── PROJECT_CODE_AUDIT.md          （审计结论）
│   └── ...（其余规范文档）
├── architecture/        ← 架构设计与 ADR
│   ├── AI_COST_ARCHITECTURE.md
│   ├── BUDGET_CONTROL_DESIGN.md
│   ├── TOKEN_USAGE_DESIGN.md
│   └── ADR/
├── api/                 ← 接口契约
├── database/            ← 表结构设计、数据字典
├── deployment/          ← 部署说明
└── acceptance/          ← 验收报告、测试报告、性能报告
```

| 目录 | 放什么 | 不放什么 |
|---|---|---|
| `engineering/` | 「怎么写代码」的规范、审计、门禁说明 | 架构决策、接口文档 |
| `architecture/` | 「为什么这样设计」+ ADR | 操作步骤 |
| `api/` | 请求/响应示例、错误码 | 实现细节 |
| `database/` | 表字典、ER 说明 | 迁移脚本（在 resources 里） |
| `deployment/` | 本地启动、环境变量清单 | 代码规范 |
| `acceptance/` | 可复现的验收证据（命令 + 真实输出） | 计划、TODO |

**文档新增规则**：一份文档只回答一类问题。写不清归属就说明还没想清楚，先别建文件。

### 6.4 一键启停脚本（根目录）

固定 6 个文件，**命名不可变**（双击可用性依赖扩展名）：

| 平台 | 启动 | 停止 | 重启 |
|---|---|---|---|
| macOS | `start.command` | `stop.command` | `restart.command` |
| Windows | `start.bat` | `stop.bat` | `restart.bat` |

详细规范见 `SCRIPT_CODING_STANDARDS.md`，核心要求：
`set -Eeuo pipefail` / 无硬编码绝对路径 / PID 文件精确关停 / 禁止 `pkill -f` / 幂等。

---

## 7. 新增文件落位决策树

**任何新增文件，按此树从上往下走，第一个匹配即答案。**

```
新增一个文件
│
├─ 是文档？
│   ├─ 讲「怎么写代码」          → docs/engineering/
│   ├─ 讲「为什么这么设计」       → docs/architecture/（重大决策另建 ADR）
│   ├─ 讲「接口怎么调」          → docs/api/
│   ├─ 讲「表怎么设计」          → docs/database/
│   ├─ 讲「怎么部署启动」        → docs/deployment/
│   └─ 讲「验证结果/验收证据」    → docs/acceptance/
│
├─ 是脚本？
│   ├─ 判断代码质量（进 CI）      → scripts/
│   ├─ 验证运行中系统的功能       → samples/
│   └─ 一键启停                  → 根目录 start/stop/restart.{command,bat}
│
├─ 是前端文件？→ 见 frontend/src
│   ├─ 页面（有路由）            → views/
│   ├─ 复用 UI 组件              → components/
│   ├─ 复用逻辑（useXxx）        → composables/
│   ├─ 接口调用                  → api/<域>.ts
│   ├─ 纯函数                    → utils/
│   └─ 跨页状态                  → stores/
│
├─ 是 SQL / Lua / Mapper XML？
│   ├─ 建表改表                  → resources/db/migration/V<n>__*.sql
│   ├─ Redis 原子操作            → resources/lua/*.lua
│   └─ MyBatis SQL               → resources/mapper/<实体>Mapper.xml
│
└─ 是 Java？→ 先定「属于哪个业务域」
    │
    ├─ 属于某个业务域 → 按类名后缀查 3.1 表 → <domain>/<layer>/
    │
    └─ 跨域基础设施
        ├─ 统一响应/异常/枚举/常量  → common/
        ├─ Spring 配置/拦截器/过滤器 → config/
        ├─ 用户身份                  → identity/
        └─ 指标埋点                  → metrics/
```

**决策树走不通时**：不要自己发明新目录。停下来，在评审中提出规范缺口，走第 10 节流程。

---

## 8. 常见误放与纠正

| 误放 | 为什么错 | 正确位置 |
|---|---|---|
| `TokenBudgetService` 放 `token/` | 预算是独立业务域，不是 Token 采集的一部分 | `budget/`（已迁移） |
| `CostProtectionEngine` 放 `token/cost/` | 保护引擎与被保护的成本计算是两回事 | `protection/`（已迁移） |
| `AnomalyDetectionService` 放 `token/cost/` | 异常检测属于告警域 | `alert/`（已迁移） |
| `ModelRouter` 放 `provider/` | 路由策略是「省钱手段」，不是「厂商适配」 | `optimization/`（已迁移） |
| 新建 `utils/` 装业务工具类 | `utils` 是无归属的垃圾桶，会无限膨胀 | 归入所属业务域，或 `common/` |
| 新建 `impl/` 只为放 Service 实现 | 本项目 18 个 Service 均为 class，无接口 | 不建接口就不建 impl（见 8.1） |
| 新建 `exception/` 放自定义异常 | 已统一在 `common/BusinessException` + `ErrorCode` | `common/` |
| 新建 `constant/` 放常量 | 已用 `common/AiEnums` + 各域 `constants.ts`（前端） | 就近放置 |

### 8.1 关于 `service.impl`

任务规范提到标准分层包含 `service/` 与 `service/impl/`。本项目的实际决定：

> **不为分层而建空接口。**

| 判断 | 依据 |
|---|---|
| 现状 | 18 个 Service 全部是**无接口的 class** |
| 是否违规 | 否。符合「不为分层建空接口」原则 |
| 何时才拆接口 | 满足任一条：① 需要 ≥2 个实现（如多厂商适配）② 需要跨模块 SPI ③ 需要 JDK 动态代理增强 |
| 拆的时候怎么放 | `<domain>/service/<XxxService>.java`（接口）+ `<domain>/service/impl/<XxxServiceImpl>.java`（实现） |

`provider/adapter/` 下的 `ProviderUsageAdapter` 就是「有 2 个实现（OpenAI / Mock）才建接口」的正确示例。

---

## 9. 目录级禁止项

| 编号 | 禁止 | 检测方式 |
|---|---|---|
| D-01 | 建空目录占位（无文件的目录） | `find src -type d -empty` |
| D-02 | 包名与目录路径不一致 | 见下方脚本 |
| D-03 | 同一业务域散落在两个一级包（如 `chat/` 与 `chat2/`） | 人工评审 |
| D-04 | `controller` 直接 import 任意 `mapper` | grep（见 2.4） |
| D-05 | `resources` 下的文件按业务域再分子目录 | 人工评审 |
| D-06 | 根目录新增第 7 个启停脚本（如 `start.sh`） | `ls *.command *.bat` |
| D-07 | 提交 `target/` `node_modules/` `dist/` `.run/` | `.gitignore` + `git status` |
| D-08 | `SELECT *`（含 XML） | `grep -rniE 'select +\*' src/main/resources/mapper/` |

**D-02 校验脚本**（可加入 `scripts/verify.sh`）：

```bash
# 包声明必须与目录路径一致
mismatch=0
while IFS= read -r f; do
  expected=$(dirname "${f#src/main/java/}" | tr '/' '.')
  actual=$(sed -n 's/^package \(.*\);$/\1/p' "$f" | head -1)
  if [ "$expected" != "$actual" ]; then
    echo "MISMATCH: $f (声明 $actual, 期望 $expected)"; mismatch=1
  fi
done < <(find src/main/java -name '*.java')
exit $mismatch
```

---

## 10. 目录变更流程

移动/重命名包属于**跨模块变更**，按 `CODE_STANDARDS.md` 第 5 节走最严门禁。

| 步骤 | 动作 | 命令要点 |
|---|---|---|
| 1 | 检查工作区干净 | `git status --porcelain` 必须为空，否则先提交或 stash |
| 2 | 用 `git mv` 而非 `mv` | 保留文件历史（本次 69 文件重组即用 `git mv`） |
| 3 | 批量改 `package` / `import` | 用脚本，**按真实源根分别计算** `src/main/java` 与 `src/test/java` |
| 4 | 编译 | `mvn -B -o clean compile` |
| 5 | 全量测试 | `mvn -B -o -Pintegration verify` |
| 6 | **启动并调真实接口** | 编译通过 ≠ 装配成功。Mapper 绑定错误只在运行期暴露 |
| 7 | 确认无残留空目录 | `find src -type d -empty -delete` |
| 8 | 更新本文档第 2.2 节与 `PROJECT_CODE_AUDIT.md` | 文档与代码不一致 = 违规 |

> **第 3 步的血泪教训（BE-01 重组时实际发生）**
>
> 迁移脚本对 `src/test/java` 下的文件也套用了 `src/main/java` 的相对路径算法，
> 结果 test 文件的包声明被写成 `package ......test.java.com.example.ai.token.cost;`，
> 13 个文件全部编译失败。
>
> 正确做法：分别以 `src/main/java` 和 `src/test/java` 为基准计算相对路径。

> **第 6 步的血泪教训**
>
> 包拆分后，原本同包无需 import 的类互相找不到了（`SpendForecastService` 找不到
> `CostAlertService`）。**编译能过，运行期才报错**——因为 Spring 是按类型注入的，
> 而 `import` 缺失在编译期就报错了……实际现象是编译期报「找不到符号」，
> 修复方式是补 `import com.example.ai.alert.CostAlertService;`（含测试类）。

---

## 11. 当前结构快照（可复现）

以下数字为 2026-10-26 实测，用于核对文档与代码是否漂移。

```bash
# 主代码文件数
find src/main/java -name '*.java' | wc -l          # 99
# 测试文件数
find src/test/java -name '*.java' | wc -l          # 14
# 业务域数（com/example/ai 直接子目录）
find src/main/java/com/example/ai -maxdepth 1 -type d | wc -l   # 16（含 ai 自身）
# Mapper XML 数
ls src/main/resources/mapper/*.xml | wc -l         # 13
# 迁移脚本数
ls src/main/resources/db/migration/*.sql | wc -l   # 5
# 前端 views 数
ls frontend/src/views/*.vue | wc -l                # 10
# 前端 components 数
ls frontend/src/components/*.vue | wc -l           # 13
```

| 域 | 文件数 | 子包 |
|---|---:|---|
| `agent` | 4 | `entity` `mapper` |
| `alert` | 6 | `controller` `entity` `mapper` |
| `analytics` | 9 | `controller` `entity` `mapper` `service` |
| `budget` | 11 | `controller` `entity` `mapper` |
| `chat` | 4 | `controller` `dto` `model` `service` |
| `common` | 9 | — |
| `config` | 8 | — |
| `cost` | 9 | `dto` `entity` `mapper` |
| `identity` | 2 | — |
| `metrics` | 1 | — |
| `optimization` | 11 | `entity` `mapper` |
| `pricing` | 4 | `controller` `entity` `mapper` `service` |
| `protection` | 6 | `controller` `entity` `mapper` |
| `provider` | 3 | `adapter` |
| `token` | 6 | `dto` `entity` `mapper` |

**注意 `chat/model/` 与 `provider/adapter/` 不在 2.3 标准分层内**，属允许的专用子包：

| 专用子包 | 用途 | 约束 |
|---|---|---|
| `chat/model/` | Spring AI `ChatModel` 实现（`MockChatModel`） | 只实现框架接口，不含业务逻辑 |
| `*/adapter/` | 外部系统适配器 | 一个外部系统一个 adapter，实现同一接口 |

---

## 12. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。依据 BE-01 包重组的实际结构编写，含决策树与 8 项目录级禁止项 |
