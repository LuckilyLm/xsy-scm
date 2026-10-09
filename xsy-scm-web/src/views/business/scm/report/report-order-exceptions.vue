<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-form-item label="发生日期" required>
      <a-range-picker v-model:value="dateRange" value-format="YYYY-MM-DD"/>
    </a-form-item>
    <a-form-item label="异常类别">
      <a-select v-model:value="filters.exceptionType" :options="typeOptions" allow-clear placeholder="全部类别" class="type-select"/>
    </a-form-item>
    <a-form-item label="仓库"><WarehouseSelect v-model:value="filters.warehouseId" width="180px"/></a-form-item>
    <a-form-item label="关键字">
      <a-input v-model:value="filters.keyword" :maxlength="120" placeholder="来源单号、订单、客户或商品" allow-clear @pressEnter="search"/>
    </a-form-item>
    <a-form-item><a-space>
      <a-button type="primary" :loading="loading" v-privilege="QUERY_PERMISSION" @click="search">查询</a-button>
      <a-button @click="reset">重置</a-button>
    </a-space></a-form-item>
  </a-form>
  <a-alert v-if="error" class="report-note" type="error" :message="error" show-icon>
    <template #action><a-button @click="search">重试查询</a-button></template>
  </a-alert>
  <a-card size="small" :bordered="false">
    <div class="report-toolbar">
      <a-typography-text v-if="applied">{{ applied.startDate }} 至 {{ applied.endDate }} · 仅显示有源单据查看权限的数据</a-typography-text>
      <a-typography-text v-else type="secondary">请选择日期后查询</a-typography-text>
      <a-button v-privilege="'scm:report:export'" :disabled="!applied || loading || !!error" :loading="exporting" @click="exportRows">导出当前结果</a-button>
    </div>
    <a-table size="small" :data-source="summary" :columns="summaryColumns" row-key="exceptionType" :pagination="false"
             :loading="loading" :scroll="{x: 450}" class="report-summary">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex === 'exceptionType'">{{ labels[record.exceptionType as OrderExceptionType] }}</template>
        <template v-else>{{ text }}</template>
      </template>
    </a-table>
    <a-table size="small" :data-source="rows" :columns="columns" :row-key="rowKey" :pagination="false" bordered
             :loading="loading" :scroll="{x: 2050}" :locale="{emptyText: '当前筛选与授权范围内没有异常事实'}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex === 'exceptionType'">{{ labels[record.exceptionType as OrderExceptionType] }}</template>
        <template v-else-if="column.dataIndex === 'sourceStatus'">{{ statusLabel(record) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" v-privilege="sources[record.exceptionType as OrderExceptionType].permission" @click="openSource(record)">来源详情</a-button>
        </template>
        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total" show-size-changer show-quick-jumper
                    :show-total="(n: number) => `共 ${n} 条异常`" @change="changePage"/>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import dayjs from 'dayjs';
import type {TableColumnsType} from 'ant-design-vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {orderExceptionApi, type OrderExceptionQuery, type OrderExceptionRow, type OrderExceptionSummary,
  type OrderExceptionType} from '/@/api/business/scm/order-exception-api';
import {reportError} from './report-errors';
import {SCM_REPORT_PERMISSION} from '/@/constants/business/scm/report-const';
import {SCM_SORTING_RESULT_ENUM} from '/@/constants/business/scm/sorting-const';
import {SCM_ORDER_RETURN_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import {fulfillmentStatuses, type FulfillmentStatus} from '../delivery/delivery-types';

const QUERY_PERMISSION = SCM_REPORT_PERMISSION.ORDER_EXCEPTION_QUERY;
const labels: Record<OrderExceptionType, string> = {
  SORTING_DIFFERENCE: '分拣数量差异', DELIVERY_EXCEPTION: '配送异常签收', RETURN_REJECTED: '售后拒绝',
};
const typeOptions = Object.entries(labels).map(([value, label]) => ({value, label}));
const sources: Record<OrderExceptionType, {path: string; key: string; permission: string}> = {
  SORTING_DIFFERENCE: {path: '/sorting/tasks', key: 'taskId', permission: 'scm:sorting:task:query'},
  DELIVERY_EXCEPTION: {path: '/delivery/routes', key: 'routeId', permission: 'scm:delivery:route:query'},
  RETURN_REJECTED: {path: '/order/order-return-list', key: 'returnId', permission: 'scm:order:return:query'},
};
const defaultRange = (): [string, string] => [dayjs().subtract(6, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')];
const dateRange = ref<[string, string]>(defaultRange());

const filters = reactive<Pick<OrderExceptionQuery, 'exceptionType' | 'warehouseId' | 'keyword'>>({});
const rows = ref<OrderExceptionRow[]>([]), summary = ref<OrderExceptionSummary[]>([]);
const pageNum = ref(1), pageSize = ref(20), total = ref(0);
const loading = ref(false), exporting = ref(false), error = ref(''), applied = ref<OrderExceptionQuery>();
const router = useRouter();
let requestId = 0;
const rowKey = (row: OrderExceptionRow) => `${row.exceptionType}:${row.sourceRowId}`;
const summaryColumns = [
  {title: '异常类别', dataIndex: 'exceptionType'}, {title: '异常条数', dataIndex: 'exceptionCount', align: 'right'},
  {title: '该类别关联订单数', dataIndex: 'orderCount', align: 'right'},
];
const columns: TableColumnsType<OrderExceptionRow> = [
  {title: '异常类别', dataIndex: 'exceptionType', width: 145}, {title: '发生时间', dataIndex: 'occurredAt', width: 180},
  {title: '来源单号', dataIndex: 'sourceNo', width: 180}, {title: '订单号', dataIndex: 'orderNo', width: 180},
  {title: '客户', dataIndex: 'customerName', width: 150}, {title: '仓库', dataIndex: 'warehouseName', width: 140},
  {title: '商品', dataIndex: 'productName', width: 140}, {title: '单位', dataIndex: 'unit', width: 70, align: 'center'},
  {title: '计划量', dataIndex: 'plannedQuantity', width: 110, align: 'right'},
  {title: '实际量', dataIndex: 'actualQuantity', width: 110, align: 'right'},
  {title: '差异（实际−计划）', dataIndex: 'differenceQuantity', width: 150, align: 'right'},
  {title: '源状态', dataIndex: 'sourceStatus', width: 100, align: 'center'}, {title: '原因', dataIndex: 'reason', width: 260},
  {title: '操作', dataIndex: 'action', fixed: 'right', width: 110, align: 'center'},
];
function statusLabel(row: OrderExceptionRow) {
  if (row.exceptionType === 'SORTING_DIFFERENCE') return SCM_SORTING_RESULT_ENUM[row.sourceStatus]?.desc ?? row.sourceStatus;
  if (row.exceptionType === 'RETURN_REJECTED') return SCM_ORDER_RETURN_STATUS_ENUM[row.sourceStatus]?.desc ?? row.sourceStatus;
  return fulfillmentStatuses[row.sourceStatus as FulfillmentStatus]?.label ?? row.sourceStatus;
}
async function load(payload: OrderExceptionQuery) {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  rows.value = [];
  summary.value = [];
  total.value = 0;
  try {
    const [page, counts] = await Promise.all([orderExceptionApi.query(payload), orderExceptionApi.summary(payload)]);
    if (id !== requestId) return;
    rows.value = page.data.list;
    total.value = page.data.total;
    summary.value = counts.data;
    applied.value = {...payload};
  } catch (e) { if (id === requestId) error.value = reportError(e); }
  finally { if (id === requestId) loading.value = false; }
}
function search() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    ++requestId;
    loading.value = false;
    error.value = '请选择完整的发生日期范围';
    return;
  }
  pageNum.value = 1;
  void load({...filters, startDate: dateRange.value[0], endDate: dateRange.value[1], pageNum: 1, pageSize: pageSize.value});
}
function changePage() {
  if (applied.value) void load({...applied.value, pageNum: pageNum.value, pageSize: pageSize.value});
}
function reset() {
  Object.assign(filters, {exceptionType: undefined, warehouseId: undefined, keyword: undefined});
  dateRange.value = defaultRange();
  pageSize.value = 20;
  search();
}
async function exportRows() {
  if (!applied.value || exporting.value) return;
  exporting.value = true;
  try { await orderExceptionApi.export({...applied.value}); }
  catch (e) { error.value = reportError(e); }
  finally { exporting.value = false; }
}
function openSource(row: OrderExceptionRow) {
  const target = sources[row.exceptionType];
  void router.push({path: target.path, query: {[target.key]: String(row.sourceId)}});
}
onMounted(search);
</script>

<style scoped>
.report-note, .report-summary { margin-bottom: 14px; }
.report-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 14px; }
.type-select { min-width: 180px; }
</style>
