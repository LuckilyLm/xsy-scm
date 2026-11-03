<!-- 完整数组专用：由表格先排序、筛选再分页，源数据保持完整。服务端分页列表继续使用 a-table。 -->
<template>
  <div class="smart-local-table">
    <a-table size="small" bordered v-bind="$attrs" :data-source="dataSource" :pagination="pagination" @change="onTableChange">
      <template v-for="(_, name) in $slots" #[name]="scope">
        <slot :name="name" v-bind="scope || {}" />
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        :current="current"
        :page-size="pageSize"
        :total="filteredTotal ?? dataSource.length"
        :page-size-options="pageSizeOptions"
        show-size-changer
        show-quick-jumper
        show-less-items
        :show-total="(total: number) => `共${total}条`"
        @change="change"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, toRef, watch} from 'vue';
import type {TableProps} from 'ant-design-vue';
import {PAGE_SIZE_OPTIONS} from '/@/constants/common-const';
import {useLocalPagination} from './use-local-pagination';

defineOptions({inheritAttrs: false});
const emit = defineEmits(['change']);
const props = withDefaults(
  defineProps<{
    dataSource?: object[];
    pageSizeOptions?: string[];
    initialPageSize?: number;
  }>(),
  {dataSource: () => [], pageSizeOptions: () => PAGE_SIZE_OPTIONS, initialPageSize: 20}
);
const {current, pageSize, change} = useLocalPagination(toRef(props, 'dataSource'), props.initialPageSize);
const filteredTotal = ref<number>();
watch([() => props.dataSource, () => props.dataSource.length], () => {
  filteredTotal.value = undefined;
});
const pagination = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  // 只隐藏原生分页器的位置，保留表格内部先排序/筛选再分页的完整处理。
  position: [],
  onChange: change,
}));

/** 原生筛选后的总数来自表格完整投影，不能使用当前页行数。 */
const onTableChange: NonNullable<TableProps['onChange']> = (page, filters, sorter, extra) => {
  filteredTotal.value = extra.currentDataSource.length;
  emit('change', page, filters, sorter, extra);
};
</script>
