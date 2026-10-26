<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6"><a-statistic title="支付渠道" :value="2" /></a-col>
      <a-col :span="6"><a-statistic title="今日订单(示例)" :value="orders.length" /></a-col>
      <a-col :span="6"><a-statistic title="账户数" :value="accounts.length" /></a-col>
      <a-col :span="6"><a-statistic title="审计条数" :value="audits.length" /></a-col>
    </a-row>
    <a-divider />
    <a-typography-paragraph>
      本脚手架面向金融与支付领域，提供统一支付渠道抽象、敏感数据加密、签名验签、操作审计、
      TCC 分布式事务与日终对账能力。左侧菜单可进入「交易查询 / 对账管理 / 审计日志 / 渠道配置」。
    </a-typography-paragraph>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { payApi, bankApi, auditApi } from '@/api'

const orders = ref<any[]>([])
const accounts = ref<any[]>([])
const audits = ref<any[]>([])

onMounted(async () => {
  try {
    orders.value = (await payApi.orders({ limit: 1000 })).data || []
  } catch {}
  try {
    accounts.value = (await bankApi.accounts()).data || []
  } catch {}
  try {
    audits.value = (await auditApi.list({ limit: 1000 })).data || []
  } catch {}
})
</script>
