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

    <view v-if="activities.length" class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">正在进行的活动</text>
        <text class="home__section-link" @click="goActivities">全部活动</text>
      </view>
      <view v-for="activity in activities.slice(0, 3)" :key="activity.id" class="home__activity"
            @click="goActivity(activity)">
        <image v-if="activity.product?.primaryImageUrl" class="home__activity-image"
               :src="activity.product.primaryImageUrl" mode="aspectFill" />
        <view class="home__activity-main">
          <text class="home__activity-name">{{ activity.activityName }}</text>
          <text class="home__activity-summary">{{ activity.summary }}</text>
          <text class="home__activity-time">至 {{ formatDate(activity.validTo) }}</text>
        </view>
        <text class="home__activity-arrow">›</text>
      </view>
    </view>

    <view class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">常购商品</text>
        <text class="home__section-link" @click="goCategory">全部商品</text>
      </view>
      <view v-if="frequentProducts.length" class="home__list">
        <ProductCard v-for="item in frequentProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
      <text v-else class="home__empty">{{ userStore.isLogin ? '暂无常购商品' : '登录后展示常购商品' }}</text>
    </view>

    <view v-if="newProducts.length" class="home__section">
      <view class="home__section-head"><text class="home__section-title">新品上架</text></view>
      <view class="home__list">
        <ProductCard v-for="item in newProducts" :key="item.skuId" :product="item"
                     @click="goDetail" @add="addToCart" @inquiry="inquiry" />
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
  import { computed, ref } from 'vue';
  import { onReachBottom, onShow } from '@dcloudio/uni-app';
  import ProductCard from '@/components/business/product-card.vue';
  import { mallCatalogApi } from '@/api/mall';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { useUserStore } from '@/store/modules/system/user';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useMallCart } from '@/composables/use-mall-cart';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import { formatDate } from '@/utils/order-display';

  const PAGE_SIZE = 10;
  const { statusBarHeight } = useSystemLayout();
  const userStore = useUserStore();
  const products = ref([]);
  const frequentProducts = ref([]);
  const activities = ref([]);
  const newProducts = ref([]);
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);
  const hasMore = computed(() => products.value.length < total.value);
  const displayName = computed(() => userStore.contactName || userStore.customerName || '客户');
  const greeting = computed(() => new Date().getHours() < 12 ? '上午好' : new Date().getHours() < 18 ? '下午好' : '晚上好');

  async function loadProducts(reset = false) {
    if (loading.value) return;
    if (reset) { products.value = []; pageNum.value = 0; total.value = 0; }
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

  function goLogin() { uni.navigateTo({ url: '/pages/login/login' }); }
  function goSearch() { uni.navigateTo({ url: '/pages-sub/product/search' }); }
  function goCategory() { uni.switchTab({ url: '/pages/category/index' }); }
  function goCoupons() { uni.navigateTo({ url: '/pages-sub/promotion/coupons' }); }
  function goActivities() { uni.navigateTo({ url: '/pages-sub/promotion/activities' }); }
  function goActivity(activity) { activity.product ? goDetail(activity.product) : goActivities(); }
  function goDetail(item) { uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${item.skuId}` }); }
  const { addToCart } = useMallCart();
  function inquiry(item) { uni.showToast({ title: `${item.productName} 暂无客户价，请联系业务员`, icon: 'none' }); }

  onReachBottom(() => { if (hasMore.value) loadProducts(); });
  let showed = false;
  onShow(() => {
    if (showed) {
      loadProducts(true);
      loadFrequent();
      loadMarketing();
    }
    showed = true;
  });
  loadProducts(true);
  loadFrequent();
  loadMarketing();
</script>

<style lang="scss" scoped>
  .home { min-height: 100vh; background: $color-bg-page; padding-bottom: $space-6; }
  .home__header { padding: 0 $space-4 $space-4; background: linear-gradient(180deg, $color-primary, $color-primary-dark); }
  .home__brand { display: block; padding-top: $space-4; font-size: $font-size-xs; color: rgba(255,255,255,.8); }
  .home__customer-row, .home__section-head { @include flex-between; margin-top: $space-2; }
  .home__customer { font-size: $font-size-xl; font-weight: $font-weight-bold; color: white; }
  .home__login { padding: $space-1 $space-3; border: 1px solid rgba(255,255,255,.6); border-radius: $radius-pill; color: white; }
  .home__greeting { display: block; margin-top: $space-1; color: rgba(255,255,255,.85); font-size: $font-size-sm; }
  .home__search { @include flex-start; height: 72rpx; margin-top: $space-3; padding: 0 $space-3; border-radius: $radius-pill; background: white; }
  .home__search-text { margin-left: $space-2; color: $color-text-placeholder; }
  .home__coupon-link { display: block; margin-top: $space-3; color: white; font-size: $font-size-sm; text-align: right; }
  .home__section { margin-top: $space-3; background: white; }
  .home__activity { display: flex; align-items: center; gap: $space-3; min-height: 120rpx; padding: $space-3 $space-4; @include hairline-bottom; }
  .home__activity-image { width: 96rpx; height: 96rpx; flex-shrink: 0; border-radius: $radius-md; }
  .home__activity-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: $space-1; }
  .home__activity-name { color: $color-text-primary; font-weight: $font-weight-bold; @include ellipsis; }
  .home__activity-summary { color: $color-primary; font-size: $font-size-sm; }
  .home__activity-time { color: $color-text-tertiary; font-size: $font-size-xs; }
  .home__activity-arrow { color: $color-text-tertiary; }
  .home__section-head { padding: $space-3 $space-4; }
  .home__section-title { font-size: $font-size-lg; font-weight: $font-weight-bold; color: $color-text-primary; }
  .home__section-link { color: $color-primary; font-size: $font-size-sm; }
  .home__empty, .home__loading { display: block; padding: $space-5; text-align: center; color: $color-text-tertiary; font-size: $font-size-sm; }
</style>
