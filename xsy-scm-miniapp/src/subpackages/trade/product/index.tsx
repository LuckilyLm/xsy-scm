import { View, Text } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useRouter } from '@tarojs/taro'
import { product as fetchProduct } from '../../../services/catalog'
import { cartAdd } from '../../../services/cart'
import { setCartBadge } from '../../../utils/cart-badge'
import { useCheckout } from '../../../stores/checkout'
import QuantityStepper from '../../../components/QuantityStepper'
import { formatPrice, priceSourceLabel } from '../../../utils/format'
import type { MallProduct } from '../../../types/mall'
import './index.css'

export default function ProductDetailPage() {
  const router = useRouter()
  const skuId = Number(router.params.skuId)
  const [product, setProduct] = useState<MallProduct | null>(null)
  const [qty, setQty] = useState(1)
  const [loading, setLoading] = useState(false)
  const setCheckoutItems = useCheckout((s) => s.setItems)

  useEffect(() => {
    if (!skuId) return
    fetchProduct(skuId)
      .then(setProduct)
      .catch(() => undefined)
  }, [skuId])

  const addToCart = async () => {
    if (!product) return
    setLoading(true)
    try {
      const cart = await cartAdd(product.skuId, String(qty))
      setCartBadge(cart.items.length)
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch {
      /* toast by request layer */
    } finally {
      setLoading(false)
    }
  }

  const buyNow = () => {
    if (!product) return
    setCheckoutItems([{ skuId: product.skuId, quantity: String(qty) }])
    Taro.navigateTo({ url: '/subpackages/trade/checkout/index' })
  }

  if (!product) {
    return (
      <View className="detail">
        <View className="detail__loading">加载中...</View>
      </View>
    )
  }

  const specText = product.specName ? `（${product.specName}）` : ''

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
          <Text className="tag tag--price">{priceSourceLabel(product.priceSource)}</Text>
        </View>
        <View className="detail__price">
          <Text className="price price-lg">{formatPrice(product.unitPrice)}</Text>
          <Text className="detail__price-unit">/{product.saleUnit}</Text>
        </View>
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
        <View className="detail__bar-add" onClick={() => !loading && addToCart()}>
          <Text>{loading ? '加入中...' : '加入购物车'}</Text>
        </View>
        <View className="detail__bar-buy" onClick={buyNow}>
          <Text>立即购买</Text>
        </View>
      </View>
    </View>
  )
}
