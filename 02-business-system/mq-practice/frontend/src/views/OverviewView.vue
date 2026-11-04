<template>
  <div>
    <div class="grid grid-4" style="margin-bottom: 16px">
      <StatCard
        label="消息发送总量"
        :value="overview?.totalSent ?? 0"
        unit="条"
        hint="每一次 send 都会落一条本地消息表"
        tone="blue"
      />
      <StatCard
        label="Broker 已确认"
        :value="overview?.confirmedCount ?? 0"
        unit="条"
        hint="Publisher Confirm ack=true"
        tone="teal"
      />
      <StatCard
        label="消费幂等记录"
        :value="overview?.idempotentRecords ?? 0"
        unit="条"
        hint="Redis 第一层 + DB 唯一键第二层"
      />
      <StatCard
        label="死信积压"
        :value="overview?.dlqCount ?? 0"
        unit="条"
        :hint="(overview?.dlqCount ?? 0) > 0 ? '需要人工确认或回放' : '暂无，链路健康'"
        :tone="(overview?.dlqCount ?? 0) > 0 ? 'red' : 'teal'"
      />
    </div>

    <div class="panel" style="margin-bottom: 16px">
      <div class="panel-head">
        <div class="panel-title"><span class="dot"></span>消息拓扑与实时积压</div>
        <div style="display: flex; align-items: center; gap: 10px">
          <span class="panel-hint">每 5 秒自动刷新 · {{ lastUpdated || '—' }}</span>
          <el-button size="small" :loading="loading" @click="refresh">
            <el-icon><Refresh /></el-icon>
            <span style="margin-left: 4px">刷新</span>
          </el-button>
        </div>
      </div>
      <div class="panel-body">
        <MqTopology :queues="queues" />
        <div v-if="!brokerAvailable" class="banner danger">
          <strong>RabbitMQ 未连接</strong>
          <span>{{ overview?.brokerMessage }}</span>
          <code>docker compose up -d rabbitmq</code>
        </div>
      </div>
    </div>

    <div class="grid grid-2" style="margin-bottom: 16px">
      <div class="panel">
        <div class="panel-head">
          <div class="panel-title"><span class="dot"></span>订单状态分布</div>
          <span class="panel-hint">状态流转全部使用 CAS 条件更新</span>
        </div>
        <div class="panel-body">
          <div class="status-grid">
            <div v-for="s in statusItems" :key="s.key" class="status-cell">
              <div class="status-count num" :style="{ color: s.color }">{{ s.value }}</div>
              <div class="status-label">{{ s.label }}</div>
            </div>
          </div>
          <div class="kv" style="margin-top: 12px">
            <span class="k">发送成功率</span>
            <span class="v">{{ successRate }}</span>
          </div>
          <div class="kv">
            <span class="k">投递超时（延迟队列中待触发）</span>
            <span class="v">{{ depth('mq.order.delay.queue') }} 条</span>
          </div>
          <div class="kv">
            <span class="k">延迟实现方式</span>
            <span class="v">
              {{ overview?.delayMode === 'plugin' ? 'x-delayed-message 插件' : 'DLX + 消息级 TTL' }}
            </span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div class="panel-title"><span class="dot"></span>可靠性开关状态</div>
          <span class="panel-hint">生产级配置自检</span>
        </div>
        <div class="panel-body">
          <div v-for="item in reliabilityItems" :key="item.label" class="rel-row">
            <div>
              <div class="rel-label">{{ item.label }}</div>
              <div class="rel-desc">{{ item.desc }}</div>
            </div>
            <span class="tag" :class="item.ok ? 'teal' : 'red'">{{ item.ok ? '已生效' : '未启用' }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid" style="grid-template-columns: repeat(3, minmax(0, 1fr))">
      <div v-for="card in scenarioCards" :key="card.path" class="panel scenario" @click="go(card.path)">
        <div class="panel-body">
          <div class="scenario-top">
            <span class="tag" :class="card.tagClass">{{ card.no }}</span>
            <span class="scenario-arrow">→</span>
          </div>
          <div class="scenario-title">{{ card.title }}</div>
          <div class="scenario-desc">{{ card.desc }}</div>
          <ul class="scenario-points">
            <li v-for="p in card.points" :key="p">{{ p }}</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Refresh } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import MqTopology from '@/components/MqTopology.vue'
import { useMonitorStore } from '@/store/monitor'

const router = useRouter()
const store = useMonitorStore()
const { overview, queues, loading, lastUpdated, brokerAvailable } = storeToRefs(store)

