<template>
  <div>
    <div class="page-title">商品列表</div>
    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="搜索商品" clearable style="width:240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">搜索</el-button>
    </div>
    <div class="product-grid" v-loading="loading">
      <el-card v-for="p in list" :key="p.id" shadow="hover" @click="goDetail(p.id)" class="card">
        <img :src="p.mainImage" class="cover" />
        <div class="title">{{ p.title }}</div>
        <div class="price">¥{{ priceText(p) }}</div>
      </el-card>
    </div>
    <el-pagination
      class="pager"
      layout="prev, pager, next"
      :total="total"
      :current-page="current"
      :page-size="size"
      @current-change="onPage"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchProducts } from '../api/product'

const router = useRouter()
const list = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(12)
const keyword = ref('')
const loading = ref(false)

function priceText(p) {
  if (p.priceMin && p.priceMax && p.priceMin !== p.priceMax) {
    return `${p.priceMin} ~ ${p.priceMax}`
  }
  return p.priceMin || p.priceMax || '0'
}

async function load() {
  loading.value = true
  try {
    const data = await searchProducts({ keyword: keyword.value, current: current.value, size: size.value })
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
function goDetail(id) {
  router.push(`/product/${id}`)
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.card {
  cursor: pointer;
}
.cover {
  width: 100%;
  height: 160px;
  object-fit: cover;
  border-radius: 6px;
}
.title {
  margin: 8px 0 4px;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price {
  color: #f56c6c;
  font-weight: 600;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
