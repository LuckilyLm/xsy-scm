<template>
  <view class="bill" :style="themeStyle">
    <view v-if="loading && !record" class="bill__state">账单详情加载中…</view>
    <view v-else-if="!record" class="bill__state">
      <text>{{ error || '账单不存在或已不可查看' }}</text>
      <text v-if="error" class="bill__retry" @click="load">重试</text>
    </view>
    <template v-else>
      <view class="bill__hero">
        <text class="bill__hero-label">未结金额</text>
        <text class="bill__hero-amount">¥{{ displayMoney(record.openAmount) }}</text>
        <text class="bill__hero-state">{{ receivableStateText(record.settleState) }}<text v-if="record.overdue"> · 已逾期</text></text>
      </view>
      <view class="bill__card">
        <view class="bill__row"
          ><text>账单编号</text><text>{{ record.receivableNo }}</text></view
        >
        <view class="bill__row"
          ><text>原始应收</text><text>¥{{ displayMoney(record.amount) }}</text></view
        >
        <view class="bill__row"
          ><text>退货红字冲减</text><text>¥{{ displayMoney(record.redAmount) }}</text></view
        >
        <view class="bill__row"
          ><text>有效核销</text><text>¥{{ displayMoney(record.writtenOffAmount) }}</text></view
        >
        <view class="bill__row"
          ><text>到期日</text><text>{{ formatDate(record.dueDate) || '未设置' }}</text></view
        >
        <view class="bill__row"
          ><text>应收发生时间</text><text>{{ formatDateTime(record.eventAt) }}</text></view
        >
      </view>
      <view v-if="record.orderId" class="bill__order" @click="goOrder">
        <text>关联订单 {{ record.orderNo || '' }}</text>
        <view class="bill__order-link xsy-chevron">
          <text>查看订单</text>
          <uni-icons type="right" :size="14" :color="COLOR_PRIMARY" />
        </view>
      </view>
      <text class="bill__note">账单金额依据正式应收、红字和核销记录计算。钱包余额不会自动抵扣应收。</text>
    </template>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { mallFundsApi } from '@/api/mall/funds-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { formatDate, formatDateTime } from '@/utils/order-display';
  import { displayMoney } from '@/utils/money-display';
  import { receivableStateText } from '@/utils/funds-display';
  import { COLOR_PRIMARY } from '@/constants/theme-color-const';

  const { themeStyle } = useMallTheme();

  const receivableId = ref(null);
  const record = ref(null);
  const loading = ref(false);
  const error = ref('');
  let requestSeq = 0;

  onLoad((options) => {
    receivableId.value = Number(options?.receivableId) || null;
    load();
  });
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });

  async function load() {
    if (!receivableId.value) return;
    const seq = ++requestSeq;
    loading.value = true;
    error.value = '';
    try {
      const response = await mallFundsApi.receivable(receivableId.value);
      if (seq === requestSeq) record.value = response.data;
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '账单详情加载失败';
      record.value = null;
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }
  function goOrder() {
    uni.navigateTo({ url: `/pages-sub/order/detail?orderId=${record.value.orderId}` });
  }
</script>

<style lang="scss" scoped>
  .bill {
    @include viewport-min-height;
    padding: $space-4 $space-4 $space-8;
    box-sizing: border-box;
    background: $color-bg-page;
  }
  .bill__state {
    padding: 240rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .bill__retry {
    display: block;
    margin-top: $space-4;
    color: var(--mall-primary-ink, #12833c);
  }
  .bill__hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: $space-6;
    border-radius: $radius-lg;
    background: $color-bg-card;
  }
  .bill__hero-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .bill__hero-amount {
    margin-top: $space-2;
    font-size: 64rpx;
    font-weight: $font-weight-bold;
    font-variant-numeric: tabular-nums;
  }
  .bill__hero-state {
    margin-top: $space-2;
    color: $color-success;
    font-size: $font-size-sm;
  }
  .bill__card {
    margin-top: $space-4;
    padding: 0 $space-4;
    @include card(0);
  }
  .bill__row {
    min-height: 96rpx;
    @include flex-between;
    @include hairline-bottom;
    gap: $space-3;
  }
  .bill__row text:first-child {
    flex-shrink: 0;
    color: $color-text-secondary;
  }
  .bill__row text:last-child {
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
  .bill__order {
    margin-top: $space-4;
    padding: $space-4;
    @include flex-between;
    @include card(0);
    color: var(--mall-primary-ink, #12833c);
  }
  .bill__note {
    display: block;
    margin-top: $space-4;
    color: $color-text-secondary;
    font-size: $font-size-sm;
    line-height: 1.6;
  }
</style>
