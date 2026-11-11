<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { governanceApi } from '@/api/governance'
import type { ProtectionRule } from '@/api/types'
import { toErrorMessage, useResource } from '@/composables/useAsyncData'
import {
  PROTECTION_ACTION_OPTIONS, PROTECTION_LEVELS, protectionLevelTone,
} from '@/constants'
import { formatDateTime, formatRatio } from '@/utils/format'
import type { TableColumn } from '@/utils/table'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import DataTable from '@/components/DataTable.vue'
import Modal from '@/components/Modal.vue'

const status = useResource(() => governanceApi.protectionStatus())
const rules = useResource(() => governanceApi.protectionRules())
const logs = useResource(() => governanceApi.protectionLogs())

function reloadAll() {
  status.execute()
  rules.execute()
  logs.execute()
}
onMounted(reloadAll)

const currentLevelCode = computed(() => status.data?.levelCode ?? 1)

const ruleColumns: TableColumn[] = [
  { key: 'ruleCode', title: '规则编码', width: '170px' },
  { key: 'ruleName', title: '规则名称' },
  { key: 'thresholdValue', title: '阈值', align: 'right', width: '100px', render: (v) => formatRatio(v) },
  { key: 'protectionLevel', title: '保护等级', width: '170px' },
  { key: 'action', title: '动作', width: '170px' },
  { key: 'priority', title: '优先级', align: 'right', width: '90px' },
  { key: 'enabled', title: '启用', width: '90px' },
  { key: 'edit', title: '操作', width: '90px', align: 'right' },
]

const routeColumns: TableColumn[] = [
  { key: 'level', title: '档位', width: '90px' },
  { key: 'model', title: '模型', width: '200px' },
  { key: 'pricingReady', title: '价格就绪', width: '120px' },
  { key: 'description', title: '说明' },
]

const logColumns: TableColumn[] = [
  { key: 'createdAt', title: '时间', width: '168px', render: (v) => formatDateTime(v) },
  { key: 'action', title: '动作', width: '170px' },
  { key: 'fromLevel', title: '原等级', align: 'right', width: '90px' },
  { key: 'toLevel', title: '新等级', align: 'right', width: '90px' },
  { key: 'operator', title: '操作人', width: '160px' },
  { key: 'reason', title: '原因' },
]

/* ---------------- 规则编辑 ---------------- */

const ruleModalOpen = ref(false)
const editingCode = ref('')
const ruleForm = reactive({
  level: 1,
  threshold: 0,
  action: 'NOTIFY',
  priority: 100,
  enabled: 1,
  remark: '',
})
const saving = ref(false)
const ruleError = ref<string | null>(null)

function openRule(row: unknown) {
  const rule = row as ProtectionRule
  editingCode.value = rule.ruleCode
  ruleForm.level = rule.protectionLevel
  ruleForm.threshold = Number(rule.thresholdValue ?? 0)
  ruleForm.action = rule.action
  ruleForm.priority = rule.priority
  ruleForm.enabled = rule.enabled
  ruleForm.remark = rule.remark ?? ''
  ruleError.value = null
  ruleModalOpen.value = true
}

async function saveRule() {
  saving.value = true
  ruleError.value = null
  try {
    await governanceApi.updateProtectionRule(editingCode.value, {
      level: ruleForm.level,
      threshold: ruleForm.threshold,
      action: ruleForm.action,
      priority: ruleForm.priority,
      enabled: ruleForm.enabled,
      remark: ruleForm.remark,
    })
    ruleModalOpen.value = false
    reloadAll()
  } catch (err) {
    ruleError.value = toErrorMessage(err)
  } finally {
    saving.value = false
  }
}

/* ---------------- 紧急开关 ---------------- */

const emergencyModalOpen = ref(false)
const emergencyMode = ref<'stop' | 'resume'>('stop')
const emergencyReason = ref('')
const emergencyError = ref<string | null>(null)
const emergencyLoading = ref(false)

function openEmergency(mode: 'stop' | 'resume') {
  emergencyMode.value = mode
  emergencyReason.value = ''
  emergencyError.value = null
  emergencyModalOpen.value = true
}

async function submitEmergency() {
  if (!emergencyReason.value.trim()) {
    emergencyError.value = '必须填写原因（会写入审计日志）'
    return
  }
  emergencyLoading.value = true
  emergencyError.value = null
  try {
    if (emergencyMode.value === 'stop') {
      await governanceApi.emergencyStop('admin', emergencyReason.value.trim())
    } else {
      await governanceApi.resume('admin', emergencyReason.value.trim())
    }
    emergencyModalOpen.value = false
    reloadAll()
  } catch (err) {
    emergencyError.value = toErrorMessage(err)
  } finally {
    emergencyLoading.value = false
  }
}
</script>

