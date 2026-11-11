import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { describeRange, RANGE_PRESETS, resolveRange, toLocalInput } from './time'

/**
 * 时间范围换算测试。
 *
 * 存在意义：所有查询接口都以 startTime/endTime（ISO UTC）为过滤条件，
 * 一旦换算错一天，报表就会漏掉首日或混入多余数据，且前端看不出异常。
 *
 * 断言刻意只依赖「区间长度」与「端点关系」，不依赖本机时区，
 * 这样在 UTC+8 与 UTC 机器上都能通过。
 */
describe('resolveRange', () => {
  const NOW = new Date('2026-11-11T08:00:00Z')

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('endTime 恒为当前时刻', () => {
    for (const preset of ['today', 'last7', 'last30', 'month'] as const) {
      const r = resolveRange(preset, '', '')
      expect(new Date(r.endTime).getTime()).toBe(NOW.getTime())
    }
  })

  it('startTime 早于 endTime', () => {
    for (const preset of ['today', 'last7', 'last30', 'month'] as const) {
      const r = resolveRange(preset, '', '')
      expect(new Date(r.startTime).getTime()).toBeLessThan(new Date(r.endTime).getTime())
    }
  })

  it('last7 覆盖 6~7 天（含起止端点）', () => {
    const r = resolveRange('last7', '', '')
    const days = (new Date(r.endTime).getTime() - new Date(r.startTime).getTime()) / 86_400_000
    expect(days).toBeGreaterThan(6)
    expect(days).toBeLessThanOrEqual(7)
  })

  it('last30 覆盖 29~30 天', () => {
    const r = resolveRange('last30', '', '')
    const days = (new Date(r.endTime).getTime() - new Date(r.startTime).getTime()) / 86_400_000
    expect(days).toBeGreaterThan(29)
    expect(days).toBeLessThanOrEqual(30)
  })

  it('month 的起点不晚于 endTime 且在同月或上月', () => {
    const r = resolveRange('month', '', '')
    const start = new Date(r.startTime)
    const end = new Date(r.endTime)
    expect(start.getTime()).toBeLessThanOrEqual(end.getTime())
    const diffDays = (end.getTime() - start.getTime()) / 86_400_000
    expect(diffDays).toBeLessThanOrEqual(31)
  })

  it('custom 使用传入的起止时间', () => {
    const r = resolveRange('custom', '2026-11-01T00:00', '2026-11-05T00:00')
    expect(r.startTime).toBe(new Date('2026-11-01T00:00').toISOString())
    expect(r.endTime).toBe(new Date('2026-11-05T00:00').toISOString())
  })

  it('custom 缺省或非法时回落到最近 7 天', () => {
    const fallback = resolveRange('custom', '', '')
    const last7 = resolveRange('last7', '', '')
    expect(fallback.startTime).toBe(last7.startTime)

    const invalid = resolveRange('custom', 'not-a-date', 'also-bad')
    expect(invalid.startTime).toBe(last7.startTime)
    expect(invalid.endTime).toBe(last7.endTime)
  })

  it('未知 preset 回落到最近 7 天而非抛异常', () => {
    // @ts-expect-error 故意传入非法值，验证运行时兜底
    const r = resolveRange('unknown', '', '')
    expect(r.startTime).toBe(resolveRange('last7', '', '').startTime)
  })
})

describe('RANGE_PRESETS', () => {
  it('包含全部 5 个预置且 value 唯一', () => {
    expect(RANGE_PRESETS).toHaveLength(5)
    const values = RANGE_PRESETS.map((p) => p.value)
    expect(new Set(values).size).toBe(values.length)
  })

  it('每项都有非空中文标签', () => {
    for (const p of RANGE_PRESETS) {
      expect(p.label.length).toBeGreaterThan(0)
    }
  })
})

describe('toLocalInput', () => {
  it('输出 datetime-local 需要的 YYYY-MM-DDTHH:mm', () => {
    // 用本地构造函数，避免时区影响断言
    const d = new Date(2026, 10, 11, 8, 5)
    expect(toLocalInput(d)).toBe('2026-11-11T08:05')
  })

  it('月/日/时/分均补零', () => {
    const d = new Date(2026, 0, 2, 3, 4)
    expect(toLocalInput(d)).toBe('2026-01-02T03:04')
  })
})

describe('describeRange', () => {
  it('拼接起止并带分隔符', () => {
    const out = describeRange({
      startTime: '2026-11-01T00:00:00Z',
      endTime: '2026-11-11T00:00:00Z',
    })
    expect(out).toContain('~')
    expect(out.split('~')).toHaveLength(2)
  })
})
