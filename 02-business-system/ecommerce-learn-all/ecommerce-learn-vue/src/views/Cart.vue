<template>
  <div>
    <div class="page-title">购物车</div>
    <el-table :data="items" v-loading="loading" style="width:100%">
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
      <el-table-column label="数量" width="160">
        <template #default="{ row }">
          <el-input-number :model-value="row.quantity" :min="1" :max="row.stock" @change="(v) => changeQty(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="小计" width="120">
        <template #default="{ row }">¥{{ (row.price * row.quantity).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row.skuId)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="footer" v-if="items.length">
      <span>已选 {{ selectedCount }} 件，合计 <b class="total">¥{{ total.toFixed(2) }}</b></span>
      <el-button type="primary" @click="checkout">去结算</el-button>
    </div>
    <el-empty v-else description="购物车是空的" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCartList, updateCartQuantity, removeCartItem } from '../api/cart'

const router = useRouter()
const items = ref([])
const loading = ref(false)

const selectedCount = computed(() => items.value.filter((i) => i.checked === 1).reduce((s, i) => s + i.quantity, 0))
const total = computed(() =>
  items.value.filter((i) => i.checked === 1).reduce((s, i) => s + i.price * i.quantity, 0)
)

async function load() {
  loading.value = true
  try {
    items.value = await getCartList()
  } finally {
    loading.value = false
  }
}
async function changeQty(row, v) {
  await updateCartQuantity({ skuId: row.skuId, quantity: v })
  row.quantity = v
}
async function remove(skuId) {
  await removeCartItem(skuId)
  ElMessage.success('已删除')
  load()
}
function checkout() {
  if (!selectedCount.value) return ElMessage.warning('请选择商品')
  router.push('/checkout')
}

onMounted(load)
</script>

<style scoped>
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
.footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
}
.total {
  color: #f56c6c;
  font-size: 18px;
}
</style>
