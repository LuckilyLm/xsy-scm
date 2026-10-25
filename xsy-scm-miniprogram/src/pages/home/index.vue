<template>
  <view class="home" :style="themeStyle">
    <!-- 顶部只有定位：高度与右上角胶囊一致，垂直居中对齐 -->
    <view class="home__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="home__location" :style="locationStyle" @click="goAddress">
        <uni-icons type="location" :size="16" color="#ffffff" />
        <text class="home__location-text">{{ locationText }}</text>
        <uni-icons type="bottom" :size="12" color="rgba(255,255,255,0.8)" />
      </view>
    </view>

    <!-- 搜索框：公告之下，白底绿描边胶囊 -->
    <view class="home__search-wrap">
      <view class="home__search" hover-class="xsy-press--active" :hover-stay-time="80" @click="goSearch">
        <uni-icons type="search" :size="18" :color="theme.primary" />
        <text class="home__search-text">搜索商品名称或规格</text>
      </view>
    </view>

    <!-- 分类横滑：取三级分类（蔬菜、水果、猪肉…），最后一个固定为领券中心 -->
    <view v-if="categoryRail.length" class="home__rail">
      <scroll-view class="home__rail-scroll" scroll-x :show-scrollbar="false">
        <view class="home__rail-inner">
          <view
            v-for="item in categoryRail"
            :key="item.categoryId"
            class="home__rail-item"
            hover-class="xsy-press--active"
            :hover-stay-time="80"
            @click="goCategory(item.categoryId)"
          >
            <view class="home__rail-icon">
              <text class="home__rail-emoji" aria-hidden="true">{{ categoryEmoji(item.categoryName) }}</text>
            </view>
            <text class="home__rail-text">{{ item.categoryName }}</text>
          </view>
          <view v-if="userStore.isLogin" class="home__rail-item" hover-class="xsy-press--active" :hover-stay-time="80" @click="goCoupons">
            <view class="home__rail-icon">
              <uni-icons type="gift" :size="20" :color="theme.primary" />
            </view>
            <text class="home__rail-text">领券中心</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <template v-for="section in contentSections" :key="section.key">
      <view v-if="section.type === 'BANNER_GROUP'" class="home__banner-wrap">
        <swiper class="home__banner" :indicator-dots="section.items.length > 1" :autoplay="section.items.length > 1" :interval="4500" circular>
          <swiper-item v-for="item in section.items" :key="item.key" class="home__banner-slide" @click="goHomeLink(item)">
            <image class="home__banner-image" :src="item.imageUrl" :alt="item.title || '商城活动'" mode="aspectFill" />
            <view v-if="item.title || item.subtitle" class="home__banner-caption">
              <text v-if="item.title" class="home__banner-title">{{ item.title }}</text>
              <text v-if="item.subtitle" class="home__banner-subtitle">{{ item.subtitle }}</text>
            </view>
          </swiper-item>
        </swiper>
      </view>
      <view v-else-if="section.type === 'ACTIVITY_TOPIC'" class="home__topic" @click="goHomeLink(section)">
        <image v-if="section.imageUrl" :src="section.imageUrl" class="home__topic-image" mode="aspectFill" />
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
          <ProductCard v-for="item in section.products" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
        </view>
      </view>
    </template>

    <view v-if="!hasPublishedLayout && activities.length" class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">正在进行的活动</text>
        <text class="home__section-link" @click="goActivities">全部活动</text>
      </view>
      <view v-for="activity in activities.slice(0, 3)" :key="activity.id" class="home__activity" @click="goActivity(activity)">
        <image
          v-if="activity.product?.primaryImageUrl"
          class="home__activity-image"
          :src="activity.product.primaryImageUrl"
          mode="aspectFill"
          lazy-load
        />
        <view class="home__activity-main">
          <text class="home__activity-name">{{ activity.activityName }}</text>
          <text class="home__activity-summary">{{ activity.summary }}</text>
          <text class="home__activity-time">至 {{ formatDate(activity.validTo) }}</text>
        </view>
        <uni-icons type="right" :size="16" :color="COLOR_TEXT_TERTIARY" />
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
      <XsyEmpty v-else :title="userStore.isLogin ? '暂无常购商品' : '登录后展示常购商品'" compact />
    </view>

    <view v-if="!hasPublishedLayout && newProducts.length" class="home__section">
      <view class="home__section-head"><text class="home__section-title">新品上架</text></view>
      <view class="home__list">
        <ProductCard v-for="item in newProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
    </view>

    <view class="home__section">
      <view class="home__section-head"><text class="home__section-title">商品目录</text></view>
      <view v-if="loading && !products.length">
        <XsySkeleton variant="list" :rows="4" />
      </view>
      <view v-else-if="products.length" class="home__list">
        <ProductCard v-for="item in products" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
        <uni-load-more :status="loadMoreStatus" :content-text="LOAD_MORE_TEXT" />
      </view>
      <XsyEmpty v-else icon="shop" title="暂无可见商品" desc="商品上架后会显示在这里" compact />
    </view>
    <XsyTabBar :active="0" />
    <MallHomePopup :visible="popupVisible" :items="popup?.items || []" @close="closePopup" @select="onPopupSelect" />
  </view>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { onHide, onReachBottom, onShow } from '@dcloudio/uni-app';
  import ProductCard from '@/components/business/product-card.vue';
  import MallHomePopup from '@/components/business/mall-home-popup.vue';
  import XsySkeleton from '@/components/common/xsy-skeleton.vue';
  import XsyEmpty from '@/components/common/xsy-empty.vue';
  import XsyTabBar from '@/components/common/xsy-tab-bar.vue';
  import { LOAD_MORE_TEXT } from '@/constants/load-more-const';
  import { mallCatalogApi } from '@/api/mall';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { mallHomeApi } from '@/api/mall/home-api';
  import { useMallNavigationStore } from '@/store/modules/mall/navigation';
  import { useUserStore } from '@/store/modules/system/user';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { useMallCart } from '@/composables/use-mall-cart';
  import { smartSentry } from '@/lib/smart-sentry';
  import { formatDate } from '@/utils/order-display';
  import { categoryEmoji } from '@/utils/category-icon';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import { HOME_POPUP_SEEN } from '@/constants/local-storage-key-const';

  const { theme, themeStyle } = useMallTheme();

  /*
   * 主题来自全局状态（store/modules/mall/theme.js）：根节点绑定 CSS 变量，
   * 首页内容请求不再修改全局主题，避免较慢的首页响应覆盖新主题。
   */

  const PAGE_SIZE = 10;
  const { statusBarHeight, menuButtonRect } = useSystemLayout();
  /** 定位行与右上角胶囊同高、并让开胶囊宽度，视觉上与胶囊同一水平线 */
  const locationStyle = computed(() => {
    const rect = menuButtonRect.value;
    if (!rect || !rect.left) {
      return { height: '32px' };
    }
    let windowWidth = 0;
    try {
      const win = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync();
      windowWidth = win.windowWidth || 0;
    } catch {
      windowWidth = 0;
    }
    return {
      height: (rect.height || 32) + 'px',
      paddingRight: Math.max(0, windowWidth - rect.left + 8) + 'px',
    };
  });
  /** 顶部定位：取默认收货地址的市区，没有地址时提示去选 */
  const locationText = ref('选择收货地址');
  async function loadLocation() {
    try {
      const response = await mallAddressApi.getList();
      const list = response.data || [];
      const chosen = list.find((item) => item.isDefault) || list[0];
      if (!chosen) {
        return;
      }
      locationText.value = [chosen.cityName, chosen.districtName].filter(Boolean).join('') || chosen.detailAddress || '选择收货地址';
    } catch (error) {
      smartSentry.captureError(error);
    }
  }
  function goAddress() {
    uni.navigateTo({ url: '/pages-sub/address/list' });
  }
  const userStore = useUserStore();
  const navigationStore = useMallNavigationStore();
  const products = ref([]);
  const frequentProducts = ref([]);
  const activities = ref([]);
  const newProducts = ref([]);
  const homeLayout = ref(null);
  let homeRequest = 0;
  const popup = ref(null);
  const popupVisible = ref(false);
  /** EVERY_ENTRY：本次进入首页是否已经展示过；onHide 后重置 */
  const popupShownThisEntry = ref(false);
  let popupRequest = 0;
  const hasPublishedLayout = computed(() => homeLayout.value?.releaseId != null);
  const configuredSections = computed(() => {
    if (!hasPublishedLayout.value) return [];
    const result = [];
    for (const block of homeLayout.value.blocks || []) {
      const groupType = block.type === 'BANNER' ? 'BANNER_GROUP' : block.type === 'SHORTCUT' ? 'SHORTCUT_GROUP' : null;
      const previous = result[result.length - 1];
      if (groupType && previous?.type === groupType) previous.items.push(block);
      else if (groupType) result.push({ type: groupType, key: block.key, items: [block] });
      else result.push(block);
    }
    return result;
  });
  const contentSections = computed(() =>
    configuredSections.value.filter((section) => section.type !== 'ANNOUNCEMENT' && section.type !== 'SHORTCUT_GROUP')
  );
  /** 分类横滑：服务端三级分类（蔬菜 / 水果 / 猪肉 …），比固定的快捷入口更贴近采购习惯 */
  const categoryRail = ref([]);
  async function loadCategoryRail() {
    try {
      const response = await mallCatalogApi.getCategories();
      const result = [];
      /*
       * 取最末级分类作为横滑入口：三级目录直接用三级，
       * 只有两级时（部分客户只维护到二级）退到二级，避免整条横滑消失。
       */
      const walk = (nodes) => {
        (nodes || []).forEach((node) => {
          if (node.level === 3 || !(node.children || []).length) {
            result.push(node);
          } else {
            walk(node.children);
          }
        });
      };
      walk(response.data || []);
      categoryRail.value = result.slice(0, 14);
    } catch (error) {
      smartSentry.captureError(error);
    }
  }
  const pageNum = ref(0);
  const total = ref(0);
  const loading = ref(false);
  const hasMore = computed(() => products.value.length < total.value);
  /** uni-load-more 的状态只有三种：more / loading / noMore */
  const loadMoreStatus = computed(() => {
    if (loading.value) return 'loading';
    return hasMore.value ? 'more' : 'noMore';
  });
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
    if (!userStore.isLogin) {
      homeLayout.value = null;
      return;
    }
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

  function todayKey() {
    const date = new Date();
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  /** DAILY 规则：同一天、同一弹窗版本只展示一次；版本更新后当天可再次展示。 */
  function popupSeenToday(version) {
    try {
      const seen = uni.getStorageSync(HOME_POPUP_SEEN);
      return Boolean(seen) && Number(seen.version) === Number(version) && seen.date === todayKey();
    } catch {
      return false;
    }
  }

  function markPopupSeen(version) {
    try {
      uni.setStorageSync(HOME_POPUP_SEEN, { version, date: todayKey() });
    } catch {
      // 存储失败只是频控失效，不影响弹窗展示
    }
  }

  async function loadHomePopup() {
    if (!userStore.isLogin || popupVisible.value) return;
    const request = ++popupRequest;
    try {
      const data = (await mallHomeApi.popup()).data;
      if (request !== popupRequest || !userStore.isLogin || popupVisible.value) return;
      if (!data.active || !data.items.length) return;
      if (data.displayRule === 'DAILY') {
        if (popupSeenToday(data.version)) return;
        markPopupSeen(data.version);
      } else if (popupShownThisEntry.value) {
        return;
      }
      popupShownThisEntry.value = true;
      popup.value = data;
      popupVisible.value = true;
    } catch (error) {
      smartSentry.captureError(error);
    }
  }

  function closePopup() {
    popupVisible.value = false;
  }

  function onPopupSelect(item) {
    popupVisible.value = false;
    goHomeLink(item);
  }

  watch(
    () => userStore.customerId,
    () => {
      homeRequest += 1;
      popupRequest += 1;
      homeLayout.value = null;
      popupVisible.value = false;
      if (userStore.isLogin) {
        loadHomeLayout();
        loadHomePopup();
      }
    }
  );

  function goHomeLink(block) {
    const target = block.linkTarget;
    switch (block.linkType) {
      case 'PRODUCT':
        if (/^\d+$/.test(String(target))) goDetail({ skuId: target });
        break;
      case 'CATEGORY':
        goCategory(target);
        break;
      case 'ACTIVITY':
        goActivities();
        break;
      case 'COUPONS':
        goCoupons();
        break;
      case 'SEARCH':
        goSearch();
        break;
      case 'EXTERNAL_URL':
        if (typeof target === 'string' && /^https:\/\//i.test(target)) {
          uni.navigateTo({ url: `/pages-sub/mall/web?url=${encodeURIComponent(target)}` });
        }
        break;
      default:
        break;
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
      loadHomePopup();
    }
    showed = true;
  });
  onHide(() => {
    // 页面不可见即结束「本次进入」；再次进入时 EVERY_ENTRY 可重新展示
    popupShownThisEntry.value = false;
    popupVisible.value = false;
  });
  loadProducts(true);
  loadHomeLayout();
  loadHomePopup();
  loadCategoryRail();
  loadLocation();
