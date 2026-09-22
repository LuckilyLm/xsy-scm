<template>
  <view class="product-card" @click="$emit('click', product)">
    <!-- 商品图：业主提供 fileKey 前用首字占位 -->
    <view class="product-card__thumb">
      <image v-if="product.imageUrl" class="product-card__image" :src="product.imageUrl" mode="aspectFill" />
      <text v-else class="product-card__thumb-text">{{ thumb }}</text>
    </view>

    <view class="product-card__main">
      <view class="product-card__title-row">
        <text class="product-card__name">{{ product.productName }}</text>
        <!-- 非标品按实重结算，是 XSY 与普通电商的关键差异，必须显式标注 -->
        <text v-if="product.isNonStandard" class="product-card__badge">按实重</text>
      </view>

      <text class="product-card__spec">{{ product.spec }}</text>

      <view class="product-card__price-row">
        <!-- 无报价时展示「询价」，绝不显示 ¥0.00（规划 §11.3） -->
        <template v-if="priced">
          <text class="product-card__price-symbol">¥</text>
          <text class="product-card__price">{{ product.price }}</text>
          <text class="product-card__unit">/{{ product.unit }}</text>
        </template>
        <text v-else class="product-card__price--unpriced">询价</text>

        <text class="product-card__source">{{ sourceLabel }}</text>
      </view>

      <view class="product-card__stock-row">
        <text class="product-card__stock" :class="`product-card__stock--${level}`">{{ text }}</text>
        <text v-if="showQty" class="product-card__qty">可售 {{ product.availableQty }}</text>
      </view>
    </view>

    <view class="product-card__action" :class="{ 'product-card__action--disabled': !orderable }" @click.stop="$emit('add', product)">
      <text class="product-card__action-text">{{ action }}</text>
    </view>
  </view>
</template>

<script setup>
  import { computed } from 'vue';
  import { actionText, hasPrice, isOrderable, priceSourceLabel, showAvailableQty, stockLevel, stockText, thumbText } from '@/utils/product-display';

  const props = defineProps({
    product: {
      type: Object,
      required: true,
    },
  });

  defineEmits(['click', 'add']);

  /*
   * 库存文案 / 价格口径 / 按钮可用性统一来自 @/utils/product-display，
   * 与详情页共用同一套规则，避免两处判断漂移。
   */
  const text = computed(() => stockText(props.product));
  const level = computed(() => stockLevel(props.product));
  const showQty = computed(() => showAvailableQty(props.product));

  const priced = computed(() => hasPrice(props.product));
  const orderable = computed(() => isOrderable(props.product));
  const action = computed(() => actionText(props.product));
  const thumb = computed(() => thumbText(props.product));
  const sourceLabel = computed(() => priceSourceLabel(props.product));
</script>

<style lang="scss" scoped>
  .product-card {
    @include flex-start;
    align-items: stretch;
    padding: $space-3 $space-4;
    background-color: $color-bg-card;
    @include hairline-bottom;

    &__thumb {
      width: 140rpx;
      height: 140rpx;
      border-radius: $radius-md;
      background-color: $color-primary-light;
      @include flex-center;
      flex-shrink: 0;
      overflow: hidden;
    }

    &__image {
      width: 100%;
      height: 100%;
    }

    &__thumb-text {
      font-size: $font-size-xl;
      font-weight: $font-weight-medium;
      color: $color-primary;
    }

    &__main {
      flex: 1;
      min-width: 0;
      margin-left: $space-3;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    &__title-row {
      @include flex-start;
    }

    &__name {
      flex: 1;
      min-width: 0;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
      @include ellipsis;
    }

    &__badge {
      flex-shrink: 0;
      margin-left: $space-2;
      padding: 0 $space-1;
      height: 32rpx;
      line-height: 32rpx;
      border-radius: $radius-sm;
      background-color: $color-warning-light;
      color: $color-warning;
      font-size: $font-size-xs;
    }

    &__spec {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: $color-text-tertiary;
      @include ellipsis;
    }

    &__price-row {
      @include flex-start;
      margin-top: $space-1;
    }

    &__price-symbol {
      font-size: $font-size-sm;
      color: $color-danger;
    }

    &__price {
      font-size: $font-size-lg;
      font-weight: $font-weight-bold;
      color: $color-danger;
    }

    &__unit {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__price--unpriced {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-secondary;
    }

    &__source {
      margin-left: $space-2;
      padding: 0 $space-1;
      height: 30rpx;
      line-height: 30rpx;
      border-radius: $radius-sm;
      background-color: $color-bg-page;
      color: $color-text-tertiary;
      font-size: $font-size-xs;
    }

    &__stock-row {
      @include flex-start;
      margin-top: $space-1;
    }

    &__stock {
      font-size: $font-size-xs;

      &--ok {
        color: $color-success;
      }

      &--warn {
        color: $color-warning;
      }

      &--off {
        color: $color-text-tertiary;
      }
    }

    &__qty {
      margin-left: $space-2;
      font-size: $font-size-xs;
      color: $color-text-tertiary;
    }

    &__action {
      align-self: flex-end;
      flex-shrink: 0;
      margin-left: $space-2;
      padding: 0 $space-3;
      height: 56rpx;
      border-radius: $radius-pill;
      background-color: $color-primary;
      @include flex-center;

      &--disabled {
        background-color: $color-primary-disabled;
      }
    }

    &__action-text {
      font-size: $font-size-sm;
      color: $color-text-inverse;
    }
  }
</style>
