<template>
  <view class="profile" :style="themeStyle">
    <view class="profile__card">
      <view v-for="item in rows" :key="item.label" class="profile__row">
        <text class="profile__label">{{ item.label }}</text>
        <text class="profile__value">{{ item.value || '未填写' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
  import { computed } from 'vue';
  import { useMallTheme } from '@/composables/use-mall-theme';
  import { useUserStore } from '@/store/modules/system/user';

  const { themeStyle } = useMallTheme();
  const userStore = useUserStore();
  // 只展示当前登录账号已有资料，不开放客户档案或手机号的自行修改。
  const rows = computed(() => [
    { label: '客户名称', value: userStore.customerName },
    { label: '登录手机号', value: userStore.phone },
  ]);
</script>

<style lang="scss" scoped>
  .profile {
    @include viewport-min-height;
    padding: $space-4 var(--xsy-page-x);
    background: $color-bg-page;
  }
  .profile__card {
    @include card(0);
    padding: 0 $space-4;
  }
  .profile__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $space-4;
    min-height: 56px;
    padding: $space-3 0;
    & + & {
      border-top: 1px solid $color-border;
    }
  }
  .profile__label {
    flex-shrink: 0;
    color: $color-text-secondary;
    font-size: $font-size-sm;
  }
  .profile__value {
    color: $color-text-primary;
    font-size: $font-size-base;
    text-align: right;
    word-break: break-all;
  }
</style>
