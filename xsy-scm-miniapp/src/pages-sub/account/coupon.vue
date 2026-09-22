<template>
  <view class="account-coupon">
    <PagePlaceholder mark="优惠券" title="优惠券" desc="可用 / 已使用 / 已过期优惠券。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-coupon {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
