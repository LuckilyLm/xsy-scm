<!--
  库存预留（出库波次新增）。

  预留是**业务动作的副产物**（销售订单确认时产生），不是人手工录的单据 ——
  因此本页只有查询与释放，没有「新建」。
  释放把占用归还可用量；重复释放会被拒绝（41016），不会把可用量虚增。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.status"
            :options="statusOptions"
            placeholder="全部"
            allow-clear
            style="width: 140px"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:reservation:query'">
            查询
          </a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-typography-text type="secondary">
          预留占用「可用量」但不改变物理库存；可用量 = 现有量 − 预留量。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_RESERVATION"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.RESERVATION"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无预留记录' }"
        :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sourceDocumentNo'">
          {{ record.sourceDocumentNo || `#${record.sourceDocumentId ?? '—'}` }}
        </template>
        <template v-else-if="column.dataIndex === 'occurredAt'">{{ datetime(record.occurredAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button
              v-if="record.status === 'ACTIVE'"
              type="link"
              size="small"
              danger
              @click="onRelease(record)"
              v-privilege="'scm:inventory:reservation:release'"
          >
            释放
          </a-button>
          <span v-else>—</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {inventoryReservationApi} from '/@/api/business/scm/inventory-reservation-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_RESERVATION_STATUS_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {InventoryReservation, InventoryReservationQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {quantityText, singleWarehouseDefault} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';

const queryForm = reactive<InventoryReservationQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryReservation[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

const statusOptions = Object.values(SCM_INVENTORY_RESERVATION_STATUS_ENUM).map((i) => ({
  value: i.value,
  label: i.desc,
}));

const columns = ref<TableColumnsType<InventoryReservation>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 160},
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 160},
  {title: 'SKU 名称', dataIndex: 'skuName', width: 150},
  {title: '商品名称', dataIndex: 'productName', width: 160},
  {title: '预留数量', dataIndex: 'quantity', align: 'right', width: 120},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 90},
  {title: '来源单号', dataIndex: 'sourceDocumentNo', width: 180},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '发生时间', dataIndex: 'occurredAt', width: 180},
  {title: '操作', dataIndex: 'action', width: 90, fixed: 'right'},
]);

function statusColor(status?: string) {
  if (status === 'ACTIVE') return 'orange';
  if (status === 'CONSUMED') return 'green';
  return 'default';
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryReservationApi.query({...queryForm});
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = inventoryError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

async function applySingleWarehouseDefault() {
  try {
    const r = await warehouseApi.list();
    warehouses.value = r.data ?? [];
    const fallback = singleWarehouseDefault(warehouses.value);
    if (fallback !== undefined) {
      queryForm.warehouseId = fallback;
    }
  } catch {
    warehouses.value = [];
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.warehouseId = undefined;
  queryForm.status = undefined;
  onSearch();
}

function onRelease(record: InventoryReservation) {
  Modal.confirm({
    title: '释放预留',
    content: `确认释放该预留？释放后 ${quantityText(record.quantity)} ${record.unitSnapshot ?? ''} 将归还到可用量。`,
    okText: '释放',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await inventoryReservationApi.release(record.id);
        message.success('已释放，可用量已恢复');
        queryData();
      } catch (e) {
        message.error(inventoryError(e));
      }
    },
  });
}

onMounted(async () => {
  await applySingleWarehouseDefault();
  await queryData();
});
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
