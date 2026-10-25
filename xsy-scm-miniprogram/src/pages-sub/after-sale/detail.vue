<template>
  <view class="ad" :style="themeStyle">
    <view v-if="loading && !record" class="ad__state">售后单加载中…</view>
    <view v-else-if="!record" class="ad__state">售后单不存在或已不可查看</view>

    <template v-else>
      <view class="ad-card ad-head">
        <text class="ad-head__status" :class="`ad-head__status--${afterSaleStatusLevel(record.displayStatus)}`">
          {{ afterSaleStatusText(record.displayStatus) }}
        </text>
        <text class="ad-head__no">售后单号 {{ record.returnNo }}</text>
        <text v-if="record.displayStatus === 'REVIEWING'" class="ad-head__hint">已提交，平台审核中</text>
        <text v-else-if="record.displayStatus === 'REFUNDING'" class="ad-head__hint">审核通过，退款处理中</text>
        <text v-else-if="record.displayStatus === 'REJECTED' && record.decisionReason" class="ad-head__hint">
          驳回原因：{{ record.decisionReason }}
        </text>
      </view>

      <!-- 售后进度：节点与 done 都由服务端按正式事实给出，客户端不推导 -->
      <view v-if="(record.progress || []).length" class="ad-card">
        <view class="ad-card__title">售后进度</view>
        <view v-for="(node, index) in record.progress" :key="node.code" class="ad-step" :class="{ 'ad-step--pending': !node.done }">
          <view class="ad-step__dot-wrap">
            <view class="ad-step__dot" :class="{ 'ad-step__dot--done': node.done }" />
            <view v-if="index < record.progress.length - 1" class="ad-step__line" />
          </view>
          <view class="ad-step__main">
            <text class="ad-step__title" :class="{ 'ad-step__title--pending': !node.done }">{{ node.text }}</text>
            <text v-if="node.time" class="ad__sub">{{ formatDateTime(node.time) }}</text>
            <text v-if="node.detail" class="ad-step__detail">{{ node.detail }}</text>
          </view>
        </view>
      </view>

      <view class="ad-card">
        <view class="ad-card__title">退货商品</view>
        <view v-for="item in record.items || []" :key="item.returnItemId" class="ad-item">
          <view class="ad-item__thumb">
            <image v-if="item.primaryImageUrl" class="ad-item__image" :src="item.primaryImageUrl" mode="aspectFill" lazy-load />
            <view v-else class="ad-item__thumb-fallback">{{ (item.productName || '商').slice(0, 1) }}</view>
          </view>
          <view class="ad-item__main">
            <text class="ad-item__name">{{ item.productName }}</text>
            <text class="ad__sub">{{ item.specName || '默认规格' }} · {{ item.unit }}</text>
            <text class="ad__sub">申请 {{ displayQuantity(item.requestedQuantity) }}{{ item.unit }} × ¥{{ displayMoney(item.lockedUnitPrice) }}</text>
            <text v-if="item.approvedQuantity" class="ad-item__approved"> 核定 {{ displayQuantity(item.approvedQuantity) }}{{ item.unit }} </text>
          </view>
          <view class="ad-item__amount">
            <text class="ad-item__amount-value"> ¥{{ displayMoney(item.approvedQuantity ? item.approvedAmount : item.estimatedAmount) }} </text>
            <text class="ad-item__amount-label">{{ item.approvedQuantity ? '核定' : '预估' }}</text>
          </view>
        </view>
      </view>

      <view v-if="record.refund" class="ad-card">
        <view class="ad-card__title">退款详情</view>
        <view class="ad__row">
          <text class="ad__row-label">退款金额</text>
          <text class="ad__row-value ad__row-value--strong">¥{{ displayMoney(record.refund.refundAmount) }}</text>
        </view>
        <view class="ad__row">
          <text class="ad__row-label">退款状态</text>
          <text class="ad__row-value">{{ record.displayStatus === 'REFUNDED' ? '已退款' : '退款处理中' }}</text>
        </view>
        <view v-if="record.refund.completedAt" class="ad__row">
          <text class="ad__row-label">退款完成时间</text>
          <text class="ad__row-value">{{ formatDateTime(record.refund.completedAt) }}</text>
        </view>
        <template v-if="(record.refund.allocations || []).length">
          <view class="ad-card__subtitle">原路退回明细</view>
          <view v-for="(allocation, index) in record.refund.allocations" :key="index" class="ad__row">
            <text class="ad__row-label">{{ refundMethodText(allocation.method) }}</text>
            <text class="ad__row-value">
              ¥{{ displayMoney(allocation.allocatedAmount) }} · {{ allocationStatusText(allocation.status) }}
              <text v-if="allocation.succeededAt"> · {{ formatDateTime(allocation.succeededAt) }}</text>
            </text>
          </view>
        </template>
      </view>

      <view class="ad-card">
        <view class="ad-card__title">申请信息</view>
        <view class="ad__row">
          <text class="ad__row-label">售后原因</text>
          <text class="ad__row-value">{{ record.reason }}</text>
        </view>
        <view class="ad__row" @click="goOrder">
          <text class="ad__row-label">关联订单</text>
          <view class="ad__row-value ad__row-value--link xsy-chevron">
            <text>{{ record.orderNo || record.orderId }}</text>
            <uni-icons type="right" :size="14" :color="COLOR_PRIMARY" />
          </view>
        </view>
        <view class="ad__row">
          <text class="ad__row-label">申请金额（预估）</text>
          <text class="ad__row-value">¥{{ displayMoney(record.requestedAmount) }}</text>
        </view>
        <view v-if="record.approvedAt" class="ad__row">
          <text class="ad__row-label">核定退款金额</text>
          <text class="ad__row-value ad__row-value--strong">¥{{ displayMoney(record.approvedAmount) }}</text>
        </view>
        <view class="ad__row">
          <text class="ad__row-label">申请时间</text>
          <text class="ad__row-value">{{ formatDateTime(record.createdAt) }}</text>
        </view>
      </view>

      <view v-if="record.cancelable" class="ad-bar">
        <view class="ad-bar__btn" @click="cancelApply">撤销申请</view>
      </view>
    </template>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { mallAfterSaleApi } from '@/api/mall/after-sale-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { displayQuantity, formatDateTime } from '@/utils/order-display';
  import { displayMoney } from '@/utils/money-display';
  import { afterSaleStatusLevel, afterSaleStatusText, allocationStatusText, refundMethodText } from '@/utils/after-sale-display';
  import { COLOR_PRIMARY } from '@/constants/theme-color-const';

  const { themeStyle } = useMallTheme();

  const returnId = ref(null);
  const record = ref(null);
  const loading = ref(false);

  onLoad((options) => {
    returnId.value = Number(options?.returnId) || null;
    load();
  });
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });

  async function load() {
    if (!returnId.value || loading.value) return;
    loading.value = true;
    try {
      const response = await mallAfterSaleApi.getDetail(returnId.value);
      record.value = response.data || null;
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '售后单加载失败', icon: 'none' });
    } finally {
      loading.value = false;
    }
  }

  function cancelApply() {
    uni.showModal({
      title: '撤销售后申请',
      content: '撤销后如需售后要重新提交，确认撤销？',
      success: ({ confirm }) => {
        if (confirm) doCancel();
      },
    });
  }

  async function doCancel() {
    try {
      // 幂等键与售后单绑定：网络重试复用同一个键，不会重复撤销
      await mallAfterSaleApi.cancel(returnId.value, `aftersale-cancel-${returnId.value}`);
      uni.showToast({ title: '售后申请已撤销', icon: 'none' });
      load();
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '撤销失败，请稍后重试', icon: 'none' });
    }
  }

  function goOrder() {
    if (!record.value?.orderId) return;
    uni.navigateTo({ url: `/pages-sub/order/detail?orderId=${record.value.orderId}` });
  }
