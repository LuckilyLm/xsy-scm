<!--
  库存余额只读查询，数量由收货确认产生的流水累加。
  SKU 使用编码/名称筛选，避免额外依赖商品选项接口的权限。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="220px"/>
      </a-form-item>
      <a-form-item label="商品规格编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="商品规格编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="商品名称" class="smart-query-form-item">
        <a-input v-model:value="queryForm.productName" placeholder="商品名称" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:balance:query'">查询</a-button>
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
          余额由「收货确认 → 采购入库」的流水累加而来，本页不提供修改入口。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_BALANCE"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.BALANCE"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无库存余额' }"
        :scroll="{ x: 1130 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'warehouseName'">{{ record.warehouseName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'warehouseCode'">
          <span class="scm-mono">{{ record.warehouseCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'skuName'">{{ skuMainText(record.specValues, record.skuName) }}</template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unit'">{{ record.unit || '—' }}</template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'reservedQuantity'">
          <span class="scm-quantity">{{ quantityText(record.reservedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'availableQuantity'">
          <span class="scm-quantity">{{ quantityText(record.availableQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'avgCost'">
          <span class="scm-money">{{ moneyText(record.avgCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'amount'">
          <span class="scm-money">{{ moneyText(record.amount) }}</span>
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
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {inventoryBalanceApi} from '/@/api/business/scm/inventory-balance-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import type {InventoryBalance, InventoryBalanceQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {moneyText, quantityText, singleWarehouseDefault, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';

const queryForm = reactive<InventoryBalanceQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryBalance[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
// 与选择器共享仓库列表，避免默认值判定重复请求。
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

// 余额按「仓库 + 商品规格」定位；低频编码单独保留并默认收起。
// 库存/预留/可用/均价/金额是一组要横向比较的数值，统一右对齐 + 等宽数字。
type InventoryBalanceColumn = TableColumnsType<InventoryBalance>[number] & {showFlag?: boolean};
const columns = ref<InventoryBalanceColumn[]>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '仓库编码', dataIndex: 'warehouseCode', width: 120, showFlag: false},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 160},
  {title: '规格编码', dataIndex: 'skuCode', width: 130, showFlag: false},
  {title: '单位', dataIndex: 'unit', align: 'center', width: 80},
  {title: '库存', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '预留', dataIndex: 'reservedQuantity', align: 'right', width: 100},
  {title: '可用', dataIndex: 'availableQuantity', align: 'right', width: 100},
  // V34 移动加权成本。均价恒有值（NOT NULL DEFAULT 0），金额由后端 quantity × avgCost 派生。
  {title: '平均成本', dataIndex: 'avgCost', align: 'right', width: 130},
  {title: '库存金额', dataIndex: 'amount', align: 'right', width: 140},
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryBalanceApi.query({...queryForm});
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

/**
 * 仅有一个启用仓库时默认选中；列表请求失败时跳过默认值，不阻断余额查询。
 */
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
  // 重置表示查看全部仓库，不重新应用默认值。
  queryForm.warehouseId = undefined;
  queryForm.skuCode = undefined;
  queryForm.productName = undefined;
  onSearch();
}

onMounted(async () => {
  // 先确定默认仓库，避免首屏短暂展示全部仓库的数据。
  await applySingleWarehouseDefault();
  await queryData();
});
</script>
