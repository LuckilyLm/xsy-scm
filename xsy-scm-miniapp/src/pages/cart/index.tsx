import { View, Text, Checkbox } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { cartList, cartUpdate, cartRemove } from '../../services/cart'
import { setCartBadge } from '../../utils/cart-badge'
import { useCheckout } from '../../stores/checkout'
import QuantityStepper from '../../components/QuantityStepper'
import EmptyState from '../../components/EmptyState'
import { formatPrice, priceSourceLabel } from '../../utils/format'
import type { MallCart, MallCartItem } from '../../types/mall'
import './index.css'

export default function CartPage() {
  const [cart, setCart] = useState<MallCart | null>(null)
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  const [selected, setSelected] = useState<Record<number, boolean>>({})
  const setCheckoutItems = useCheckout((s) => s.setItems)

  const load = async () => {
    try {
      const c = await cartList()
      setCart(c)
      setCartBadge(c.items.length)
      const q: Record<number, string> = {}
      const sel: Record<number, boolean> = {}
      c.items.forEach((it) => {
        q[it.skuId] = it.quantity
        sel[it.skuId] = it.available
      })
      setQuantities(q)
      setSelected(sel)
    } catch {
      /* toast by request layer */
    }
  }

  useDidShow(() => load())

  useEffect(() => {
    /* noop，保留以兼容 hooks 规则 */
  }, [])

  const updateQty = async (skuId: number, qty: number) => {
    const str = String(qty)
    setQuantities((prev) => ({ ...prev, [skuId]: str }))
    try {
      const c = await cartUpdate(skuId, str)
      setCart(c)
      setCartBadge(c.items.length)
    } catch {
      load()
    }
  }

  const remove = async (skuId: number) => {
    Taro.showModal({
      title: '提示',
      content: '确定从购物车移除该商品？',
      success: async (res) => {
        if (!res.confirm) return
        try {
          const c = await cartRemove(skuId)
          setCart(c)
          setCartBadge(c.items.length)
        } catch {
          load()
        }
      },
    })
  }

  const toggle = (skuId: number) =>
    setSelected((prev) => ({ ...prev, [skuId]: !prev[skuId] }))

  const allSelected =
    !!cart && cart.items.length > 0 && cart.items.every((it) => !it.available || selected[it.skuId])
  const toggleAll = () => {
    if (!cart) return
    const next = !allSelected
    const sel: Record<number, boolean> = {}
    cart.items.forEach((it) => {
      sel[it.skuId] = !it.available ? false : next
    })
    setSelected(sel)
  }

  const selectedItems: MallCartItem[] =
    cart?.items.filter((it) => it.available && selected[it.skuId]) ?? []
  const totalAmount = selectedItems.reduce((sum, it) => sum + (Number(it.lineAmount) || 0), 0)

  const checkout = () => {
    if (selectedItems.length === 0) {
      Taro.showToast({ title: '请选择要结算的商品', icon: 'none' })
      return
    }
    setCheckoutItems(selectedItems.map((it) => ({ skuId: it.skuId, quantity: it.quantity })))
    Taro.navigateTo({ url: '/subpackages/trade/checkout/index' })
  }

  if (!cart) {
    return <EmptyState text="购物车加载中..." />
  }

  if (cart.items.length === 0) {
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
      <View className="cart__list">
        {cart.items.map((it) => (
          <View key={it.skuId} className="cart-item">
            <Checkbox
              className="cart-item__check"
              value={String(it.skuId)}
              checked={!!selected[it.skuId]}
              disabled={!it.available}
              onClick={() => toggle(it.skuId)}
            />
            <View className="cart-item__body">
              <View className="cart-item__name">
                {it.productName}
                {it.specName ? `（${it.specName}）` : ''}
              </View>
              <View className="cart-item__meta">
                <Text className="tag">{it.saleUnit}</Text>
                {it.priceSource === 'AGREEMENT' && (
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
          <Checkbox value="__all__" checked={allSelected} onClick={toggleAll} />
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
