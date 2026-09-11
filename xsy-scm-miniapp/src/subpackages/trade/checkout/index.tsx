import { View, Text } from '@tarojs/components'
import { useRef, useState, useEffect } from 'react'
import Taro, { useDidShow } from '@tarojs/taro'
import { preview, submit } from '../../../services/checkout'
import { addressList } from '../../../services/address'
import { setCartBadge } from '../../../utils/cart-badge'
import { useCheckout } from '../../../stores/checkout'
import { uuid } from '../../../utils/id'
import { formatPrice, priceSourceLabel } from '../../../utils/format'
import { isApiError } from '../../../services/http'
import { errorMessage, showApiError } from '../../../utils/error'
import { MallErrorCode } from '../../../types/mall'
import type {
  MallAddress,
  MallCheckoutItemInput,
  MallCheckoutPreview,
} from '../../../types/mall'
import './index.css'

export default function CheckoutPage() {
  const storeItems = useCheckout((s) => s.items)
  const clearCheckout = useCheckout((s) => s.clear)

  const [addresses, setAddresses] = useState<MallAddress[]>([])
  const [addressId, setAddressId] = useState<number | undefined>(undefined)
  const [previewData, setPreviewData] = useState<MallCheckoutPreview | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)

  // 幂等键与“下单内容签名”绑定：内容不变时网络重试复用同一个 key（后端据此去重），
  // 只有内容变化（改地址 / 改商品 / 变价后重新预览）或提交成功才换新 key。
  const keyRef = useRef('')
  const signatureRef = useRef('')
  const submitLockRef = useRef(false)
  const previewSeqRef = useRef(0)

  const signatureOf = (items: MallCheckoutItemInput[], addrId: number, fingerprint: string) =>
    JSON.stringify({ items, addrId, fingerprint })

  const runPreview = async (addrId: number) => {
    if (storeItems.length === 0) return
    const seq = ++previewSeqRef.current
    setLoading(true)
    setPreviewError(null)
    try {
      const data = await preview(storeItems, addrId)
      // 快速切换地址时丢弃过期响应，避免旧预览覆盖新地址。
      if (previewSeqRef.current !== seq) return
      setPreviewData(data)
    } catch (e: unknown) {
      if (previewSeqRef.current !== seq) return
      setPreviewData(null)
      setPreviewError(errorMessage(e, '结算信息加载失败，请重试'))
    } finally {
      if (previewSeqRef.current === seq) setLoading(false)
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
        setAddressId(undefined)
        setPreviewData(null)
        Taro.showToast({ title: '请先添加收货地址', icon: 'none' })
      }
    } catch (e: unknown) {
      showApiError(e, '收货地址加载失败')
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
    // ref 互斥：state 更新异步，只有 ref 能真正挡住连点造成的重复提交。
    if (submitLockRef.current) return
    if (addressId === undefined) {
      Taro.showToast({ title: '请选择收货地址', icon: 'none' })
      return
    }
    if (!previewData) {
      Taro.showToast({ title: '订单信息加载中，请稍候', icon: 'none' })
      return
    }

    const signature = signatureOf(storeItems, addressId, previewData.priceFingerprint)
    if (signatureRef.current !== signature) {
      signatureRef.current = signature
      keyRef.current = uuid()
    }

    submitLockRef.current = true
    setSubmitting(true)
    try {
      const res = await submit(
        {
          items: storeItems,
          addressId,
          priceFingerprint: previewData.priceFingerprint,
        },
        keyRef.current,
      )
      keyRef.current = ''
      signatureRef.current = ''
      clearCheckout()
      setCartBadge(0)
      Taro.showToast({ title: '下单成功', icon: 'success' })
      setTimeout(
        () => Taro.redirectTo({ url: `/subpackages/trade/order-detail/index?id=${res.orderId}` }),
        600,
      )
    } catch (e: unknown) {
      if (isApiError(e, MallErrorCode.PRICE_CHANGED)) {
        // 指纹即将变化，作废旧键；重新预览并让客户再次确认。
        keyRef.current = ''
        signatureRef.current = ''
        Taro.showToast({ title: '价格已变化，请重新确认', icon: 'none' })
        runPreview(addressId)
        return
      }
      if (
        isApiError(e, MallErrorCode.SKU_NOT_VISIBLE) ||
        isApiError(e, MallErrorCode.SKU_NOT_FOUND)
      ) {
        // 商品在结算过程中被下架或取消可见：刷新预览并提示原因。
        showApiError(e)
        runPreview(addressId)
        return
      }
      // 其余错误（网络/超时）保留幂等键：客户重试时复用同一 key，不会重复建单。
      showApiError(e, '下单失败，请稍后重试')
    } finally {
      submitLockRef.current = false
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

  const canSubmit = !!previewData && addressId !== undefined && !loading && !submitting

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

      {previewData && previewData.unavailableCount > 0 && (
        <View className="checkout__warn">
          <Text>{previewData.unavailableCount} 件商品已失效，请返回购物车调整</Text>
        </View>
      )}

      <View className="checkout__items">
        {previewError ? (
          <View className="checkout__error">
            <Text className="checkout__error-text">{previewError}</Text>
            {addressId !== undefined && (
              <View className="checkout__retry" onClick={() => runPreview(addressId)}>
                <Text>重新加载</Text>
              </View>
            )}
          </View>
        ) : loading && !previewData ? (
          <View className="checkout__error">
            <Text className="checkout__error-text">结算信息加载中...</Text>
          </View>
        ) : (
          previewData?.items.map((it) => (
            <View key={it.skuId} className="checkout__item">
              <View className="checkout__item-body">
                <Text className="checkout__item-name">
                  {it.productName}
                  {it.specName ? `（${it.specName}）` : ''}
                </Text>
                <Text className="checkout__item-meta">
                  {it.saleUnit}
                  {priceSourceLabel(it.priceSource) ? ` · ${priceSourceLabel(it.priceSource)}` : ''}
                </Text>
              </View>
              <View className="checkout__item-right">
                <Text className="price price-md">{formatPrice(it.lineAmount)}</Text>
                <Text className="checkout__item-qty">x{it.quantity}</Text>
              </View>
            </View>
          ))
        )}
      </View>

      <View className="checkout__bar">
        <View className="checkout__bar-total">
          <Text>合计：</Text>
          <Text className="price price-lg">
            {formatPrice(previewData?.totalAmount ?? null)}
          </Text>
        </View>
        <View
          className={`checkout__submit ${canSubmit ? '' : 'checkout__submit--disabled'}`}
          onClick={onSubmit}
        >
          <Text>{submitting ? '提交中...' : '提交订单'}</Text>
        </View>
      </View>
    </View>
  )
}
