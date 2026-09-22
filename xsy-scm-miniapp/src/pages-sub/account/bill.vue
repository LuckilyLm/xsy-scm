<template>
  <view class="account-bill">
    <PagePlaceholder mark="对账" title="对账" desc="待对账与历史对账。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-bill {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
