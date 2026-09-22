<template>
  <view class="account-about">
    <PagePlaceholder mark="关于我们" title="关于我们" desc="平台介绍与版本信息。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .account-about {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
