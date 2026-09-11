import { View, Text, ScrollView, Input } from '@tarojs/components'
import { useRef, useState, useEffect } from 'react'
import Taro, { useReachBottom, useDidShow } from '@tarojs/taro'
import { orderPage } from '../../../services/order'
import EmptyState from '../../../components/EmptyState'
import { formatPrice, orderStatusLabel, formatDateTime } from '../../../utils/format'
import { errorMessage } from '../../../utils/error'
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
  const [keyword, setKeyword] = useState('')
  const [orders, setOrders] = useState<MallOrder[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadingRef = useRef(false)
  const firstShowRef = useRef(true)
  const queryRef = useRef<{ tab: OrderStatus | undefined; keyword: string }>({
    tab: undefined,
    keyword: '',
  })
  queryRef.current = { tab, keyword }

  const load = async (
    nextPage: number,
    status: OrderStatus | undefined,
    kw: string,
    append = false,
  ) => {
    if (loadingRef.current) return
    loadingRef.current = true
    setLoading(true)
    try {
      const res = await orderPage({
        page: nextPage,
        pageSize: PAGE_SIZE,
        status,
        keyword: kw.trim() || undefined,
      })
      setOrders((prev) => (append ? [...prev, ...res.records] : res.records))
      setPage(nextPage)
      setHasMore(res.records.length >= PAGE_SIZE && res.total > nextPage * PAGE_SIZE)
      setError(null)
    } catch (e: unknown) {
      setError(errorMessage(e, '订单加载失败，请稍后重试'))
      if (!append) setOrders([])
    } finally {
      loadingRef.current = false
      setLoading(false)
    }
  }

  const reload = () => load(1, queryRef.current.tab, queryRef.current.keyword)

  useEffect(() => {
    load(1, tab, keyword)
  }, [tab])

  // 下单后返回列表 / 从详情返回时需要看到最新订单状态。
  useDidShow(() => {
    if (firstShowRef.current) {
      firstShowRef.current = false
      return
    }
    reload()
  })

  const onTab = (t: OrderStatus | undefined) => {
    if (t === tab) return
    setOrders([])
    setHasMore(true)
    setTab(t)
  }

  const onSearch = () => reload()

  useReachBottom(() => {
    if (hasMore && !loadingRef.current) {
      load(page + 1, queryRef.current.tab, queryRef.current.keyword, true)
    }
  })

  const goDetail = (id: number) =>
    Taro.navigateTo({ url: `/subpackages/trade/order-detail/index?id=${id}` })

  return (
    <View className="orders">
      <View className="orders__search">
        <Input
          className="orders__search-input"
          placeholder="搜索订单号"
          value={keyword}
          confirmType="search"
          onInput={(e) => setKeyword(e.detail.value)}
          onConfirm={onSearch}
        />
        <View className="orders__search-btn" onClick={onSearch}>
          <Text>搜索</Text>
        </View>
      </View>

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
        {error ? (
          <View className="orders__error">
            <Text className="orders__error-text">{error}</Text>
            <View className="orders__retry" onClick={reload}>
              <Text>重新加载</Text>
            </View>
          </View>
        ) : orders.length === 0 && !loading ? (
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
