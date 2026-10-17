/** 后台表格使用简化的列类型，避免复杂的 Ant Design 泛型导致 TypeScript 推断过深。 */
export interface ScmListColumn {
  title?: string;
  dataIndex?: string;
  key?: string;
  align?: 'left' | 'center' | 'right';
  fixed?: 'left' | 'right';
  width?: number;
  ellipsis?: boolean;
  /** `TableOperator` 读取的默认显隐标记；`false` 表示该列默认不展示。 */
  showFlag?: boolean;
}

export const SCM_COLUMN_WIDTH = {
  selection: 44,
  expand: 36,
  image: 64,
  unit: 68,
  count: 76,
  status: 88,
  quantity: 108,
  money: 132,
  moneyRange: 156,
  date: 116,
  dateTime: 176,
  phone: 140,
  name: 190,
  actionThree: 136,
} as const;

export {tableColumnsWidth as scmColumnsWidth} from '/@/components/support/table-operator/table-width';

function toPixelWidth(width?: number | string): number {
  return typeof width === 'number' ? width : Number.parseFloat(String(width ?? '')) || 0;
}

/**
 * 把容器剩余宽度分给各列，保证表格永远铺满内容区。
 *
 * 分配顺序：
 * 1. 优先补给「仍在使用页面默认宽度」的列 —— 用户没动过的列吸收剩余，动过的列保持原值；
 * 2. 若可伸缩列都被用户改过，则按各列宽度等比放大 —— 与 antd 默认行为一致，宁可像素值等比变化，
 *    也不留出右侧空白（用户明确要求「怎么调都要铺满」）。
 *
 * @param columns       当前列（已合并用户保存的宽度）
 * @param extra         容器宽度减去各列宽度之和后的剩余像素，≤0 时不处理
 * @param defaultWidths 可伸缩列的页面默认宽度，键为 dataIndex
 */
export function fillDefaultColumnWidths<T extends { dataIndex?: unknown; width?: number | string }>(
    columns: readonly T[], extra: number, defaultWidths: Record<string, number>): T[] {
  if (extra <= 0 || columns.length === 0) {
    return [...columns];
  }
  const flexible = Object.keys(defaultWidths).filter((key) => {
    const column = columns.find((item) => String(item.dataIndex ?? '') === key);
    return column != null && toPixelWidth(column.width) === defaultWidths[key];
  });
  if (flexible.length > 0) {
    const share = Math.floor(extra / flexible.length);
    return columns.map((column) => {
      const index = flexible.indexOf(String(column.dataIndex ?? ''));
      if (index < 0) {
        return column;
      }
      // 除不尽的零头给第一列，避免每列都出现小数
      return {...column, width: toPixelWidth(column.width) + share + (index === 0 ? extra % flexible.length : 0)};
    });
  }

  const total = columns.reduce((sum, column) => sum + toPixelWidth(column.width), 0);
  if (total <= 0) {
    return [...columns];
  }
  // 固定列（操作列）不参与等比放大，剩余宽度全部由可滚动列吸收
  const scalable = columns.filter((column) => !(column as {fixed?: string}).fixed);
  const scalableTotal = scalable.reduce((sum, column) => sum + toPixelWidth(column.width), 0);
  const target = scalableTotal + extra;
  const factor = scalableTotal > 0 ? target / scalableTotal : 1;
  let used = 0;
  const scaled = columns.map((column) => {
    if ((column as {fixed?: string}).fixed) {
      return column;
    }
    const width = Math.max(1, Math.round(toPixelWidth(column.width) * factor));
    used += width;
    return {...column, width};
  });
  // 四舍五入会让总和差几个像素，补到最后一个可伸缩列，避免 antd 再按比例微调
  const lastScalable = [...scaled].reverse().find((column) => !(column as {fixed?: string}).fixed);
  if (lastScalable && used !== target) {
    lastScalable.width = Math.max(1, toPixelWidth(lastScalable.width) + (target - used));
  }
  return scaled;
}
