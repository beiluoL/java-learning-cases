<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { usageApi } from '@/api/usage'
import type { UsageQuery, UsageRecord } from '@/api/types'
import { useResource } from '@/composables/useAsyncData'
import { useTimeRangeStore } from '@/stores/timeRange'
import { PAGE_SIZE_OPTIONS, USAGE_STATUS_OPTIONS, requestStatusTone, usageStatusTone } from '@/constants'
import { formatCount, formatDateTime, formatMoney, formatToken } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import FilterBar from '@/components/FilterBar.vue'
import DataTable from '@/components/DataTable.vue'
import Modal from '@/components/Modal.vue'
import AsyncState from '@/components/AsyncState.vue'

interface KvItem { label: string; value: string }

const store = useTimeRangeStore()

const filters = reactive({
  userId: '',
  modelName: '',
  businessScene: '',
  requestStatus: '',
  usageStatus: '',
  minCost: '',
  maxCost: '',
})

const page = ref(1)
const size = ref(20)
const sort = ref('createdAt,DESC')

function buildQuery(): UsageQuery {
  return {
    page: page.value,
    size: size.value,
    sort: sort.value,
    userId: filters.userId || undefined,
    modelName: filters.modelName || undefined,
    businessScene: filters.businessScene || undefined,
    requestStatus: filters.requestStatus || undefined,
    usageStatus: filters.usageStatus || undefined,
    minCost: filters.minCost || undefined,
    maxCost: filters.maxCost || undefined,
    startTime: store.range.startTime,
    endTime: store.range.endTime,
  }
}

const list = useResource(() => usageApi.page(buildQuery()))

// 时间筛选变化（或首次进入）时，重置到第 1 页并以新的 startTime/endTime 重新取数
watch(() => store.revision, () => {
  page.value = 1
  list.execute()
}, { immediate: true })

