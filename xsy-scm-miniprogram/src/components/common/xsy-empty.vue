<template>
  <view class="empty" :class="[`empty--${tone}`, { 'empty--compact': compact }]">
    <view class="empty__icon">
      <uni-icons :type="iconType" :size="iconSize" :color="iconColor" />
    </view>
    <text class="empty__title">{{ title }}</text>
    <text v-if="desc" class="empty__desc">{{ desc }}</text>
    <view v-if="actionText" class="empty__action" hover-class="xsy-press-button--active" :hover-stay-time="80" @click="$emit('action')">
      <text class="empty__action-text">{{ actionText }}</text>
    </view>
  </view>
</template>

<script setup>
  import { computed } from 'vue';

  /*
   * 空态 / 失败态的统一出口。
   *
   * 两种语气刻意分开：
   *   muted  没有数据（空购物车、无订单）—— 中性灰，配一个可选的动作按钮
   *   error  加载失败 —— 红色图标 + 「重试」，不能让用户以为真的没有数据
   * 页面只传文案，图标与配色由这里决定，避免各页自己挑颜色。
   */
  const props = defineProps({
    title: {
      type: String,
      required: true,
    },
    desc: {
      type: String,
      default: '',
    },
    icon: {
      type: String,
      default: '',
    },
    tone: {
      type: String,
      default: 'muted',
    },
    actionText: {
      type: String,
      default: '',
    },
    /** 嵌在卡片或分栏里时用小一号的留白 */
    compact: {
      type: Boolean,
      default: false,
    },
  });

  defineEmits(['action']);

  const iconType = computed(() => props.icon || (props.tone === 'error' ? 'info-filled' : 'info'));
  const iconColor = computed(() => (props.tone === 'error' ? '#e5484d' : '#bbbfc4'));
  const iconSize = computed(() => (props.compact ? 40 : 56));
</script>

<style lang="scss" scoped>
  .empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 120rpx $space-6;
    text-align: center;

    &--compact {
      padding: 48rpx $space-4;
    }

    &__icon {
      @include flex-center;
      width: 128rpx;
      height: 128rpx;
      border-radius: 50%;
      background-color: $color-bg-page;

      .empty--compact & {
        width: 88rpx;
        height: 88rpx;
      }
    }

    &__title {
      margin-top: $space-3;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $color-text-secondary;
    }

    &__desc {
      margin-top: $space-1;
      font-size: $font-size-sm;
      color: $color-text-tertiary;
      line-height: 1.5;
    }

    &__action {
      @include pressable;
      margin-top: $space-5;
      padding: 0 $space-6;
      height: 72rpx;
      border-radius: $radius-pill;
      background-color: var(--mall-primary, #16a34a);
      @include flex-center;
    }

    &--error &__action {
      background-color: $color-bg-card;
      border: 1px solid var(--mall-primary, #16a34a);
    }

    &__action-text {
      font-size: $font-size-base;
      color: $color-text-inverse;
    }

    &--error &__action-text {
      color: var(--mall-primary-ink, #12833c);
    }
  }
</style>