<template>
  <div class="stack">
    <PageHeader
      eyebrow="Protection"
      title="系统保护策略"
      description="五级保护：预算使用率命中规则阈值即自动升级。等级只决定「是否放行 / 是否降级 / 是否允许 Agent」，不会绕过预算预扣。"
    >
      <template #actions>
        <button class="btn" :disabled="status.loading" @click="reloadAll">刷新</button>
        <button
          v-if="!status.data?.emergencyStopped" class="btn btn--danger"
          @click="openEmergency('stop')"
        >紧急停用</button>
        <button v-else class="btn btn--primary" @click="openEmergency('resume')">恢复运行</button>
      </template>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="当前保护等级"
        :value="status.data?.level ?? '—'"
        :sub="`levelCode ${currentLevelCode} · ${PROTECTION_LEVELS.find((l) => l.code === currentLevelCode)?.label ?? ''}`"
        :tone="currentLevelCode >= 4 ? 'danger' : currentLevelCode >= 3 ? 'warning' : 'success'"
        :loading="status.loading"
      />
      <StatCard
        label="紧急开关"
        :value="status.data?.emergencyStopped ? '已停用' : '正常'"
        sub="停用时拒绝一切新的付费调用"
        :tone="status.data?.emergencyStopped ? 'danger' : 'success'"
        :loading="status.loading"
      />
      <StatCard
        label="最高预算使用率"
        :value="formatRatio(status.data?.maxBudgetUsageRate)"
        sub="所有预算作用域中的最大值，作为等级判定输入"
        tone="info"
        :loading="status.loading"
      />
      <StatCard
        label="未恢复严重告警"
        :value="String(status.data?.openCriticalAlerts ?? '—')"
        sub="PROTECTION_ACTION 自身不计入，避免自锁"
        :tone="(status.data?.openCriticalAlerts ?? 0) > 0 ? 'warning' : 'neutral'"
        :loading="status.loading"
      />
    </div>

    <!-- 五级语义 -->
    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">五级保护语义</h3>
          <p class="panel__subtitle">等级由「紧急开关 &gt; 预算阈值规则 &gt; 未恢复 CRITICAL 告警」三者取最高值</p>
        </div>
      </header>
      <div class="panel__body level-grid">
        <div
          v-for="lv in PROTECTION_LEVELS" :key="lv.code" class="level-card"
          :class="{ 'is-current': lv.code === currentLevelCode }"
        >
          <div class="level-card__head">
            <span class="badge" :class="'badge--' + protectionLevelTone(lv.code)">L{{ lv.code }}</span>
            <strong>{{ lv.name }}</strong>
            <span v-if="lv.code === currentLevelCode" class="badge badge--primary">当前</span>
          </div>
          <p class="level-card__label">{{ lv.label }}</p>
          <p class="level-card__desc">{{ lv.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 模型路由档位 -->
    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">模型路由档位</h3>
          <p class="panel__subtitle">/cost-protection/status · routes · 降级方向只允许「贵 → 便宜」，不会反向升级</p>
        </div>
      </header>
      <DataTable
        :columns="routeColumns" :rows="status.data?.routes ?? []" row-key="level"
        :loading="status.loading" :error="status.error" :error-trace-id="status.errorTraceId"
        :empty="!status.loading && !status.error && (status.data?.routes?.length ?? 0) === 0"
        @retry="status.execute"
      >
        <template #cell-pricingReady="{ value }">
          <span class="badge" :class="value ? 'badge--success' : 'badge--warning'">
            {{ value ? '有生效价' : '缺生效价（会回退默认模型）' }}
          </span>
        </template>
      </DataTable>
    </section>

    <!-- 保护规则 -->
    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">保护规则</h3>
          <p class="panel__subtitle">/cost-protection/rules · 阈值以小数比率存储（0.85 = 85%）</p>
        </div>
      </header>
      <DataTable
        :columns="ruleColumns" :rows="rules.data ?? []" row-key="ruleCode"
        :loading="rules.loading" :error="rules.error" :error-trace-id="rules.errorTraceId"
        :empty="!rules.loading && !rules.error && (rules.data?.length ?? 0) === 0"
        @retry="rules.execute"
      >
        <template #cell-protectionLevel="{ value }">
          <span class="badge" :class="'badge--' + protectionLevelTone(Number(value))">
            L{{ value }} · {{ PROTECTION_LEVELS.find((l) => l.code === Number(value))?.name ?? '' }}
          </span>
        </template>
        <template #cell-action="{ value }">
          <span class="mono badge badge--neutral">{{ value }}</span>
        </template>
        <template #cell-enabled="{ value }">
          <span class="badge" :class="Number(value) === 1 ? 'badge--success' : 'badge--neutral'">
            {{ Number(value) === 1 ? '启用' : '关闭' }}
          </span>
        </template>
        <template #cell-edit="{ row }">
          <button class="btn btn--sm" @click="openRule(row)">编辑</button>
        </template>
      </DataTable>
    </section>

    <!-- 审计日志 -->
    <section class="panel">
      <header class="panel__header">
        <div>
          <h3 class="panel__title">保护动作审计日志</h3>
          <p class="panel__subtitle">/cost-protection/logs · 紧急停用与恢复必须记录操作人与原因</p>
        </div>
      </header>
      <DataTable
        :columns="logColumns" :rows="logs.data ?? []" row-key="id"
        :loading="logs.loading" :error="logs.error" :error-trace-id="logs.errorTraceId"
        :empty="!logs.loading && !logs.error && (logs.data?.length ?? 0) === 0"
        empty-text="尚无保护动作记录"
        @retry="logs.execute"
      >
        <template #cell-action="{ value }">
          <span class="badge" :class="String(value) === 'EMERGENCY_STOP' ? 'badge--danger' : 'badge--success'">
            {{ value }}
          </span>
        </template>
      </DataTable>
    </section>

    <!-- 规则编辑弹窗 -->
    <Modal v-model="ruleModalOpen" title="编辑保护规则" :subtitle="editingCode" width="520px">
      <div class="form-grid">
        <label class="field">
          <span class="field__label">保护等级（1~5）</span>
          <input v-model.number="ruleForm.level" class="input" type="number" min="1" max="5" />
          <span class="field__hint">
            {{ PROTECTION_LEVELS.find((l) => l.code === ruleForm.level)?.label ?? '超出 1~5 范围会被后端拒绝' }}
          </span>
        </label>
        <label class="field">
          <span class="field__label">阈值（0~1 小数）</span>
          <input v-model.number="ruleForm.threshold" class="input" type="number" step="0.01" min="0" />
          <span class="field__hint">当前值 {{ formatRatio(ruleForm.threshold) }}</span>
        </label>
        <label class="field">
          <span class="field__label">动作</span>
          <select v-model="ruleForm.action" class="select">
            <option v-for="o in PROTECTION_ACTION_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field__label">优先级（数字越小越先判定）</span>
          <input v-model.number="ruleForm.priority" class="input" type="number" min="1" />
        </label>
        <label class="field">
          <span class="field__label">启用</span>
          <select v-model.number="ruleForm.enabled" class="select">
            <option :value="1">启用</option>
            <option :value="0">关闭</option>
          </select>
        </label>
        <label class="field form-grid__full">
          <span class="field__label">备注</span>
          <input v-model="ruleForm.remark" class="input" type="text" placeholder="说明这条规则的用途" />
        </label>
      </div>
      <p v-if="ruleError" class="form-error">{{ ruleError }}</p>
      <template #footer>
        <button class="btn" @click="ruleModalOpen = false">取消</button>
        <button class="btn btn--primary" :disabled="saving" @click="saveRule">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </Modal>

    <!-- 紧急开关弹窗 -->
    <Modal
      v-model="emergencyModalOpen"
      :title="emergencyMode === 'stop' ? '紧急停用付费调用' : '恢复付费调用'"
      subtitle="该操作会写入审计日志，并影响所有用户与模型"
      width="480px"
    >
      <div class="form-grid">
        <label class="field form-grid__full">
          <span class="field__label">原因（必填）</span>
          <textarea v-model="emergencyReason" class="textarea" rows="3"
            :placeholder="emergencyMode === 'stop' ? '例如：发现异常刷量，先全局止血' : '例如：异常已排除，恢复运行'" />
        </label>
      </div>
      <p class="text-muted form-hint">
        <template v-if="emergencyMode === 'stop'">
          停用后所有新的付费调用会返回 40302；恢复时会把本次停用写下的 PROTECTION_ACTION 告警一并标记为已恢复。
        </template>
        <template v-else>
          恢复后保护等级会重新按预算使用率与未恢复告警计算，通常回到 NORMAL。
        </template>
      </p>
      <p v-if="emergencyError" class="form-error">{{ emergencyError }}</p>
      <template #footer>
        <button class="btn" @click="emergencyModalOpen = false">取消</button>
        <button
          class="btn" :class="emergencyMode === 'stop' ? 'btn--danger' : 'btn--primary'"
          :disabled="emergencyLoading" @click="submitEmergency"
        >
          {{ emergencyLoading ? '提交中…' : (emergencyMode === 'stop' ? '确认停用' : '确认恢复') }}
        </button>
      </template>
    </Modal>

    <p v-if="status.data" class="foot-note text-muted">
      当前等级 {{ status.data.level }} 由后端实时计算，刷新即可反映最新预算与告警状态。
    </p>
  </div>
</template>

<style scoped>
.level-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--space-3);
}
.level-card {
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  min-width: 0;
}
.level-card.is-current {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
}
.level-card__head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: var(--font-sm); }
.level-card__label { margin-top: 6px; font-size: var(--font-sm); color: var(--color-text-secondary); }
.level-card__desc { margin-top: 2px; font-size: var(--font-xs); color: var(--color-text-muted); }

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}
.form-grid__full { grid-column: 1 / -1; }
.form-error { margin-top: var(--space-3); font-size: var(--font-sm); color: var(--color-danger); }
.form-hint { margin-top: var(--space-3); font-size: var(--font-xs); line-height: 1.7; }
.foot-note { font-size: var(--font-xs); }
@media (max-width: 560px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
