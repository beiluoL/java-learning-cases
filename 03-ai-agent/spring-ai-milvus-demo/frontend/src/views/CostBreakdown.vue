<script setup lang="ts">
import { computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { governanceApi } from '@/api/governance'
import { costApi } from '@/api/cost'
import { isEmptyOf, useResource } from '@/composables/useAsyncData'
import { useTimeRangeStore } from '@/stores/timeRange'
import { costKindTone } from '@/constants'
import { CHART_COLORS, hbarChartOption, pieChartOption } from '@/utils/chart'
import { formatCount, formatCurrency, formatRatio, toNumber } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import DataTable from '@/components/DataTable.vue'
import EChart from '@/components/EChart.vue'

const store = useTimeRangeStore()

const breakdown = useResource(() => governanceApi.breakdown(store.range))
const users = useResource(() => governanceApi.users(store.range, 20))
const models = useResource(() => costApi.models(store.range))
const scenes = useResource(() => costApi.scenes(store.range))
const forecast = useResource(() => governanceApi.forecast())
const infrastructure = useResource(() => governanceApi.dailySummary('ALL'))

function reloadAll() {
  breakdown.execute()
  users.execute()
  models.execute()
  scenes.execute()
  forecast.execute()
  infrastructure.execute()
}
watch(() => store.revision, reloadAll, { immediate: true })

/** 三类口径分段：ACTUAL 才是真实账单，ALLOCATED 是分摊，SAVED 是节省（不进成本合计）。 */
const costKindLabel: Record<string, string> = {
  ACTUAL: '真实账单',
  ALLOCATED: '基础设施分摊',
  SAVED: '已节省（不计入成本）',
}

const apiCost = computed(() => breakdown.data?.apiCost ?? '')
const allocatedCost = computed(() => breakdown.data?.allocatedCost ?? '')
const savedCost = computed(() => breakdown.data?.savedCost ?? '')

/** 构成占比图：只画真实账单与分摊，SAVED 单独呈现，避免与成本混为一谈 */
const costPieItems = computed(() =>
  (breakdown.data?.items ?? [])
    .filter((i) => i.costKind !== 'SAVED')
    .map((i) => ({
      name: `${i.entryType} · ${costKindLabel[i.costKind] ?? i.costKind}`,
      value: toNumber(i.amount),
    })),
)

const costPieOption = computed<EChartsOption>(() => pieChartOption(costPieItems.value))

const userBarOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (users.data ?? []).map((u) => ({ name: u.userId, value: toNumber(u.totalCost) })),
    CHART_COLORS[1],
  ),
)

const modelBarOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (models.data ?? []).map((m) => ({ name: m.modelName, value: toNumber(m.totalCost) })),
    CHART_COLORS[0],
  ),
)

const scenePieOption = computed<EChartsOption>(() =>
  pieChartOption(
    (scenes.data ?? []).map((s) => ({ name: s.businessScene, value: toNumber(s.totalCost) })),
  ),
)

const itemColumns: TableColumn[] = [
  { key: 'entryType', title: '账务类型', width: '170px' },
  { key: 'costKind', title: '口径', width: '150px' },
  {
    key: 'entryCount',
    title: '笔数',
    align: 'right',
    width: '90px',
    render: (v) => formatCount(v),
  },
  { key: 'amount', title: '金额', align: 'right', render: (v) => formatCurrency(v) },
  {
    key: 'costRatio',
    title: '占比',
    align: 'right',
    width: '100px',
    render: (v) => formatRatio(v),
  },
]

const userColumns: TableColumn[] = [
  { key: 'userId', title: '用户', width: '180px' },
  {
    key: 'requestCount',
    title: '请求次数',
    align: 'right',
    width: '110px',
    render: (v) => formatCount(v),
  },
  { key: 'totalCost', title: '成本', align: 'right', render: (v) => formatCurrency(v) },
  {
    key: 'costRatio',
    title: '占比',
    align: 'right',
    width: '100px',
    render: (v) => formatRatio(v),
  },
]

