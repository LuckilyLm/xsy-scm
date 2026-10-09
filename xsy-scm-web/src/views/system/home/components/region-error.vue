<!--
  * 区块加载失败的提示
  *
  * 首页有多个互不相关的数据源，失败只影响自己那一块：这里给一句可读的原因和一个重试入口，
  * 不弹全局提示、不把整页变成错误页。
-->
<template>
  <a-empty
    class="home-region-error"
    role="alert"
    aria-live="polite"
    :description="message"
    :style="{minHeight: minHeight + 'px'}"
  >
    <template #image>
      <exclamation-circle-outlined class="home-region-error__icon" aria-hidden="true"/>
    </template>
    <a-button size="small" @click="emit('retry')">重试</a-button>
  </a-empty>
</template>

<script setup lang="ts">
withDefaults(defineProps<{message: string; minHeight?: number}>(), {minHeight: 200});
const emit = defineEmits(['retry']);
</script>

<style lang="less" scoped>
.home-region-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin: 0;
}

:deep(.ant-empty-image) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  margin-bottom: 8px;
  font-size: 32px;
  color: var(--scm-error);
}

:deep(.ant-empty-description) {
  max-width: 100%;
  margin-bottom: 8px;
  color: var(--scm-text-secondary);
  overflow-wrap: anywhere;
}
</style>
