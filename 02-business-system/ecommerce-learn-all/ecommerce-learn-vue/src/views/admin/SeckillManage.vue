<template>
  <div>
    <div class="page-title">秒杀管理</div>
    <el-button type="primary" @click="dialog = true">新增秒杀活动</el-button>
    <el-table :data="list" v-loading="loading" style="margin-top:12px">
      <el-table-column prop="title" label="标题" />
      <el-table-column label="秒杀价" width="120">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      <el-table-column label="库存/已售" width="140">
        <template #default="{ row }">{{ row.stock }} / {{ row.sold }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '进行中' : '未开始/已结束' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新增秒杀活动" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="SPU ID"><el-input-number v-model="form.spuId" :min="1" /></el-form-item>
        <el-form-item label="SKU ID"><el-input-number v-model="form.skuId" :min="1" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="秒杀价"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSeckillList } from '../../api/seckill'
import { saveSeckill } from '../../api/admin'

const list = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ spuId: 3001, skuId: 4001, title: '', price: 0, stock: 10 })

async function load() {
  loading.value = true
  try {
    list.value = await getSeckillList()
  } finally {
    loading.value = false
  }
}
async function save() {
  await saveSeckill({ ...form, status: 1 })
  ElMessage.success('已保存并预热库存')
  dialog.value = false
  load()
}
onMounted(load)
</script>
