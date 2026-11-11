<script setup lang="ts">
import { computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { costApi } from '@/api/cost'
import { isEmptyOf, useResource } from '@/composables/useAsyncData'
import { useTimeRangeStore } from '@/stores/timeRange'
import { formatBucketLabel, formatToken, toNumber } from '@/utils/format'
import { CHART_COLORS, barChartOption, hbarChartOption, pieChartOption } from '@/utils/chart'
import PageHeader from '@/components/PageHeader.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import AsyncState from '@/components/AsyncState.vue'
import StatCard from '@/components/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import EChart from '@/components/EChart.vue'

type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const store = useTimeRangeStore()

// 所有请求均携带 store.range（startTime / endTime）
const overview = useResource(() => costApi.overview(store.range))
const models = useResource(() => costApi.models(store.range))
const scenes = useResource(() => costApi.scenes(store.range))
const topUsers = useResource(() => costApi.topUsers(store.range, 10))
const trend = useResource(() => costApi.trend(store.range, 'DAY'))

function reloadAll() {
  overview.execute()
  models.execute()
  scenes.execute()
  topUsers.execute()
  trend.execute()
}
watch(() => store.revision, reloadAll, { immediate: true })

const statCards = computed<{ label: string; value: string; sub: string; tone: StatTone }[]>(() => {
  const d = overview.data
  return [
    {
      label: '输入 Token',
      value: d ? formatToken(d.inputTokens) : '—',
      sub: 'overview.inputTokens',
      tone: 'primary',
    },
    {
      label: '输出 Token',
      value: d ? formatToken(d.outputTokens) : '—',
      sub: 'overview.outputTokens',
      tone: 'info',
    },
    {
      label: '缓存输入 Token',
      value: d ? formatToken(d.cachedInputTokens) : '—',
      sub: 'overview.cachedInputTokens',
      tone: 'success',
    },
    {
      label: '总 Token',
      value: d ? formatToken(d.totalTokens) : '—',
      sub: 'overview.totalTokens',
      tone: 'warning',
    },
  ]
})

/** 模型分布：/cost/models 未直接给总 Token，按 inputTokens + outputTokens 聚合 */
const modelDistOption = computed<EChartsOption>(() =>
  pieChartOption(
    (models.data ?? []).map((m) => ({
      name: m.modelName,
      value: toNumber(m.inputTokens) + toNumber(m.outputTokens),
    })),
  ),
)

/** 场景分布：/cost/scenes 直接提供 totalTokens */
const sceneDistOption = computed<EChartsOption>(() =>
  pieChartOption(
    (scenes.data ?? []).map((s) => ({ name: s.businessScene, value: toNumber(s.totalTokens) })),
  ),
)

/** 用户分布：/cost/top-users 返回 {name, value}，按接口 value 原样排行 */
const userDistOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (topUsers.data ?? []).map((i) => ({ name: i.name, value: toNumber(i.value) })),
    CHART_COLORS[1],
  ),
)

/** 每日消耗：/cost/trend 的 inputTokens / outputTokens */
const dailyOption = computed<EChartsOption>(() => {
  const points = trend.data ?? []
  return barChartOption(
    points.map((p) => formatBucketLabel(p.bucket)),
    [
      { name: '输入 Token', data: points.map((p) => toNumber(p.inputTokens)) },
      { name: '输出 Token', data: points.map((p) => toNumber(p.outputTokens)) },
    ],
  )
})
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Token Analytics"
      title="Token 使用分析"
      description="Token 总量来自 /cost/overview；分布图表分别取自 /cost/models、/cost/scenes、/cost/top-users 与 /cost/trend。"
    >
      <template #actions><TimeRangePicker /></template>
    </PageHeader>

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

    <div class="chart-grid">
      <ChartCard
        title="模型分布"
        subtitle="/cost/models · 按 inputTokens + outputTokens 汇总"
        :loading="models.loading"
        :error="models.error"
        :error-trace-id="models.errorTraceId"
        :empty="isEmptyOf(models)"
        height="300px"
        @retry="models.execute"
      >
        <EChart :option="modelDistOption" height="300px" />
      </ChartCard>
      <ChartCard
        title="业务场景分布"
        subtitle="/cost/scenes · 按 totalTokens"
        :loading="scenes.loading"
        :error="scenes.error"
        :error-trace-id="scenes.errorTraceId"
        :empty="isEmptyOf(scenes)"
        height="300px"
        @retry="scenes.execute"
      >
        <EChart :option="sceneDistOption" height="300px" />
      </ChartCard>
    </div>

    <div class="chart-grid">
      <ChartCard
        title="用户分布 TOP10"
        subtitle="/cost/top-users · 按接口返回的 value 排行（成本口径）"
        :loading="topUsers.loading"
        :error="topUsers.error"
        :error-trace-id="topUsers.errorTraceId"
        :empty="isEmptyOf(topUsers)"
        height="300px"
        @retry="topUsers.execute"
      >
        <EChart :option="userDistOption" height="300px" />
      </ChartCard>
      <ChartCard
        title="每日 Token 消耗"
        subtitle="/cost/trend · granularity=DAY"
        :loading="trend.loading"
        :error="trend.error"
        :error-trace-id="trend.errorTraceId"
        :empty="isEmptyOf(trend)"
        height="300px"
        @retry="trend.execute"
      >
        <EChart :option="dailyOption" height="300px" />
      </ChartCard>
    </div>
  </div>
</template>
