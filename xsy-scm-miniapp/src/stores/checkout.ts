import { create } from 'zustand'
import type { MallCheckoutItemInput } from '../types/mall'

/**
 * 结算中转：购物车勾选商品后，将 {skuId, quantity} 列表放入本 store，
 * 再跳转结算页读取，避免通过 URL 传递复杂数组。
 */
interface CheckoutStore {
  items: MallCheckoutItemInput[]
  setItems: (items: MallCheckoutItemInput[]) => void
  clear: () => void
}

export const useCheckout = create<CheckoutStore>((set) => ({
  items: [],
  setItems: (items) => set({ items }),
  clear: () => set({ items: [] }),
}))
