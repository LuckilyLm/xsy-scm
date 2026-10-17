/** 按当前显示列合计水平滚动宽度。选择列和展开列由调用方传入实际预留宽度。 */
export function tableColumnsWidth<T extends {width?: number | string; showFlag?: boolean}>(
    columns: readonly T[], extraWidth = 0): number {
  return columns.reduce((width, column) => {
    if (column.showFlag === false) return width;
    const columnWidth = typeof column.width === 'number' ? column.width
        : /^\d+(?:\.\d+)?(?:px)?$/.test(column.width ?? '') ? Number.parseFloat(column.width!) : 0;
    return width + (Number.isFinite(columnWidth) && columnWidth > 0 ? columnWidth : 0);
  }, extraWidth);
}
