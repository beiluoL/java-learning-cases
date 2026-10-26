<template>
  <div>
    <a-form layout="inline" :model="q" style="margin-bottom: 16px">
      <a-form-item label="操作类型">
        <a-input v-model:value="q.action" placeholder="如 PAY/REFUND" allow-clear />
      </a-form-item>
      <a-form-item><a-button type="primary" @click="load">查询</a-button></a-form-item>
    </a-form>
    <a-table :columns="columns" :data-source="data" row-key="id" :pagination="{ pageSize: 10 }" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi } from '@/api'

const q = reactive({ action: '' })
const data = ref<any[]>([])
const columns = [
  { title: 'ID', dataIndex: 'id' },
  { title: '操作者', dataIndex: 'operator' },
  { title: '操作', dataIndex: 'action' },
  { title: '资源', dataIndex: 'resource' },
  { title: '业务ID', dataIndex: 'bizId' },
  { title: 'IP', dataIndex: 'ip' },
  { title: '结果', dataIndex: 'result' },
  { title: '时间', dataIndex: 'occurTime' }
]

async function load() {
  const res: any = await auditApi.list({ action: q.action || undefined, limit: 200 })
  data.value = res.data || []
}
onMounted(load)
</script>
