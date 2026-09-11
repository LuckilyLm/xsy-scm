import { Image, Input, ScrollView, Text, View } from '@tarojs/components'
import { useState, useEffect } from 'react'
import Taro, { usePullDownRefresh, useReachBottom } from '@tarojs/taro'
import type { CSSProperties } from 'react'
import { categories, home as fetchHome, products } from '../../services/catalog'
import { cartAdd } from '../../services/cart'
import { setCartBadge } from '../../utils/cart-badge'
import { formatPrice } from '../../utils/format'
import { showApiError } from '../../utils/error'
import ProductCard from '../../components/ProductCard'
import EmptyState from '../../components/EmptyState'
import type {
  MallCategory,
  MallHomeProduct,
  MallHomeResponse,
  MallHomeSection,
  MallHomeSectionPayload,
  MallProduct,
  MallPromotion,
} from '../../types/mall'
import './index.css'

const PAGE_SIZE = 20

function payloadOf(section: MallHomeSection): MallHomeSectionPayload {
  return section.payload || {}
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value : undefined
}

function PayloadProductGrid({ products: items }: { products: MallHomeProduct[] }) {
  return (
    <View className="home__payload-products">
      {items.map((item, index) => (
        <View className="home__payload-product" key={`${item.skuId || item.productName || 'item'}-${index}`}>
          {item.imageUrl ? (
            <Image className="home__payload-product-image" src={item.imageUrl} mode="aspectFill" />
          ) : (
            <View className="home__payload-product-image home__payload-product-image--empty">
              <Text>鲜</Text>
            </View>
          )}
          <Text className="home__payload-product-name">{item.productName || '精选商品'}</Text>
          <View className="home__payload-product-meta">
            <Text className="home__payload-product-price">{formatPrice(item.unitPrice)}</Text>
            {item.saleUnit && <Text className="home__payload-product-unit">/{item.saleUnit}</Text>}
          </View>
        </View>
      ))}
    </View>
  )
}

function PromotionGrid({ promotions }: { promotions: MallPromotion[] }) {
  return (
    <View className="home__promotions">
      {promotions.map((promotion) => (
        <View className="home__promotion" key={promotion.id}>
          <View className="home__promotion-head">
            <Text className="home__promotion-name">{promotion.name}</Text>
            {promotion.effective && <Text className="tag tag--warn">进行中</Text>}
          </View>
          <Text className="home__promotion-description">
            {promotion.description || (promotion.type === 'FLASH_SALE' ? '限时优惠，先到先得' : '精选商品专享活动')}
          </Text>
        </View>
      ))}
    </View>
  )
}

