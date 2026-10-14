<template>
  <view class="activities">
    <view v-if="loading" class="activities__state">活动加载中…</view>
    <view v-else-if="error" class="activities__state">{{ error }}<text class="activities__retry" @click="load">重试</text></view>
    <view v-else-if="!rows.length" class="activities__state">当前暂无可参加的活动</view>
    <view v-for="item in rows" :key="item.id" class="activities__card" @click="openProduct(item)">
      <view class="activities__head"
        ><text class="activities__name">{{ item.activityName }}</text
        ><text class="activities__type">{{ item.activityTypeLabel }}</text></view
      >
      <text class="activities__summary">{{ item.summary }}</text>
      <text v-if="item.validTo" class="activities__time">活动至 {{ formatDateTime(item.validTo) }}</text>
      <text v-if="item.product" class="activities__link">查看参与商品 ›</text>
    </view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { mallPromotionApi } from '@/api/mall/promotion-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { formatDateTime } from '@/utils/order-display';

  const rows = ref([]);
  const loading = ref(false);
  const error = ref('');
  onLoad(load);
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });

  async function load() {
    loading.value = true;
    error.value = '';
    try {
      rows.value = (await mallPromotionApi.activities()).data || [];
    } catch (failure) {
      smartSentry.captureError(failure);
      rows.value = [];
      error.value = failure?.message || '活动加载失败';
    } finally {
      loading.value = false;
    }
  }

  function openProduct(item) {
    if (item.product?.skuId) uni.navigateTo({ url: `/pages-sub/product/detail?skuId=${item.product.skuId}` });
  }
</script>

<style lang="scss" scoped>
  .activities {
    min-height: 100vh;
    padding: $space-1 0 $space-8;
    background: $color-bg-page;
  }
  .activities__state {
    padding: 220rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .activities__retry {
    display: block;
    margin-top: $space-4;
    color: $color-primary;
  }
  .activities__card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .activities__head {
    @include flex-between;
    gap: $space-3;
  }
  .activities__name {
    color: $color-text-primary;
    font-weight: $font-weight-bold;
  }
  .activities__type {
    color: $color-primary;
    font-size: $font-size-xs;
    white-space: nowrap;
  }
  .activities__summary {
    display: block;
    margin-top: $space-3;
    color: $color-text-primary;
  }
  .activities__time {
    display: block;
    margin-top: $space-3;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .activities__link {
    display: block;
    margin-top: $space-3;
    color: $color-primary;
    font-size: $font-size-sm;
  }
</style>
