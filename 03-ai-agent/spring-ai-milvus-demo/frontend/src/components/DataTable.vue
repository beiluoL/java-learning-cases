<script setup lang="ts">
import AsyncState from './AsyncState.vue'
import type { TableColumn } from '@/utils/table'

const props = withDefaults(defineProps<{
  columns: TableColumn[]
  rows: unknown[]
  rowKey?: string
  loading?: boolean
  error?: string | null
  errorTraceId?: string
  empty?: boolean
  emptyText?: string
  activeSort?: string
  clickable?: boolean
  minHeight?: string
}>(), {
  rowKey: '',
  loading: false,
  error: null,
  errorTraceId: '',
  empty: false,
  emptyText: '没有符合条件的记录',
  activeSort: '',
  clickable: false,
  minHeight: '160px',
})

const emit = defineEmits<{
  retry: []
  'row-click': [row: unknown]
  sort: [key: string]
}>()

function cellValue(row: unknown, key: string): unknown {
  if (row && typeof row === 'object') return (row as Record<string, unknown>)[key]
  return undefined
}

function display(row: unknown, col: TableColumn): string {
  const raw = cellValue(row, col.key)
  if (col.render) return col.render(raw, row)
  if (raw === null || raw === undefined || raw === '') return '—'
  return String(raw)
}

function onRowClick(row: unknown) {
  if (props.clickable) emit('row-click', row)
}

function onSort(col: TableColumn) {
  if (col.sortable) emit('sort', col.key)
}

function isSorted(col: TableColumn): boolean {
  return Boolean(props.activeSort && props.activeSort.startsWith(`${col.key},`))
}
</script>

<template>
  <AsyncState
    :loading="loading"
    :error="error"
    :error-trace-id="errorTraceId"
    :empty="empty"
    :empty-text="emptyText"
    :min-height="minHeight"
    @retry="emit('retry')"
  >
    <div class="table-scroll">
      <table class="table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ width: col.width, textAlign: col.align || 'left' }"
              :class="{ 'is-sortable': col.sortable, 'is-sorted': isSorted(col) }"
              @click="onSort(col)"
            >
              <span>{{ col.title }}</span>
              <span v-if="col.sortable" class="table__sort" aria-hidden="true">↕</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, idx) in rows"
            :key="rowKey ? String(cellValue(row, rowKey) ?? idx) : idx"
            :class="{ 'is-clickable': clickable }"
            @click="onRowClick(row)"
          >
            <td v-for="col in columns" :key="col.key" :style="{ textAlign: col.align || 'left' }">
              <slot :name="'cell-' + col.key" :row="row" :value="cellValue(row, col.key)">
                {{ display(row, col) }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="$slots.footer" class="table__footer"><slot name="footer" /></div>
  </AsyncState>
</template>

<style scoped>
.table-scroll { width: 100%; overflow-x: auto; }
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--font-base);
  min-width: 640px;
}
.table th,
.table td {
  padding: 10px var(--space-4);
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
  vertical-align: middle;
}
.table th {
  background: var(--color-surface-muted);
  color: var(--color-text-secondary);
  font-size: var(--font-sm);
  font-weight: var(--font-weight-semibold);
  text-align: left;
  position: sticky;
  top: 0;
}
.table th.is-sortable { cursor: pointer; user-select: none; }
.table th.is-sortable:hover { color: var(--color-primary); }
.table th.is-sorted { color: var(--color-primary); }
.table__sort { margin-left: 5px; opacity: 0.45; font-size: var(--font-xs); }
.table th.is-sorted .table__sort { opacity: 1; }
.table tbody tr:last-child td { border-bottom: none; }
.table tbody tr:hover { background: var(--color-surface-muted); }
.table tbody tr.is-clickable { cursor: pointer; }
.table__footer { border-top: 1px solid var(--color-border); }
</style>
