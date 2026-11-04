<template>
  <div class="topo">
    <div class="topo-head">
      <span>业务场景</span>
      <span>交换机（类型）</span>
      <span>队列 / 积压</span>
      <span>消费者</span>
    </div>

    <div v-for="row in rows" :key="row.key" class="topo-row">
      <div class="cell scene">
        <span class="tag" :class="row.tagClass">{{ row.scene }}</span>
      </div>

      <div class="cell exchange">
        <div class="mono">{{ row.exchange }}</div>
        <div class="type">{{ row.exchangeType }}</div>
      </div>

      <div class="cell queue">
        <template v-for="(q, i) in row.queues" :key="q">
          <div v-if="i > 0" class="hop">▼ {{ row.hopLabel }}</div>
          <div class="queue-line">
            <span class="mono">{{ q }}</span>
            <span class="depth" :class="{ hot: depthOf(q) > 0 }">
              {{ depthOf(q) }}
              <em v-if="depthOf(q) > 0">条</em>
            </span>
          </div>
        </template>
      </div>

      <div class="cell consumer">
        <div>{{ row.consumer }}</div>
        <div class="hint">{{ row.consumerHint }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { QueueInfoView } from '@/types'

const props = defineProps<{ queues: QueueInfoView[] }>()

interface Row {
  key: string
  scene: string
  tagClass: string
  exchange: string
  exchangeType: string
  queues: string[]
  hopLabel?: string
  consumer: string
  consumerHint: string
}

const rows: Row[] = [
  {
    key: 'order-create',
    scene: '① 秒杀下单',
    tagClass: 'blue',
    exchange: 'mq.order.exchange',
    exchangeType: 'direct',
    queues: ['mq.order.create.queue'],
    consumer: 'OrderCreateConsumer',
    consumerHint: '扣库存 + 落单，手动 ACK'
  },
  {
    key: 'order-cancel',
    scene: '② 超时取消',
    tagClass: 'amber',
    exchange: 'mq.order.delay.exchange',
    exchangeType: 'direct + TTL',
    queues: ['mq.order.delay.queue', 'mq.order.cancel.queue'],
    hopLabel: 'TTL 到期转死信',
    consumer: 'OrderCancelConsumer',
    consumerHint: '回查状态再取消；≤60s 演示走 short 队列'
  },
  {
    key: 'product-cache',
    scene: '③ 商品变更',
    tagClass: 'violet',
    exchange: 'mq.product.exchange',
    exchangeType: 'topic',
    queues: ['mq.product.cache.queue'],
    consumer: 'CacheSyncConsumer',
    consumerHint: '刷新 / 删除 Redis 缓存'
  },
  {
    key: 'product-search',
    scene: '③ 商品变更',
    tagClass: 'violet',
    exchange: 'mq.product.exchange',
    exchangeType: 'topic（同一 routingKey）',
    queues: ['mq.product.search.queue'],
    consumer: 'SearchIndexConsumer',
    consumerHint: '写检索索引（模拟 ES）'
  },
  {
    key: 'dlq',
    scene: '兜底',
    tagClass: 'red',
    exchange: 'mq.dlx.exchange',
    exchangeType: 'direct',
    queues: ['mq.dlq.queue'],
    consumer: '人工 / 回放',
    consumerHint: '查看后可一键重投'
  }
]

const depthMap = computed(() => {
  const map = new Map<string, number>()
  props.queues.forEach((q) => map.set(q.name, q.messageCount))
  return map
})

const depthOf = (name: string) => depthMap.value.get(name) ?? 0
</script>

<style scoped>
.topo {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
}

.topo-head {
  display: grid;
  grid-template-columns: 110px 1.15fr 1.35fr 1.1fr;
  gap: 12px;
  padding: 9px 14px;
  background: var(--surface-soft);
  border-bottom: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.06em;
}

.topo-row {
  display: grid;
  grid-template-columns: 110px 1.15fr 1.35fr 1.1fr;
  gap: 12px;
  padding: 12px 14px;
  align-items: center;
  border-bottom: 1px solid var(--border);
}

.topo-row:last-child {
  border-bottom: none;
}

.topo-row:hover {
  background: #fcfcfd;
}

.cell {
  min-width: 0;
}

.exchange .mono,
.queue .mono {
  font-size: 11.5px;
  color: var(--text);
  word-break: break-all;
}

.exchange .type {
  font-size: 10.5px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.queue-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
}

.depth {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.depth.hot {
  color: var(--amber);
  font-weight: 650;
}

.depth em {
  font-style: normal;
  font-size: 10px;
}

.hop {
  font-size: 10.5px;
  color: var(--text-muted);
  padding: 3px 0 3px 8px;
  font-family: var(--font-mono);
}

.consumer > div:first-child {
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.hint {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 2px;
}

@media (max-width: 1080px) {
  .topo-head {
    display: none;
  }

  .topo-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
