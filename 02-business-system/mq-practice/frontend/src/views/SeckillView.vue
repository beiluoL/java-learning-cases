<template>
  <div>
    <div class="intro">
      <div>
        <div class="intro-title">接口只做三件事：Redis 预扣 → 落排队单 → 投递消息</div>
        <div class="intro-desc">
          真正的扣库存、建订单、发通知都在消费端完成。接口 RT 从"数据库事务耗时"变成"一次 Redis + 一次 send"，
          这就是削峰填谷的实际收益。
        </div>
      </div>
      <div class="intro-metrics">
        <div class="metric">
          <div class="metric-value num">{{ totalStock }}</div>
          <div class="metric-label">商品库存合计</div>
        </div>
        <div class="metric">
          <div class="metric-value num">{{ redisStockTotal }}</div>
          <div class="metric-label">Redis 可预扣</div>
        </div>
        <div class="metric">
          <div class="metric-value num">{{ latest ? `${latest.costMs}ms` : '—' }}</div>
          <div class="metric-label">最近受理耗时</div>
        </div>
      </div>
    </div>

    <div class="layout">
      <div class="panel">
        <div class="panel-head">
          <div class="panel-title"><span class="dot"></span>秒杀商品</div>
          <el-button size="small" :loading="loading" @click="store.loadProducts()">
            <el-icon><Refresh /></el-icon>
            <span style="margin-left: 4px">刷新库存</span>
          </el-button>
        </div>
        <div class="panel-body">
          <div v-if="products.length === 0" class="empty-state">暂无商品，请确认后端已启动</div>
          <div class="product-grid">
            <div v-for="p in products" :key="p.id" class="product-card">
              <div class="product-top">
                <div>
                  <div class="product-name">{{ p.name }}</div>
                  <div class="product-no mono">{{ p.productNo }} · v{{ p.version }}</div>
                </div>
                <span class="tag" :class="p.status === 1 ? 'teal' : ''">
                  {{ p.statusText }}
                </span>
              </div>

              <div class="product-price num">
                ¥{{ p.price.toFixed(2) }}
              </div>

              <div class="stock-row">
                <span class="stock-label">数据库库存</span>
                <div class="stock-bar">
                  <div class="stock-fill db" :style="{ width: percent(p.availableStock, p.totalStock) }"></div>
                </div>
                <span class="stock-num num">{{ p.availableStock }} / {{ p.totalStock }}</span>
              </div>
              <div class="stock-row">
                <span class="stock-label">Redis 预扣</span>
                <div class="stock-bar">
                  <div class="stock-fill redis" :style="{ width: percent(p.redisStock, p.totalStock) }"></div>
                </div>
                <span class="stock-num num">{{ p.redisStock ?? '—' }}</span>
              </div>

              <div class="product-actions">
                <el-input-number v-model="quantities[p.id]" :min="1" :max="5" size="small" />
                <el-button
                  type="primary"
                  size="small"
                  :loading="submitting && activeProductId === p.id"
                  :disabled="p.status !== 1"
                  @click="buy(p)"
                >
                  立即抢购
                </el-button>
                <el-button size="small" text @click="store.reset(p.id)">重置</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="side">
        <div class="panel">
          <div class="panel-head">
            <div class="panel-title"><span class="dot"></span>排队与落单</div>
            <span v-if="polling" class="tag blue">
              <span class="blink"></span>
              轮询中 {{ pollCount }}
            </span>
          </div>
          <div class="panel-body">
            <div v-if="!latest" class="empty-state">
              点击左侧「立即抢购」，这里会展示受理凭证与异步落单结果
            </div>
            <template v-else>
              <div class="kv">
                <span class="k">订单号</span>
                <span class="v">{{ latest.orderNo }}</span>
              </div>
              <div class="kv">
                <span class="k">接口耗时</span>
                <span class="v">{{ latest.costMs }} ms</span>
              </div>
              <div class="kv">
                <span class="k">预扣后余量</span>
                <span class="v">{{ latest.remainingStock ?? '未预热' }}</span>
              </div>
              <div class="kv">
                <span class="k">messageId</span>
                <span class="v">{{ latest.messageId.slice(0, 16) }}…</span>
              </div>

              <div class="steps">
                <div class="step done">
                  <span class="step-dot"></span>
                  <div>
                    <div class="step-title">已受理（QUEUING）</div>
                    <div class="step-desc">{{ latest.message }}</div>
                  </div>
                </div>
                <div class="step" :class="stepClass(1)">
                  <span class="step-dot"></span>
                  <div>
                    <div class="step-title">
                      {{ latestOrder?.status === 'FAILED' ? '落单失败（FAILED）' : '异步落单（UNPAID）' }}
                    </div>
                    <div class="step-desc">
                      <template v-if="!latestOrder">等待消费者处理…</template>
                      <template v-else-if="latestOrder.status === 'FAILED'">
                        {{ latestOrder.cancelReason || '库存不足' }}
                      </template>
                      <template v-else>
                        金额 ¥{{ latestOrder.amount.toFixed(2) }} · 支付截止 {{ latestOrder.expireTime }}
                      </template>
                    </div>
                  </div>
                </div>
                <div class="step" :class="stepClass(2)">
                  <span class="step-dot"></span>
                  <div>
                    <div class="step-title">支付 / 超时取消</div>
                    <div class="step-desc">
                      <template v-if="latestOrder?.status === 'UNPAID'">
                        <CountdownText
                          :seconds="latestOrder.remainingSeconds"
                          :active="true"
                          style="margin-right: 6px"
                        />
                        后自动取消
                      </template>
                      <template v-else>到「订单」页可模拟支付或等待自动取消</template>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="panel" style="margin-top: 16px">
          <div class="panel-head">
            <div class="panel-title"><span class="dot"></span>并发压测</div>
            <span class="panel-hint">验证「不超卖」</span>
          </div>
          <div class="panel-body">
            <div class="stress-controls">
              <el-select v-model="stressProductId" size="small" style="width: 100%">
                <el-option
                  v-for="p in products"
                  :key="p.id"
                  :label="`${p.name}（库存 ${p.availableStock}）`"
                  :value="p.id"
                />
              </el-select>
              <div class="stress-row">
                <span class="panel-hint">并发数</span>
                <el-radio-group v-model="concurrency" size="small">
                  <el-radio-button :value="20">20</el-radio-button>
                  <el-radio-button :value="50">50</el-radio-button>
                  <el-radio-button :value="100">100</el-radio-button>
                  <el-radio-button :value="200">200</el-radio-button>
                </el-radio-group>
              </div>
              <el-button type="primary" size="small" :loading="stressing" style="width: 100%" @click="runStress">
                开始压测
              </el-button>
            </div>

            <div v-if="stressResult" class="stress-result">
              <div class="stress-bars">
                <div class="stress-bar">
                  <span>成功受理</span>
                  <div class="bar-track">
                    <div class="bar-fill ok" :style="{ width: `${(stressResult.success / stressResult.total) * 100}%` }"></div>
                  </div>
                  <span class="num">{{ stressResult.success }}</span>
                </div>
                <div class="stress-bar">
                  <span>售罄拒绝</span>
                  <div class="bar-track">
                    <div class="bar-fill sold" :style="{ width: `${(stressResult.soldOut / stressResult.total) * 100}%` }"></div>
                  </div>
                  <span class="num">{{ stressResult.soldOut }}</span>
                </div>
                <div class="stress-bar">
                  <span>其它失败</span>
                  <div class="bar-track">
                    <div class="bar-fill fail" :style="{ width: `${(stressResult.failed / stressResult.total) * 100}%` }"></div>
                  </div>
                  <span class="num">{{ stressResult.failed }}</span>
                </div>
              </div>
              <div class="kv">
                <span class="k">库存变化</span>
                <span class="v">{{ stressResult.stockBefore }} → {{ stressResult.stockAfter }}</span>
              </div>
              <div class="kv">
                <span class="k">总耗时 / 平均</span>
                <span class="v">{{ stressResult.durationMs }}ms / {{ stressResult.avgCostMs }}ms</span>
              </div>
              <div class="conclusion">{{ stressResult.conclusion }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import CountdownText from '@/components/CountdownText.vue'
import { useSeckillStore } from '@/store/seckill'
import type { ProductView } from '@/types'

const store = useSeckillStore()
const {
  products,
  loading,
  submitting,
  latest,
  latestOrder,
  polling,
  pollCount,
  stressResult,
  stressing
} = storeToRefs(store)

const quantities = reactive<Record<number, number>>({})
const activeProductId = ref<number | null>(null)
const stressProductId = ref<number>(1)
const concurrency = ref(50)

const totalStock = computed(() =>
  products.value.reduce((sum, p) => sum + (p.availableStock ?? 0), 0)
)
const redisStockTotal = computed(() =>
  products.value.reduce((sum, p) => sum + (p.redisStock ?? 0), 0)
)

const percent = (value: number | null, total: number | null) => {
  if (!value || !total) return '0%'
  return `${Math.min(100, (value / total) * 100)}%`
}

const stepClass = (index: number) => {
  const status = latestOrder.value?.status
  if (!status) return ''
  if (index === 1) return status === 'FAILED' ? 'error' : status === 'QUEUING' ? 'active' : 'done'
  return status === 'UNPAID' ? 'active' : status === 'PAID' || status === 'CANCELLED' ? 'done' : ''
}

async function buy(product: ProductView) {
  activeProductId.value = product.id
  try {
    const response = await store.submit({
      productId: product.id,
      quantity: quantities[product.id] ?? 1
    })
    ElMessage.success(`已受理，耗时 ${response.costMs}ms`)
  } catch {
    /* 错误提示由 axios 拦截器统一处理 */
  } finally {
    activeProductId.value = null
  }
}

async function runStress() {
  try {
    const result = await store.stress(stressProductId.value, 1, concurrency.value)
    ElMessage.success(`压测完成：成功 ${result.success}，售罄 ${result.soldOut}`)
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  await store.loadProducts()
  products.value.forEach((p) => {
    if (!quantities[p.id]) quantities[p.id] = 1
  })
  const first = products.value.find((p) => p.id === 1) || products.value[0]
  if (first) stressProductId.value = first.id
})

onBeforeUnmount(() => store.stopPolling())
</script>

<style scoped>
.intro {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  background: linear-gradient(120deg, #eef1fd 0%, #f7f9ff 55%, #eef7f6 100%);
  border: 1px solid #e0e5f7;
  border-radius: var(--radius-lg);
  padding: 18px 22px;
  margin-bottom: 16px;
}

.intro-title {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.2px;
}

.intro-desc {
  font-size: 12.5px;
  color: var(--text-secondary);
  margin-top: 4px;
  max-width: 720px;
  line-height: 1.7;
}

.intro-metrics {
  display: flex;
  gap: 26px;
  flex: 0 0 auto;
}

.metric-value {
  font-size: 22px;
  font-weight: 650;
  color: var(--primary);
  line-height: 1.2;
}

.metric-label {
  font-size: 11px;
  color: var(--text-muted);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.side {
  min-width: 0;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(272px, 1fr));
  gap: 12px;
}

.product-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 13px 14px;
  background: var(--surface);
  transition: border-color 0.16s, box-shadow 0.16s;
}

.product-card:hover {
  border-color: #cdd5f2;
  box-shadow: var(--shadow-sm);
}

.product-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: flex-start;
}

