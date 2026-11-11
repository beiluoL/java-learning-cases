# 前端编码规范（Vue 3 + TypeScript）

> 文档编号：ENG-FE-001  
> 版本：1.0  
> 生效日期：2026-10-26  
> 技术栈（锁版，见 `CODE_STANDARDS.md`）：Vue 3.5.13 / TypeScript 5.7.3 / Vite 6.0.7 / Pinia 2.3.1 / vue-router 4.5.0 / ECharts 5.6.0 / axios 1.7.9  
> 目录：`frontend/` · 开发端口 `5174`（`strictPort`）· 后端代理 `/api` → `http://127.0.0.1:8091`  
> 强制力：**违反「必须」条款的代码评审不通过；`npm run lint` + `npm run type-check` 必须双双通过**  
> 上位文档：[`CODE_STANDARDS.md`](./CODE_STANDARDS.md) · 相关：[`DIRECTORY_STRUCTURE.md`](./DIRECTORY_STRUCTURE.md) §5

---

## 0. 本项目的三条前端铁律

| 铁律 | 含义 | 现状 |
|---|---|---|
| **样式只能引用设计令牌** | 组件内不写 `#4f46e5`，写 `var(--color-primary)` | ⚠️ 10 个视图仍有硬编码色值（FE-05） |
| **请求只能走 `api/request.ts`** | 视图/组件禁止 `import axios` | ✅ 0 处违规 |
| **类型不留 `any`** | 后端返回必须有显式 interface | ✅ 0 处 `any` |

---

## 1. 命名

### 1.1 文件与目录

| 类型 | 规则 | 示例 |
|---|---|---|
| 视图（页面） | `UpperCamelCase.vue`，与路由 `name` 一致 | `CostProtection.vue` |
| 组件 | `UpperCamelCase.vue`，名词或名词短语 | `StatCard.vue` `TimeRangePicker.vue` |
| 组合式函数 | `use` + `UpperCamelCase`.ts | `useAsyncData.ts` |
| API 模块 | 小写域名 | `cost.ts` `budget.ts` `governance.ts` |
| 工具 | 小写用途名 | `format.ts` `time.ts` `chart.ts` `table.ts` |
| Store | 小写域名词 | `timeRange.ts` |
| 样式 | 小写，`kebab-case` 用短横 | `global.css` `tokens.css` |
| 禁止 | `index.vue` 作为视图、`test.vue`、`new1.vue`、大写 API 文件名 | |

### 1.2 代码标识符

| 元素 | 规则 | 示例 |
|---|---|---|
| 变量 / 函数 | `camelCase` | `loading` `fetchOverview` |
| 常量 | `UPPER_SNAKE_CASE` | `MAX_PAGE_SIZE` `DEFAULT_RANGE` |
| 类型 / 接口 | `UpperCamelCase`，**不加 `I` 前缀** | `TokenUsage` `CostOverview`（不是 `ITokenUsage`） |
| 枚举 | `UpperCamelCase` 类型 + `UPPER_SNAKE` 成员 | `type Range = '24h' \| '7d'` |
| Props | `camelCase` 定义，模板用 `kebab-case` | 定义 `minHeight`，模板 `:min-height` |
| Emits | `kebab-case` 事件名 | `@retry` `@update:model-value` |
| CSS 类 | `kebab-case`，BEM 风格的 `block__element--modifier` | `.stat-card__value--danger` |
| 布尔变量 | `is` / `has` / `can` 前缀 | `isEmpty` `canRetry` |

---

## 2. 组件规范

### 2.1 一律 `<script setup lang="ts">`

```vue
<script setup lang="ts">
import { computed } from 'vue'

// ✅ 类型化 props + withDefaults（本项目 13 个组件全部如此）
const props = withDefaults(defineProps<{
  title: string
  value: string | number
  trend?: 'up' | 'down' | 'flat'
  loading?: boolean
}>(), {
  trend: 'flat',
  loading: false,
})

// ✅ 类型化 emits
const emit = defineEmits<{ retry: [] }>()

const trendClass = computed(() => `stat-card__trend--${props.trend}`)
</script>
```

