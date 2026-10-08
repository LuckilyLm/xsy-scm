<template>
  <svg
    class="scm-icon"
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    :role="title ? 'img' : undefined"
    :aria-label="title || undefined"
    :aria-hidden="title ? undefined : true"
    focusable="false"
  >
    <use :href="`${homeAsset('icons/xsy-icons.svg')}#icon-${name}`" />
  </svg>
</template>

<script setup lang="ts">
import {homeAsset} from '../home-assets';

withDefaults(defineProps<{
  name: string;
  size?: number | string;
  title?: string;
}>(), {size: 24, title: ''});
</script>

<style scoped lang="less">
.scm-icon {
  display: inline-block;
  flex-shrink: 0;
  overflow: visible;
  vertical-align: -0.125em;
}
</style>
