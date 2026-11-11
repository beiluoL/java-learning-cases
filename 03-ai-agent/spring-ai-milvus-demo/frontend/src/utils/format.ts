/**
 * 统一格式化：金额按字符串处理避免 BigDecimal 精度丢失，Token 为整数，
 * 时间统一从 ISO UTC 转成本地时区展示。
 */

/** 金额：千分位 + 保留 6~8 位小数（不足补 0，超过截断），全程不经过 parseFloat */
export function formatMoney(value: unknown, minDecimals = 6, maxDecimals = 8): string {
  if (value === null || value === undefined || value === '') return '—'
  let raw = String(value).trim()
  if (!/^-?\d*(\.\d*)?$/.test(raw) || raw === '' || raw === '-' || raw === '.') return String(value)
  const negative = raw.startsWith('-')
  if (negative) raw = raw.slice(1)
  const [intPart = '0', fracPart = ''] = raw.split('.')
  const groupedInt = (intPart || '0').replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  let frac = fracPart
  if (frac.length < minDecimals) frac = frac.padEnd(minDecimals, '0')
  else if (frac.length > maxDecimals) frac = frac.slice(0, maxDecimals)
  return `${negative ? '-' : ''}${groupedInt}${frac ? `.${frac}` : ''}`
}

const CURRENCY_SYMBOL: Record<string, string> = { USD: '$', CNY: '¥', EUR: '€' }

/** 带币种前缀的金额 */
export function formatCurrency(value: unknown, currency = 'USD'): string {
  const prefix = CURRENCY_SYMBOL[currency] ?? `${currency} `
  return `${prefix}${formatMoney(value)}`
}

/** Token / 次数：整数千分位 */
export function formatToken(value: unknown): string {
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return Math.round(n).toLocaleString('en-US')
}

export const formatCount = formatToken

/** 转数值（图表用），非法值归零 */
export function toNumber(value: unknown): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

/** 比率：<=1 视为小数（×100），否则视为已是百分数 */
export function toPercentNumber(value: unknown): number {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  return Math.abs(n) <= 1 ? n * 100 : n
}

export function formatRatio(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return `${toPercentNumber(value).toFixed(2)}%`
}

/** ISO UTC → 本地时区日期时间 */
export function formatDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const d = new Date(String(value))
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

/** ISO UTC → 本地时区日期 */
export function formatDate(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  const d = new Date(String(value))
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

/** 趋势图 bucket 标签：YYYY-MM-DD → MM-DD；带时间的 → MM-DD HH:00；其余原样 */
export function formatBucketLabel(bucket: unknown): string {
  const raw = String(bucket ?? '')
  if (!raw) return '—'
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) return raw.slice(5)
  if (/^\d{4}-\d{2}$/.test(raw)) return raw
  const d = new Date(raw)
  if (!Number.isNaN(d.getTime())) {
    const date = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const hasTime = /[T\s]/.test(raw) && (d.getHours() !== 0 || d.getMinutes() !== 0)
    return hasTime ? `${date} ${String(d.getHours()).padStart(2, '0')}:00` : date
  }
  return raw
}
