<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { governanceApi } from '@/api/governance'
import type { AnomalyFinding, CostAlertRow, ThresholdHit } from '@/api/types'
import { toErrorMessage, useResource } from '@/composables/useAsyncData'
import {
  ALERT_LEVEL_OPTIONS,
  ALERT_STATUS_OPTIONS,
  ALERT_TARGET_TYPE_OPTIONS,
  ALERT_TYPE_OPTIONS,
  PAGE_SIZE_OPTIONS,
  alertLevelTone,
  alertStatusTone,
} from '@/constants'
import { formatCount, formatDateTime } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import StatCard from '@/components/StatCard.vue'
import DataTable from '@/components/DataTable.vue'

const filters = reactive({
  alertType: '',
  alertLevel: '',
  status: '',
  targetType: '',
  days: 7,
})

const page = ref(1)
const size = ref(20)

function buildQuery(status?: string) {
  return {
    page: page.value,
    size: size.value,
    alertType: filters.alertType || undefined,
    alertLevel: filters.alertLevel || undefined,
    status: status ?? (filters.status || undefined),
    targetType: filters.targetType || undefined,
    days: filters.days,
  }
}

const list = useResource(() => governanceApi.alerts(buildQuery()))

/**
 * 三个状态分别取一次总数（size=1 只为了拿 total）。
 * 告警中心没有聚合计数接口，这样比拉全量再前端统计更贴近真实数据。
 */
const openCount = useResource(() =>
  governanceApi.alerts({ page: 1, size: 1, status: 'OPEN', days: filters.days }),
)
const ackCount = useResource(() =>
  governanceApi.alerts({ page: 1, size: 1, status: 'ACKNOWLEDGED', days: filters.days }),
)
const resolvedCount = useResource(() =>
  governanceApi.alerts({ page: 1, size: 1, status: 'RESOLVED', days: filters.days }),
)
const criticalOpen = useResource(() =>
  governanceApi.alerts({
    page: 1,
    size: 1,
    status: 'OPEN',
    alertLevel: 'CRITICAL',
    days: filters.days,
  }),
)

function reloadAll() {
  list.execute()
  openCount.execute()
  ackCount.execute()
  resolvedCount.execute()
  criticalOpen.execute()
}

onMounted(reloadAll)

