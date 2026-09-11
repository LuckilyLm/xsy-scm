/**
 * 精确十进制工具（无 BigInt 依赖，可在微信小程序运行时安全使用）。
 *
 * 商城金额与数量一律以字符串从后端传入（后端用 BigDecimal，结算保留 4 位小数）。
 * 前端**禁止**用 Number() 直接做加减：二进制浮点会产生 0.1 + 0.2 = 0.30000000000000004
 * 这类误差，导致展示金额与后端权威金额不一致。
 *
 * 实现方式：把十进制字符串按固定内部精度（6 位小数）解析为**整数**，
 * 用整数加减与整除完成求和与 HALF_UP 四舍五入，全程不产生浮点误差。
 */

/** 内部定点精度。后端结算 4 位，留 2 位余量用于中间计算。 */
const INTERNAL_SCALE = 6
/** 解析后的整数位数上限，保证在 Number.MAX_SAFE_INTEGER 内精确表示。 */
const MAX_DIGITS = 15

const DECIMAL_PATTERN = /^([+-]?)(\d*)(?:\.(\d*))?$/

interface ParsedDecimal {
  /** 内部精度下的整数值。 */
  unscaled: number
  /** 输入本身的小数位数（用于保留原始精度展示）。 */
  inputScale: number
}

function parseDecimal(value: string | number | null | undefined): ParsedDecimal | null {
  if (value === null || value === undefined) return null
  const raw = (typeof value === 'number' ? String(value) : value).trim()
  if (raw === '') return null
  const matched = DECIMAL_PATTERN.exec(raw)
  if (!matched) return null
  const negative = matched[1] === '-'
  const intDigits = matched[2] || ''
  const fracRaw = matched[3] || ''
  // 空串与孤立的 "." 视为非法；"0" 是合法金额。
  if (intDigits === '' && fracRaw === '') return null
  const intPart = intDigits || '0'
  const keptFrac = fracRaw.slice(0, INTERNAL_SCALE)
  if (intPart.length + keptFrac.length > MAX_DIGITS) return null
  const digits = `${intPart}${keptFrac.padEnd(INTERNAL_SCALE, '0')}`
  let unscaled = Number(digits)
  if (!Number.isFinite(unscaled)) return null
  // 超出内部精度的部分按 HALF_UP 进位，避免静默截断。
  if (fracRaw.length > INTERNAL_SCALE && fracRaw.charCodeAt(INTERNAL_SCALE) - 48 >= 5) {
    unscaled += 1
  }
  return { unscaled: negative ? -unscaled : unscaled, inputScale: fracRaw.length }
}

function fromUnscaled(unscaled: number, scale: number): string {
  const negative = unscaled < 0
  const abs = Math.abs(unscaled)
  const text = String(abs).padStart(scale + 1, '0')
  if (scale === 0) return `${negative ? '-' : ''}${text}`
  const splitAt = text.length - scale
  return `${negative ? '-' : ''}${text.slice(0, splitAt)}.${text.slice(splitAt)}`
}

function trimTrailingZeros(text: string): string {
  if (!text.includes('.')) return text
  return text.replace(/0+$/, '').replace(/\.$/, '')
}

function groupThousands(intPart: string): string {
  return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 精确求和；非法输入被忽略，全部非法时返回 '0'。 */
export function sumDecimal(values: Array<string | number | null | undefined>): string {
  let total = 0
  let seen = false
  for (const value of values) {
    const parsed = parseDecimal(value)
    if (parsed === null) continue
    seen = true
    total += parsed.unscaled
  }
  if (!seen) return '0'
  return trimTrailingZeros(fromUnscaled(total, INTERNAL_SCALE))
}

/** 按 HALF_UP 保留指定位数，与后端结算口径一致；非法输入返回 null。 */
export function roundDecimal(
  value: string | number | null | undefined,
  scale = 2,
): string | null {
  const parsed = parseDecimal(value)
  if (parsed === null) return null
  const { unscaled } = parsed
  if (scale >= INTERNAL_SCALE) {
    return fromUnscaled(unscaled * 10 ** (scale - INTERNAL_SCALE), scale)
  }
  const divisor = 10 ** (INTERNAL_SCALE - scale)
  const negative = unscaled < 0
  const abs = Math.abs(unscaled)
  let quotient = Math.trunc(abs / divisor)
  if ((abs % divisor) * 2 >= divisor) quotient += 1
  return fromUnscaled(negative ? -quotient : quotient, scale)
}

/** 去掉小数尾随零，用于数量展示（"1.5000" -> "1.5"，"2.0000" -> "2"）。 */
export function trimDecimal(value: string | number | null | undefined): string {
  const parsed = parseDecimal(value)
  if (parsed === null) {
    return value === null || value === undefined ? '' : String(value)
  }
  return trimTrailingZeros(fromUnscaled(parsed.unscaled, INTERNAL_SCALE))
}

/** 是否为大于零的合法数量。 */
export function isPositiveDecimal(value: string | number | null | undefined): boolean {
  const parsed = parseDecimal(value)
  return parsed !== null && parsed.unscaled > 0
}

export interface MoneyOptions {
  prefix?: string
  /** 展示小数位，默认 2。 */
  scale?: number
  /** 缺价/非法时的占位文本，默认 `${prefix}0.00`。 */
  placeholder?: string
  /** 是否对整数部分加千分位。 */
  grouped?: boolean
}

/** 货币展示：默认 ¥ + 两位小数（HALF_UP）。非法或缺失时返回占位文本。 */
export function formatMoney(
  value: string | number | null | undefined,
  options: MoneyOptions = {},
): string {
  const { prefix = '¥', scale = 2, placeholder, grouped = false } = options
  const rounded = roundDecimal(value, scale)
  if (rounded === null) {
    return placeholder ?? `${prefix}${fromUnscaled(0, scale)}`
  }
  if (!grouped) return `${prefix}${rounded}`
  const negative = rounded.startsWith('-')
  const plain = negative ? rounded.slice(1) : rounded
  const [intPart, fracPart] = plain.split('.')
  const body = fracPart ? `${groupThousands(intPart)}.${fracPart}` : groupThousands(intPart)
  return `${prefix}${negative ? '-' : ''}${body}`
}
