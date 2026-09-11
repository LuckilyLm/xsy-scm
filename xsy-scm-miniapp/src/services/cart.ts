import { http } from './http'
import type { MallCart } from '../types/mall'

/** 数量必须以字符串传递（后端 DecimalStringDeserializer 要求 JSON 字符串）。 */
export function cartList() {
  return http.get<MallCart>('/api/mall/cart')
}

export function cartAdd(skuId: number, quantity: string) {
  return http.post<MallCart>('/api/mall/cart/items', { skuId, quantity })
}

export function cartUpdate(skuId: number, quantity: string) {
  return http.put<MallCart>(`/api/mall/cart/items/${skuId}`, { skuId, quantity })
}

export function cartRemove(skuId: number) {
  return http.del<MallCart>(`/api/mall/cart/items/${skuId}`)
}
