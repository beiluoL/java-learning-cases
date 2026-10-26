<template>
  <div v-loading="loading">
    <div class="page-title">确认订单</div>
    <el-card>
      <el-table :data="preview" style="width:100%">
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="cell">
              <img :src="row.image" class="thumb" />
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="数量" width="100" prop="quantity" />
        <el-table-column label="小计" width="120">
          <template #default="{ row }">¥{{ (row.price * row.quantity).toFixed(2) }}</template>
        </el-table-column>
      </el-table>
      <div class="amount">应付金额：<b>¥{{ amount.toFixed(2) }}</b></div>
    </el-card>

    <el-card class="addr">
      <el-form :model="form" label-width="80px">
        <el-form-item label="收货人" required>
          <el-input v-model="form.receiverName" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.receiverPhone" />
        </el-form-item>
        <el-form-item label="地址" required>
          <el-input v-model="form.receiverAddress" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="submitting" @click="submit">提交订单并支付</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { getCartList } from '../api/cart'
import { createOrder, payOrder } from '../api/order'

const route = useRoute()
const router = useRouter()
const preview = ref([])
const loading = ref(false)
const submitting = ref(false)
const form = reactive({ receiverName: '', receiverPhone: '', receiverAddress: '' })

const amount = computed(() => preview.value.reduce((s, i) => s + i.price * i.quantity, 0))

async function load() {
  loading.value = true
  try {
    const skuId = route.query.skuId
    if (skuId) {
      const qty = Number(route.query.quantity || 1)
      const detail = await getProductDetail(route.params.id || route.query.spuId)
      const sku = detail.skus.find((s) => String(s.id) === String(skuId))
      preview.value = [{
        title: detail.title,
        image: sku?.image || detail.mainImage,
        price: sku?.price,
        quantity: qty,
        skuId
      }]
    } else {
      const cart = await getCartList()
      preview.value = cart
        .filter((c) => c.checked === 1)
        .map((c) => ({ title: c.title, image: c.image, price: c.price, quantity: c.quantity, skuId: c.skuId }))
    }
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.receiverName || !form.receiverPhone || !form.receiverAddress) {
    return ElMessage.warning('请填写完整收货信息')
  }
  submitting.value = true
  try {
    const skuId = route.query.skuId
    const dto = {
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress,
      useCart: !skuId
    }
    if (skuId) {
      dto.items = [{ skuId: Number(skuId), quantity: Number(route.query.quantity || 1) }]
    }
    const order = await createOrder(dto)
    const pay = await payOrder(order.orderId, 'alipay')
    ElMessage.success('下单成功，模拟支付中')
    // 模拟支付回调：本地直接通知后端标记已支付
    await fetch(`/api/pay/notify`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ channel: 'alipay', orderNo: order.orderNo, tradeNo: pay.prepayTradeNo, success: true, amount: pay.amount })
    }).catch(() => {})
    router.push('/orders')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.addr {
  margin-top: 16px;
}
.cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
}
.amount {
  margin-top: 12px;
  text-align: right;
}
.amount b {
  color: #f56c6c;
  font-size: 18px;
}
</style>
