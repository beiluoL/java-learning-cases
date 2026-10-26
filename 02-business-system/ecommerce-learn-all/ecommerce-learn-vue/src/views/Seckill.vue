<template>
  <div>
    <div class="page-title">限时秒杀</div>
    <div class="product-grid" v-loading="loading">
      <el-card v-for="a in list" :key="a.id" shadow="hover" class="sk-card">
        <div class="title">{{ a.title }}</div>
        <div class="price">秒杀价 ¥{{ a.price }}</div>
        <div class="meta">已售 {{ a.sold }} / 库存 {{ a.stock }}</div>
        <el-button type="danger" :disabled="a.status !== 1" @click="buy(a)">
          {{ a.status === 1 ? '立即秒杀' : '未开始/已结束' }}
        </el-button>
      </el-card>
    </div>
    <el-empty v-if="!loading && !list.length" description="暂无秒杀活动" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSeckillList, doSeckill } from '../api/seckill'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await getSeckillList()
  } finally {
    loading.value = false
  }
}
async function buy(a) {
  try {
    const res = await doSeckill(a.id, a.skuId, 1)
    ElMessage.success(res.message || '秒杀成功')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<style scoped>
.sk-card {
  text-align: center;
}
.title {
  font-weight: 600;
  margin-bottom: 8px;
}
.price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: 700;
}
.meta {
  color: #999;
  font-size: 12px;
  margin: 6px 0 12px;
}
</style>
