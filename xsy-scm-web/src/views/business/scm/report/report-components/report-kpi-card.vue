<template>
  <a-card size="small" :class="['report-kpi', layout === 'strip' ? 'report-kpi--strip' : '']">
    <!-- 时点指标通常只有一条，用整行横条承载说明文字，避免孤立卡片和空洞留白 -->
    <template v-if="layout === 'strip'">
      <span class="report-kpi-label">{{ label }}</span>
      <span class="report-kpi-value" :class="tone ? `report-kpi-value--${tone}` : ''">{{ value }}</span>
      <p v-if="sub || warning" class="report-kpi-strip__meta">
        <span v-if="sub">{{ sub }}</span>
        <span v-if="warning" class="report-kpi-warn">{{ warning }}</span>
      </p>
    </template>
    <template v-else>
      <div class="report-kpi-label">
        <span>{{ label }}</span>
        <a-tag v-if="currentPoint">当前时点</a-tag>
      </div>
      <div class="report-kpi-value" :class="tone ? `report-kpi-value--${tone}` : ''">{{ value }}</div>
      <div v-if="sub" class="report-kpi-sub">{{ sub }}</div>
      <div v-if="warning" class="report-kpi-warn">{{ warning }}</div>
    </template>
  </a-card>
</template>

<script setup lang="ts">
/** 指标卡只显示调用方格式化好的值，不重新处理数字；当前时点指标要标时间。 */
withDefaults(
  defineProps<{
    label: string;
    value: string;
    /** 第二行说明文本，如「截至 2026-09-23 18:20:31」。 */
    sub?: string;
    /** 数据不完整的提示，如「已跳过 3 行无成本流水」。 */
    warning?: string;
    /** 时点值标记。 */
    currentPoint?: boolean;
    /** 数值颜色：danger 用于逾期合计这类要第一眼看到的指标。 */
    tone?: 'danger';
    /** strip 为整行横条，用于只有一个时点指标的场景；默认是并列的卡片。 */
    layout?: 'tile' | 'strip';
  }>(),
  {layout: 'tile'}
);
</script>

<style scoped>
.report-kpi {
  height: 100%;
  border-color: var(--scm-line);
  border-radius: 10px;
  box-shadow: none;
}

.report-kpi :deep(.ant-card-body) {
  padding: 12px 16px;
}

.report-kpi-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--scm-ink-soft);
  font-size: 12px;
  line-height: 18px;
  letter-spacing: 0.02em;
}

.report-kpi-value {
  color: var(--scm-ink);
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 30px;
  margin-top: 4px;
}

.report-kpi-value--danger {
  /* 用 SCM 主题变量；项目没有定义 antd 的 --ant-color-* */
  color: var(--scm-error, #cf1322);
}

.report-kpi-sub {
  color: var(--scm-ink-soft);
  font-size: 12px;
  line-height: 18px;
  margin-top: 4px;
}

.report-kpi-warn {
  color: var(--scm-warning);
  font-size: 12px;
  line-height: 18px;
  margin-top: 4px;
}

/* 横条：标签与数值同一基线，说明文字独占一行，整条铺满可用宽度 */
.report-kpi--strip {
  border-color: var(--scm-accent-line);
  background: var(--scm-accent-soft);
}

.report-kpi--strip :deep(.ant-card-body) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'label value'
    'meta meta';
  align-items: baseline;
  column-gap: 24px;
  row-gap: 2px;
  padding: 14px 18px;
}

.report-kpi--strip .report-kpi-label {
  grid-area: label;
}

.report-kpi--strip .report-kpi-value {
  grid-area: value;
  margin-top: 0;
}

.report-kpi-strip__meta {
  display: flex;
  flex-wrap: wrap;
  grid-area: meta;
  gap: 4px 16px;
  margin: 0;
  color: var(--scm-ink-soft);
  font-size: 12px;
  line-height: 18px;
}

.report-kpi-strip__meta .report-kpi-warn {
  margin-top: 0;
}
</style>
