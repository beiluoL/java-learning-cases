<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import StatCard from '@/components/StatCard.vue';
import EChart from '@/components/EChart.vue';
import RealtimeMetricGrid from '@/components/RealtimeMetricGrid.vue';
import AlertList from '@/components/AlertList.vue';
import OpinionFeed from '@/components/OpinionFeed.vue';
import { useRealtimeStore } from '@/stores/realtime';
import { fetchOverview, fetchOpinionOverview, fetchDeviceStatus, fetchMetricTrend } from '@/api/dashboard';
import type { DashboardOverviewVO, StatusCountVO } from '@/api/types';
import type { OpinionStatVO, OpinionTrendVO } from '@/api/types';

const realtimeStore = useRealtimeStore();
const { metrics, alerts, opinions, connected } = storeToRefs(realtimeStore);

const overview = ref<DashboardOverviewVO | null>(null);
const deviceStatus = ref<StatusCountVO[]>([]);
const opinionStat = ref<OpinionStatVO | null>(null);
const opinionTrend = ref<OpinionTrendVO[]>([]);

const selectedDevice = ref('dev-001');
const selectedMetric = ref('temperature');
const trendPoints = ref<{ time: string; value: number }[]>([]);
const devices = ['dev-001', 'dev-002', 'dev-003', 'dev-004', 'dev-005'];
const metricsList = ['temperature', 'humidity', 'power', 'voltage', 'co2'];

async function loadStatic() {
  overview.value = await fetchOverview();
  deviceStatus.value = await fetchDeviceStatus();
  const opinion = await fetchOpinionOverview();
  opinionStat.value = opinion.stat;
  opinionTrend.value = opinion.trend;
}

async function loadTrend() {
  trendPoints.value = await fetchMetricTrend(selectedDevice.value, selectedMetric.value, 6);
}

watch([selectedDevice, selectedMetric], () => {
  loadTrend();
});

const deviceStatusOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '65%'],
      data: deviceStatus.value.map((s) => ({
        name: s.status === 'ONLINE' ? '在线' : '离线',
        value: s.count,
        itemStyle: { color: s.status === 'ONLINE' ? '#52c41a' : '#bfbfbf' },
      })),
    },
  ],
}));

const sentimentOption = computed(() => {
  const s = opinionStat.value;
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        data: [
          { name: '正面', value: s?.positive ?? 0, itemStyle: { color: '#52c41a' } },
          { name: '负面', value: s?.negative ?? 0, itemStyle: { color: '#ff4d4f' } },
          { name: '中性', value: s?.neutral ?? 0, itemStyle: { color: '#1677ff' } },
        ],
      },
    ],
  };
});

const opinionTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['正面', '负面', '中性'], bottom: 0 },
  grid: { left: 40, right: 16, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: opinionTrend.value.map((t) => t.date) },
  yAxis: { type: 'value' },
  series: [
    { name: '正面', type: 'line', stack: 'total', areaStyle: {}, data: opinionTrend.value.map((t) => t.positive), itemStyle: { color: '#52c41a' } },
    { name: '负面', type: 'line', stack: 'total', areaStyle: {}, data: opinionTrend.value.map((t) => t.negative), itemStyle: { color: '#ff4d4f' } },
    { name: '中性', type: 'line', stack: 'total', areaStyle: {}, data: opinionTrend.value.map((t) => t.neutral), itemStyle: { color: '#1677ff' } },
  ],
}));

const metricTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 16, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: trendPoints.value.map((p) => p.time) },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: trendPoints.value.map((p) => p.value),
      itemStyle: { color: '#1677ff' },
      areaStyle: { color: 'rgba(22,119,255,0.15)' },
    },
  ],
}));

let timer: number | undefined;
onMounted(() => {
  loadStatic();
  loadTrend();
  timer = window.setInterval(() => {
    loadStatic();
    loadTrend();
  }, 5000);
});
onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
  }
});
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="16" class="section-gap">
      <el-col :span="6">
        <StatCard title="设备总数" :value="overview?.deviceTotal ?? 0" icon="Cpu" color="#1677ff" />
      </el-col>
      <el-col :span="6">
        <StatCard title="在线设备" :value="overview?.deviceOnline ?? 0" icon="Connection" color="#52c41a" />
      </el-col>
      <el-col :span="6">
        <StatCard title="24h数据点" :value="overview?.dataPointCount24h ?? 0" icon="DataLine" color="#722ed1" />
      </el-col>
      <el-col :span="6">
        <StatCard title="告警数" :value="overview?.alertCount ?? 0" icon="Warning" color="#ff4d4f" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-gap">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">设备状态分布</div>
          <EChart :option="deviceStatusOption" height="280px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">舆情情感分布</div>
          <EChart :option="sentimentOption" height="280px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">指标趋势</div>
          <div class="trend-filter">
            <el-select v-model="selectedDevice" size="small" style="width: 110px">
              <el-option v-for="d in devices" :key="d" :label="d" :value="d" />
            </el-select>
            <el-select v-model="selectedMetric" size="small" style="width: 110px">
              <el-option v-for="m in metricsList" :key="m" :label="m" :value="m" />
            </el-select>
          </div>
          <EChart :option="metricTrendOption" height="230px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-gap">
      <el-col :span="16">
        <el-card shadow="hover">
          <div class="card-title">
            实时指标快照
            <el-tag :type="connected ? 'success' : 'danger'" size="small" effect="plain">
              {{ connected ? '实时' : '离线' }}
            </el-tag>
          </div>
          <RealtimeMetricGrid :snapshots="metrics ? Object.values(metrics) : []" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">舆情趋势（近7天）</div>
          <EChart :option="opinionTrendOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">实时告警</div>
          <AlertList :alerts="alerts ?? []" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="card-title">实时舆情流</div>
          <OpinionFeed :opinions="opinions ?? []" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.trend-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
