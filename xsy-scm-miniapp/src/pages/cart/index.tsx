import { View, Text, Checkbox } from '@tarojs/components'
import { useRef, useState } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { cartList, cartUpdate, cartRemove } from '../../services/cart'
import { setCartBadge } from '../../utils/cart-badge'
import { useCheckout } from '../../stores/checkout'
import QuantityStepper from '../../components/QuantityStepper'
import EmptyState from '../../components/EmptyState'
import { formatPrice, priceSourceLabel } from '../../utils/format'
import { sumDecimal } from '../../utils/decimal'
import { showApiError } from '../../utils/error'
import type { MallCart, MallCartItem } from '../../types/mall'
import './index.css'

export default function CartPage() {
  const [cart, setCart] = useState<MallCart | null>(null)
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [selected, setSelected] = useState<Record<number, boolean>>({})
  const [loading, setLoading] = useState(true)
  const setCheckoutItems = useCheckout((s) => s.setItems)
  // 每个 SKU 的请求序号：只有最新一次响应可以落地，避免快速改数量时旧响应覆盖新状态。
  const seqRef = useRef<Record<number, number>>({})

  const applyCart = (next: MallCart, preserveSelection = false) => {
    setCart(next)
    setCartBadge(next.items.filter((it) => it.available).length)
    setQuantities((prev) => {
      const merged: Record<number, string> = { ...prev }
      next.items.forEach((it) => {
        merged[it.skuId] = it.quantity
      })
      return merged
    })
    setSelected((prev) => {
      const merged: Record<number, boolean> = {}
      next.items.forEach((it) => {
        merged[it.skuId] = it.available ? (preserveSelection ? prev[it.skuId] ?? true : true) : false
      })
      return merged
    })
  }

  const load = async () => {
    setLoading(true)
    try {
      applyCart(await cartList())
    } catch (e: unknown) {
      showApiError(e, '购物车加载失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  useDidShow(() => load())

  const updateQty = async (skuId: number, qty: number) => {
    const str = String(qty)
    setQuantities((prev) => ({ ...prev, [skuId]: str }))
    const seq = (seqRef.current[skuId] ?? 0) + 1
    seqRef.current[skuId] = seq
    try {
      const next = await cartUpdate(skuId, str)
      if (seqRef.current[skuId] !== seq) return
      applyCart(next, true)
    } catch (e: unknown) {
      if (seqRef.current[skuId] !== seq) return
      showApiError(e, '数量更新失败，请重试')
      load()
    }
  }

  const remove = (skuId: number) => {
    Taro.showModal({
      title: '提示',
      content: '确定从购物车移除该商品？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          applyCart(await cartRemove(skuId), true)
        } catch (e: unknown) {
          showApiError(e, '移除失败，请重试')
          load()
        }
      },
    })
  }

  const toggle = (skuId: number) =>
    setSelected((prev) => ({ ...prev, [skuId]: !prev[skuId] }))

  const hasAvailable = !!cart && cart.items.some((it) => it.available)
  const allSelected =
    hasAvailable && !!cart && cart.items.every((it) => !it.available || selected[it.skuId])
  const toggleAll = () => {
    if (!cart) return
    const next = !allSelected
    const merged: Record<number, boolean> = {}
    cart.items.forEach((it) => {
      merged[it.skuId] = it.available ? next : false
    })
    setSelected(merged)
  }

  const selectedItems: MallCartItem[] =
    cart?.items.filter((it) => it.available && selected[it.skuId]) ?? []
  // 小计只对后端返回的 lineAmount 做精确求和：前端不重新定价，也不用浮点累加。
  const totalAmount = sumDecimal(selectedItems.map((it) => it.lineAmount))

  const checkout = () => {
    if (selectedItems.length === 0) {
      Taro.showToast({ title: '请选择要结算的商品', icon: 'none' })
      return
    }
    setCheckoutItems(selectedItems.map((it) => ({ skuId: it.skuId, quantity: it.quantity })))
    Taro.navigateTo({ url: '/subpackages/trade/checkout/index' })
  }

  if (loading && !cart) {
    return (
      <View className="cart">
        <EmptyState text="购物车加载中..." />
      </View>
    )
  }

  if (!cart || cart.items.length === 0) {
    return (
      <View className="cart">
        <EmptyState text="购物车还是空的" />
        <View className="cart__go" onClick={() => Taro.switchTab({ url: '/pages/home/index' })}>
          <Text>去逛逛</Text>
        </View>
      </View>
    )
  }

  return (
    <View className="cart">
      {cart.unavailableCount > 0 && (
        <View className="cart__notice">
          <Text>{cart.unavailableCount} 件商品已失效，请移除后结算</Text>
        </View>
      )}

      <View className="cart__list">
        {cart.items.map((it) => (
          <View key={it.skuId} className="cart-item">
            <Checkbox
              className="cart-item__check"
              value={String(it.skuId)}
              checked={!!selected[it.skuId]}
              disabled={!it.available}
              onClick={() => it.available && toggle(it.skuId)}
            />
            <View className="cart-item__body">
              <View className="cart-item__name">
                {it.productName}
                {it.specName ? `（${it.specName}）` : ''}
              </View>
              <View className="cart-item__meta">
                {it.saleUnit && <Text className="tag">{it.saleUnit}</Text>}
                {priceSourceLabel(it.priceSource) && (
                  <Text className="tag tag--price">{priceSourceLabel(it.priceSource)}</Text>
                )}
              </View>
              {it.available ? (
                <View className="cart-item__bottom">
                  <Text className="price price-md">{formatPrice(it.lineAmount)}</Text>
                  <QuantityStepper
                    value={Number(quantities[it.skuId] ?? it.quantity)}
                    min={1}
                    onChange={(v) => updateQty(it.skuId, v)}
                  />
                </View>
              ) : (
                <Text className="cart-item__unavailable">已失效：{it.reason}</Text>
              )}
            </View>
            <View className="cart-item__remove" onClick={() => remove(it.skuId)}>
              <Text>删除</Text>
            </View>
          </View>
        ))}
      </View>

      <View className="cart__bar">
        <View className="cart__bar-left" onClick={toggleAll}>
          <Checkbox className="cart-item__check" value="__all__" checked={allSelected} />
          <Text>全选</Text>
        </View>
        <View className="cart__bar-right">
          <Text className="cart__total-label">合计：</Text>
          <Text className="price price-lg">{formatPrice(totalAmount)}</Text>
          <View className="cart__checkout" onClick={checkout}>
            <Text>结算({selectedItems.length})</Text>
          </View>
        </View>
      </View>
    </View>
  )
}
