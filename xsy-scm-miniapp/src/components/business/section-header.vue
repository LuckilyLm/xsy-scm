<template>
  <view class="section-header">
    <view class="section-header__left">
      <view class="section-header__bar" />
      <text class="section-header__title">{{ title }}</text>
      <text v-if="subtitle" class="section-header__subtitle">{{ subtitle }}</text>
    </view>

    <view v-if="moreText" class="section-header__more" @click="$emit('more')">
      <text class="section-header__more-text">{{ moreText }}</text>
      <text class="section-header__more-arrow">›</text>
    </view>
  </view>
</template>

<script setup>
  /**
   * 楼层标题条（Home V2 轻量展示组件）。
   * 只负责标题 / 副标题 / 右侧「更多」的排版，不持有业务状态。
   */
  defineProps({
    title: {
      type: String,
      required: true,
    },
    subtitle: {
      type: String,
      default: '',
    },
    /** 传入才渲染右侧「更多」，空串则不占位 */
    moreText: {
      type: String,
      default: '',
    },
  });

  defineEmits(['more']);
</script>

<style lang="scss" scoped>
  .section-header {
    @include flex-between;
    padding: $space-4 $space-4 $space-3;

    &__left {
      @include flex-start;
      min-width: 0;
    }

    &__bar {
      width: 6rpx;
      height: 32rpx;
      border-radius: $radius-pill;
      background-color: $color-primary;
      flex-shrink: 0;
    }

    &__title {
      margin-left: $space-2;
      font-size: $font-size-lg;
      font-weight: $font-weight-bold;
      color: $color-text-primary;
      @include ellipsis;
    }

    &__subtitle {
      margin-left: $space-2;
      font-size: $font-size-xs;
      color: $color-text-tertiary;
      @include ellipsis;
    }

    &__more {
      @include flex-start;
      flex-shrink: 0;
      margin-left: $space-3;
    }

    &__more-text {
      font-size: $font-size-sm;
      color: $color-text-tertiary;
    }

    &__more-arrow {
      margin-left: $space-1;
      font-size: $font-size-base;
      color: $color-text-tertiary;
    }
  }
</style>
