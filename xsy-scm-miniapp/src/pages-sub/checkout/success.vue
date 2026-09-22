<template>
  <view class="checkout-success">
    <PagePlaceholder mark="下单成功" title="下单成功" desc="提交结果、订单号与后续动作入口。" plan-ref="规划 §17" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .checkout-success {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
