<template>
  <view class="order-detail">
    <PagePlaceholder
      mark="订单详情"
      title="订单详情"
      desc="突出「买了什么 / 订了多少 / 最后称了多少 / 多少钱 / 什么时候送 / 送到哪 / 现在到哪了」。"
      plan-ref="规划 §18.1"
    />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .order-detail {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