</script>

<style lang="scss" scoped>
  .ad {
    @include viewport-min-height;
    background: $color-bg-page;
    box-sizing: border-box;
    padding-bottom: 160rpx;
  }
  .ad__state {
    padding-top: 240rpx;
    text-align: center;
    color: $color-text-tertiary;
  }
  .ad-card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .ad-card__title {
    margin-bottom: $space-2;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .ad-card__subtitle {
    margin-top: $space-3;
    color: $color-text-secondary;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .ad-head__status {
    display: block;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
  }
  .ad-head__status--warn {
    color: $color-warning;
  }
  .ad-head__status--primary {
    color: $color-success;
  }
  .ad-head__status--ok {
    color: $color-success;
  }
  .ad-head__status--danger {
    color: $color-danger;
  }
  .ad-head__status--off {
    color: $color-text-tertiary;
  }
  .ad-head__no {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .ad-head__hint {
    display: block;
    margin-top: $space-2;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .ad__sub {
    display: block;
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .ad__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-top: $space-2;
  }
  .ad__row-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .ad__row-value {
    flex: 1;
    margin-left: $space-3;
    text-align: right;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .ad__row-value--strong {
    color: $color-danger;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .ad__row-value--link {
    color: var(--mall-primary-ink, #12833c);
  }
  .ad-step {
    display: flex;
    align-items: flex-start;
    margin-top: $space-2;
  }
  .ad-step__dot-wrap {
    width: 24rpx;
    flex-shrink: 0;
    align-self: stretch;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .ad-step__dot {
    width: 16rpx;
    height: 16rpx;
    margin-top: 8rpx;
    border-radius: 50%;
    background: $color-border;
  }
  .ad-step__dot--done {
    background: $color-success;
  }
  .ad-step__line {
    flex: 1;
    width: 2rpx;
    margin-top: 4rpx;
    background: $color-divider;
  }
  .ad-step__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .ad-step__title {
    color: $color-text-primary;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .ad-step__title--pending {
    color: $color-text-placeholder;
    font-weight: $font-weight-regular;
  }
  .ad-step__detail {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .ad-item {
    display: flex;
    align-items: flex-start;
    padding: $space-3 0;
    @include hairline-bottom;
  }
  .ad-item__thumb {
    width: 112rpx;
    height: 112rpx;
    flex-shrink: 0;
    border-radius: $radius-md;
    overflow: hidden;
    background: $color-bg-hover;
  }
  .ad-item__image {
    width: 100%;
    height: 100%;
  }
  .ad-item__thumb-fallback {
    width: 100%;
    height: 100%;
    @include flex-center;
    color: $color-text-tertiary;
  }
  .ad-item__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .ad-item__name {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
  }
  .ad-item__approved {
    display: block;
    margin-top: $space-1;
    color: $color-success;
    font-size: $font-size-xs;
  }
  .ad-item__amount {
    margin-left: $space-3;
    text-align: right;
  }
  .ad-item__amount-value {
    display: block;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .ad-item__amount-label {
    display: block;
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .ad-bar {
    @include fixed-bottom-bar;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    height: 108rpx;
    padding: 0 $space-4;
  }
  .ad-bar__btn {
    min-width: 180rpx;
    height: 72rpx;
    @include flex-center;
    border: 1px solid $color-border;
    border-radius: $radius-pill;
    color: $color-text-secondary;
    font-size: $font-size-base;
  }
</style>
