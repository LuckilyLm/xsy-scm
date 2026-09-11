import {
  AppstoreOutlined,
  BellOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Input, Skeleton, Tag } from 'antd';
import type { CSSProperties } from 'react';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchMallHomePreview } from '../../api/mall';
import type { MallHomeProduct, MallHomeSection, MallPromotion } from '../../types/mall';
import styles from './MallHomePage.module.css';

interface SectionPayload {
  title?: string;
  description?: string;
  imageUrl?: string;
  targetUrl?: string;
  notice?: string;
  products?: MallHomeProduct[];
}

function readPayload(payload: unknown): SectionPayload {
  if (!payload || typeof payload !== 'object') {
    return {};
  }
  const value = payload as Record<string, unknown>;
  return {
    title: typeof value.title === 'string' ? value.title : undefined,
    description: typeof value.description === 'string' ? value.description : undefined,
    imageUrl: typeof value.imageUrl === 'string' ? value.imageUrl : undefined,
    targetUrl: typeof value.targetUrl === 'string' ? value.targetUrl : undefined,
    notice: typeof value.notice === 'string' ? value.notice : undefined,
    products: Array.isArray(value.products) ? value.products as MallHomeProduct[] : undefined,
  };
}

function formatPrice(value: string | null | undefined): string {
  return value ? `¥${value}` : '询价';
}

function SectionTitle({ children, hint, id }: { children: string; hint?: string; id: string }) {
  return (
    <div className={styles.sectionHeading}>
      <h2 className={styles.sectionTitle} id={id}>{children}</h2>
      {hint ? <span className={styles.sectionHint}>{hint}</span> : null}
    </div>
  );
}

function BannerSection({ section }: { section: MallHomeSection }) {
  const payload = readPayload(section.payload);
  const title = section.title || payload.title || '鲜蔬源 · 每日新鲜到家';
  const style = payload.imageUrl ? { backgroundImage: `linear-gradient(90deg, rgba(18, 45, 34, .88), rgba(18, 45, 34, .22)), url(${payload.imageUrl})` } : undefined;
  return (
    <section className={styles.hero} aria-labelledby={`mall-banner-${section.id}`} style={style}>
      <div className={styles.heroContent}>
        <p className={styles.heroEyebrow}>FRESH SUPPLY · SMART PROCUREMENT</p>
        <h1 className={styles.heroTitle} id={`mall-banner-${section.id}`}>{title}</h1>
        <p className={styles.heroDescription}>{payload.description || '精选应季食材与优质商品，透明价格，稳定供应。'}</p>
        {payload.targetUrl ? <Button className={styles.heroAction} href={payload.targetUrl}>了解活动</Button> : null}
      </div>
    </section>
  );
}

function ProductCard({ product }: { product: MallHomeProduct }) {
  return (
    <article className={styles.productCard} aria-label={product.productName}>
      <div className={styles.productImage} aria-hidden="true">
        {product.imageUrl ? <img alt="" loading="lazy" src={product.imageUrl} /> : <ShoppingOutlined />}
      </div>
      <div className={styles.productName}>{product.productName}</div>
      <div className={styles.productMeta} title={`${product.categoryName || ''} · ${product.specName || ''}`}>
        {product.categoryName || '未分类'} · {product.specName || product.saleUnit || '标准规格'}
      </div>
      <div className={styles.productFooter}>
        <div>
          <span className={styles.price}>{formatPrice(product.unitPrice)}</span>
          {product.marketPrice && product.marketPrice !== product.unitPrice ? <span className={styles.marketPrice}>{formatPrice(product.marketPrice)}</span> : null}
        </div>
        {product.priceSource ? <Tag color="green">{product.priceSource === 'AGREEMENT' ? '协议价' : '专属价'}</Tag> : null}
      </div>
    </article>
  );
}

function ActivityCard({ promotion }: { promotion: MallPromotion }) {
  return (
    <article className={styles.activityCard}>
      <div className={styles.activityTopline}><span className={styles.activityName}>{promotion.name}</span>{promotion.effective ? <Tag color="orange">进行中</Tag> : null}</div>
      <p className={styles.activityDescription}>{promotion.description || (promotion.type === 'FLASH_SALE' ? '限时优惠，先到先得' : '精选商品专享活动')}</p>
    </article>
  );
}