| 规则 | 内容 |
|---|---|
| 禁止 | Options API（`export default { data() {} }`） |
| 禁止 | `defineComponent` 包裹 |
| 禁止 | 未类型化的 `defineProps(['a', 'b'])` 数组写法 |
| 必须 | `withDefaults` 给出全部可选 prop 的默认值 |
| 必须 | props 只读；需要派生值用 `computed`，**不要直接改 props** |
| 必须 | 组件名多单词（`vue/multi-word-component-names` 本项目已关闭，但仍应遵守） |

### 2.2 Props 设计

| 规则 | 内容 |
|---|---|
| 单向数据流 | 子组件不得修改 props；要改就 `emit` 出去让父组件改 |
| 默认值必须合理 | 未传也能正常渲染，不出现 `undefined` 显示 |
| 避免 boolean 反向语义 | `disabled` 而不是 `enabled` 取反 |
| 复杂对象用接口 | `interface ChartSeries { name: string; data: number[] }` |
| **无 props 是合法设计** | `TimeRangePicker.vue` 直接读 store，属正确设计，不是缺陷 |

### 2.3 组件职责边界

| 目录 | 允许 import | 禁止 import |
|---|---|---|
| `views/*` | `components` `composables` `api` `stores` `utils` | — |
| `components/*` | `utils`、其他 `components` | `api`、`stores`、`views` |
| `composables/*` | `api` `utils` `stores` | `components` `views` |
| `utils/*` | 仅标准库与其他 `utils` | 任何 `api`/`stores`/`components` |
| `api/*` | `request.ts`、`types.ts`、`utils` | `views` `components` `stores` |

```bash
# 自检：组件不得直连 API（必须 0 行）
grep -rn "from '@/api\|from '../api" frontend/src/components/
```

### 2.4 模板规范

| 规则 | 内容 |
|---|---|
| `v-for` 必须有稳定 `:key` | 用业务 id，**禁止用数组索引**（列表会重排时渲染错乱） |
| `v-if` 与 `v-for` 不同元素 | Vue 3 中 `v-if` 优先级高于 `v-for`，同元素会拿不到循环变量 |
| 复杂表达式抽 `computed` | 模板里不写超过 2 个运算符的表达式 |
| 事件处理避免内联多语句 | 抽成方法 |
| 组件标签用 `kebab-case` | `<stat-card />`（Vue 3 两种都支持，项目内保持一致） |

---

## 3. TypeScript

### 3.1 编译器配置（`tsconfig.json`，已开启）

```jsonc
{
  "strict": true,                      // ✅ 已开
  "noFallthroughCasesInSwitch": true,  // ✅ 已开
  "isolatedModules": true,             // ✅ 已开（Vite 要求）
  "noEmit": true                       // ✅ 类型检查由 vue-tsc 负责
}
```

**待补（B8 批次）**：

| 选项 | 作用 | 补之前需先修的历史问题 |
|---|---|---|
| `noUnusedLocals` | 禁止未使用变量 | 可能有若干未用 import |
| `noUnusedParameters` | 禁止未使用参数 | 同上 |
| `noUncheckedIndexedAccess` | 索引访问返回 `T \| undefined` | 数组访问处需补判空，改动面较大 |

### 3.2 类型规则

| 规则 | 内容 |
|---|---|
| **禁止 `any`** | 用 `unknown` + 类型守卫，或定义真实接口。本项目 **0 处** `any`，保持 |
| 禁止 `@ts-ignore` | 需要抑制时用 `@ts-expect-error` 并写明原因 |
| 后端返回必须有 interface | 放在 `api/types.ts`，字段名与后端 JSON 完全一致 |
| 金额一律 `string` | 后端返回字符串，前端**不得 `Number()` 转换后参与算账**（会丢精度） |
| 可选字段显式标 `?` | 与后端 `null` 语义对应时用 `\| null` |
| 联合类型优于 string | `type Range = '24h' \| '7d' \| '30d'` |

