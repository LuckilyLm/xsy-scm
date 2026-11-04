<template>
  <view class="mine" :style="themeStyle">
    <view class="mine__header" :style="{paddingTop: statusBarHeight + 'px', paddingRight: headerRightGap + 'px'}">
      <view class="mine__avatar">{{ avatarText }}</view>
      <view class="mine__identity">
        <text class="mine__name">{{ userStore.customerName || '未登录' }}</text>
        <text class="mine__meta">{{ metaLine }}</text>
      </view>
      <text v-if="!userStore.isLogin" class="mine__login" @click="goLogin">登录</text>
    </view>
    <view class="mine__card">
      <text class="mine__store-name">{{ storeName }}</text>
      <view class="mine__benefits">
        <view
          class="mine__benefit-link"
          data-entry="coupons"
          aria-label="前往领券中心"
          hover-class="xsy-press--active"
          :hover-stay-time="80"
          @click="goCoupons"
        >
          <image class="mine__coupon-image" :src="MALL_SHORTCUT_IMAGES.coupons" mode="aspectFit" aria-hidden="true" />
          <text>领券中心</text>
        </view>
        <view
          class="mine__benefit-link"
          data-entry="my-coupons"
          aria-label="查看我的优惠券"
          hover-class="xsy-press--active"
          :hover-stay-time="80"
          @click="goMyCoupons"
        >
          <image class="mine__coupon-image" :src="MALL_PROFILE_IMAGES.myCoupons" mode="aspectFit" aria-hidden="true" />
          <text>我的优惠券</text>
        </view>
      </view>
      <text class="mine__group-title">常用功能</text>
      <view class="mine__grid">
        <view
          v-for="entry in primaryEntries"
          :key="entry.key"
          class="mine__entry"
          :data-entry="entry.key"
          hover-class="xsy-press--active"
          :hover-stay-time="80"
          @click="entry.onClick"
        >
          <view class="mine__entry-icon">
            <image class="mine__entry-image" :src="entry.image" mode="aspectFit" aria-hidden="true" />
          </view>
          <text class="mine__entry-text">{{ entry.label }}</text>
        </view>
      </view>
      <text class="mine__group-title mine__group-title--divided">服务与帮助</text>
      <view class="mine__grid">
        <view
          v-for="entry in serviceEntries"
          :key="entry.key"
          class="mine__entry"
          :data-entry="entry.key"
          hover-class="xsy-press--active"
          :hover-stay-time="80"
          @click="entry.onClick"
        >
          <view class="mine__entry-icon">
            <image v-if="entry.image" class="mine__entry-image" :src="entry.image" mode="aspectFit" aria-hidden="true" />
            <view v-else class="mine__entry-fallback"><uni-icons :type="entry.icon" :size="20" :color="theme.primary" /></view>
          </view>
          <text class="mine__entry-text">{{ entry.label }}</text>
        </view>
      </view>
    </view>
    <view v-if="userStore.isLogin" class="mine__logout" hover-class="xsy-press--active" :hover-stay-time="80" @click="logout">退出登录</view>
    <XsyTabBar :active="4" />
  </view>
</template>

<script setup>
import {computed} from 'vue';
import {useSystemLayout} from '@/composables/use-system-layout';
import {useMallTheme} from '@/composables/use-mall-theme';
import {useMallStorefront} from '@/composables/use-mall-storefront';
import {useUserStore} from '@/store/modules/system/user';
import XsyTabBar from '@/components/common/xsy-tab-bar.vue';
import {MALL_SHORTCUT_IMAGES, MALL_PROFILE_IMAGES} from '@/constants/mall-visual-const';

const {theme, themeStyle} = useMallTheme();
const {storeName, storeLink} = useMallStorefront();

const {statusBarHeight, menuButtonRect} = useSystemLayout();
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
  uni.navigateTo({url: '/pages/login/login'});
}
function goCart() {
  uni.switchTab({url: '/pages/cart/index'});
}
function goOrders() {
  uni.switchTab({url: '/pages/order/index'});
}
function goAddress() {
  uni.navigateTo({url: '/pages-sub/address/list'});
}
function goAfterSales() {
  userStore.isLogin ? uni.navigateTo({url: '/pages-sub/after-sale/list'}) : goLogin();
}
function goMyCoupons() {
  userStore.isLogin ? uni.navigateTo({url: '/pages-sub/promotion/my'}) : goLogin();
}
function goCoupons() {
  userStore.isLogin ? uni.navigateTo({url: '/pages-sub/promotion/coupons'}) : goLogin();
}
function changePassword() {
  userStore.isLogin ? uni.navigateTo({url: '/pages/account/password'}) : goLogin();
}
/** 单页内容不要求登录：条款、公告与售后规则对未登录顾客也应可查 */
function goPage(pageCode) {
  uni.navigateTo({url: `/pages-sub/page/index?pageCode=${pageCode}`});
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
    success: ({result}) => {
      const matched = /#\/t\/([^/?#]+)/.exec(result || '');
      const token = matched ? matched[1] : (result || '').trim();
      if (!token) {
        uni.showToast({title: '未识别到溯源码', icon: 'none'});
        return;
      }
      uni.navigateTo({url: `/pages-sub/trace/detail?token=${encodeURIComponent(token)}`});
    },
    fail: () => uni.showToast({title: '已取消扫码', icon: 'none'}),
  });
}

