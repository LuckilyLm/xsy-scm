<template>
  <view class="promotion-flash-sale">
    <PagePlaceholder mark="限时抢购" title="限时抢购" desc="活动商品与活动价。" plan-ref="规划 §9.2" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .promotion-flash-sale {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
