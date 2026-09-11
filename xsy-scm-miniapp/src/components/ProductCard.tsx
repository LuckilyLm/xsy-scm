import { View, Text } from '@tarojs/components'
import Taro from '@tarojs/taro'
import PriceTag from './PriceTag'
import { priceSourceLabel } from '../utils/format'
import type { MallProduct } from '../types/mall'

interface Props {
  product: MallProduct
  onAdd?: (product: MallProduct) => void
}

export default function ProductCard({ product, onAdd }: Props) {
  const spec = product.specName ? `（${product.specName}）` : ''

  const goDetail = () =>
    Taro.navigateTo({ url: `/subpackages/trade/product/index?skuId=${product.skuId}` })

  const handleAdd = (e: { stopPropagation: () => void }) => {
    e.stopPropagation()
    onAdd?.(product)
  }

  return (
    <View className="product-card" onClick={goDetail}>
      <View className="product-card__body">
        <View className="product-card__title">
          {product.productName}
          {spec}
        </View>
        <View className="product-card__meta">
          <Text className="tag">{product.saleUnit}</Text>
          {product.productType === 'NON_STANDARD' && (
            <Text className="tag tag--warn">非标</Text>
          )}
          {product.priceSource === 'AGREEMENT' && (
            <Text className="tag tag--price">{priceSourceLabel(product.priceSource)}</Text>
          )}
        </View>
        <View className="product-card__footer">
          <PriceTag value={product.unitPrice} size="md" />
          <Text className="product-card__unit">/{product.saleUnit}</Text>
        </View>
      </View>
      <View className="product-card__add" onClick={handleAdd}>
        <Text className="product-card__add-btn">+</Text>
      </View>
    </View>
  )
}
