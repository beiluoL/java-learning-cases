<script setup lang="ts">
import { ref } from 'vue'
import { penetrationGet, penetrationAttack } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import RedisStateCard from '@/components/RedisStateCard.vue'
import { ElMessage } from 'element-plus'

const id = ref(1)
const result = ref<any>(null)
const attackCount = ref(20)
const summary = ref<any>(null)
const stateRef = ref()

async function query() {
  const r = await penetrationGet(id.value)
  result.value = r.data
  ElMessage.info(result.value ? '命中（合法 id 走缓存查询）' : '返回空：被布隆拦截 / 空值缓存命中')
  stateRef.value?.refresh()
}
async function attack() {
  const r = await penetrationAttack(attackCount.value)
  summary.value = r.data
  ElMessage.success('攻击模拟完成，查看下方统计')
  stateRef.value?.refresh()
}
</script>

<template>
  <div class="wrap">
    <DemoPanel
      title="场景6：缓存穿透（布隆过滤器 + 空值缓存）"
      :desc="`恶意刷不存在的 id（如 -1、超长随机串），请求绕过缓存直击 DB。两道防线：① 布隆过滤器预热所有合法 id，不存在的 id 直接拦截，绝不查 DB；② 布隆放行但 DB 真没有时，缓存空对象（短 TTL），避免重复穿透。点「模拟攻击」批量打非法 id，观察 bloom_block 远大于 db 命中。`"
    >
      <el-form inline>
        <el-form-item label="商品ID"><el-input-number v-model="id" /></el-form-item>
        <el-form-item><el-button type="primary" @click="query">查询</el-button></el-form-item>
      </el-form>
      <el-alert v-if="result" :title="'查询结果'" :description="JSON.stringify(result)" type="success" :closable="false" />
      <el-divider />
      <el-form inline>
        <el-form-item label="攻击次数"><el-input-number v-model="attackCount" :min="1" :max="1000" /></el-form-item>
        <el-form-item><el-button type="danger" @click="attack">模拟批量攻击（非法 id）</el-button></el-form-item>
      </el-form>
      <el-alert v-if="summary" :title="'攻击统计'" :description="JSON.stringify(summary)" type="warning" :closable="false" />
    </DemoPanel>
    <RedisStateCard ref="stateRef" />
  </div>
</template>

<style scoped>
.wrap { display: flex; gap: 16px; flex-wrap: wrap; align-items: flex-start; }
</style>
