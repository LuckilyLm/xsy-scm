<template>
  <view class="nav-bar" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="nav-bar__bar" :style="{ height: navBarHeight + 'px' }">
      <view class="nav-bar__side nav-bar__side--left">
        <slot name="left"></slot>
      </view>

      <view class="nav-bar__title">
        <slot>
          <text class="nav-bar__title-text">{{ title }}</text>
        </slot>
      </view>

      <view class="nav-bar__side nav-bar__side--right">
        <slot name="right"></slot>
      </view>
    </view>

    <!-- 标题栏下方的扩展区（如首页搜索框、分类搜索框） -->
    <view v-if="divider" class="nav-bar__divider" />

    <slot name="bottom"></slot>
  </view>
</template>

<script setup>
  import { useSystemLayout } from '@/composables/use-system-layout';

  defineProps({
    /** 标题文案，默认插槽存在时忽略 */
    title: {
      type: String,
      default: '',
    },
    /**
     * 是否在标题栏与底部扩展区之间渲染 1px 分割线。
     *
     * 默认 false —— 只有设计稿明确要求分割线的页面（当前仅 Category / Final）才传，
     * 避免影响购物车 / 订单等既有页面的视觉。
     */
    divider: {
      type: Boolean,
      default: false,
    },
  });

  const { statusBarHeight, navBarHeight } = useSystemLayout();
</script>

<style lang="scss" scoped>
  .nav-bar {
    background-color: $color-bg-card;

    &__bar {
      @include flex-between;
      padding: 0 $space-4;
    }

    &__side {
      @include flex-start;
      min-width: 88rpx;

      &--right {
        justify-content: flex-end;
      }
    }

    &__title {
      flex: 1;
      @include flex-center;
      min-width: 0;
    }

    &__title-text {
      font-size: $font-size-lg;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
      @include ellipsis;
    }

    /* 标题栏下方的 1px 分割线（可选，见 divider prop） */
    &__divider {
      height: 1px;
      background-color: $color-divider;
    }
  }
</style>
