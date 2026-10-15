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

/** 按列宽合计表格横向滚动宽度，避免每页各自写一遍 `reduce`。 */
export function scmColumnsWidth(columns: readonly ScmListColumn[]): number {
  return columns.reduce((width, column) => width + Number(column.width ?? 0), 0);
}
