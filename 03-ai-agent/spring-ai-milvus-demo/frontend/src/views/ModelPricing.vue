<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pricingApi, type PricingPayload } from '@/api/pricing'
import type { Pricing } from '@/api/types'
import { isEmptyOf, toErrorMessage, useResource } from '@/composables/useAsyncData'
import { DEFAULT_CURRENCY, MODEL_TYPE_OPTIONS } from '@/constants'
import { formatDateTime, formatMoney } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import Modal from '@/components/Modal.vue'
import AsyncState from '@/components/AsyncState.vue'

const list = useResource(() => pricingApi.list())
onMounted(() => list.execute())

const columns: TableColumn[] = [
  { key: 'provider', title: 'Provider', width: '120px' },
  { key: 'modelName', title: '模型', width: '190px' },
  { key: 'modelType', title: '类型', width: '110px' },
  { key: 'inputPrice', title: '输入单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'outputPrice', title: '输出单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'cachedInputPrice', title: '缓存输入单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'currency', title: '币种', width: '80px' },
  { key: 'version', title: '版本', width: '120px' },
  { key: 'effectiveFrom', title: '生效时间', width: '170px', render: (v) => formatDateTime(v) },
  { key: 'effectiveTo', title: '失效时间', width: '170px', render: (v) => formatDateTime(v) },
  { key: 'enabled', title: '状态', width: '90px' },
  { key: 'demoFlag', title: '标记', width: '120px' },
  { key: 'actions', title: '操作', width: '210px' },
]

const historyColumns: TableColumn[] = [
  { key: 'version', title: '版本', width: '130px' },
  { key: 'provider', title: 'Provider', width: '120px' },
  { key: 'modelType', title: '类型', width: '100px' },
  { key: 'inputPrice', title: '输入单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'outputPrice', title: '输出单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'cachedInputPrice', title: '缓存单价', align: 'right', render: (v) => formatMoney(v) },
  { key: 'effectiveFrom', title: '生效时间', width: '170px', render: (v) => formatDateTime(v) },
  { key: 'effectiveTo', title: '失效时间', width: '170px', render: (v) => formatDateTime(v) },
  { key: 'enabled', title: '状态', width: '90px', render: (v) => (v ? '启用' : '停用') },
  { key: 'demoFlag', title: '标记', width: '120px', render: (v) => (v ? 'DEMO 示例价' : '正式') },
]

function toPayload(p: Pricing): PricingPayload {
  return {
    provider: p.provider,
    modelName: p.modelName,
    modelType: p.modelType,
    inputPrice: p.inputPrice,
    outputPrice: p.outputPrice,
    cachedInputPrice: p.cachedInputPrice,
    currency: p.currency,
    version: p.version,
    effectiveFrom: p.effectiveFrom,
    effectiveTo: p.effectiveTo,
    enabled: p.enabled,
    demoFlag: p.demoFlag,
  }
}

function isEnabled(row: unknown): boolean {
  return Boolean((row as Pricing).enabled)
}

/* ---------------- 新增 / 编辑 ---------------- */
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const actionError = ref<string | null>(null)

function emptyForm(): PricingPayload {
  return {
    provider: '',
    modelName: '',
    modelType: 'CHAT',
    inputPrice: '0',
    outputPrice: '0',
    cachedInputPrice: '0',
    currency: DEFAULT_CURRENCY,
    version: '',
    effectiveFrom: '',
    effectiveTo: '',
    enabled: true,
    demoFlag: true,
  }
}

const form = reactive<PricingPayload>(emptyForm())

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  Object.assign(form, emptyForm())
  formError.value = null
  formOpen.value = true
}

function openEdit(row: unknown) {
  const pricing = row as Pricing
  formMode.value = 'edit'
  editingId.value = pricing.id
  Object.assign(form, toPayload(pricing))
  formError.value = null
  formOpen.value = true
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  formError.value = null
  const payload: PricingPayload = { ...form }
  try {
    if (formMode.value === 'create') await pricingApi.create(payload)
    else await pricingApi.update(editingId.value, payload)
    formOpen.value = false
    list.execute()
  } catch (err) {
    formError.value = toErrorMessage(err)
  } finally {
    submitting.value = false
  }
}

/** 停用：按契约 PUT /pricing/{id} 提交 enabled=false */
async function disable(row: unknown) {
  const pricing = row as Pricing
  if (!window.confirm(`确认停用价格版本「${pricing.id}」？`)) return
  actionError.value = null
  try {
    await pricingApi.update(pricing.id, { ...toPayload(pricing), enabled: false })
    list.execute()
  } catch (err) {
    actionError.value = toErrorMessage(err)
  }
}

/* ---------------- 历史版本 ---------------- */
const historyOpen = ref(false)
const historyModel = ref('')
const history = useResource(() => pricingApi.history(historyModel.value))

