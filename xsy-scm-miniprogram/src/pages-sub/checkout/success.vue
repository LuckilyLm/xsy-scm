<template>
  <view class="done" :style="themeStyle">
    <view class="done__icon">
      <uni-icons type="checkmarkempty" :size="56" :color="COLOR_SUCCESS" />
    </view>
    <text class="done__title">订单已提交</text>
    <text v-if="orderNo" class="done__no">订单号 {{ orderNo }}</text>
    <text v-if="amount" class="done__amount">订货金额 ¥{{ displayMoney(amount) }}</text>
    <template v-if="hasDiscount">
      <text class="done__discount">预估优惠 −¥{{ displayMoney(discount) }}</text>
      <text class="done__payable">预估应付 ¥{{ displayMoney(payable) }}</text>
    </template>
    <text class="done__final">最终确认金额：待后台确认</text>
    <text class="done__discount-tip">优惠在后台确认订单时重新校验，确认结果请查看订单详情。</text>
    <text class="done__tip">后台确认后将按约定时间配送；非标品按实际称重结算。</text>

    <view class="done__actions">
      <view class="done__btn done__btn--ghost" @click="goOrders">查看订单</view>
      <view class="done__btn done__btn--primary" @click="goHome">继续采购</view>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { COLOR_SUCCESS } from '@/constants/theme-color-const';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { displayMoney } from '@/utils/money-display';

  import { onLoad } from '@dcloudio/uni-app';

  const { themeStyle } = useMallTheme();

  const orderId = ref('');
  const orderNo = ref('');
  const amount = ref('');
  const discount = ref('');
  const payable = ref('');
  const hasDiscount = computed(() => !!discount.value && Number(discount.value) > 0 && !!payable.value);

  onLoad((options) => {
    orderId.value = options?.orderId || '';
    orderNo.value = options?.orderNo ? decodeURIComponent(options.orderNo) : '';
    amount.value = options?.amount || '';
    discount.value = options?.discount || '';
    payable.value = options?.payable || '';
  });

  function goOrders() {
    if (orderId.value) {
      uni.navigateTo({ url: `/pages-sub/order/detail?orderId=${orderId.value}` });
      return;
    }
    uni.switchTab({ url: '/pages/order/index' });
  }

  function goHome() {
    uni.switchTab({ url: '/pages/home/index' });
  }
</script>

<style lang="scss" scoped>
  .done {
    @include flex-center;
    flex-direction: column;
    @include viewport-min-height;
    padding: 0 $space-6;
    background-color: $color-bg-page;
  }
  .done__icon {
    width: 160rpx;
    height: 160rpx;
    border-radius: 50%;
    background-color: $color-success-light;
    @include flex-center;
  }
  .done__title {
    margin-top: $space-5;
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
  }
  .done__no {
    margin-top: $space-2;
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .done__amount {
    margin-top: $space-1;
    font-size: $font-size-lg;
    font-weight: $font-weight-bold;
    color: $color-danger;
  }
  .done__discount {
    margin-top: $space-1;
    font-size: $font-size-sm;
    color: $color-text-secondary;
  }
  .done__payable {
    margin-top: $space-1;
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
    color: $color-danger;
  }
  .done__final {
    margin-top: $space-3;
    font-size: $font-size-sm;
    color: $color-text-primary;
  }
  .done__discount-tip {
    margin-top: $space-1;
    font-size: $font-size-xs;
    color: $color-text-tertiary;
    text-align: center;
  }
  .done__tip {
    margin-top: $space-3;
    font-size: $font-size-xs;
    color: $color-text-tertiary;
    text-align: center;
  }
  .done__actions {
    @include flex-between;
    width: 100%;
    margin-top: $space-8;
  }
  .done__btn {
    flex: 1;
    height: 84rpx;
    @include flex-center;
    border-radius: $radius-pill;
    font-size: $font-size-base;
  }
  .done__btn--ghost {
    margin-right: $space-3;
    border: 1px solid $color-border;
    color: $color-text-secondary;
  }
  .done__btn--primary {
    background-color: var(--mall-primary, #16a34a);
    color: white;
  }
</style>