```ts
// ✅ 金额保持字符串，只做展示格式化
interface CostOverview {
  totalCost: string        // 后端 BigDecimal.toPlainString()
  requestCount: number
  successRate: number      // 0~1
  budgetUsageRate: number  // 0~1
}

// ❌ 转成 number 再相加 → 19 位小数会被截断
const sum = list.reduce((s, x) => s + Number(x.totalCost), 0)
```

### 3.3 类型文件的组织

现状：`api/types.ts` **445 行 / 41 个 interface**（FE-01，P2）。

| 规则 | 内容 |
|---|---|
| 单文件软上限 | 400 行 |
| 超限处置 | 按后端域拆分 `api/types/<domain>.ts`，`types.ts` 改为 re-export |
| 新增类型 | 优先放到已有域文件；`types.ts` 超过 400 行后**不再直接追加** |
| 命名冲突 | 用域前缀区分（`CostAlert` vs `BudgetAlert`），不要用 `Alert1` |

---

## 4. 请求层（`api/`）

### 4.1 结构

```
api/
├── request.ts    ← axios 实例、拦截器、统一错误、traceId 透出
├── types.ts      ← 后端契约类型（待按域拆分）
├── usage.ts      ← /api/admin/ai/usage
├── cost.ts       ← /api/admin/ai/cost
├── budget.ts     ← /api/admin/ai/budgets + /api/admin/budget
├── pricing.ts    ← /api/admin/ai/pricing
└── governance.ts ← 治理看板聚合
```

### 4.2 规则

| 规则 | 内容 |
|---|---|
| 唯一 axios 实例 | 只在 `request.ts` 创建；**视图/组件零 `import axios`** |
| 统一响应解包 | 拦截器判断 `code === 0` 返回 `data`，否则抛错 |
| 错误必须带 traceId | 后端返回 `traceId`，前端展示出来便于排障 |
| baseURL | 用相对路径 `/api`，由 Vite 代理转发（**不得写死后端 host**） |
| 超时 | 在 `request.ts` 统一设，不在各接口单独设 |
| 取消 | 组件卸载/参数变化时用 `AbortController` 取消在途请求 |

```ts
// ✅ 一个域一个文件，函数名与后端语义对应
export function fetchCostOverview(params: TimeRangeParams) {
  return request.get<CostOverview>('/admin/ai/cost/overview', { params })
}

// ❌ 视图里直接写 HTTP 细节
const res = await axios.get('http://127.0.0.1:8091/api/admin/ai/cost/overview')
```

### 4.3 为什么禁止写死后端地址

Vite 代理已把 `/api` 转发到 `127.0.0.1:8091`（配置在 `vite.config.ts`）。
写死地址会导致：① 生产环境跨域 ② 换端口要改多处 ③ 无法用 mock。

> **`vite.config.ts` 中的端口注释必须保留**：本项目 8080 属于其他项目，
> 5173 也被占用，所以固定 5174 + `strictPort: true`（端口占用时**直接报错**而非静默漂移）。

---

## 5. 状态管理

| 规则 | 内容 |
|---|---|
| 组件局部状态用 `ref` / `reactive` | 不要为了「统一」把所有状态塞 store |
| 跨页面共享才用 Pinia | 本项目仅 `timeRange`（全局时间筛选）一个 store |
| Store 用 setup 语法 | `defineStore('x', () => { ... return { ... } })` |
| 禁止在 store 里做 HTTP 请求 | 请求归 `api/`，store 只存状态 |
| 派生状态用 `computed` | 不在 store 里冗余存一份计算结果 |

---

## 6. 样式

### 6.1 设计令牌（强制）

所有颜色、间距、圆角、阴影、字号**只能引用 `styles/tokens.css` 的变量**：

