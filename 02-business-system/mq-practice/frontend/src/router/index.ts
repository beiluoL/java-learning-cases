import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

/**
 * 路由懒加载：每个场景一个 chunk，首屏只加载总览。
 */
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/overview' },
  {
    path: '/overview',
    name: 'overview',
    component: () => import('@/views/OverviewView.vue'),
    meta: { title: '总览与拓扑', subtitle: '三大场景的实时状态与消息流转路径' }
  },
  {
    path: '/seckill',
    name: 'seckill',
    component: () => import('@/views/SeckillView.vue'),
    meta: { title: '场景一 · 秒杀下单', subtitle: '削峰填谷：接口只受理，落单交给消费者' }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('@/views/OrderView.vue'),
    meta: { title: '场景二 · 订单与超时取消', subtitle: 'DLX + TTL 延迟消息驱动 30 分钟自动关单' }
  },
  {
    path: '/products',
    name: 'products',
    component: () => import('@/views/ProductView.vue'),
    meta: { title: '场景三 · 商品变更订阅', subtitle: '一次事件，两个独立消费者并行处理' }
  },
  {
    path: '/monitor',
    name: 'monitor',
    component: () => import('@/views/MonitorView.vue'),
    meta: { title: 'MQ 监控台', subtitle: '队列深度、消息账本、幂等记录、死信与故障注入' }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/AdminView.vue'),
    meta: {
      title: 'RabbitMQ 运维台',
      subtitle: 'Broker 侧视角：队列 · 交换机 · 绑定 · 消费者 · 连接（经后端代理）'
    }
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})
