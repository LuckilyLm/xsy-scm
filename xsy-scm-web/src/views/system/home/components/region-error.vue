<!--
  * 区块加载失败的提示
  *
  * 首页有多个互不相关的数据源，失败只影响自己那一块：这里给一句可读的原因和一个重试入口，
  * 不弹全局提示、不把整页变成错误页。
-->
<template>
  <a-alert class="home-region-error" type="warning" show-icon :message="message">
    <template #action>
      <a-button size="small" @click="emit('retry')">重试</a-button>
    </template>
  </a-alert>
</template>

<script setup lang="ts">
defineProps<{message: string}>();
const emit = defineEmits(['retry']);
</script>

<style lang="less" scoped>
.home-region-error {
  margin-bottom: 0;
}
</style>
