<template>
  <view class="skeleton" :class="`skeleton--${variant}`">
    <!-- 详情页主图位：尺寸对齐真实主图，避免数据到位后整页下移 -->
    <view v-if="variant === 'detail'" class="skeleton__hero" />

    <view v-for="index in rows" :key="index" class="skeleton__row">
      <!-- 商品行与订单卡都有图位；纯文本骨架不占图位，否则行高会跳 -->
      <view v-if="variant === 'list'" class="skeleton__thumb" />
      <view v-else-if="variant === 'card'" class="skeleton__thumbs">
        <view v-for="thumb in 3" :key="thumb" class="skeleton__thumb skeleton__thumb--sm" />
      </view>

      <view class="skeleton__body">
        <view class="skeleton__line skeleton__line--title" />
        <view class="skeleton__line skeleton__line--medium" />
        <view class="skeleton__line skeleton__line--short" />
      </view>
    </view>
  </view>
</template>

<script setup>
  /*
   * 列表骨架屏。
   *
   * 只在「首屏还没有任何数据」时使用；下拉刷新与翻页沿用原列表，
   * 换成骨架会让已有内容闪一下，反而更差。
   * 尺寸刻意贴合 ProductCard / 订单卡的真实行高，避免数据到位后整页跳动。
   */
  defineProps({
    /** list = 商品行，card = 订单卡，detail = 主图 + 文本块，block = 纯文本块 */
    variant: {
      type: String,
      default: 'list',
    },
    rows: {
      type: Number,
      default: 4,
    },
  });
</script>

<style lang="scss" scoped>
  @keyframes xsy-skeleton-shimmer {
    0% {
      background-position: 140% 0;
    }
    100% {
      background-position: -40% 0;
    }
  }

  .skeleton {
    background-color: $color-bg-card;

    &__row {
      display: flex;
      align-items: flex-start;
      padding: $space-3 $space-4;
      @include hairline-bottom;
    }

    &--card &__row {
      flex-direction: column;
      padding: $space-4;
      @include hairline-bottom($color-divider);
    }

    &__thumb,
    &__line,
    &__hero {
      background-image: linear-gradient(90deg, $color-bg-hover 25%, #f7f8fa 37%, $color-bg-hover 63%);
      background-size: 400% 100%;
      animation: xsy-skeleton-shimmer 1.4s ease infinite;
    }

    &__hero {
      width: 100%;
      height: 560rpx;
    }

    &__thumb {
      flex-shrink: 0;
      width: 152rpx;
      height: 152rpx;
      border-radius: $radius-md;
    }

    &__thumb--sm {
      width: 96rpx;
      height: 96rpx;
      margin-right: $space-2;
    }

    &__thumbs {
      display: flex;
      align-items: center;
    }

    &__body {
      flex: 1;
      min-width: 0;
      margin-left: $space-3;

      .skeleton--card & {
        width: 100%;
        margin-left: 0;
        margin-top: $space-3;
      }
    }

    &__line {
      height: 24rpx;
      border-radius: $radius-sm;

      & + & {
        margin-top: $space-2;
      }

      &--title {
        width: 70%;
        height: 30rpx;
      }

      &--medium {
        width: 48%;
      }

      &--short {
        width: 32%;
      }
    }
  }
</style>
