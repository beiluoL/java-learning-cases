<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import { governanceApi } from '@/api/governance'
import { toErrorMessage } from '@/composables/useAsyncData'
import { useResource } from '@/composables/useAsyncData'
import { CHART_COLORS, barChartOption } from '@/utils/chart'
import { formatCount, formatCurrency, formatDateTime, formatRatio } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import type { OptimizationExecuteResult } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import DataTable from '@/components/DataTable.vue'
import EChart from '@/components/EChart.vue'

/** 收益口径：MEASURED 实测 / COUNTERFACTUAL 反推 / ESTIMATED 估算，禁止混写 */
const MEASURE_LABEL: Record<string, string> = {
  MEASURED: '实测',
  COUNTERFACTUAL: '反推（真实用量 × 真实价格）',
  ESTIMATED: '估算',
}

const MEASURE_TONE: Record<string, string> = {
  MEASURED: 'success',
  COUNTERFACTUAL: 'info',
  ESTIMATED: 'warning',
}

const overview = useResource(() => governanceApi.optimization())
const strategies = useResource(() => governanceApi.strategies())
const history = useResource(() => governanceApi.optimizationHistory(undefined, 1, 20))

onMounted(() => {
  overview.execute()
  strategies.execute()
  history.execute()
})

function reloadAll() {
  overview.execute()
  history.execute()
}

const summary = computed(() => overview.data?.summary)

/** 各策略节省金额对比：只用后端给的 savedCost，不做前端二次估算 */
const strategyBarOption = computed<EChartsOption>(() => {
  const rows = summary.value?.strategies ?? []
  return barChartOption(
    rows.map((s) => s.displayName),
    [
      {
        name: '节省金额 (USD)',
        data: rows.map((s) => Number(s.savedCost) || 0),
        color: CHART_COLORS[2],
      },
    ],
  )
})

const strategyColumns: TableColumn[] = [
  { key: 'displayName', title: '策略', width: '150px' },
  { key: 'strategy', title: '编码', width: '190px' },
  { key: 'trigger', title: '触发时机' },
  { key: 'measureKind', title: '收益口径', width: '150px' },
  { key: 'enabled', title: '状态', width: '90px' },
]

const statColumns: TableColumn[] = [
  { key: 'displayName', title: '策略', width: '150px' },
  {
    key: 'executionCount',
    title: '执行次数',
    align: 'right',
    width: '100px',
    render: (v) => formatCount(v),
  },
  {
    key: 'beforeTokens',
    title: '优化前 Token',
    align: 'right',
    width: '130px',
    render: (v) => formatCount(v),
  },
  {
    key: 'afterTokens',
    title: '优化后 Token',
    align: 'right',
    width: '130px',
    render: (v) => formatCount(v),
  },
  {
    key: 'savedTokens',
    title: '节省 Token',
    align: 'right',
    width: '120px',
    render: (v) => formatCount(v),
  },
  { key: 'savedCost', title: '节省金额', align: 'right', render: (v) => formatCurrency(v) },
  { key: 'measureKind', title: '口径', width: '130px' },
  {
    key: 'lastExecutedAt',
    title: '最近执行',
    width: '168px',
    render: (v) => (v ? formatDateTime(v) : '—'),
  },
]

const historyColumns: TableColumn[] = [
  { key: 'createdAt', title: '时间', width: '168px', render: (v) => formatDateTime(v) },
  { key: 'strategy', title: '策略', width: '190px' },
  {
    key: 'beforeTokens',
    title: '前',
    align: 'right',
    width: '90px',
    render: (v) => formatCount(v),
  },
  { key: 'afterTokens', title: '后', align: 'right', width: '90px', render: (v) => formatCount(v) },
  {
    key: 'savedTokens',
    title: '节省',
    align: 'right',
    width: '90px',
    render: (v) => formatCount(v),
  },
  { key: 'savedCost', title: '节省金额', align: 'right', render: (v) => formatCurrency(v) },
  { key: 'measureKind', title: '口径', width: '130px' },
]

/* ---------------- 试算 ---------------- */

