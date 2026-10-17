<template>
  <view class="product-card" :class="`product-card--${state}`" @click="$emit('click', product)">
    <!-- 商品图：有图走真实图片，无图或加载失败都回退到首字占位 -->
    <view class="product-card__thumb">
      <image
        v-if="product.imageUrl && !imageFailed"
        class="product-card__image"
        :src="product.imageUrl"
        mode="aspectFill"
        lazy-load
        @error="onImageError"
      />
      <view v-else class="product-card__thumb-fallback">
        <text class="product-card__thumb-text">{{ thumb }}</text>
      </view>
    </view>

    <view class="product-card__main">
      <!-- 商品名称：最多两行，超出截断 -->
      <text class="product-card__name">{{ product.productName }}</text>

      <!-- 规格 / 分类 -->
      <view class="product-card__meta-row">
        <text class="product-card__spec">{{ specText }}</text>
        <!-- 非标品按实重结算，是 XSY 与普通电商的关键差异，必须显式标注 -->
        <text v-if="nonStandard" class="product-card__badge">按实重</text>
      </view>

      <!--
        价格类型标签独立成行。
        旧版把「价格 + 单位 + 价格类型 + 库存」塞进同一横排，窄屏下必然互相挤压
        （实测 123.6px 行宽撑不下，单位被压成两行）。这里拆行后各段互不争抢。
        无客户价时不展示：价格位已经写了「询价」，再挂「暂无报价」属于重复信息。
      -->
      <view v-if="sourceLabel && priced" class="product-card__tag-row">
        <text class="product-card__source" :class="`product-card__source--${sourceLevel}`">
          {{ sourceLabel }}
        </text>
      </view>

      <view class="product-card__price-row">
        <!-- 无报价时展示「询价」，绝不显示 ¥0.00（规划 §11.3） -->
        <template v-if="priced">
          <text class="product-card__price-symbol">¥</text>
          <text class="product-card__price">{{ product.price }}</text>
          <text class="product-card__unit">/{{ product.unit }}</text>
        </template>
        <text v-else class="product-card__price--unpriced">询价</text>
      </view>

      <view class="product-card__stock-row">
        <text class="product-card__stock" :class="`product-card__stock--${level}`">{{ text }}</text>
        <text v-if="showQty" class="product-card__qty">可售 {{ product.availableQty }}</text>
      </view>
    </view>

    <view class="product-card__action" :class="`product-card__action--${actionKind}`" @click.stop="onAction">
      <text class="product-card__action-text">{{ actionLabel }}</text>
    </view>
  </view>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import {
    hasPrice,
    isNonStandard,
    actionText,
    isOrderable,
    priceSourceLabel,
    priceSourceLevel,
    showAvailableQty,
    specCategoryText,
    stockLevel,
    stockText,
    thumbText,
  } from '@/utils/product-display';

  const props = defineProps({
    product: {
      type: Object,
      required: true,
    },
  });

  const emit = defineEmits(['click', 'add', 'inquiry']);

  /*
   * 库存文案 / 价格口径 / 按钮可用性统一来自 @/utils/product-display，
   * 与详情页共用同一套规则，避免两处判断漂移。
   */
  const text = computed(() => stockText(props.product));
  const level = computed(() => stockLevel(props.product));
  const showQty = computed(() => showAvailableQty(props.product));

  const priced = computed(() => hasPrice(props.product));
  const orderable = computed(() => isOrderable(props.product));
  const actionLabel = computed(() => actionText(props.product));
  const thumb = computed(() => thumbText(props.product));
  const sourceLabel = computed(() => priceSourceLabel(props.product));
  const sourceLevel = computed(() => priceSourceLevel(props.product));
  const specText = computed(() => specCategoryText(props.product));
  const nonStandard = computed(() => isNonStandard(props.product));

  /** 卡片状态修饰类：ok / warn / off / unpriced */
  const state = computed(() => (priced.value ? level.value : 'unpriced'));

  const actionKind = computed(() => {
    if (!priced.value) return 'inquiry';
    if (!orderable.value) return 'disabled';
    // 缺货商品走「预订」样式：仍可下单，但视觉上区别于现货
    return props.product.stockStatus === 'OUT_OF_STOCK' ? 'preorder' : 'add';
  });

  function onAction() {
    if (actionKind.value === 'inquiry') {
      emit('inquiry', props.product);
    } else if (actionKind.value === 'add' || actionKind.value === 'preorder') {
      emit('add', props.product);
    }
  }

  /*
   * 图片加载失败的兜底。
   *
   * 商品图是本地 static 素材（见 mock/fixtures.js 的说明），仓库里只保留每品类少量样本，
   * 完整素材留在开发机本地。所以干净检出时部分 imageUrl 指向的文件并不存在——
   * 这里必须退回首字占位，而不是让用户看到一张裂图。
   * 生产环境同理：后端 fileKey 失效时也不该出现裂图。
   */
  const imageFailed = ref(false);

  function onImageError() {
    imageFailed.value = true;
  }

  // 列表复用组件实例时，换了商品要重新给图片一次机会
  watch(
    () => props.product && props.product.imageUrl,
    () => {
      imageFailed.value = false;
    }
  );
