<template>
  <view class="movements" :style="themeStyle">
    <scroll-view scroll-x class="movements__filters">
      <view class="movements__filter-row">
        <text
          v-for="filter in MOVEMENT_FILTERS"
          :key="filter.type"
          class="movements__filter"
          :class="{ 'movements__filter--active': type === filter.type }"
          @click="setType(filter.type)"
        >
          {{ filter.label }}
        </text>
      </view>
    </scroll-view>
    <view class="movements__directions">
      <text
        v-for="filter in directions"
        :key="filter.value"
        class="movements__direction"
        :class="{ 'movements__direction--active': direction === filter.value }"
        @click="setDirection(filter.value)"
      >
        {{ filter.label }}
      </text>
    </view>
    <view v-if="loading && !rows.length" class="movements__state">收支记录加载中…</view>
    <view v-else-if="error && !rows.length" class="movements__state">
      <text>{{ error }}</text
      ><text class="movements__retry" @click="load(true)">重试</text>
    </view>
    <view v-else-if="!rows.length" class="movements__state">暂无符合条件的收支记录</view>
    <view v-for="row in rows" :key="row.movementNo" class="movements__card">
      <view class="movements__top">
        <text class="movements__type">{{ movementTypeText(row.movementType) }}</text>
        <text class="movements__amount" :class="{ 'movements__amount--in': row.direction === 'CREDIT' }">
          {{ row.direction === 'CREDIT' ? '+' : '−' }}¥{{ displayAmount(row.amount) }}
        </text>
      </view>
      <text class="movements__meta">{{ formatDateTime(row.occurredAt) }}</text>
      <text v-if="row.orderNo" class="movements__meta">关联订单 {{ row.orderNo }}</text>
      <text v-if="row.refundNo" class="movements__meta">关联退款 {{ row.refundNo }}</text>
      <text v-if="row.rechargeNo" class="movements__meta">充值单号 {{ row.rechargeNo }}</text>
      <text v-if="row.returnId" class="movements__link" @click="goAfterSale(row.returnId)">查看售后详情 ›</text>
    </view>
    <view v-if="rows.length" class="movements__bottom">
      {{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多记录了' }}
    </view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
  import { mallFundsApi } from '@/api/mall/funds-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { displayAmount, formatDateTime } from '@/utils/order-display';
  import { MOVEMENT_FILTERS, movementTypeText } from '@/utils/funds-display';

  const { themeStyle } = useMallTheme();

  const PAGE_SIZE = 20;
  const directions = [
    { value: '', label: '全部收支' },
    { value: 'CREDIT', label: '收入' },
    { value: 'DEBIT', label: '支出' },
  ];
  const type = ref('');
  const direction = ref('');
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

  function setType(next) {
    if (type.value === next) return;
    type.value = next;
    load(true);
  }
  function setDirection(next) {
    if (direction.value === next) return;
    direction.value = next;
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
      const query = { pageNum: nextPage, pageSize: PAGE_SIZE };
      if (type.value) query.type = type.value;
      if (direction.value) query.direction = direction.value;
      const response = await mallFundsApi.walletMovements(query);
      if (seq !== requestSeq) return;
      const page = response.data || {};
      rows.value = reset ? page.list || [] : rows.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '收支记录加载失败';
      if (rows.value.length) uni.showToast({ title: error.value, icon: 'none' });
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }

  function goAfterSale(returnId) {
    uni.navigateTo({ url: `/pages-sub/after-sale/detail?returnId=${returnId}` });
  }
</script>

<style lang="scss" scoped>
  .movements {
    @include viewport-min-height;
    padding-bottom: $space-8;
    background: $color-bg-page;
  }
  .movements__filters {
    white-space: nowrap;
    background: $color-bg-card;
  }
  .movements__filter-row {
    display: inline-flex;
    gap: $space-4;
    padding: $space-4;
  }
  .movements__filter {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .movements__filter--active {
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-bold;
  }
  .movements__directions {
    display: flex;
    gap: $space-4;
    padding: 0 $space-4 $space-4;
    background: $color-bg-card;
  }
  .movements__direction {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .movements__direction--active {
    color: var(--mall-primary-ink, #12833c);
    font-weight: $font-weight-bold;
  }
  .movements__state {
    padding: 240rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .movements__retry {
    display: block;
    margin-top: $space-4;
    color: var(--mall-primary-ink, #12833c);
  }
  .movements__card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .movements__top {
    @include flex-between;
  }
  .movements__type {
    font-weight: $font-weight-medium;
  }
  .movements__amount {
    font-variant-numeric: tabular-nums;
    font-weight: $font-weight-bold;
  }
  .movements__amount--in {
    color: $color-success;
  }
  .movements__meta {
    display: block;
    margin-top: $space-2;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .movements__link {
    display: block;
    margin-top: $space-3;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
  .movements__bottom {
    padding: $space-5;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
</style>
