<template>
  <a-tooltip :title="text" :trigger="['hover', 'focus']">
    <span class="scm-field-help" role="button" tabindex="0" :aria-label="`${label}说明`">
      <InfoCircleOutlined/>
    </span>
  </a-tooltip>
</template>

<script setup lang="ts">
import {InfoCircleOutlined} from '@ant-design/icons-vue';

defineProps<{label: string; text: string}>();
</script>

<style scoped lang="less">
.scm-field-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-inline-start: 4px;
  color: var(--scm-text-secondary);
  cursor: help;
}

.scm-field-help:focus-visible {
  border-radius: 4px;
  outline: 2px solid var(--scm-primary);
  outline-offset: 2px;
}
</style>
