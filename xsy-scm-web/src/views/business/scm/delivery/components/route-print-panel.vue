<template>
  <div class="smart-table-btn-block">
    <a-segmented
        :value="printMode"
        :options="[
          {label: '按订单', value: 'orders'},
          {label: '按客户', value: 'customers'},
        ]"
        @change="emit('update:printMode', $event as 'orders' | 'customers')"
    />
    <a-select
        v-if="printMode === 'customers'"
        :value="customerStatusFilter"
        style="width: 150px"
        :options="[
          {label: '全部客户', value: 'ALL'},
          {label: '未打印客户', value: 'UNPRINTED'},
          {label: '部分打印客户', value: 'PARTIAL'},
          {label: '已打印客户', value: 'PRINTED'},
        ]"
        @change="emit('update:customerStatusFilter', $event as CustomerStatusFilter)"
    />
    <a-select
        v-if="printMode === 'customers'"
        :value="customerFilter"
        style="width: 160px"
        :options="[
          {label: '全部订单', value: 'ALL'},
          {label: '仅未打印订单', value: 'UNPRINTED'},
          {label: '仅已打印订单', value: 'PRINTED'},
        ]"
        @change="emit('update:customerFilter', $event as CustomerOrderFilter)"
    />
    <a-button
        v-if="canPrint"
        type="primary"
        v-privilege="'scm:delivery:route:print'"
        :disabled="busy || !canRecordPrint"
        @click="emit('recordPrint')"
    >生成打印 · 登记 {{ targetCount }}</a-button>
  </div>
  <a-alert
      message="「生成打印」仅登记本次已生成打印预览并累加计次，不代表发货确认，也不扣减库存。客户与订单的打印状态在生成时由服务端按当前有效订单重新判定，列表状态仅供预览。"
      type="info"
      show-icon
  />
  <a-table
      v-if="printMode === 'orders'"
      size="small"
      :columns="orderViewColumns"
      :data-source="orders"
      row-key="orderId"
      :loading="loading"
      :pagination="false"
      :scroll="{x: 1080}"
      :row-selection="orderRowSelection"
      bordered
  >
    <template #bodyCell="{column, record}">
      <template v-if="column.dataIndex === 'orderAmount'">{{ money(record.orderAmount) }}</template>
      <template v-else-if="column.dataIndex === 'printStatus'">
        <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">
          {{ printStatuses[record.printStatus as PrintStatus].label }}
        </a-tag>
      </template>
      <template v-else-if="column.dataIndex === 'lastPrintedAt'">
        {{ record.lastPrintedAt ? datetime(record.lastPrintedAt) : '—' }}
      </template>
    </template>
  </a-table>
  <a-table
      v-else
      size="small"
      :columns="customerViewColumns"
      :data-source="customers"
      row-key="customerId"
      :loading="loading"
      :pagination="false"
      :scroll="{x: 760}"
      :row-selection="customerRowSelection"
      bordered
  >
    <template #bodyCell="{column, record}">
      <template v-if="column.dataIndex === 'totalAmount'">{{ money(record.totalAmount) }}</template>
      <template v-else-if="column.dataIndex === 'printStatus'">
        <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">
          {{ printStatuses[record.printStatus as PrintStatus].label }}
        </a-tag>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {datetime} from '../../common/scm-display';
import {money} from '../delivery-display';
import {printStatuses} from '../delivery-types';
import type {Id, PrintStatus, RouteCustomerView, RouteOrderView} from '../delivery-types';

type CustomerStatusFilter = 'ALL' | 'PRINTED' | 'UNPRINTED' | 'PARTIAL';
type CustomerOrderFilter = 'ALL' | 'PRINTED' | 'UNPRINTED';

const props = defineProps<{
  printMode: 'orders' | 'customers';
  customerStatusFilter: CustomerStatusFilter;
  customerFilter: CustomerOrderFilter;
  orderSelection: Id[];
  customerSelection: Id[];
  orders: RouteOrderView[];
  customers: RouteCustomerView[];
  loading: boolean;
  busy: boolean;
  canPrint: boolean;
  canRecordPrint: boolean;
  targetCount: number;
  canViewAmount: boolean;
}>();

const emit = defineEmits<{
  'update:printMode': [value: 'orders' | 'customers'];
  'update:customerStatusFilter': [value: CustomerStatusFilter];
  'update:customerFilter': [value: CustomerOrderFilter];
  'update:orderSelection': [value: Id[]];
  'update:customerSelection': [value: Id[]];
  recordPrint: [];
}>();

const orderViewColumns = computed<TableColumnsType<RouteOrderView>>(() => [
  {title: '订单号', dataIndex: 'orderNo', width: 170},
  {title: '客户', dataIndex: 'customerName', width: 160},
  {title: '停靠序', dataIndex: 'stopSeq', width: 80, align: 'right'},
  {title: '商品行', dataIndex: 'itemCount', width: 80, align: 'right'},
  ...(props.canViewAmount ? [{title: '订单金额', dataIndex: 'orderAmount', width: 120, align: 'right' as const}] : []),
  {title: '打印次数', dataIndex: 'printCount', width: 90, align: 'right'},
  {title: '打印状态', dataIndex: 'printStatus', width: 100, align: 'center'},
  {title: '最近打印', dataIndex: 'lastPrintedAt', width: 170},
]);

const customerViewColumns = computed<TableColumnsType<RouteCustomerView>>(() => [
  {title: '客户', dataIndex: 'customerName', width: 200},
  {title: '订单数', dataIndex: 'orderCount', width: 90, align: 'right'},
  {title: '商品行', dataIndex: 'itemCount', width: 90, align: 'right'},
  ...(props.canViewAmount ? [{title: '金额', dataIndex: 'totalAmount', width: 130, align: 'right' as const}] : []),
  {title: '已打印订单', dataIndex: 'printedOrderCount', width: 110, align: 'right'},
  {title: '打印状态', dataIndex: 'printStatus', width: 110, align: 'center'},
]);

const orderRowSelection = computed(() => ({
  selectedRowKeys: props.orderSelection,
  onChange: (keys: Array<string | number>) => emit('update:orderSelection', keys),
}));

const customerRowSelection = computed(() => ({
  selectedRowKeys: props.customerSelection,
  onChange: (keys: Array<string | number>) => emit('update:customerSelection', keys),
}));
</script>