```css
/* ✅ */
.stat-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  color: var(--color-text);
}

/* ❌ 硬编码：主题一改就漂移，且 Stylelint 会报错 */
.stat-card {
  background: #ffffff;
  border: 1px solid #e9ebef;
  border-radius: 12px;
}
```

现有令牌分组：品牌色 / 中性表面 / 文字 / 语义色（success·warning·danger·info·demo）/ 间距 / 圆角 / 阴影 / 字号。

### 6.2 作用域

| 规则 | 内容 |
|---|---|
| 组件样式必须 `scoped` | 避免全局污染 |
| 全局样式只放 `global.css` | reset、排版基线、滚动条 |
| 需要穿透子组件用 `:deep()` | 不要去掉 `scoped` |
| 禁止 `!important` | 出现即说明选择器优先级设计有问题 |

### 6.3 单位

| 场景 | 单位 |
|---|---|
| 字号、间距、圆角 | `px`（后台系统不需要 rem 缩放） |
| 图表容器高度 | `px` 或 `vh` |
| 边框 | `1px` |
| 禁止 | `em`（叠加计算难预测）、`pt` |

### 6.4 现状待治理

| 问题 | 现状 | 处置 |
|---|---|---|
| 硬编码色值（FE-05） | 10 个 `.vue` 的 scoped 样式 | 接入 Stylelint 后按报告收敛到令牌 |
| 无 Stylelint | 无法自动检查 | B8 接入 `stylelint-config-standard` + `stylelint-declaration-strict-value` |

---

## 7. 质量工具链

