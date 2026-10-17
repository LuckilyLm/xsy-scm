<template>
  <view class="home">
    <view class="home__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="home__brand">鲜蔬源商城 · 客户采购</text>
      <view class="home__customer-row">
        <text class="home__customer">{{ userStore.customerName || '欢迎光临' }}</text>
        <text v-if="!userStore.isLogin" class="home__login" @click="goLogin">登录</text>
      </view>
      <text class="home__greeting">{{ userStore.isLogin ? `${greeting}，${displayName}` : '登录后查看客户专属商品与价格' }}</text>
      <view class="home__search" @click="goSearch">
        <uni-icons type="search" :size="20" :color="COLOR_TEXT_TERTIARY" />
        <text class="home__search-text">搜索商品名称或规格</text>
      </view>
      <text v-if="userStore.isLogin" class="home__coupon-link" @click="goCoupons">领券中心 ›</text>
    </view>

    <template v-for="section in configuredSections" :key="section.key">
      <view v-if="section.type === 'BANNER_GROUP'" class="home__banner-wrap">
        <swiper class="home__banner" :indicator-dots="section.items.length > 1" :autoplay="section.items.length > 1"
                :interval="4500" circular>
          <swiper-item v-for="item in section.items" :key="item.key" class="home__banner-slide" @click="goHomeLink(item)">
            <image class="home__banner-image" :src="item.imageUrl" :alt="item.title || '商城活动'" mode="aspectFill"/>
            <view v-if="item.title || item.subtitle" class="home__banner-caption">
              <text v-if="item.title" class="home__banner-title">{{ item.title }}</text>
              <text v-if="item.subtitle" class="home__banner-subtitle">{{ item.subtitle }}</text>
            </view>
          </swiper-item>
        </swiper>
      </view>
      <view v-else-if="section.type === 'SHORTCUT_GROUP'" class="home__shortcuts">
        <view v-for="item in section.items" :key="item.key" class="home__shortcut" @click="goHomeLink(item)">
          <image v-if="item.imageUrl" :src="item.imageUrl" class="home__shortcut-image" mode="aspectFill"/>
          <text>{{ item.title }}</text>
        </view>
      </view>
      <view v-else-if="section.type === 'ANNOUNCEMENT'" class="home__announcement" @click="goHomeLink(section)">
        <text class="home__announcement-title">{{ section.title || '商城公告' }}</text>
        <text class="home__announcement-text">{{ section.text }}</text>
      </view>
      <view v-else-if="section.type === 'ACTIVITY_TOPIC'" class="home__topic" @click="goHomeLink(section)">
        <image v-if="section.imageUrl" :src="section.imageUrl" class="home__topic-image" mode="aspectFill"/>
        <view class="home__topic-copy">
          <text class="home__section-title">{{ section.title }}</text>
          <text v-if="section.subtitle" class="home__topic-subtitle">{{ section.subtitle }}</text>
        </view>
      </view>
      <view v-else-if="section.type === 'RECOMMENDATION'" class="home__section">
        <view class="home__section-head">
          <text class="home__section-title">{{ section.title }}</text>
          <text class="home__section-link" @click="goCategory">全部商品</text>
        </view>
        <view class="home__list">
          <ProductCard v-for="item in section.products" :key="item.skuId" :product="item"
                       @click="goDetail" @add="addToCart" @inquiry="inquiry"/>
        </view>
      </view>
    </template>

    <view v-if="!hasPublishedLayout && activities.length" class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">正在进行的活动</text>
        <text class="home__section-link" @click="goActivities">全部活动</text>
      </view>
      <view v-for="activity in activities.slice(0, 3)" :key="activity.id" class="home__activity" @click="goActivity(activity)">
        <image v-if="activity.product?.primaryImageUrl" class="home__activity-image" :src="activity.product.primaryImageUrl" mode="aspectFill" />
        <view class="home__activity-main">
          <text class="home__activity-name">{{ activity.activityName }}</text>
          <text class="home__activity-summary">{{ activity.summary }}</text>
          <text class="home__activity-time">至 {{ formatDate(activity.validTo) }}</text>
        </view>
        <text class="home__activity-arrow">›</text>
      </view>
    </view>

    <view v-if="!hasPublishedLayout" class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">常购商品</text>
        <text class="home__section-link" @click="goCategory">全部商品</text>
      </view>
      <view v-if="frequentProducts.length" class="home__list">
        <ProductCard v-for="item in frequentProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
      <text v-else class="home__empty">{{ userStore.isLogin ? '暂无常购商品' : '登录后展示常购商品' }}</text>
    </view>

    <view v-if="!hasPublishedLayout && newProducts.length" class="home__section">
      <view class="home__section-head"><text class="home__section-title">新品上架</text></view>
      <view class="home__list">
        <ProductCard v-for="item in newProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
    </view>

    <view class="home__section">
      <view class="home__section-head"><text class="home__section-title">商品目录</text></view>
      <view v-if="products.length" class="home__list">
        <ProductCard v-for="item in products" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
      <text v-else-if="!loading" class="home__empty">暂无可见商品</text>
      <text class="home__loading">{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : products.length ? '没有更多了' : '' }}</text>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onReachBottom, onShow } from '@dcloudio/uni-app';
  import ProductCard from '@/components/business/product-card.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { mallHomeApi } from '@/api/mall/home-api';
  import { useMallNavigationStore } from '@/store/modules/mall/navigation';
  import { useUserStore } from '@/store/modules/system/user';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useMallCart } from '@/composables/use-mall-cart';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import { formatDate } from '@/utils/order-display';

  const PAGE_SIZE = 10;
  const { statusBarHeight } = useSystemLayout();
  const userStore = useUserStore();
  const navigationStore = useMallNavigationStore();
  const products = ref([]);
  const frequentProducts = ref([]);
  const activities = ref([]);
  const newProducts = ref([]);
  const homeLayout = ref(null);
  let homeRequest = 0;
  const hasPublishedLayout = computed(() => homeLayout.value?.releaseId != null);
  const configuredSections = computed(() => {
    if (!hasPublishedLayout.value) return [];
    const result = [];
    for (const block of homeLayout.value.blocks || []) {
      const groupType = block.type === 'BANNER' ? 'BANNER_GROUP' : block.type === 'SHORTCUT' ? 'SHORTCUT_GROUP' : null;
      const previous = result[result.length - 1];
      if (groupType && previous?.type === groupType) previous.items.push(block);
      else if (groupType) result.push({type: groupType, key: block.key, items: [block]});
      else result.push(block);
    }
    return result;
  });
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);
  const hasMore = computed(() => products.value.length < total.value);
  const displayName = computed(() => userStore.contactName || userStore.customerName || '客户');
  const greeting = computed(() => (new Date().getHours() < 12 ? '上午好' : new Date().getHours() < 18 ? '下午好' : '晚上好'));

  async function loadProducts(reset = false) {
    if (loading.value) return;
    if (reset) {
      products.value = [];
      pageNum.value = 0;
      total.value = 0;
    }
    loading.value = true;
    try {
      const response = await mallCatalogApi.getProducts({ pageNum: pageNum.value + 1, pageSize: PAGE_SIZE });
      const page = response.data || {};
      products.value = pageNum.value ? products.value.concat(page.list || []) : page.list || [];
      total.value = page.total || 0;
      pageNum.value += 1;
    } catch (error) {
      smartSentry.captureError(error);
    } finally {
      loading.value = false;
    }
  }

  async function loadFrequent() {
    if (!userStore.isLogin) return;
    try {
      const response = await mallCatalogApi.getFavorites({ days: 90, limit: 5 });
      // 后端 /scm/mall/catalog/frequent 返回商品数组，不是分页包装
      frequentProducts.value = response.data || [];
    } catch (error) {
      smartSentry.captureError(error);
    }
  }

  async function loadMarketing() {
    if (!userStore.isLogin) {
      activities.value = [];
      newProducts.value = [];
      return;
    }
    try {
      activities.value = (await mallPromotionApi.activities()).data || [];
      newProducts.value = (await mallPromotionApi.newProducts()).data || [];
    } catch (error) {
      smartSentry.captureError(error);
    }
  }

  async function loadHomeLayout() {
    const request = ++homeRequest;
    const customerId = userStore.customerId;
    if (!userStore.isLogin) { homeLayout.value = null; return; }
    try {
      const page = (await mallHomeApi.published()).data;
      if (request !== homeRequest || !userStore.isLogin || userStore.customerId !== customerId) return;
      homeLayout.value = page;
    } catch (error) {
      if (request !== homeRequest || userStore.customerId !== customerId) return;
      homeLayout.value = null;
      smartSentry.captureError(error);
    }
    if (!hasPublishedLayout.value) {
      await Promise.all([loadFrequent(), loadMarketing()]);
    }
  }

  watch(() => userStore.customerId, () => {
    homeRequest += 1;
    homeLayout.value = null;
    if (userStore.isLogin) loadHomeLayout();
  });

  function goHomeLink(block) {
    const target = block.linkTarget;
    switch (block.linkType) {
      case 'PRODUCT':
        if (/^\d+$/.test(String(target))) goDetail({skuId: target});
        break;
      case 'CATEGORY': goCategory(target); break;
      case 'ACTIVITY': goActivities(); break;
      case 'COUPONS': goCoupons(); break;
      case 'SEARCH': goSearch(); break;
      case 'EXTERNAL_URL':
        if (typeof target === 'string' && /^https:\/\//i.test(target)) {
          uni.navigateTo({url: `/pages-sub/mall/web?url=${encodeURIComponent(target)}`});
        }
        break;
      default: break;
    }
  }

  function goLogin() {
    uni.navigateTo({ url: '/pages/login/login' });
  }
  function goSearch() {
    uni.navigateTo({ url: '/pages-sub/product/search' });
  }
  function goCategory(categoryId) {
    if (categoryId && /^\d+$/.test(String(categoryId))) {
      navigationStore.setPendingCategoryId(categoryId);
    }
    uni.switchTab({ url: '/pages/category/index' });
  }
  function goCoupons() {
    uni.navigateTo({ url: '/pages-sub/promotion/coupons' });
  }
  function goActivities() {
    uni.navigateTo({ url: '/pages-sub/promotion/activities' });
  }
  function goActivity(activity) {
    activity.product ? goDetail(activity.product) : goActivities();
  }
  function goDetail(item) {
    uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${item.skuId}` });
  }
  const { addToCart } = useMallCart();
  function inquiry(item) {
    uni.showToast({ title: `${item.productName} 暂无客户价，请联系业务员`, icon: 'none' });
  }

  onReachBottom(() => {
    if (hasMore.value) loadProducts();
  });
  let showed = false;
  onShow(() => {
    if (showed) {
      loadProducts(true);
      loadHomeLayout();
    }
    showed = true;
  });
  loadProducts(true);
  loadHomeLayout();
</script>

<style lang="scss" scoped>
  .home {
    min-height: 100vh;
    background: $color-bg-page;
    padding-bottom: $space-6;
  }
  .home__header {
    padding: 0 $space-4 $space-4;
    background: linear-gradient(180deg, $color-primary, $color-primary-dark);
  }
  .home__brand {
    display: block;
    padding-top: $space-4;
    font-size: $font-size-xs;
    color: rgba(255, 255, 255, 0.8);
  }
  .home__customer-row,
  .home__section-head {
    @include flex-between;
    margin-top: $space-2;
  }
  .home__customer {
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: white;
  }
  .home__login {
    padding: $space-1 $space-3;
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: $radius-pill;
    color: white;
  }
  .home__greeting {
    display: block;
    margin-top: $space-1;
    color: rgba(255, 255, 255, 0.85);
    font-size: $font-size-sm;
  }
  .home__search {
    @include flex-start;
    height: 72rpx;
    margin-top: $space-3;
    padding: 0 $space-3;
    border-radius: $radius-pill;
    background: white;
  }
  .home__search-text {
    margin-left: $space-2;
    color: $color-text-placeholder;
  }
  .home__coupon-link {
    display: block;
    margin-top: $space-3;
    color: white;
    font-size: $font-size-sm;
    text-align: right;
  }
  .home__section {
    margin-top: $space-3;
    background: white;
  }
  .home__banner-wrap {
    padding: $space-3 $space-4 0;
  }
  .home__banner {
    height: 280rpx;
    border-radius: $radius-md;
    overflow: hidden;
  }
  .home__banner-image {
    display: block;
    width: 100%;
    height: 280rpx;
  }
  .home__banner-slide {position: relative}
  .home__banner-caption {
    position: absolute;
    right: 0;
    bottom: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    gap: $space-1;
    padding: $space-2 $space-3;
    background: rgba(0, 0, 0, 0.62);
    color: white;
  }
  .home__banner-title {font-size: $font-size-base; font-weight: $font-weight-bold}
  .home__banner-subtitle {font-size: $font-size-sm}
  .home__shortcuts {
    display: flex;
    flex-wrap: wrap;
    padding: $space-3 $space-2;
    margin-top: $space-3;
    background: white;
  }
  .home__shortcut {
    width: 25%;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-2;
    padding: $space-2;
    color: $color-text-primary;
    font-size: $font-size-sm;
    text-align: center;
  }
  .home__shortcut-image {
    width: 80rpx;
    height: 80rpx;
    border-radius: $radius-md;
  }
  .home__announcement {
    display: flex;
    gap: $space-2;
    padding: $space-3 $space-4;
    margin-top: $space-3;
    background: white;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .home__announcement-title {
    flex-shrink: 0;
    color: $color-primary;
    font-weight: $font-weight-bold;
  }
  .home__announcement-text {
    flex: 1;
    min-width: 0;
  }
  .home__topic {
    margin-top: $space-3;
    background: white;
  }
  .home__topic-image {
    display: block;
    width: 100%;
    height: 240rpx;
  }
  .home__topic-copy {
    display: flex;
    flex-direction: column;
    gap: $space-1;
    padding: $space-3 $space-4;
  }
  .home__topic-subtitle {
    color: $color-text-tertiary;
    font-size: $font-size-sm;
  }
  .home__activity {
    display: flex;
    align-items: center;
    gap: $space-3;
    min-height: 120rpx;
    padding: $space-3 $space-4;
    @include hairline-bottom;
  }
  .home__activity-image {
    width: 96rpx;
    height: 96rpx;
    flex-shrink: 0;
    border-radius: $radius-md;
  }
  .home__activity-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: $space-1;
  }
  .home__activity-name {
    color: $color-text-primary;
    font-weight: $font-weight-bold;
    @include ellipsis;
  }
  .home__activity-summary {
    color: $color-primary;
    font-size: $font-size-sm;
  }
  .home__activity-time {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .home__activity-arrow {
    color: $color-text-tertiary;
  }
  .home__section-head {
    padding: $space-3 $space-4;
  }
  .home__section-title {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .home__section-link {
    color: $color-primary;
    font-size: $font-size-sm;
  }
  .home__empty,
  .home__loading {
    display: block;
    padding: $space-5;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-sm;
  }
</style>
