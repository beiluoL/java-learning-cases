import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from '@/views/DashboardView.vue';

const routes = [
  { path: '/', redirect: '/dashboard' },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: DashboardView,
    meta: { title: '实时数据看板' },
  },
  {
    path: '/devices',
    name: 'devices',
    component: () => import('@/views/DeviceView.vue'),
    meta: { title: '设备管理' },
  },
  {
    path: '/analysis',
    name: 'analysis',
    component: () => import('@/views/AnalysisView.vue'),
    meta: { title: '实时分析' },
  },
  {
    path: '/opinion',
    name: 'opinion',
    component: () => import('@/views/OpinionView.vue'),
    meta: { title: '舆情监控' },
  },
  {
    path: '/visualization',
    name: 'visualization',
    component: () => import('@/views/VisualizationView.vue'),
    meta: { title: '数据可视化' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
