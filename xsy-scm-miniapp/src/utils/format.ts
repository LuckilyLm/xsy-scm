import type { OrderStatus, PriceSource, OrderSource } from '../types/mall'

export function formatPrice(value: string | number | null | undefined, prefix = '¥'): string {
  if (value === null || value === undefined || value === '') return `${prefix}0.00`
  const n = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(n)) return `${prefix}0.00`
  return prefix + n.toFixed(2)
}

export function formatQuantity(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '0'
  return String(value)
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

export function priceSourceLabel(source: PriceSource): string {
  switch (source) {
    case 'AGREEMENT':
      return '协议价'
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
