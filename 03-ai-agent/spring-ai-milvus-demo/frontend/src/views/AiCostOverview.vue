<script setup lang="ts">
import { computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { costApi } from '@/api/cost'
import { isEmptyOf, useResource } from '@/composables/useAsyncData'
import { useTimeRangeStore } from '@/stores/timeRange'
import { requestStatusTone, severityTone, usageStatusTone } from '@/constants'
import {
  formatBucketLabel,
  formatCount,
  formatCurrency,
  formatDateTime,
  formatRatio,
  formatToken,
  toNumber,
} from '@/utils/format'
import { CHART_COLORS, hbarChartOption, lineChartOption, pieChartOption } from '@/utils/chart'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import AsyncState from '@/components/AsyncState.vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import DataTable from '@/components/DataTable.vue'
import EChart from '@/components/EChart.vue'

type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const store = useTimeRangeStore()

// 8 个数据块全部依赖 store.range，时间筛选变化时统一重新取数。
const overview = useResource(() => costApi.overview(store.range))
const trend = useResource(() => costApi.trend(store.range, 'DAY'))
const models = useResource(() => costApi.models(store.range))
const scenes = useResource(() => costApi.scenes(store.range))
const topModels = useResource(() => costApi.topModels(store.range, 10))
const topUsers = useResource(() => costApi.topUsers(store.range, 10))
const recent = useResource(() => costApi.recentRequests(store.range, 10))
const alerts = useResource(() => costApi.alerts(store.range, 10))

function reloadAll() {
  overview.execute()
  trend.execute()
  models.execute()
  scenes.execute()
  topModels.execute()
  topUsers.execute()
  recent.execute()
  alerts.execute()
}

watch(() => store.revision, reloadAll, { immediate: true })

const statCards = computed<{ label: string; value: string; sub: string; tone: StatTone }[]>(() => {
  const d = overview.data
  return [
    {
      label: '总成本',
      value: d ? formatCurrency(d.totalCost) : '—',
      sub: d
        ? `成功率 ${formatRatio(d.successRate)} · 预算使用率 ${formatRatio(d.budgetUsageRate)}`
        : '',
      tone: 'primary',
    },
    {
      label: '总 Token',
      value: d ? formatToken(d.totalTokens) : '—',
      sub: d ? `其中缓存输入 ${formatToken(d.cachedInputTokens)}` : '',
      tone: 'info',
    },
    { label: '输入 Token', value: d ? formatToken(d.inputTokens) : '—', sub: '', tone: 'neutral' },
    { label: '输出 Token', value: d ? formatToken(d.outputTokens) : '—', sub: '', tone: 'neutral' },
    { label: '请求次数', value: d ? formatCount(d.requestCount) : '—', sub: '', tone: 'success' },
    {
      label: '平均请求成本',
      value: d ? formatCurrency(d.avgCostPerRequest) : '—',
      sub: 'avgCostPerRequest · 单次请求',
      tone: 'warning',
    },
  ]
})

const trendLabels = computed(() => (trend.data ?? []).map((p) => formatBucketLabel(p.bucket)))

const costTrendOption = computed<EChartsOption>(() => {
  const points = trend.data ?? []
  return lineChartOption(trendLabels.value, [
    {
      name: '总成本 (USD)',
      data: points.map((p) => toNumber(p.totalCost)),
      color: CHART_COLORS[0],
      area: true,
    },
  ])
})

const tokenTrendOption = computed<EChartsOption>(() => {
  const points = trend.data ?? []
  return lineChartOption(trendLabels.value, [
    {
      name: '输入 Token',
      data: points.map((p) => toNumber(p.inputTokens)),
      color: CHART_COLORS[0],
    },
    {
      name: '输出 Token',
      data: points.map((p) => toNumber(p.outputTokens)),
      color: CHART_COLORS[2],
    },
  ])
})

const modelPieOption = computed<EChartsOption>(() =>
  pieChartOption(
    (models.data ?? []).map((m) => ({ name: m.modelName, value: toNumber(m.totalCost) })),
  ),
)

const scenePieOption = computed<EChartsOption>(() =>
  pieChartOption(
    (scenes.data ?? []).map((s) => ({ name: s.businessScene, value: toNumber(s.totalCost) })),
  ),
)

const topModelsOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (topModels.data ?? []).map((i) => ({ name: i.name, value: toNumber(i.value) })),
    CHART_COLORS[0],
  ),
)

const topUsersOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (topUsers.data ?? []).map((i) => ({ name: i.name, value: toNumber(i.value) })),
    CHART_COLORS[1],
  ),
)

const recentColumns: TableColumn[] = [
  { key: 'createdAt', title: '时间', width: '168px', render: (v) => formatDateTime(v) },
  { key: 'userId', title: '用户', width: '130px' },
  { key: 'modelName', title: '模型', width: '170px' },
  { key: 'businessScene', title: '场景', width: '120px' },
  { key: 'totalTokens', title: 'Token', align: 'right', render: (v) => formatToken(v) },
  { key: 'totalCost', title: '成本', align: 'right', render: (v) => formatCurrency(v) },
  { key: 'requestStatus', title: '状态', width: '110px' },
  { key: 'usageStatus', title: 'Usage', width: '120px' },
]

