import type { EChartsOption } from 'echarts'

/** 图表色板（与设计 token 一致），纯 UI 常量 */
export const CHART_COLORS: string[] = [
  '#4f46e5', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444',
  '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316',
]

export interface ChartSeries {
  name: string
  data: number[]
  color?: string
  area?: boolean
}

export interface ChartItem {
  name: string
  value: number
}

const axisLabelStyle = { color: '#9ca3af', fontSize: 11 }
const categoryAxisLine = { lineStyle: { color: '#e5e7eb' } }
const splitLine = { lineStyle: { color: '#eef0f3', type: 'dashed' as const } }
const legendStyle = { textStyle: { color: '#6b7280', fontSize: 11 }, itemWidth: 12, itemHeight: 8 }

/** 折线 / 面积趋势图 */
export function lineChartOption(labels: string[], series: ChartSeries[]): EChartsOption {
  const option: EChartsOption = {
    color: CHART_COLORS,
    tooltip: { trigger: 'axis' },
    legend: { data: series.map((s) => s.name), top: 0, right: 0, ...legendStyle },
    grid: { left: 8, right: 18, top: 34, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLabel: { ...axisLabelStyle, hideOverlap: true },
      axisLine: categoryAxisLine,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: axisLabelStyle,
      splitLine,
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: series.map((s, i) => {
      const color = s.color ?? CHART_COLORS[i % CHART_COLORS.length]
      return {
        name: s.name,
        type: 'line' as const,
        smooth: true,
        showSymbol: false,
        symbolSize: 6,
        lineStyle: { width: 2, color },
        itemStyle: { color },
        areaStyle: s.area ? { opacity: 0.12, color } : undefined,
        data: s.data,
      }
    }),
  }
  return option
}

/** 纵向柱状图（支持多系列） */
export function barChartOption(labels: string[], series: ChartSeries[]): EChartsOption {
  const option: EChartsOption = {
    color: CHART_COLORS,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: series.length > 1 ? { data: series.map((s) => s.name), top: 0, right: 0, ...legendStyle } : { show: false },
    grid: { left: 8, right: 18, top: series.length > 1 ? 34 : 12, bottom: 6, containLabel: true },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { ...axisLabelStyle, hideOverlap: true, rotate: labels.length > 8 ? 28 : 0 },
      axisLine: categoryAxisLine,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: axisLabelStyle,
      splitLine,
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: series.map((s, i) => ({
      name: s.name,
      type: 'bar' as const,
      barMaxWidth: 30,
      itemStyle: { color: s.color ?? CHART_COLORS[i % CHART_COLORS.length], borderRadius: [3, 3, 0, 0] },
      data: s.data,
    })),
  }
  return option
}

/** 环形占比饼图 */
export function pieChartOption(items: ChartItem[]): EChartsOption {
  const option: EChartsOption = {
    color: CHART_COLORS,
    tooltip: { trigger: 'item' },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 4,
      top: 'middle',
      ...legendStyle,
    },
    series: [
      {
        type: 'pie' as const,
        radius: ['46%', '72%'],
        center: ['36%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#ffffff', borderWidth: 2, borderRadius: 4 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 12, fontWeight: 'bold' } },
        data: items.map((i) => ({ name: i.name, value: i.value })),
      },
    ],
  }
  return option
}

/** 横向条形图（TOP 排行 / 模型对比） */
export function hbarChartOption(items: ChartItem[], color = CHART_COLORS[0]): EChartsOption {
  // category 轴第一条在最下方，升序排列让最大值显示在顶部
  const ascending = [...items].sort((a, b) => a.value - b.value)
  const option: EChartsOption = {
    color: [color],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 28, top: 8, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: axisLabelStyle,
      splitLine,
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'category',
      data: ascending.map((i) => i.name),
      axisLabel: { color: '#6b7280', fontSize: 11, width: 130, overflow: 'truncate' },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar' as const,
        barMaxWidth: 18,
        itemStyle: { color, borderRadius: [0, 3, 3, 0] },
        data: ascending.map((i) => i.value),
      },
    ],
  }
  return option
}

/** 空图表占位配置（数据为空时不渲染图表，这里仅作为兜底） */
export function emptyChartOption(): EChartsOption {
  return { series: [] }
}