</script>

<style lang="scss" scoped>
  .product-card {
    @include flex-start;
    align-items: stretch;
    padding: $space-3 $space-4;
    background-color: $color-bg-card;
    @include hairline-bottom;

    &__thumb {
      width: 152rpx;
      height: 152rpx;
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

    &__thumb-fallback {
      width: 100%;
      height: 100%;
      background-color: $color-primary-light;
      @include flex-center;
    }

    &__thumb-text {
      font-size: 48rpx;
      line-height: 1;
      font-weight: $font-weight-medium;
      color: $color-primary;
    }

    /* 缺货时图片降透明度，避免与在售商品混淆 */
    &--off &__image {
      opacity: 0.45;
    }

    &__main {
      flex: 1;
      min-width: 0;
      margin-left: $space-3;
      display: flex;
      flex-direction: column;
    }

    &__name {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
      line-height: 1.35;
      /* 商品名最多两行 */
      @include ellipsis(2);
    }

    &__meta-row {
      @include flex-start;
      margin-top: $space-1;
      min-width: 0;
    }

    &__spec {
      /* 不占满整行，让「按实重」标签紧跟规格文字，而不是被推到卡片最右侧 */
      flex: 0 1 auto;
      min-width: 0;
      font-size: $font-size-sm;
      color: $color-text-tertiary;
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
      white-space: nowrap;
    }

    /* 价格类型标签：独立一行，不与价格争抢横向空间 */
    &__tag-row {
      @include flex-start;
      margin-top: $space-1;
    }

    &__source {
      flex-shrink: 0;
      padding: 0 $space-1;
      height: 30rpx;
      line-height: 30rpx;
      border-radius: $radius-sm;
      font-size: $font-size-xs;
      white-space: nowrap;

      &--agreement {
        background-color: $color-primary-light;
        color: $color-primary;
      }

      &--type {
        background-color: $color-info-light;
        color: $color-info;
      }

      &--standard {
        background-color: $color-bg-page;
        color: $color-text-tertiary;
      }

      &--unpriced {
        background-color: $color-warning-light;
        color: $color-warning;
      }
    }

    &__price-row {
      @include flex-start;
      margin-top: $space-1;
      /* 价格与单位整体不换行，避免单位被挤到第二行 */
      flex-wrap: nowrap;
      min-width: 0;
    }

    &__price-symbol {
      flex-shrink: 0;
      font-size: $font-size-sm;
      color: $color-danger;
      white-space: nowrap;
    }

    &__price {
      flex-shrink: 0;
      font-size: $font-size-lg;
      font-weight: $font-weight-bold;
      color: $color-danger;
      white-space: nowrap;
    }

    &__unit {
      flex-shrink: 0;
      font-size: $font-size-sm;
      color: $color-text-tertiary;
      white-space: nowrap;
    }

    &__price--unpriced {
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-secondary;
      white-space: nowrap;
    }

    &__stock-row {
      @include flex-start;
      margin-top: $space-2;
      min-width: 0;
    }

    &__stock {
      flex-shrink: 0;
      font-size: $font-size-xs;
      white-space: nowrap;

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
      @include ellipsis;
    }

    &__action {
      align-self: flex-end;
      flex-shrink: 0;
      margin-left: $space-3;
      padding: 0 $space-4;
      height: 60rpx;
      border-radius: $radius-pill;
      @include flex-center;

      /* 主操作：实心品牌绿，白字，与询价（描边）区分 */
      &--add {
        background-color: $color-primary;
      }

      /* 缺货预订：描边样式，可点击但视觉弱于现货 */
      &--preorder {
        background-color: $color-bg-card;
        border: 1px solid $color-primary;
      }

      &--unavailable {
        background-color: $color-bg-hover;
        border: 1px solid $color-border;
      }

      &--inquiry {
        background-color: $color-bg-card;
        border: 1px solid $color-primary;
      }

      &--disabled {
        background-color: $color-bg-hover;
      }
    }

    &__action-text {
      font-size: $font-size-sm;
      white-space: nowrap;
      color: $color-text-inverse;
    }

    &__action--inquiry &__action-text {
      color: $color-primary;
    }

    &__action--preorder &__action-text {
      color: $color-primary;
    }

    &__action--unavailable &__action-text {
      color: $color-text-secondary;
    }

    &__action--disabled &__action-text {
      color: $color-text-tertiary;
    }
  }
</style>