/* 常用功能和服务共用四列栅格，交付图片自带浅绿底面，文字保持独立可读。 */
const primaryEntries = [
  {key: 'orders', label: '我的订单', image: MALL_PROFILE_IMAGES.orders, onClick: goOrders},
  {key: 'cart', label: '购物车', image: MALL_PROFILE_IMAGES.cart, onClick: goCart},
  {key: 'address', label: '收货地址', image: MALL_PROFILE_IMAGES.address, onClick: goAddress},
  {key: 'after-sale', label: '售后服务', image: MALL_PROFILE_IMAGES.afterSale, onClick: goAfterSales},
];

const serviceEntries = computed(() => [
  {key: 'trace', label: '扫码溯源', image: MALL_PROFILE_IMAGES.trace, onClick: scanTrace},
  {key: 'announcement', label: '商城公告', image: MALL_PROFILE_IMAGES.announcement, onClick: () => goPage('ANNOUNCEMENT')},
  {key: 'about', label: '关于我们', image: MALL_PROFILE_IMAGES.about, onClick: () => goPage('ABOUT')},
  {key: 'terms', label: '服务条款', image: MALL_PROFILE_IMAGES.terms, onClick: () => goPage('TERMS')},
  {key: 'after-sale-rule', label: '售后规则', image: MALL_PROFILE_IMAGES.afterSaleRule, onClick: () => goPage('AFTER_SALE')},
  {key: 'password', label: '修改密码', image: MALL_PROFILE_IMAGES.password, onClick: changePassword},
  ...(storeLink.value
    ? [{key: 'store-link', label: '复制店铺链接', icon: 'link', onClick: () => uni.setClipboardData({data: storeLink.value})}]
    : []),
]);

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号？',
    success: async ({confirm}) => {
      if (!confirm) return;
      await userStore.logout();
      uni.reLaunch({url: '/pages/login/login'});
    },
  });
}
</script>

<style lang="scss" scoped>
.mine {
  @include viewport-min-height;
  padding-bottom: calc(var(--xsy-tabbar-h, 100rpx) + #{$space-8});
  background: var(--mall-surface-gradient, $color-bg-page);
}
.mine__header {
  @include flex-start;
  padding: $space-6 var(--xsy-page-x) $space-8;
  background: var(--mall-brand-gradient, var(--mall-primary, #16a34a));
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
.mine__group-title {
  display: block;
  padding: $space-4 var(--xsy-page-x) $space-1;
  color: $color-text-tertiary;
  font-size: $font-size-sm;
}
.mine__store-name {
  display: block;
  padding: $space-4 var(--xsy-page-x) 0;
  color: $color-text-primary;
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  @include ellipsis;
}
.mine__benefits {
  display: flex;
  margin: $space-3 var(--xsy-page-x) 0;
  border-radius: $radius-md;
  background: var(--mall-surface-gradient, var(--mall-primary-light, #e8f5ec));
  color: var(--mall-primary-ink, #12833c);
}
.mine__benefit-link {
  @include pressable;
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: $tap-min;
  align-items: center;
  justify-content: center;
  gap: $space-1;
  padding: $space-2;
  font-size: $font-size-sm;
  white-space: nowrap;
}
.mine__coupon-image {
  flex-shrink: 0;
  width: 80rpx;
  height: 80rpx;
}
/* 第二组标题上一道细分隔线，分组清楚但不抢眼 */
.mine__group-title--divided {
  margin-top: $space-2;
  padding-top: $space-4;
  border-top: 1px solid $color-border;
}
.mine__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: $space-2;
  padding: $space-1 var(--xsy-page-x) $space-3;
}
.mine__entry {
  @include pressable;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $space-1;
  padding: $space-2 0;
}
.mine__entry-icon {
  width: 100%;
  height: 144rpx;
  @include flex-center;
}
.mine__entry-image {
  width: 100%;
  height: 100%;
}
.mine__entry-fallback {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--mall-primary-light, #e8f5ec);
  @include flex-center;
}
.mine__entry-text {
  max-width: 100%;
  color: $color-text-primary;
  /* 常用入口保留完整短标签，较长的配置标签统一省略。 */
  font-size: $font-size-base;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