function openHistory(row: unknown) {
  const pricing = row as Pricing
  historyModel.value = pricing.modelName
  historyOpen.value = true
  history.execute()
}
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Pricing Registry"
      title="模型价格管理"
      description="来源 /api/admin/ai/pricing；价格版本与历史记录由后端维护，金额一律按字符串提交以保留精度。"
    >
      <template #actions>
        <button class="btn btn--primary btn--sm" type="button" @click="openCreate">新增价格版本</button>
      </template>
    </PageHeader>

    <div class="notice">
      <span class="badge badge--demo">DEMO</span>
      <span>契约约定：初版价格统一 USD，全部带 demoFlag = true，为 DEMO 示例价，不代表供应商真实报价。</span>
    </div>

    <div v-if="actionError" class="action-error" role="alert">
      <span>{{ actionError }}</span>
      <button class="btn btn--sm btn--ghost" type="button" @click="actionError = null">关闭</button>
    </div>

    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">当前生效价格</h3>
          <p class="panel__subtitle">provider / modelName / modelType / 单价 / 版本 / 生效区间 / DEMO 标记</p>
        </div>
      </header>
      <DataTable
        :columns="columns" :rows="list.data ?? []" row-key="id"
        :loading="list.loading" :error="list.error" :error-trace-id="list.errorTraceId"
        :empty="isEmptyOf(list)" empty-text="暂无价格版本，点击「新增价格版本」创建"
        @retry="list.execute"
      >
        <template #cell-enabled="{ value }">
          <span class="badge" :class="value ? 'badge--success' : 'badge--neutral'">{{ value ? '启用' : '停用' }}</span>
        </template>
        <template #cell-demoFlag="{ value }">
          <span v-if="value" class="badge badge--demo">DEMO 示例价</span>
          <span v-else class="badge badge--neutral">正式</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="row">
            <button class="btn btn--sm" type="button" @click.stop="openEdit(row)">编辑</button>
            <button class="btn btn--sm" type="button" @click.stop="openHistory(row)">历史</button>
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
      :title="formMode === 'create' ? '新增价格版本' : '编辑价格版本'"
      :subtitle="formMode === 'create' ? 'POST /api/admin/ai/pricing' : `PUT /api/admin/ai/pricing/${editingId}`"
      width="680px"
    >
      <div v-if="formError" class="form-error" role="alert">
        <span>{{ formError }}</span>
        <button class="btn btn--sm" type="button" @click="submit">重试</button>
      </div>
      <div class="form-grid">
        <label class="field">
          <span class="field__label">Provider *</span>
          <input v-model="form.provider" class="input" placeholder="openai" />
        </label>
        <label class="field">
          <span class="field__label">模型名 modelName *</span>
          <input v-model="form.modelName" class="input" placeholder="gpt-4o" />
        </label>
        <label class="field">
          <span class="field__label">模型类型 modelType</span>
          <select v-model="form.modelType" class="select">
            <option v-for="o in MODEL_TYPE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">版本号 version</span>
          <input v-model="form.version" class="input" placeholder="DEMO-v1" />
        </label>
        <label class="field">
          <span class="field__label">输入单价 inputPrice</span>
          <input v-model="form.inputPrice" class="input" placeholder="0.000001" />
        </label>
        <label class="field">
          <span class="field__label">输出单价 outputPrice</span>
          <input v-model="form.outputPrice" class="input" placeholder="0.000002" />
        </label>
        <label class="field">
          <span class="field__label">缓存输入单价 cachedInputPrice</span>
          <input v-model="form.cachedInputPrice" class="input" placeholder="0" />
        </label>
        <label class="field">
          <span class="field__label">币种 currency</span>
          <input v-model="form.currency" class="input" />
        </label>
        <label class="field">
          <span class="field__label">生效时间 effectiveFrom</span>
          <input v-model="form.effectiveFrom" class="input" placeholder="2026-11-01T00:00:00Z" />
          <span class="field__hint">ISO 8601 UTC</span>
        </label>
        <label class="field">
          <span class="field__label">失效时间 effectiveTo</span>
          <input v-model="form.effectiveTo" class="input" placeholder="2027-11-01T00:00:00Z" />
          <span class="field__hint">ISO 8601 UTC</span>
        </label>
        <div class="field--full row">
          <label class="checkbox"><input v-model="form.demoFlag" type="checkbox" /> DEMO 示例价 demoFlag</label>
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

    <!-- 历史版本弹窗 -->
    <Modal
      v-model="historyOpen"
      title="价格历史版本"
      :subtitle="`GET /api/admin/ai/pricing/history?modelName=${historyModel}`"
      width="940px"
    >
      <AsyncState
        :loading="history.loading" :error="history.error" :error-trace-id="history.errorTraceId"
        :empty="isEmptyOf(history)" empty-text="该模型暂无历史版本" min-height="200px"
        @retry="history.execute"
      >
        <DataTable :columns="historyColumns" :rows="history.data ?? []" row-key="id" />
      </AsyncState>
      <template #footer>
        <button class="btn" type="button" @click="historyOpen = false">关闭</button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-demo-border);
  border-radius: var(--radius-sm);
  background: var(--color-demo-soft);
  font-size: var(--font-sm);
  color: var(--color-demo);
}
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
