<template>
  <view class="orders">
    <view v-if="loading && !orders.length" class="orders__state">订单加载中…</view>
    <view v-else-if="!orders.length" class="orders__state">暂无订单</view>
    <view v-for="order in orders" :key="order.orderId" class="order-card" @click="goDetail(order.orderId)">
      <view class="order-card__head">
        <text class="order-card__no">{{ order.orderNo }}</text>
        <text class="order-card__status">{{ statusText(order.status) }}</text>
      </view>
      <view class="order-card__meta">
        <text>{{ formatTime(order.createdAt) }}</text>
        <text v-if="order.expectDeliveryTime">期望配送 {{ formatDate(order.expectDeliveryTime) }}</text>
      </view>
      <view class="order-card__foot">
        <text class="order-card__label">订单金额</text>
        <text class="order-card__amount">¥{{ order.orderedTotalAmount || '0.0000' }}</text>
      </view>
    </view>
    <view v-if="loading && orders.length" class="orders__more">加载中…</view>
    <view v-else-if="orders.length && !hasMore" class="orders__more">没有更多订单了</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { smartSentry } from '@/lib/smart-sentry';

  const PAGE_SIZE = 10;
  const orders = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);

  onShow(() => load(true));
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value) load(false);
  });

  async function load(reset) {
    if (loading.value) return;
    loading.value = true;
    try {
      const nextPage = reset ? 1 : pageNum.value + 1;
      const response = await mallOrderApi.getList({ pageNum: nextPage, pageSize: PAGE_SIZE });
      const page = response.data || {};
      orders.value = reset ? page.list || [] : orders.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '订单加载失败', icon: 'none' });
    } finally {
      loading.value = false;
    }
  }

  function goDetail(orderId) {
    uni.navigateTo({ url: `/pages-sub/order/detail?orderId=${orderId}` });
  }

  function statusText(status) {
    return { DRAFT: '草稿', PENDING: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }[status] || status || '-';
  }

  function formatDate(value) {
    return value ? String(value).slice(0, 10) : '';
  }

  function formatTime(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '';
  }
</script>

<style lang="scss" scoped>
  .orders { min-height: 100vh; padding: $space-3 $space-4 $space-8; background: $color-bg-page; box-sizing: border-box; }
  .orders__state { padding-top: 240rpx; text-align: center; color: $color-text-tertiary; font-size: $font-size-base; }
  .orders__more { padding: $space-4 0; text-align: center; color: $color-text-tertiary; font-size: $font-size-sm; }
  .order-card { margin-bottom: $space-3; padding: $space-4; @include card(0); }
  .order-card__head, .order-card__foot { @include flex-between; }
  .order-card__no { color: $color-text-primary; font-size: $font-size-base; font-weight: $font-weight-bold; }
  .order-card__status { color: $color-primary; font-size: $font-size-sm; }
  .order-card__meta { display: flex; flex-direction: column; margin-top: $space-3; color: $color-text-tertiary; font-size: $font-size-sm; }
  .order-card__meta text + text { margin-top: $space-1; }
  .order-card__foot { margin-top: $space-3; padding-top: $space-3; border-top: 1px solid $color-divider; }
  .order-card__label { color: $color-text-secondary; font-size: $font-size-sm; }
  .order-card__amount { color: $color-danger; font-size: $font-size-lg; font-weight: $font-weight-bold; }
</style>
