<!--
  库存预警列表（阈值预警波次新增）。

  **只读页**：预警不是一种可以「标记已读」的状态，它只是 (阈值, 可用量) 的当前计算结果。
  引入「已读 / 已忽略」会让预警与真实库存脱钩 —— 货补上了那条「已读」记录还在，
  货又少了它却已经被忽略过。用户想看什么就按状态筛什么。

  页面上必须讲清楚的一件事：**判定基准是可用量（现有量 − 预留量），不是现有量**。
  「明明有 20 kg 在库，为什么说低于下限 10 kg？」的答案是那 20 kg 里有 18 kg 已预留 ——
  下限的业务含义是「还够不够发货」，货已经被订走就不算有货。
  因此三个数量都展示出来，用户能自己看懂预警为什么触发。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" :options="warehouses" width="200px"/>
      </a-form-item>
      <a-form-item label="SKU 编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="SKU 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.status"
            :options="statusOptions"
            style="width: 150px"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:warning:query'">查询</a-button>
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
          预警由「预警阈值」配置驱动：只有配置了阈值的仓库 + SKU 才会出现在这里。
          判定基准是<strong>可用量</strong>（现有量 − 预留量）。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_WARNING"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.WARNING"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="thresholdId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '没有需要处理的库存预警' }"
        :scroll="{ x: 1500 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.statusDesc || record.status }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'reservedQuantity'">
          <span class="num">{{ quantityText(record.reservedQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'availableQuantity'">
          <span class="num strong">{{ quantityText(record.availableQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMin'">
          <span class="num">{{ quantityText(record.warnMin) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'warnMax'">
          <span class="num">{{ quantityText(record.warnMax) }}</span>
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
import {inventoryWarningApi} from '/@/api/business/scm/inventory-warning-api';
import {warehouseApi} from '/@/api/business/scm/warehouse-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_TABLE_ID,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
} from '/@/constants/business/scm/inventory-const';
import type {InventoryWarning, InventoryWarningQuery} from './inventory-types';
import type {Warehouse} from '../purchase/purchase-types';
import {quantityText, singleWarehouseDefault} from './inventory-model';
import {inventoryError} from './inventory-errors';

const queryForm = reactive<InventoryWarningQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<InventoryWarning[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const warehouses = ref<Warehouse[]>([]);
let requestId = 0;

/**
 * 第一项是「仅异常」而不是「全部」—— 后端 status 为空时的语义就是只看异常。
 * 标成「全部」会与事实不符，用户选了它却看不到正常项会以为系统漏数据。
 */
const statusOptions = [
  {value: undefined, label: '仅异常'},
  ...Object.values(SCM_INVENTORY_WARNING_STATUS_ENUM).map((i) => ({
    value: i.value,
    label: i.desc,
  })),
];

const columns = ref<TableColumnsType<InventoryWarning>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: 'SKU 编码', dataIndex: 'skuCode', width: 160},
  {title: 'SKU 名称', dataIndex: 'skuName', width: 150},
  {title: '商品名称', dataIndex: 'productName', width: 150},
  {title: '单位', dataIndex: 'unit', align: 'center', width: 90},
  {title: '现有量', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '已预留', dataIndex: 'reservedQuantity', align: 'right', width: 110},
  {title: '可用量', dataIndex: 'availableQuantity', align: 'right', width: 110},
  {title: '预警下限', dataIndex: 'warnMin', align: 'right', width: 110},
  {title: '预警上限', dataIndex: 'warnMax', align: 'right', width: 110},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110, fixed: 'right'},
]);

/** 低于下限是补货问题（红），高于上限是积压（橙）。 */
function statusColor(status?: string) {
  if (status === 'LOW') return 'red';
  if (status === 'HIGH') return 'orange';
  return 'green';
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryWarningApi.query({...queryForm});
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
  queryForm.skuCode = undefined;
  // 重置回「仅异常」而不是「全部」：这是本页的默认语义
  queryForm.status = undefined;
  onSearch();
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

.strong {
  font-weight: 600;
}
</style>
