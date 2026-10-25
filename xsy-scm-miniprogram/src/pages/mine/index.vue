<template>
  <view class="mine" :style="themeStyle">
    <view class="mine__header" :style="{ paddingTop: statusBarHeight + 'px', paddingRight: headerRightGap + 'px' }">
      <view class="mine__avatar">{{ avatarText }}</view>
      <view class="mine__identity">
        <text class="mine__name">{{ userStore.customerName || '未登录' }}</text>
        <text class="mine__meta">{{ metaLine }}</text>
      </view>
      <text v-if="!userStore.isLogin" class="mine__login" @click="goLogin">登录</text>
    </view>
    <view class="mine__card">
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goCart"
        ><text>购物车</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goAddress"
        ><text>收货地址</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goAfterSales"
        ><text>售后服务</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goWallet"
        ><text>我的钱包</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goCredit"
        ><text>账期与账单</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goMyCoupons"
        ><text>我的优惠券</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goCoupons"
        ><text>领券中心</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goPage('ANNOUNCEMENT')"
        ><text>商城公告</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goPage('ABOUT')"
        ><text>关于我们</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goPage('TERMS')"
        ><text>服务条款</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goPage('AFTER_SALE')"
        ><text>售后规则</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="changePassword"
        ><text>修改登录密码</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="goOrders"
        ><text>我的订单</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
      <view class="mine__row" hover-class="xsy-press-row--active" :hover-stay-time="80" @click="scanTrace"
        ><text>扫码溯源</text><uni-icons type="right" :size="14" :color="COLOR_TEXT_TERTIARY"
      /></view>
    </view>
    <view v-if="userStore.isLogin" class="mine__logout" hover-class="xsy-press--active" :hover-stay-time="80" @click="logout">退出登录</view>
    <XsyTabBar :active="4" />
  </view>
</template>

<script setup>
  import { computed } from 'vue';
  import { useSystemLayout } from '@/composables/use-system-layout';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { useUserStore } from '@/store/modules/system/user';
  import { COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import XsyTabBar from '@/components/common/xsy-tab-bar.vue';

  const { themeStyle } = useMallTheme();

  const { statusBarHeight, menuButtonRect } = useSystemLayout();
  /** 未登录时右侧有「登录」按钮，必须让开右上角胶囊 */
  const headerRightGap = computed(() => {
    const rect = menuButtonRect.value;
    if (!rect || !rect.left) {
      return 16;
    }
    try {
      const win = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync();
      return Math.max(16, (win.windowWidth || 0) - rect.left + 8);
    } catch {
      return 16;
    }
  });
  const userStore = useUserStore();
  const avatarText = computed(() => (userStore.customerName || userStore.contactName || '客').slice(0, 1));
  const metaLine = computed(() => (userStore.isLogin ? [userStore.customerCode, userStore.phone].filter(Boolean).join(' · ') : '登录后查看客户信息'));
  function goLogin() {
    uni.navigateTo({ url: '/pages/login/login' });
  }
  function goCart() {
    uni.switchTab({ url: '/pages/cart/index' });
  }
  function goOrders() {
    uni.switchTab({ url: '/pages/order/index' });
  }
  function goAddress() {
    uni.navigateTo({ url: '/pages-sub/address/list' });
  }
  function goAfterSales() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/after-sale/list' }) : goLogin();
  }
  function goWallet() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/wallet/index' }) : goLogin();
  }
  function goCredit() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/credit/index' }) : goLogin();
  }
  function goMyCoupons() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/promotion/my' }) : goLogin();
  }
  function goCoupons() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages-sub/promotion/coupons' }) : goLogin();
  }
  function changePassword() {
    userStore.isLogin ? uni.navigateTo({ url: '/pages/account/password' }) : goLogin();
  }
  /** 单页内容不要求登录：条款、公告与售后规则对未登录顾客也应可查 */
  function goPage(pageCode) {
    uni.navigateTo({ url: `/pages-sub/page/index?pageCode=${pageCode}` });
  }

  /**
   * 扫码溯源：扫标签上的二维码。
   *
   * 不要求登录——客户和监管都可能只是扫一下看看货从哪来。
   * 标签里的地址是哈希路由，令牌在 `#/t/` 之后，所以先按 URL 解析再退回整串。
   */
  function scanTrace() {
    uni.scanCode({
      scanType: ['qrCode'],
      success: ({ result }) => {
        const matched = /#\/t\/([^/?#]+)/.exec(result || '');
        const token = matched ? matched[1] : (result || '').trim();
        if (!token) {
          uni.showToast({ title: '未识别到溯源码', icon: 'none' });
          return;
        }
        uni.navigateTo({ url: `/pages-sub/trace/detail?token=${encodeURIComponent(token)}` });
      },
      fail: () => uni.showToast({ title: '已取消扫码', icon: 'none' }),
    });
  }

  function logout() {
    uni.showModal({
      title: '退出登录',
      content: '确认退出当前账号？',
      success: async ({ confirm }) => {
        if (!confirm) return;
        await userStore.logout();
        uni.reLaunch({ url: '/pages/login/login' });
      },
    });
  }
</script>

<style lang="scss" scoped>
  .mine {
    @include viewport-min-height;
    padding-bottom: calc(var(--xsy-tabbar-h, 100rpx) + #{$space-8});
    background: $color-bg-page;
  }
  .mine__header {
    @include flex-start;
    padding: $space-6 var(--xsy-page-x) $space-8;
    background: linear-gradient(180deg, var(--mall-primary, #16a34a), var(--mall-primary-dark, #12833c));
  }
  .mine__avatar {
    width: 112rpx;
    height: 112rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.2);
    color: white;
    font-size: $font-size-xxl;
    @include flex-center;
  }
  .mine__identity {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
    display: flex;
    flex-direction: column;
  }
  .mine__name {
    color: white;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    @include ellipsis;
  }
  .mine__meta {
    margin-top: $space-1;
    color: rgba(255, 255, 255, 0.8);
    font-size: $font-size-sm;
  }
  .mine__login {
    padding: $space-1 $space-3;
    border: 1px solid rgba(255, 255, 255, 0.6);
    border-radius: $radius-pill;
    color: white;
  }
  .mine__card {
    margin: -$space-4 var(--xsy-page-x) 0;
    @include card(0);
    overflow: hidden;
  }
  .mine__row {
    @include pressable;
    height: 100rpx;
    padding: 0 var(--xsy-page-x);
    @include flex-between;
    @include hairline-bottom;
    color: $color-text-primary;
  }
  .mine__logout {
    @include pressable;
    margin: $space-6 var(--xsy-page-x) 0;
    height: 88rpx;
    @include flex-center;
    @include card(0);
    color: $color-danger;
  }
</style>