let timer: number | undefined

const depth = (name: string) => store.queueByName(name)?.messageCount ?? 0

const statusItems = computed(() => [
  { key: 'QUEUING', label: '排队中', value: overview.value?.orderQueuing ?? 0, color: '#3f5bd9' },
  { key: 'UNPAID', label: '待支付', value: overview.value?.orderUnpaid ?? 0, color: '#b4740e' },
  { key: 'PAID', label: '已支付', value: overview.value?.orderPaid ?? 0, color: '#0e9384' },
  { key: 'CANCELLED', label: '已取消', value: overview.value?.orderCancelled ?? 0, color: '#6b7280' },
  { key: 'FAILED', label: '创建失败', value: overview.value?.orderFailed ?? 0, color: '#c53a2b' }
])

const successRate = computed(() => {
  const total = overview.value?.totalSent ?? 0
  if (total === 0) return '—'
  const confirmed = overview.value?.confirmedCount ?? 0
  return `${((confirmed / total) * 100).toFixed(1)} %（${confirmed}/${total}）`
})

const reliabilityItems = computed(() => [
  {
    label: 'Publisher Confirm',
    desc: 'publisher-confirm-type=correlated，逐条消息关联 CorrelationData',
    ok: true
  },
  {
    label: 'Publisher Return',
    desc: 'mandatory=true + ReturnsCallback，覆盖"到交换机但无队列"的漏洞',
    ok: true
  },
  {
    label: '手动 ACK',
    desc: 'acknowledge-mode=manual，业务成功才 basicAck',
    ok: true
  },
  {
    label: '消费幂等',
    desc: 'Redis SETNX + mq_idempotent_log 唯一键双层校验',
    ok: overview.value?.idempotentEnabled ?? false
  },
  {
    label: '失败重试 + 死信',
    desc: '重试队列 5s 退避，超过 3 次进入 mq.dlq.queue',
    ok: true
  }
])

const scenarioCards = [
  {
    no: '场景 01',
    tagClass: 'blue',
    path: '/seckill',
    title: '秒杀下单 · 削峰填谷',
    desc: '接口只做 Redis 预扣 + 投递消息，落单与扣库存在消费端串行完成。',
    points: ['Redis Lua 原子预扣', 'DB 条件更新兜底防超卖', '前端轮询排队结果']
  },
  {
    no: '场景 02',
    tagClass: 'amber',
    path: '/orders',
    title: '超时取消 · 延迟消息',
    desc: 'DLX + TTL 让消息"静默"30 分钟后再触发，消费时回查状态防误取消。',
    points: ['延迟队列无消费者', '状态 CAS 与支付互斥', '定时扫描兜底补偿']
  },
  {
    no: '场景 03',
    tagClass: 'violet',
    path: '/products',
    title: '商品变更 · 发布订阅',
    desc: '一条事件被两个独立队列消费，缓存与检索索引各自演进、互不阻塞。',
    points: ['topic 交换机多订阅', '版本号防乱序覆盖', '最终一致性可观察']
  }
]

const go = (path: string) => router.push(path)

async function refresh() {
  await store.loadOverview()
}

onMounted(async () => {
  await store.loadOverview()
  timer = window.setInterval(() => store.loadOverview(true), 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.banner {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: var(--radius);
  font-size: 12.5px;
}

.banner.danger {
  background: var(--danger-soft);
  border: 1px solid #f6cfcb;
  color: var(--danger);
}

.banner code {
  font-family: var(--font-mono);
  background: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid #f0d5d2;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.status-cell {
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 8px;
  text-align: center;
}

.status-count {
  font-size: 20px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.status-label {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.rel-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--border);
}

.rel-row:last-child {
  border-bottom: none;
}

.rel-label {
  font-size: 12.5px;
  font-weight: 600;
}

.rel-desc {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 1px;
}

.scenario {
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}

.scenario:hover {
  border-color: #c9d2f5;
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.scenario-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.scenario-arrow {
  color: var(--text-muted);
  font-size: 13px;
}

.scenario-title {
  font-size: 14px;
  font-weight: 650;
  margin-top: 10px;
}

.scenario-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
  min-height: 38px;
}

.scenario-points {
  margin: 10px 0 0;
  padding-left: 16px;
  color: var(--text-muted);
  font-size: 11.5px;
  line-height: 1.9;
}

@media (max-width: 1180px) {
  .grid[style*='repeat(3'] {
    grid-template-columns: minmax(0, 1fr) !important;
  }

  .status-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
