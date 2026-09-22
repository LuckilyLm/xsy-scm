<template>
  <view class="account-setting">
    <PagePlaceholder mark="设置" title="设置" desc="账号与绑定、缓存清理与退出登录。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-setting {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