</script>

<style lang="scss" scoped>
  .home {
    @include viewport-min-height;
    background: $color-bg-page;
    /* 让开自绘底部导航 */
    padding-bottom: calc(var(--xsy-tabbar-h, 100rpx) + #{$space-6});
  }
  .home__header {
    padding: 0 var(--xsy-page-x) $space-3;
    background: linear-gradient(180deg, var(--mall-primary, #16a34a), var(--mall-primary-dark, #12833c));
  }
  .home__section-head {
    @include flex-between;
  }
  /* 顶部定位：与胶囊同高，点击去地址列表 */
  .home__location {
    display: flex;
    align-items: center;
    gap: $space-1;
  }
  .home__location-text {
    max-width: 72%;
    color: white;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  /* 搜索框与分类横滑同处一张白卡，避免出现「白框浮在灰底上」的碎块 */
  .home__search-wrap {
    padding: $space-3 var(--xsy-page-x) 0;
    background: white;
  }
  .home__search {
    @include pressable;
    @include flex-start;
    height: 76rpx;
    padding: 0 $space-3;
    border: 1px solid var(--mall-primary, #16a34a);
    border-radius: $radius-pill;
    background: white;
  }
  .home__search-text {
    margin-left: $space-2;
    color: $color-text-placeholder;
    font-size: $font-size-sm;
  }
  /* 分类横滑：圆形首字图标 + 名称，横向滚动 */
  .home__rail {
    padding: $space-3 0;
    background: white;
  }
  .home__rail-scroll {
    white-space: nowrap;
  }
  .home__rail-inner {
    display: flex;
    align-items: flex-start;
    gap: $space-4;
    padding: 0 var(--xsy-page-x);
  }
  .home__rail-item {
    @include pressable;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-1;
    flex-shrink: 0;
    width: 96rpx;
  }
  .home__rail-icon {
    width: 88rpx;
    height: 88rpx;
    border-radius: $radius-pill;
    background: var(--mall-primary-light, #e8f5ec);
    @include flex-center;
  }
  .home__rail-emoji {
    font-size: 40rpx;
    line-height: 1;
  }
  .home__rail-text {
    max-width: 96rpx;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .home__section {
    margin-top: $space-3;
    background: white;
  }
  .home__banner-wrap {
    padding: $space-3 var(--xsy-page-x) 0;
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
  .home__banner-slide {
    position: relative;
  }
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
  .home__banner-title {
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .home__banner-subtitle {
    font-size: $font-size-sm;
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
    padding: $space-3 var(--xsy-page-x);
  }
  .home__topic-subtitle {
    color: $color-text-tertiary;
    font-size: $font-size-sm;
  }
  .home__activity {
    @include pressable;
    display: flex;
    align-items: center;
    gap: $space-3;
    min-height: 120rpx;
    padding: $space-3 var(--xsy-page-x);
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
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
  .home__activity-time {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .home__section-head {
    padding: $space-3 var(--xsy-page-x);
  }
  .home__section-title {
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .home__section-link {
    @include pressable;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
</style>
