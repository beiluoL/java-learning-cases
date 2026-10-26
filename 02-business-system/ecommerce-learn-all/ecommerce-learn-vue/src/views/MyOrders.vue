<template>
  <div>
    <div class="page-title">我的订单</div>
    <div v-loading="loading">
      <el-empty v-if="!list.length" description="暂无订单" />
      <el-card v-for="o in list" :key="o.id" class="order-card">
        <div class="order-head">
          <span>订单号：{{ o.orderNo }}</span>
          <el-tag :type="statusType(o.status)">{{ o.statusDesc }}</el-tag>
          <span class="time">{{ o.createdAt }}</span>
        </div>
        <el-table :data="o.items || []" size="small">
          <el-table-column label="商品">
            <template #default="{ row }">{{ row.skuName }}</template>
          </el-table-column>
          <el-table-column label="单价" width="100">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="80" />
        </el-table>
        <div class="order-foot">
          <span>合计：<b>¥{{ o.payAmount }}</b></span>
          <div>
            <el-button v-if="o.status === 0" size="small" type="danger" @click="cancel(o)">取消</el-button>
            <el-button v-if="o.status === 0" size="small" type="primary" @click="pay(o)">去支付</el-button>
            <el-button v-if="o.status === 2" size="small" type="success" @click="confirm(o)">确认收货</el-button>
            <span v-if="o.logisticsNo" class="logistics">物流：{{ o.logisticsCompany }} {{ o.logisticsNo }}</span>
          </div>
        </div>
      </el-card>
    </div>
    <el-pagination layout="prev,pager,next" :total="total" :current-page="current" @current-change="onPage" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyOrders, cancelOrder, confirmOrder, payOrder } from '../api/order'

const list = ref([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)

function statusType(s) {
  return { 0: 'warning', 1: 'primary', 2: 'success', 3: 'success', 4: 'info' }[s] || 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await getMyOrders({ current: current.value, size: 10 })
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
async function cancel(o) {
  await ElMessageBox.confirm('确认取消该订单？')
  await cancelOrder(o.id)
  ElMessage.success('已取消')
  load()
}
async function confirm(o) {
  await confirmOrder(o.id)
  ElMessage.success('已确认收货')
  load()
}
async function pay(o) {
  const pay = await payOrder(o.id, 'alipay')
  await fetch('/api/pay/notify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ channel: 'alipay', orderNo: o.orderNo, tradeNo: pay.prepayTradeNo, success: true })
  }).catch(() => {})
  ElMessage.success('支付成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.order-card {
  margin-bottom: 12px;
}
.order-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  color: #666;
  font-size: 13px;
}
.time {
  margin-left: auto;
}
.order-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.order-foot b {
  color: #f56c6c;
}
.logistics {
  color: #999;
  margin-left: 12px;
}
</style>
