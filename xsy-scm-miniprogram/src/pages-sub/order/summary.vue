<template>
  <view class="summary" :style="themeStyle">
    <view class="summary__filters">
      <picker mode="date" :value="startDate" :end="endDate || today" @change="onStartChange">
        <text class="summary__date">{{ startDate || '开始日期' }}</text>
      </picker>
      <text class="summary__sep">至</text>
      <picker mode="date" :value="endDate" :start="startDate" :end="today" @change="onEndChange">
        <text class="summary__date">{{ endDate || '结束日期' }}</text>
      </picker>
      <text v-if="startDate || endDate" class="summary__clear" @click="clearRange">清除</text>
    </view>

    <view class="summary__totals">
      <view class="summary__total">
        <text class="summary__total-label">合计金额</text>
        <text class="summary__total-value">¥{{ displayMoney(totals.amount) }}</text>
      </view>
      <view class="summary__total">
        <text class="summary__total-label">商品规格</text>
        <text class="summary__total-value">{{ totals.skuCount || 0 }}</text>
      </view>
      <view class="summary__total">
        <text class="summary__total-label">订单数</text>
        <text class="summary__total-value">{{ totals.orderCount || 0 }}</text>
      </view>
    </view>

    <view v-if="loading && !rows.length" class="summary__state">汇总加载中…</view>
    <view v-else-if="error && !rows.length" class="summary__state">
      <text>{{ error }}</text>
      <text class="summary__retry" @click="load(true)">重试</text>
    </view>
    <view v-else-if="!rows.length" class="summary__state">所选区间内没有下单记录</view>

    <view v-for="row in rows" :key="row.skuId" class="summary__card">
      <image v-if="row.imageUrl" class="summary__image" :src="row.imageUrl" mode="aspectFill" lazy-load />
      <view v-else class="summary__image summary__image--empty">
        <uni-icons type="image" :size="20" :color="COLOR_TEXT_PLACEHOLDER" />
      </view>
      <view class="summary__body">
        <text class="summary__name">{{ row.productName }}</text>
        <text class="summary__spec">{{ row.specName }}</text>
        <text class="summary__meta">出现在 {{ row.orderCount }} 张订单</text>
      </view>
      <view class="summary__figures">
        <text class="summary__amount">¥{{ displayMoney(row.amount) }}</text>
        <text class="summary__qty">{{ displayQuantity(row.quantity) }}{{ row.saleUnit || '' }}</text>
      </view>
    </view>

    <view v-if="rows.length" class="summary__bottom">
      {{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}
    </view>
  </view>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_PLACEHOLDER } from '@/constants/theme-color-const';
  import { displayQuantity } from '@/utils/order-display';
  import { displayMoney } from '@/utils/money-display';

  const { themeStyle } = useMallTheme();

  const PAGE_SIZE = 20;
  const today = formatDateInput(new Date());
  const rows = ref([]);
  const totals = reactive({ quantity: '0', amount: '0', orderCount: 0, skuCount: 0 });
  const startDate = ref('');
  const endDate = ref('');
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const error = ref('');
  /** 请求序号：改日期区间时旧请求可能还在路上，谁最后发出谁作数。 */
  let loadSeq = 0;

  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value && !loading.value) load(false);
  });

  function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function onStartChange(event) {
    startDate.value = event.detail.value;
    load(true);
  }

  function onEndChange(event) {
    endDate.value = event.detail.value;
    load(true);
  }

  function clearRange() {
    startDate.value = '';
    endDate.value = '';
    load(true);
  }

  async function load(reset) {
    if (!reset && loading.value) return;
    const seq = (loadSeq += 1);
    const nextPage = reset ? 1 : pageNum.value + 1;
    loading.value = true;
    error.value = '';
    try {
      const response = await mallOrderApi.getSummary({
        pageNum: nextPage,
        pageSize: PAGE_SIZE,
        startDate: startDate.value || undefined,
        endDate: endDate.value || undefined,
      });
      if (seq !== loadSeq) return;
      const page = response.data || {};
      rows.value = reset ? page.items || [] : rows.value.concat(page.items || []);
      Object.assign(totals, page.totals || {});
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (failure) {
      if (seq !== loadSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '汇总加载失败';
      if (rows.value.length) uni.showToast({ title: error.value, icon: 'none' });
    } finally {
      if (seq === loadSeq) loading.value = false;
    }
  }

  load(true);
</script>

<style lang="scss" scoped>
  .summary {
    @include viewport-min-height;
    padding-bottom: $space-8;
    background: $color-bg-page;
  }
  .summary__filters {
    display: flex;
    align-items: center;
    gap: $space-3;
    padding: $space-4;
    background: $color-bg-card;
    @include hairline-bottom;
  }
  .summary__date {
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .summary__sep {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .summary__clear {
    margin-left: auto;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
  }
  .summary__totals {
    display: flex;
    margin: $space-3 $space-4 0;
    padding: $space-4 0;
    border-radius: $radius-md;
    background: $color-bg-card;
  }
  .summary__total {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $space-2;
  }
  .summary__total-label {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .summary__total-value {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .summary__state {
    padding: 240rpx $space-4 0;
    text-align: center;
    color: $color-text-secondary;
  }
  .summary__retry {
    display: block;
    margin-top: $space-4;
    color: var(--mall-primary-ink, #12833c);
  }
  .summary__card {
    display: flex;
    align-items: center;
    gap: $space-3;
    margin: $space-3 $space-4 0;
    padding: $space-3;
    border-radius: $radius-md;
    background: $color-bg-card;
  }
  .summary__image {
    width: 120rpx;
    height: 120rpx;
    border-radius: $radius-sm;
    background: $color-bg-page;
  }
  .summary__image--empty {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .summary__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: $space-1;
    min-width: 0;
  }
  .summary__name {
    color: $color-text-primary;
    font-size: $font-size-base;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .summary__spec {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .summary__meta {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .summary__figures {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: $space-1;
  }
  .summary__amount {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .summary__qty {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .summary__bottom {
    padding: $space-4;
    text-align: center;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
</style>
