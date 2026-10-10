<template>
  <view class="done">
    <view class="done__icon">
      <uni-icons type="checkmarkempty" :size="56" :color="COLOR_PRIMARY" />
    </view>
    <text class="done__title">订单已提交</text>
    <text v-if="orderNo" class="done__no">订单号 {{ orderNo }}</text>
    <text v-if="amount" class="done__amount">订单金额 ¥{{ amount }}</text>
    <text class="done__tip">后台确认后将按约定时间配送；非标品按实际称重结算。</text>

    <view class="done__actions">
      <view class="done__btn done__btn--ghost" @click="goOrders">查看订单</view>
      <view class="done__btn done__btn--primary" @click="goHome">继续采购</view>
    </view>
  </view>
</template>

<script setup>
  import { ref } from 'vue';
  import { onLoad } from '@dcloudio/uni-app';
  import { COLOR_PRIMARY } from '@/constants/theme-color-const';

  const orderNo = ref('');
  const amount = ref('');

  onLoad((options) => {
    orderNo.value = options?.orderNo ? decodeURIComponent(options.orderNo) : '';
    amount.value = options?.amount || '';
  });

  function goOrders() {
    // 订单中心尚未开放：如实提示，不做假跳转
    uni.showToast({ title: '订单中心将在下一阶段开放', icon: 'none' });
  }

  function goHome() {
    uni.switchTab({ url: '/pages/home/index' });
  }
</script>

<style lang="scss" scoped>
  .done { @include flex-center; flex-direction: column; min-height: 100vh; padding: 0 $space-6; background-color: $color-bg-page; }
  .done__icon { width: 160rpx; height: 160rpx; border-radius: 50%; background-color: $color-primary-light; @include flex-center; }
  .done__title { margin-top: $space-5; font-size: $font-size-xl; font-weight: $font-weight-bold; color: $color-text-primary; }
  .done__no { margin-top: $space-2; font-size: $font-size-sm; color: $color-text-secondary; }
  .done__amount { margin-top: $space-1; font-size: $font-size-lg; font-weight: $font-weight-bold; color: $color-danger; }
  .done__tip { margin-top: $space-3; font-size: $font-size-xs; color: $color-text-tertiary; text-align: center; }
  .done__actions { @include flex-between; width: 100%; margin-top: $space-8; }
  .done__btn { flex: 1; height: 84rpx; @include flex-center; border-radius: $radius-pill; font-size: $font-size-base; }
  .done__btn--ghost { margin-right: $space-3; border: 1px solid $color-border; color: $color-text-secondary; }
  .done__btn--primary { background-color: $color-primary; color: white; }
</style>
