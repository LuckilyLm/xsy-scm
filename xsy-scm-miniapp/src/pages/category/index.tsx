import { View, Text, ScrollView } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { useReachBottom } from '@tarojs/taro'
import { categories, products } from '../../services/catalog'
import { cartAdd } from '../../services/cart'
import { setCartBadge } from '../../utils/cart-badge'
import { showApiError } from '../../utils/error'
import ProductCard from '../../components/ProductCard'
import EmptyState from '../../components/EmptyState'
import type { MallCategory, MallProduct } from '../../types/mall'
import './index.css'

const PAGE_SIZE = 20

export default function CategoryPage() {
  const [cats, setCats] = useState<MallCategory[]>([])
  const [activeId, setActiveId] = useState<number | undefined>(undefined)
  const [list, setList] = useState<MallProduct[]>([])
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)

  const loadProducts = async (nextPage: number, catId: number, append = false) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await products({ page: nextPage, pageSize: PAGE_SIZE, categoryId: catId })
      setList((prev) => (append ? [...prev, ...res.records] : res.records))
      setPage(nextPage)
      setHasMore(res.records.length >= PAGE_SIZE && res.total > nextPage * PAGE_SIZE)
    } catch {
      /* toast by request layer */
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    categories()
      .then((data) => {
        const tops = data.filter((c) => c.level === 1)
        setCats(tops)
        if (tops.length > 0) {
          setActiveId(tops[0].id)
          loadProducts(1, tops[0].id)
        }
      })
      .catch(() => undefined)
  }, [])

  const onSelect = (id: number) => {
    if (id === activeId) return
    setActiveId(id)
    setList([])
    loadProducts(1, id)
  }

  useReachBottom(() => {
    if (hasMore && !loading && activeId !== undefined) {
      loadProducts(page + 1, activeId, true)
    }
  })

  const onAdd = async (p: MallProduct) => {
    try {
      const cart = await cartAdd(p.skuId, '1')
      setCartBadge(cart.items.filter((it) => it.available).length)
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch (e: unknown) {
      showApiError(e, '加入购物车失败，请稍后重试')
    }
  }

  return (
    <View className="category">
      <ScrollView className="category__side" scrollY>
        {cats.map((c) => (
          <View
            key={c.id}
            className={`category__side-item ${activeId === c.id ? 'category__side-item--active' : ''}`}
            onClick={() => onSelect(c.id)}
          >
            <Text>{c.name}</Text>
          </View>
        ))}
      </ScrollView>
      <ScrollView className="category__main" scrollY>
        {list.length === 0 && !loading ? (
          <EmptyState text="该分类暂无商品" />
        ) : (
          list.map((p) => <ProductCard key={p.skuId} product={p} onAdd={onAdd} />)
        )}
        {loading && <Text className="category__loading">加载中...</Text>}
        {!hasMore && list.length > 0 && <Text className="category__loading">没有更多了</Text>}
      </ScrollView>
    </View>
  )
}
