<template>
  <div>
    <div style="margin-bottom: 16px">
      <a-button type="primary" @click="showForm = true">新增渠道配置</a-button>
    </div>
    <a-table :columns="columns" :data-source="data" row-key="id" :pagination="{ pageSize: 10 }" />
    <a-modal v-model:open="showForm" title="渠道配置" @ok="save">
      <a-form :model="form">
        <a-form-item label="渠道"><a-input v-model:value="form.channel" placeholder="alipay/wechat" /></a-form-item>
        <a-form-item label="商户号"><a-input v-model:value="form.merchantNo" /></a-form-item>
        <a-form-item label="AppId"><a-input v-model:value="form.appId" /></a-form-item>
        <a-form-item label="沙箱"><a-switch v-model:checked="form.sandbox" /></a-form-item>
        <a-form-item label="配置JSON"><a-textarea v-model:value="form.configJson" :rows="4" placeholder='{"privateKey":"...","alipayPublicKey":"..."}' /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { channelApi } from '@/api'
import { message } from 'ant-design-vue'

const data = ref<any[]>([])
const showForm = ref(false)
const form = reactive({ channel: '', merchantNo: '', appId: '', sandbox: false, configJson: '', enabled: true })

const columns = [
  { title: 'ID', dataIndex: 'id' },
  { title: '渠道', dataIndex: 'channel' },
  { title: '商户号', dataIndex: 'merchantNo' },
  { title: 'AppId', dataIndex: 'appId' },
  { title: '沙箱', dataIndex: 'sandbox' },
  { title: '启用', dataIndex: 'enabled' }
]

async function load() {
  const res: any = await channelApi.list()
  data.value = res.data || []
}
async function save() {
  await channelApi.save({ ...form })
  message.success('保存成功')
  showForm.value = false
  await load()
}
onMounted(load)
</script>
