<!-- 表单顶部的静态说明使用紧凑提示组件，避免占用过多空间；业务操作错误仍使用明确的错误提示。 -->
<template>
  <section class="form-note">
    <p v-if="title" class="form-note__title">{{ title }}</p>
    <p v-if="lines.length === 1" class="form-note__line">{{ lines[0] }}</p>
    <ul v-else-if="lines.length > 1" class="form-note__list">
      <li v-for="line in lines" :key="line">{{ line }}</li>
    </ul>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  /** 说明块的小标题；只有一句话、不需要标题时省略 */
  title?: string;
  /** 说明正文，一行一段；两行及以上会渲染成列表 */
  lines: string[];
}>();
</script>

<style lang="less" scoped>
.form-note {
  margin-bottom: 14px;
  padding: 10px 14px 12px;
  /* 框架页没有 --scm-* 变量，取值全部带兜底 */
  border: 1px solid var(--scm-border, rgba(0, 0, 0, 0.06));
  border-left: 3px solid var(--scm-primary, #00b96b);
  border-radius: 6px;
  background: var(--scm-fill, rgba(0, 0, 0, 0.02));
  font-size: 12px;
  line-height: 20px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.65));
}

.form-note__title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  color: var(--scm-text, rgba(0, 0, 0, 0.88));
}

.form-note__line {
  margin: 0;
}

.form-note__list {
  margin: 0;
  padding-left: 16px;
}

.form-note__list li + li {
  margin-top: 2px;
}
</style>
