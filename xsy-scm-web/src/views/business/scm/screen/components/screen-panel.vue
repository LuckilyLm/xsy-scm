<template>
  <section class="scm-panel">
    <header v-if="title || $slots.title || $slots.extra" class="scm-panel-head">
      <slot name="title">
        <span class="scm-panel-title">{{ title }}</span>
      </slot>
      <div v-if="$slots.extra" class="scm-panel-extra">
        <slot name="extra"/>
      </div>
    </header>
    <div class="scm-panel-body" :class="{ 'is-flex': flex, 'is-flush': flush }">
      <slot/>
    </div>
  </section>
</template>

<script setup lang="ts">
/** 统一大屏标题、背景和边框。图表容器可以取消内边距，但必须有确定高度才能初始化。 */
defineProps<{
  /** 面板标题 */
  title?: string;
  /** body 用 flex 纵向排列（图表 / 列表撑满时用） */
  flex?: boolean;
  /** 去掉 body 内边距（图表贴边） */
  flush?: boolean;
}>();
</script>
