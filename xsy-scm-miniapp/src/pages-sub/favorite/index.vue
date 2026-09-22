<template>
  <view class="favorite-index">
    <PagePlaceholder mark="常购" title="常购商品" desc="常购商品、最近采购与快速加购。" plan-ref="规划 §14" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .favorite-index {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