const infraColumns: TableColumn[] = [
  { key: 'summaryDate', title: '日期', width: '120px' },
  { key: 'dimension', title: '维度', width: '110px' },
  { key: 'dimensionKey', title: '维度值', width: '160px' },
  {
    key: 'requestCount',
    title: '请求',
    align: 'right',
    width: '90px',
    render: (v) => formatCount(v),
  },
  { key: 'totalTokens', title: 'Token', align: 'right', render: (v) => formatCount(v) },
  { key: 'totalCost', title: '成本', align: 'right', render: (v) => formatCurrency(v) },
]
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Cost Breakdown"
      title="成本构成"
      description="三类口径严格分开：真实账单（ACTUAL）、基础设施分摊（ALLOCATED）、已节省（SAVED）。分摊费用不会被冒充成供应商账单，节省额也不计入成本合计。"
    >
      <template #actions><TimeRangePicker /></template>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="真实 API 账单 (ACTUAL)"
        :value="formatCurrency(apiCost)"
        sub="供应商实际计费口径，来自账务流水 MODEL_CALL"
        tone="primary"
        :loading="breakdown.loading"
      />
      <StatCard
        label="基础设施分摊 (ALLOCATED)"
        :value="formatCurrency(allocatedCost)"
        sub="按请求量分摊的自建资源成本，非供应商账单"
        tone="info"
        :loading="breakdown.loading"
      />
      <StatCard
        label="已节省 (SAVED)"
        :value="formatCurrency(savedCost)"
        sub="缓存命中与优化收益，不计入成本合计"
        tone="success"
        :loading="breakdown.loading"
      />
      <StatCard
        label="预计本月成本"
        :value="formatCurrency(forecast.data?.monthly.projectedCost)"
        :sub="`预算 ${formatCurrency(forecast.data?.monthly.budgetLimit)} · 使用率 ${formatRatio(forecast.data?.monthly.usageRate)}`"
        :tone="forecast.data?.monthly.willExceed ? 'danger' : 'neutral'"
        :loading="forecast.loading"
      />
      <StatCard
        label="最近 24h 成本"
        :value="formatCurrency(forecast.data?.monthly.rolling24hCost)"
        :sub="`滚动外推 ${formatCurrency(forecast.data?.monthly.rollingProjectedCost)}`"
        tone="warning"
        :loading="forecast.loading"
      />
      <StatCard
        label="日预算耗尽预计"
        :value="
          forecast.data?.dailyExhaustionEtaHours
            ? `${forecast.data.dailyExhaustionEtaHours} 小时`
            : '—'
        "
        sub="剩余额度 ÷ 今日每小时消耗速度"
        tone="neutral"
        :loading="forecast.loading"
      />
    </div>

    <div class="chart-grid">
      <ChartCard
        title="成本构成占比"
        subtitle="/cost/breakdown · 仅真实账单与分摊（SAVED 不参与）"
        :loading="breakdown.loading"
        :error="breakdown.error"
        :error-trace-id="breakdown.errorTraceId"
        :empty="!breakdown.loading && !breakdown.error && costPieItems.length === 0"
        empty-text="所选时间范围内没有成本流水"
        height="300px"
        @retry="breakdown.execute"
      >
        <EChart :option="costPieOption" height="300px" />
      </ChartCard>
      <ChartCard
        title="业务场景成本占比"
        subtitle="/cost/scenes · 按 totalCost 聚合"
        :loading="scenes.loading"
        :error="scenes.error"
        :error-trace-id="scenes.errorTraceId"
        :empty="isEmptyOf(scenes)"
        height="300px"
        @retry="scenes.execute"
      >
        <EChart :option="scenePieOption" height="300px" />
      </ChartCard>
    </div>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">构成明细</h3>
          <p class="panel__subtitle">
            /cost/breakdown · 按账务类型与口径聚合，笔数与金额均可回溯到流水
          </p>
        </div>
      </header>
      <DataTable
        :columns="itemColumns"
        :rows="breakdown.data?.items ?? []"
        row-key="entryType"
        :loading="breakdown.loading"
        :error="breakdown.error"
        :error-trace-id="breakdown.errorTraceId"
        :empty="
          !breakdown.loading && !breakdown.error && (breakdown.data?.items?.length ?? 0) === 0
        "
        empty-text="所选时间范围内没有账务流水"
        @retry="breakdown.execute"
      >
        <template #cell-costKind="{ value }">
          <span class="badge" :class="'badge--' + costKindTone(String(value))">
            {{ costKindLabel[String(value)] ?? value }}
          </span>
        </template>
      </DataTable>
    </section>

    <div class="chart-grid">
      <ChartCard
        title="用户成本排行"
        subtitle="/cost/users · 用于归因到具体用户"
        :loading="users.loading"
        :error="users.error"
        :error-trace-id="users.errorTraceId"
        :empty="isEmptyOf(users)"
        height="300px"
        @retry="users.execute"
      >
        <EChart :option="userBarOption" height="300px" />
      </ChartCard>
      <ChartCard
        title="模型成本排行"
        subtitle="/cost/models · 用于归因到具体模型"
        :loading="models.loading"
        :error="models.error"
        :error-trace-id="models.errorTraceId"
        :empty="isEmptyOf(models)"
        height="300px"
        @retry="models.execute"
      >
        <EChart :option="modelBarOption" height="300px" />
      </ChartCard>
    </div>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">用户成本明细</h3>
          <p class="panel__subtitle">/cost/users · 与上方排行同源，便于对照具体数值</p>
        </div>
      </header>
      <DataTable
        :columns="userColumns"
        :rows="users.data ?? []"
        row-key="userId"
        :loading="users.loading"
        :error="users.error"
        :error-trace-id="users.errorTraceId"
        :empty="isEmptyOf(users)"
        @retry="users.execute"
      />
    </section>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">每日汇总（五维度）</h3>
          <p class="panel__subtitle">
            /cost/daily-summary · 由定时任务每小时重建；预测口径：{{
              forecast.data?.monthly.basis || '—'
            }}
          </p>
        </div>
      </header>
      <DataTable
        :columns="infraColumns"
        :rows="infrastructure.data ?? []"
        row-key="id"
        :loading="infrastructure.loading"
        :error="infrastructure.error"
        :error-trace-id="infrastructure.errorTraceId"
        :empty="isEmptyOf(infrastructure)"
        empty-text="尚无日汇总数据，可点击下方按钮重建"
        @retry="infrastructure.execute"
      />
    </section>
  </div>
</template>
