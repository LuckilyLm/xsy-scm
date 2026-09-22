<template>
  <view class="order-weigh">
    <PagePlaceholder mark="实重明细" title="实重明细" desc="逐行展示下单量与实际称重量、差额与最终金额。" plan-ref="规划 §19" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .order-weigh {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
