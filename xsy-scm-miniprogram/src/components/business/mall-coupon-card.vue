<template>
  <view class="coupon-card">
    <view class="coupon-card__top">
      <view class="coupon-card__benefit">{{ couponBenefit(coupon) }}</view>
      <view class="coupon-card__main">
        <text class="coupon-card__name">{{ coupon.couponName }}</text>
        <text class="coupon-card__threshold">{{ couponThreshold(coupon) }}</text>
      </view>
    </view>
    <view class="coupon-card__bottom">
      <text class="coupon-card__window">{{ couponWindow(coupon) }}</text>
      <text v-if="actionLabel" class="coupon-card__action" :class="{ 'coupon-card__action--disabled': disabled }" @click="onAction">
        {{ actionLabel }}
      </text>
      <text v-else-if="statusLabel" class="coupon-card__status">{{ statusLabel }}</text>
    </view>
  </view>
</template>

<script setup>
  import { couponBenefit, couponThreshold, couponWindow } from '@/utils/promotion-display';

  const props = defineProps({
    coupon: { type: Object, required: true },
    actionLabel: { type: String, default: '' },
    statusLabel: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  });
  const emit = defineEmits(['action']);

  function onAction() {
    if (!props.disabled) emit('action', props.coupon);
  }
</script>

<style lang="scss" scoped>
  .coupon-card {
    margin: $space-3 $space-4 0;
    padding: $space-4;
    @include card(0);
  }
  .coupon-card__top {
    display: flex;
    align-items: center;
    gap: $space-4;
  }
  .coupon-card__benefit {
    min-width: 144rpx;
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    font-variant-numeric: tabular-nums;
  }
  .coupon-card__main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: $space-2;
  }
  .coupon-card__name {
    color: $color-text-primary;
    font-weight: $font-weight-medium;
    @include ellipsis;
  }
  .coupon-card__threshold {
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .coupon-card__bottom {
    margin-top: $space-4;
    padding-top: $space-3;
    @include flex-between;
    @include hairline-top;
    gap: $space-2;
  }
  .coupon-card__window {
    color: $color-text-tertiary;
    font-size: $font-size-xs;
  }
  .coupon-card__action {
    color: var(--mall-primary-ink, #12833c);
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    flex-shrink: 0;
  }
  .coupon-card__action--disabled,
  .coupon-card__status {
    color: $color-text-tertiary;
  }
  .coupon-card__status {
    font-size: $font-size-sm;
  }
</style>
