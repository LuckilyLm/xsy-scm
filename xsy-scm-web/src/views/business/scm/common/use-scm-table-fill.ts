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
  /** Ant Design 的表头单元格属性钩子；拖拽列宽靠它给 th 打标记。 */
  customHeaderCell?: unknown;
}

/** 鼠标拖拽调列宽：右边缘 8px 内按下才开始，避免和表头点击、排序冲突。 */
const RESIZE_HIT_ZONE = 8;

/** 拖拽时的最小宽度：再窄就看不清列名，也点不到操作按钮。 */
const RESIZE_MIN_WIDTH = 60;

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

  // 拖拽中的列：记下按下时的渲染宽度，移动时按像素差写回 columns
  const resizing = ref<{dataIndex: string; startX: number; startWidth: number} | null>(null);

  // 拖完列宽会跟一次 click，若表头带排序会把拖拽当成点排序，这里吞掉那一次
  let swallowNextClick = false;

  /**
   * 给可调整的列打上表头标记。固定列同样可调：操作列太窄时按钮会被挤成两行。
   * 没有 dataIndex 的列（勾选/展开）和显式声明 `resizable: false` 的列不参与。
   */
  function withResizeHandle(column: T): T {
    if (
      typeof column.dataIndex !== 'string' ||
      typeof column.width !== 'number' ||
      column.showFlag === false ||
      (column as {resizable?: boolean}).resizable === false ||
      column.customHeaderCell
    ) {
      return column;
    }
    return {
      ...column,
      customHeaderCell: () => ({
        class: 'scm-resize-header',
        'data-column-key': column.dataIndex,
      }),
    };
  }

  const displayColumns = computed<T[]>(() => {
    // 拖拽期间不做自动铺满/压缩：否则每移动 1px 都被算一次剩余宽度，列会自己跳
    if (resizing.value) {
      return columns.value.map(withResizeHandle);
    }
    const extra = hostWidth.value - auxiliaryWidth() - scmColumnsWidth(columns.value);
    if (extra > 0) {
      return fillDefaultColumnWidths(columns.value, extra, defaultWidths).map(withResizeHandle);
    }
    // 差 1~3px 是取整误差，从文本列扣掉，避免出现假滚动条
    if (extra > -ROUNDING_TOLERANCE) {
      return absorbRounding(columns.value, -extra).map(withResizeHandle);
    }
    // 差距不到可用宽度的 20% 时压缩文本列；压不动就保留横向滚动
    if (-extra <= hostWidth.value * MAX_SHRINK_RATIO) {
      return shrinkToFit(columns.value, -extra).map(withResizeHandle);
    }
    return columns.value.map(withResizeHandle);
  });
  const scrollX = computed(() => scmColumnsWidth(displayColumns.value) + auxiliaryWidth());

  /**
   * 鼠标拖拽列宽（Excel 手感）：按住表头右边缘左右拖，松手定格。
   *
   * <p>写回页面 columns 的 width 后，这一列就不再等于默认宽度，自动铺满不会再动它；
   * 想恢复默认宽度，清空「设置列」里的宽度即可。
   */
  function onResizeStart(event: MouseEvent) {
    if (event.button !== 0) {
      return;
    }
    const header = (event.target as HTMLElement | null)?.closest?.('th.scm-resize-header') as HTMLElement | null;
    if (!header) {
      return;
    }
    const rect = header.getBoundingClientRect();
    if (event.clientX < rect.right - RESIZE_HIT_ZONE) {
      return;
    }
    const dataIndex = header.dataset.columnKey;
    if (!dataIndex) {
      return;
    }
    // 阻止拖动时选中文字、触发排序；也不让表头点击把这一列当成排序
    event.preventDefault();
    event.stopPropagation();
    swallowNextClick = true;
    // 屏幕上显示的是自动铺满/压缩后的宽度；先把这份显示宽度冻结成列宽，
    // 否则一按下就退回页面原始列宽，其它列会先跳一次，拖拽边界也跟不上鼠标
    const frozen = displayColumns.value.map((column) => {
      const copy = {...column};
      delete copy.customHeaderCell;
      return copy;
    });
    resizing.value = {dataIndex, startX: event.clientX, startWidth: Math.round(rect.width)};
    columns.value = frozen;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    window.addEventListener('mousemove', onResizeMove);
    window.addEventListener('mouseup', onResizeEnd, {once: true});
  }

  function onResizeMove(event: MouseEvent) {
    const drag = resizing.value;
    if (!drag) {
      return;
    }
    const width = Math.max(RESIZE_MIN_WIDTH, Math.round(drag.startWidth + (event.clientX - drag.startX)));
    const index = columns.value.findIndex((column) => column.dataIndex === drag.dataIndex);
    if (index < 0) {
      return;
    }
    (columns.value as T[])[index] = {...columns.value[index], width};
  }

  function onResizeEnd() {
    const drag = resizing.value;
    if (!drag) {
      return;
    }
    // 冻结后的宽度成为新的铺满基线：窗口再变化时这些列仍参与自动铺满/压缩，
    // 不会因为不等于页面初始宽度而被当成用户手改列宽永久锁死。
    // 固定列和居中的状态/操作列不参与拉伸，保持原来的分配口径。
    // 表格本来就放不下（仍在横向滚动）时不能登记：否则同一份缺口会被再压一次。
    if (scmColumnsWidth(columns.value) <= hostWidth.value) {
      for (const column of columns.value) {
        if (typeof column.dataIndex !== 'string' || typeof column.width !== 'number' || column.fixed) {
          continue;
        }
        if (column.align === 'center' || column.align === 'right') {
          continue;
        }
        defaultWidths[column.dataIndex] = toPixel(column.width);
      }
    }
    // 打标告知「表格列设置」这是拖拽改宽：它看到标记后立刻落库，
    // 刷新页面和再次打开列设置读到的都是当前宽度，不会被旧配置盖回去
    const index = columns.value.findIndex((column) => column.dataIndex === drag.dataIndex);
    if (index >= 0) {
      (columns.value as (T & {dragAndDropFlag?: boolean})[])[index] = {...columns.value[index], dragAndDropFlag: true};
    }
    resizing.value = null;
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    window.removeEventListener('mousemove', onResizeMove);
    // 拖完没有触发 click（例如拖到表体上松手）时，别把下一次正常点击也吞掉
    setTimeout(() => {
      swallowNextClick = false;
    }, 0);
  }

  /** 拖拽结束后的那次 click 不能再落到表头排序上。 */
  function onClickCapture(event: MouseEvent) {
    if (!swallowNextClick) {
      return;
    }
    swallowNextClick = false;
    event.stopPropagation();
    event.preventDefault();
  }

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
    host.addEventListener('mousedown', onResizeStart as EventListener);
    host.addEventListener('click', onClickCapture as EventListener, true);
  });
  onBeforeUnmount(() => {
    observer?.disconnect();
    tableRef.value?.$el?.removeEventListener('mousedown', onResizeStart as EventListener);
    tableRef.value?.$el?.removeEventListener('click', onClickCapture as EventListener, true);
    window.removeEventListener('mousemove', onResizeMove);
    window.removeEventListener('mouseup', onResizeEnd);
  });

  return {tableRef, displayColumns, scrollX};
}
