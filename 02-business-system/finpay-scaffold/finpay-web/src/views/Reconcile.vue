<template>
  <div>
    <a-form layout="inline" :model="q" style="margin-bottom: 16px">
      <a-form-item label="渠道">
        <a-select v-model:value="q.channel" style="width: 140px">
          <a-select-option value="alipay">支付宝</a-select-option>
          <a-select-option value="wechat">微信支付</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="对账日期">
        <a-date-picker v-model:value="q.date" value-format="YYYY-MM-DD" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="loading" @click="run">执行日终对账</a-button>
      </a-form-item>
    </a-form>

    <a-result v-if="report" status="info" :title="`对账日期 ${report.date} · 渠道 ${report.channel}`"
      :sub-title="`本地 ${report.localCount} 笔 / 渠道 ${report.channelCount} 笔 / 匹配 ${report.matchedCount} 笔 / 差错 ${report.mismatchCount} 笔`" />

    <a-table v-if="report" :columns="columns" :data-source="report.mismatches" row-key="outTradeNo" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { reconcileApi } from '@/api'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const q = reactive({ channel: 'alipay', date: dayjs().subtract(1, 'day').format('YYYY-MM-DD') })
const loading = ref(false)
const report = ref<any>(null)

const columns = [
  { title: '差错类型', dataIndex: ['type'] },
  { title: '商户订单号', dataIndex: 'outTradeNo' },
  { title: '说明', dataIndex: 'description' }
]

async function run() {
  loading.value = true
  try {
    const res: any = await reconcileApi.run({ channel: q.channel, date: q.date })
    report.value = res.data
    if (res.data.mismatchCount === 0) message.success('对账平，无差错')
  } finally {
    loading.value = false
  }
}
</script>
