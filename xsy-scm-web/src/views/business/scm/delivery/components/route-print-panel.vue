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
    <span class="print-counts">
      <span v-for="item in activeCounts" :key="item.key">{{ item.label }} {{ item.count }}</span>
    </span>
    <a-button
        v-if="canPrint"
        type="primary"
        v-privilege="'scm:delivery:route:print'"
        :disabled="busy || !canRecordPrint"
        @click="emit('recordPrint')"
    >生成打印 · 登记 {{ targetCount }}</a-button>
  </div>
  <template v-if="printMode === 'orders'">
    <!-- 1~3 行用轻量列表：一两条数据铺一张宽表格，下面会空出一大片 -->
    <div v-if="orders.length && orders.length <= 3" class="print-cards">
      <label v-for="record in orders" :key="record.orderId" class="print-card">
        <a-checkbox
            :checked="orderSelection.includes(record.orderId)"
            @change="toggleOrder(record.orderId, $event)"
        />
        <div class="print-card__main">
          <div class="print-card__head">
            <strong class="scm-mono">{{ record.orderNo }}</strong>
            <span v-if="canViewAmount" class="scm-money">{{ money(record.orderAmount) }}</span>
          </div>
          <p class="print-card__line">{{ record.customerName }} · 停靠序 {{ record.stopSeq }}</p>
          <p class="print-card__line print-card__line--muted">
            打印 {{ record.printCount }} 次 · 最近 {{ record.lastPrintedAt ? datetime(record.lastPrintedAt) : '—' }}
          </p>
        </div>
        <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">
          {{ printStatuses[record.printStatus as PrintStatus].label }}
        </a-tag>
      </label>
    </div>
    <a-empty v-else-if="!orders.length && !loading" description="没有可打印的订单"/>
    <a-table
        v-else
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
  </template>
  <template v-else>
    <div v-if="customers.length && customers.length <= 3" class="print-cards">
      <label v-for="record in customers" :key="record.customerId" class="print-card">
        <a-checkbox
            :checked="customerSelection.includes(record.customerId)"
            @change="toggleCustomer(record.customerId, $event)"
        />
        <div class="print-card__main">
          <div class="print-card__head">
            <strong>{{ record.customerName }}</strong>
            <span v-if="canViewAmount" class="scm-money">{{ money(record.totalAmount) }}</span>
          </div>
          <p class="print-card__line">{{ record.orderCount }} 张订单 · 已打印 {{ record.printedOrderCount }}</p>
        </div>
        <a-tag :color="printStatuses[record.printStatus as PrintStatus].color">
          {{ printStatuses[record.printStatus as PrintStatus].label }}
        </a-tag>
      </label>
    </div>
    <a-empty v-else-if="!customers.length && !loading" description="没有可打印的客户"/>
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

/** 打印状态分布：字典顺序即展示顺序，计数只作提示，不参与提交口径。 */
const activeCounts = computed(() => {
  const rows: {printStatus: string}[] = props.printMode === 'orders' ? props.orders : props.customers;
  const counts = new Map<string, number>();
  rows.forEach((row) => counts.set(row.printStatus, (counts.get(row.printStatus) ?? 0) + 1));
  return Object.keys(printStatuses).map((key) => ({
    key,
    label: printStatuses[key as PrintStatus].label,
    count: counts.get(key) ?? 0,
  }));
});

function toggleOrder(id: Id, event: Event) {
  const checked = (event.target as {checked?: boolean} | null)?.checked === true;
  const next = checked
      ? [...props.orderSelection, id]
      : props.orderSelection.filter((item) => String(item) !== String(id));
  emit('update:orderSelection', next);
}

function toggleCustomer(id: Id, event: Event) {
  const checked = (event.target as {checked?: boolean} | null)?.checked === true;
  const next = checked
      ? [...props.customerSelection, id]
      : props.customerSelection.filter((item) => String(item) !== String(id));
  emit('update:customerSelection', next);
}
</script>

<style scoped>
.print-counts {
  display: flex;
  gap: 16px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.print-cards {
  display: grid;
  gap: 12px;
}

.print-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid var(--scm-border, #f0f0f0);
  border-radius: 8px;
  cursor: pointer;
}

.print-card__main {
  flex: 1 1 auto;
  min-width: 0;
}

.print-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.print-card__line {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
}

.print-card__line--muted {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}
</style>