const trialStrategy = ref('PROMPT_COMPRESSION')
const trialText = ref(
  '你是企业 AI 助手。\n\n你是企业 AI 助手。\n请用中文回答。\n\n\n请用中文回答。\n如果上下文不足，请明确说明缺少什么信息。',
)
const trialResult = ref<OptimizationExecuteResult | null>(null)
const trialLoading = ref(false)
const trialError = ref<string | null>(null)

const trialOptions = computed(() =>
  (strategies.data ?? [])
    .filter((s) =>
      ['PROMPT_COMPRESSION', 'CONTEXT_COMPRESSION', 'RAG_CONTEXT_OPTIMIZE'].includes(s.strategy),
    )
    .map((s) => ({ value: s.strategy, label: s.displayName })),
)

async function runTrial() {
  if (!trialText.value.trim()) {
    trialError.value = '请输入待优化的文本'
    return
  }
  trialLoading.value = true
  trialError.value = null
  trialResult.value = null
  try {
    trialResult.value = await governanceApi.executeOptimization(
      trialStrategy.value,
      trialText.value,
    )
    history.execute()
  } catch (err) {
    trialError.value = toErrorMessage(err)
  } finally {
    trialLoading.value = false
  }
}
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Optimization"
      title="优化中心"
      description="收益按三口径分列：MEASURED 实测（真实流水）、COUNTERFACTUAL 反推（真实用量 × 真实价格）、ESTIMATED 估算（试算，不写入账单）。估算值永远不会被标成实测。"
    >
      <template #actions>
        <button class="btn" :disabled="overview.loading" @click="reloadAll">刷新</button>
      </template>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="实测节省 (MEASURED)"
        :value="formatCurrency(summary?.measuredSavedCost)"
        sub="来自响应缓存命中的真实流水，可直接验证"
        tone="success"
        :loading="overview.loading"
      />
      <StatCard
        label="反推节省 (COUNTERFACTUAL)"
        :value="formatCurrency(summary?.counterfactualSavedCost)"
        sub="省下的 Token × 该模型生效价格"
        tone="info"
        :loading="overview.loading"
      />
      <StatCard
        label="估算节省 (ESTIMATED)"
        :value="formatCurrency(summary?.estimatedSavedCost)"
        sub="试算与预估，不构成账单依据"
        tone="warning"
        :loading="overview.loading"
      />
      <StatCard
        label="响应缓存命中率"
        :value="formatRatio(summary?.cache.hitRate)"
        :sub="`命中 ${formatCount(summary?.cache.hit)} · 未命中 ${formatCount(summary?.cache.miss)}`"
        tone="primary"
        :loading="overview.loading"
      />
      <StatCard
        label="Embedding 去重率"
        :value="formatRatio(summary?.embedding.skipRate)"
        :sub="`跳过 ${formatCount(summary?.embedding.skipped)} · 新生成 ${formatCount(summary?.embedding.generated)}`"
        tone="info"
        :loading="overview.loading"
      />
    </div>

    <div class="chart-grid">
      <ChartCard
        title="各策略节省金额"
        subtitle="/cost/optimization · summary.strategies[].savedCost"
        :loading="overview.loading"
        :error="overview.error"
        :error-trace-id="overview.errorTraceId"
        :empty="!overview.loading && !overview.error && (summary?.strategies?.length ?? 0) === 0"
        empty-text="暂无优化执行记录"
        height="280px"
        @retry="overview.execute"
      >
        <EChart :option="strategyBarOption" height="280px" />
      </ChartCard>

      <section class="panel trial">
        <header class="panel__header">
          <div>
            <h3 class="panel__title">策略试算</h3>
            <p class="panel__subtitle">
              /cost-optimization/execute · 纯函数试算，结果标记 ESTIMATED，不写入任何账单
            </p>
          </div>
        </header>
        <div class="panel__body trial__body">
          <div class="trial__row">
            <label class="field trial__select">
              <span class="field__label">策略</span>
              <select v-model="trialStrategy" class="select">
                <option v-for="opt in trialOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </label>
            <button class="btn btn--primary" :disabled="trialLoading" @click="runTrial">
              {{ trialLoading ? '试算中…' : '开始试算' }}
            </button>
          </div>
          <label class="field">
            <span class="field__label">待优化文本（默认给了一段含重复行与空行的样例）</span>
            <textarea v-model="trialText" class="textarea" rows="5" />
          </label>
          <p v-if="trialError" class="trial__error">{{ trialError }}</p>
          <div v-if="trialResult" class="trial__result">
            <div class="trial__metrics">
              <span
                >优化前
                <strong class="mono">{{ formatCount(trialResult.beforeTokens) }}</strong></span
              >
              <span
                >优化后
                <strong class="mono">{{ formatCount(trialResult.afterTokens) }}</strong></span
              >
              <span
                >节省 <strong class="mono">{{ formatCount(trialResult.savedTokens) }}</strong></span
              >
              <span
                >预估金额
                <strong class="mono">{{
                  formatCurrency(trialResult.estimatedSavedCost)
                }}</strong></span
              >
            </div>
            <p class="trial__note">{{ trialResult.detail }}</p>
            <p class="trial__note text-muted">
              口径 {{ trialResult.measureKind }} · {{ trialResult.qualityNote }}
            </p>
          </div>
        </div>
      </section>
    </div>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">策略清单</h3>
          <p class="panel__subtitle">
            /cost-optimization/strategies · 每条策略的触发条件与收益口径
          </p>
        </div>
      </header>
      <DataTable
        :columns="strategyColumns"
        :rows="strategies.data ?? []"
        row-key="strategy"
        :loading="strategies.loading"
        :error="strategies.error"
        :error-trace-id="strategies.errorTraceId"
        :empty="!strategies.loading && !strategies.error && (strategies.data?.length ?? 0) === 0"
        @retry="strategies.execute"
      >
        <template #cell-measureKind="{ value }">
          <span class="badge" :class="'badge--' + (MEASURE_TONE[String(value)] ?? 'neutral')">
            {{ MEASURE_LABEL[String(value)] ?? value }}
          </span>
        </template>
        <template #cell-enabled="{ value }">
          <span class="badge" :class="value ? 'badge--success' : 'badge--neutral'">
            {{ value ? '启用' : '关闭' }}
          </span>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">策略实际效果</h3>
          <p class="panel__subtitle">/cost/optimization · summary.strategies · 来自优化日志聚合</p>
        </div>
      </header>
      <DataTable
        :columns="statColumns"
        :rows="summary?.strategies ?? []"
        row-key="strategy"
        :loading="overview.loading"
        :error="overview.error"
        :error-trace-id="overview.errorTraceId"
        :empty="!overview.loading && !overview.error && (summary?.strategies?.length ?? 0) === 0"
        empty-text="暂无优化执行记录（只有真实产生节省时才会落库，不虚报）"
        @retry="overview.execute"
      >
        <template #cell-measureKind="{ value }">
          <span class="badge" :class="'badge--' + (MEASURE_TONE[String(value)] ?? 'neutral')">
            {{ MEASURE_LABEL[String(value)] ?? value }}
          </span>
        </template>
      </DataTable>
    </section>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">优化执行记录</h3>
          <p class="panel__subtitle">/cost-optimization/history · 最近 20 条</p>
        </div>
      </header>
      <DataTable
        :columns="historyColumns"
        :rows="history.data?.records ?? []"
        row-key="id"
        :loading="history.loading"
        :error="history.error"
        :error-trace-id="history.errorTraceId"
        :empty="!history.loading && !history.error && (history.data?.records?.length ?? 0) === 0"
        empty-text="当前没有产生实际节省的优化记录"
        @retry="history.execute"
      >
        <template #cell-measureKind="{ value }">
          <span class="badge" :class="'badge--' + (MEASURE_TONE[String(value)] ?? 'neutral')">
            {{ value }}
          </span>
        </template>
      </DataTable>
    </section>
  </div>
</template>

<style scoped>
.trial {
  display: flex;
  flex-direction: column;
}
.trial__body {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.trial__row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-3);
  flex-wrap: wrap;
}
.trial__select {
  min-width: 200px;
  flex: 1;
}
.trial__error {
  font-size: var(--font-sm);
  color: var(--color-danger);
}
.trial__result {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}
.trial__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
}
.trial__metrics strong {
  color: var(--color-text);
}
.trial__note {
  margin-top: 6px;
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
  word-break: break-all;
}
</style>
