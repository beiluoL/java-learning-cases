<script setup lang="ts">
import { computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { costApi } from '@/api/cost'
import { isEmptyOf, useResource } from '@/composables/useAsyncData'
import { useTimeRangeStore } from '@/stores/timeRange'
import { formatCount, formatMoney, formatRatio, formatToken, toNumber } from '@/utils/format'
import { CHART_COLORS, hbarChartOption } from '@/utils/chart'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import TimeRangePicker from '@/components/TimeRangePicker.vue'
import ChartCard from '@/components/ChartCard.vue'
import DataTable from '@/components/DataTable.vue'
import EChart from '@/components/EChart.vue'
import ProgressBar from '@/components/ProgressBar.vue'

const store = useTimeRangeStore()
const models = useResource(() => costApi.models(store.range))

watch(
  () => store.revision,
  () => models.execute(),
  { immediate: true },
)

const columns: TableColumn[] = [
  { key: 'provider', title: 'Provider', width: '140px' },
  { key: 'modelName', title: '模型', width: '200px' },
  { key: 'requestCount', title: '调用次数', align: 'right', render: (v) => formatCount(v) },
  { key: 'inputTokens', title: '输入 Token', align: 'right', render: (v) => formatToken(v) },
  { key: 'outputTokens', title: '输出 Token', align: 'right', render: (v) => formatToken(v) },
  { key: 'totalCost', title: '总成本 (USD)', align: 'right', render: (v) => formatMoney(v) },
  { key: 'avgCost', title: '平均成本', align: 'right', render: (v) => formatMoney(v) },
  { key: 'costRatio', title: '成本占比', width: '180px', align: 'right' },
]

const compareOption = computed<EChartsOption>(() =>
  hbarChartOption(
    (models.data ?? []).map((m) => ({ name: m.modelName, value: toNumber(m.totalCost) })),
    CHART_COLORS[0],
  ),
)
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Model Cost"
      title="模型成本分析"
      description="数据来源 /api/admin/ai/cost/models，金额口径为 USD，时间筛选同步生效。"
    >
      <template #actions><TimeRangePicker /></template>
    </PageHeader>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">模型成本明细</h3>
          <p class="panel__subtitle">provider / modelName / 调用次数 / Token / 成本 / 占比</p>
        </div>
      </header>
      <DataTable
        :columns="columns"
        :rows="models.data ?? []"
        row-key="modelName"
        :loading="models.loading"
        :error="models.error"
        :error-trace-id="models.errorTraceId"
        :empty="isEmptyOf(models)"
        @retry="models.execute"
      >
        <template #cell-costRatio="{ value }">
          <div class="ratio-cell">
            <span class="mono">{{ formatRatio(value) }}</span>
            <ProgressBar :value="toNumber(value)" :show-value="false" />
          </div>
        </template>
      </DataTable>
    </section>

    <ChartCard
      title="模型成本横向对比"
      subtitle="/cost/models · 按 totalCost 排序"
      :loading="models.loading"
      :error="models.error"
      :error-trace-id="models.errorTraceId"
      :empty="isEmptyOf(models)"
      height="360px"
      @retry="models.execute"
    >
      <EChart :option="compareOption" height="360px" />
    </ChartCard>
  </div>
</template>

<style scoped>
.ratio-cell {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 150px;
}
.ratio-cell > :last-child {
  flex: 1;
  max-width: 90px;
}
</style>
