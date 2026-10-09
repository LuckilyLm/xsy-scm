/**
 * SCM 列表列定义的共享类型（跨模块共享）。
 *
 * 为什么不用 `TableColumnsType<T>[number] & {showFlag?: boolean}`：
 * 对 Ant Design 的列泛型取 `[number]` 再交叉 `showFlag`，会得到一个非常"贵"的
 * 联合类型；一旦在 `columns.value.reduce(...)` 里遍历它，TypeScript 就会报
 * `TS2589: Type instantiation is excessively deep and possibly infinite`。
 * 交叉类型里的 `showFlag` 又是 `TableOperator` 依赖的显隐标记，不能直接删掉。
 *
 * 因此这里用最小的本地结构描述 SCM 列表实际用到的列属性。`a-table` 与
 * `TableOperator` 都按结构化类型消费列，本类型可安全传入；同时避免了实例化
 * 整套 Ant Design 列泛型。
 *
 * 需要 `customRender` / `sorter` / 树形列等 Ant Design 高级能力时，不要往这里
 * 堆属性，而应改用 `TableColumnsType<T>` 并在该处避免对其 `reduce` 求和。
 */
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
