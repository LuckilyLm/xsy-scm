<template>
  <view class="wallet" :style="themeStyle">
    <view v-if="loading && !summary" class="wallet__state">钱包信息加载中…</view>
    <view v-else-if="error && !summary" class="wallet__state">
      <text>{{ error }}</text>
      <text class="wallet__retry" @click="load">重试</text>
    </view>
    <template v-else-if="summary">
      <view v-if="stale" class="wallet__stale" @click="load"> 更新失败，当前为上次查询结果。点击重试 › </view>
      <view class="wallet__balance">
        <text class="wallet__label">{{ summary.balanceVisible ? '账户余额' : '共享结算账户' }}</text>
        <text v-if="summary.balanceVisible" class="wallet__amount">¥{{ displayMoney(summary.availableBalance) }}</text>
        <text v-else class="wallet__private">总余额暂不展示</text>
        <text class="wallet__hint">
          {{ summary.balanceVisible ? '余额根据实际收支流水计算' : '可查看本客户产生的收支记录' }}
        </text>
      </view>
      <view class="wallet__section">
        <view class="wallet__head" @click="goMovements"><text>最近收支</text><text class="wallet__link">全部记录 ›</text></view>
        <view v-if="!movements.length" class="wallet__empty">{{ summary.hasWallet === false ? '暂无钱包流水' : '暂无收支记录' }}</view>
        <view v-for="row in movements" :key="row.movementNo" class="wallet__row">
          <view class="wallet__row-main">
            <text>{{ movementTypeText(row.movementType) }}</text>
            <text class="wallet__time">{{ formatDateTime(row.occurredAt) }}</text>
          </view>
          <text class="wallet__change" :class="{ 'wallet__change--in': row.direction === 'CREDIT' }">
            {{ row.direction === 'CREDIT' ? '+' : '−' }}¥{{ displayMoney(row.amount) }}
          </text>
        </view>
      </view>
      <view class="wallet__note">充值服务暂未开通。余额退款以实际入账流水为准。</view>
    </template>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app';
  import { mallFundsApi } from '@/api/mall/funds-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { formatDateTime } from '@/utils/order-display';
  import { displayMoney } from '@/utils/money-display';
  import { movementTypeText } from '@/utils/funds-display';

  const { themeStyle } = useMallTheme();

  const summary = ref(null);
  const movements = ref([]);
  const loading = ref(false);
  const error = ref('');
  const stale = ref(false);
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
      const [walletResponse, movementResponse] = await Promise.all([
        mallFundsApi.walletSummary(),
        mallFundsApi.walletMovements({ pageNum: 1, pageSize: 5 }),
      ]);
      if (seq !== requestSeq) return;
      summary.value = walletResponse.data;
      movements.value = movementResponse.data?.list || [];
      stale.value = false;
    } catch (failure) {
      if (seq !== requestSeq) return;
      smartSentry.captureError(failure);
      error.value = failure?.message || '钱包信息加载失败';
      stale.value = !!summary.value;
    } finally {
      if (seq === requestSeq) loading.value = false;
    }
  }

  function goMovements() {
    uni.navigateTo({ url: '/pages-sub/wallet/movements' });
  }
</script>

<style lang="scss" scoped>
  .wallet {
    @include viewport-min-height;
    padding: $space-4 $space-4 $space-8;
    box-sizing: border-box;
    background: $color-bg-page;
  }
  .wallet__state {
    padding: $space-8 $space-4;
    text-align: center;
    color: $color-text-secondary;
  }
  .wallet__retry {
    display: block;
    margin-top: $space-4;
    color: var(--mall-primary-ink, #12833c);
  }
  .wallet__stale {
    margin-bottom: $space-3;
    padding: $space-3 $space-4;
    border-radius: $radius-md;
    background: $color-warning-light;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .wallet__balance {
    display: flex;
    flex-direction: column;
    padding: $space-6;
    border-radius: $radius-lg;
    background: var(--mall-primary, #16a34a);
    color: $color-text-inverse;
  }
  .wallet__label {
    font-size: $font-size-sm;
    opacity: 0.9;
  }
  .wallet__amount {
    margin-top: $space-3;
    font-size: 64rpx;
    font-weight: $font-weight-bold;
    font-variant-numeric: tabular-nums;
  }
  .wallet__private {
    margin-top: $space-3;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
  }
  .wallet__hint {
    margin-top: $space-4;
    font-size: $font-size-sm;
    opacity: 0.9;
  }
  .wallet__section {
    margin-top: $space-4;
    padding: 0 $space-4;
    @include card(0);
  }
  .wallet__head {
    height: 104rpx;
    @include flex-between;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
  }
  .wallet__link {
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
    font-weight: $font-weight-regular;
  }
  .wallet__empty {
    padding: $space-8 0;
    text-align: center;
    color: $color-text-tertiary;
  }
  .wallet__row {
    min-height: 104rpx;
    display: flex;
    align-items: center;
    justify-content: space-between;
    @include hairline-bottom;
  }
  .wallet__row-main {
    display: flex;
    flex-direction: column;
    gap: $space-1;
  }
  .wallet__time {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .wallet__change {
    font-variant-numeric: tabular-nums;
    font-weight: $font-weight-medium;
  }
  .wallet__change--in {
    color: $color-success;
  }
  .wallet__note {
    margin-top: $space-4;
    color: $color-text-secondary;
    font-size: $font-size-sm;
    line-height: 1.6;
  }
</style>
