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
/**
 * 报表指标卡（的「重要金额/数量先展示指标卡」）。
 *
 * `value` 收的是已经格式化好的文本（由 `moneyText` / `countText` 产出），
 * 不在组件里做任何数值处理：定点数的位数与形状由源头决定，
 * 组件再格式化一次就会出现「同一笔钱在卡片和表格里位数不同」。
 * `null` 由调用方传成 `—`，卡片本身不猜。
 *
 * `currentPoint` + `sub` 服务「当前库存账面金额」这类时点值：
 * 它不受查询区间影响，不标注就会被读成区间合计。
 */
defineProps<{
    label: string;
    value: string;
    /** 第二行说明文本，如「截至 2026-09-23 18:20:31」。 */
    sub?: string;
    /** 数据不完整的提示，如「已跳过 3 行无成本流水」。 */
    warning?: string;
    /** 时点值标记。 */
    currentPoint?: boolean;
    /** 数值语义色：danger 用于逾期合计这类必须第一眼看到的坏消息；颜色之外标签仍说明含义。 */
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
  /* 用 SCM 主题变量：antd 4.2.5 不开 cssVar，`--ant-color-*` 在项目里从未定义（全仓库已清零） */
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
