<template>
  <view class="mine">
    <view class="mine__header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="mine__avatar">{{ avatarText }}</view>
      <view class="mine__identity">
        <text class="mine__name">{{ userStore.customerName || '未登录' }}</text>
        <text class="mine__meta">{{ metaLine }}</text>
      </view>
      <text v-if="!userStore.isLogin" class="mine__login" @click="goLogin">登录</text>
    </view>
    <view class="mine__card">
      <view class="mine__row" @click="goCart"><text>购物车</text><text>›</text></view>
      <view class="mine__row" @click="goAddress"><text>收货地址</text><text>›</text></view>
      <view class="mine__row" @click="goAfterSales"><text>售后服务</text><text>›</text></view>
      <view class="mine__row" @click="goWallet"><text>我的钱包</text><text>›</text></view>
      <view class="mine__row" @click="goCredit"><text>账期与账单</text><text>›</text></view>
      <view class="mine__row" @click="changePassword"><text>修改登录密码</text><text>›</text></view>
      <view class="mine__row" @click="goOrders"><text>我的订单</text><text>›</text></view>
    </view>
    <view v-if="userStore.isLogin" class="mine__logout" @click="logout">退出登录</view>
  </view>
</template>

<script setup>
  import { computed } from 'vue';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useUserStore } from '@/store/modules/system/user';

  const { statusBarHeight } = useSystemLayout();
  const userStore = useUserStore();
  const avatarText = computed(() => (userStore.customerName || userStore.contactName || '客').slice(0, 1));
  const metaLine = computed(() => userStore.isLogin ? [userStore.customerCode, userStore.phone].filter(Boolean).join(' · ') : '登录后查看客户信息');
  function goLogin() { uni.navigateTo({ url: '/pages/login/login' }); }
  function goCart() { uni.switchTab({ url: '/pages/cart/index' }); }
  function goOrders() { uni.switchTab({ url: '/pages/order/index' }); }
  function goAddress() { uni.navigateTo({ url: '/pages-sub/address/list' }); }
  function goAfterSales() { userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/after-sale/list' }) : goLogin(); }
  function goWallet() { userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/wallet/index' }) : goLogin(); }
  function goCredit() { userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/credit/index' }) : goLogin(); }
  function changePassword() { userStore.isLogin ? uni.navigateTo({ url: '/pages/account/password' }) : goLogin(); }
  function logout() {
    uni.showModal({ title: '退出登录', content: '确认退出当前账号？', success: async ({ confirm }) => {
      if (!confirm) return;
      await userStore.logout();
      uni.reLaunch({ url: '/pages/login/login' });
    }});
  }
</script>

<style lang="scss" scoped>
  .mine { min-height: 100vh; padding-bottom: $space-8; background: $color-bg-page; }
  .mine__header { @include flex-start; padding: $space-6 $space-4 $space-8; background: linear-gradient(180deg, $color-primary, $color-primary-dark); }
  .mine__avatar { width: 112rpx; height: 112rpx; border-radius: 50%; background: rgba(255,255,255,.2); color: white; font-size: $font-size-xxl; @include flex-center; }
  .mine__identity { flex: 1; min-width: 0; margin-left: $space-3; display: flex; flex-direction: column; }
  .mine__name { color: white; font-size: $font-size-xl; font-weight: $font-weight-bold; @include ellipsis; }
  .mine__meta { margin-top: $space-1; color: rgba(255,255,255,.8); font-size: $font-size-sm; }
  .mine__login { padding: $space-1 $space-3; border: 1px solid rgba(255,255,255,.6); border-radius: $radius-pill; color: white; }
  .mine__card { margin: -$space-4 $space-4 0; @include card(0); overflow: hidden; }
  .mine__row { height: 100rpx; padding: 0 $space-4; @include flex-between; @include hairline-bottom; color: $color-text-primary; }
  .mine__logout { margin: $space-6 $space-4 0; height: 88rpx; @include flex-center; @include card(0); color: $color-danger; }
</style>
