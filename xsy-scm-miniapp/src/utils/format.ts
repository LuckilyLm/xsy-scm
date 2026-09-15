import type { OrderStatus, PriceSource, OrderSource } from '../types/mall'
import { formatMoney, trimDecimal } from './decimal'

/**
 * 金额展示。价格一律来自后端，前端只做定点格式化（HALF_UP 保留 2 位）。
 * 缺价时用 placeholder 表达业务含义（商品场景传 '询价'），避免把“无价”显示成 ¥0.00。
 */
export function formatPrice(
  value: string | number | null | undefined,
  prefix = '¥',
  placeholder?: string,
): string {
  return formatMoney(value, { prefix, placeholder })
}

/** 数量展示：去掉后端返回的尾随零。 */
export function formatQuantity(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '0'
  return trimDecimal(value)
}

export function orderStatusLabel(status: OrderStatus): string {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'PENDING':
      return '待确认'
    case 'CONFIRMED':
      return '已确认'
    case 'CANCELLED':
      return '已取消'
    default:
      return status
  }
}

export function orderSourceLabel(source: OrderSource | string): string {
  switch (source) {
    case 'MALL':
      return '商城订单'
    default:
      return source
  }
}

export function priceSourceLabel(source: PriceSource | null | undefined): string {
  switch (source) {
    case 'AGREEMENT':
      return '协议价'
    case 'CUSTOMER_TYPE':
      return '客户类型价'
    case 'MARKET':
      return '市场价'
    case 'OVERRIDE':
      return '改价'
    default:
      return ''
  }
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}`
}
