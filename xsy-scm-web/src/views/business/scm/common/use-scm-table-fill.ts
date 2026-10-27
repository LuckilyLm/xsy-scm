import {computed, onBeforeUnmount, onMounted, ref} from 'vue';
import type {Ref} from 'vue';
import {fillDefaultColumnWidths, scmColumnsWidth} from './scm-column';

/** 列宽计算只依赖这几个字段，兼容 Ant Design 的列类型。 */
export interface ScmTableColumn {
  dataIndex?: unknown;
  width?: number | string;
  fixed?: unknown;
  ellipsis?: unknown;
  align?: 'left' | 'center' | 'right';
  showFlag?: boolean;
}

/** 表格列宽铺满内容区；用户手动调过的列保持原值。 */
export function useScmTableFill<T extends ScmTableColumn>(columns: Ref<readonly T[]>, auxiliaryWidth: () => number = () => 0) {
  // 初始化时记录默认宽度；优先拉伸可省略的文本列，其次是左对齐列
  const candidates = columns.value.filter((column) => typeof column?.width === 'number' && !column?.fixed);
  const ellipsisColumns = candidates.filter((column) => column?.ellipsis);
  const fillableColumns = ellipsisColumns.length
    ? ellipsisColumns
    : candidates.filter((column) => column?.align !== 'center' && column?.align !== 'right');
  const defaultWidths: Record<string, number> = {};
  for (const column of fillableColumns) {
    if (typeof column?.dataIndex === 'string' && typeof column.width === 'number') {
      defaultWidths[column.dataIndex] = column.width;
    }
  }

  /** 像素取整容差：小于这个差额视为「已经铺满」，不做横向滚动。 */
  const ROUNDING_TOLERANCE = 4;

  /** 超出可用宽度 20% 就是宽表，交给横向滚动。 */
  const MAX_SHRINK_RATIO = 0.2;

  /** 文本列压缩下限：再窄就没法读了。 */
  const MIN_TEXT_WIDTH = 80;

  /**
   * 按比例压缩文本列，最低 80px；压不下就保留横向滚动。
   *
   * <p>可压缩的是所有左对齐文本列，而不是只有声明了 ellipsis 的列：一张表往往只有名称列写了
   * ellipsis，光靠它能腾出的宽度不够时，整张表会被判成「宽表」直接横向滚动，右侧列被固定
   * 操作列压住。压缩时自动补上 ellipsis，避免文字换行把行高撑开。
   */
  function shrinkToFit<
    T extends {dataIndex?: unknown; width?: number | string; ellipsis?: unknown; fixed?: unknown; align?: 'left' | 'center' | 'right'},
  >(columns: readonly T[], deficit: number): T[] {
    const flexible = columns
      .map((column, index) => ({column, index}))
      .filter(({column}) => {
        if (typeof column.dataIndex !== 'string' || column.fixed) {
          return false;
        }
        if (column.align === 'center' || column.align === 'right') {
          return false;
        }
        if (toPixel(column.width) <= MIN_TEXT_WIDTH) {
          return false;
        }
        // 用户手动调过宽度的列不参与自动压缩
        const defaultWidth = defaultWidths[column.dataIndex];
        return defaultWidth == null || toPixel(column.width) === defaultWidth;
      });
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
      result[index] = {...column, width: width - applied, ...(column.ellipsis ? {} : {ellipsis: true})};
    });
    return result;
  }

  /** 把取整多出的几像素从第一列扣掉。 */
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

  const tableRef = ref<{$el?: HTMLElement}>();
  const hostWidth = ref(0);
  let observer: ResizeObserver | undefined;

  /** 取表格内容区宽度；量不到时退回外层容器。 */
  function availableWidth(): number {
    const host = tableRef.value?.$el;
    if (!host) {
      return 0;
    }
    const inner = host.querySelector<HTMLElement>('.ant-table-content, .ant-table-body');
    return Math.floor((inner ?? host).clientWidth);
  }

  const displayColumns = computed<T[]>(() => {
    const extra = hostWidth.value - auxiliaryWidth() - scmColumnsWidth(columns.value);
    if (extra > 0) {
      return fillDefaultColumnWidths(columns.value, extra, defaultWidths);
    }
    // 差 1~3px 是取整误差，从文本列扣掉，避免出现假滚动条
    if (extra > -ROUNDING_TOLERANCE) {
      return absorbRounding(columns.value, -extra);
    }
    // 差距不到可用宽度的 20% 时压缩文本列；压不动就保留横向滚动
    if (-extra <= hostWidth.value * MAX_SHRINK_RATIO) {
      return shrinkToFit(columns.value, -extra);
    }
    return [...columns.value];
  });
  const scrollX = computed(() => scmColumnsWidth(displayColumns.value) + auxiliaryWidth());

  onMounted(() => {
    const host = tableRef.value?.$el;
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
