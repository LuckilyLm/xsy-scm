<template>
  <view class="od">
    <view v-if="loading && !order" class="od__state">订单加载中…</view>
    <view v-else-if="!order" class="od__state">订单不存在或已不可查看</view>

    <template v-else>
      <view class="od-card od-head">
        <text class="od-head__status" :class="`od-head__status--${displayStatusLevel(order.displayStatus)}`">
          {{ displayStatusText(order.displayStatus) }}
        </text>
        <text class="od-head__no">订单号 {{ order.orderNo }}</text>
        <text v-if="order.displayStatus === 'DELIVERING'" class="od-head__hint">已发车在途，请留意配送联系</text>
        <text v-else-if="order.displayStatus === 'PREPARING'" class="od-head__hint">后台已接单，正在备货</text>
        <text v-else-if="order.displayStatus === 'PENDING'" class="od-head__hint">已提交，等待后台受理</text>
        <text v-else-if="order.displayStatus === 'CANCELLED' && order.cancelReason" class="od-head__hint"> 取消原因：{{ order.cancelReason }} </text>
        <text v-if="isAbnormalFulfillment(order.fulfillmentStatus)" class="od-head__abnormal">
          配送异常{{ order.signReason ? `：${order.signReason}` : '' }}，如有疑问请联系业务员
        </text>
      </view>

      <view v-if="order.receiverName" class="od-card">
        <view class="od-card__title">收货信息</view>
        <text class="od__line">{{ order.receiverName }} {{ order.receiverPhone }}</text>
        <text class="od__sub">{{ order.address }}</text>
      </view>

      <!-- 配送进度：节点与 done 都由服务端按正式事实给出，客户端不推导 -->
      <view v-if="(order.progress || []).length" class="od-card">
        <view class="od-card__title">配送进度</view>
        <view v-for="(node, index) in order.progress" :key="node.code" class="od-step" :class="{ 'od-step--pending': !node.done }">
          <view class="od-step__dot-wrap">
            <view class="od-step__dot" :class="{ 'od-step__dot--done': node.done }" />
            <view v-if="index < order.progress.length - 1" class="od-step__line" />
          </view>
          <view class="od-step__main">
            <text class="od-step__title" :class="{ 'od-step__title--pending': !node.done }">{{ node.text }}</text>
            <text v-if="node.time" class="od__sub">{{ formatDateTime(node.time) }}</text>
            <text v-if="node.detail" class="od-step__detail">{{ node.detail }}</text>
          </view>
        </view>
      </view>

      <view class="od-card">
        <view class="od-card__title">配送信息</view>
        <view class="od__row">
          <text class="od__row-label">期望配送时间</text>
          <text class="od__row-value">{{ formatDateTime(order.expectDeliveryTime) || '未指定' }}</text>
        </view>
        <template v-if="order.delivery">
          <view class="od__row">
            <text class="od__row-label">配送线路</text>
            <text class="od__row-value">{{ order.delivery.routeName || '—' }}</text>
          </view>
          <view v-if="order.delivery.deliveryDate" class="od__row">
            <text class="od__row-label">配送日期</text>
            <text class="od__row-value">{{ order.delivery.deliveryDate }}</text>
          </view>
          <view v-if="order.delivery.plannedDepartureTime" class="od__row">
            <text class="od__row-label">计划发车</text>
            <text class="od__row-value">{{ formatDateTime(order.delivery.plannedDepartureTime) }}</text>
          </view>
          <view v-if="order.delivery.dispatchedAt" class="od__row">
            <text class="od__row-label">实际发车</text>
            <text class="od__row-value">{{ formatDateTime(order.delivery.dispatchedAt) }}</text>
          </view>
          <view v-if="order.delivery.driverName" class="od__row">
            <text class="od__row-label">配送司机</text>
            <text class="od__row-value">{{ order.delivery.driverName }}</text>
          </view>
          <view v-if="order.delivery.driverPhone" class="od__row">
            <text class="od__row-label">联系电话</text>
            <text class="od__row-value od__row-value--link" @click="callDriver">{{ order.delivery.driverPhone }}</text>
          </view>
          <view v-if="order.delivery.vehicleNo" class="od__row">
            <text class="od__row-label">配送车辆</text>
            <text class="od__row-value">{{ order.delivery.vehicleNo }}</text>
          </view>
          <view v-if="order.delivery.warehouseName" class="od__row">
            <text class="od__row-label">发货仓</text>
            <text class="od__row-value">{{ order.delivery.warehouseName }}</text>
          </view>
          <view v-if="order.delivery.completedAt" class="od__row">
            <text class="od__row-label">线路完成</text>
            <text class="od__row-value">{{ formatDateTime(order.delivery.completedAt) }}</text>
          </view>
        </template>
        <text v-else class="od__sub">后台尚未安排配送线路</text>
        <view v-if="order.signedAt" class="od__row">
          <text class="od__row-label">配送结果</text>
          <text class="od__row-value" :class="{ 'od__row-value--danger': isAbnormalFulfillment(order.fulfillmentStatus) }">
            {{ isAbnormalFulfillment(order.fulfillmentStatus) ? '配送异常' : '已签收' }}
          </text>
        </view>
        <view v-if="order.signedAt" class="od__row">
          <!-- 异常签收（拒收/破损）也是终态结果，用「配送结果记录时间」而不是「签收时间」，不把拒收说成签收 -->
          <text class="od__row-label">配送结果记录时间</text>
          <text class="od__row-value">{{ formatDateTime(order.signedAt) }}</text>
        </view>
        <view v-if="order.signReason" class="od__row">
          <text class="od__row-label">
            {{ isAbnormalFulfillment(order.fulfillmentStatus) ? '异常原因' : '签收备注' }}
          </text>
          <text class="od__row-value">{{ order.signReason }}</text>
        </view>
      </view>

      <view class="od-card">
        <view class="od-card__title">商品明细</view>
        <view v-for="item in order.items || []" :key="item.itemId" class="od-item">
          <view class="od-item__thumb">
            <image v-if="item.primaryImageUrl" class="od-item__image" :src="item.primaryImageUrl" mode="aspectFill" />
            <view v-else class="od-item__thumb-fallback">{{ (item.productName || '商').slice(0, 1) }}</view>
          </view>
          <view class="od-item__main">
            <text class="od-item__name">{{ item.productName }}</text>
            <text class="od__sub">{{ item.specName || '默认规格' }} · {{ item.saleUnit }}</text>
            <view class="od-item__prices">
              <text class="od-item__price">¥{{ displayAmount(item.unitPrice) }}</text>
              <text class="od__sub"> × {{ displayQuantity(item.orderedQuantity) }}</text>
            </view>
            <text v-if="item.nonStandard" class="od-item__actual">
              实重 {{ item.actualQuantity ? `${displayQuantity(item.actualQuantity)}${item.saleUnit}` : '待称重' }}
            </text>
          </view>
          <view class="od-item__amount">
            <text class="od-item__amount-value"> ¥{{ displayAmount(item.settlementLineAmount || item.orderedLineAmount) }} </text>
            <text class="od-item__amount-label">{{ item.settlementLineAmount ? '结算' : '预估' }}</text>
          </view>
        </view>
      </view>

      <view class="od-card">
        <view class="od-card__title">金额</view>
        <view class="od__row">
          <text class="od__row-label">预计金额</text>
          <text class="od__row-value">¥{{ displayAmount(order.orderedTotalAmount) }}</text>
        </view>
        <view v-if="order.discount" class="od__row">
          <text class="od__row-label">已确认优惠</text>
          <text class="od__row-value">−¥{{ displayAmount(order.discount.discountAmount) }}</text>
        </view>
        <text v-if="order.discount?.couponName" class="od__sub">优惠券：{{ order.discount.couponName }}</text>
        <text v-for="name in order.discount?.activityNames || []" :key="name" class="od__sub">活动：{{ name }}</text>
        <text v-for="gift in order.discount?.giftDescriptions || []" :key="gift" class="od__sub">赠品：{{ gift }}</text>
        <view class="od__row">
          <text class="od__row-label">{{ order.discount ? '实重商品金额（优惠前）' : '最终结算金额' }}</text>
          <text class="od__row-value od__row-value--strong">
            {{ order.settlementTotalAmount ? `¥${displayAmount(order.settlementTotalAmount)}` : '待结算' }}
          </text>
        </view>
        <text v-if="hasNonStandard" class="od__tip">含按实重结算商品，最终金额以出库实重为准</text>
      </view>

      <!-- 售后：已接单即可申请（含配送异常）；已有售后单展示最新状态，点击进售后详情 -->
      <view v-if="canApplyAfterSale(order) || afterSales.length" class="od-card">
        <view class="od-card__title">售后服务</view>
        <view v-for="record in afterSales" :key="record.returnId" class="od__row" @click="goAfterSaleDetail(record.returnId)">
          <text class="od__row-label">售后单 {{ record.returnNo }}</text>
          <text class="od__row-value od-aftersale__status" :class="`od-aftersale__status--${afterSaleStatusLevel(record.displayStatus)}`">
            {{ afterSaleStatusText(record.displayStatus) }} ›
          </text>
        </view>
        <view v-if="canApplyAfterSale(order)" class="od-aftersale__apply" @click="goAfterSaleApply"> 申请售后（退货 / 退款） </view>
      </view>

      <view class="od-card">
        <view class="od-card__title">订单信息</view>
        <view class="od__row">
          <text class="od__row-label">下单时间</text>
          <text class="od__row-value">{{ formatDateTime(order.createdAt) }}</text>
        </view>
        <view v-if="order.confirmedAt" class="od__row">
          <text class="od__row-label">受理时间</text>
          <text class="od__row-value">{{ formatDateTime(order.confirmedAt) }}</text>
        </view>
        <view v-if="order.cancelledAt" class="od__row">
          <text class="od__row-label">取消时间</text>
          <text class="od__row-value">{{ formatDateTime(order.cancelledAt) }}</text>
        </view>
        <view v-if="order.remark" class="od__row">
          <text class="od__row-label">订单备注</text>
          <text class="od__row-value">{{ order.remark }}</text>
        </view>
      </view>

      <view class="od-card">
        <view class="od-card__title">处理记录</view>
        <view v-if="!(order.timeline || []).length" class="od__sub">暂无记录</view>
        <view v-for="(log, index) in order.timeline || []" :key="log.logId" class="od-log">
          <view class="od-log__dot-wrap">
            <view class="od-log__dot" />
            <view v-if="index < (order.timeline || []).length - 1" class="od-log__line" />
          </view>
          <view class="od-log__main">
            <text class="od-log__title">{{ operationText(log.operationType) }}</text>
            <text class="od__sub">{{ log.operatorName }} · {{ formatDateTime(log.createdAt) }}</text>
            <text v-if="log.reason" class="od-log__reason">{{ log.reason }}</text>
          </view>
        </view>
      </view>

      <view class="od-bar">
        <view v-if="order.cancelable" class="od-bar__btn" @click="cancelOrder">取消订单</view>
        <view class="od-bar__btn od-bar__btn--primary" @click="reorder">再次购买</view>
      </view>
    </template>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
  import { mallOrderApi } from '@/api/mall/order-api';
  import { mallAfterSaleApi } from '@/api/mall/after-sale-api';
  import { smartSentry } from '@/lib/smart-sentry';
  import {
    CANCEL_REASONS,
    displayAmount,
    displayQuantity,
    displayStatusLevel,
    displayStatusText,
    formatDateTime,
    isAbnormalFulfillment,
    operationText,
  } from '@/utils/order-display';
  import { afterSaleStatusLevel, afterSaleStatusText, canApplyAfterSale } from '@/utils/after-sale-display';

  const orderId = ref(null);
  const order = ref(null);
  const afterSales = ref([]);
  const loading = ref(false);

  const hasNonStandard = computed(() => (order.value?.items || []).some((item) => item.nonStandard === true));

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
      loadAfterSales();
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '订单加载失败', icon: 'none' });
    } finally {
      loading.value = false;
    }
  }

  /** 售后状态独立加载：它失败不拖垮订单详情，只少一个售后区块 */
  async function loadAfterSales() {
    try {
      const response = await mallAfterSaleApi.getList({ pageNum: 1, pageSize: 20, orderId: orderId.value });
      afterSales.value = response.data?.list || [];
    } catch (error) {
      smartSentry.captureError(error);
      afterSales.value = [];
    }
  }

  function goAfterSaleApply() {
    uni.navigateTo({ url: `/pages-sub/after-sale/apply?orderId=${orderId.value}` });
  }

  function goAfterSaleDetail(returnId) {
    uni.navigateTo({ url: `/pages-sub/after-sale/detail?returnId=${returnId}` });
  }

  /** 司机电话来自配送事实快照；点击直接拨号，方便客户在途沟通 */
  function callDriver() {
    const phone = order.value?.delivery?.driverPhone;
    if (!phone) return;
    uni.makePhoneCall({ phoneNumber: phone, fail: () => {} });
  }

  function cancelOrder() {
    uni.showActionSheet({
      itemList: CANCEL_REASONS,
      success: ({ tapIndex }) => {
        const reason = CANCEL_REASONS[tapIndex];
        uni.showModal({
          title: '确认取消订单',
          content: '取消后不可恢复，确认取消该订单？',
          success: ({ confirm }) => {
            if (confirm) doCancel(reason);
          },
        });
      },
    });
  }

  async function doCancel(reason) {
    try {
      // 幂等键与「订单 + 原因」绑定：网络重试复用同一个键，不会二次取消
      const key = `cancel-${orderId.value}-${reason}`;
      await mallOrderApi.cancel(orderId.value, reason, key);
      uni.showToast({ title: '订单已取消', icon: 'none' });
      load();
    } catch (error) {
      smartSentry.captureError(error);
      uni.showToast({ title: error?.message || '取消失败，请稍后重试', icon: 'none' });
    }
  }

  async function reorder() {
    try {
      const response = await mallOrderApi.reorder(orderId.value);
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
  .od {
    min-height: 100vh;
    background: $color-bg-page;
    box-sizing: border-box;
    padding-bottom: 160rpx;
  }
  .od__state {
    padding-top: 240rpx;
    text-align: center;
    color: $color-text-tertiary;
  }
  .od-card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .od-card__title {
    margin-bottom: $space-2;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .od-head__status {
    display: block;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
  }
  .od-head__status--warn {
    color: $color-warning;
  }
  .od-head__status--primary {
    color: $color-primary;
  }
  .od-head__status--ok {
    color: $color-success;
  }
  .od-head__status--off {
    color: $color-text-tertiary;
  }
  .od-head__no {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .od-head__hint {
    display: block;
    margin-top: $space-2;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .od-head__abnormal {
    display: block;
    margin-top: $space-2;
    padding: $space-2 $space-3;
    border-radius: $radius-sm;
    background: #fdecec;
    color: $color-danger;
    font-size: $font-size-xs;
  }
  .od__row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-top: $space-2;
  }
  .od__row-label {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .od__row-value {
    flex: 1;
    margin-left: $space-3;
    text-align: right;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .od__row-value--strong {
    color: $color-danger;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .od__row-value--link {
    color: $color-primary;
    text-decoration: underline;
  }
  .od__row-value--danger {
    color: $color-danger;
    font-weight: $font-weight-medium;
  }
  .od-aftersale__apply {
    margin-top: $space-3;
    padding: $space-2 0;
    border: 1px solid $color-primary;
    border-radius: $radius-pill;
    text-align: center;
    color: $color-primary;
    font-size: $font-size-sm;
  }
  .od-aftersale__status--warn {
    color: $color-warning;
  }
  .od-aftersale__status--primary {
    color: $color-primary;
  }
  .od-aftersale__status--ok {
    color: $color-success;
  }
  .od-aftersale__status--danger {
    color: $color-danger;
  }
  .od-aftersale__status--off {
    color: $color-text-tertiary;
  }
  .od-step {
    display: flex;
    align-items: flex-start;
    margin-top: $space-2;
  }
  .od-step__dot-wrap {
    width: 24rpx;
    flex-shrink: 0;
    align-self: stretch;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .od-step__dot {
    width: 16rpx;
    height: 16rpx;
    margin-top: 8rpx;
    border-radius: 50%;
    background: $color-border;
  }
  .od-step__dot--done {
    background: $color-primary;
  }
  .od-step__line {
    flex: 1;
    width: 2rpx;
    margin-top: 4rpx;
    background: $color-divider;
  }
  .od-step__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .od-step__title {
    color: $color-text-primary;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .od-step__title--pending {
    color: $color-text-placeholder;
    font-weight: $font-weight-regular;
  }
  .od-step__detail {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .od__line,
  .od__sub {
    display: block;
  }
  .od__line {
    margin-top: $space-1;
    color: $color-text-primary;
    font-size: $font-size-sm;
  }
  .od__sub {
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .od__tip {
    display: block;
    margin-top: $space-2;
    color: $color-warning;
    font-size: $font-size-xs;
  }
  .od-item {
    display: flex;
    align-items: flex-start;
    padding: $space-3 0;
    @include hairline-bottom;
  }
  .od-item__thumb {
    width: 112rpx;
    height: 112rpx;
    flex-shrink: 0;
    border-radius: $radius-md;
    overflow: hidden;
    background: $color-bg-hover;
  }
  .od-item__image {
    width: 100%;
    height: 100%;
  }
  .od-item__thumb-fallback {
    width: 100%;
    height: 100%;
    @include flex-center;
    color: $color-text-tertiary;
  }
  .od-item__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .od-item__name {
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
  }
  .od-item__prices {
    display: flex;
    align-items: baseline;
    margin-top: $space-1;
  }
  .od-item__price {
    color: $color-danger;
    font-size: $font-size-sm;
    font-weight: $font-weight-bold;
  }
  .od-item__actual {
    display: block;
    margin-top: $space-1;
    color: $color-primary;
    font-size: $font-size-xs;
  }
  .od-item__amount {
    margin-left: $space-3;
    text-align: right;
  }
  .od-item__amount-value {
    display: block;
    color: $color-text-primary;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
  }
  .od-item__amount-label {
    display: block;
    margin-top: $space-1;
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .od-log {
    display: flex;
    align-items: flex-start;
    margin-top: $space-2;
  }
  .od-log__dot-wrap {
    width: 24rpx;
    flex-shrink: 0;
    align-items: center;
    align-self: stretch;
    display: flex;
    flex-direction: column;
  }
  .od-log__dot {
    width: 14rpx;
    height: 14rpx;
    margin-top: 8rpx;
    border-radius: 50%;
    background: $color-primary;
  }
  .od-log__line {
    flex: 1;
    width: 2rpx;
    margin-top: 4rpx;
    background: $color-divider;
  }
  .od-log__main {
    flex: 1;
    min-width: 0;
    margin-left: $space-3;
  }
  .od-log__title {
    color: $color-text-primary;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
  }
  .od-log__reason {
    display: block;
    margin-top: $space-1;
    color: $color-text-secondary;
    font-size: $font-size-xs;
  }
  .od-bar {
    @include fixed-bottom-bar;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    height: 108rpx;
    padding: 0 $space-4;
  }
  .od-bar__btn {
    min-width: 180rpx;
    height: 72rpx;
    @include flex-center;
    border: 1px solid $color-border;
    border-radius: $radius-pill;
    color: $color-text-secondary;
    font-size: $font-size-base;
  }
  .od-bar__btn + .od-bar__btn {
    margin-left: $space-3;
  }
  .od-bar__btn--primary {
    border-color: $color-primary;
    background: $color-primary;
    color: $color-text-inverse;
  }
</style>
