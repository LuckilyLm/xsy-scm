<template>
  <view class="favorite-reorder">
    <PagePlaceholder mark="再来一单" title="再来一单确认" desc="按历史订单回填购物车，并处理失效 / 缺货 / 改价异常。" plan-ref="规划 §15" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .favorite-reorder {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
