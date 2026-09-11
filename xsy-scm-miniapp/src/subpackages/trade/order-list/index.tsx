import { View, Text, ScrollView } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useReachBottom } from '@tarojs/taro'
import { orderPage } from '../../../services/order'
import EmptyState from '../../../components/EmptyState'
import { formatPrice, orderStatusLabel, formatDateTime } from '../../../utils/format'
import type { MallOrder, OrderStatus } from '../../../types/mall'
import './index.css'

const PAGE_SIZE = 20

const TABS: { label: string; value: OrderStatus | undefined }[] = [
  { label: '全部', value: undefined },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已取消', value: 'CANCELLED' },
]

export default function OrderListPage() {
  const [tab, setTab] = useState<OrderStatus | undefined>(undefined)
  const [orders, setOrders] = useState<MallOrder[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)

  const load = async (nextPage: number, status: OrderStatus | undefined, append = false) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await orderPage({ page: nextPage, pageSize: PAGE_SIZE, status })
      setOrders((prev) => (append ? [...prev, ...res.records] : res.records))
      setPage(nextPage)
      setHasMore(res.records.length >= PAGE_SIZE && res.total > nextPage * PAGE_SIZE)
    } catch {
      /* toast by request layer */
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(1, tab)
  }, [tab])

  const onTab = (t: OrderStatus | undefined) => {
    if (t === tab) return
    setOrders([])
    setTab(t)
  }

  useReachBottom(() => {
    if (hasMore && !loading) load(page + 1, tab, true)
  })

  const goDetail = (id: number) =>
    Taro.navigateTo({ url: `/subpackages/trade/order-detail/index?id=${id}` })

  return (
    <View className="orders">
      <View className="orders__tabs">
        {TABS.map((t) => (
          <View
            key={t.label}
            className={`orders__tab ${tab === t.value ? 'orders__tab--active' : ''}`}
            onClick={() => onTab(t.value)}
          >
            <Text>{t.label}</Text>
          </View>
        ))}
      </View>

      <ScrollView className="orders__body" scrollY>
        {orders.length === 0 && !loading ? (
          <EmptyState text="暂无订单" />
        ) : (
          orders.map((o) => (
            <View key={o.id} className="orders__item" onClick={() => goDetail(o.id)}>
              <View className="orders__item-head">
                <Text className="orders__no">订单号 {o.orderNo}</Text>
                <Text className="orders__status">{orderStatusLabel(o.status)}</Text>
              </View>
              <View className="orders__item-body">
                <Text className="orders__count">{o.items.length} 件商品</Text>
                <Text className="price price-md">{formatPrice(o.totalAmount)}</Text>
              </View>
              <View className="orders__item-foot">
                <Text className="orders__time">{formatDateTime(o.createdAt)}</Text>
              </View>
            </View>
          ))
        )}
        {loading && <Text className="orders__loading">加载中...</Text>}
        {!hasMore && orders.length > 0 && <Text className="orders__loading">没有更多了</Text>}
      </ScrollView>
    </View>
  )
}
