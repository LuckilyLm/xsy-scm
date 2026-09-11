import { View, Text } from '@tarojs/components'
import { useState, useEffect } from 'react'
import { useRouter } from '@tarojs/taro'
import { orderDetail } from '../../../services/order'
import { formatPrice, orderStatusLabel, orderSourceLabel, priceSourceLabel, formatDateTime } from '../../../utils/format'
import type { MallOrder } from '../../../types/mall'
import './index.css'

export default function OrderDetailPage() {
  const router = useRouter()
  const id = Number(router.params.id)
  const [order, setOrder] = useState<MallOrder | null>(null)

  useEffect(() => {
    if (!id) return
    orderDetail(id)
      .then(setOrder)
      .catch(() => undefined)
  }, [id])

  if (!order) {
    return (
      <View className="od">
        <View className="od__loading">加载中...</View>
      </View>
    )
  }

  return (
    <View className="od">
      <View className="od__head">
        <View className="od__head-left">
          <Text className="od__status">{orderStatusLabel(order.status)}</Text>
          <Text className="od__source">{orderSourceLabel(order.source)}</Text>
        </View>
        <Text className="od__no">{order.orderNo}</Text>
      </View>

      <View className="od__items">
        {order.items.map((it) => {
          const showActual = it.actualQuantity && it.actualQuantity !== it.orderedQuantity
          return (
            <View key={it.id} className="od__item">
              <View className="od__item-body">
                <Text className="od__item-name">
                  {it.productName}
                  {it.specName ? `（${it.specName}）` : ''}
                </Text>
                <Text className="od__item-meta">
                  {it.saleUnit} · {priceSourceLabel(it.priceSource)}
                </Text>
              </View>
              <View className="od__item-right">
                <Text className="price price-md">{formatPrice(it.amount)}</Text>
                <Text className="od__item-qty">下单 {it.orderedQuantity}</Text>
                {showActual && (
                  <Text className="od__item-actual">实重 {it.actualQuantity}</Text>
                )}
              </View>
            </View>
          )
        })}
      </View>

      <View className="od__summary">
        <View className="od__summary-row">
          <Text>商品金额</Text>
          <Text className="price price-md">{formatPrice(order.totalAmount)}</Text>
        </View>
        <View className="od__summary-row">
          <Text>下单时间</Text>
          <Text className="od__text">{formatDateTime(order.createdAt)}</Text>
        </View>
        {order.submittedAt && (
          <View className="od__summary-row">
            <Text>提交时间</Text>
            <Text className="od__text">{formatDateTime(order.submittedAt)}</Text>
          </View>
        )}
        {order.confirmedAt && (
          <View className="od__summary-row">
            <Text>确认时间</Text>
            <Text className="od__text">{formatDateTime(order.confirmedAt)}</Text>
          </View>
        )}
      </View>
    </View>
  )
}
