<template>
  <view class="address-list">
    <PagePlaceholder mark="收货地址" title="收货地址" desc="地址列表、设为默认，以及新增 / 编辑入口。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .address-list {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
