<template>
  <div>
    <a-form layout="inline" :model="q" style="margin-bottom: 16px">
      <a-form-item label="渠道">
        <a-select v-model:value="q.channel" style="width: 140px" allow-clear placeholder="全部">
          <a-select-option value="alipay">支付宝</a-select-option>
          <a-select-option value="wechat">微信支付</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="q.status" style="width: 140px" allow-clear placeholder="全部">
          <a-select-option value="INIT">初始化</a-select-option>
          <a-select-option value="SUCCESS">成功</a-select-option>
          <a-select-option value="FAILED">失败</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="load">查询</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="showCreate = true">模拟下单</a-button>
      </a-form-item>
    </a-form>

    <a-table :columns="columns" :data-source="data" row-key="outTradeNo" :pagination="{ pageSize: 10 }" />

    <a-modal v-model:open="showCreate" title="模拟下单" @ok="create">
      <a-form :model="form">
        <a-form-item label="渠道"><a-select v-model:value="form.channel">
          <a-select-option value="alipay">支付宝</a-select-option>
          <a-select-option value="wechat">微信</a-select-option>
        </a-select></a-form-item>
        <a-form-item label="商户号"><a-input v-model:value="form.merchantNo" /></a-form-item>
        <a-form-item label="金额(元)"><a-input-number v-model:value="form.amount" :min="0.01" /></a-form-item>
        <a-form-item label="商品"><a-input v-model:value="form.subject" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { payApi } from '@/api'
import { message } from 'ant-design-vue'

const q = reactive({ channel: undefined as string | undefined, status: undefined as string | undefined })
const data = ref<any[]>([])
const showCreate = ref(false)
const form = reactive({ channel: 'alipay', merchantNo: 'MOCK_MCH', amount: 0.01, subject: '测试商品' })

const columns = [
  { title: '商户订单号', dataIndex: 'outTradeNo' },
  { title: '渠道交易号', dataIndex: 'tradeNo' },
  { title: '渠道', dataIndex: 'channel' },
  { title: '金额', dataIndex: 'amount' },
  { title: '状态', dataIndex: 'status' },
  { title: '创建时间', dataIndex: 'createTime' }
]

async function load() {
  const res: any = await payApi.orders({ channel: q.channel, status: q.status, limit: 200 })
  data.value = res.data || []
}
async function create() {
  try {
    const res: any = await payApi.create({ ...form, currency: 'CNY', tradeType: 'NATIVE' })
    message.success('下单成功，返回二维码/跳转信息')
    showCreate.value = false
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}
onMounted(load)
</script>
