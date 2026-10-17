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
