<template>
  <view class="orders">
    <view class="orders__tabs">
      <view
        v-for="tab in ORDER_TABS"
        :key="tab.value"
        class="orders__tab"
        :class="{ 'orders__tab--active': tab.value === displayStatus }"
        @click="switchTab(tab.value)"
      >
        <text class="orders__tab-text">{{ tab.label }}</text>
      </view>
    </view>

    <view v-if="loading && !orders.length" class="orders__state">订单加载中…</view>
    <view v-else-if="!orders.length" class="orders__state">{{ emptyText }}</view>

    <view v-for="order in orders" :key="order.orderId" class="order-card">
      <view class="order-card__head" @click="goDetail(order.orderId)">
        <text class="order-card__no">订单号：{{ order.orderNo }}</text>
        <view class="order-card__badges">
          <text v-if="isAbnormalFulfillment(order.fulfillmentStatus)" class="order-card__abnormal">配送异常</text>
          <text class="order-card__status" :class="`order-card__status--${displayStatusLevel(order.displayStatus)}`">
            {{ displayStatusText(order.displayStatus) }}
          </text>
        </view>
      </view>

      <view class="order-card__goods" @click="goDetail(order.orderId)">
        <view class="order-card__thumbs">
          <view v-for="(thumb, index) in order.itemThumbnails || []" :key="index" class="order-card__thumb">
            <image class="order-card__image" :src="thumb" mode="aspectFill" />
          </view>
          <view v-for="index in emptyThumbCount(order)" :key="`empty-${index}`" class="order-card__thumb order-card__thumb--empty">
            <uni-icons type="image" :size="20" :color="COLOR_TEXT_PLACEHOLDER" />
          </view>
        </view>
        <view class="order-card__summary">
          <text class="order-card__count">共 {{ order.itemCount || 0 }} 件</text>
          <text class="order-card__time">{{ formatDateTime(order.createdAt) }}</text>
        </view>
      </view>

      <view class="order-card__foot" @click="goDetail(order.orderId)">
        <text class="order-card__label">预计金额</text>
        <text class="order-card__amount">¥{{ displayAmount(order.orderedTotalAmount) }}</text>
      </view>

      <view class="order-card__actions">
        <view v-if="order.cancelable" class="order-card__btn" @click="cancelOrder(order)">取消订单</view>
        <view class="order-card__btn order-card__btn--primary" @click="reorder(order)">再次购买</view>
      </view>
    </view>

    <view v-if="loading && orders.length" class="orders__more">加载中…</view>
    <view v-else-if="orders.length && !hasMore" class="orders__more">没有更多订单了</view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import { COLOR_TEXT_PLACEHOLDER } from '@/constants/theme-color-const';
  import {
    CANCEL_REASONS,
    ORDER_TABS,
    displayAmount,
    displayStatusLevel,
    displayStatusText,
    formatDateTime,
    isAbnormalFulfillment,
  } from '@/utils/order-display';

  const PAGE_SIZE = 10;
  const orders = ref([]);
  const pageNum = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  /** 当前页签；空串为「全部」 */
  const displayStatus = ref('');
  /**
   * 请求序号：切页签时旧请求可能还在路上，谁最后发出谁作数，旧响应一律丢弃。
   * 没有它会出现「切到配送中却显示上一分类结果」。
   */
  let loadSeq = 0;

  const emptyText = computed(() => (displayStatus.value ? `暂无${displayStatusText(displayStatus.value)}订单` : '暂无订单'));

  onShow(() => load(true));
  onPullDownRefresh(async () => {
    await load(true);
    uni.stopPullDownRefresh();
  });
  onReachBottom(() => {
    // 翻页要等上一页回来，否则会重复请求同一页；切页签不受此限制
    if (hasMore.value && !loading.value) load(false);
  });

  function switchTab(value) {
    if (displayStatus.value === value) return;
    displayStatus.value = value;
    // 切分类总是发出新请求，不被在途的上一分类请求挡住
    load(true);
  }

  async function load(reset) {
    if (!reset && loading.value) return;
    const seq = (loadSeq += 1);
    const tabAtRequest = displayStatus.value;
    loading.value = true;
    try {
      const nextPage = reset ? 1 : pageNum.value + 1;
      const response = await mallOrderApi.getList({
        pageNum: nextPage,
        pageSize: PAGE_SIZE,
        displayStatus: tabAtRequest || undefined,
      });
      if (seq !== loadSeq) return;
      const page = response.data || {};
      orders.value = reset ? page.list || [] : orders.value.concat(page.list || []);
      pageNum.value = nextPage;
      hasMore.value = nextPage < Number(page.pages || 0);
    } catch (error) {
      if (seq !== loadSeq) return;
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '订单加载失败', icon: 'none' });
    } finally {
      if (seq === loadSeq) loading.value = false;
    }
  }

  /** 设计图固定三格：有图的显示图，缺的用占位补齐，避免卡片高度跳动 */
  function emptyThumbCount(order) {
    const shown = (order.itemThumbnails || []).length;
    if (!order.itemCount || shown >= 3) return 0;
    return Math.min(3, order.itemCount) - shown;
  }

  function goDetail(orderId) {
    uni.navigateTo({ url: `/pages-sub/order/detail?orderId=${orderId}` });
  }

  function cancelOrder(order) {
    uni.showActionSheet({
      itemList: CANCEL_REASONS,
      success: ({ tapIndex }) => {
        const reason = CANCEL_REASONS[tapIndex];
        uni.showModal({
          title: '确认取消订单',
          content: `取消后不可恢复，确认取消订单 ${order.orderNo}？`,
          success: ({ confirm }) => {
            if (confirm) doCancel(order, reason);
          },
        });
      },
    });
  }

  async function doCancel(order, reason) {
    try {
      // 幂等键与「订单 + 原因」绑定：网络重试复用同一个键，重复点击不会二次取消
      const key = `cancel-${order.orderId}-${reason}`;
      await mallOrderApi.cancel(order.orderId, reason, key);
      uni.showToast({ title: '订单已取消', icon: 'none' });
      load(true);
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '取消失败，请稍后重试', icon: 'none' });
    }
  }

  async function reorder(order) {
    try {
      const response = await mallOrderApi.reorder(order.orderId);
      const data = response.data || {};
      const added = data.addedCount || 0;
      const skipped = data.skipped || [];
      if (!added) {
        uni.showModal({
          title: '未能加入购物车',
          content: skipped.map((row) => `${row.productName}：${row.reason}`).join('\n') || '历史商品均已不可购买',
          showCancel: false,
        });
        return;
      }
      uni.showToast({
        title: skipped.length ? `已加入 ${added} 项，${skipped.length} 项未加入` : `已加入 ${added} 项`,
        icon: 'none',
      });
      setTimeout(() => uni.switchTab({ url: '/pages/cart/index' }), 700);
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '加入购物车失败', icon: 'none' });
    }
  }
