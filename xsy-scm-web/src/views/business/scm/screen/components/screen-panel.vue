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
/**
 * 大屏统一面板壳。
 *
 * <p>存在的意义是**统一**：设计稿要求所有内容块共用同一套「半透明深底 + 细边框 + 标题条」，
 * 如果每个业务组件各写一遍，最后必然出现某块面板边框颜色或圆角对不上。
 *
 * <p>{@code flush} 与 {@code flex} 是给图表用的：
 * ECharts 的绘图区需要贴边（内边距会把轴标签挤掉），且容器必须有确定的高度，
 * 否则 `echarts.init` 拿到 0 高度后图表不渲染。
 */
defineProps<{
  /** 面板标题 */
  title?: string;
  /** body 用 flex 纵向排列（图表 / 列表撑满时用） */
  flex?: boolean;
  /** 去掉 body 内边距（图表贴边） */
  flush?: boolean;
}>();
</script>
