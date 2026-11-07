<template>
  <view class="my-coupons" :style="themeStyle">
    <view class="my-coupons__tabs">
      <text
        v-for="item in COUPON_TABS"
        :key="item.value"
        class="my-coupons__tab"
        :class="{ 'my-coupons__tab--active': tab === item.value }"
        @click="setTab(item.value)"
        >{{ item.label }}</text
      >
    </view>
    <view v-if="loading && !rows.length" class="my-coupons__state">优惠券加载中…</view>
    <XsyEmpty v-else-if="error && !rows.length" tone="error" :title="error" action-text="重试" @action="load(true)" />
    <view v-else-if="!rows.length" class="my-coupons__empty">
      <XsyEmpty kind="coupons" :title="`暂无${currentTabLabel}优惠券`" />
    </view>
    <MallCouponCard
      v-for="coupon in rows"
      :key="coupon.id"
      :coupon="coupon"
      :action-label="selectMode && coupon.effectiveStatus === 'AVAILABLE' ? '选择' : ''"
      :disabled="coupon.effectiveStatus !== 'AVAILABLE'"
      :status-label="couponStatusText(coupon.effectiveStatus)"
      @action="selectCoupon"
    />
    <view v-if="rows.length" class="my-coupons__bottom">{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</view>
    <view v-if="error && rows.length" class="my-coupons__retry" @click="load(false)">{{ error }}，点击重试</view>
    <view class="my-coupons__bar">
      <view class="my-coupons__claim" hover-class="xsy-press-button--active" @click="goClaimable">前往领券中心</view>
    </view>
  </view>
</template>

<script setup>
  import XsyEmpty from '@/components/common/xsy-empty.vue';
  import { computed, ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
  import MallCouponCard from '@/pages-sub/shared/components/business/mall-coupon-card.vue';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { xsySentry } from '@/lib/xsy-sentry';
  import { COUPON_TABS, couponStatusText } from '@/pages-sub/shared/utils/promotion-display';

  const { themeStyle } = useMallTheme();

  const PAGE_SIZE = 20;
  const tab = ref('AVAILABLE');
  const selectMode = ref(false);
  const rows = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const error = ref('');
  const currentTabLabel = computed(() => COUPON_TABS.find((item) => item.value === tab.value)?.label || '');
  let requestSeq = 0;
  onLoad((options) => {
    selectMode.value = String(options?.select || '') === '1';
  });
  onShow(() => load(true));
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value && !loading.value) load(false);
  });

  function setTab(value) {
    if (tab.value === value) return;
    tab.value = value;
    load(true);
  }

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
      const page = (await mallPromotionApi.myCoupons({ pageNum: nextPage, pageSize: PAGE_SIZE, tab: tab.value })).data || {};
      if (seq !== requestSeq) return;
      rows.value = reset ? page.list || [] : rows.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (failure) {
      if (seq !== requestSeq) return;
      xsySentry.captureError(failure);
      error.value = failure?.message || '优惠券加载失败';
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }

  function selectCoupon(coupon) {
    if (!selectMode.value || tab.value !== 'AVAILABLE' || coupon.effectiveStatus !== 'AVAILABLE') return;
    const pages = getCurrentPages();
    const channel = pages[pages.length - 1]?.getOpenerEventChannel?.();
    channel?.emit('couponSelected', { id: coupon.id, name: coupon.couponName });
    uni.navigateBack();
  }
  function goClaimable() {
    uni.navigateTo({ url: '/pages-sub/promotion/coupons' });
  }
</script>

<style lang="scss" scoped>
  .my-coupons {
    @include viewport-min-height;
    @include content-clear-bottom(68px, $space-4);
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    background: $color-bg-page;
  }
  .my-coupons__empty {
    flex: 1;
    display: flex;
    justify-content: center;
    flex-direction: column;
  }
  .my-coupons__bar {
    @include fixed-bottom-bar;
    @include action-bar-spacing;
  }
  .my-coupons__claim {
    @include flex-center;
    height: 44px;
    border-radius: $radius-pill;
    background: var(--mall-action-gradient, var(--mall-primary, #16a34a));
    color: white;
    font-size: $font-size-base;
  }
  .my-coupons__tabs {
    display: flex;
    justify-content: space-around;
    padding: $space-4;
    background: $color-bg-card;
  }
  .my-coupons__tab {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .my-coupons__tab--active {
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-bold;
  }
  .my-coupons__state {
    padding: 220rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .my-coupons__retry {
    display: block;
    padding: $space-4;
    text-align: center;
    color: var(--mall-primary-ink, #12833c);
  }
  .my-coupons__bottom {
    padding: $space-5;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
</style>
