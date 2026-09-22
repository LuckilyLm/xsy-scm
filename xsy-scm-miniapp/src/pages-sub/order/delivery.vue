<template>
  <view class="order-delivery">
    <PagePlaceholder mark="配送详情" title="配送详情" desc="配送进度与配送信息。" plan-ref="规划 §18" />
  </view>
</template>

<script setup>
  import PagePlaceholder from '@/components/common/page-placeholder.vue';
</script>

<style lang="scss" scoped>
  .order-delivery {
    min-height: 100vh;
    background-color: $color-bg-page;
  }
</style>
