<template>
  <a-card size="small" class="report-kpi">
    <div class="report-kpi-label">
      <span>{{ label }}</span>
      <a-tag v-if="currentPoint">当前时点</a-tag>
    </div>
    <div class="report-kpi-value" :class="tone ? `report-kpi-value--${tone}` : ''">{{ value }}</div>
    <div v-if="sub" class="report-kpi-sub">{{ sub }}</div>
    <div v-if="warning" class="report-kpi-warn">{{ warning }}</div>
  </a-card>
</template>

<script setup lang="ts">
/** 指标卡只显示调用方格式化好的值，不重新处理数字；当前时点指标要标时间。 */
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
}>();
</script>

<style scoped>
.report-kpi {
  height: 100%;
}

.report-kpi-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--scm-text-secondary);
  font-size: 13px;
}

.report-kpi-value {
  color: var(--scm-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
  margin-top: 4px;
}

.report-kpi-value--danger {
  /* 用 SCM 主题变量；项目没有定义 antd 的 --ant-color-* */
  color: var(--scm-error, #cf1322);
}

.report-kpi-sub {
  color: var(--scm-text-secondary);
  font-size: 12px;
  margin-top: 2px;
}

.report-kpi-warn {
  color: var(--scm-warning);
  font-size: 12px;
  margin-top: 2px;
}
</style>
