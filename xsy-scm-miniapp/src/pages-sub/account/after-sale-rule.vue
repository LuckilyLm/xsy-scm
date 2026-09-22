<template>
  <view class="account-after-sale-rule">
    <PagePlaceholder mark="售后规则" title="售后规则" desc="静态售后规则内容。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-after-sale-rule {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
