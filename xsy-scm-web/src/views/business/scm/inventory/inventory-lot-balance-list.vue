<!-- 批次库存：按仓库 + 商品规格 + 批次展开；页面只读，批次写入发生在收货和出库命令中。 -->
<template>
  <a-form class="scm-filter-bar" layout="inline" @submit.prevent="onSearch">
    <div class="scm-filter-fields">
      <a-form-item label="仓库">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px" />
      </a-form-item>
      <a-form-item label="批次号">
        <a-input v-model:value="queryForm.lotNo" placeholder="批次号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="来源生产批号">
        <a-input v-model:value="queryForm.sourceLotNo" placeholder="来源生产批号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="商品名称">
        <a-input v-model:value="queryForm.productName" placeholder="商品名称" allow-clear @pressEnter="onSearch" />
      </a-form-item>
    </div>
    <div class="scm-filter-actions">
      <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:lot:query'">查询</a-button>
      <a-button @click="resetQuery">重置</a-button>
    </div>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block scm-table-toolbar">
      <div class="smart-table-operate-block"></div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_LOT_BALANCE" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.LOT_BALANCE"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '暂无批次库存'}"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuName'">
          {{ skuMainText(record.specValues, record.skuName) }}
        </template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lotNo'">
          <span class="scm-mono">{{ record.lotNo }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-if="canViewInspection" type="link" size="small" @click="inspectionLot = record">检测报告</a-button>
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
  <a-drawer :open="!!inspectionLot" title="批次检测报告" :width="scmDrawerWidth('m')" @close="inspectionLot = undefined">
    <template v-if="inspectionLot">
      <p>{{ inspectionLot.productName }} · {{ inspectionLot.skuName }}</p>
      <TraceInspectionPanel :lot-id="inspectionLot.lotId" :lot-no="inspectionLot.lotNo" :can-manage="canManageInspection" />
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {inventoryLotApi} from '/@/api/business/scm/inventory-lot-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import {quantityText, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import type {InventoryLotBalance, InventoryLotBalanceQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {hasPermission} from '../common/scm-permission';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import TraceInspectionPanel from '/@/views/business/scm/trace/components/trace-inspection-panel.vue';

const canManageInspection = hasPermission('scm:trace:inspection:manage');
const canViewInspection = canManageInspection || hasPermission('scm:inventory:unit:query');
const inspectionLot = ref<InventoryLotBalance>();

const queryForm = reactive<InventoryLotBalanceQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryLotBalance[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);

type InventoryLotBalanceColumn = TableColumnsType<InventoryLotBalance>[number] & {showFlag?: boolean};

const columns = ref<InventoryLotBalanceColumn[]>([
  {title: '仓库', dataIndex: 'warehouseName', width: 140, ellipsis: true},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120, ellipsis: true, showFlag: false},
  {title: '商品名称', dataIndex: 'productName', width: 180, ellipsis: true},
  {title: '商品规格', dataIndex: 'skuName', width: 150, ellipsis: true},
  {title: '规格编码', dataIndex: 'skuCode', width: 130, ellipsis: true, showFlag: false},
  {title: '批次号', dataIndex: 'lotNo', width: 190, ellipsis: true},
  {title: '来源生产批号', dataIndex: 'sourceLotNo', width: 160, ellipsis: true},
  {title: '数量', dataIndex: 'quantity', width: 110},
  {title: '单位', dataIndex: 'unit', width: 80},
  {title: '更新时间', dataIndex: 'updatedAt', width: 170},
  ...(canViewInspection ? [{title: '操作', dataIndex: 'action', width: 100, align: 'center' as const, fixed: 'right' as const}] : []),
]);

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    const res = await inventoryLotApi.queryBalance(queryForm, {suppressGlobalErrorMessage: true});
    tableData.value = res.data?.list ?? [];
    total.value = Number(res.data?.total ?? 0);
  } catch (e) {
    // 失败时显示空表，同时在页面上提示并允许重试
    error.value = inventoryError(e);
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  void queryData();
}

function resetQuery() {
  queryForm.lotNo = undefined;
  queryForm.sourceLotNo = undefined;
  queryForm.productName = undefined;
  onSearch();
}

async function loadWarehouses() {
  try {
    const res = await warehouseApi.query({pageNum: 1, pageSize: 100}, {suppressGlobalErrorMessage: true});
    warehouses.value = (res.data?.list ?? []) as unknown as Warehouse[];
  } catch {
    // 仓库下拉失败不影响列表，列表仍按权限返回
    warehouses.value = [];
  }
}

onMounted(async () => {
  await loadWarehouses();
  await queryData();
});
</script>
