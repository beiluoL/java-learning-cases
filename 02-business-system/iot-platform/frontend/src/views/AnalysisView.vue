<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import EChart from '@/components/EChart.vue';
import RealtimeMetricGrid from '@/components/RealtimeMetricGrid.vue';
import AlertList from '@/components/AlertList.vue';
import { storeToRefs } from 'pinia';
import { useRealtimeStore } from '@/stores/realtime';
import { fetchRules } from '@/api/analysis';
import { simulateDeviceData } from '@/api/simulator';

const realtimeStore = useRealtimeStore();
const { metrics, alerts } = storeToRefs(realtimeStore);
const rules = ref<Record<string, { metric: string; min: number; max: number; unit: string }>>({});
const simulating = ref(false);

const snapshotList = computed(() => (metrics.value ? Object.values(metrics.value) : []));

const currentValuesOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 16, top: 20, bottom: 70 },
  xAxis: {
    type: 'category',
    data: snapshotList.value.map((s) => `${s.deviceCode}/${s.metric}`),
    axisLabel: { rotate: 45, fontSize: 10 },
  },
  yAxis: { type: 'value' },
  series: [
    {
      type: 'bar',
      data: snapshotList.value.map((s) => Number(s.currentValue)),
      itemStyle: {
        color: (params: { dataIndex: number }) =>
          snapshotList.value[params.dataIndex]?.status === 'ABNORMAL' ? '#ff4d4f' : '#1677ff',
      },
    },
  ],
}));

async function loadRules() {
  rules.value = await fetchRules();
}

async function onSimulate() {
  simulating.value = true;
  try {
    await simulateDeviceData(8);
    ElMessage.success('已触发设备数据模拟');
  } finally {
    simulating.value = false;
  }
}

onMounted(loadRules);
</script>

<template>
  <div>
    <el-row :gutter="16" class="section-gap">
      <el-col :span="16">
        <el-card shadow="hover">
          <div class="card-title">
            实时指标快照
            <el-button type="primary" size="small" :loading="simulating" @click="onSimulate">
              模拟推送数据
            </el-button>
          </div>
          <RealtimeMetricGrid :snapshots="snapshotList" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-title">实时告警</div>
          <AlertList :alerts="alerts ?? []" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-gap">
      <el-col :span="14">
        <el-card shadow="hover">
          <div class="card-title">当前指标值（异常标红）</div>
          <EChart :option="currentValuesOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <div class="card-title">阈值规则</div>
          <el-table :data="Object.values(rules)" stripe size="small">
            <el-table-column prop="metric" label="指标" />
            <el-table-column label="正常范围">
              <template #default="{ row }">[{{ row.min }}, {{ row.max }}] {{ row.unit }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
