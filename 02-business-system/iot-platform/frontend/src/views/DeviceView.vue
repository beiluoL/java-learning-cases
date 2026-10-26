<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import RealtimeMetricGrid from '@/components/RealtimeMetricGrid.vue';
import { fetchDevices, saveDevice, deleteDevice, fetchRealtime } from '@/api/device';
import { useRealtimeStore } from '@/stores/realtime';
import type { DeviceVO, DeviceMetricSnapshot } from '@/api/types';

const devices = ref<DeviceVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);
const realtimeSnapshots = ref<DeviceMetricSnapshot[]>([]);
const realtimeStore = useRealtimeStore();

const dialogVisible = ref(false);
const form = reactive({
  deviceCode: '',
  deviceName: '',
  deviceType: 'sensor',
  protocol: 'MQTT',
  location: '',
});
const saving = ref(false);

async function loadDevices() {
  const res = await fetchDevices({
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  });
  devices.value = res.records;
  total.value = res.total;
}

async function loadRealtime() {
  realtimeSnapshots.value = await fetchRealtime();
}

function openDialog() {
  form.deviceCode = '';
  form.deviceName = '';
  form.deviceType = 'sensor';
  form.protocol = 'MQTT';
  form.location = '';
  dialogVisible.value = true;
}

async function submit() {
  if (!form.deviceCode || !form.deviceName) {
    ElMessage.warning('设备编码与名称为必填');
    return;
  }
  saving.value = true;
  try {
    await saveDevice({ ...form });
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await loadDevices();
  } finally {
    saving.value = false;
  }
}

async function remove(row: DeviceVO) {
  await ElMessageBox.confirm(`确认删除设备 ${row.deviceCode} ?`, '提示', { type: 'warning' });
  await deleteDevice(row.id);
  ElMessage.success('已删除');
  await loadDevices();
}

onMounted(() => {
  loadDevices();
  loadRealtime();
  void realtimeStore;
});
</script>

<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="hover">
          <div class="card-title">
            设备档案
            <el-button type="primary" size="small" @click="openDialog">新增设备</el-button>
          </div>
          <el-table :data="devices" stripe>
            <el-table-column prop="deviceCode" label="设备编码" />
            <el-table-column prop="deviceName" label="名称" />
            <el-table-column prop="deviceType" label="类型" />
            <el-table-column prop="protocol" label="协议" />
            <el-table-column prop="location" label="位置" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" effect="dark">
                  {{ row.status === 'ONLINE' ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="remove(row)">删除</el-button>
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
            @current-change="loadDevices"
          />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <div class="card-title">实时指标（来自实时通道/接口）</div>
          <RealtimeMetricGrid :snapshots="realtimeSnapshots" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="新增设备" width="480px">
      <el-form label-width="90px">
        <el-form-item label="设备编码" required>
          <el-input v-model="form.deviceCode" placeholder="如 dev-006" />
        </el-form-item>
        <el-form-item label="设备名称" required>
          <el-input v-model="form.deviceName" placeholder="如 车间温度传感器" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-input v-model="form.deviceType" />
        </el-form-item>
        <el-form-item label="通信协议">
          <el-input v-model="form.protocol" />
        </el-form-item>
        <el-form-item label="安装位置">
          <el-input v-model="form.location" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
