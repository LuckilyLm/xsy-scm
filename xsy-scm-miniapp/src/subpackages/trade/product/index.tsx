import { View, Text } from '@tarojs/components'
import { useRef, useState, useEffect } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { product as fetchProduct } from '../../../services/catalog'
import { cartAdd } from '../../../services/cart'
import { setCartBadge } from '../../../utils/cart-badge'
import { useCheckout } from '../../../stores/checkout'
import QuantityStepper from '../../../components/QuantityStepper'
import { formatPrice, priceSourceLabel } from '../../../utils/format'
import { isApiError } from '../../../services/http'
import { errorMessage, showApiError } from '../../../utils/error'
import { MallErrorCode } from '../../../types/mall'
import type { MallProduct } from '../../../types/mall'
import './index.css'

export default function ProductDetailPage() {
  const router = useRouter()
  const skuId = Number(router.params.skuId)
  const [product, setProduct] = useState<MallProduct | null>(null)
  const [qty, setQty] = useState(1)
  const [loading, setLoading] = useState(true)
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const setCheckoutItems = useCheckout((s) => s.setItems)
  const addLockRef = useRef(false)

  const load = async () => {
    if (!skuId) {
      setError('商品参数缺失')
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const data = await fetchProduct(skuId)
      setProduct(data)
      setError(null)
    } catch (e: unknown) {
      // 40372 不可购买 / 40470 已下架：都是“商品不可见”的合法业务结果，不是网络故障。
      setError(
        isApiError(e, MallErrorCode.SKU_NOT_VISIBLE) || isApiError(e, MallErrorCode.SKU_NOT_FOUND)
          ? errorMessage(e, '商品不可购买或已下架')
          : errorMessage(e, '商品加载失败，请稍后重试'),
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [skuId])

  const goBack = () => {
    Taro.navigateBack({ delta: 1 }).catch(() => Taro.switchTab({ url: '/pages/home/index' }))
  }

  const addToCart = async () => {
    if (!product || addLockRef.current) return
    addLockRef.current = true
    setAdding(true)
    try {
      const cart = await cartAdd(product.skuId, String(qty))
      setCartBadge(cart.items.filter((it) => it.available).length)
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch (e: unknown) {
      // 加购失败必须显式提示，不再静默（例如商品已下架或不可购买）。
      showApiError(e, '加入购物车失败，请稍后重试')
    } finally {
      addLockRef.current = false
      setAdding(false)
    }
  }

  const buyNow = () => {
    if (!product) return
    setCheckoutItems([{ skuId: product.skuId, quantity: String(qty) }])
    Taro.navigateTo({ url: '/subpackages/trade/checkout/index' })
  }

  if (loading) {
    return (
      <View className="detail">
        <View className="detail__loading">加载中...</View>
      </View>
    )
  }

  if (error || !product) {
    return (
      <View className="detail">
        <View className="detail__error">
          <Text className="detail__error-text">{error ?? '商品不存在'}</Text>
          <View className="detail__error-btn" onClick={load}>
            <Text>重试</Text>
          </View>
          <View className="detail__error-btn detail__error-btn--ghost" onClick={goBack}>
            <Text>返回</Text>
          </View>
        </View>
      </View>
    )
  }

  const specText = product.specName ? `（${product.specName}）` : ''
  const hasPrice = product.unitPrice !== null && product.unitPrice !== undefined

  return (
    <View className="detail">
      <View className="detail__card">
        <View className="detail__name">
          {product.productName}
          {specText}
        </View>
        <View className="detail__meta">
          <Text className="tag">{product.saleUnit}</Text>
          {product.productType === 'NON_STANDARD' && <Text className="tag tag--warn">非标</Text>}
          {priceSourceLabel(product.priceSource) && (
            <Text className="tag tag--price">{priceSourceLabel(product.priceSource)}</Text>
          )}
        </View>
        <View className="detail__price">
          {/* 价格一律以后端解析结果为准；缺价展示“询价”，不伪造 ¥0.00。 */}
          <Text className="price price-lg">{formatPrice(product.unitPrice, '¥', '询价')}</Text>
          {hasPrice && <Text className="detail__price-unit">/{product.saleUnit}</Text>}
        </View>
        {!hasPrice && (
          <View className="detail__tip">
            <Text>该商品暂未配置可售价格，请联系业务员确认后再下单。</Text>
          </View>
        )}
        {product.productType === 'NON_STANDARD' && (
          <View className="detail__tip">
            <Text>非标品以分拣后实际称重结算，下单数量为预估。</Text>
          </View>
        )}
      </View>

      <View className="detail__row">
        <Text className="detail__row-label">购买数量</Text>
        <QuantityStepper value={qty} min={1} onChange={setQty} />
      </View>

      <View className="detail__bar">
        <View className="detail__bar-add" onClick={() => !adding && addToCart()}>
          <Text>{adding ? '加入中...' : '加入购物车'}</Text>
        </View>
        <View className="detail__bar-buy" onClick={buyNow}>
          <Text>立即购买</Text>
        </View>
      </View>
    </View>
  )
}
