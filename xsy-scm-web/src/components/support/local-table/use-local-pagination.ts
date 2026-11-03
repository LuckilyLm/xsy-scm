import {computed, ref, watch, type Ref} from 'vue';

/**
 * 完整数组的展示分页，不改变调用方的数据、图表或导出范围。
 * @param rows 完整结果数组的只读引用；空数组显示零条，不能传入服务端的一页数据。
 * @param initialPageSize 首次显示条数，必须为正整数。
 * @returns 受控页码、每页条数及切页方法；结果替换时回首页，原数组缩减时校正页码。
 */
export function useLocalPagination(rows: Readonly<Ref<readonly object[]>>, initialPageSize = 20) {
  const current = ref(1);
  const pageSize = ref(initialPageSize);
  const total = computed(() => rows.value.length);

  watch(rows, () => {
    current.value = 1;
  });
  watch([total, pageSize], () => {
    current.value = Math.min(current.value, Math.max(1, Math.ceil(total.value / pageSize.value)));
  });

  /** 切换条数时先回首页，避免把上一种条数的页码用于新的结果窗口。 */
  function change(page: number, size: number) {
    const nextSize = Math.max(1, Math.floor(size));
    const nextPage = nextSize === pageSize.value ? page : 1;
    pageSize.value = nextSize;
    current.value = Math.max(1, Math.min(Math.floor(nextPage), Math.max(1, Math.ceil(total.value / nextSize))));
  }

  return {current, pageSize, total, change};
}
