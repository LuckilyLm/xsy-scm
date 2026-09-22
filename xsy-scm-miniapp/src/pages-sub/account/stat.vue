<template>
  <view class="account-stat">
    <PagePlaceholder mark="下单统计" title="下单统计" desc="按周期的下单金额与频次统计。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-stat {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
