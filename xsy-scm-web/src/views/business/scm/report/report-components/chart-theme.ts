/** 管理后台报表沿用统一浅色主题，不与深色大屏混用；同一指标在不同图表保持相同颜色。 */
export const REPORT_CHART_COLORS = {
  /** 主系列：金额类第一顺位。 */
  primary: '#00B96B',
  /** 次系列：蓝。 */
  secondary: '#1677FF',
  /** 第三顺位：橙，用于退款 / 损耗这类「不是主口径」的量。 */
  warning: '#FA8C16',
  /** 第四顺位：青。 */
  cyan: '#13C2C2',
  /** 第五顺位：紫，只用于损耗类型占比这类少量分类。 */
  purple: '#722ED1',
  axisText: '#4E5969',
  splitLine: '#E5E6EB',
};

/** 饼图 / 多系列的顺位色板。 */
export const REPORT_CHART_SERIES: readonly string[] = [
  REPORT_CHART_COLORS.primary,
  REPORT_CHART_COLORS.secondary,
  REPORT_CHART_COLORS.warning,
  REPORT_CHART_COLORS.cyan,
  REPORT_CHART_COLORS.purple,
];

/** 图表容器高度：报表页统一，避免同一页面卡片高矮不齐。 */
export const REPORT_CHART_HEIGHT = '280px';
