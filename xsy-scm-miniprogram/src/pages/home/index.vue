<template>
  <view class="home" :style="themeStyle">
    <view v-if="headerScrolled" class="home__reading-nav" :style="{ paddingTop: statusBarHeight + 'px', height: navBarHeight + 'px' }">
      <text>{{ storeName }}</text>
    </view>
    <!-- 定位行按动态导航高度与胶囊居中，搜索框共用品牌背景。 -->
    <view class="home__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="home__location" :style="locationStyle" @click="goAddress">
        <uni-icons type="location" :size="16" color="#ffffff" />
        <text class="home__location-text">{{ locationText }}</text>
        <uni-icons type="bottom" :size="12" color="rgba(255,255,255,0.8)" />
      </view>
      <view class="home__search-wrap">
        <view class="home__search" hover-class="xsy-press--active" :hover-stay-time="80" aria-label="搜索商品名称或规格" @click="goSearch">
          <uni-icons type="search" :size="18" :color="MALL_HOME_COLORS.searchIcon" />
          <text class="home__search-text">搜索商品名称或规格</text>
        </view>
      </view>
    </view>

    <template v-for="section in contentSections" :key="section.key">
      <view v-if="section.type === 'BANNER_GROUP'" class="home__banner-wrap" :class="{ 'home__banner-wrap--topic': section.key !== leadBannerKey }">
        <swiper
          class="home__banner"
          :indicator-dots="section.items.length > 1"
          :autoplay="section.items.length > 1 && bannerAutoplay"
          :interval="4500"
          :circular="section.items.length > 1"
        >
          <swiper-item v-for="item in section.items" :key="item.key" class="home__banner-slide" @click="goHomeLink(item)">
            <XsyRemoteImage class="home__banner-image" :src="item.imageUrl" :alt="item.title || '商城活动'" mode="aspectFit" />
          </swiper-item>
        </swiper>
      </view>
      <!-- 分类横滑：跟在轮播之后，与公告条交换位置；没有分类时整行不出现 -->
      <view v-else-if="section.type === 'RAIL'" class="home__rail">
        <scroll-view class="home__rail-scroll" scroll-x :show-scrollbar="false">
          <view class="home__rail-inner">
            <view
              v-for="item in categoryRail"
              :key="item.categoryId"
              class="home__rail-item"
              :data-category-id="item.categoryId"
              hover-class="xsy-press--active"
              :hover-stay-time="80"
              @click="goCategory(item.categoryId)"
            >
              <view class="home__rail-icon">
                <XsyRemoteImage
                  v-if="categoryIcon(item.categoryName)"
                  class="home__rail-glyph"
                  :src="categoryIcon(item.categoryName)"
                  mode="aspectFit"
                  aria-hidden="true"
                />
                <uni-icons v-else type="shop" :size="28" :color="theme.primary" />
              </view>
              <text class="home__rail-text">{{ item.categoryName }}</text>
            </view>
          </view>
        </scroll-view>
      </view>
      <view v-else-if="section.type === 'SERVICES'">
        <view class="home__services" :class="{ 'home__services--standalone': !categoryRail.length }">
          <view
            v-for="shortcut in serviceShortcuts"
            :key="shortcut.key"
            class="home__service"
            :aria-label="shortcut.label"
            :data-shortcut="shortcut.key"
            hover-class="xsy-press--active"
            @click="goService(shortcut.key)"
          >
            <XsyRemoteImage class="home__service-image" :src="MALL_SHORTCUT_IMAGES[shortcut.key]" mode="aspectFit" aria-hidden="true" />
            <text class="home__shortcut-text">{{ shortcut.label }}</text>
          </view>
        </view>
      </view>
      <view
        v-else-if="section.type === 'ANNOUNCEMENT'"
        class="home__notice"
        :class="{ 'home__notice--link': hasLink(section) }"
        @click="hasLink(section) && goHomeLink(section)"
      >
        <text class="home__notice-tag">公告</text>
        <text class="home__notice-text">{{ section.text }}</text>
        <!-- 只有配置了跳转才显示箭头，避免出现点了没反应的假入口 -->
        <uni-icons v-if="hasLink(section)" type="right" :size="14" :color="COLOR_TEXT_TERTIARY" />
      </view>
      <view v-else-if="section.type === 'SHORTCUT_GROUP'" class="home__shortcuts">
        <view v-for="item in section.items" :key="item.key" class="home__shortcut" @click="goHomeLink(item)">
          <XsyRemoteImage v-if="item.imageUrl" class="home__shortcut-icon" :src="item.imageUrl" mode="aspectFill" lazy-load />
          <view v-else class="home__shortcut-icon home__shortcut-icon--fallback">{{ (item.title || '入').slice(0, 1) }}</view>
          <text class="home__shortcut-text">{{ item.title }}</text>
        </view>
      </view>
      <view v-else-if="section.type === 'ACTIVITY_TOPIC'" class="home__topic" @click="goHomeLink(section)">
        <XsyRemoteImage v-if="section.imageUrl" :src="section.imageUrl" :alt="section.title" class="home__topic-image" mode="aspectFit" lazy-load />
        <view v-else class="home__topic-copy">
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

    <view v-if="!hasActivityTopic && activities.length" class="home__section">
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

    <view id="home-frequent" class="home__section">
      <view class="home__section-head">
        <text class="home__section-title">常购商品</text>
        <text class="home__section-link" @click="goCategory">全部商品</text>
      </view>
      <view v-if="frequentProducts.length" class="home__list">
        <ProductCard v-for="item in frequentProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
      <XsyEmpty v-else kind="products" :title="userStore.isLogin ? '暂无常购商品' : '登录后展示常购商品'" compact />
    </view>

    <view id="home-new" class="home__section">
      <view class="home__section-head"><text class="home__section-title">新品上架</text></view>
      <view v-if="newProducts.length" class="home__list">
        <ProductCard v-for="item in newProducts" :key="item.skuId" :product="item" @click="goDetail" @add="addToCart" @inquiry="inquiry" />
      </view>
      <XsyEmpty v-else kind="products" title="暂无新品" compact />
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
      <XsyEmpty v-else kind="products" title="暂无可见商品" desc="商品上架后会显示在这里" compact />
    </view>
    <XsyTabBar :active="0" />
    <MallHomePopup :visible="popupVisible" :items="popup?.items || []" @close="closePopup" @select="onPopupSelect" />
  </view>