</script>

<style lang="scss" scoped>
  .orders {
    min-height: 100vh;
    background: $color-bg-page;
    box-sizing: border-box;
    padding-bottom: $space-8;
  }
  .orders__tabs {
    display: flex;
    align-items: center;
    background: $color-bg-card;
    @include hairline-bottom;
  }
  .orders__tab {
    flex: 1;
    padding: $space-3 0;
    text-align: center;
  }
  .orders__tab-text {
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .orders__tab--active .orders__tab-text {
    color: $color-primary;
    font-weight: $font-weight-bold;
  }
  .orders__state {
    padding-top: 240rpx;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-base;
  }
  .orders__more {
    padding: $space-4 0;
    text-align: center;
    color: $color-text-tertiary;
    font-size: $font-size-sm;
  }
  .order-card {
    margin: $space-3 $space-4 0;
    /* card() 的 padding 参数会覆盖前面的 padding 声明，这里必须传内边距，否则内容贴边 */
    @include card($space-4);
  }
  .order-card__head {
    @include flex-between;
  }
  .order-card__no {
    color: $color-text-primary;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .order-card__badges {
    display: flex;
    align-items: center;
  }
  .order-card__abnormal {
    margin-right: $space-2;
    padding: 2rpx 12rpx;
    border-radius: $radius-pill;
    background: #fdecec;
    color: $color-danger;
    font-size: $font-size-xs;
  }
  .order-card__status {
    font-size: $font-size-sm;
    font-weight: $font-weight-bold;
  }
  .order-card__status--warn {
    color: $color-warning;
  }
  .order-card__status--primary {
    color: $color-primary;
  }
  .order-card__status--ok {
    color: $color-text-secondary;
  }
  .order-card__status--off {
    color: $color-text-tertiary;
  }
  .order-card__goods {
    display: flex;
    align-items: center;
    margin-top: $space-3;
  }
  .order-card__thumbs {
    display: flex;
    align-items: center;
    flex-shrink: 0;
  }
  .order-card__thumb {
    width: 96rpx;
    height: 96rpx;
    margin-right: $space-2;
    border-radius: $radius-md;
    overflow: hidden;
    background: $color-bg-hover;
  }
  .order-card__thumb:last-child {
    margin-right: 0;
  }
  .order-card__thumb--empty {
    @include flex-center;
    background: $color-primary-light;
  }
  .order-card__image {
    width: 100%;
    height: 100%;
  }
  /* 件数与下单时间贴着缩略图竖排，避免右侧出现大片空白 */
  .order-card__summary {
    margin-left: $space-3;
    display: flex;
    flex-direction: column;
    gap: $space-1;
    min-width: 0;
  }
  .order-card__count {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .order-card__foot {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-top: $space-3;
    padding-top: $space-3;
    border-top: 1px solid $color-divider;
  }
  .order-card__label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .order-card__amount {
    margin-left: $space-1;
    color: $color-danger;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
  }
  .order-card__time {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .order-card__actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: $space-3;
    margin-top: $space-3;
    padding-top: $space-3;
    border-top: 1px solid $color-divider;
  }
  .order-card__btn {
    min-width: 152rpx;
    height: 60rpx;
    @include flex-center;
    border: 1px solid $color-border;
    border-radius: $radius-pill;
    background: $color-bg-page;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .order-card__btn--primary {
    border-color: $color-primary;
    background: $color-primary;
    color: $color-text-inverse;
  }
</style>
