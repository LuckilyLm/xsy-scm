<template>
  <view v-if="banner" class="banner-card" @click="$emit('click', banner)">
    <!-- 运营图整块作为背景，后续后台换图即可整体替换，无需改首页结构 -->
    <image v-if="banner.imageUrl" class="banner-card__bg" :src="banner.imageUrl" mode="aspectFill" />
    <view v-else class="banner-card__bg banner-card__bg--empty" />

    <!-- 左侧压一层渐变遮罩，保证文字在任何运营图上都可读 -->
    <view class="banner-card__scrim" />

    <view class="banner-card__content">
      <text v-if="banner.eyebrow" class="banner-card__eyebrow">{{ banner.eyebrow }}</text>
      <text class="banner-card__title">{{ banner.title }}</text>
      <text v-if="banner.subtitle" class="banner-card__subtitle">{{ banner.subtitle }}</text>

      <view v-if="banner.ctaText" class="banner-card__cta">
        <text class="banner-card__cta-text">{{ banner.ctaText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  /**
   * Banner V2（Home V2 轻量展示组件）。
   *
   * 结构对齐 Figma「Banner V2」：Eyebrow / 主标题 / 副标题 / CTA，
   * 右侧为运营图区。这里把运营图整块铺底 + 左侧渐变遮罩，
   * 这样后台换图时整块替换即可，不必再次调整首页结构。
   *
   * 数据来自首页楼层 sections 里 type === 'BANNER' 的 item（规划 §9.2）。
   */
  defineProps({
    banner: {
      type: Object,
      default: null,
    },
  });

  defineEmits(['click']);
</script>

<style lang="scss" scoped>
  .banner-card {
    position: relative;
    margin: 0 $space-4;
    /* 对齐 Figma Banner V2 的 358 × 188（390 屏宽下内容区正是 358） */
    height: 360rpx;
    border-radius: $radius-lg;
    overflow: hidden;
    background-color: $color-primary-dark;

    &__bg {
      position: absolute;
      left: 0;
      top: 0;
      width: 100%;
      height: 100%;

      &--empty {
        background: linear-gradient(135deg, $color-primary 0%, $color-primary-dark 100%);
      }
    }

    &__scrim {
      position: absolute;
      left: 0;
      top: 0;
      width: 86%;
      height: 100%;
      background: linear-gradient(90deg, rgba(0, 0, 0, 0.62) 0%, rgba(0, 0, 0, 0) 100%);
    }

    &__content {
      position: relative;
      height: 100%;
      padding: $space-4;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: flex-start;
    }

    &__eyebrow {
      padding: 0 $space-2;
      height: 32rpx;
      line-height: 32rpx;
      border-radius: $radius-pill;
      /* 深色底而非浅色底：浅底 + 白字在运营图上对比度只有 2.7:1，不达 WCAG AA */
      background-color: rgba(0, 0, 0, 0.42);
      color: $color-text-inverse;
      font-size: $font-size-xs;
      white-space: nowrap;
    }

    &__title {
      margin-top: $space-2;
      font-size: $font-size-xl;
      font-weight: $font-weight-bold;
      color: $color-text-inverse;
      line-height: 1.3;
      @include ellipsis(2);
    }

    &__subtitle {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: rgba(255, 255, 255, 0.88);
      @include ellipsis;
    }

    &__cta {
      margin-top: $space-3;
      padding: 0 $space-4;
      height: 56rpx;
      border-radius: $radius-pill;
      background-color: $color-primary;
      @include flex-center;
    }

    &__cta-text {
      font-size: $font-size-sm;
      font-weight: $font-weight-medium;
      color: $color-text-inverse;
      white-space: nowrap;
    }
  }
</style>
