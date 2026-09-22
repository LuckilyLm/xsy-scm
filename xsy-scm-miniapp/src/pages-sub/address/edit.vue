<template>
  <view class="address-edit">
    <PagePlaceholder mark="地址编辑" title="地址编辑" desc="收货人、联系电话、所在区域与详细地址维护。" plan-ref="规划 §20" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .address-edit {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
