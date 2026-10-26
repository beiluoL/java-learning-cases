<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import EChart from '@/components/EChart.vue';
import {
  fetchDeviceStatus,
  fetchMetricTrend,
  fetchOpinionOverview,
  fetchHotTopics,
} from '@/api/dashboard';
import type { StatusCountVO, OpinionStatVO, OpinionTrendVO, HotTopicVO } from '@/api/types';

const devices = ['dev-001', 'dev-002', 'dev-003', 'dev-004', 'dev-005'];
const colors = ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1'];
const selectedMetric = ref('temperature');

const deviceStatus = ref<StatusCountVO[]>([]);
const opinionStat = ref<OpinionStatVO | null>(null);
const opinionTrend = ref<OpinionTrendVO[]>([]);
const hot = ref<HotTopicVO[]>([]);

const comparison = ref<{ device: string; points: { time: string; value: number }[] }[]>([]);

async function loadStatus() {
  deviceStatus.value = await fetchDeviceStatus();
}

async function loadOpinion() {
  const res = await fetchOpinionOverview();
  opinionStat.value = res.stat;
  opinionTrend.value = res.trend;
  hot.value = res.hotTopics;
}

async function loadComparison() {
  const results = await Promise.all(
    devices.map(async (d) => ({
      device: d,
      points: await fetchMetricTrend(d, selectedMetric.value, 6),
    })),
  );
  comparison.value = results;
}

watch(selectedMetric, loadComparison);

const statusOption = computed(() => ({
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

const comparisonOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: comparison.value.map((c) => c.device), bottom: 0 },
  grid: { left: 50, right: 16, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: comparison.value[0]?.points.map((p) => p.time) ?? [] },
  yAxis: { type: 'value' },
  series: comparison.value.map((c, idx) => ({
    name: c.device,
    type: 'line',
    smooth: true,
    showSymbol: false,
    data: c.points.map((p) => p.value),
    itemStyle: { color: colors[idx % colors.length] },
  })),
}));

const sentimentOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['40%', '65%'],
      data: [
        { name: '正面', value: opinionStat.value?.positive ?? 0, itemStyle: { color: '#52c41a' } },
        { name: '负面', value: opinionStat.value?.negative ?? 0, itemStyle: { color: '#ff4d4f' } },
        { name: '中性', value: opinionStat.value?.neutral ?? 0, itemStyle: { color: '#1677ff' } },
      ],
    },
  ],
}));

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

const hotOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 80, right: 16, top: 20, bottom: 30 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: hot.value.map((h) => h.topic).reverse() },
  series: [{ type: 'bar', data: hot.value.map((h) => h.count).reverse(), itemStyle: { color: '#1677ff' } }],
}));

onMounted(() => {
  loadStatus();
  loadOpinion();
  loadComparison();
});
</script>

<template>
  <div>
    <el-row :gutter="16" class="section-gap">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">设备状态分布</div>
          <EChart :option="statusOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">舆情情感分布</div>
          <EChart :option="sentimentOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">热点话题 TOP</div>
          <EChart :option="hotOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-gap">
      <el-col :span="14">
        <el-card shadow="hover">
          <div class="card-title">
            多设备指标对比
            <el-select v-model="selectedMetric" size="small" style="width: 140px">
              <el-option v-for="m in ['temperature','humidity','power','voltage','co2']" :key="m" :label="m" :value="m" />
            </el-select>
          </div>
          <EChart :option="comparisonOption" height="340px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <div class="card-title">舆情趋势（近7天）</div>
          <EChart :option="opinionTrendOption" height="340px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
