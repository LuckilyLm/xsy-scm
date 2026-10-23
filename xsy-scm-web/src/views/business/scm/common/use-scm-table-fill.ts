import {computed, onBeforeUnmount, onMounted, ref} from 'vue';
import type {Ref} from 'vue';
import {fillDefaultColumnWidths, scmColumnsWidth} from './scm-column';

/**
 * 列表表格「按列宽渲染 + 铺满内容区」。
 *
 * 背景：antd 给带 scroll.x 的表格加了内联 `min-width: 100%`，配合 `table-layout: fixed`
 * 会把所有列按比例放大，用户在「设置列」里填的宽度渲染不出来。主题里已放开最小宽度，
 * 于是表格宽度等于各列宽度之和；本方法把容器剩余宽度补给「仍在使用页面默认宽度」的文本列，
 * 让短表也能铺满，同时不篡改用户手动调过的列。
 *
 * 用法：
 *   const columns = ref([...页面默认列]);
 *   const {tableRef, displayColumns, scrollX} = useScmTableFill(columns);
 * 模板：`<a-table ref="tableRef" :columns="displayColumns" :scroll="{ x: scrollX }">`
 */
export function useScmTableFill(columns: Ref<readonly any[]>, auxiliaryWidth: () => number = () => 0) {
  // 页面默认宽度在 setup 阶段抓取：此后 columns 会被「设置列」合并成用户值。
  // 优先拉伸可截断的文本列（ellipsis）；没有标记时退化为「非固定、非居中/右对齐」的列。
  const candidates = columns.value.filter((column) => typeof column?.width === 'number' && !column?.fixed);
  const ellipsisColumns = candidates.filter((column) => column?.ellipsis);
  const fillableColumns = ellipsisColumns.length
    ? ellipsisColumns
    : candidates.filter((column) => column?.align !== 'center' && column?.align !== 'right');
  const defaultWidths: Record<string, number> = {};
  for (const column of fillableColumns) {
    if (typeof column?.dataIndex === 'string') {
      defaultWidths[column.dataIndex] = column.width;
    }
  }

  /** 像素取整容差：小于这个差额视为「已经铺满」，不做横向滚动。 */
  const ROUNDING_TOLERANCE = 4;

  /** 允许压缩的上限：超出可用宽度 20% 就是真·宽表，交给横向滚动。 */
  const MAX_SHRINK_RATIO = 0.2;

  /** 文本列压缩下限：再窄就没法读了。 */
  const MIN_TEXT_WIDTH = 80;

  /**
   * 按比例压缩「仍在默认宽度」的文本列，把差额吃掉。
   *
   * 用户手动调过的列不参与（与放大时同一口径），压缩后低于 `MIN_TEXT_WIDTH` 的列
   * 按下限收住，剩余的差额摊给还有余量的列；实在吃不掉就原样返回，保留横向滚动。
   */
  function shrinkToFit<T extends {dataIndex?: unknown; width?: number | string}>(columns: readonly T[], deficit: number): T[] {
    const flexible = columns
      .map((column, index) => ({column, index}))
      .filter(
        ({column}) =>
          typeof column.dataIndex === 'string' &&
          defaultWidths[column.dataIndex] != null &&
          toPixel(column.width) === defaultWidths[column.dataIndex] &&
          toPixel(column.width) > MIN_TEXT_WIDTH
      );
    const capacity = flexible.reduce((sum, {column}) => sum + toPixel(column.width) - MIN_TEXT_WIDTH, 0);
    if (flexible.length === 0 || capacity < deficit) {
      return [...columns];
    }
    const totalWidth = flexible.reduce((sum, {column}) => sum + toPixel(column.width), 0);
    let remaining = deficit;
    const result = [...columns];
    flexible.forEach(({column, index}, position) => {
      const width = toPixel(column.width);
      const room = width - MIN_TEXT_WIDTH;
      const isLast = position === flexible.length - 1;
      const take = isLast ? remaining : Math.min(room, Math.round((deficit * width) / totalWidth));
      const applied = Math.min(room, take, remaining);
      remaining -= applied;
      result[index] = {...column, width: width - applied};
    });
    return result;
  }

  /**
   * 把取整多出来的几像素从可伸缩文本列里扣掉。
   *
   * 只扣第一列且要求扣完仍不窄于 60px：几像素的差额集中在一列上，肉眼看不出来；
   * 摊到所有列会让每列都变成小数宽度。
   */
  function absorbRounding<T extends {dataIndex?: unknown; width?: number | string}>(columns: readonly T[], deficit: number): T[] {
    const index = columns.findIndex(
      (column) => typeof column.dataIndex === 'string' && defaultWidths[column.dataIndex] != null && toPixel(column.width) - deficit >= 60
    );
    if (index < 0) {
      return [...columns];
    }
    return columns.map((column, position) => (position === index ? {...column, width: toPixel(column.width) - deficit} : column));
  }

  function toPixel(width?: number | string): number {
    const value = typeof width === 'number' ? width : Number.parseFloat(String(width ?? ''));
    return Number.isFinite(value) ? Math.round(value) : 0;
  }

  const tableRef = ref();
  const hostWidth = ref(0);
  let observer: ResizeObserver | undefined;

  /**
   * 表格滚动容器的可用宽度。
   *
   * 外层 wrapper 与内层 `.ant-table-content` 之间差 1px（antd 的表格容器自带内缩），
   * 用 wrapper 宽度算出来的 scroll.x 会比真实可用宽度大 1px —— 于是「明明铺满了」
   * 却仍然出现一条几乎满宽的横向滚动条。这里量内层容器，量不到（还没渲染）再退回外层。
   */
  function availableWidth(): number {
    const host = tableRef.value?.$el as HTMLElement | undefined;
    if (!host) {
      return 0;
    }
    const inner = host.querySelector<HTMLElement>('.ant-table-content, .ant-table-body');
    return Math.floor((inner ?? host).clientWidth);
  }

  const displayColumns = computed(() => {
    const extra = hostWidth.value - auxiliaryWidth() - scmColumnsWidth(columns.value as any);
    if (extra > 0) {
      return fillDefaultColumnWidths(columns.value as any, extra, defaultWidths);
    }
    // 差 1~3px 属像素取整，不是「列比屏宽」：从可伸缩文本列里扣掉，避免假滚动条。
    if (extra > -ROUNDING_TOLERANCE) {
      return absorbRounding(columns.value as any, -extra);
    }
    // 差得不多（不到可用宽度的 20%）时按比例压一压仍在默认宽度的文本列，
    // 让「差几十像素就铺满」的表不再拖一条横向滚动条；压不动（列太多 / 用户调过宽度）
    // 就保留滚动 —— 真·宽表不该被压成每条都是省略号。
    if (-extra <= hostWidth.value * MAX_SHRINK_RATIO) {
      return shrinkToFit(columns.value as any, -extra);
    }
    return [...(columns.value as any[])];
  });
  const scrollX = computed(() => scmColumnsWidth(displayColumns.value as any) + auxiliaryWidth());

  onMounted(() => {
    const host = tableRef.value?.$el as HTMLElement | undefined;
    if (!host) {
      return;
    }
    hostWidth.value = availableWidth();
    observer = new ResizeObserver(() => {
      hostWidth.value = availableWidth();
    });
    observer.observe(host);
  });
  onBeforeUnmount(() => observer?.disconnect());

  return {tableRef, displayColumns, scrollX};
}
