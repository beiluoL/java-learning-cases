<template>
  <div class="admin-wrap">
    <div class="stat-grid">
      <div class="stat-card"><div class="label">总会话数</div><div class="value">{{ overview.totalConversations }}</div></div>
      <div class="stat-card"><div class="label">已转人工</div><div class="value" style="color:#d46b08;">{{ overview.escalatedConversations }}</div></div>
      <div class="stat-card"><div class="label">已解决</div><div class="value" style="color:#389e0d;">{{ overview.resolvedConversations }}</div></div>
      <div class="stat-card"><div class="label">平均满意度</div><div class="value" style="color:#1677ff;">{{ overview.satisfactionAverage?.toFixed(2) || '—' }}</div></div>
    </div>

    <div class="panel">
      <div class="panel-title">满意度分析</div>
      <el-row :gutter="16">
        <el-col :span="10">
          <div ref="chartRef" style="height:280px;"></div>
        </el-col>
        <el-col :span="14">
          <el-table :data="byIntent" size="small" border>
            <el-table-column prop="intent" label="意图">
              <template #default="{ row }">{{ intentLabel(row.intent) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="评价数" />
            <el-table-column prop="average" label="平均分">
              <template #default="{ row }">{{ Number(row.average).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
          <div style="margin-top:10px; color:#8a9099; font-size:12px;">
            共 {{ satisfaction.total }} 条评价；转人工会话占比
            {{ satisfaction.totalConversations ? Math.round((satisfaction.escalatedConversations / satisfaction.totalConversations) * 100) : 0 }}%
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="panel">
      <div class="panel-title">对话记录</div>
      <el-form :inline="true" class="filters">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:130px;">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="意图">
          <el-select v-model="filters.intent" placeholder="全部" clearable style="width:130px;">
            <el-option v-for="i in intentOptions" :key="i.value" :label="i.label" :value="i.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="标题/内容" clearable style="width:160px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="conversations" size="small" border stripe @row-dblclick="openDetail">
        <el-table-column prop="id" label="会话ID" width="120" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="意图" width="100">
          <template #default="{ row }">{{ row.intent ? intentLabel(row.intent) : '—' }}</template>
        </el-table-column>
        <el-table-column label="转人工" width="70">
          <template #default="{ row }">{{ row.escalated ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="80" />
        <el-table-column label="满意度" width="80">
          <template #default="{ row }">{{ row.satisfactionScore ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top:14px; justify-content:flex-end;"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="会话详情" size="46%">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="会话标题">{{ detail.conversation.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.conversation.status) }}</el-descriptions-item>
          <el-descriptions-item label="意图">{{ detail.conversation.intent ? intentLabel(detail.conversation.intent) : '—' }}</el-descriptions-item>
          <el-descriptions-item label="满意度">{{ detail.conversation.satisfactionScore ?? '未评价' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.ticket" style="margin:14px 0; padding:10px; background:#fff7e6; border:1px solid #ffd591; border-radius:8px;">
          <b>转人工工单 #{{ detail.ticket.id }}</b>
          <div style="font-size:13px; color:#8a9099; margin-top:4px;">原因：{{ detail.ticket.reason }} · 状态：{{ detail.ticket.status }}</div>
          <div style="font-size:13px; margin-top:4px;">{{ detail.ticket.summary }}</div>
        </div>

        <div class="chat-body" style="background:#f5f7fa; border-radius:8px; padding:10px; max-height:50vh; overflow:auto;">
          <div v-for="m in detail.messages" :key="m.id" class="msg-row" :class="m.role === 'user' ? 'user' : 'bot'">
            <div class="avatar" :class="m.role === 'user' ? 'user' : 'bot'">{{ m.role === 'user' ? '🙂' : '🤖' }}</div>
            <div class="bubble" :style="m.role === 'user' ? 'background:#1677ff;color:#fff;' : ''">{{ m.content }}</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import * as echarts from 'echarts';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getOverview, getSatisfaction, getAdminConversations,
  getConversationDetail, deleteConversation
} from '../api/client';
import type { SessionSummary, ConversationDetail } from '../types';

const overview = ref<any>({});
const satisfaction = ref<any>({ distribution: {}, byIntent: [], total: 0, escalatedConversations: 0, totalConversations: 0 });
const byIntent = ref<any[]>([]);
const conversations = ref<SessionSummary[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const filters = ref({ status: '', intent: '', keyword: '' });
const detailVisible = ref(false);
const detail = ref<ConversationDetail | null>(null);
const chartRef = ref<HTMLElement | null>(null);
let chart: echarts.ECharts | null = null;

const statusOptions = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'ESCALATED', label: '已转人工' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' }
];
const intentOptions = [
  { value: 'REFUND', label: '退款' },
  { value: 'ORDER_QUERY', label: '查询订单' },
  { value: 'TECH_SUPPORT', label: '技术支持' }
];

const statusMap: Record<string, { label: string; tag: string }> = {
  ACTIVE: { label: '进行中', tag: 'info' },
  ESCALATED: { label: '已转人工', tag: 'warning' },
  RESOLVED: { label: '已解决', tag: 'success' },
  CLOSED: { label: '已关闭', tag: 'default' }
};
const intentLabelMap: Record<string, string> = {
  REFUND: '退款', ORDER_QUERY: '查询订单', TECH_SUPPORT: '技术支持', UNKNOWN: '未知'
};

function statusLabel(s: string) { return statusMap[s]?.label || s; }
function statusTag(s: string): any { return (statusMap[s]?.tag as any) || 'info'; }
function intentLabel(i: string) { return intentLabelMap[i] || i; }

async function loadOverview() { overview.value = await getOverview(); }
async function loadSatisfaction() {
  satisfaction.value = await getSatisfaction();
  byIntent.value = satisfaction.value.byIntent || [];
  renderChart();
}
async function loadConversations() {
  const res = await getAdminConversations({ ...filters.value, page: page.value - 1, size: size.value });
  conversations.value = res.content;
  total.value = res.total;
}
function search() { page.value = 1; loadConversations(); }
function resetFilters() { filters.value = { status: '', intent: '', keyword: '' }; search(); }
function onPage(p: number) { page.value = p; loadConversations(); }

async function openDetail(row: SessionSummary) {
  detail.value = await getConversationDetail(row.id);
  detailVisible.value = true;
}
async function remove(row: SessionSummary) {
  try {
    await ElMessageBox.confirm('确认删除该会话及其消息？', '提示', { type: 'warning' });
    await deleteConversation(row.id);
    ElMessage.success('已删除');
    loadConversations();
  } catch (e) { /* cancel */ }
}

function renderChart() {
  if (!chartRef.value) return;
  if (!chart) chart = echarts.init(chartRef.value);
  const dist = satisfaction.value.distribution || {};
  const data = [1, 2, 3, 4, 5].map((s) => ({ name: s + ' 星', value: dist[s] || 0 }));
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '68%'], center: ['50%', '45%'],
      label: { formatter: '{b}: {c}' },
      data
    }]
  });
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadSatisfaction(), loadConversations()]);
  await nextTick();
  renderChart();
});
</script>