const records = computed(() => list.data?.records ?? [])
const total = computed(() => list.data?.total ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const columns: TableColumn[] = [
  { key: 'createdAt', title: '时间', width: '168px', render: (v) => formatDateTime(v) },
  { key: 'alertLevel', title: '等级', width: '110px' },
  { key: 'alertType', title: '类型', width: '200px' },
  { key: 'targetType', title: '对象类型', width: '110px' },
  { key: 'targetId', title: '对象', width: '140px' },
  { key: 'message', title: '内容' },
  {
    key: 'occurrenceCount',
    title: '次数',
    align: 'right',
    width: '80px',
    render: (v) => formatCount(v),
  },
  { key: 'status', title: '状态', width: '130px' },
  { key: 'action', title: '操作', width: '150px', align: 'right' },
]

function onSearch() {
  page.value = 1
  reloadAll()
}

function onReset() {
  Object.assign(filters, { alertType: '', alertLevel: '', status: '', targetType: '', days: 7 })
  page.value = 1
  reloadAll()
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  list.execute()
}

function onSizeChange() {
  page.value = 1
  list.execute()
}

/* ---------------- 行操作 ---------------- */

const acting = ref<number | null>(null)
const actionError = ref<string | null>(null)

async function acknowledge(row: unknown) {
  await act(row as CostAlertRow, () => governanceApi.acknowledgeAlert((row as CostAlertRow).id))
}

async function resolve(row: unknown) {
  await act(row as CostAlertRow, () => governanceApi.resolveAlert((row as CostAlertRow).id))
}

async function act(row: CostAlertRow, fn: () => Promise<unknown>) {
  acting.value = row.id
  actionError.value = null
  try {
    await fn()
    reloadAll()
  } catch (err) {
    actionError.value = toErrorMessage(err)
  } finally {
    acting.value = null
  }
}

/* ---------------- 手动触发检测 ---------------- */

const thresholdHits = ref<ThresholdHit[] | null>(null)
const findings = ref<AnomalyFinding[] | null>(null)
const scanLoading = ref(false)
const scanError = ref<string | null>(null)

async function runThresholdEvaluate() {
  scanLoading.value = true
  scanError.value = null
  try {
    const result = await governanceApi.evaluateThresholds()
    thresholdHits.value = result.thresholdHits
    reloadAll()
  } catch (err) {
    scanError.value = toErrorMessage(err)
  } finally {
    scanLoading.value = false
  }
}

async function runAnomalyScan() {
  scanLoading.value = true
  scanError.value = null
  try {
    const result = await governanceApi.anomalyScan()
    findings.value = result.findings
    reloadAll()
  } catch (err) {
    scanError.value = toErrorMessage(err)
  } finally {
    scanLoading.value = false
  }
}
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Alert Center"
      title="预警中心"
      description="预算阈值越线（50/70/85/95/100%）、消费速度预测、异常行为全部汇入这里。同一「类型 + 对象 + 等级 + 阈值」只保留一条未恢复告警，重复触发只累加次数。"
    >
      <template #actions>
        <button class="btn" :disabled="scanLoading" @click="runThresholdEvaluate">
          {{ scanLoading ? '执行中…' : '手动阈值评估' }}
        </button>
        <button class="btn" :disabled="scanLoading" @click="runAnomalyScan">
          {{ scanLoading ? '执行中…' : '异常检测扫描' }}
        </button>
      </template>
    </PageHeader>

    <p v-if="scanError" class="scan-error">{{ scanError }}</p>

    <div v-if="thresholdHits" class="panel scan-result">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">阈值评估结果</h3>
          <p class="panel__subtitle">
            POST /cost-alert/evaluate · 仅写/恢复告警，不改动任何业务数据
          </p>
        </div>
        <button class="btn btn--sm btn--ghost" @click="thresholdHits = null">关闭</button>
      </header>
      <div class="panel__body">
        <p v-if="thresholdHits.length === 0" class="text-muted">
          所有预算作用域均未越线，无需产生新告警。
        </p>
        <ul v-else class="scan-list">
          <li v-for="hit in thresholdHits" :key="hit.budgetType + hit.targetId + hit.threshold">
            <span class="badge" :class="hit.raised ? 'badge--danger' : 'badge--success'">
              {{ hit.raised ? '越线' : '恢复' }}
            </span>
            <span class="mono">{{ hit.budgetType }}/{{ hit.targetId }}</span>
            阈值 <strong class="mono">{{ hit.threshold }}</strong
            >， 当前 <strong class="mono">{{ hit.usageRate }}</strong
            >， 等级 <span class="mono">{{ hit.alertLevel }}</span> · 动作
            <span class="mono">{{ hit.action }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div v-if="findings" class="panel scan-result">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">异常检测结果</h3>
          <p class="panel__subtitle">
            POST /cost-alert/anomaly-scan · 6 条可解释规则，全部基于「今日 vs 近 7 日基线」
          </p>
        </div>
        <button class="btn btn--sm btn--ghost" @click="findings = null">关闭</button>
      </header>
      <div class="panel__body">
        <p v-if="findings.length === 0" class="text-muted">
          未命中任何规则。基线样本不足时规则保持静默，这是预期行为（不会为了「有输出」而虚报）。
        </p>
        <ul v-else class="scan-list">
          <li v-for="f in findings" :key="f.rule + f.targetId">
            <span class="badge" :class="'badge--' + alertLevelTone(f.level)">{{ f.level }}</span>
            <span class="mono">{{ f.rule }}</span> · {{ f.message }}
            <span class="text-muted"
              >（当前 {{ f.currentValue }} / 基线 {{ f.baselineValue }}）</span
            >
          </li>
        </ul>
      </div>
    </div>

    <div class="stat-grid">
      <StatCard
        label="未处理 (OPEN)"
        :value="formatCount(openCount.data?.total)"
        sub="需要人工确认或等待恢复"
        tone="danger"
        :loading="openCount.loading"
      />
      <StatCard
        label="已确认 (ACKNOWLEDGED)"
        :value="formatCount(ackCount.data?.total)"
        sub="已知悉，尚未恢复"
        tone="warning"
        :loading="ackCount.loading"
      />
      <StatCard
        label="已恢复 (RESOLVED)"
        :value="formatCount(resolvedCount.data?.total)"
        sub="越线回落或人工关闭"
        tone="success"
        :loading="resolvedCount.loading"
      />
      <StatCard
        label="未恢复严重告警"
        :value="formatCount(criticalOpen.data?.total)"
        sub="存在 CRITICAL 未恢复时保护等级至少为 SEVERE"
        tone="danger"
        :loading="criticalOpen.loading"
      />
    </div>

    <FilterBar title="筛选条件" description="按类型、等级、状态、对象类型与时间范围过滤">
      <label class="field">
        <span class="field__label">告警类型</span>
        <select v-model="filters.alertType" class="select">
          <option value="">全部类型</option>
          <option v-for="o in ALERT_TYPE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">等级</span>
        <select v-model="filters.alertLevel" class="select">
          <option value="">全部等级</option>
          <option v-for="o in ALERT_LEVEL_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">状态</span>
        <select v-model="filters.status" class="select">
          <option value="">全部状态</option>
          <option v-for="o in ALERT_STATUS_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">对象类型</span>
        <select v-model="filters.targetType" class="select">
          <option value="">全部</option>
          <option v-for="o in ALERT_TARGET_TYPE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">最近天数</span>
        <select v-model.number="filters.days" class="select">
          <option :value="1">近 1 天</option>
          <option :value="7">近 7 天</option>
          <option :value="30">近 30 天</option>
          <option :value="92">近 92 天</option>
        </select>
      </label>
      <template #actions>
        <button class="btn btn--primary" @click="onSearch">查询</button>
        <button class="btn" @click="onReset">重置</button>
      </template>
    </FilterBar>

    <p v-if="actionError" class="scan-error">{{ actionError }}</p>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">告警列表</h3>
          <p class="panel__subtitle">/cost-alert/page · 状态流转：OPEN → ACKNOWLEDGED → RESOLVED</p>
        </div>
      </header>
      <DataTable
        :columns="columns"
        :rows="records"
        row-key="id"
        :loading="list.loading"
        :error="list.error"
        :error-trace-id="list.errorTraceId"
        :empty="!list.loading && !list.error && records.length === 0"
        empty-text="该筛选条件下没有告警"
        @retry="list.execute"
      >
        <template #cell-alertLevel="{ value }">
          <span class="badge" :class="'badge--' + alertLevelTone(String(value ?? ''))">{{
            value ?? '—'
          }}</span>
        </template>
        <template #cell-status="{ value }">
          <span class="badge" :class="'badge--' + alertStatusTone(String(value ?? ''))">{{
            value ?? '—'
          }}</span>
        </template>
        <template #cell-message="{ value }">
          <span class="cell-message text-truncate" :title="String(value ?? '')">{{
            value ?? '—'
          }}</span>
        </template>
        <template #cell-action="{ row }">
          <div class="row-actions">
            <button
              class="btn btn--sm"
              :disabled="
                acting === (row as CostAlertRow).id || (row as CostAlertRow).status !== 'OPEN'
              "
              @click="acknowledge(row)"
            >
              确认
            </button>
            <button
              class="btn btn--sm"
              :disabled="
                acting === (row as CostAlertRow).id || (row as CostAlertRow).status === 'RESOLVED'
              "
              @click="resolve(row)"
            >
              恢复
            </button>
          </div>
        </template>
      </DataTable>

      <div class="pagination">
        <div class="row">
          <span class="pagination__info"
            >共 {{ formatCount(total) }} 条 · 第 {{ page }} / {{ totalPages }} 页</span
          >
          <select
            v-model.number="size"
            class="select page-size"
            aria-label="每页条数"
            @change="onSizeChange"
          >
            <option v-for="s in PAGE_SIZE_OPTIONS" :key="s" :value="s">{{ s }} 条/页</option>
          </select>
        </div>
        <div class="pagination__controls">
          <button class="btn btn--sm" type="button" :disabled="page <= 1" @click="goPage(page - 1)">
            上一页
          </button>
          <span class="pagination__page mono">{{ page }}</span>
          <button
            class="btn btn--sm"
            type="button"
            :disabled="page >= totalPages"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <p class="foot-note text-muted">
      金额字段说明：告警中的 amount / currentValue 均为 USD；阈值以小数比率存储（0.85 表示
      85%），表格统一按百分比展示。
    </p>
  </div>
</template>

<style scoped>
.scan-error {
  padding: 8px 12px;
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--font-sm);
}
.scan-result .panel__body {
  padding-top: var(--space-3);
}
.scan-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 0;
  list-style: none;
}
.scan-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
}
.cell-message {
  display: block;
  max-width: 380px;
}
.row-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
.page-size {
  width: 108px;
}
.foot-note {
  font-size: var(--font-xs);
}
</style>
