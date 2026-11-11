<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { budgetApi, type BudgetPayload } from '@/api/budget'
import type { BudgetPolicy } from '@/api/types'
import { isEmptyOf, toErrorMessage, useResource } from '@/composables/useAsyncData'
import { BUDGET_TYPE_OPTIONS, DEFAULT_CURRENCY } from '@/constants'
import { formatMoney, formatToken } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import AsyncState from '@/components/AsyncState.vue'
import ProgressBar from '@/components/ProgressBar.vue'
import DataTable from '@/components/DataTable.vue'
import Modal from '@/components/Modal.vue'

const list = useResource(() => budgetApi.list())
const status = useResource(() => budgetApi.status())

onMounted(() => {
  list.execute()
  status.execute()
})

function refresh() {
  list.execute()
  status.execute()
}

const columns: TableColumn[] = [
  { key: 'id', title: 'ID', width: '150px' },
  { key: 'budgetType', title: '类型', width: '110px' },
  { key: 'targetId', title: '目标', width: '130px' },
  { key: 'dailyLimit', title: '日限额', align: 'right', render: (v) => formatMoney(v) },
  { key: 'monthlyLimit', title: '月限额', align: 'right', render: (v) => formatMoney(v) },
  { key: 'perRequestLimit', title: '单请求限额', align: 'right', render: (v) => formatMoney(v) },
  { key: 'maxInputTokens', title: '最大输入 Token', align: 'right', render: (v) => formatToken(v) },
  { key: 'maxOutputTokens', title: '最大输出 Token', align: 'right', render: (v) => formatToken(v) },
  { key: 'allowOverrun', title: '允许超限', width: '100px' },
  { key: 'enabled', title: '状态', width: '90px' },
  { key: 'currency', title: '币种', width: '80px' },
  { key: 'remark', title: '备注' },
  { key: 'actions', title: '操作', width: '150px' },
]

function toPayload(p: BudgetPolicy): BudgetPayload {
  return {
    budgetType: p.budgetType,
    targetId: p.targetId,
    dailyLimit: p.dailyLimit,
    monthlyLimit: p.monthlyLimit,
    perRequestLimit: p.perRequestLimit,
    maxInputTokens: p.maxInputTokens,
    maxOutputTokens: p.maxOutputTokens,
    allowOverrun: p.allowOverrun,
    enabled: p.enabled,
    currency: p.currency,
    remark: p.remark,
  }
}

function isEnabled(row: unknown): boolean {
  return Boolean((row as BudgetPolicy).enabled)
}

/* ---------------- 新增 / 编辑 ---------------- */
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const actionError = ref<string | null>(null)

function emptyForm(): BudgetPayload {
  return {
    budgetType: 'GLOBAL',
    targetId: '',
    dailyLimit: '0',
    monthlyLimit: '0',
    perRequestLimit: '0',
    maxInputTokens: 0,
    maxOutputTokens: 0,
    allowOverrun: false,
    enabled: true,
    currency: DEFAULT_CURRENCY,
    remark: '',
  }
}

const form = reactive<BudgetPayload>(emptyForm())

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  Object.assign(form, emptyForm())
  formError.value = null
  formOpen.value = true
}

function openEdit(row: unknown) {
  const policy = row as BudgetPolicy
  formMode.value = 'edit'
  editingId.value = policy.id
  Object.assign(form, toPayload(policy))
  formError.value = null
  formOpen.value = true
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  formError.value = null
  const payload: BudgetPayload = {
    ...form,
    maxInputTokens: Number(form.maxInputTokens),
    maxOutputTokens: Number(form.maxOutputTokens),
  }
  try {
    if (formMode.value === 'create') await budgetApi.create(payload)
    else await budgetApi.update(editingId.value, payload)
    formOpen.value = false
    refresh()
  } catch (err) {
    formError.value = toErrorMessage(err)
  } finally {
    submitting.value = false
  }
}

