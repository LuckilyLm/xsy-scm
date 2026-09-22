<template>
  <view class="placeholder">
    <view class="placeholder__mark">
      <text class="placeholder__mark-text">{{ mark }}</text>
    </view>

    <text class="placeholder__title">{{ title }}</text>
    <text v-if="desc" class="placeholder__desc">{{ desc }}</text>
    <text v-if="planRef" class="placeholder__ref">{{ planRef }}</text>
  </view>
</template>

<script setup>
  /**
   * 未开发页面的占位块。
   *
   * 用途：Step 2 阶段先让 pages.json 引用到真实存在的页面（否则构建会失败），
   * 同时把「这页将来做什么、依据哪一节规划」直接写在页面上，避免后续接手
   * 时再翻文档。
   *
   * 页面进入垂直切片开发后，本组件应被真实内容替换掉。
   */
  defineProps({
    /** 占位标记文字，一般用页面简称 */
    mark: {
      type: String,
      default: '待开发',
    },
    /** 页面名称 */
    title: {
      type: String,
      required: true,
    },
    /** 一句话说明这页要做什么 */
    desc: {
      type: String,
      default: '',
    },
    /** 规划文档出处，如「规划 §7」 */
    planRef: {
      type: String,
      default: '',
    },
  });
</script>

<style lang="scss" scoped>
  .placeholder {
    @include flex-center;
    flex-direction: column;
    padding: $space-8 $space-6;
    text-align: center;

    &__mark {
      width: 160rpx;
      height: 160rpx;
      border-radius: 50%;
      background-color: $color-primary-light;
      @include flex-center;
    }

    &__mark-text {
      font-size: $font-size-sm;
      color: $color-primary;
      font-weight: $font-weight-medium;
    }

    &__title {
      margin-top: $space-4;
      font-size: $font-size-lg;
      font-weight: $font-weight-medium;
      color: $color-text-primary;
    }

    &__desc {
      margin-top: $space-2;
      font-size: $font-size-sm;
      color: $color-text-secondary;
      line-height: 1.6;
    }

    &__ref {
      margin-top: $space-3;
      font-size: $font-size-xs;
      color: $color-text-tertiary;
    }
  }
</style>
