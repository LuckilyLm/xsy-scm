<template>
  <span class="xsy-brand-lockup">
    <img :src="dark ? darkMark : mark" alt="鲜蔬园"/>
  </span>
</template>

<script setup lang="ts">
import mark from '/@/assets/images/logo/xsy-logo.png';
import darkMark from '/@/assets/images/logo/xsy-logo-white.png';

defineProps<{dark?: boolean}>();
</script>

<style scoped>
.xsy-brand-lockup {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
}

.xsy-brand-lockup img {
  flex: none;
  width: auto;
  height: 32px;
  max-width: 100%;
  object-fit: contain;
}
</style>
