import type { TimeRange } from '@/api/types'
import { formatDateTime } from './format'

export type RangePreset = 'today' | 'last7' | 'last30' | 'month' | 'custom'

export interface PresetOption {
  value: RangePreset
  label: string
}

/** 纯 UI 选项常量：时间筛选按钮，不是统计数据 */
export const RANGE_PRESETS: PresetOption[] = [
  { value: 'today', label: '今天' },
  { value: 'last7', label: '最近 7 天' },
  { value: 'last30', label: '最近 30 天' },
  { value: 'month', label: '本月' },
  { value: 'custom', label: '自定义' },
]

function startOfDay(d: Date): Date {
  const copy = new Date(d)
  copy.setHours(0, 0, 0, 0)
  return copy
}

function startOfMonth(d: Date): Date {
  const copy = startOfDay(d)
  copy.setDate(1)
  return copy
}

function shiftDays(d: Date, days: number): Date {
  const copy = new Date(d)
  copy.setDate(copy.getDate() + days)
  return copy
}

function toIso(d: Date): string {
  return d.toISOString()
}

/** 解析为 ISO 8601 UTC 的 startTime / endTime（自定义值按本地时区 datetime-local 解析） */
export function resolveRange(
  preset: RangePreset,
  customStart: string,
  customEnd: string,
): TimeRange {
  const now = new Date()
  switch (preset) {
    case 'today':
      return { startTime: toIso(startOfDay(now)), endTime: toIso(now) }
    case 'last7':
      return { startTime: toIso(startOfDay(shiftDays(now, -6))), endTime: toIso(now) }
    case 'last30':
      return { startTime: toIso(startOfDay(shiftDays(now, -29))), endTime: toIso(now) }
    case 'month':
      return { startTime: toIso(startOfMonth(now)), endTime: toIso(now) }
    case 'custom': {
      const s = customStart ? new Date(customStart) : startOfDay(shiftDays(now, -6))
      const e = customEnd ? new Date(customEnd) : now
      return {
        startTime: toIso(Number.isNaN(s.getTime()) ? startOfDay(shiftDays(now, -6)) : s),
        endTime: toIso(Number.isNaN(e.getTime()) ? now : e),
      }
    }
    default:
      return { startTime: toIso(startOfDay(shiftDays(now, -6))), endTime: toIso(now) }
  }
}

export function describeRange(range: TimeRange): string {
  return `${formatDateTime(range.startTime)}  ~  ${formatDateTime(range.endTime)}`
}

/** datetime-local 输入框需要 YYYY-MM-DDTHH:mm 格式的本地时间字符串 */
export function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
