import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export type NavGroup = '分析' | '治理' | '运维'

export interface NavItem {
  path: string
  label: string
  group: NavGroup
}

/**
 * 侧栏导航与路由共用同一份定义。
 * 10 个页面覆盖治理平台全部视图：成本核算 → 构成拆解 → 优化 → 预算 → 预警 → 保护 → 追踪。
 */
export const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard/cost', label: '成本总览', group: '分析' },
  { path: '/dashboard/cost/breakdown', label: '成本构成', group: '分析' },
  { path: '/dashboard/token-usage', label: 'Token 分析', group: '分析' },
  { path: '/dashboard/model-cost', label: '模型成本', group: '分析' },

  { path: '/dashboard/budget', label: '预算管理', group: '治理' },
  { path: '/dashboard/cost-optimization', label: '优化中心', group: '治理' },
  { path: '/dashboard/cost-alerts', label: '预警中心', group: '治理' },
  { path: '/dashboard/cost-protection', label: '系统保护策略', group: '治理' },
  { path: '/dashboard/model-pricing', label: '价格管理', group: '治理' },

  { path: '/dashboard/ai-traces', label: '请求追踪', group: '运维' },
]

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard/cost' },
  {
    path: '/dashboard/cost',
    name: 'cost-overview',
    component: () => import('@/views/AiCostOverview.vue'),
    meta: { title: '成本总览' },
  },
  {
    path: '/dashboard/cost/breakdown',
    name: 'cost-breakdown',
    component: () => import('@/views/CostBreakdown.vue'),
    meta: { title: '成本构成' },
  },
  {
    path: '/dashboard/token-usage',
    name: 'token-usage',
    component: () => import('@/views/TokenUsage.vue'),
    meta: { title: 'Token 分析' },
  },
  {
    path: '/dashboard/model-cost',
    name: 'model-cost',
    component: () => import('@/views/ModelCost.vue'),
    meta: { title: '模型成本' },
  },
  {
    path: '/dashboard/budget',
    name: 'budget',
    component: () => import('@/views/Budget.vue'),
    meta: { title: '预算管理' },
  },
  {
    path: '/dashboard/cost-optimization',
    name: 'cost-optimization',
    component: () => import('@/views/CostOptimization.vue'),
    meta: { title: '优化中心' },
  },
  {
    path: '/dashboard/cost-alerts',
    name: 'cost-alerts',
    component: () => import('@/views/CostAlerts.vue'),
    meta: { title: '预警中心' },
  },
  {
    path: '/dashboard/cost-protection',
    name: 'cost-protection',
    component: () => import('@/views/CostProtection.vue'),
    meta: { title: '系统保护策略' },
  },
  {
    path: '/dashboard/model-pricing',
    name: 'model-pricing',
    component: () => import('@/views/ModelPricing.vue'),
    meta: { title: '价格管理' },
  },
  {
    path: '/dashboard/ai-traces',
    name: 'ai-traces',
    component: () => import('@/views/AiRequests.vue'),
    meta: { title: '请求追踪' },
  },

  // 旧路径兼容：V1 页面曾经用过这些地址，重定向到新位置，避免书签失效
  { path: '/dashboard/ai-cost', redirect: '/dashboard/cost' },
  { path: '/dashboard/ai-requests', redirect: '/dashboard/ai-traces' },

  { path: '/:pathMatch(.*)*', redirect: '/dashboard/cost' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '控制台'
  document.title = `${title} · AI Cost Governance Platform`
})

export default router
