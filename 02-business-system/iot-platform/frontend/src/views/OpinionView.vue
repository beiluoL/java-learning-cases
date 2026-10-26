<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { storeToRefs } from 'pinia';
import EChart from '@/components/EChart.vue';
import OpinionFeed from '@/components/OpinionFeed.vue';
import StatCard from '@/components/StatCard.vue';
import { useRealtimeStore } from '@/stores/realtime';
import {
  fetchOpinionList,
  fetchOpinionStat,
  fetchOpinionTrend,
  fetchHotTopics,
} from '@/api/opinion';
import { simulateOpinion } from '@/api/simulator';
import type { OpinionVO, OpinionStatVO, OpinionTrendVO, HotTopicVO } from '@/api/types';

const realtimeStore = useRealtimeStore();
const { opinions } = storeToRefs(realtimeStore);

const list = ref<OpinionVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);
const filters = ref({ sentiment: '', topic: '', source: '' });
const stat = ref<OpinionStatVO | null>(null);
const trend = ref<OpinionTrendVO[]>([]);
const hot = ref<HotTopicVO[]>([]);
const simulating = ref(false);

const sentimentOptions = [
  { label: '正面', value: 'POSITIVE' },
  { label: '负面', value: 'NEGATIVE' },
  { label: '中性', value: 'NEUTRAL' },
];

async function loadList() {
  const res = await fetchOpinionList({
    sentiment: filters.value.sentiment || undefined,
    topic: filters.value.topic || undefined,
    source: filters.value.source || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  });
  list.value = res.records;
  total.value = res.total;
}

async function loadStat() {
  stat.value = await fetchOpinionStat();
  trend.value = await fetchOpinionTrend(7);
  hot.value = await fetchHotTopics(7, 10);
}

async function onSimulate() {
  simulating.value = true;
  try {
    await simulateOpinion(8);
    ElMessage.success('已触发舆情模拟');
    await Promise.all([loadList(), loadStat()]);
  } finally {
    simulating.value = false;
  }
}

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['正面', '负面', '中性'], bottom: 0 },
  grid: { left: 40, right: 16, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: trend.value.map((t) => t.date) },
  yAxis: { type: 'value' },
  series: [
    { name: '正面', type: 'line', stack: 'total', areaStyle: {}, data: trend.value.map((t) => t.positive), itemStyle: { color: '#52c41a' } },
    { name: '负面', type: 'line', stack: 'total', areaStyle: {}, data: trend.value.map((t) => t.negative), itemStyle: { color: '#ff4d4f' } },
    { name: '中性', type: 'line', stack: 'total', areaStyle: {}, data: trend.value.map((t) => t.neutral), itemStyle: { color: '#1677ff' } },
  ],
}));

const hotOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 80, right: 16, top: 20, bottom: 30 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: hot.value.map((h) => h.topic).reverse() },
  series: [
    { type: 'bar', data: hot.value.map((h) => h.count).reverse(), itemStyle: { color: '#1677ff' } },
  ],
}));

onMounted(() => {
  loadList();
  loadStat();
});
</script>

<template>
  <div>
    <el-row :gutter="16" class="section-gap">
      <el-col :span="6">
        <StatCard title="舆情总量" :value="stat?.total ?? 0" icon="ChatDotRound" color="#1677ff" />
      </el-col>
      <el-col :span="6">
        <StatCard title="正面" :value="stat?.positive ?? 0" icon="SuccessFilled" color="#52c41a" />
      </el-col>
      <el-col :span="6">
        <StatCard title="负面" :value="stat?.negative ?? 0" icon="WarningFilled" color="#ff4d4f" />
      </el-col>
      <el-col :span="6">
        <StatCard title="情感指数" :value="(stat?.sentimentIndex ?? 0).toFixed(2)" icon="DataLine" color="#722ed1" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-gap">
      <el-col :span="14">
        <el-card shadow="hover">
          <div class="card-title">舆情趋势（近7天）</div>
          <EChart :option="trendOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <div class="card-title">热点话题 TOP</div>
          <EChart :option="hotOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="hover">
          <div class="card-title">
            舆情列表
            <el-button type="primary" size="small" :loading="simulating" @click="onSimulate">
              模拟生成舆情
            </el-button>
          </div>
          <div class="filters">
            <el-select v-model="filters.sentiment" placeholder="情感" clearable size="small" style="width: 120px">
              <el-option v-for="o in sentimentOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-input v-model="filters.topic" placeholder="话题" clearable size="small" style="width: 140px" />
            <el-input v-model="filters.source" placeholder="来源" clearable size="small" style="width: 120px" />
            <el-button size="small" type="primary" @click="loadList">查询</el-button>
          </div>
          <el-table :data="list" stripe>
            <el-table-column prop="topic" label="话题" width="120" />
            <el-table-column prop="content" label="内容" show-overflow-tooltip />
            <el-table-column prop="source" label="来源" width="90" />
            <el-table-column label="情感" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="row.sentiment === 'POSITIVE' ? 'success' : row.sentiment === 'NEGATIVE' ? 'danger' : 'info'"
                  size="small"
                  effect="dark"
                >
                  {{ row.sentiment === 'POSITIVE' ? '正面' : row.sentiment === 'NEGATIVE' ? '负面' : '中性' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="section-gap"
            background
            layout="prev, pager, next, total"
            :total="total"
            :page-size="pageSize"
            v-model:current-page="pageNum"
            @current-change="loadList"
          />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <div class="card-title">实时舆情流</div>
          <OpinionFeed :opinions="opinions ?? []" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
