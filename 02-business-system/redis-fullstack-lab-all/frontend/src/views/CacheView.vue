<script setup lang="ts">
import { ref } from 'vue'
import { cacheGet, cacheUpdate, cacheDelete, cacheDemo } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const id = ref(1)
const name = ref('客制化机械键盘（改）')
const price = ref(499)
const result = ref<any>(null)
const demoResult = ref('')
const stateRef = ref()

async function get() {
  const r = await cacheGet(id.value)
  result.value = r.data
  ElMessage.success('已读取')
  stateRef.value?.refresh()
}
async function update() {
  const r = await cacheUpdate(id.value, name.value, price.value)
  result.value = r.data
  ElMessage.success('已更新 DB 并删除缓存')
  stateRef.value?.refresh()
}
async function del() {
  await cacheDelete(id.value)
  result.value = null
  ElMessage.success('缓存已删')
  stateRef.value?.refresh()
}
async function demo() {
  const r = await cacheDemo()
  demoResult.value = (r.data as string) || ''
  ElMessage.info('方法体真实只执行一次，第二次走缓存')
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景1：缓存加速（Cache-Aside）"
      :desc="`商品详情页高并发读，直接打 DB 扛不住。采用 Cache-Aside：读时先查缓存，未命中查 DB 并回填（带随机 TTL 防雪崩）；写时更新 DB 后【删除】缓存而非更新，保证最终一致。下方观测卡可看到 cache:hit / cache:miss / cache:db 的次数变化。`"
    >
      <el-form inline>
        <el-form-item label="商品ID"><el-input-number v-model="id" :min="1" /></el-form-item>
        <el-form-item><el-button type="primary" @click="get">读取商品</el-button></el-form-item>
        <el-form-item><el-button @click="update">更新商品</el-button></el-form-item>
        <el-form-item><el-button @click="del">删除缓存</el-button></el-form-item>
      </el-form>
      <el-form inline>
        <el-form-item label="新名称"><el-input v-model="name" /></el-form-item>
        <el-form-item label="新价格"><el-input-number v-model="price" :min="0" /></el-form-item>
      </el-form>
      <el-alert v-if="result" :title="'返回数据'" :description="JSON.stringify(result)" type="success" :closable="false" />
      <el-divider />
      <el-button @click="demo">调用 @RedisCacheable 方法（连点两次看效果）</el-button>
      <div v-if="demoResult" class="demores">{{ demoResult }}</div>
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
.demores { margin-top: 8px; color: #1890ff; }
</style>
