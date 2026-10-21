<template>
  <view class="coupons" :style="themeStyle">
    <view class="coupons__nav" @click="goMine">查看我的优惠券 ›</view>
    <view v-if="loading && !rows.length" class="coupons__state">优惠券加载中…</view>
    <view v-else-if="error && !rows.length" class="coupons__state">{{ error }}<text class="coupons__retry" @click="load(true)">重试</text></view>
    <view v-else-if="!rows.length" class="coupons__state">当前暂无可领优惠券</view>
    <MallCouponCard
      v-for="coupon in rows"
      :key="coupon.couponId"
      :coupon="coupon"
      :action-label="claimingId === coupon.couponId ? '领取中…' : coupon.claimableNow ? '领取' : '已达上限'"
      :disabled="!coupon.claimableNow || claimingId !== null"
      @action="claim"
    />
    <view v-if="rows.length" class="coupons__bottom">{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
    <view v-if="error && rows.length" class="coupons__retry" @click="load(false)">{{ error }}，点击重试</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
  import MallCouponCard from '@/components/business/mall-coupon-card.vue';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { smartSentry } from '@/lib/smart-sentry';

  const { themeStyle } = useMallTheme();

  const PAGE_SIZE = 20;
  const rows = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const claimingId = ref(null);
  const error = ref('');
  const claimKeyPrefix = 'MALL_COUPON_CLAIM_';
  let requestSeq = 0;
  onLoad(() => load(true));
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value && !loading.value) load(false);
  });

  async function load(reset) {
    if (!reset && loading.value) return;
    const seq = ++requestSeq;
    if (reset) {
      rows.value = [];
      pageNum.value = 0;
      hasMore.value = true;
    }
    loading.value = true;
    error.value = '';
    try {
      const nextPage = pageNum.value + 1;
      const page = (await mallPromotionApi.claimableCoupons({ pageNum: nextPage, pageSize: PAGE_SIZE })).data || {};
      if (seq !== requestSeq) return;
      rows.value = reset ? page.list || [] : rows.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '优惠券加载失败';
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }

  async function claim(coupon) {
    if (claimingId.value !== null || !coupon.claimableNow) return;
    claimingId.value = coupon.couponId;
    const storageKey = `${claimKeyPrefix}${coupon.couponId}`;
    const key = uni.getStorageSync(storageKey) || `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    uni.setStorageSync(storageKey, key);
    try {
      await mallPromotionApi.claimCoupon(coupon.couponId, key);
      uni.removeStorageSync(storageKey);
      uni.showToast({ title: '领取成功', icon: 'success' });
      await load(true);
    } catch (failure) {
      smartSentry.captureError(failure);
      uni.showToast({ title: failure?.message || '领取失败，请重试', icon: 'none' });
    } finally {
      claimingId.value = null;
    }
  }

  function goMine() {
    uni.navigateTo({ url: '/pages-sub/promotion/my' });
  }
</script>

<style lang="scss" scoped>
  .coupons {
    @include viewport-min-height;
    padding-bottom: $space-8;
    background: $color-bg-page;
  }
  .coupons__nav {
    padding: $space-4;
    background: $color-bg-card;
    color: var(--mall-primary-ink, #12833c);
    text-align: right;
    font-size: $font-size-sm;
  }
  .coupons__state {
    padding: 220rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .coupons__retry {
    display: block;
    padding: $space-4;
    text-align: center;
    color: var(--mall-primary-ink, #12833c);
  }
  .coupons__bottom {
    padding: $space-5;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
</style>
