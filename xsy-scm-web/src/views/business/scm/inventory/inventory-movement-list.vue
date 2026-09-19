<!--
  库存流水只读查询，来源单号可跳转至收货单列表。
  时间筛选使用业务发生时刻 occurred_at，区间左闭右开；历史回填的写入时刻不参与筛选。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" width="200px" />
      </a-form-item>
      <a-form-item label="SKU 编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="SKU 编码" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="流水类型" class="smart-query-form-item">
        <SmartEnumSelect
          enum-name="SCM_INVENTORY_MOVEMENT_TYPE_ENUM"
          v-model:value="queryForm.movementType"
          width="140px"
        />
      </a-form-item>
      <a-form-item label="发生区间" class="smart-query-form-item" extra="结束时间不包含">
        <a-range-picker
          v-model:value="occurredRange"
          show-time
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          :allow-empty="[true, true]"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:movement:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-typography-text type="secondary">
          流水是只追加的账本：不可编辑、不可删除，冲销以新增反向流水实现。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_MOVEMENT"
          :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
      :id="SCM_INVENTORY_TABLE_ID.MOVEMENT"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{ emptyText: '暂无库存流水' }"
      :scroll="{ x: 1900 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'occurredAt'">{{ datetime(record.occurredAt) }}</template>
        <template v-else-if="column.dataIndex === 'movementType'">
          {{ movementTypeText(record.movementType, SCM_INVENTORY_MOVEMENT_TYPE_ENUM) }}
        </template>
        <template v-else-if="column.dataIndex === 'receiptNo'">
          <!-- 入库显示收货单号、出库显示出库单号；两者是不同的跳转目标，故分开字段而非合并 -->
          <a v-if="record.receiptNo" @click="openReceipt(record.receiptNo)">{{ record.receiptNo }}</a>
          <span v-else-if="record.sourceDocumentNo">{{ record.sourceDocumentNo }}</span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="num">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitCost'">
          <span class="num">{{ quantityText(record.unitCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'beforeQuantity'">
          <span class="num">{{ quantityText(record.beforeQuantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'afterQuantity'">
          <span class="num">{{ quantityText(record.afterQuantity) }}</span>
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
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { TableColumnsType } from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import { inventoryMovementApi } from '/@/api/business/scm/inventory-movement-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type { InventoryMovement, InventoryMovementQuery } from './inventory-types';
import { movementTypeText, quantityText } from './inventory-model';
import { inventoryError } from './inventory-errors';
import { datetime } from '../common/scm-display';

const router = useRouter();
const queryForm = reactive<InventoryMovementQuery>({ pageNum: 1, pageSize: 20 });
const occurredRange = ref<[string, string] | undefined>();
const tableData = ref<InventoryMovement[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let requestId = 0;

const columns = ref<TableColumnsType<InventoryMovement>>([
  { title: '发生时间', dataIndex: 'occurredAt', width: 190 },
  { title: '类型', dataIndex: 'movementType', width: 110 },
  { title: '来源单号', dataIndex: 'receiptNo', width: 170 },
  { title: '仓库', dataIndex: 'warehouseName', width: 150 },
  { title: 'SKU 编码', dataIndex: 'skuCode', width: 170 },
  { title: 'SKU 名称', dataIndex: 'skuName', width: 140 },
  { title: '数量', dataIndex: 'quantity', align: 'right', width: 120 },
  { title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 80 },
  { title: '单位成本', dataIndex: 'unitCost', align: 'right', width: 120 },
  { title: '期初', dataIndex: 'beforeQuantity', align: 'right', width: 120 },
  { title: '期末', dataIndex: 'afterQuantity', align: 'right', width: 120 },
  { title: '操作者', dataIndex: 'operator', width: 110 },
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await inventoryMovementApi.query({
      ...queryForm,
      // 清空时省略枚举字段，空字符串会被后端校验拒绝。
      movementType: queryForm.movementType || undefined,
      occurredFrom: occurredRange.value?.[0] ?? null,
      occurredTo: occurredRange.value?.[1] ?? null,
    });
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

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.warehouseId = undefined;
  queryForm.skuId = undefined;
  queryForm.skuCode = undefined;
  queryForm.movementType = undefined;
  queryForm.sourceDocumentType = undefined;
  queryForm.sourceDocumentId = undefined;
  occurredRange.value = undefined;
  onSearch();
}

function openReceipt(receiptNo: string) {
  router.push({ path: '/purchase/purchase-receipt-list', query: { receiptNo } });
}

onMounted(queryData);
</script>

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
