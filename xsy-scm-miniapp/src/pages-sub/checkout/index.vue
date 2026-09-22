<template>
  <view class="checkout-index">
    <PagePlaceholder
      mark="结算"
      title="结算"
      desc="地址、配送时间、商品明细、非标品预估、优惠、结算方式、订单备注与价格变化确认。"
      plan-ref="规划 §17"
    />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .checkout-index {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
