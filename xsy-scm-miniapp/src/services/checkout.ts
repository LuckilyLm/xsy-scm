import { http } from './http'
import type {
  MallCheckoutPreview,
  MallCheckoutItemInput,
  MallOrderSubmitResult,
  MallOrderSubmitInput,
} from '../types/mall'

export function preview(items: MallCheckoutItemInput[], addressId: number) {
  return http.post<MallCheckoutPreview>('/api/mall/orders/preview', { items, addressId })
}

/**
 * 提交订单。priceFingerprint 须来自 preview 返回，服务端据此检测变价（不一致返回 40970）。
 * idempotencyKey 由调用方生成并复用（同一笔重试使用相同 key，避免重复建单）。
 */
export function submit(
  input: MallOrderSubmitInput,
  idempotencyKey: string,
) {
  return http.post<MallOrderSubmitResult>('/api/mall/orders', input, { idempotencyKey })
}
