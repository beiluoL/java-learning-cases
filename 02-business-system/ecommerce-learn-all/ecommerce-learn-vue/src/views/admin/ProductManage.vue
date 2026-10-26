<template>
  <div>
    <div class="page-title">商品管理</div>
    <el-button type="primary" @click="dialog = true">新增商品</el-button>
    <el-table :data="list" v-loading="loading" style="margin-top:12px">
      <el-table-column prop="id" label="ID" width="100" />
      <el-table-column prop="title" label="标题" />
      <el-table-column label="价格" width="160">
        <template #default="{ row }">
          {{ row.priceMin && row.priceMax && row.priceMin !== row.priceMax
            ? `¥${row.priceMin}~${row.priceMax}` : '¥' + (row.priceMin || row.priceMax) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination layout="prev,pager,next" :total="total" :current-page="current" @current-change="onPage" />

    <el-dialog v-model="dialog" title="新增商品" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="分类ID"><el-input-number v-model="form.categoryId" :min="1" /></el-form-item>
        <el-form-item label="品牌ID"><el-input-number v-model="form.brandId" :min="0" /></el-form-item>
        <el-form-item label="规格描述"><el-input v-model="form.specs" placeholder='如 颜色:黑;存储:256G' /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchProducts } from '../../api/product'
import { saveProduct, deleteProduct } from '../../api/admin'

const list = ref([])
const total = ref(0)
const current = ref(1)
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ title: '', categoryId: 1002, brandId: 2001, specs: '', price: 0, stock: 0 })

async function load() {
  loading.value = true
  try {
    const data = await searchProducts({ current: current.value, size: 10 })
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
async function save() {
  const specs = {}
  form.specs.split(';').forEach((kv) => {
    const [k, v] = kv.split(':')
    if (k && v) specs[k.trim()] = v.trim()
  })
  await saveProduct({
    title: form.title,
    categoryId: form.categoryId,
    brandId: form.brandId,
    status: 1,
    skus: [{ specs: JSON.stringify(specs), price: form.price, stock: form.stock, status: 1 }]
  })
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function remove(row) {
  await ElMessageBox.confirm('确认删除该商品？')
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