.product-name {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.35;
}

.product-no {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 2px;
}

.product-price {
  font-size: 17px;
  font-weight: 650;
  color: var(--danger);
  margin: 8px 0 10px;
}

.stock-row {
  display: grid;
  grid-template-columns: 68px 1fr 66px;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.stock-label {
  font-size: 11px;
  color: var(--text-muted);
}

.stock-num {
  font-size: 11px;
  text-align: right;
  color: var(--text-secondary);
}

.stock-bar {
  height: 5px;
  border-radius: 3px;
  background: #eef0f4;
  overflow: hidden;
}

.stock-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.35s ease;
}

.stock-fill.db {
  background: linear-gradient(90deg, #3f5bd9, #6d83e6);
}

.stock-fill.redis {
  background: linear-gradient(90deg, #0e9384, #4cc0b2);
}

.product-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.product-actions .el-input-number {
  width: 92px;
}

.steps {
  margin-top: 14px;
  padding-left: 2px;
}

.step {
  display: flex;
  gap: 10px;
  padding: 0 0 14px 0;
  position: relative;
  opacity: 0.55;
}

.step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 4.5px;
  top: 14px;
  bottom: 2px;
  width: 1px;
  background: var(--border);
}

.step.done,
.step.active,
.step.error {
  opacity: 1;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d5d9e2;
  margin-top: 5px;
  flex: 0 0 10px;
  z-index: 1;
}

.step.done .step-dot {
  background: var(--teal);
}

.step.active .step-dot {
  background: var(--primary);
  box-shadow: 0 0 0 3px rgba(63, 91, 217, 0.16);
}

.step.error .step-dot {
  background: var(--danger);
}

.step-title {
  font-size: 12.5px;
  font-weight: 600;
}

.step-desc {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 1px;
  word-break: break-all;
}

.blink {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  animation: blink 0.9s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.2;
  }
}

.stress-controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.stress-result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
}

.stress-bars {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-bottom: 10px;
}

.stress-bar {
  display: grid;
  grid-template-columns: 62px 1fr 32px;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text-secondary);
}

.bar-track {
  height: 7px;
  background: #eef0f4;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
}

.bar-fill.ok {
  background: linear-gradient(90deg, #0e9384, #52c7b8);
}

.bar-fill.sold {
  background: linear-gradient(90deg, #b4740e, #e0a53c);
}

.bar-fill.fail {
  background: linear-gradient(90deg, #c53a2b, #e07a6d);
}

.conclusion {
  margin-top: 8px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
}

@media (max-width: 1180px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
