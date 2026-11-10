<template>
  <div>
    <div class="status-panel">
      <div class="status-left">
        <div class="status-title">
          <span class="dot" :class="status?.ok ? 'ok' : 'bad'"></span>
          <span v-if="status?.ok">RabbitMQ {{ status.version }}</span>
          <span v-else>RabbitMQ 未连接</span>
        </div>
        <div class="status-sub">
          <template v-if="status?.ok">
            集群 {{ status.clusterName }} · Erlang {{ status.erlangVersion }} · vhost
            <code class="mono">{{ status.vhost }}</code> · 账号
            <code class="mono">{{ status.username }}</code>
          </template>
          <template v-else>{{ status?.message || '正在检测…' }}</template>
        </div>
      </div>

      <div class="status-metrics">
        <div class="metric">
          <div class="metric-value num">{{ status?.queueCount ?? 0 }}</div>
          <div class="metric-label">队列</div>
        </div>
        <div class="metric">
          <div class="metric-value num">{{ status?.consumerCount ?? 0 }}</div>
          <div class="metric-label">消费者</div>
        </div>
        <div class="metric">
          <div class="metric-value num">{{ status?.connectionCount ?? 0 }}</div>
          <div class="metric-label">连接</div>
        </div>
        <div class="metric">
          <div class="metric-value num" :class="{ warn: (status?.readyMessages ?? 0) > 0 }">
            {{ status?.readyMessages ?? 0 }}
          </div>
          <div class="metric-label">就绪消息</div>
        </div>
      </div>

      <div class="status-actions">
        <el-input
          v-model="token"
          type="password"
          size="small"
          placeholder="运维口令（写操作需要）"
          show-password
          style="width: 190px"
          @change="saveToken"
        />
        <el-button size="small" :loading="loading" @click="refreshAll">
          <el-icon><Refresh /></el-icon>
          <span style="margin-left: 4px">刷新</span>
        </el-button>
        <el-button size="small" type="primary" plain @click="openConsole">原生控制台 ↗</el-button>
      </div>
    </div>

    <div v-if="status && !status.ok" class="offline">
      <div class="offline-title">无法访问 RabbitMQ 管理接口</div>
      <div class="offline-desc">{{ status.message }}</div>
      <ul class="offline-list">
        <li>确认 broker 已启动：<code class="mono">docker compose up -d rabbitmq</code></li>
        <li>确认镜像带 management 插件：<code class="mono">rabbitmq:3.13-management</code></li>
        <li>确认后端配置的地址可访问：<code class="mono">{{ status.baseUrl }}</code></li>
        <li>确认账号密码正确：<code class="mono">mq.practice.admin.username/password</code></li>
      </ul>
    </div>

    <div class="panel">
      <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
        <!-- ------------------------------ 队列 ------------------------------ -->
        <el-tab-pane label="队列" name="queues">
          <div class="tab-body">
            <div class="hint-line">
              <span class="tag blue">只读优先</span>
              这里的「查看」使用 <code class="mono">ack_requeue_true</code>：消息被读出后立刻放回，
              <b>不会消费掉</b>。清空队列是写操作，需要上面的运维口令。
            </div>
            <el-table
              :data="queues"
              v-loading="tabLoading"
              empty-text="暂无队列（或 broker 未连接）"
              @row-click="openPeek"
            >
              <el-table-column label="队列" min-width="270">
                <template #default="{ row }">
                  <div class="mono name">{{ row.name }}</div>
                  <div class="sub">
                    <span class="tag" :class="row.managedByProject ? 'violet' : ''">{{ row.type }}</span>
                    <span v-if="row.messageTtlMs" class="tag">TTL {{ row.messageTtlMs }}ms</span>
                    <span v-if="row.arguments['x-dead-letter-exchange']" class="tag">DLX</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="92">
                <template #default="{ row }">
                  <span class="tag" :class="row.state === 'running' ? 'teal' : ''">{{ row.state }}</span>
                </template>
              </el-table-column>

              <el-table-column label="就绪" width="88" align="right">
                <template #default="{ row }">
                  <span class="num" :class="{ hot: row.ready > 0 }">{{ row.ready }}</span>
                </template>
              </el-table-column>

              <el-table-column label="未确认" width="94" align="right">
                <template #default="{ row }">
                  <span class="num" :class="{ warn: row.unacked > 0 }">{{ row.unacked }}</span>
                </template>
              </el-table-column>

              <el-table-column label="消费者" width="86" align="right">
                <template #default="{ row }">
                  <span class="tag" :class="row.consumers > 0 ? 'teal' : ''">{{ row.consumers }}</span>
                </template>
              </el-table-column>

              <el-table-column label="速率 发/投" width="126" align="right">
                <template #default="{ row }">
                  <span class="num rate">{{ row.publishRate.toFixed(1) }} / {{ row.deliverRate.toFixed(1) }}</span>
                  <div v-if="row.redeliverRate > 0" class="sub err">重投 {{ row.redeliverRate.toFixed(1) }}/s</div>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="150" align="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click.stop="openPeek(row)">查看</el-button>
                  <el-tooltip
                    :disabled="row.managedByProject"
                    content="只允许清空本项目声明的队列（mq. 前缀）"
                    placement="top"
                  >
                    <span>
                      <el-button
                        link
                        type="danger"
                        size="small"
                        :disabled="!row.managedByProject"
                        @click.stop="handlePurge(row)"
                      >
                        清空
                      </el-button>
                    </span>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ---------------------------- 交换机 ---------------------------- -->
        <el-tab-pane label="交换机" name="exchanges">
          <div class="tab-body">
            <el-table :data="exchanges" v-loading="tabLoading" empty-text="暂无交换机">
              <el-table-column label="交换机" min-width="260">
                <template #default="{ row }">
                  <span class="mono">{{ row.name || '(默认交换机)' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="140">
                <template #default="{ row }">
                  <span class="tag blue">{{ row.type }}</span>
                </template>
              </el-table-column>
              <el-table-column label="持久化" width="90">
                <template #default="{ row }">{{ row.durable ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="入 / 出 速率" width="150" align="right">
                <template #default="{ row }">
                  <span class="num">{{ row.publishInRate.toFixed(1) }} / {{ row.publishOutRate.toFixed(1) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="参数" min-width="180">
                <template #default="{ row }">
                  <span class="mono sub">{{ argsText(row.arguments) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ------------------------------ 绑定 ------------------------------ -->
        <el-tab-pane label="绑定" name="bindings">
          <div class="tab-body">
            <div class="hint-line">
              <span class="tag amber">排查重点</span>
              「消息发出去了但没人收到」十有八九是这里：routingKey 与绑定对不上，或队列根本没绑。
            </div>
            <el-table :data="bindings" v-loading="tabLoading" empty-text="暂无绑定">
              <el-table-column label="源交换机" min-width="200">
                <template #default="{ row }">
                  <span class="mono">{{ row.source || '(默认)' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="路由键" min-width="180">
                <template #default="{ row }">
                  <span class="mono rk">{{ row.routingKey || '(空)' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="目标" min-width="230">
                <template #default="{ row }">
                  <span class="tag">{{ row.destinationType }}</span>
                  <span class="mono" style="margin-left: 6px">{{ row.destination }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ----------------------------- 消费者 ----------------------------- -->
        <el-tab-pane label="消费者" name="consumers">
          <div class="tab-body">
            <div class="hint-line">
              <span class="tag teal">关键字段</span>
              <code class="mono">prefetch</code>（预取条数）、<code class="mono">ackRequired</code>（是否手动 ACK）、
              <code class="mono">activity</code>（down 说明消费线程卡住）。
            </div>
            <el-table :data="consumers" v-loading="tabLoading" empty-text="暂无消费者">
              <el-table-column label="队列" min-width="240">
                <template #default="{ row }"><span class="mono">{{ row.queue }}</span></template>
              </el-table-column>
              <el-table-column label="consumerTag" min-width="200">
                <template #default="{ row }"><span class="mono sub">{{ row.consumerTag }}</span></template>
              </el-table-column>
              <el-table-column label="预取" width="80" align="right">
                <template #default="{ row }"><span class="num">{{ row.prefetchCount }}</span></template>
              </el-table-column>
              <el-table-column label="手动 ACK" width="100">
                <template #default="{ row }">
                  <span class="tag" :class="row.ackRequired ? 'teal' : ''">{{ row.ackRequired ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="活跃" width="90">
                <template #default="{ row }">
                  <span class="tag" :class="row.activityStatus === 'up' ? 'teal' : 'red'">{{ row.activityStatus }}</span>
                </template>
              </el-table-column>
              <el-table-column label="来源主机" width="130">
                <template #default="{ row }"><span class="mono sub">{{ row.peerHost }}</span></template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ------------------------------ 连接 ------------------------------ -->
        <el-tab-pane label="连接" name="connections">
          <div class="tab-body">
            <el-table :data="connections" v-loading="tabLoading" empty-text="暂无连接">
              <el-table-column label="连接" min-width="240">
                <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
              </el-table-column>
              <el-table-column label="用户" width="100">
                <template #default="{ row }"><span class="mono">{{ row.user }}</span></template>
              </el-table-column>
              <el-table-column label="对端" width="130">
                <template #default="{ row }"><span class="mono sub">{{ row.peerHost }}</span></template>
              </el-table-column>
              <el-table-column label="通道" width="80" align="right">
                <template #default="{ row }"><span class="num">{{ row.channels }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <span class="tag" :class="row.state === 'running' ? 'teal' : ''">{{ row.state }}</span>
                </template>
              </el-table-column>
              <el-table-column label="客户端" min-width="150">
                <template #default="{ row }"><span class="sub mono">{{ row.clientInfo || '—' }}</span></template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- ---------------------------- 投递测试 ---------------------------- -->
        <el-tab-pane label="投递测试" name="publish">
          <div class="tab-body publish-body">
            <div class="hint-line">
              <span class="tag red">写操作</span>
              手动投一条消息到指定交换机，用于验证绑定是否生效（<code class="mono">routed=false</code>
              就说明没有队列匹配该 routingKey）。
            </div>
            <el-form label-width="96px" style="max-width: 720px">
              <el-form-item label="交换机">
                <el-select v-model="publishForm.exchange" filterable style="width: 100%">
                  <el-option
                    v-for="ex in exchanges.filter((e) => e.name)"
                    :key="ex.name"
                    :label="`${ex.name}  (${ex.type})`"
                    :value="ex.name"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="routingKey">
                <el-input v-model="publishForm.routingKey" placeholder="例如 product.changed" />
              </el-form-item>
              <el-form-item label="消息类型">
                <el-select v-model="publishForm.messageType" style="width: 100%">
                  <el-option label="ProductChangeMessage（商品变更）" value="ProductChangeMessage" />
                  <el-option label="OrderCreateMessage（订单创建）" value="OrderCreateMessage" />
                  <el-option label="OrderCancelMessage（订单取消）" value="OrderCancelMessage" />
                  <el-option label="不指定（原始消息，仅用于查看）" value="" />
                </el-select>
                <div class="panel-hint" style="margin-top: 4px; line-height: 1.7">
                  消费端是强类型签名，会按消息头 <code class="mono">__TypeId__</code> 反序列化。
                  不指定类型时，消息能进队列但消费端会拒绝并入死信。
                </div>
              </el-form-item>
              <el-form-item label="消息体">
                <el-input
                  v-model="publishForm.payload"
                  type="textarea"
                  :rows="6"
                  placeholder='{"productId":1,"changeType":"PRICE_CHANGED"}'
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="publishing" @click="handlePublish">投递</el-button>
                <span class="panel-hint" style="margin-left: 10px">
                  投递会写入消息头 <code class="mono">x-mq-scene=MANUAL_PUBLISH</code>，便于在业务日志里区分
                </span>
              </el-form-item>
            </el-form>
            <div v-if="publishResult" class="publish-result" :class="publishResult.routed ? 'ok' : 'bad'">
              {{ publishResult.note }}
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ---------------------------- 消息查看抽屉 ---------------------------- -->
    <el-drawer v-model="peekVisible" size="640px" :title="`查看队列：${peekQueueName}`">
      <template #header>
        <div>
          <div style="font-size: 14px; font-weight: 500">查看队列：{{ peekQueueName }}</div>
          <div class="panel-hint" style="margin-top: 2px">
            只读模式 · 消息读完放回队列 · 共取 {{ peekList.length }} 条
          </div>
        </div>
      </template>

      <div v-loading="peekLoading">
        <div v-if="peekList.length === 0" class="empty-state">队列为空，没有可展示的消息</div>
        <div v-for="msg in peekList" :key="msg.index" class="msg-card">
          <div class="msg-head">
            <span class="tag blue">#{{ msg.index }}</span>
            <span class="mono">{{ msg.exchange || '(默认交换机)' }} → {{ msg.routingKey }}</span>
            <span v-if="msg.redelivered" class="tag amber" title="可能是消费失败重投，也可能是刚才 peek 读出后放回导致">redelivered</span>
            <span class="tag">剩余 {{ msg.messageCount }}</span>
          </div>
          <div class="code-block" style="margin: 8px 0">{{ pretty(msg.payload) }}</div>
          <div v-if="headerEntries(msg.headers).length" class="msg-headers">
            <div v-for="[k, v] in headerEntries(msg.headers)" :key="k" class="kv">
              <span class="k mono">{{ k }}</span>
              <span class="v">{{ formatHeader(v) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  fetchAdminBindings,
  fetchAdminConnections,
  fetchAdminConsumers,
  fetchAdminExchanges,
  fetchAdminQueues,
  fetchAdminStatus,
  getAdminToken,
  peekQueue,
  publishMessage,
  purgeQueue,
  setAdminToken
} from '@/api/admin'
import type {
  AdminBinding,
  AdminConnection,
  AdminConsumer,
  AdminExchange,
  AdminQueue,
  AdminStatus,
  PeekedMessage,
  PublishResult
} from '@/types'

const status = ref<AdminStatus | null>(null)
const activeTab = ref('queues')
const loading = ref(false)
const tabLoading = ref(false)

const queues = ref<AdminQueue[]>([])
const exchanges = ref<AdminExchange[]>([])
const bindings = ref<AdminBinding[]>([])
const consumers = ref<AdminConsumer[]>([])
const connections = ref<AdminConnection[]>([])

const token = ref(getAdminToken())
const peekVisible = ref(false)
const peekLoading = ref(false)
const peekQueueName = ref('')
const peekList = ref<PeekedMessage[]>([])

const publishing = ref(false)
const publishResult = ref<PublishResult | null>(null)
const publishForm = reactive({
  exchange: '',
  routingKey: 'product.changed',
  // 预填一条能真正跑通的示例（商品改价事件），改完直接点投递即可
  payload: JSON.stringify(
    {
      messageId: 'manual-001',
      productId: 6,
      productNo: 'P1006',
      name: 'AirPods Pro 3',
      price: 1899.0,
      status: 1,
      changeType: 'PRICE_CHANGED',
      version: 1,
      changeTime: '2026-11-10 10:00:00'
    },
    null,
    2
  ),
  messageType: 'ProductChangeMessage'
})

let timer: number | undefined

function saveToken() {
  setAdminToken(token.value)
}

async function loadStatus() {
  status.value = await fetchAdminStatus()
}

async function loadQueues() {
  queues.value = await fetchAdminQueues()
}

async function loadExchanges() {
  exchanges.value = await fetchAdminExchanges()
  // 首次进入投递页时选一个合理的默认交换机，避免空下拉
  if (!publishForm.exchange) {
    publishForm.exchange = exchanges.value.find((e) => e.name === 'mq.product.exchange')?.name
      || exchanges.value.find((e) => e.name.startsWith('mq.'))?.name
      || exchanges.value.find((e) => e.name)?.name
      || ''
  }
}

async function loadBindings() {
  bindings.value = await fetchAdminBindings()
}

async function loadConsumers() {
  consumers.value = await fetchAdminConsumers()
}

async function loadConnections() {
  connections.value = await fetchAdminConnections()
}

/** 只刷新当前页签，避免每次轮询都打 6 个接口 */
async function loadActiveTab(silent = false) {
  if (!silent) tabLoading.value = true
  try {
    if (activeTab.value === 'queues') await loadQueues()
    else if (activeTab.value === 'exchanges' || activeTab.value === 'publish') await loadExchanges()
    else if (activeTab.value === 'bindings') await loadBindings()
    else if (activeTab.value === 'consumers') await loadConsumers()
    else if (activeTab.value === 'connections') await loadConnections()
  } catch {
    /* 错误提示由 axios 拦截器统一处理 */
  } finally {
    tabLoading.value = false
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await loadStatus()
    if (status.value?.ok) await loadActiveTab()
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  loadActiveTab()
}

function openConsole() {
  const url = status.value?.consoleUrl || 'http://localhost:15672'
  window.open(url, '_blank', 'noopener')
}

async function openPeek(row: AdminQueue) {
  if (!row?.name) return
  peekQueueName.value = row.name
  peekVisible.value = true
  peekLoading.value = true
  try {
    peekList.value = (await peekQueue(row.name, 10)) || []
  } catch {
    peekList.value = []
  } finally {
    peekLoading.value = false
  }
}

async function handlePurge(row: AdminQueue) {
  if (!token.value) {
    ElMessage.warning('请先在右上角填写运维口令')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认清空队列 ${row.name}？当前有 ${row.messages} 条消息，清空后不可恢复。`,
      '清空队列',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const purged = await purgeQueue(row.name)
    ElMessage.success(`已清空 ${purged} 条消息`)
    await loadQueues()
  } catch {
    /* ignore */
  }
}

async function handlePublish() {
  if (!publishForm.exchange || !publishForm.routingKey || !publishForm.payload) {
    ElMessage.warning('交换机、routingKey、消息体都不能为空')
    return
  }
  if (!token.value) {
    ElMessage.warning('请先在右上角填写运维口令')
    return
  }
  publishing.value = true
  try {
    const result = await publishMessage({ ...publishForm, source: 'mq-console' })
    publishResult.value = result
    if (result.routed) {
      ElMessage.success('已成功路由到队列')
      await loadQueues()
    } else {
      ElMessage.warning('消息到达交换机，但没有队列匹配该 routingKey')
    }
  } catch {
    /* ignore */
  } finally {
    publishing.value = false
  }
}

function pretty(payload: string) {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

/** 头部只展示排查时有用的键，避免一屏全是 content_type 之类的噪音 */
function headerEntries(headers: Record<string, unknown>): [string, unknown][] {
  if (!headers) return []
  const keep = ['x-death', 'x-mq-scene', 'x-mq-biz-key', 'x-mq-message-id', 'x-mq-retry-count', 'x-delay', 'x-message-ttl']
  return Object.entries(headers).filter(([k]) => keep.includes(k))
}

function formatHeader(value: unknown) {
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function argsText(args: Record<string, unknown>) {
  const entries = Object.entries(args || {})
  if (entries.length === 0) return '—'
  return entries.map(([k, v]) => `${k}=${v}`).join(' · ')
}

onMounted(async () => {
  await refreshAll()
  // 5 秒轮询：排队/消费类数据变化快，静止的页面没有诊断价值
  timer = window.setInterval(() => {
    loadStatus().catch(() => undefined)
    if (status.value?.ok) loadActiveTab(true).catch(() => undefined)
  }, 5000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.status-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 18px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.status-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.dot.ok {
  background: var(--teal);
}

.dot.bad {
  background: var(--danger);
}

.status-sub {
  font-size: 11.5px;
  color: var(--text-muted);
  margin-top: 3px;
}

.status-sub code {
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
}

.status-metrics {
  display: flex;
  gap: 26px;
}

.metric-value {
  font-size: 20px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--primary);
}

.metric-value.warn {
  color: var(--amber);
}

.metric-label {
  font-size: 11px;
  color: var(--text-muted);
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.offline {
  background: var(--danger-soft);
  border: 1px solid #f6cfcb;
  border-radius: var(--radius-lg);
  padding: 14px 18px;
  margin-bottom: 16px;
}

.offline-title {
  font-size: 13.5px;
  font-weight: 650;
  color: var(--danger);
}

.offline-desc {
  font-size: 12px;
  color: var(--danger);
  margin-top: 4px;
}

.offline-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 11.5px;
  line-height: 1.9;
  color: var(--text-secondary);
}

.offline-list code {
  background: #fff;
  border: 1px solid #f0d5d2;
  border-radius: 4px;
  padding: 0 4px;
}

.tabs {
  padding: 4px 18px 18px;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.tab-body {
  padding-top: 4px;
}

.hint-line {
  font-size: 11.5px;
  color: var(--text-secondary);
  line-height: 1.8;
  background: var(--surface-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 9px 12px;
  margin-bottom: 12px;
}

.hint-line code {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
}

.name {
  font-size: 12px;
  font-weight: 600;
}

.sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 2px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.sub.err {
  color: var(--danger);
}

.num.hot {
  color: var(--danger);
  font-weight: 650;
}

.num.warn {
  color: var(--amber);
  font-weight: 650;
}

.rate {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.rk {
  color: var(--violet);
}

.msg-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 12px;
  margin-bottom: 10px;
  background: var(--surface);
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.msg-head .mono {
  font-size: 11px;
  color: var(--text-secondary);
}

.msg-headers {
  background: var(--surface-soft);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
}

.msg-headers :deep(.kv) {
  justify-content: flex-start;
  gap: 14px;
}

.msg-headers :deep(.kv .k) {
  min-width: 118px;
}

.msg-headers :deep(.kv .v) {
  text-align: left;
}

.publish-result {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: var(--radius);
  font-size: 12px;
}

.publish-result.ok {
  background: var(--teal-soft);
  border: 1px solid #c8ebe6;
  color: var(--teal);
}

.publish-result.bad {
  background: var(--amber-soft);
  border: 1px solid #f0e0c2;
  color: var(--amber);
}

@media (max-width: 1100px) {
  .status-metrics {
    gap: 16px;
  }
}
</style>
