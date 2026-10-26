<template>
  <div>
    <div class="page-title">订单管理</div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="orderNo" label="订单号" width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.statusDesc }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">¥{{ row.payAmount }}</template>
      </el-table-column>
      <el-table-column prop="receiverName" label="收货人" width="100" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" size="small" type="primary" @click="openShip(row)">发货</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination layout="prev,pager,next" :total="total" :current-page="current" @current-change="onPage" />

    <el-dialog v-model="shipDialog" title="发货" width="420px">
      <el-form :model="shipForm" label-width="90px">
        <el-form-item label="物流公司"><el-input v-model="shipForm.logisticsCompany" /></el-form-item>
        <el-form-item label="物流单号"><el-input v-model="shipForm.logisticsNo" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialog = false">取消</el-button>
        <el-button type="primary" @click="ship">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminOrders, adminShip } from '../../api/admin'

const list = ref([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)
const shipDialog = ref(false)
const currentId = ref(null)
const shipForm = reactive({ logisticsCompany: '', logisticsNo: '' })

async function load() {
  loading.value = true
  try {
    const data = await getAdminOrders({ current: current.value, size: 10 })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function onPage(p) {
  current.value = p
  load()
}
function openShip(row) {
  currentId.value = row.id
  shipForm.logisticsCompany = ''
  shipForm.logisticsNo = ''
  shipDialog.value = true
}
async function ship() {
  await adminShip(currentId.value, { ...shipForm })
  ElMessage.success('已发货')
  shipDialog.value = false
  load()
}
onMounted(load)
</script>
