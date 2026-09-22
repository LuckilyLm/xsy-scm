<template>
  <view class="home">
    <!-- ===================== 首屏（规划 §8.1） ===================== -->
    <view class="home__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <!-- 当前客户 / 门店 -->
      <view class="home__identity">
        <view class="home__identity-text">
          <text class="home__greeting">{{ greeting }}，{{ displayName }}</text>
          <text class="home__store">{{ storeLabel }}</text>
        </view>
        <view v-if="!userStore.isLogin" class="home__login" @click="goLogin">登录</view>
        <view v-else class="home__switch" @click="onSwitchStore">切换门店</view>
      </view>

      <!-- 配送地址 -->
      <view class="home__address" @click="goAddressList">
        <text class="home__address-label">配送至</text>
        <text class="home__address-value">{{ deliveryAddress || '请选择收货地址' }}</text>
        <text class="home__address-arrow">›</text>
      </view>

      <!-- 搜索：永久高优先级 -->
      <view class="home__search" @click="goSearch">
        <text class="home__search-mark">搜</text>
        <text class="home__search-ph">搜商品 / SKU</text>
      </view>
    </view>

    <!-- 高频快捷入口 -->
    <view class="home__quick">
      <view v-for="item in QUICK_ENTRIES" :key="item.key" class="home__quick-item" @click="onQuickEntry(item)">
        <view class="home__quick-mark" :style="{ backgroundColor: item.bg, color: item.color }">
          <text>{{ item.mark }}</text>
        </view>
        <text class="home__quick-text">{{ item.text }}</text>
      </view>
    </view>

    <!-- 楼层区：Banner / 公告 / 分类导航 / 常购 / 再来一单 / 商品流 -->
    <view class="home__floors">
      <PagePlaceholder
        mark="首页楼层"
        title="首页楼层待接入"
        desc="公告、分类导航、常购商品、再来一单、限时抢购、新品推荐与商品流将按规划 §8.2 的优先级逐层接入。"
        plan-ref="规划 §8.2 / §9"
      />
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useUserStore } from '@/store/modules/system/user';

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

  const displayName = computed(() => {
    if (!userStore.isLogin) {
      return '欢迎光临';
    }
    return userStore.contactName || userStore.customerName || '客户';
  });

  const storeLabel = computed(() => userStore.customerName || '未绑定客户');

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
</script>

<style lang="scss" scoped>
  .home {
    min-height: 100vh;
    background-color: $color-bg-page;

    &__header {
      padding: 0 $space-4 $space-4;
      background: linear-gradient(180deg, $color-primary 0%, $color-primary-dark 100%);
    }

    &__identity {
      @include flex-between;
      padding-top: $space-4;
    }

    &__identity-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    &__greeting {
      font-size: $font-size-xl;
      font-weight: $font-weight-bold;
      color: $color-text-inverse;
      @include ellipsis;
    }

    &__store {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: rgba(255, 255, 255, 0.85);
      @include ellipsis;
    }

    &__login,
    &__switch {
      flex-shrink: 0;
      margin-left: $space-3;
      padding: $space-1 $space-3;
      border-radius: $radius-pill;
      border: 1px solid rgba(255, 255, 255, 0.6);
      font-size: $font-size-sm;
      color: $color-text-inverse;
    }

    &__address {
      @include flex-start;
      margin-top: $space-3;
      font-size: $font-size-sm;
    }

    &__address-label {
      color: rgba(255, 255, 255, 0.75);
    }

    &__address-value {
      margin-left: $space-2;
      color: $color-text-inverse;
      @include ellipsis;
    }

    &__address-arrow {
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

    &__search-mark {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__search-ph {
      margin-left: $space-2;
      font-size: $font-size-base;
      color: $color-text-placeholder;
    }

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

    &__floors {
      margin-top: $space-3;
    }
  }
</style>
