/**
 * 报表中心图表的**管理后台浅色主题**配色。
 *
 * 刻意与大屏（`screen/styles/variables.less` 的深蓝底 + 霓虹青）分开：
 * 两套视觉系统不混用是 AGENTS §11 的硬边界。这里的取值全部来自 Admin 主题色板
 * （primary `#00B96B` 与 `src/theme/color.ts` 里既有的辅助色），不为单个页面新调颜色。
 *
 * 顺序即语义顺序：同一指标在不同图上永远是同一个颜色，
 * 用户从概览趋势切到明细表时不需要重新对照图例。
 */
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