</template>

<script setup>
  import XsyRemoteImage from '@/components/common/xsy-remote-image.vue';
  import { computed, ref, watch } from 'vue';
  import { onHide, onPageScroll, onReachBottom, onShow } from '@dcloudio/uni-app';
  import { guardPage } from '@/lib/login-guard';
  import ProductCard from '@/components/business/product-card.vue';
  import MallHomePopup from '@/components/business/mall-home-popup.vue';
  import XsySkeleton from '@/components/common/xsy-skeleton.vue';
  import XsyEmpty from '@/components/common/xsy-empty.vue';
  import XsyTabBar from '@/components/common/xsy-tab-bar.vue';
  import { LOAD_MORE_TEXT } from '@/constants/load-more-const';
  import { mallCatalogApi } from '@/api/mall/catalog-api';
  import { mallAddressApi } from '@/api/mall/address-api';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { mallHomeApi } from '@/api/mall/home-api';
  import { useMallNavigationStore } from '@/store/modules/mall/navigation';
  import { useUserStore } from '@/store/modules/system/user';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { useMallStorefront } from '@/composables/use-mall-storefront';
  import { useMallCart } from '@/composables/use-mall-cart';
  import { xsySentry } from '@/lib/xsy-sentry';
  import { formatDate } from '@/utils/order-display';
  import { categoryIcon } from '@/utils/category-icon';
  import { COLOR_TEXT_TERTIARY, MALL_HOME_COLORS } from '@/constants/theme-color-const';
  import { HOME_POPUP_SEEN } from '@/constants/local-storage-key-const';
  import { MALL_SHORTCUT_IMAGES } from '@/constants/mall-visual-const';

  const { theme, themeStyle: sharedThemeStyle } = useMallTheme();
  const themeStyle = computed(() => ({
    ...sharedThemeStyle.value,
    '--home-header-start': MALL_HOME_COLORS.headerStart,
    '--home-header-end': MALL_HOME_COLORS.headerEnd,
    '--home-page-bg': MALL_HOME_COLORS.background,
    '--home-search-placeholder': MALL_HOME_COLORS.placeholder,
  }));
  const { storeName } = useMallStorefront();

  /*
   * 主题来自全局状态（store/modules/mall/theme.js）：根节点绑定 CSS 变量，
   * 首页内容请求不再修改全局主题，避免较慢的首页响应覆盖新主题。
   */

  const PAGE_SIZE = 10;
  const { statusBarHeight, navBarHeight, menuButtonRect } = useSystemLayout();
  const headerScrolled = ref(false);
  // 深色头图离开后切回白底原生前景，避免系统时间和胶囊覆盖商品内容。
  onPageScroll(({ scrollTop }) => {
    const scrolled = scrollTop > 96;
    if (scrolled === headerScrolled.value) return;
    headerScrolled.value = scrolled;
    uni.setNavigationBarColor({ frontColor: scrolled ? '#000000' : '#ffffff', backgroundColor: scrolled ? '#ffffff' : MALL_HOME_COLORS.headerStart });
  });
  /** 定位行与右上角胶囊同高、并让开胶囊宽度，视觉上与胶囊同一水平线 */
  const locationStyle = computed(() => {
    const rect = menuButtonRect.value;
    if (!rect || !rect.left) {
      return { height: navBarHeight.value + 'px' };
    }
    let windowWidth = 0;
    try {
      const win = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync();
      windowWidth = win.windowWidth || 0;
    } catch {
      windowWidth = 0;
    }
    return {
      height: navBarHeight.value + 'px',
      paddingRight: Math.max(0, windowWidth - rect.left + 8) + 'px',
    };
  });
  /** 顶部定位：取默认收货地址的市区，没有地址时提示去选 */
  const locationText = ref('选择收货地址');
  async function loadLocation() {
    if (!userStore.isLogin) return;
    try {
      const response = await mallAddressApi.getList();
      const list = response.data || [];
      const chosen = list.find((item) => item.isDefault) || list[0];
      if (!chosen) {
        return;
      }
      locationText.value = [chosen.cityName, chosen.districtName].filter(Boolean).join('') || chosen.detailAddress || '选择收货地址';
    } catch (error) {
      xsySentry.captureError(error);
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
  /** 页面不可见时暂停轮播，避免后台继续计时和耗电 */
  const bannerAutoplay = ref(true);
  /** EVERY_ENTRY：本次进入首页是否已经展示过；onHide 后重置 */
  const popupShownThisEntry = ref(false);
  let popupRequest = 0;
  // 只发布配色或所有装修内容均不可见时，继续提供默认首页内容。
  const hasPublishedLayout = computed(() => Boolean(homeLayout.value?.releaseId != null && homeLayout.value?.blocks?.length));
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
  const leadBannerKey = computed(() => configuredSections.value.find((section) => section.type === 'BANNER_GROUP')?.key);
  const hasActivityTopic = computed(() => configuredSections.value.some((section) => section.type === 'ACTIVITY_TOPIC'));
  const serviceShortcuts = [
    { key: 'new', label: '新品上架' },
    { key: 'frequent', label: '常购清单' },
    { key: 'activities', label: '活动专区' },
    { key: 'delivery', label: '配送查询' },
  ];
  /**
   * 分类横滑不是后台组件，但它需要跟在轮播后面：把 RAIL 作为虚拟段插入轮播之后。
   * 公告等后台组件仍按 sortOrder 渲染，所以公告排在轮播前时就会显示在轮播上方。
   */
  const contentSections = computed(() => {
    const sections = [...configuredSections.value];
    const bannerIndex = sections.findIndex((section) => section.type === 'BANNER_GROUP');
    const railIndex = bannerIndex < 0 ? 0 : bannerIndex + 1;
    const fixed = categoryRail.value.length ? [{ type: 'RAIL', key: 'rail' }] : [];
    fixed.push({ type: 'SERVICES', key: 'services' });
    return [...sections.slice(0, railIndex), ...fixed, ...sections.slice(railIndex)];
  });
  /** 首页展示正式可见树的一级分类，进入分类页后再逐级选购。 */
  const categoryRail = ref([]);
  async function loadCategoryRail() {
    if (!userStore.isLogin) return;
    try {
      const response = await mallCatalogApi.getCategories();
      // 只用正式可见树的一级分类，素材包里的其它大类不自动变成业务入口。
      categoryRail.value = (response.data || []).slice(0, 14);
    } catch (error) {
      xsySentry.captureError(error);
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
    if (!userStore.isLogin) return;
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
      xsySentry.captureError(error);
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
      xsySentry.captureError(error);
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
      xsySentry.captureError(error);
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
      xsySentry.captureError(error);
    }
    // 营销装修不替代客户商品区，轮播发布后常购和新品仍读取正式数据。
    await Promise.all([loadFrequent(), loadMarketing()]);
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
      xsySentry.captureError(error);
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
        loadProducts(true);
        loadCategoryRail();
        loadLocation();
        loadHomeLayout();
        loadHomePopup();
      }
    }
  );

  /** 是否配置了可跳转的链接；没有链接的公告/图片不接受点击 */
  function hasLink(block) {
    return Boolean(block && block.linkType && block.linkType !== 'NONE');
  }

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
  /** 固定功能入口使用现有页面或首页数据区，不扩展装修接口的跳转枚举。 */
  function goService(key) {
    if (key === 'activities') return goActivities();
    if (key === 'delivery') return uni.switchTab({ url: '/pages/order/index' });
    if (!guardPage('/pages/category/index')) return;
    // 锚点让开固定阅读栏与微信胶囊，不把分区标题滚到系统状态栏下面。
    uni
      .createSelectorQuery()
      .select(key === 'new' ? '#home-new' : '#home-frequent')
      .boundingClientRect()
      .selectViewport()
      .scrollOffset()
      .exec(([section, viewport]) => {
        if (section && viewport)
          uni.pageScrollTo({ scrollTop: Math.max(0, section.top + viewport.scrollTop - statusBarHeight.value - 52), duration: 250 });
      });
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
    uni.setNavigationBarColor({
      frontColor: headerScrolled.value ? '#000000' : '#ffffff',
      backgroundColor: headerScrolled.value ? '#ffffff' : MALL_HOME_COLORS.headerStart,
    });
    bannerAutoplay.value = true;
    if (showed) {
      loadProducts(true);
      loadHomeLayout();
      loadHomePopup();
    }
    showed = true;
  });
  onHide(() => {
    // 页面不可见即结束「本次进入」；再次进入时 EVERY_ENTRY 可重新展示
    bannerAutoplay.value = false;
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
    --xsy-page-x: #{$space-4};
    background: var(--home-page-bg);
    /* 让开自绘底部导航 */
    padding-bottom: calc(var(--xsy-tabbar-h, 100rpx) + #{$space-6});
  }
  .home__header {
    padding: 0 var(--xsy-page-x) $space-4;
    background: linear-gradient(115deg, var(--home-header-start), var(--home-header-end));
  }
  .home__reading-nav {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    z-index: 30;
    box-sizing: content-box;
    background: white;
    display: flex;
    align-items: center;
    padding-left: var(--xsy-page-x);
    color: $color-text-primary;
    font-size: $font-size-lg;
    font-weight: $font-weight-medium;
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
  /* 搜索与定位共用绿色容器，控件之外不再出现独立白色底块。 */
  .home__search-wrap {
    padding-top: $space-3;
  }
  .home__search {
    @include pressable;
    @include flex-start;
    box-sizing: border-box;
    height: 44px;
    padding: 0 $space-3;
    border-radius: $radius-pill;
    background: white;
  }
  .home__search-text {
    margin-left: $space-2;
    color: var(--home-search-placeholder);
    font-size: $font-size-sm;
  }
  /* 分类横滑与下方四列快捷入口对齐，其余分类保留在滚动区域内。 */
  .home__rail {
    margin: $space-3 var(--xsy-page-x) 0;
    padding: $space-3 0;
    border-radius: $radius-lg $radius-lg 0 0;
    background: white;
  }
  .home__rail-scroll {
    white-space: nowrap;
  }
  .home__rail-inner {
    display: flex;
    width: 100%;
    align-items: flex-start;
  }
  .home__rail-item {
    @include pressable;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-1;
    flex: 0 0 25%;
    min-width: 0;
  }
  .home__rail-icon {
    width: 132rpx;
    height: 132rpx;
    @include flex-center;
  }
  .home__rail-glyph {
    width: 128rpx;
    height: 128rpx;
  }
  .home__rail-text {
    max-width: 100%;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .home__section {
    margin: $space-3 var(--xsy-page-x) 0;
    overflow: hidden;
    border-radius: $radius-lg;
    background: white;
  }
  .home__services {
    display: flex;
    justify-content: space-around;
    padding: $space-3 0 $space-4;
    margin: 0 var(--xsy-page-x);
    border-radius: 0 0 $radius-lg $radius-lg;
    background: white;
  }
  .home__services--standalone {
    margin-top: $space-3;
    border-radius: $radius-lg;
  }
  .home__service {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-2;
    min-width: 132rpx;
  }
  .home__service-image {
    width: 112rpx;
    height: 112rpx;
  }
  .home__banner-wrap {
    padding: $space-3 var(--xsy-page-x) 0;
  }
  .home__banner {
    height: calc((100vw - var(--xsy-page-x) - var(--xsy-page-x)) / 2.5);
    border-radius: $radius-lg;
    overflow: hidden;
  }
  .home__banner-image {
    display: block;
    width: 100%;
    height: 100%;
    /* 图片未加载或加载失败时仍是主题色块，不出现白洞 */
    background: white;
  }
  .home__banner-wrap--topic .home__banner {
    height: calc((100vw - var(--xsy-page-x) - var(--xsy-page-x)) / 3);
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
  .home__notice {
    display: flex;
    align-items: center;
    gap: $space-2;
    margin: $space-3 var(--xsy-page-x) 0;
    padding: 0 $space-3;
    /* 整行可点，高度按 44px 触控下限 */
    min-height: 88rpx;
    border-radius: $radius-md;
    background: white;
  }
  .home__notice-tag {
    flex-shrink: 0;
    padding: 2rpx 10rpx;
    border-radius: $radius-sm;
    background: var(--mall-primary, #16a34a);
    color: white;
    font-size: $font-size-xs;
  }
  .home__notice-text {
    flex: 1;
    min-width: 0;
    color: $color-text-primary;
    font-size: $font-size-sm;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .home__shortcuts {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: $space-2;
    margin: $space-3 var(--xsy-page-x) 0;
    padding: $space-3 var(--xsy-page-x);
    background: white;
    border-radius: $radius-lg;
  }
  .home__shortcut {
    @include pressable;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-1;
    padding: $space-2 0;
  }
  .home__shortcut-icon {
    width: 64rpx;
    height: 64rpx;
    border-radius: $radius-md;
    background: $color-bg-hover;
  }
  .home__shortcut-icon--fallback {
    @include flex-center;
    color: var(--mall-primary, #16a34a);
    font-size: $font-size-sm;
  }
  .home__shortcut-text {
    max-width: 100%;
    color: $color-text-secondary;
    font-size: $font-size-xs;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .home__topic {
    margin: $space-3 var(--xsy-page-x) 0;
    overflow: hidden;
    border-radius: $radius-lg;
  }
  .home__topic-image {
    display: block;
    width: 100%;
    height: calc((100vw - var(--xsy-page-x) - var(--xsy-page-x)) / 3);
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
    /* 负外边距抵消内边距，视觉位置不变但点击区域达到 44px */
    padding: $space-3 0 $space-3 $space-4;
    margin: -$space-3 0;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
</style>