### 7.1 现状（`package.json`）

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "type-check": "vue-tsc --noEmit",
  "lint": "eslint .",
  "preview": "vite preview --port 5174 --strictPort"
}
```

| 工具 | 状态 |
|---|---|
| ESLint 9（flat config）+ `typescript-eslint` + `eslint-plugin-vue` | ✅ 已有 |
| TypeScript `strict` + `vue-tsc` | ✅ 已有 |
| **Prettier** | ❌ 缺（FE-04，P0） |
| **Stylelint** | ❌ 缺（FE-04，P0） |
| **Vitest** + `npm test` | ❌ 缺（FE-04，P0） |

### 7.2 目标（B8 批次交付）

```json
"scripts": {
  "dev": "vite",
  "build": "vite build",
  "type-check": "vue-tsc --noEmit",
  "lint": "eslint .",
  "lint:style": "stylelint \"src/**/*.{vue,css}\"",
  "format": "prettier --write \"src/**/*.{ts,vue,css}\"",
  "format:check": "prettier --check \"src/**/*.{ts,vue,css}\"",
  "test": "vitest run",
  "test:watch": "vitest",
  "verify": "npm run format:check && npm run lint && npm run lint:style && npm run type-check && npm run test && npm run build"
}
```

`npm run verify` 是前端唯一门禁入口，由 `scripts/verify.sh` 调用。

### 7.3 ESLint 规则基线

现有配置中两条 `warn` 应升级为 `error`：

```js
'@typescript-eslint/no-unused-vars': 'error',   // 原 warn
'@typescript-eslint/no-explicit-any': 'error',  // 原 warn
```

**升级前先确认 0 违规**（当前 `no-explicit-any` 已 0 处，可直接升）。

建议新增：

| 规则 | 作用 |
|---|---|
| `vue/component-api-style: ['error', ['script-setup']]` | 禁止 Options API |
| `vue/define-props-declaration: ['error', 'type-based']` | 强制类型化 props |
| `vue/no-unused-properties` | 发现未使用的 prop |
| `no-console: ['warn', { allow: ['warn', 'error'] }]` | 禁止遗留 `console.log` |

---

## 8. 测试

| 规则 | 内容 |
|---|---|
| 框架 | Vitest（与 Vite 同源，无需额外构建配置） |
| 组件测试 | `@vue/test-utils` |
| 覆盖重点 | `utils/`（纯函数）→ `composables/`（逻辑）→ `components/`（渲染分支） |
| 不测 | `views/` 的整页渲染（成本高收益低，靠 E2E） |
| 命名 | `xxx.spec.ts`，与被测文件同目录或 `__tests__/` |
| 断言 | 断言行为，不断言实现细节（不测内部变量名） |

**优先补测试的文件**（按性价比）：

| 文件 | 理由 |
|---|---|
| `utils/format.ts` | 金额/比率格式化，错了会显示错数字 |
| `utils/time.ts` | 时间范围换算，边界多 |
| `utils/table.ts` | 分页/排序计算 |
| `composables/useAsyncData.ts` | loading/error/empty 三态机 |

---

## 9. 性能

| 规则 | 内容 |
|---|---|
| 路由懒加载 | `component: () => import('@/views/Xxx.vue')` |
| ECharts 按需引入 | 只注册用到的图表类型与组件 |
| 大列表 | 超过 200 行用虚拟滚动或分页（本项目用服务端分页） |
| 计算属性缓存 | 模板中的派生数据用 `computed`，不在模板里调函数 |
| 图片 | 用 WebP；小于 4KB 内联 |
| 禁止 | 在 `watch` 里做无节流的高频操作 |

---

## 10. 拒绝清单

| 编号 | 拒绝项 | 检测方式 |
|---|---|---|
| F-01 | `any` | `grep -rn ": any" frontend/src/` |
| F-02 | `@ts-ignore` | `grep -rn "@ts-ignore"` |
| F-03 | 视图/组件直连 `axios` | `grep -rn "from 'axios'" frontend/src/views frontend/src/components` |
| F-04 | Options API | ESLint `vue/component-api-style` |
| F-05 | 硬编码色值 | `grep -rnE "#[0-9a-fA-F]{3,8}\b" frontend/src/**/*.vue` |
| F-06 | `v-for` 缺 `:key` 或用索引 | ESLint `vue/require-v-for-key` |
| F-07 | `!important` | `grep -rn "!important"` |
| F-08 | `console.log` 遗留 | ESLint `no-console` |
| F-09 | 写死后端 host | `grep -rn "127.0.0.1:8091\|localhost:8091" frontend/src/` |
| F-10 | `Number()` 转金额后相加 | 人工评审 |
| F-11 | 单文件 > 400 行 | 见下表 |
| F-12 | 拼写错误的文件名 | 人工评审 |

**F-11 当前超限清单**

| 文件 | 行数 | 处置 |
|---|---:|---|
| `api/types.ts` | 445 | FE-01，B9 按域拆分 |
| `views/CostProtection.vue` | 412 | FE-06，B9 抽子组件 |

---

## 11. 现状核对（2026-10-26）

| 指标 | 当前 | 目标 |
|---|---:|---:|
| 视图数 | 10 | — |
| 组件数 | 13 | — |
| API 模块数 | 6（含 `request`/`types`） | — |
| `any` | 0 | 0 ✅ |
| `@ts-ignore` | 0 | 0 ✅ |
| 视图/组件直连 axios | 0 | 0 ✅ |
| 类型化 props 覆盖率 | 13 / 13 | 100% ✅ |
| TS `strict` | 已开 | ✅ |
| 「三态」组件复用 | `AsyncState` 统一 loading/error/empty | ✅ |
| Prettier | 未接入 | ❌ B8 |
| Stylelint | 未接入 | ❌ B8 |
| Vitest | 未接入 | ❌ B8 |
| 硬编码色值 | 存在于 10 个视图 | 收敛至令牌（B8） |

复现命令：

```bash
cd frontend
npm run type-check && npm run lint        # 必须双双 exit 0
grep -rn ": any" src/ | wc -l            # 0
grep -rn "from 'axios'" src/views src/components | wc -l   # 0
```

---

## 12. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0 | 2026-10-26 | 首版。配置与指标来自 `package.json` / `tsconfig.json` / `eslint.config.js` 与源码实测 |