export function MallHomePage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const home = useQuery({ queryKey: ['mall-home-preview'], queryFn: fetchMallHomePreview });
  const theme = home.data?.theme;
  const sections = home.data?.sections ?? [];
  const banner = sections.find((section) => section.sectionType === 'BANNER');
  const noticeSection = sections.find((section) => section.sectionType === 'CUSTOM' && readPayload(section.payload).notice);
  const productSections = sections.filter((section) => ['RECOMMEND', 'NEW_ARRIVAL', 'FLASH_SALE'].includes(section.sectionType));
  const products = useMemo(() => productSections.flatMap((section) => readPayload(section.payload).products ?? []).slice(0, 12), [productSections]);
  const topCategories = (home.data?.categories ?? []).filter((category) => category.level === 1).slice(0, 8);
  const cssVariables = theme ? {
    '--mall-primary-color': theme.primaryColor,
    '--mall-accent-color': theme.accentColor,
    '--mall-page-background': theme.pageBackground,
    '--mall-card-radius': `${theme.cardRadius}px`,
    '--mall-card-background': '#fff',
    '--mall-card-shadow': theme.cardStyle === 'FLAT' ? 'none' : '0 8px 24px rgba(31, 35, 41, 0.06)',
    '--mall-border-color': theme.cardStyle === 'SHADOW' ? 'transparent' : '#e5e6eb',
    '--mall-text-color': '#1f2329',
    '--mall-text-secondary': '#4e5969',
  } as CSSProperties : undefined;

  function search() {
    const value = keyword.trim();
    navigate(value ? `/products?keyword=${encodeURIComponent(value)}` : '/products');
  }

  if (home.isPending) {
    return <div className={styles.page} aria-busy="true" aria-label="商城首页加载中"><Skeleton active paragraph={{ rows: 12 }} /></div>;
  }

  if (home.isError) {
    return <div className={styles.page} role="alert"><Alert action={<Button icon={<ReloadOutlined />} onClick={() => void home.refetch()}>重新加载</Button>} description="请检查网络连接后重试。" message="商城首页加载失败" showIcon type="error" /></div>;
  }

  const hasContent = Boolean(banner || topCategories.length || home.data.promotions.length || products.length || noticeSection || sections.length);
  if (!hasContent) {
    return <div className={styles.page}><Empty description="商城首页暂无内容" /></div>;
  }

  return (
    <div className={styles.page} style={cssVariables}>
      <div className={styles.previewBar} role="note">
        <span>后台预览模式 · 与小程序共用同一套首页与主题配置</span>
        <a href="/shop/theme">去配置主题</a>
        <a href="/shop/home-sections">去装修首页</a>
      </div>

      <section className={styles.searchPanel} aria-label="商城搜索">
        <div><p className={styles.kicker}>鲜蔬源商城</p><h1 className={styles.pageTitle}>今天，采购新鲜好货</h1></div>
        <Input.Search allowClear enterButton="搜索" onChange={(event) => setKeyword(event.target.value)} onSearch={search} placeholder="搜索商品名称、SKU 或条码" prefix={<SearchOutlined />} size="large" value={keyword} />
      </section>

      {banner ? <BannerSection section={banner} /> : null}

      {noticeSection ? <section className={styles.notice} aria-label="商城公告"><BellOutlined /><strong>商城公告</strong><span>{readPayload(noticeSection.payload).notice}</span></section> : null}

      {topCategories.length ? <section className={styles.section} aria-labelledby="mall-categories-title"><SectionTitle hint="按分类快速查找" id="mall-categories-title">商品分类</SectionTitle><div className={styles.categoryGrid}>{topCategories.map((category) => <button className={styles.categoryCard} key={category.id} onClick={() => navigate(`/products?categoryId=${category.id}`)} type="button"><AppstoreOutlined aria-hidden="true" /><span className={styles.categoryName}>{category.name}</span><span className={styles.categoryCount}>{category.productCount} 件商品</span></button>)}</div></section> : null}

      {home.data.promotions.length ? <section className={styles.section} aria-labelledby="mall-activities-title"><SectionTitle hint="当前可参与的优惠" id="mall-activities-title">限时活动</SectionTitle><div className={styles.activityGrid}>{home.data.promotions.slice(0, 4).map((promotion) => <ActivityCard key={promotion.id} promotion={promotion} />)}</div></section> : null}

      {products.length ? <section className={styles.section} aria-labelledby="mall-products-title"><SectionTitle hint="按客户价格展示" id="mall-products-title">推荐商品</SectionTitle><div className={styles.productGrid}>{products.map((product) => <ProductCard key={product.skuId} product={product} />)}</div></section> : null}
      {!products.length && productSections.length ? <section className={styles.section}><Empty className={styles.empty} description="推荐商品暂未配置" /></section> : null}
    </div>
  );
}