export default function HomePage() {
  const [keyword, setKeyword] = useState('')
  const [cats, setCats] = useState<MallCategory[]>([])
  const [activeCat, setActiveCat] = useState<number | undefined>(undefined)
  const [list, setList] = useState<MallProduct[]>([])
  const [homeData, setHomeData] = useState<MallHomeResponse | null>(null)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)

  const loadProducts = async (
    nextPage: number,
    cat?: number,
    kw?: string,
    append = false,
  ) => {
    if (loading) return
    setLoading(true)
    try {
      const res = await products({ page: nextPage, pageSize: PAGE_SIZE, categoryId: cat, keyword: kw })
      setList((prev) => (append ? [...prev, ...res.records] : res.records))
      setPage(nextPage)
      setHasMore(res.records.length >= PAGE_SIZE && res.total > nextPage * PAGE_SIZE)
    } catch {
      /* 错误已由请求层 toast 提示 */
    } finally {
      setLoading(false)
      Taro.stopPullDownRefresh()
    }
  }

  const loadHome = () =>
    fetchHome()
      .then((data) => setHomeData(data))
      .catch(() => undefined)

  useEffect(() => {
    loadHome()
    categories()
      .then((data) => setCats(data.filter((c) => c.level === 1)))
      .catch(() => undefined)
    loadProducts(1)
  }, [])

  const onSearch = () => loadProducts(1, activeCat, keyword.trim() || undefined)
  const onSelectCat = (id?: number) => {
    setActiveCat(id)
    loadProducts(1, id, keyword.trim() || undefined)
  }

  usePullDownRefresh(() => {
    loadHome()
    loadProducts(1, activeCat, keyword.trim() || undefined)
  })
  useReachBottom(() => {
    if (hasMore && !loading) loadProducts(page + 1, activeCat, keyword.trim() || undefined, true)
  })

  const onAdd = async (p: MallProduct) => {
    try {
      const cart = await cartAdd(p.skuId, '1')
      setCartBadge(cart.items.filter((it) => it.available).length)
      Taro.showToast({ title: '已加入购物车', icon: 'success' })
    } catch (e: unknown) {
      // 可见性（40372）等失败必须显式反馈，避免“点了没反应”。
      showApiError(e, '加入购物车失败，请稍后重试')
    }
  }

  const homeCategories = homeData?.categories?.filter((c) => c.level === 1) || cats
  const sections = [...(homeData?.sections || [])].sort(
    (a, b) => (a.sortOrder ?? Number.MAX_SAFE_INTEGER) - (b.sortOrder ?? Number.MAX_SAFE_INTEGER),
  )
  const theme = homeData?.theme
  const themeStyle = theme
    ? ({
        '--color-primary': theme.primaryColor || '#16a34a',
        '--color-accent': theme.accentColor || '#f97316',
        '--color-bg': theme.pageBackground || '#f5f7fa',
        '--home-card-radius': `${Math.max(theme.cardRadius || 16, 8) * 2}rpx`,
        '--home-card-shadow': theme.cardStyle === 'FLAT' ? 'none' : '0 8rpx 24rpx rgba(31, 35, 41, .06)',
        '--home-card-border': theme.cardStyle === 'SHADOW' ? 'transparent' : 'var(--color-border)',
      } as CSSProperties)
    : undefined

  const renderSection = (section: MallHomeSection) => {
    const payload = payloadOf(section)
    const title = section.title || textValue(payload.title) || '精选内容'
    const description = textValue(payload.description) || textValue(payload.text)

    if (section.sectionType === 'BANNER') {
      return (
        <View className="home__banner" key={section.id} onClick={() => {
          if (payload.targetUrl?.startsWith('/')) Taro.navigateTo({ url: payload.targetUrl })
        }}>
          {payload.imageUrl && <Image className="home__banner-image" src={payload.imageUrl} mode="aspectFill" />}
          <View className="home__banner-mask" />
          <View className="home__banner-content">
            <Text className="home__banner-eyebrow">FRESH SUPPLY · SMART PROCUREMENT</Text>
            <Text className="home__banner-title">{title}</Text>
            <Text className="home__banner-description">{description || '精选应季食材与优质商品，稳定供应。'}</Text>
          </View>
        </View>
      )
    }

    if (section.sectionType === 'CUSTOM' && payload.notice) {
      return (
        <View className="home__notice" key={section.id}>
          <Text className="home__notice-icon">!</Text>
          <Text className="home__notice-title">{title === '精选内容' ? '商城公告' : title}</Text>
          <Text className="home__notice-text">{payload.notice}</Text>
        </View>
      )
    }

    if (section.sectionType === 'CATEGORY') {
      const categoryItems = payload.categories?.length ? payload.categories : homeCategories
      return (
        <View className="home__section" key={section.id}>
          <View className="home__section-heading">
            <Text className="home__section-title">{title}</Text>
            {description && <Text className="home__section-hint">{description}</Text>}
          </View>
          <View className="home__category-grid">
            {categoryItems.slice(0, 8).map((category) => (
              <View className="home__category-card" key={category.id} onClick={() => onSelectCat(category.id)}>
                <Text className="home__category-name">{category.name}</Text>
                <Text className="home__category-count">{category.productCount} 件商品</Text>
              </View>
            ))}
          </View>
        </View>
      )
    }

    if (section.sectionType === 'FLASH_SALE' && homeData?.promotions?.length) {
      return (
        <View className="home__section" key={section.id}>
          <View className="home__section-heading">
            <Text className="home__section-title">{title === '精选内容' ? '限时活动' : title}</Text>
            {description && <Text className="home__section-hint">{description}</Text>}
          </View>
          <PromotionGrid promotions={homeData.promotions} />
        </View>
      )
    }

    const payloadProducts = payload.products || []
    if (['RECOMMEND', 'NEW_ARRIVAL'].includes(section.sectionType) && payloadProducts.length) {
      return (
        <View className="home__section" key={section.id}>
          <View className="home__section-heading">
            <Text className="home__section-title">{title}</Text>
            {description && <Text className="home__section-hint">{description}</Text>}
          </View>
          <PayloadProductGrid products={payloadProducts} />
        </View>
      )
    }

    if (description || payload.items?.length) {
      return (
        <View className="home__section" key={section.id}>
          <View className="home__section-heading">
            <Text className="home__section-title">{title}</Text>
          </View>
          {description && <Text className="home__section-description">{description}</Text>}
          {payload.items?.map((item, index) => (
            <View className="home__payload-item" key={`${section.id}-${index}`}>
              <Text>{textValue(item.title) || textValue(item.name) || '活动内容'}</Text>
              {textValue(item.description) && <Text className="home__payload-item-description">{textValue(item.description)}</Text>}
            </View>
          ))}
        </View>
      )
    }

    return null
  }

  const hasPromotionSection = sections.some((section) => section.sectionType === 'FLASH_SALE')

  return (
    <View className="home" style={themeStyle}>
      <View className="home__search">
        <Input
          className="home__search-input"
          placeholder="搜索商品名称"
          value={keyword}
          confirmType="search"
          onInput={(e) => setKeyword(e.detail.value)}
          onConfirm={onSearch}
        />
        <View className="home__search-btn" onClick={onSearch}>
          <Text>搜索</Text>
        </View>
      </View>

      {sections.map(renderSection)}
      {!hasPromotionSection && homeData?.promotions?.length ? (
        <View className="home__section">
          <View className="home__section-heading">
            <Text className="home__section-title">限时活动</Text>
            <Text className="home__section-hint">当前可参与的优惠</Text>
          </View>
          <PromotionGrid promotions={homeData.promotions} />
        </View>
      ) : null}

      <ScrollView className="home__cats" scrollX>
        <View
          className={`home__cat ${activeCat === undefined ? 'home__cat--active' : ''}`}
          onClick={() => onSelectCat(undefined)}
        >
          <Text>全部</Text>
        </View>
        {cats.map((c) => (
          <View
            key={c.id}
            className={`home__cat ${activeCat === c.id ? 'home__cat--active' : ''}`}
            onClick={() => onSelectCat(c.id)}
          >
            <Text>{c.name}</Text>
          </View>
        ))}
      </ScrollView>

      <View className="home__section-heading home__products-heading">
        <Text className="home__section-title">商品列表</Text>
        <Text className="home__section-hint">按客户价格展示</Text>
      </View>
      <View className="home__list">
        {list.length === 0 && !loading ? (
          <EmptyState text="没有找到商品" />
        ) : (
          list.map((p) => <ProductCard key={p.skuId} product={p} onAdd={onAdd} />)
        )}
        {loading && <Text className="home__loading">加载中...</Text>}
        {!hasMore && list.length > 0 && <Text className="home__loading">没有更多了</Text>}
      </View>
    </View>
  )
}
