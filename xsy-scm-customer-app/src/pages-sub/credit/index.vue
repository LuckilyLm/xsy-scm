<template>
  <view class="credit">
    <view v-if="loading && !summary" class="credit__state">账期信息加载中…</view>
    <view v-else-if="error && !summary" class="credit__state">
      <text>{{ error }}</text><text class="credit__retry" @click="load">重试</text>
    </view>
    <template v-else-if="summary">
      <view class="credit__hero">
        <text class="credit__hero-label">本客户应收未结</text>
        <text class="credit__hero-amount">¥{{ displayAmount(summary.openReceivableAmount) }}</text>
        <text class="credit__hero-hint">来自已生成的应收账单，不含尚未转应收的订单</text>
      </view>
      <view v-if="summary.earliestOverdueDate" class="credit__alert">
        最早逾期账单到期日 {{ formatDate(summary.earliestOverdueDate) }}，请查看未结清账单
      </view>
      <view class="credit__card">
        <view class="credit__row"><text>已确认订单占用</text><text>¥{{ displayAmount(summary.confirmedOrderAmount) }}</text></view>
        <view class="credit__row">
          <text>授信额度</text>
          <text>{{ summary.sharedSettlement ? '共享额度暂不展示' : summary.creditLimitVisible ? `¥${displayAmount(summary.creditLimit)}` : '未设置明确额度' }}</text>
        </view>
        <view v-if="!summary.sharedSettlement && summary.creditPeriodType" class="credit__row">
          <text>账期规则</text><text>{{ periodText(summary) }}</text>
        </view>
        <text v-if="summary.sharedSettlement" class="credit__note">这里只展示本客户账单，集团整体授信需另行授权。</text>
      </view>
      <view class="credit__card">
        <view class="credit__head" @click="goReceivables"><text>最近账单</text><text class="credit__link">全部账单 ›</text></view>
        <view v-if="!recent.length" class="credit__empty">暂无应收账单</view>
        <view v-for="row in recent" :key="row.receivableId" class="credit__bill" @click="goDetail(row.receivableId)">
          <view class="credit__bill-main"><text>{{ row.orderNo || row.receivableNo }}</text><text class="credit__bill-state">{{ receivableStateText(row.settleState) }} ›</text></view>
          <text class="credit__bill-sub">未结 ¥{{ displayAmount(row.openAmount) }} · 到期 {{ formatDate(row.dueDate) || '未设置' }}</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app';
  import { mallFundsApi } from '@/api/mall/funds-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { displayAmount, formatDate } from '@/utils/order-display';
  import { receivableStateText } from '@/utils/funds-display';

  const summary = ref(null);
  const recent = ref([]);
  const loading = ref(false);
  const error = ref('');
  let requestSeq = 0;
  let showed = false;

  onLoad(() => load());
  onShow(() => {
    if (showed) load();
    showed = true;
  });
  onPullDownRefresh(async () => {
    await load();
    uni.stopPullDownRefresh();
  });

  async function load() {
    const seq = ++requestSeq;
    loading.value = true;
    error.value = '';
    try {
      const [summaryResponse, listResponse] = await Promise.all([
        mallFundsApi.creditSummary(),
        mallFundsApi.receivables({ pageNum: 1, pageSize: 5 }),
      ]);
      if (seq !== requestSeq) return;
      summary.value = summaryResponse.data;
      recent.value = listResponse.data?.list || [];
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '账期信息加载失败';
      if (summary.value) uni.showToast({ title: error.value, icon: 'none' });
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }

  function periodText(value) {
    if (value.creditPeriodType === 'BY_AMOUNT') return `达到 ¥${displayAmount(value.creditAmountThreshold)} 结算`;
    if (value.creditPeriodType === 'BY_TIME' && value.creditPeriodUnit === 'DAY') return `${value.creditPeriodValue} 天`;
    if (value.creditPeriodType === 'BY_TIME' && value.creditPeriodUnit === 'MONTH') {
      return `${value.creditPeriodValue} 个月${value.settleDay ? ` · 每月 ${value.settleDay} 日` : ''}`;
    }
    return '请咨询平台确认';
  }
  function goReceivables() { uni.navigateTo({ url: '/pages-sub/credit/receivables' }); }
  function goDetail(id) { uni.navigateTo({ url: `/pages-sub/credit/detail?receivableId=${id}` }); }
</script>

<style lang="scss" scoped>
  .credit { min-height: 100vh; padding: $space-4 $space-4 $space-8; box-sizing: border-box; background: $color-bg-page; }
  .credit__state { padding: $space-8 $space-4; text-align: center; color: $color-text-secondary; }
  .credit__retry { display: block; margin-top: $space-4; color: $color-primary; }
  .credit__hero { display: flex; flex-direction: column; padding: $space-6; border-radius: $radius-lg; background: $color-primary; color: $color-text-inverse; }
  .credit__hero-label { font-size: $font-size-sm; opacity: .9; }
  .credit__hero-amount { margin-top: $space-3; font-size: 64rpx; font-weight: $font-weight-bold; font-variant-numeric: tabular-nums; }
  .credit__hero-hint { margin-top: $space-4; font-size: $font-size-sm; opacity: .9; }
  .credit__alert { margin-top: $space-3; padding: $space-3 $space-4; border-radius: $radius-md; background: $color-warning-light; color: $color-text-primary; font-size: $font-size-sm; line-height: 1.5; }
  .credit__card { margin-top: $space-4; padding: 0 $space-4; @include card(0); }
  .credit__row { min-height: 100rpx; @include flex-between; @include hairline-bottom; gap: $space-3; }
  .credit__row text:first-child { flex-shrink: 0; color: $color-text-secondary; }
  .credit__row text:last-child { text-align: right; font-variant-numeric: tabular-nums; }
  .credit__note { display: block; padding: $space-3 0; color: $color-text-secondary; font-size: $font-size-sm; line-height: 1.5; }
  .credit__head { height: 104rpx; @include flex-between; font-size: $font-size-lg; font-weight: $font-weight-bold; }
  .credit__link { color: $color-primary; font-size: $font-size-sm; font-weight: $font-weight-regular; }
  .credit__empty { padding: $space-8 0; text-align: center; color: $color-text-tertiary; }
  .credit__bill { padding: $space-3 0; @include hairline-bottom; }
  .credit__bill-main { @include flex-between; font-weight: $font-weight-medium; }
  .credit__bill-state { color: $color-primary; font-size: $font-size-sm; }
  .credit__bill-sub { display: block; margin-top: $space-2; color: $color-text-secondary; font-size: $font-size-sm; }
</style>
