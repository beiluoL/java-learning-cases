<template>
  <div v-loading="loading">
    <div class="page-title">数据看板</div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="c in cards" :key="c.label">
        <el-card class="stat">
          <div class="label">{{ c.label }}</div>
          <div class="value">{{ c.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getDashboardStats } from '../../api/admin'

const loading = ref(false)
const stats = ref({})
const cards = ref([])

async function load() {
  loading.value = true
  try {
    const s = await getDashboardStats()
    stats.value = s
    cards.value = [
      { label: '用户数', value: s.userCount },
      { label: '商品数(SPU)', value: s.productCount },
      { label: '订单数', value: s.orderCount },
      { label: '销售额', value: '¥' + (s.totalSales || 0) },
      { label: '已支付订单', value: s.paidOrderCount },
      { label: '今日订单', value: s.todayOrderCount },
      { label: '秒杀活动', value: s.seckillActivityCount },
      { label: '状态', value: '运行中' }
    ]
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.stat {
  text-align: center;
  margin-bottom: 16px;
}
.label {
  color: #888;
  font-size: 13px;
}
.value {
  font-size: 26px;
  font-weight: 700;
  color: var(--brand);
  margin-top: 6px;
}
</style>
