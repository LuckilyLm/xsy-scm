<template>
  <view class="al" :style="themeStyle">
    <view v-if="loading && !records.length" class="al__state">售后单加载中…</view>
    <view v-else-if="!records.length" class="al__state">暂无售后单</view>

    <view v-for="record in records" :key="record.returnId" class="al-card" @click="goDetail(record.returnId)">
      <view class="al-card__head">
        <text class="al-card__no">售后单号：{{ record.returnNo }}</text>
        <text class="al-card__status" :class="`al-card__status--${afterSaleStatusLevel(record.displayStatus)}`">
          {{ afterSaleStatusText(record.displayStatus) }}
        </text>
      </view>
      <view class="al-card__goods">
        <view class="al-card__thumbs">
          <view v-for="(thumb, index) in record.itemThumbnails || []" :key="index" class="al-card__thumb">
            <image class="al-card__image" :src="thumb" mode="aspectFill" lazy-load />
          </view>
          <view v-for="index in emptyThumbCount(record)" :key="`empty-${index}`" class="al-card__thumb al-card__thumb--empty">
            <uni-icons type="image" :size="20" :color="COLOR_TEXT_PLACEHOLDER" />
          </view>
        </view>
        <view class="al-card__count xsy-chevron">
          <text>共 {{ record.itemCount || 0 }} 件</text>
          <uni-icons type="right" :size="12" :color="COLOR_TEXT_TERTIARY" />
        </view>
      </view>
      <view class="al-card__foot">
        <text class="al-card__label">{{ amountLabel(record) }}</text>
        <text class="al-card__amount">¥{{ displayAmount(amountOf(record)) }}</text>
      </view>
      <text class="al-card__time">申请时间：{{ formatDateTime(record.createdAt) }}</text>
    </view>

    <view v-if="loading && records.length" class="al__more">加载中…</view>
    <view v-else-if="records.length && !hasMore" class="al__more">没有更多售后单了</view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';

  import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app';
  import { mallAfterSaleApi } from '@/api/mall/after-sale-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_PLACEHOLDER, COLOR_TEXT_TERTIARY } from '@/constants/theme-color-const';
  import { displayAmount, formatDateTime } from '@/utils/order-display';
  import { afterSaleStatusLevel, afterSaleStatusText } from '@/utils/after-sale-display';

  const { themeStyle } = useMallTheme();

  const PAGE_SIZE = 10;
  const records = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  /** 只看某一订单的售后（从订单详情进入时带上） */
  const orderId = ref(null);
  let loadSeq = 0;
  let showed = false;

  onLoad((options) => {
    orderId.value = Number(options?.orderId) || null;
    load(true);
  });
  onShow(() => {
    // 从申请页 / 详情页返回时要拿到最新状态；首次 onShow 在 onLoad 之后，跳过避免重复请求
    if (showed) load(true);
    showed = true;
  });
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    if (hasMore.value && !loading.value) load(false);
  });

  async function load(reset) {
    if (!reset && loading.value) return;
    const seq = (loadSeq += 1);
    loading.value = true;
    try {
      const nextPage = reset ? 1 : pageNum.value + 1;
      const response = await mallAfterSaleApi.getList({
        pageNum: nextPage,
        pageSize: PAGE_SIZE,
        orderId: orderId.value || undefined,
      });
      if (seq !== loadSeq) return;
      const page = response.data || {};
      records.value = reset ? page.list || [] : records.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = (page.list || []).length >= PAGE_SIZE;
    } catch (error) {
      if (seq !== loadSeq) return;
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '售后单加载失败', icon: 'none' });
    } finally {
      if (seq === loadSeq) loading.value = false;
    }
  }

  /** 审核后有核定金额就看核定金额；审核中看申请金额（预估） */
  function amountOf(record) {
    return record.displayStatus === 'REVIEWING' ? record.requestedAmount : record.approvedAmount;
  }

  function amountLabel(record) {
    return record.displayStatus === 'REVIEWING' ? '申请金额（预估）' : '核定退款金额';
  }

  function emptyThumbCount(record) {
    return Math.max(0, 3 - (record.itemThumbnails || []).length);
  }

  function goDetail(returnId) {
    uni.navigateTo({ url: `/pages-sub/after-sale/detail?returnId=${returnId}` });
  }
</script>

<style lang="scss" scoped>
  .al {
    @include viewport-min-height;
    background: $color-bg-page;
    box-sizing: border-box;
    padding-bottom: $space-6;
  }
  .al__state {
    padding-top: 240rpx;
    text-align: center;
    color: $color-text-tertiary;
  }
  .al__more {
    padding: $space-4 0;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .al-card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .al-card__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .al-card__no {
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .al-card__status {
    font-size: $font-size-xs;
    font-weight: $font-weight-medium;
  }
  .al-card__status--warn {
    color: $color-warning;
  }
  .al-card__status--primary {
    color: $color-success;
  }
  .al-card__status--ok {
    color: $color-success;
  }
  .al-card__status--danger {
    color: $color-danger;
  }
  .al-card__status--off {
    color: $color-text-tertiary;
  }
  .al-card__goods {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: $space-3;
  }
  .al-card__thumbs {
    display: flex;
  }
  .al-card__thumb {
    width: 96rpx;
    height: 96rpx;
    border-radius: $radius-md;
    overflow: hidden;
    background: $color-bg-hover;
    @include flex-center;
  }
  .al-card__thumb + .al-card__thumb {
    margin-left: $space-2;
  }
  .al-card__image {
    width: 100%;
    height: 100%;
  }
  .al-card__count {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .al-card__foot {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-top: $space-3;
  }
  .al-card__label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .al-card__amount {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .al-card__time {
    display: block;
    margin-top: $space-2;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
</style>