/** 停用：契约未提供 DELETE，按 PUT /budgets/{id} 提交 enabled=false */
async function disable(row: unknown) {
  const policy = row as BudgetPolicy
  if (!window.confirm(`确认停用预算策略「${policy.id}」？`)) return
  actionError.value = null
  try {
    await budgetApi.update(policy.id, { ...toPayload(policy), enabled: false })
    refresh()
  } catch (err) {
    actionError.value = toErrorMessage(err)
  }
}
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Budget Governance"
      title="预算管理"
      description="实时状态来自 /budgets/status（含 Redis 预扣）；策略维护走 /budgets 的 POST 与 PUT。"
    />

    <div v-if="actionError" class="action-error" role="alert">
      <span>{{ actionError }}</span>
      <button class="btn btn--sm btn--ghost" type="button" @click="actionError = null">关闭</button>
    </div>

    <!-- 预算卡片 -->
    <section class="stack">
      <div class="row row--between">
        <h3 class="section-title">预算实时状态</h3>
        <span class="text-muted">按 usageRate 着色：≥70% 警告、≥90% 危险</span>
      </div>
      <AsyncState
        :loading="status.loading" :error="status.error" :error-trace-id="status.errorTraceId"
        :empty="isEmptyOf(status)" empty-text="暂无预算状态数据" min-height="150px"
        @retry="status.execute"
      >
        <div class="budget-grid">
          <div
            v-for="(s, i) in status.data ?? []"
            :key="`${s.budgetType}-${s.targetId}-${i}`"
            class="budget-card panel"
          >
            <div class="budget-card__head">
              <span class="badge badge--primary">{{ s.budgetType }}</span>
              <span class="text-muted mono text-truncate">{{ s.targetId || '—' }}</span>
            </div>
            <ProgressBar :value="s.usageRate" label="使用率" />
            <dl class="budget-card__stats">
              <div><dt>限额 limitAmount</dt><dd class="mono">{{ formatMoney(s.limitAmount) }}</dd></div>
              <div><dt>已用 usedAmount</dt><dd class="mono">{{ formatMoney(s.usedAmount) }}</dd></div>
              <div><dt>预扣 reservedAmount</dt><dd class="mono">{{ formatMoney(s.reservedAmount) }}</dd></div>
              <div><dt>剩余 remainingAmount</dt><dd class="mono">{{ formatMoney(s.remainingAmount) }}</dd></div>
            </dl>
          </div>
        </div>
      </AsyncState>
    </section>

    <!-- 策略表格 -->
    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">预算策略</h3>
          <p class="panel__subtitle">budgetType · 限额 · Token 上限 · 允许超限 · 启停状态</p>
        </div>
        <button class="btn btn--primary btn--sm" type="button" @click="openCreate">新增策略</button>
      </header>
      <DataTable
        :columns="columns" :rows="list.data ?? []" row-key="id"
        :loading="list.loading" :error="list.error" :error-trace-id="list.errorTraceId"
        :empty="isEmptyOf(list)" empty-text="暂无预算策略，点击「新增策略」创建"
        @retry="list.execute"
      >
        <template #cell-allowOverrun="{ value }">
          <span class="badge" :class="value ? 'badge--warning' : 'badge--neutral'">{{ value ? '允许' : '禁止' }}</span>
        </template>
        <template #cell-enabled="{ value }">
          <span class="badge" :class="value ? 'badge--success' : 'badge--neutral'">{{ value ? '启用' : '停用' }}</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="row">
            <button class="btn btn--sm" type="button" @click.stop="openEdit(row)">编辑</button>
            <button
              class="btn btn--sm btn--danger" type="button"
              :disabled="!isEnabled(row)" @click.stop="disable(row)"
            >停用</button>
          </div>
        </template>
      </DataTable>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <Modal
      v-model="formOpen"
      :title="formMode === 'create' ? '新增预算策略' : '编辑预算策略'"
      :subtitle="formMode === 'create' ? 'POST /api/admin/ai/budgets' : `PUT /api/admin/ai/budgets/${editingId}`"
      width="640px"
    >
      <div v-if="formError" class="form-error" role="alert">
        <span>{{ formError }}</span>
        <button class="btn btn--sm" type="button" @click="submit">重试</button>
      </div>
      <div class="form-grid">
        <label class="field">
          <span class="field__label">预算类型 *</span>
          <select v-model="form.budgetType" class="select">
            <option v-for="o in BUDGET_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">目标 ID targetId</span>
          <input v-model="form.targetId" class="input" placeholder="留空表示全局" />
        </label>
        <label class="field">
          <span class="field__label">日限额 dailyLimit</span>
          <input v-model="form.dailyLimit" class="input" placeholder="0" />
        </label>
        <label class="field">
          <span class="field__label">月限额 monthlyLimit</span>
          <input v-model="form.monthlyLimit" class="input" placeholder="0" />
        </label>
        <label class="field">
          <span class="field__label">单请求限额 perRequestLimit</span>
          <input v-model="form.perRequestLimit" class="input" placeholder="0" />
        </label>
        <label class="field">
          <span class="field__label">币种 currency</span>
          <input v-model="form.currency" class="input" />
        </label>
        <label class="field">
          <span class="field__label">最大输入 Token</span>
          <input v-model.number="form.maxInputTokens" type="number" min="0" class="input" />
        </label>
        <label class="field">
          <span class="field__label">最大输出 Token</span>
          <input v-model.number="form.maxOutputTokens" type="number" min="0" class="input" />
        </label>
        <label class="field field--full">
          <span class="field__label">备注 remark</span>
          <textarea v-model="form.remark" class="textarea" placeholder="可选" />
        </label>
        <div class="field--full row">
          <label class="checkbox"><input v-model="form.allowOverrun" type="checkbox" /> 允许超限 allowOverrun</label>
          <label class="checkbox"><input v-model="form.enabled" type="checkbox" /> 启用 enabled</label>
        </div>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="formOpen = false">取消</button>
        <button class="btn btn--primary" type="button" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : (formMode === 'create' ? '确认新增' : '保存修改') }}
        </button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.budget-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(268px, 1fr)); gap: var(--space-4); }
.budget-card { padding: var(--space-4) var(--space-5); }
.budget-card__head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-4); }
.budget-card__stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: var(--space-4); }
.budget-card__stats dt { font-size: var(--font-xs); color: var(--color-text-muted); }
.budget-card__stats dd { margin: 2px 0 0; font-size: var(--font-base); }

.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.field--full { grid-column: 1 / -1; }
.form-error, .action-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: 10px 12px;
  border: 1px solid var(--color-danger-border);
  border-radius: var(--radius-sm);
  background: var(--color-danger-soft);
  color: var(--color-danger);
  font-size: var(--font-sm);
}
@media (max-width: 560px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
