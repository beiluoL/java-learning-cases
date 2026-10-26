<template>
  <div v-loading="loading">
    <div class="page-title">商品详情</div>
    <el-row :gutter="24" v-if="detail">
      <el-col :span="10">
        <img :src="detail.mainImage" class="big-img" />
      </el-col>
      <el-col :span="14">
        <h2>{{ detail.title }}</h2>
        <div class="brand">品牌：{{ detail.brandName }} ｜ 分类：{{ detail.categoryName }}</div>
        <div class="price">¥{{ priceRange }}</div>
        <div class="sku-list">
          <div class="sku-label">选择规格：</div>
          <el-radio-group v-model="selectedSkuId">
            <el-radio-button v-for="s in detail.skus" :key="s.id" :value="s.id">
              {{ skuSpecText(s) }} ｜ ¥{{ s.price }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="qty">
          数量：
          <el-input-number v-model="quantity" :min="1" :max="currentStock" />
          <span class="stock">库存 {{ currentStock }}</span>
        </div>
        <div class="actions">
          <el-button type="warning" @click="addCart">加入购物车</el-button>
          <el-button type="primary" @click="buyNow">立即购买</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail } from '../api/product'
import { addToCart } from '../api/cart'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(false)
const selectedSkuId = ref(null)
const quantity = ref(1)

const selectedSku = computed(() => detail.value?.skus.find((s) => s.id === selectedSkuId.value))
const currentStock = computed(() => selectedSku.value?.stock ?? 0)
const priceRange = computed(() => {
  if (!detail.value) return ''
  const { priceMin, priceMax } = detail.value
  return priceMin === priceMax ? `¥${priceMin}` : `¥${priceMin} ~ ¥${priceMax}`
})

function skuSpecText(s) {
  try {
    return Object.values(JSON.parse(s.specs || '{}')).join(' / ')
  } catch {
    return s.skuCode
  }
}

async function load() {
  loading.value = true
  try {
    detail.value = await getProductDetail(route.params.id)
    if (detail.value?.skus?.length) {
      selectedSkuId.value = detail.value.skus[0].id
    }
  } finally {
    loading.value = false
  }
}

async function addCart() {
  if (!selectedSkuId.value) return ElMessage.warning('请选择规格')
  await addToCart({ skuId: selectedSkuId.value, quantity: quantity.value })
  ElMessage.success('已加入购物车')
}

function buyNow() {
  if (!selectedSkuId.value) return ElMessage.warning('请选择规格')
  router.push({
    path: '/checkout',
    query: { spuId: detail.value.id, skuId: selectedSkuId.value, quantity: quantity.value }
  })
}

onMounted(load)
</script>

<style scoped>
.big-img {
  width: 100%;
  border-radius: 8px;
}
.brand {
  color: #888;
  margin: 8px 0;
}
.price {
  color: #f56c6c;
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 16px;
}
.sku-list {
  margin-bottom: 16px;
}
.sku-label {
  margin-bottom: 8px;
  color: #666;
}
.qty {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.stock {
  color: #999;
}
.actions {
  display: flex;
  gap: 12px;
}
</style>