const records = computed(() => list.data?.records ?? [])
const total = computed(() => list.data?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const listEmpty = computed(() => !list.loading && !list.error && records.value.length === 0)

const columns: TableColumn[] = [
  { key: 'createdAt', title: '时间', width: '168px', sortable: true, render: (v) => formatDateTime(v) },
  { key: 'userId', title: '用户', width: '130px' },
  { key: 'modelName', title: '模型', width: '170px' },
  { key: 'businessScene', title: '场景', width: '120px' },
  { key: 'provider', title: 'Provider', width: '110px' },
  { key: 'inputTokens', title: '输入', align: 'right', sortable: true, render: (v) => formatToken(v) },
  { key: 'outputTokens', title: '输出', align: 'right', sortable: true, render: (v) => formatToken(v) },
  { key: 'totalTokens', title: '总 Token', align: 'right', sortable: true, render: (v) => formatToken(v) },
  { key: 'totalCost', title: '成本 (USD)', align: 'right', sortable: true, render: (v) => formatMoney(v) },
  { key: 'latencyMs', title: '耗时', align: 'right', sortable: true, render: (v) => `${formatCount(v)} ms` },
  { key: 'requestStatus', title: '状态', width: '110px' },
  { key: 'usageStatus', title: 'Usage', width: '120px' },
  { key: 'pricingVersion', title: '价格版本', width: '120px' },
  { key: 'traceId', title: 'traceId', width: '150px' },
]

function onSearch() {
  page.value = 1
  list.execute()
}

function onReset() {
  Object.assign(filters, {
    userId: '', modelName: '', businessScene: '', requestStatus: '',
    usageStatus: '', minCost: '', maxCost: '',
  })
  sort.value = 'createdAt,DESC'
  page.value = 1
  list.execute()
}

function onSort(key: string) {
  const [field, dir] = sort.value.split(',')
  sort.value = `${key},${field === key && dir === 'DESC' ? 'ASC' : 'DESC'}`
  page.value = 1
  list.execute()
}

function onSizeChange() {
  page.value = 1
  list.execute()
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  list.execute()
}

/* ---------------- 详情抽屉 ---------------- */
const drawerOpen = ref(false)
const selectedId = ref('')
const detail = useResource(() => usageApi.detail(selectedId.value))

function openDetail(row: unknown) {
  const record = row as UsageRecord
  selectedId.value = record.requestId
  drawerOpen.value = true
  detail.execute()
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const baseItems = computed<KvItem[]>(() => {
  const d = detail.data
  if (!d) return []
  return [
    { label: 'requestId', value: d.requestId },
    { label: 'traceId', value: d.traceId },
    { label: 'userId', value: d.userId },
    { label: 'conversationId', value: d.conversationId },
    { label: 'businessScene', value: d.businessScene },
    { label: 'provider / modelName', value: `${d.provider} / ${d.modelName}` },
    { label: 'modelType', value: d.modelType },
    { label: 'requestStatus', value: d.requestStatus },
    { label: 'usageStatus', value: d.usageStatus },
    { label: 'currency', value: d.currency },
    { label: 'pricingVersion', value: d.pricingVersion },
    { label: 'latencyMs', value: `${formatCount(d.latencyMs)} ms` },
    { label: 'totalCost', value: formatMoney(d.totalCost) },
    { label: 'createdAt', value: formatDateTime(d.createdAt) },
  ]
})

const usageItems = computed<KvItem[]>(() => {
  const d = detail.data
  if (!d) return []
  return [
    { label: 'inputTokens · 输入', value: formatToken(d.inputTokens) },
    { label: 'outputTokens · 输出', value: formatToken(d.outputTokens) },
    { label: 'cachedInputTokens · 缓存输入', value: formatToken(d.cachedInputTokens) },
    { label: 'totalTokens · 合计', value: formatToken(d.totalTokens) },
  ]
})

const breakdownItems = computed<KvItem[]>(() => {
  const b = detail.data?.costBreakdown
  if (!b) return []
  return [
    { label: 'pricingVersion · 价格版本', value: b.pricingVersion },
    { label: 'inputPrice · 输入单价', value: formatMoney(b.inputPrice) },
    { label: 'outputPrice · 输出单价', value: formatMoney(b.outputPrice) },
    { label: 'cachedInputPrice · 缓存单价', value: formatMoney(b.cachedInputPrice) },
    { label: 'normalInputTokens', value: formatToken(b.normalInputTokens) },
    { label: 'cachedInputTokens', value: formatToken(b.cachedInputTokens) },
    { label: 'outputTokens', value: formatToken(b.outputTokens) },
    { label: 'inputCost · 输入成本', value: formatMoney(b.inputCost) },
    { label: 'cachedCost · 缓存成本', value: formatMoney(b.cachedCost) },
    { label: 'outputCost · 输出成本', value: formatMoney(b.outputCost) },
    { label: 'totalCost · 合计成本', value: formatMoney(b.totalCost) },
  ]
})

const budgetItems = computed<KvItem[]>(() => {
  const b = detail.data?.budget
  if (!b) return []
  return Object.entries(b).map(([key, value]) => ({ label: key, value: stringify(value) }))
})
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Request Tracing"
      title="请求追踪"
      description="调用明细来自 /api/admin/token-usage/page；点击任意一行查看 Usage 状态、成本计算过程、价格版本、预算预扣与 traceId，可一路回溯到账务流水。"
    >
      <template #actions><TimeRangePicker /></template>
    </PageHeader>

    <FilterBar title="筛选条件" description="时间范围在顶部切换；其余条件填写后点击「查询」">
      <label class="field">
        <span class="field__label">用户 ID</span>
        <input v-model="filters.userId" class="input" placeholder="userId" />
      </label>
      <label class="field">
        <span class="field__label">模型</span>
        <input v-model="filters.modelName" class="input" placeholder="modelName" />
      </label>
      <label class="field">
        <span class="field__label">业务场景</span>
        <input v-model="filters.businessScene" class="input" placeholder="businessScene" />
      </label>
      <label class="field">
        <span class="field__label">请求状态</span>
        <input v-model="filters.requestStatus" class="input" placeholder="requestStatus" />
      </label>
      <label class="field">
        <span class="field__label">Usage 类型</span>
        <select v-model="filters.usageStatus" class="select">
          <option value="">全部</option>
          <option v-for="o in USAGE_STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">最小成本</span>
        <input v-model="filters.minCost" class="input" placeholder="minCost" />
      </label>
      <label class="field">
        <span class="field__label">最大成本</span>
        <input v-model="filters.maxCost" class="input" placeholder="maxCost" />
      </label>
      <template #actions>
        <button class="btn btn--primary" type="button" @click="onSearch">查询</button>
        <button class="btn" type="button" @click="onReset">重置</button>
      </template>
    </FilterBar>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">调用明细</h3>
          <p class="panel__subtitle">排序字段：{{ sort }} · 点击可排序列头切换升降序</p>
        </div>
      </header>
      <DataTable
        :columns="columns" :rows="records" row-key="requestId"
        :loading="list.loading" :error="list.error" :error-trace-id="list.errorTraceId"
        :empty="listEmpty" :active-sort="sort" clickable
        @retry="list.execute" @row-click="openDetail" @sort="onSort"
      >
        <template #cell-requestStatus="{ value }">
          <span class="badge" :class="'badge--' + requestStatusTone(String(value ?? ''))">{{ value ?? '—' }}</span>
        </template>
        <template #cell-usageStatus="{ value }">
          <span class="badge" :class="'badge--' + usageStatusTone(String(value ?? ''))">{{ value ?? '—' }}</span>
        </template>

        <template #footer>
          <div class="pagination">
            <div class="row">
              <span class="pagination__info">共 {{ formatCount(total) }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
              <select v-model.number="size" class="select page-size" aria-label="每页条数" @change="onSizeChange">
                <option v-for="s in PAGE_SIZE_OPTIONS" :key="s" :value="s">{{ s }} 条/页</option>
              </select>
            </div>
            <div class="pagination__controls">
              <button class="btn btn--sm" type="button" :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
              <span class="pagination__page mono">{{ page }}</span>
              <button class="btn btn--sm" type="button" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
            </div>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 详情抽屉 -->
    <Modal
      v-model="drawerOpen" title="调用明细" :subtitle="selectedId"
      position="right" width="600px"
    >
      <AsyncState
        :loading="detail.loading" :error="detail.error" :error-trace-id="detail.errorTraceId"
        :empty="!detail.loading && !detail.error && !detail.data"
        min-height="220px" @retry="detail.execute"
      >
        <div class="stack detail">
          <section class="detail-section">
            <h4 class="detail-section__title">基础信息</h4>
            <dl class="kv">
              <div v-for="item in baseItems" :key="item.label" class="kv__item">
                <dt>{{ item.label }}</dt><dd class="mono">{{ item.value }}</dd>
              </div>
            </dl>
          </section>

          <section class="detail-section">
            <h4 class="detail-section__title">Usage · Token 用量</h4>
            <dl class="kv">
              <div v-for="item in usageItems" :key="item.label" class="kv__item">
                <dt>{{ item.label }}</dt><dd class="mono">{{ item.value }}</dd>
              </div>
            </dl>
          </section>

          <section v-if="breakdownItems.length" class="detail-section">
            <h4 class="detail-section__title">成本计算过程 · costBreakdown</h4>
            <p class="formula mono">
              inputCost = normalInputTokens × inputPrice<br />
              cachedCost = cachedInputTokens × cachedInputPrice<br />
              outputCost = outputTokens × outputPrice<br />
              totalCost = inputCost + cachedCost + outputCost
            </p>
            <dl class="kv">
              <div v-for="item in breakdownItems" :key="item.label" class="kv__item">
                <dt>{{ item.label }}</dt><dd class="mono">{{ item.value }}</dd>
              </div>
            </dl>
          </section>
          <p v-else class="text-muted">该明细未返回 costBreakdown（以后端实际返回为准）。</p>

          <section v-if="budgetItems.length" class="detail-section">
            <h4 class="detail-section__title">预算预扣 / 结算状态</h4>
            <dl class="kv">
              <div v-for="item in budgetItems" :key="item.label" class="kv__item">
                <dt>{{ item.label }}</dt><dd class="mono">{{ item.value }}</dd>
              </div>
            </dl>
          </section>
        </div>
      </AsyncState>
      <template #footer>
        <button class="btn" type="button" @click="drawerOpen = false">关闭</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.page-size { width: 110px; }
.detail { gap: var(--space-6); }
.detail-section__title {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
}
.kv { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; }
.kv__item { min-width: 0; }
.kv__item dt { font-size: var(--font-xs); color: var(--color-text-muted); }
.kv__item dd { margin: 3px 0 0; font-size: var(--font-base); word-break: break-all; }
.formula {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  font-size: var(--font-sm);
  line-height: 1.9;
  color: var(--color-text-secondary);
}
@media (max-width: 520px) {
  .kv { grid-template-columns: 1fr; }
}
</style>
