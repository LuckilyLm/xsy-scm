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
  image: 68,
  unit: 72,
  count: 80,
  status: 96,
  quantity: 112,
  money: 136,
  moneyRange: 184,
  date: 120,
  dateTime: 180,
  phone: 140,
  name: 200,
  actionThree: 144,
} as const;

export {tableColumnsWidth as scmColumnsWidth} from '/@/components/support/table-operator/table-width';
