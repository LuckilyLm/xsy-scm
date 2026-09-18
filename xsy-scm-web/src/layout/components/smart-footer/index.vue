<!--
  * 底部：版权、公司等信息
  *
-->
<template>
  <div class="version">
    <a class="smart-copyright"> ©2026-{{ currentYear }} 鲜蔬源智慧供应链管理平台 </a>
  </div>
</template>
<script setup lang="ts">
  import dayjs from 'dayjs';

  const currentYear = dayjs().year();
</script>

<style lang="less" scoped>
  .version {
    font-size: 14px;
    color: rgba(0, 0, 0, 0.45);
    a {
      color: rgba(0, 0, 0, 0.45);
    }
    a:hover {
      color: @primary-color;
    }
  }
</style>
