<template>
  <view class="auth-bind">
    <PagePlaceholder
      mark="客户绑定"
      title="客户绑定与审核状态"
      desc="微信授权后的客户绑定，以及审核中 / 审核未通过的状态展示。"
      plan-ref="规划 §31 / §32"
    />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .auth-bind {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
