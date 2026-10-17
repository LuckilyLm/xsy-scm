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
  const candidates = columns.value.filter(
      column => typeof column?.width === 'number' && !column?.fixed);
  const ellipsisColumns = candidates.filter(column => column?.ellipsis);
  const fillableColumns = ellipsisColumns.length
      ? ellipsisColumns
      : candidates.filter(column => column?.align !== 'center' && column?.align !== 'right');
  const defaultWidths: Record<string, number> = {};
  for (const column of fillableColumns) {
    if (typeof column?.dataIndex === 'string') {
      defaultWidths[column.dataIndex] = column.width;
    }
  }

  const tableRef = ref();
  const hostWidth = ref(0);
  let observer: ResizeObserver | undefined;

  const displayColumns = computed(() => {
    const extra = Math.max(0, hostWidth.value - auxiliaryWidth() - scmColumnsWidth(columns.value as any));
    return fillDefaultColumnWidths(columns.value as any, extra, defaultWidths);
  });
  const scrollX = computed(() => scmColumnsWidth(displayColumns.value as any) + auxiliaryWidth());

  onMounted(() => {
    const host = tableRef.value?.$el as HTMLElement | undefined;
    if (!host) {
      return;
    }
    hostWidth.value = Math.floor(host.clientWidth);
    observer = new ResizeObserver(entries => {
      hostWidth.value = Math.floor(entries[0]?.contentRect.width ?? 0);
    });
    observer.observe(host);
  });
  onBeforeUnmount(() => observer?.disconnect());

  return {tableRef, displayColumns, scrollX};
}
