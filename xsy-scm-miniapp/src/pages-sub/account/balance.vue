<template>
  <view class="account-balance">
    <PagePlaceholder mark="余额" title="余额" desc="账户余额与流水。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-balance {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
