<!--
  表单 / 弹窗顶部的规则说明块（SCM 与框架页共用，因此不放在任何一方的目录里）。

  取代原来那种整块 `a-alert type="info"`：它自带 16px 内边距 + 16px 外边距，
  在 860px 宽的弹窗里会占掉一百多像素高，而里面只是一两句静态说明 ——
  弹窗里最重的元素不该是一段说明。这里收成「1px 边框 + 3px 左侧竖条 + 浅底」，
  让说明退到它该在的位置。

  一行用段落，多行自动成列表：单条规则配个项目符号只是噪声。

  ⚠️ 用 `lines` 数组而不是 slot：Vue 的 scoped 样式管不到 slot 内容，
  列表符号与行高会丢在父组件的样式作用域里。
-->
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
