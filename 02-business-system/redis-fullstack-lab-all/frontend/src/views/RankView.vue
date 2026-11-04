<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import { rankAdd, rankTop, rankOf } from '@/api/redislab'
import DemoPanel from '@/components/DemoPanel.vue'
import { ElMessage } from 'element-plus'

const member = ref('玩家A')
const score = ref(10)
const myMember = ref('玩家A')
const myRank = ref<any>(null)
const topList = ref<any[]>([])
const chartRef = ref<HTMLDivElement>()

async function add() {
  await rankAdd(member.value, score.value)
  ElMessage.success('已加分')
  await refreshTop()
}
async function refreshTop() {
  const r = await rankTop(10)
  topList.value = r.data || []
  renderChart()
}
async function queryRank() {
  const r = await rankOf(myMember.value)
  myRank.value = r.data
}
function renderChart() {
  nextTick(() => {
    if (!chartRef.value) return
    const chart = echarts.init(chartRef.value)
    const data = topList.value.map((i) => ({ name: i.member, value: i.score }))
    chart.setOption({
      tooltip: {},
      xAxis: { type: 'category', data: data.map((d) => d.name) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: data.map((d) => d.value), itemStyle: { color: '#5470c6' } }]
    })
  })
}
onMounted(refreshTop)
</script>

<template>
  <DemoPanel
    title="场景3：排行榜（ZSET）"
    :desc="`游戏积分榜 / 商品销量榜 / 热搜榜，需实时排序且高并发更新。用 ZSET：ZADD/ZINCRBY 加分、ZREVRANGE 取 TopN、ZREVRANK 查名次。这里演示「今日榜 + 总榜」。`"
  >
    <el-form inline>
      <el-form-item label="成员"><el-input v-model="member" /></el-form-item>
      <el-form-item label="加分"><el-input-number v-model="score" /></el-form-item>
      <el-form-item><el-button type="primary" @click="add">加分</el-button></el-form-item>
    </el-form>
    <el-form inline>
      <el-form-item label="查名次"><el-input v-model="myMember" /></el-form-item>
      <el-form-item><el-button @click="queryRank">查询</el-button></el-form-item>
      <el-alert
        v-if="myRank"
        :title="'名次结果'"
        :description="JSON.stringify(myRank)"
        type="success"
        :closable="false"
        style="width: 100%"
      />
    </el-form>
    <el-table :data="topList" border size="small">
      <el-table-column prop="rank" label="名次" width="80" />
      <el-table-column prop="member" label="成员" />
      <el-table-column prop="score" label="分数" />
    </el-table>
    <div ref="chartRef" style="width: 100%; height: 320px; margin-top: 12px"></div>
  </DemoPanel>
</template>
