<!--
  W6 库存流水（新增文件）。仿 W5 `purchase-log-list.vue` 的只读列表骨架。
  适配：`/scm/inventory/movement/query`（**唯一**端点）、`scm:inventory:movement:query`（菜单 821）、
        `scm-inventory-movement-table`、`inventory-errors`、loading/empty/error/retry、`v-privilege`。

  **append-only 在页面上的体现**：没有任何新增/编辑/删除入口。流水是账本，
  未来冲销靠「新增反向 movement」，因此这一页永远只会多行、不会改行。

  **来源单号可跳收货单**：`receiptNo` 链到收货单列表并带上 `receiptNo` 查询参数
  （见 `purchase-receipt-list.vue` 里的 `route.query.receiptNo` 处理）。
  它是**人类可读溯源**；机器可读的溯源是 `sourceDocumentId` / `sourceDocumentItemId`（不展示）。

  **时间筛选过滤的是 `occurred_at`（业务发生时刻 = 收货确认时刻），不是写入时刻**：
  backfill 回放的历史收货，其写入时刻是迁移执行时刻，用写入时刻过滤会让这部分数据查不到。
  区间为**左闭右开**：`occurredFrom <= occurred_at < occurredTo`。
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
          <a v-if="record.receiptNo" @click="openReceipt(record.receiptNo)">{{ record.receiptNo }}</a>
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
      // 枚举筛选清空时必须送 undefined（字段被省略），**不能送空串**：
      // 后端 `InventoryMovementQueryForm` 上是 `@Pattern(regexp = "PURCHASE_IN")`，
      // 空串会被 Bean Validation 判成 40000，页面看起来像「一清空就报错」。
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

/** 来源单号 → 收货单列表（带 `receiptNo` 查询参数）。只读溯源，不改变收货单任何状态。 */
function openReceipt(receiptNo: string) {
  router.push({ path: '/purchase/purchase-receipt-list', query: { receiptNo } });
}

onMounted(queryData);
</script>

<style scoped>
/* 数量与金额一律等宽右对齐：4 位定点数在比例字体下会参差不齐，扫描一列数字时很费眼 */
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
