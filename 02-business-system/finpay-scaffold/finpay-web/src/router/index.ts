import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '概览' } },
      { path: 'transactions', name: 'transactions', component: () => import('@/views/TransactionQuery.vue'), meta: { title: '交易查询' } },
      { path: 'reconcile', name: 'reconcile', component: () => import('@/views/Reconcile.vue'), meta: { title: '对账管理' } },
      { path: 'audit', name: 'audit', component: () => import('@/views/AuditLog.vue'), meta: { title: '审计日志' } },
      { path: 'channels', name: 'channels', component: () => import('@/views/ChannelConfig.vue'), meta: { title: '渠道配置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.token) {
    return '/login'
  }
  if (to.path === '/login' && auth.token) {
    return '/'
  }
})

export default router
