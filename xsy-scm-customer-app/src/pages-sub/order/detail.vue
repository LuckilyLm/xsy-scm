<template>
  <view class="detail">
    <view v-if="loading" class="detail__state">订单加载中…</view>
    <template v-else-if="order">
      <view class="detail-card detail-head">
        <view>
          <text class="detail-head__status">{{ statusText(order.status) }}</text>
          <text class="detail-head__no">订单号 {{ order.orderNo }}</text>
        </view>
        <text class="detail-head__amount">¥{{ order.orderedTotalAmount || '0.0000' }}</text>
      </view>

      <view v-if="order.receiverName" class="detail-card">
        <view class="detail-card__title">收货信息</view>
        <text class="detail__line">{{ order.receiverName }} {{ order.receiverPhone }}</text>
        <text class="detail__secondary">{{ order.address }}</text>
      </view>

      <view class="detail-card">
        <view class="detail-card__title">商品明细</view>
        <view v-for="item in order.items || []" :key="item.itemId" class="detail-item">
          <view class="detail-item__main">
            <text class="detail-item__name">{{ item.productName }}</text>
            <text class="detail__secondary">{{ item.specName || '默认规格' }} · {{ item.saleUnit }}</text>
          </view>
          <view class="detail-item__price">
            <text>¥{{ item.unitPrice || '0.0000' }}</text>
            <text class="detail__secondary">×{{ item.orderedQuantity }}</text>
          </view>
        </view>
      </view>

      <view class="detail-card">
        <view class="detail-card__title">订单信息</view>
        <text class="detail__line">下单时间：{{ formatTime(order.createdAt) }}</text>
        <text v-if="order.expectDeliveryTime" class="detail__line">期望配送：{{ formatDate(order.expectDeliveryTime) }}</text>
        <text v-if="order.remark" class="detail__line">订单备注：{{ order.remark }}</text>
      </view>
    </template>
    <view v-else class="detail__state">订单不存在或已不可查看</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { smartSentry } from '@/lib/smart-sentry';

  const orderId = ref(null);
  const order = ref(null);
  const loading = ref(false);

  onLoad((options) => {
    orderId.value = Number(options?.orderId) || null;
    load();
  });
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });

  async function load() {
    if (!orderId.value || loading.value) return;
    loading.value = true;
    try {
      const response = await mallOrderApi.getDetail(orderId.value);
      order.value = response.data || null;
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '订单加载失败', icon: 'none' });
    } finally {
      loading.value = false;
    }
  }

  function statusText(status) {
    return { DRAFT: '草稿', PENDING: '待确认', CONFIRMED: '已确认', CANCELLED: '已取消' }[status] || status || '-';
  }
  function formatDate(value) { return value ? String(value).slice(0, 10) : ''; }
  function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : ''; }
</script>

<style lang="scss" scoped>
  .detail { min-height: 100vh; padding: $space-3 $space-4 $space-8; background: $color-bg-page; box-sizing: border-box; }
  .detail__state { padding-top: 240rpx; text-align: center; color: $color-text-tertiary; }
  .detail-card { margin-bottom: $space-3; padding: $space-4; @include card(0); }
  .detail-card__title { margin-bottom: $space-3; color: $color-text-primary; font-size: $font-size-base; font-weight: $font-weight-bold; }
  .detail-head { @include flex-between; }
  .detail-head__status { display: block; color: $color-primary; font-size: $font-size-xl; font-weight: $font-weight-bold; }
  .detail-head__no { display: block; margin-top: $space-1; color: $color-text-tertiary; font-size: $font-size-sm; }
  .detail-head__amount { color: $color-danger; font-size: $font-size-xl; font-weight: $font-weight-bold; }
  .detail__line, .detail__secondary { display: block; }
  .detail__line { margin-top: $space-2; color: $color-text-secondary; font-size: $font-size-sm; }
  .detail__secondary { margin-top: $space-1; color: $color-text-tertiary; font-size: $font-size-sm; }
  .detail-item { @include flex-between; padding: $space-3 0; @include hairline-bottom; }
  .detail-item__main { flex: 1; min-width: 0; }
  .detail-item__name { color: $color-text-primary; font-size: $font-size-base; font-weight: $font-weight-medium; }
  .detail-item__price { margin-left: $space-3; text-align: right; color: $color-danger; font-size: $font-size-sm; }
</style>
