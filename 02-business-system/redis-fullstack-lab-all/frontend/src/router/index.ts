import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

import CacheView from '@/views/CacheView.vue'
import SessionView from '@/views/SessionView.vue'
import RankView from '@/views/RankView.vue'
import RateLimitView from '@/views/RateLimitView.vue'
import PubSubView from '@/views/PubSubView.vue'
import PenetrationView from '@/views/PenetrationView.vue'
import BreakdownView from '@/views/BreakdownView.vue'
import AvalancheView from '@/views/AvalancheView.vue'
import LockView from '@/views/LockView.vue'
import SeckillView from '@/views/SeckillView.vue'
import IdempotentView from '@/views/IdempotentView.vue'
import AiQuotaView from '@/views/AiQuotaView.vue'
import DelayQueueView from '@/views/DelayQueueView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/cache' },
  { path: '/cache', name: '缓存加速', component: CacheView },
  { path: '/session', name: '分布式会话', component: SessionView },
  { path: '/rank', name: '排行榜', component: RankView },
  { path: '/ratelimit', name: '限流控制', component: RateLimitView },
  { path: '/pubsub', name: '发布订阅', component: PubSubView },
  { path: '/penetration', name: '缓存穿透', component: PenetrationView },
  { path: '/breakdown', name: '缓存击穿', component: BreakdownView },
  { path: '/avalanche', name: '缓存雪崩', component: AvalancheView },
  { path: '/lock', name: '分布式锁', component: LockView },
  { path: '/seckill', name: '秒杀库存', component: SeckillView },
  { path: '/idempotent', name: '幂等处理', component: IdempotentView },
  { path: '/aiquota', name: 'AI 额度管理', component: AiQuotaView },
  { path: '/delayqueue', name: '延迟队列', component: DelayQueueView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
