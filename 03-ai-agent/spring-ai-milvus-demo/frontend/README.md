# AI Token 成本管理平台 · 前端

企业级 AI Token 成本管理平台前端。接口契约以 `docs/api/AI_COST_API.md` 为唯一基准，
**所有统计数值均来自真实 API**，无数据时展示空状态，不写入任何 mock 统计数组。

## 技术栈

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Axios + ECharts。
不依赖 Element Plus / Ant Design，UI 由原生 CSS 与自写组件实现（见 `src/styles/tokens.css`）。

## 启动

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5174/dashboard/ai-cost>。

| 命令 | 说明 |
|---|---|
| `npm run dev` | 启动开发服务器 |
| `npm run type-check` | `vue-tsc --noEmit` 严格类型检查 |
| `npm run build` | 生产构建 |
| `npm run lint` | ESLint（flat config） |
| `npm run preview` | 预览构建产物（同样固定 5174） |

## 端口约定（重要）

- **前端 dev server：5174**，且 `strictPort: true`（见 `vite.config.ts`）。
  5173 已被其他项目占用，端口冲突时会直接报错，绝不自动漂移抢占。
- **后端服务：8091**。`vite.config.ts` 中 `/api` 代理到 `http://localhost:8091`。
  8080 属于其他项目，严禁指向。
- 后端需先启动并对外提供契约中的 `/api/...` 接口，否则页面会显示「无法连接后端服务」错误态，
  可通过错误态的「重新加载」按钮重试。

## 页面与接口

| 路由 | 页面 | 主要接口 |
|---|---|---|
| `/dashboard/ai-cost` | 成本总览 | `cost/overview`、`cost/trend`、`cost/models`、`cost/scenes`、`cost/top-models`、`cost/top-users`、`cost/recent-requests`、`cost/alerts` |
| `/dashboard/token-usage` | Token 使用分析 | `cost/overview`、`cost/models`、`cost/scenes`、`cost/top-users`、`cost/trend` |
| `/dashboard/model-cost` | 模型成本分析 | `cost/models` |
| `/dashboard/ai-requests` | 调用记录 | `usage/page`、`usage/{requestId}` |
| `/dashboard/budget` | 预算管理 | `budgets`、`budgets/status`、`POST/PUT /budgets` |
| `/dashboard/model-pricing` | 模型价格管理 | `pricing`、`pricing/history`、`POST/PUT /pricing` |

成本总览 / Token 分析 / 模型成本三个页面共享顶部时间筛选（今天 / 最近 7 天 / 最近 30 天 / 本月 / 自定义），
筛选变化会同步改写**所有**请求的 `startTime` / `endTime`（ISO 8601 UTC）并重新取数。
调用记录页的时间维度同样来自该筛选器。

## 目录结构

```
frontend/
├── vite.config.ts            # 5174 + /api → 8091
├── src/
│   ├── api/                  # Axios 实例（解包 {code,message,data,traceId}）+ 各接口模块 + 契约类型
│   ├── components/           # StatCard / ChartCard / DataTable / FilterBar / Modal / *State / ProgressBar 等
│   ├── composables/          # useAsyncData / useResource（加载、错误、重试、防竞态）
│   ├── stores/               # Pinia 全局时间筛选
│   ├── styles/               # 设计 token + 全局样式
│   ├── utils/                # 金额/Token/时间格式化、ECharts 配置工厂、时间范围计算
│   ├── views/                # 6 个业务页面
│   ├── router/ App.vue main.ts
```

## 约定

- 金额是字符串（BigDecimal 精度），格式化全程不经过 `parseFloat`，保留 6~8 位小数并加千分位。
- Token 为整数千分位；时间统一由 ISO UTC 转为本地时区展示。
- `code !== 0` 抛出 `ApiError`（携带 `message` 与 `traceId`），页面错误态可直接展示并重试。
- 价格版本 `demoFlag = true` 会标记为 **DEMO 示例价**，不代表供应商真实报价。
- 移动端：栅格自动降级、表格横向滚动、侧栏折叠为横向导航。
