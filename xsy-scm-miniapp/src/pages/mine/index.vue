<template>
  <view class="mine">
    <!-- 客户卡片 -->
    <view class="mine__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="mine__profile">
        <view class="mine__avatar">
          <text class="mine__avatar-text">{{ avatarText }}</text>
        </view>

        <view class="mine__profile-text">
          <text class="mine__name">{{ userStore.customerName || '未登录' }}</text>
          <text class="mine__meta">{{ metaLine }}</text>
        </view>

        <view v-if="!userStore.isLogin" class="mine__login" @click="goLogin">登录</view>
      </view>
    </view>

    <!-- 资产 -->
    <view class="mine__assets">
      <view v-for="asset in ASSETS" :key="asset.key" class="mine__asset" @click="onNavigate(asset)">
        <text class="mine__asset-value">{{ asset.value }}</text>
        <text class="mine__asset-label">{{ asset.label }}</text>
      </view>
    </view>

    <!-- 服务 -->
    <view class="mine__group">
      <text class="mine__group-title">服务</text>
      <view class="mine__list">
        <view v-for="item in SERVICES" :key="item.key" class="mine__cell" @click="onNavigate(item)">
          <text class="mine__cell-text">{{ item.text }}</text>
          <text class="mine__cell-arrow">›</text>
        </view>
      </view>
    </view>

    <view v-if="userStore.isLogin" class="mine__logout" @click="onLogout">退出登录</view>
  </view>
</template>

<script setup>
  import { computed } from 'vue';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useUserStore } from '@/store/modules/system/user';

  const { statusBarHeight } = useSystemLayout();
  const userStore = useUserStore();

  /** 账期 / 余额 / 优惠券 / 待对账：均依赖后端账户接口，暂以占位展示 */
  const ASSETS = [
    { key: 'credit', label: '可用账期', value: '--', url: '/pages-sub/account/credit' },
    { key: 'balance', label: '余额', value: '--', url: '/pages-sub/account/balance' },
    { key: 'coupon', label: '优惠券', value: '--', url: '/pages-sub/account/coupon' },
    { key: 'bill', label: '待对账', value: '--', url: '/pages-sub/account/bill' },
  ];

  const SERVICES = [
    { key: 'address', text: '收货地址', url: '/pages-sub/address/list' },
    { key: 'favorite', text: '常购清单', url: '/pages-sub/favorite/index' },
    { key: 'stat', text: '下单统计', url: '/pages-sub/account/stat' },
    { key: 'message', text: '消息通知', url: '/pages-sub/account/message' },
    { key: 'salesman', text: '联系业务员', action: 'salesman' },
    { key: 'service', text: '客服', action: 'service' },
    { key: 'terms', text: '服务条款', url: '/pages-sub/account/terms' },
    { key: 'after-sale', text: '售后规则', url: '/pages-sub/account/after-sale-rule' },
    { key: 'about', text: '关于我们', url: '/pages-sub/account/about' },
    { key: 'setting', text: '设置', url: '/pages-sub/account/setting' },
  ];

  const avatarText = computed(() => {
    const name = userStore.customerName || userStore.contactName;
    return name ? name.slice(0, 1) : '客';
  });

  const metaLine = computed(() => {
    if (!userStore.isLogin) {
      return '登录后查看客户信息';
    }
    const parts = [userStore.customerCode, userStore.phone].filter(Boolean);
    return parts.length ? parts.join(' · ') : '--';
  });

  function goLogin() {
    uni.navigateTo({ url: '/pages/login/login' });
  }

  function requireLogin() {
    if (userStore.isLogin) {
      return true;
    }
    goLogin();
    return false;
  }

  function onNavigate(item) {
    if (!requireLogin()) {
      return;
    }
    if (item.url) {
      uni.navigateTo({ url: item.url });
      return;
    }
    // 联系业务员 / 客服依赖后端配置（业务员手机号、客服企微），接口就绪后接入
    uni.showToast({ title: '该功能待后端接口就绪', icon: 'none' });
  }

  function onLogout() {
    uni.showModal({
      title: '退出登录',
      content: '确认退出当前账号？',
      success: async (res) => {
        if (res.confirm) {
          await userStore.logout();
          uni.reLaunch({ url: '/pages/login/login' });
        }
      },
    });
  }
</script>

<style lang="scss" scoped>
  .mine {
    min-height: 100vh;
    background-color: $color-bg-page;
    padding-bottom: $space-8;

    &__header {
      padding: 0 $space-4 $space-8;
      background: linear-gradient(180deg, $color-primary 0%, $color-primary-dark 100%);
    }

    &__profile {
      @include flex-start;
      padding-top: $space-6;
    }

    &__avatar {
      width: 112rpx;
      height: 112rpx;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.22);
      @include flex-center;
      flex-shrink: 0;
    }

    &__avatar-text {
      font-size: $font-size-xxl;
      font-weight: $font-weight-bold;
      color: $color-text-inverse;
    }

    &__profile-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      margin-left: $space-3;
      min-width: 0;
    }

    &__name {
      font-size: $font-size-xl;
      font-weight: $font-weight-bold;
      color: $color-text-inverse;
      @include ellipsis;
    }

    &__meta {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: rgba(255, 255, 255, 0.85);
      @include ellipsis;
    }

    &__login {
      flex-shrink: 0;
      padding: $space-1 $space-3;
      border-radius: $radius-pill;
      border: 1px solid rgba(255, 255, 255, 0.6);
      font-size: $font-size-sm;
      color: $color-text-inverse;
    }

    &__assets {
      @include flex-between;
      margin: -$space-6 $space-4 0;
      padding: $space-4 0;
      @include card;
    }

    &__asset {
      @include flex-center;
      flex-direction: column;
      flex: 1;
    }

    &__asset-value {
      font-size: $font-size-lg;
      font-weight: $font-weight-bold;
      color: $color-text-primary;
    }

    &__asset-label {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: $color-text-secondary;
    }

    &__group {
      margin: $space-4 $space-4 0;
    }

    &__group-title {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
      padding-left: $space-1;
    }

    &__list {
      margin-top: $space-2;
      @include card(0);
      overflow: hidden;
    }

    &__cell {
      @include flex-between;
      height: 100rpx;
      padding: 0 $space-4;
      @include hairline-bottom;

      &:last-child::after {
        display: none;
      }
    }

    &__cell-text {
      font-size: $font-size-base;
      color: $color-text-primary;
    }

    &__cell-arrow {
      font-size: $font-size-lg;
      color: $color-text-placeholder;
    }

    &__logout {
      @include flex-center;
      margin: $space-6 $space-4 0;
      height: 88rpx;
      border-radius: $radius-md;
      background-color: $color-bg-card;
      font-size: $font-size-base;
      color: $color-danger;
    }
  }
</style>
