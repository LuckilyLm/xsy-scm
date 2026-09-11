import { View, Text } from '@tarojs/components'
import { useRef, useState, useEffect } from 'react'
import { useRouter, useDidShow } from '@tarojs/taro'
import { orderDetail } from '../../../services/order'
import {
  formatPrice,
  formatQuantity,
  orderStatusLabel,
  orderSourceLabel,
  priceSourceLabel,
  formatDateTime,
} from '../../../utils/format'
import { errorMessage } from '../../../utils/error'
import type { MallOrder } from '../../../types/mall'
import './index.css'

export default function OrderDetailPage() {
  const router = useRouter()
  const id = Number(router.params.id)
  const [order, setOrder] = useState<MallOrder | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const firstShowRef = useRef(true)

  const load = async () => {
    if (!id) {
      setError('订单参数缺失')
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setOrder(await orderDetail(id))
      setError(null)
    } catch (e: unknown) {
      setError(errorMessage(e, '订单加载失败，请稍后重试'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [id])

  // 从其他页面返回时刷新状态（例如后台确认后状态变化）。
  useDidShow(() => {
    if (firstShowRef.current) {
      firstShowRef.current = false
      return
    }
    load()
  })

  if (loading && !order) {
    return (
      <View className="od">
        <View className="od__loading">加载中...</View>
      </View>
    )
  }

  if (error || !order) {
    return (
      <View className="od">
        <View className="od__error">
          <Text className="od__error-text">{error ?? '订单不存在'}</Text>
          <View className="od__retry" onClick={load}>
            <Text>重新加载</Text>
          </View>
        </View>
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
          const showActual =
            it.actualQuantity && it.actualQuantity !== it.orderedQuantity
          return (
            <View key={it.id} className="od__item">
              <View className="od__item-body">
                <Text className="od__item-name">
                  {it.productName}
                  {it.specName ? `（${it.specName}）` : ''}
                </Text>
                <Text className="od__item-meta">
                  {it.saleUnit}
                  {priceSourceLabel(it.priceSource) ? ` · ${priceSourceLabel(it.priceSource)}` : ''}
                </Text>
              </View>
              <View className="od__item-right">
                <Text className="price price-md">{formatPrice(it.amount)}</Text>
                <Text className="od__item-qty">下单 {formatQuantity(it.orderedQuantity)}</Text>
                {showActual && (
                  <Text className="od__item-actual">实重 {formatQuantity(it.actualQuantity)}</Text>
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
