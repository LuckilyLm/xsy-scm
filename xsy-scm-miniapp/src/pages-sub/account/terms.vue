<template>
  <view class="account-terms">
    <PagePlaceholder mark="服务条款" title="服务条款" desc="静态条款内容。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-terms {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
