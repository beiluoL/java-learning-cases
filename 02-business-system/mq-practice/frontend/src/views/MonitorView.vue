<template>
  <div>
    <div class="grid grid-4" style="margin-bottom: 16px">
      <StatCard label="队列总数" :value="queues.length" unit="个" hint="含重试队列与死信队列" tone="blue" />
      <StatCard
        label="总积压"
        :value="totalBacklog"
        unit="条"
        :hint="totalBacklog > 0 ? '注意：延迟队列中的消息属于正常驻留' : '所有队列已清空'"
        :tone="totalBacklog > 50 ? 'amber' : 'default'"
      />
      <StatCard
        label="死信"
        :value="overview?.dlqCount ?? 0"
        unit="条"
        :tone="(overview?.dlqCount ?? 0) > 0 ? 'red' : 'teal'"
        hint="可查看详情并一键回放"
      />
      <StatCard
        label="投递异常"
        :value="(overview?.returnedCount ?? 0) + (overview?.failedCount ?? 0)"
        unit="条"
        hint="RETURNED + FAILED，需关注"
        :tone="(overview?.returnedCount ?? 0) + (overview?.failedCount ?? 0) > 0 ? 'red' : 'teal'"
      />
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
        <!-- ------------------------------ 队列 ------------------------------ -->
        <el-tab-pane label="队列" name="queues">
          <div class="tab-body">
            <el-table :data="queues" v-loading="loading" empty-text="RabbitMQ 未连接">
              <el-table-column label="队列" min-width="250">
                <template #default="{ row }">
                  <span class="mono">{{ row.name }}</span>
                  <div class="sub">{{ row.role }}</div>
                </template>
              </el-table-column>
              <el-table-column label="积压" width="220">
                <template #default="{ row }">
                  <div class="depth-row">
                    <div class="depth-track">
                      <div
                        class="depth-fill"
                        :class="{ hot: row.messageCount > 0 }"
                        :style="{ width: `${Math.min(100, (row.messageCount / maxDepth) * 100)}%` }"
                      ></div>
                    </div>
                    <span class="num">{{ row.messageCount }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="消费者" width="110" align="right">
                <template #default="{ row }">
                  <span class="tag" :class="row.consumerCount > 0 ? 'teal' : ''">
                    {{ row.consumerCount }}
                  </span>
                </template>
              </el-table-column>
            </el-table>

            <div class="legend">
              <span class="tag">延迟队列积压 = 尚未到期的"取消闹钟"</span>
              <span class="tag">重试队列积压 = 正在退避重试的消息</span>
              <span class="tag">无消费者的队列是设计使然（delay / retry）</span>
            </div>
          </div>
        </el-tab-pane>

        <!-- ---------------------------- 消息账本 ---------------------------- -->
        <el-tab-pane label="消息账本" name="messages">
          <div class="tab-body">
            <div class="filters">
              <el-select v-model="messageQuery.scene" placeholder="全部场景" clearable style="width: 190px" @change="loadMessages">
                <el-option v-for="s in scenes" :key="s" :label="s" :value="s" />
              </el-select>
              <el-select v-model="messageQuery.status" placeholder="全部状态" clearable style="width: 150px" @change="loadMessages">
                <el-option label="SENDING" value="SENDING" />
                <el-option label="CONFIRMED" value="CONFIRMED" />
                <el-option label="RETURNED" value="RETURNED" />
                <el-option label="FAILED" value="FAILED" />
              </el-select>
              <el-button @click="loadMessages">
                <el-icon><Refresh /></el-icon>
                <span style="margin-left: 4px">刷新</span>
              </el-button>
            </div>

            <el-table :data="messages" v-loading="messageLoading" empty-text="暂无消息">
              <el-table-column label="时间" width="150">
                <template #default="{ row }"><span class="mono">{{ row.createTime }}</span></template>
              </el-table-column>
              <el-table-column label="场景" width="170">
                <template #default="{ row }"><span class="mono">{{ row.scene }}</span></template>
              </el-table-column>
              <el-table-column label="交换机 / 路由键" min-width="230">
                <template #default="{ row }">
                  <div class="mono">{{ row.exchangeName }}</div>
                  <div class="sub mono">rk={{ row.routingKey }}</div>
                </template>
              </el-table-column>
              <el-table-column label="业务键" min-width="180">
                <template #default="{ row }"><span class="mono">{{ row.bizKey }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="130">
                <template #default="{ row }">
                  <span class="tag" :class="statusClass(row.status)">{{ row.status }}</span>
                  <div v-if="row.errorMsg" class="sub err">{{ row.errorMsg }}</div>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="88" align="right">
                <template #default="{ row }">
                  <span class="num">{{ row.costMs === null ? '—' : `${row.costMs}ms` }}</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="pager">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :total="messageTotal"
                :page-size="messageQuery.size"
                :current-page="messageQuery.page"
                @current-change="onMessagePage"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- ---------------------------- 幂等记录 ---------------------------- -->
        <el-tab-pane label="幂等记录" name="idempotent">
          <div class="tab-body">
            <div class="filters">
              <el-select v-model="idemQuery.scene" placeholder="全部场景" clearable style="width: 190px" @change="loadIdempotent">
                <el-option v-for="s in scenes" :key="s" :label="s" :value="s" />
              </el-select>
              <el-button @click="loadIdempotent">
                <el-icon><Refresh /></el-icon>
                <span style="margin-left: 4px">刷新</span>
              </el-button>
              <span class="panel-hint">
                同一 bizKey 只会成功一次；状态 2=成功 3=失败（失败会放行重试）
              </span>
            </div>

            <el-table :data="idempotentLogs" v-loading="idemLoading" empty-text="暂无记录">
              <el-table-column label="时间" width="150">
                <template #default="{ row }"><span class="mono">{{ row.createTime }}</span></template>
              </el-table-column>
              <el-table-column label="场景" width="180">
                <template #default="{ row }"><span class="mono">{{ row.scene }}</span></template>
              </el-table-column>
              <el-table-column label="幂等键" min-width="280">
                <template #default="{ row }"><span class="mono">{{ row.bizKey }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <span class="tag" :class="idemClass(row.status)">{{ idemText(row.status) }}</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="pager">
              <el-pagination
                background
                layout="prev, pager, next, total"
                :total="idemTotal"
                :page-size="idemQuery.size"
                :current-page="idemQuery.page"
                @current-change="onIdemPage"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- ------------------------------ 死信 ------------------------------ -->
        <el-tab-pane label="死信" name="dlq">
          <div class="tab-body">
            <div class="filters">
              <el-button @click="loadDlq">
                <el-icon><Refresh /></el-icon>
                <span style="margin-left: 4px">查看死信</span>
              </el-button>
              <el-button type="primary" :disabled="dlqList.length === 0" :loading="replaying" @click="handleReplay">
                一键回放
              </el-button>
              <span class="panel-hint">查看使用 basicGet + requeue，不会消费掉消息</span>
            </div>

            <div v-if="dlqList.length === 0" class="empty-state">
              死信队列为空。可在「故障注入」里把某个消费者设为 100% 失败，再触发一次业务，观察消息如何进入死信。
            </div>
            <div v-else class="dlq-list">
              <div v-for="item in dlqList" :key="item.index" class="dlq-card">
                <div class="dlq-head">
                  <span class="tag red">{{ item.reason }}</span>
                  <span class="mono">{{ item.originalExchange }} → {{ item.originalRoutingKey }}</span>
                  <span class="tag">死亡 {{ item.deathCount }} 次</span>
                </div>
                <div class="kv">
                  <span class="k">messageId</span><span class="v">{{ item.messageId }}</span>
                </div>
                <div class="kv">
                  <span class="k">scene / bizKey</span>
                  <span class="v">{{ item.scene }} / {{ item.bizKey }}</span>
                </div>
                <div class="code-block" style="margin-top: 8px">{{ item.payload }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ---------------------------- 故障注入 ---------------------------- -->
        <el-tab-pane label="故障注入" name="chaos">
          <div class="tab-body">
            <div class="chaos-intro">
              打开后消费者会按概率抛异常，可完整观察：
              <b>失败 → 重试队列（5s 退避）→ 3 次后进死信 → 修复后回放</b>。
              生产环境不会存在这类接口，这里是教学开关。
            </div>

            <div class="chaos-grid">
              <div v-for="(cfg, key) in chaosMap" :key="key" class="chaos-card">
                <div class="chaos-head">
                  <span class="mono">{{ key }}</span>
                  <el-switch
                    :model-value="cfg.enabled"
                    size="small"
                    @change="(val: string | number | boolean) => toggleChaos(key, Boolean(val))"
                  />
                </div>
                <div class="chaos-rate">
                  <span class="panel-hint">失败概率</span>
                  <el-slider
                    :model-value="cfg.failRate"
                    :step="10"
                    :max="100"
                    size="small"
                    @change="(val: number | number[]) => setRate(key, val as number)"
                  />
                  <span class="num">{{ cfg.failRate }}%</span>
                </div>
              </div>
            </div>

            <div class="chaos-actions">
              <el-button size="small" :loading="chaosLoading" @click="loadChaos">刷新状态</el-button>
              <el-button size="small" type="danger" plain @click="disableAll">
                全部关闭（恢复健康）
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import StatCard from '@/components/StatCard.vue'
import { useMonitorStore } from '@/store/monitor'
import {
  fetchChaos,
  fetchDlq,
  fetchIdempotentLogs,
  fetchMessages,
  replayDlq,
  setChaos
} from '@/api/monitor'
import type { ChaosConfig, DlqMessage, IdempotentLog, MessageLog } from '@/types'

const store = useMonitorStore()
const { overview, queues, loading } = storeToRefs(store)

const activeTab = ref('queues')
const scenes = ['ORDER_CREATE', 'ORDER_CANCEL', 'PRODUCT_CACHE_SYNC', 'PRODUCT_SEARCH_SYNC', 'PRODUCT_CHANGE']

/* ------------------------------ 队列 ------------------------------ */
const totalBacklog = computed(() => queues.value.reduce((sum, q) => sum + q.messageCount, 0))
const maxDepth = computed(() => Math.max(10, ...queues.value.map((q) => q.messageCount)))

/* ---------------------------- 消息账本 ---------------------------- */
const messages = ref<MessageLog[]>([])
const messageTotal = ref(0)
const messageLoading = ref(false)
const messageQuery = reactive({ scene: '', status: '', page: 1, size: 10 })

const statusClass = (status: string) =>
  status === 'CONFIRMED' ? 'teal' : status === 'RETURNED' ? 'amber' : status === 'FAILED' ? 'red' : 'blue'

async function loadMessages() {
  messageLoading.value = true
  try {
    const data = await fetchMessages({ ...messageQuery })
    messages.value = data.records
    messageTotal.value = data.total
  } finally {
    messageLoading.value = false
  }
}

function onMessagePage(page: number) {
  messageQuery.page = page
  loadMessages()
}

/* ---------------------------- 幂等记录 ---------------------------- */
const idempotentLogs = ref<IdempotentLog[]>([])
const idemTotal = ref(0)
const idemLoading = ref(false)
const idemQuery = reactive({ scene: '', page: 1, size: 10 })

const idemText = (status: number) => (status === 2 ? '成功' : status === 3 ? '失败' : '处理中')
const idemClass = (status: number) => (status === 2 ? 'teal' : status === 3 ? 'red' : 'blue')

async function loadIdempotent() {
  idemLoading.value = true
  try {
    const data = await fetchIdempotentLogs({ ...idemQuery })
    idempotentLogs.value = data.records
    idemTotal.value = data.total
  } finally {
    idemLoading.value = false
  }
}

function onIdemPage(page: number) {
  idemQuery.page = page
  loadIdempotent()
}

/* ------------------------------ 死信 ------------------------------ */
const dlqList = ref<DlqMessage[]>([])
const replaying = ref(false)

async function loadDlq() {
  dlqList.value = await fetchDlq(10)
}

async function handleReplay() {
  replaying.value = true
  try {
    const count = await replayDlq()
    ElMessage.success(`已回放 ${count} 条死信`)
    await Promise.all([loadDlq(), store.loadOverview(true)])
  } finally {
    replaying.value = false
  }
}

/* ---------------------------- 故障注入 ---------------------------- */
const chaosMap = ref<Record<string, ChaosConfig>>({})
const chaosLoading = ref(false)

async function loadChaos() {
  chaosLoading.value = true
  try {
    chaosMap.value = await fetchChaos()
  } finally {
    chaosLoading.value = false
  }
}

async function toggleChaos(scene: string, enabled: boolean) {
  const rate = enabled ? chaosMap.value[scene]?.failRate || 100 : 0
  chaosMap.value = await setChaos(scene, enabled, rate)
  ElMessage.success(`${scene} 故障注入已${enabled ? '开启' : '关闭'}`)
}

async function setRate(scene: string, rate: number) {
  const enabled = rate > 0
  chaosMap.value = await setChaos(scene, enabled, rate)
}

async function disableAll() {
  for (const scene of scenes) {
    await setChaos(scene, false, 0)
  }
  await loadChaos()
  ElMessage.success('所有故障注入已关闭')
}

/* ------------------------------ 生命周期 ------------------------------ */
function onTabChange(name: string) {
  if (name === 'messages') loadMessages()
  if (name === 'idempotent') loadIdempotent()
  if (name === 'dlq') loadDlq()
  if (name === 'chaos') loadChaos()
}

let timer: number | undefined

onMounted(async () => {
  await store.loadOverview()
  timer = window.setInterval(() => store.loadOverview(true), 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.tabs {
  padding: 4px 18px 18px;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tab-body {
  padding-top: 4px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 1px;
}

.sub.err {
  color: var(--danger);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.depth-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.depth-track {
  flex: 1;
  max-width: 140px;
  height: 6px;
  background: #eef0f4;
  border-radius: 3px;
  overflow: hidden;
}

.depth-fill {
  height: 100%;
  width: 0;
  background: linear-gradient(90deg, #0e9384, #52c7b8);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.depth-fill.hot {
  background: linear-gradient(90deg, #b4740e, #e0a53c);
}

.legend {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.dlq-list {
  display: grid;
  gap: 10px;
}

.dlq-card {
  border: 1px solid #f3d8d5;
  background: #fffbfa;
  border-radius: var(--radius);
  padding: 12px 14px;
}

.dlq-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.dlq-head .mono {
  font-size: 11px;
  color: var(--text-secondary);
}

.chaos-intro {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 13px;
  line-height: 1.8;
  margin-bottom: 14px;
}

.chaos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.chaos-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
  background: var(--surface);
}

.chaos-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.chaos-head .mono {
  font-size: 11.5px;
  font-weight: 600;
}

.chaos-rate {
  display: grid;
  grid-template-columns: 56px 1fr 40px;
  align-items: center;
  gap: 8px;
}

.chaos-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
</style>