const alertColumns: TableColumn[] = [
  { key: 'severity', title: '级别', width: '96px' },
  { key: 'alertType', title: '类型', width: '130px' },
  { key: 'budgetType', title: '预算类型', width: '110px' },
  { key: 'message', title: '预警内容' },
  { key: 'createdAt', title: '时间', width: '168px', render: (v) => formatDateTime(v) },
]
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Overview"
      title="成本总览"
      description="全部指标来自 /api/admin/ai/cost/* 统计接口，时间筛选会同步作用于下方每一个请求。"
    >
      <template #actions><TimeRangePicker /></template>
    </PageHeader>

    <!-- 6 个统计卡片 -->
    <AsyncState
      :error="overview.error"
      :error-trace-id="overview.errorTraceId"
      :empty="!overview.loading && !overview.error && !overview.data"
      :min-height="'122px'"
      @retry="overview.execute"
    >
      <div class="stat-grid">
        <StatCard
          v-for="card in statCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :sub="card.sub"
          :tone="card.tone"
          :loading="overview.loading"
        />
      </div>
    </AsyncState>

    <!-- 趋势图 -->
    <div class="chart-grid">
      <ChartCard
        title="每日成本趋势"
        subtitle="/cost/trend · granularity=DAY · totalCost"
        :loading="trend.loading"
        :error="trend.error"
        :error-trace-id="trend.errorTraceId"
        :empty="isEmptyOf(trend)"
        height="280px"
        @retry="trend.execute"
      >
        <EChart :option="costTrendOption" height="280px" />
      </ChartCard>
      <ChartCard
        title="Token 趋势"
        subtitle="/cost/trend · inputTokens / outputTokens"
        :loading="trend.loading"
        :error="trend.error"
        :error-trace-id="trend.errorTraceId"
        :empty="isEmptyOf(trend)"
        height="280px"
        @retry="trend.execute"
      >
        <EChart :option="tokenTrendOption" height="280px" />
      </ChartCard>
    </div>

    <!-- 占比饼图 -->
    <div class="chart-grid">
      <ChartCard
        title="模型成本占比"
        subtitle="/cost/models · 按 totalCost 聚合"
        :loading="models.loading"
        :error="models.error"
        :error-trace-id="models.errorTraceId"
        :empty="isEmptyOf(models)"
        height="280px"
        @retry="models.execute"
      >
        <EChart :option="modelPieOption" height="280px" />
      </ChartCard>
      <ChartCard
        title="业务场景占比"
        subtitle="/cost/scenes · 按 totalCost 聚合"
        :loading="scenes.loading"
        :error="scenes.error"
        :error-trace-id="scenes.errorTraceId"
        :empty="isEmptyOf(scenes)"
        height="280px"
        @retry="scenes.execute"
      >
        <EChart :option="scenePieOption" height="280px" />
      </ChartCard>
    </div>

    <!-- TOP10 -->
    <div class="chart-grid">
      <ChartCard
        title="高成本模型 TOP10"
        subtitle="/cost/top-models · value 为成本金额"
        :loading="topModels.loading"
        :error="topModels.error"
        :error-trace-id="topModels.errorTraceId"
        :empty="isEmptyOf(topModels)"
        height="300px"
        @retry="topModels.execute"
      >
        <EChart :option="topModelsOption" height="300px" />
      </ChartCard>
      <ChartCard
        title="高频用户 TOP10"
        subtitle="/cost/top-users · 用户维度排行"
        :loading="topUsers.loading"
        :error="topUsers.error"
        :error-trace-id="topUsers.errorTraceId"
        :empty="isEmptyOf(topUsers)"
        height="300px"
        @retry="topUsers.execute"
      >
        <EChart :option="topUsersOption" height="300px" />
      </ChartCard>
    </div>

    <!-- 明细与预警 -->
    <div class="overview-bottom">
      <section class="panel">
        <header class="panel__header">
          <div>
            <h3 class="panel__title">最近调用记录</h3>
            <p class="panel__subtitle">/cost/recent-requests · 最近 10 条</p>
          </div>
        </header>
        <DataTable
          :columns="recentColumns"
          :rows="recent.data ?? []"
          row-key="requestId"
          :loading="recent.loading"
          :error="recent.error"
          :error-trace-id="recent.errorTraceId"
          :empty="isEmptyOf(recent)"
          @retry="recent.execute"
        >
          <template #cell-requestStatus="{ value }">
            <span class="badge" :class="'badge--' + requestStatusTone(String(value ?? ''))">{{
              value ?? '—'
            }}</span>
          </template>
          <template #cell-usageStatus="{ value }">
            <span class="badge" :class="'badge--' + usageStatusTone(String(value ?? ''))">{{
              value ?? '—'
            }}</span>
          </template>
        </DataTable>
      </section>

      <section class="panel">
        <header class="panel__header">
          <div>
            <h3 class="panel__title">预算预警</h3>
            <p class="panel__subtitle">/cost/alerts · 最近 10 条</p>
          </div>
        </header>
        <DataTable
          :columns="alertColumns"
          :rows="alerts.data ?? []"
          row-key="id"
          :loading="alerts.loading"
          :error="alerts.error"
          :error-trace-id="alerts.errorTraceId"
          :empty="isEmptyOf(alerts)"
          empty-text="当前时间范围内没有预算预警"
          @retry="alerts.execute"
        >
          <template #cell-severity="{ value }">
            <span class="badge" :class="'badge--' + severityTone(String(value ?? ''))">{{
              value ?? '—'
            }}</span>
          </template>
          <template #cell-message="{ value }">
            <span class="cell-message text-truncate" :title="String(value ?? '')">{{
              value ?? '—'
            }}</span>
          </template>
        </DataTable>
      </section>
    </div>
  </div>
</template>

<style scoped>
.overview-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: var(--space-4);
}
.cell-message {
  display: block;
  max-width: 340px;
}
@media (max-width: 1100px) {
  .overview-bottom {
    grid-template-columns: 1fr;
  }
}
</style>
