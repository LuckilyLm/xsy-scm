import { View, Text } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { preview, submit } from '../../../services/checkout'
import { addressList } from '../../../services/address'
import { setCartBadge } from '../../../utils/cart-badge'
import { useCheckout } from '../../../stores/checkout'
import { uuid } from '../../../utils/id'
import { formatPrice, priceSourceLabel } from '../../../utils/format'
import { ApiError } from '../../../services/http'
import { MallErrorCode } from '../../../types/mall'
import type { MallAddress, MallCheckoutPreview } from '../../../types/mall'
import './index.css'

export default function CheckoutPage() {
  const storeItems = useCheckout((s) => s.items)
  const clearCheckout = useCheckout((s) => s.clear)

  const [addresses, setAddresses] = useState<MallAddress[]>([])
  const [addressId, setAddressId] = useState<number | undefined>(undefined)
  const [previewData, setPreviewData] = useState<MallCheckoutPreview | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const runPreview = async (addrId: number) => {
    if (storeItems.length === 0) return
    setLoading(true)
    try {
      const data = await preview(storeItems, addrId)
      setPreviewData(data)
    } catch {
      /* toast by request layer */
    } finally {
      setLoading(false)
    }
  }

  const loadAddresses = async () => {
    try {
      const list = await addressList()
      setAddresses(list)
      const def = list.find((a) => a.defaultAddress) ?? list[0]
      if (def) {
        setAddressId(def.id)
        runPreview(def.id)
      } else {
        Taro.showToast({ title: '请先添加收货地址', icon: 'none' })
      }
    } catch {
      /* toast by request layer */
    }
  }

  useDidShow(() => {
    if (storeItems.length === 0) return
    loadAddresses()
  })

  // 进入页面若没有选中商品，提示返回购物车
  useEffect(() => {
    if (storeItems.length === 0) {
      Taro.showToast({ title: '请先选择商品', icon: 'none' })
    }
  }, [storeItems.length])

  const onSelectAddress = (id: number) => {
    setAddressId(id)
    runPreview(id)
  }

  const goAddAddress = () =>
    Taro.navigateTo({ url: '/subpackages/account/address-edit/index' })

  const onSubmit = async () => {
    if (addressId === undefined) {
      Taro.showToast({ title: '请选择收货地址', icon: 'none' })
      return
    }
    if (!previewData) {
      Taro.showToast({ title: '订单信息加载中，请稍候', icon: 'none' })
      return
    }
    setSubmitting(true)
    try {
      const key = uuid()
      const res = await submit(
        {
          items: storeItems,
          addressId,
          priceFingerprint: previewData.priceFingerprint,
        },
        key,
      )
      clearCheckout()
      setCartBadge(0)
      Taro.showToast({ title: '下单成功', icon: 'success' })
      setTimeout(
        () => Taro.redirectTo({ url: `/subpackages/trade/order-detail/index?id=${res.orderId}` }),
        600,
      )
    } catch (e) {
      if (e instanceof ApiError && e.code === MallErrorCode.PRICE_CHANGED) {
        Taro.showToast({ title: '价格已变化，请重新确认', icon: 'none' })
        if (addressId !== undefined) runPreview(addressId)
        return
      }
      /* 其他错误已由请求层 toast */
    } finally {
      setSubmitting(false)
    }
  }

  if (storeItems.length === 0) {
    return (
      <View className="checkout">
        <View className="checkout__empty">请先在购物车选择要结算的商品</View>
        <View
          className="checkout__back"
          onClick={() => Taro.switchTab({ url: '/pages/cart/index' })}
        >
          <Text>去购物车</Text>
        </View>
      </View>
    )
  }

  return (
    <View className="checkout">
      <View className="checkout__address" onClick={goAddAddress}>
        {addresses.length === 0 ? (
          <Text className="checkout__address-add">+ 添加收货地址</Text>
        ) : (
          addresses.map((a) => (
            <View
              key={a.id}
              className={`checkout__addr-item ${addressId === a.id ? 'checkout__addr-item--active' : ''}`}
              onClick={(e) => {
                e.stopPropagation()
                onSelectAddress(a.id)
              }}
            >
              <Text className="checkout__addr-line">
                {a.receiverName} {a.phone}
              </Text>
              <Text className="checkout__addr-detail">
                {a.region} {a.detailAddress}
              </Text>
            </View>
          ))
        )}
      </View>

      <View className="checkout__items">
        {previewData?.items.map((it) => (
          <View key={it.skuId} className="checkout__item">
            <View className="checkout__item-body">
              <Text className="checkout__item-name">
                {it.productName}
                {it.specName ? `（${it.specName}）` : ''}
              </Text>
              <Text className="checkout__item-meta">
                {it.saleUnit} · {priceSourceLabel(it.priceSource)}
              </Text>
            </View>
            <View className="checkout__item-right">
              <Text className="price price-md">{formatPrice(it.lineAmount)}</Text>
              <Text className="checkout__item-qty">x{it.quantity}</Text>
            </View>
          </View>
        ))}
      </View>

      <View className="checkout__bar">
        <View className="checkout__bar-total">
          <Text>合计：</Text>
          <Text className="price price-lg">
            {formatPrice(previewData?.totalAmount ?? '0')}
          </Text>
        </View>
        <View
          className="checkout__submit"
          onClick={() => !submitting && !loading && onSubmit()}
        >
          <Text>{submitting ? '提交中...' : '提交订单'}</Text>
        </View>
      </View>
    </View>
  )
}
