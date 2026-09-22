<template>
  <view class="account-credit">
    <PagePlaceholder mark="账期" title="账期" desc="可用账期与账期明细。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-credit {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
