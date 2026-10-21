<template>
  <span class="xsy-brand-lockup" :class="{'xsy-brand-lockup--dark': dark}">
    <img :src="dark ? darkMark : mark" alt="" aria-hidden="true"/>
    <span>鲜蔬园</span>
  </span>
</template>

<script setup lang="ts">
import mark from '/@/assets/images/logo/xsy-logo-min.png';
import darkMark from '/@/assets/images/logo/xsy-logo-min-white.png';

defineProps<{dark?: boolean}>();
</script>

<style scoped>
.xsy-brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  color: #146b3a;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.xsy-brand-lockup img {
  flex: none;
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.xsy-brand-lockup--dark {
  color: #fff;
}
</style>
