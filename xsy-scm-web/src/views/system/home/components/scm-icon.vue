<template>
  <img
    class="scm-icon"
    :width="size"
    :height="size"
    :role="title ? 'img' : undefined"
    :alt="title"
    :aria-hidden="title ? undefined : true"
    :src="homeAsset(`icons/svg/${name}.svg`)"
    draggable="false"
  />
</template>

<script setup lang="ts">
import {homeAsset} from '../home-assets';

/** Use the standalone SVG so each icon remains directly addressable and editable. */
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
