<template>
  <view class="home">
    <!-- ===================== 首屏：客户 / 门店 / 地址 / 搜索 ===================== -->
    <view class="home__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="home__brand">鲜蔬源商城 · 客户采购</text>

      <!-- 门店身份 + 切换门店：仅「已登录且已绑定客户/门店」时显示切换门店 -->
      <view class="home__store-row">
        <text class="home__store">{{ hasStore ? userStore.customerName : '欢迎光临' }}</text>
        <view v-if="hasStore" class="home__store-action" @click="onSwitchStore">
          <text class="home__store-action-text">切换门店</text>
        </view>
        <view v-else class="home__store-action" @click="goLogin">
          <text class="home__store-action-text">登录</text>
        </view>
      </view>

      <text class="home__greeting">{{ hasStore ? `${greeting}，${displayName}` : '登录后查看客户价格' }}</text>

      <!-- 配送地址 -->
      <view class="home__address" @click="goAddressList">
        <text class="home__address-label">配送至</text>
        <text class="home__address-value">{{ deliveryAddress || '请选择收货地址' }}</text>
        <text class="home__address-arrow">›</text>
      </view>

      <!-- 商品 / SKU 搜索：图标 20px、内边距 12px、图文间距 8px，对齐 Figma Home / Final 的 SearchOutlined 定位 -->
      <view class="home__search" @click="goSearch">
        <uni-icons type="search" :size="20" :color="COLOR_TEXT_TERTIARY" />
        <text class="home__search-ph">搜商品 / SKU</text>
      </view>
    </view>

    <!-- ===================== 高频快捷入口 ===================== -->
    <view class="home__quick">
      <view v-for="item in QUICK_ENTRIES" :key="item.key" class="home__quick-item" @click="onQuickEntry(item)">
        <view class="home__quick-mark" :style="{ backgroundColor: item.bg, color: item.color }">
          <text>{{ item.mark }}</text>
        </view>
        <text class="home__quick-text">{{ item.text }}</text>
      </view>
    </view>

    <!-- ===================== 公告 ===================== -->
    <view v-if="notice" class="home__notice">
      <text class="home__notice-tag">公告</text>
      <text class="home__notice-text">{{ notice }}</text>
    </view>

    <!-- ===================== 商品分类（真实六类，来自服务端楼层） ===================== -->
    <view v-if="categoryNav.length" class="home__section">
      <SectionHeader title="商品分类" />
      <CategoryNav :items="categoryNav" @select="onCategory" />
    </view>

    <!-- ===================== Banner ===================== -->
    <view v-if="banner" class="home__section">
      <BannerCard :banner="banner" @click="onBanner" />
    </view>

    <!-- ===================== 限时抢购（无活动整层隐藏） ===================== -->
    <view v-if="flashSale" class="home__section">
      <FlashSale
        :title="flashSale.title"
        :subtitle="flashSale.subtitle"
        :end-time="flashSale.endTime"
        :items="flashSale.items || []"
        @click="goDetail"
        @add="onAdd"
      />
    </view>

    <!-- ===================== 推荐商品 ===================== -->
    <view v-if="recommend.length" class="home__section">
      <SectionHeader title="推荐商品" more-text="更多" @more="goCategoryTab" />
      <view class="home__list">
        <ProductCard v-for="p in recommend" :key="p.skuId" :product="p" @click="goDetail" @add="onAdd" @inquiry="onInquiry" />
      </view>
    </view>

    <!-- ===================== 商品列表（商品流） ===================== -->
    <view class="home__section">
      <SectionHeader title="商品列表" :more-text="flowMoreText" @more="goSearch" />
      <view v-if="flow.length" class="home__list">
        <ProductCard v-for="p in flow" :key="p.skuId" :product="p" @click="goDetail" @add="onAdd" @inquiry="onInquiry" />
      </view>

      <view v-if="flowLoading" class="home__flow-tip">
        <text class="home__flow-tip-text">加载中…</text>
      </view>
      <view v-else-if="!flowHasMore && flow.length" class="home__flow-tip">
        <text class="home__flow-tip-text">没有更多了</text>
      </view>
      <view v-else-if="!flow.length && !flowLoading" class="home__flow-tip">
        <text class="home__flow-tip-text">暂无可展示商品</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onReachBottom } from '@dcloudio/uni-app';
  import SectionHeader from '@/components/business/section-header.vue';
  import CategoryNav from '@/components/business/category-nav.vue';
  import BannerCard from '@/components/business/banner-card.vue';
  import FlashSale from '@/components/business/flash-sale.vue';
  import ProductCard from '@/components/business/product-card.vue';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { mallHomeApi, mallCatalogApi } from '@/api/mall';
  import { smartSentry } from '@/lib/smart-sentry';
  import { useUserStore } from '@/store/modules/system/user';
  import { useMallNavigationStore } from '@/store/modules/mall/navigation';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';

  const { statusBarHeight } = useSystemLayout();
  const userStore = useUserStore();

  /** 配送地址：接入 address-api 后改为真实默认地址 */
  const deliveryAddress = ref('');

  const QUICK_ENTRIES = [
    { key: 'favorite', text: '常购商品', mark: '常', bg: '#e8f5ec', color: '#16a34a' },
    { key: 'reorder', text: '再来一单', mark: '再', bg: '#eaf2fe', color: '#3b82f6' },
    { key: 'flash', text: '今日特价', mark: '特', bg: '#fef6e7', color: '#f59e0b' },
    { key: 'bill', text: '我的账单', mark: '账', bg: '#f2f3f5', color: '#646a73' },
  ];

  /* ===================== 首页楼层 ===================== */

  const sections = ref([]);

  const notice = computed(() => {
    const hit = sections.value.find((s) => s.type === 'NOTICE');
    return (hit && hit.content) || '';
  });

  const categoryNav = computed(() => {
    const hit = sections.value.find((s) => s.type === 'CATEGORY_NAV');
    return (hit && hit.items) || [];
  });

  const banner = computed(() => {
    const hit = sections.value.find((s) => s.type === 'BANNER');
    return (hit && hit.items && hit.items[0]) || null;
  });

  const flashSale = computed(() => sections.value.find((s) => s.type === 'FLASH_SALE') || null);

  const recommend = computed(() => {
    const hit = sections.value.find((s) => s.type === 'RECOMMEND');
    return (hit && hit.items) || [];
  });

  async function loadHome() {
    try {
      const res = await mallHomeApi.getHome();
      sections.value = (res.data && res.data.sections) || [];
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  /* ===================== 商品流（复用统一 ProductCard） ===================== */

  const FLOW_PAGE_SIZE = 10;

  const flow = ref([]);
  const flowPageNum = ref(0);
  const flowTotal = ref(0);
  const flowLoading = ref(false);

  const flowHasMore = computed(() => flow.value.length < flowTotal.value);
  const flowMoreText = computed(() => (flowHasMore.value ? '更多' : ''));

  async function loadFlow(reset = false) {
    if (flowLoading.value) {
      return;
    }
    if (reset) {
      flowPageNum.value = 0;
      flow.value = [];
      flowTotal.value = 0;
    }

    flowLoading.value = true;
    try {
      const res = await mallCatalogApi.getProducts({
        pageNum: flowPageNum.value + 1,
        pageSize: FLOW_PAGE_SIZE,
      });
      const page = res.data || {};
      flow.value = flowPageNum.value === 0 ? page.list || [] : flow.value.concat(page.list || []);
      flowTotal.value = page.total || 0;
      flowPageNum.value += 1;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      flowLoading.value = false;
    }
  }

  /** 触底加载下一页：loading 中与取满 total 后都不再请求 */
  function loadMoreFlow() {
    if (flowLoading.value || !flowHasMore.value) {
      return;
    }
    loadFlow(false);
  }

  onReachBottom(loadMoreFlow);

  /* ===================== 展示文案 ===================== */

  /** 已登录且存在当前客户 / 门店：只有此时才显示门店名与「切换门店」 */
  const hasStore = computed(() => userStore.isLogin && !!userStore.customerName);

  /** 已绑定状态下问候语称呼：联系人优先 */
  const displayName = computed(() => userStore.contactName || userStore.customerName || '客户');

  const greeting = computed(() => {
    const hour = new Date().getHours();
    if (hour < 6) {
      return '凌晨好';
    }
    if (hour < 12) {
      return '上午好';
    }
    if (hour < 18) {
      return '下午好';
    }
    return '晚上好';
  });

  /* ===================== 交互 ===================== */

  /** 需要登录态的操作统一收口 */
  function requireLogin() {
    if (userStore.isLogin) {
      return true;
    }
    uni.navigateTo({ url: '/pages/login/login' });
    return false;
  }

  function goLogin() {
    uni.navigateTo({ url: '/pages/login/login' });
  }

  function goSearch() {
    uni.navigateTo({ url: '/pages-sub/product/search' });
  }

  function goAddressList() {
    if (!requireLogin()) {
      return;
    }
    uni.navigateTo({ url: '/pages-sub/address/list' });
  }

  /**
   * 切到分类 Tab，并带上要选中的一级分类。
   *
   * `uni.switchTab` 不支持 query，所以目标分类只能通过一个瞬时的内存状态带过去
   * （`mall/navigation` store，不持久化）；分类页在 onShow 里消费它并选中对应分类。
   * 只传 categoryId，不传分类对象——分类清单始终以服务端 getCategories() 为准。
   */
  function goCategoryTab() {
    uni.switchTab({ url: '/pages/category/index' });
  }

  function onCategory(item) {
    if (item && item.categoryId) {
      useMallNavigationStore().setPendingCategoryId(item.categoryId);
    }
    goCategoryTab();
  }

  function onBanner(item) {
    // Banner 跳转由服务端配置下发（规划 §9.2），当前只支持分类跳转
    if (item && item.linkType === 'CATEGORY') {
      goCategoryTab();
      return;
    }
    goCategoryTab();
  }

  function onSwitchStore() {
    // 门店切换依赖后端「账号-客户」多对多关系，接口就绪后接入
    uni.showToast({ title: '门店切换待后端接口就绪', icon: 'none' });
  }

  function onQuickEntry(item) {
    if (!requireLogin()) {
      return;
    }
    const routes = {
      favorite: '/pages-sub/favorite/index',
      reorder: '/pages-sub/favorite/reorder',
      flash: '/pages-sub/promotion/flash-sale',
      bill: '/pages-sub/account/bill',
    };
    const url = routes[item.key];
    if (url) {
      uni.navigateTo({ url });
    }
  }

  function goDetail(product) {
    uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${product.skuId}` });
  }

  function onAdd(product) {
    // 加购属 §39 第 10 项（购物车），cart 契约接入后替换这里
    uni.showToast({ title: `加入购物车：${product.productName}`, icon: 'none' });
  }

  /** 无客户价商品不进入加购，改为引导询价 */
  function onInquiry(product) {
    uni.showToast({ title: `${product.productName} 暂无客户价，请联系业务员询价`, icon: 'none' });
  }

  loadHome();
  loadFlow(true);
</script>

<style lang="scss" scoped>
  .home {
    min-height: 100vh;
    background-color: $color-bg-page;
    padding-bottom: $space-6;

    /* ===================== 头部 ===================== */
    &__header {
      padding: 0 $space-4 $space-4;
      background: linear-gradient(180deg, $color-primary 0%, $color-primary-dark 100%);
    }

    &__brand {
      display: block;
      padding-top: $space-4;
      font-size: $font-size-xs;
      color: rgba(255, 255, 255, 0.8);
    }

    &__store-row {
      @include flex-between;
      margin-top: $space-2;
    }

    &__store {
      flex: 1;
      min-width: 0;
      font-size: $font-size-xl;
      font-weight: $font-weight-bold;
      color: $color-text-inverse;
      @include ellipsis;
    }

    &__store-action {
      flex-shrink: 0;
      margin-left: $space-3;
      padding: $space-1 $space-3;
      border-radius: $radius-pill;
      border: 1px solid rgba(255, 255, 255, 0.6);
    }

    &__store-action-text {
      font-size: $font-size-sm;
      color: $color-text-inverse;
      white-space: nowrap;
    }

    &__greeting {
      display: block;
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: rgba(255, 255, 255, 0.85);
      @include ellipsis;
    }

    &__address {
      @include flex-start;
      margin-top: $space-3;
      font-size: $font-size-sm;
      min-width: 0;
    }

    &__address-label {
      flex-shrink: 0;
      color: rgba(255, 255, 255, 0.75);
    }

    &__address-value {
      flex: 1;
      min-width: 0;
      margin-left: $space-2;
      color: $color-text-inverse;
      @include ellipsis;
    }

    &__address-arrow {
      flex-shrink: 0;
      margin-left: $space-1;
      color: rgba(255, 255, 255, 0.75);
    }

    &__search {
      @include flex-start;
      margin-top: $space-3;
      height: 72rpx;
      padding: 0 $space-3;
      border-radius: $radius-pill;
      background-color: $color-bg-card;
    }

    &__search-ph {
      margin-left: $space-2;
      font-size: $font-size-base;
      color: $color-text-placeholder;
    }

    /* ===================== 快捷入口 ===================== */
    &__quick {
      @include flex-between;
      margin: $space-3 $space-4 0;
      padding: $space-4 $space-2;
      @include card;
    }

    &__quick-item {
      @include flex-center;
      flex-direction: column;
      flex: 1;
    }

    &__quick-mark {
      width: 88rpx;
      height: 88rpx;
      border-radius: 50%;
      @include flex-center;
      font-size: $font-size-lg;
      font-weight: $font-weight-medium;
    }

    &__quick-text {
      margin-top: $space-2;
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    /* ===================== 公告 ===================== */
    &__notice {
      @include flex-start;
      margin: $space-3 $space-4 0;
      padding: $space-2 $space-3;
      border-radius: $radius-md;
      background-color: $color-warning-light;
    }

    &__notice-tag {
      flex-shrink: 0;
      padding: 0 $space-1;
      height: 32rpx;
      line-height: 32rpx;
      border-radius: $radius-sm;
      background-color: $color-warning;
      /* 深色字：白字在 warning 橙底上仅 2.15:1，不达 AA */
      color: $color-text-primary;
      font-size: $font-size-xs;
    }

    &__notice-text {
      flex: 1;
      min-width: 0;
      margin-left: $space-2;
      font-size: $font-size-xs;
      /* 正文 text-secondary：warning 橙在 warning-light 底上仅约 2.00:1，实测此处 5.08:1 */
      color: $color-text-secondary;
      @include ellipsis;
    }

    /* ===================== 楼层 ===================== */
    &__section {
      margin-top: $space-3;
    }

    &__list {
      background-color: $color-bg-card;
    }

    &__flow-tip {
      @include flex-center;
      padding: $space-4 0;
      background-color: $color-bg-card;
    }

    &__flow-tip-text {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }
  }
</style>
