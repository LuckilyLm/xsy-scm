<template>
  <view class="account-message">
    <PagePlaceholder mark="消息" title="消息通知" desc="订单状态、配送与活动等通知。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-message {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
