import { describe, expect, it } from 'vitest'
import {
  formatBucketLabel,
  formatCurrency,
  formatDate,
  formatDateTime,
  formatMoney,
  formatRatio,
  formatToken,
  toNumber,
  toPercentNumber,
} from './format'

/**
 * 金额与比率格式化回归测试。
 *
 * 存在意义：后端金额是 DECIMAL(20,8)，以字符串返回。前端一旦把金额转成 Number
 * 再格式化，19 位有效数字会被 IEEE754 截断，显示出来的账目就是错的。
 * 这里锁定「金额全程走字符串」，任何把 parseFloat/Number 引入 formatMoney 的改动都会失败。
 */
describe('formatMoney', () => {
  it('保留 6~8 位小数并按需补零', () => {
    expect(formatMoney('20')).toBe('20.000000')
    expect(formatMoney('0.0000064')).toBe('0.0000064')
    expect(formatMoney('20.00000000')).toBe('20.00000000')
  })

  it('超过 8 位小数截断而非四舍五入', () => {
    expect(formatMoney('1.123456789')).toBe('1.12345678')
  })

  it('整数部分加千分位', () => {
    expect(formatMoney('1234567.5')).toBe('1,234,567.500000')
  })

  it('负数保留符号', () => {
    expect(formatMoney('-0.5')).toBe('-0.500000')
    expect(formatMoney('-1234.5678')).toBe('-1,234.567800')
  })

  it('超长精度不丢位（19 位有效数字）', () => {
    // 这个值用 Number 表示会被截断成 123456789012345680000
    expect(formatMoney('123456789012345678.90123456')).toBe('123,456,789,012,345,678.90123456')
  })

  it('空值返回占位符', () => {
    expect(formatMoney(null)).toBe('—')
    expect(formatMoney(undefined)).toBe('—')
    expect(formatMoney('')).toBe('—')
  })

  it('非数字原样返回，不静默变成 0', () => {
    expect(formatMoney('abc')).toBe('abc')
    expect(formatMoney('-')).toBe('-')
    expect(formatMoney('.')).toBe('.')
  })
})

describe('formatCurrency', () => {
  it('已知币种用符号', () => {
    expect(formatCurrency('1.5', 'USD')).toBe('$1.500000')
    expect(formatCurrency('1.5', 'CNY')).toBe('¥1.500000')
  })

  it('未知币种用代码加空格', () => {
    expect(formatCurrency('1.5', 'GBP')).toBe('GBP 1.500000')
  })
})

describe('formatToken', () => {
  it('整数千分位', () => {
    expect(formatToken(1234)).toBe('1,234')
    expect(formatToken('1000000')).toBe('1,000,000')
    expect(formatToken(0)).toBe('0')
  })

  it('小数四舍五入到整数', () => {
    expect(formatToken(12.6)).toBe('13')
  })

  it('非法值返回占位符', () => {
    expect(formatToken('abc')).toBe('—')
    expect(formatToken(null)).toBe('—')
  })
})

describe('toNumber / toPercentNumber', () => {
  it('非法值归零', () => {
    expect(toNumber('abc')).toBe(0)
    expect(toNumber(undefined)).toBe(0)
    expect(toNumber('12.5')).toBe(12.5)
  })

  it('<=1 视为小数并放大 100 倍', () => {
    expect(toPercentNumber(0.5)).toBe(50)
    expect(toPercentNumber(1)).toBe(100)
    expect(toPercentNumber(0)).toBe(0)
  })

  it('>1 视为已是百分数', () => {
    expect(toPercentNumber(50)).toBe(50)
    expect(toPercentNumber(1.01)).toBe(1.01)
  })
})

describe('formatRatio', () => {
  it('输出两位小数百分比', () => {
    expect(formatRatio(0.1234)).toBe('12.34%')
    expect(formatRatio(1)).toBe('100.00%')
  })

  it('空值返回占位符', () => {
    expect(formatRatio(null)).toBe('—')
    expect(formatRatio('')).toBe('—')
  })
})

describe('formatBucketLabel', () => {
  it('日期去掉年份', () => {
    expect(formatBucketLabel('2026-11-11')).toBe('11-11')
  })

  it('月粒度原样返回', () => {
    expect(formatBucketLabel('2026-11')).toBe('2026-11')
  })

  it('空值返回占位符', () => {
    expect(formatBucketLabel('')).toBe('—')
    expect(formatBucketLabel(null)).toBe('—')
  })
})

describe('formatDateTime / formatDate', () => {
  it('空值返回占位符', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('')).toBe('—')
    expect(formatDate(undefined)).toBe('—')
  })

  it('非法时间原样返回，不抛异常', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
    expect(formatDate('2026-13-45')).toBe('2026-13-45')
  })

  it('合法 ISO 时间返回非占位字符串', () => {
    const out = formatDateTime('2026-11-11T08:00:00Z')
    expect(out).not.toBe('—')
    expect(out.length).toBeGreaterThan(10)
  })
})
