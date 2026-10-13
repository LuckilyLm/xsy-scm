<template>
  <view class="bills">
    <scroll-view scroll-x class="bills__tabs">
      <view class="bills__tab-row">
        <text v-for="filter in RECEIVABLE_FILTERS" :key="filter.state" class="bills__tab"
              :class="{ 'bills__tab--active': state === filter.state }" @click="selectState(filter.state)">
          {{ filter.label }}
        </text>
      </view>
    </scroll-view>
    <view v-if="loading && !rows.length" class="bills__state">账单加载中…</view>
    <view v-else-if="error && !rows.length" class="bills__state"><text>{{ error }}</text><text class="bills__retry" @click="load(true)">重试</text></view>
    <view v-else-if="!rows.length" class="bills__state">暂无符合条件的账单</view>
    <view v-for="row in rows" :key="row.receivableId" class="bills__card" @click="goDetail(row.receivableId)">
      <view class="bills__head"><text>{{ row.receivableNo }}</text><text class="bills__status">{{ receivableStateText(row.settleState) }}</text></view>
      <text class="bills__order">订单 {{ row.orderNo || '—' }}</text>
      <view class="bills__amount"><text>未结金额</text><text>¥{{ displayAmount(row.openAmount) }}</text></view>
      <view class="bills__foot"><text>到期日 {{ formatDate(row.dueDate) || '未设置' }}</text><text v-if="row.overdue" class="bills__overdue">已逾期</text><text v-else>查看详情 ›</text></view>
    </view>
    <view v-if="rows.length" class="bills__bottom">{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多账单了' }}</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
  import { mallFundsApi } from '@/api/mall/funds-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { displayAmount, formatDate } from '@/utils/order-display';
  import { RECEIVABLE_FILTERS, receivableStateText } from '@/utils/funds-display';

  const PAGE_SIZE = 20;
  const state = ref('');
  const rows = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const error = ref('');
  let requestSeq = 0;

  onLoad(() => load(true));
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value && !loading.value) load(false);
  });

  function selectState(next) {
    if (state.value === next) return;
    state.value = next;
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
      const nextPage = reset ? 1 : pageNum.value + 1;
      const response = await mallFundsApi.receivables({ pageNum: nextPage, pageSize: PAGE_SIZE, settleState: state.value || undefined });
      if (seq !== requestSeq) return;
      const page = response.data || {};
      rows.value = reset ? page.list || [] : rows.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '账单加载失败';
      if (rows.value.length) uni.showToast({ title: error.value, icon: 'none' });
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }
  function goDetail(id) { uni.navigateTo({ url: `/pages-sub/credit/detail?receivableId=${id}` }); }
</script>

<style lang="scss" scoped>
  .bills { min-height: 100vh; padding-bottom: $space-8; background: $color-bg-page; }
  .bills__tabs { white-space: nowrap; background: $color-bg-card; }
  .bills__tab-row { display: inline-flex; gap: $space-5; padding: $space-4; }
  .bills__tab { color: $color-text-secondary; font-size: $font-size-sm; }
  .bills__tab--active { color: $color-primary; font-weight: $font-weight-bold; }
  .bills__state { padding: 240rpx $space-4 0; text-align: center; color: $color-text-secondary; }
  .bills__retry { display: block; margin-top: $space-4; color: $color-primary; }
  .bills__card { margin: $space-3 $space-4 0; padding: $space-4; @include card(0); }
  .bills__head { @include flex-between; gap: $space-3; font-weight: $font-weight-medium; }
  .bills__status { color: $color-primary; font-size: $font-size-sm; flex-shrink: 0; }
  .bills__order { display: block; margin-top: $space-3; color: $color-text-secondary; font-size: $font-size-sm; }
  .bills__amount { display: flex; justify-content: space-between; align-items: baseline; margin-top: $space-4; font-size: $font-size-sm; }
  .bills__amount text:last-child { font-size: $font-size-xl; font-weight: $font-weight-bold; font-variant-numeric: tabular-nums; }
  .bills__foot { @include flex-between; margin-top: $space-3; color: $color-text-tertiary; font-size: $font-size-xs; }
  .bills__overdue { color: $color-danger; }
  .bills__bottom { padding: $space-5; text-align: center; color: $color-text-tertiary; font-size: $font-size-xs; }
</style>
