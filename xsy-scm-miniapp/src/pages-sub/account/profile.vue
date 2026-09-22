<template>
  <view class="account-profile">
    <PagePlaceholder mark="客户资料" title="客户资料" desc="客户名称、门店、业务员与客户类型等资料展示。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-profile {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
