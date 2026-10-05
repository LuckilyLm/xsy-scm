<!--
  库存流水只读查询，来源单号可跳转至收货单列表。
  时间筛选使用业务发生时刻 occurred_at，区间左闭右开；历史回填的写入时刻不参与筛选。
  列按「谁 / 什么货 / 动了多少 / 动完剩多少」组织：仓库与商品规格的编码作为名称下方的
  secondary text，期初量收进结存格的次要行，不再各占一列。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="queryForm.warehouseId" width="200px"/>
      </a-form-item>
      <a-form-item label="商品规格编码" class="smart-query-form-item">
        <a-input v-model:value="queryForm.skuCode" placeholder="商品规格编码" allow-clear @pressEnter="onSearch"/>
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
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
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
        :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'occurredAt'">{{ datetime(record.occurredAt) }}</template>
        <template v-else-if="column.dataIndex === 'movementType'">
          <!-- 流水表的 quantity 恒为正，方向只能由类型派生，故两者同格展示 -->
          <span class="movement-type">
            <ScmStatusTag
                v-if="directionOf(record)"
                :tone="directionTone(directionOf(record))"
                :label="directionOf(record)"
            />
            <span>{{ movementTypeText(record.movementType, SCM_INVENTORY_MOVEMENT_TYPE_ENUM) }}</span>
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'receiptNo'">
          <!-- 入库显示收货单号、出库显示出库单号；两者是不同的跳转目标，故分开字段而非合并 -->
          <a v-if="record.receiptNo" @click="openReceipt(record.receiptNo)">{{ record.receiptNo }}</a>
          <span v-else-if="record.sourceDocumentNo">{{ record.sourceDocumentNo }}</span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.dataIndex === 'warehouse'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.warehouseName || '—' }}</span>
            <span v-if="record.warehouseCode" class="scm-cell-stack__sub">{{ record.warehouseCode }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'sku'">
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ skuMainText(record.specValues, record.skuName) }}</span>
            <span v-if="record.skuCode" class="scm-cell-stack__sub">{{ record.skuCode }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitSnapshot'">{{ record.unitSnapshot || '—' }}</template>
        <template v-else-if="column.dataIndex === 'unitCost'">
          <span class="scm-money">{{ moneyText(record.unitCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'afterQuantity'">
          <!-- 结存：期末在上（本次动完的账面量），期初在下（同一格内的对照值） -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main scm-quantity">{{ quantityText(record.afterQuantity) }}</span>
            <span class="scm-cell-stack__sub scm-quantity">期初 {{ quantityText(record.beforeQuantity) }}</span>
          </div>
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
import {useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {inventoryMovementApi} from '/@/api/business/scm/inventory-movement-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_INVENTORY_MOVEMENT_INBOUND_TYPES,
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_TABLE_ID,
} from '/@/constants/business/scm/inventory-const';
import type {InventoryMovement, InventoryMovementQuery} from './inventory-types';
import {moneyText, movementTypeText, quantityText, skuMainText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import {datetime} from '../common/scm-display';

const router = useRouter();
const queryForm = reactive<InventoryMovementQuery>({pageNum: 1, pageSize: 20});
const occurredRange = ref<[string, string] | undefined>();
const tableData = ref<InventoryMovement[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
let requestId = 0;

// 流水一行 = 一次库存变动。列按「何时 / 什么业务 / 哪张单 / 哪个仓 / 什么货 / 动多少 / 动完剩多少」排列；
// 仓库与商品规格的编码是名称下方的次要信息，期初量是结存的对照行，都不再各占一列。
const columns = ref<TableColumnsType<InventoryMovement>>([
  {title: '发生时间', dataIndex: 'occurredAt', width: 170},
  {title: '类型', dataIndex: 'movementType', width: 150},
  {title: '业务单号', dataIndex: 'receiptNo', width: 170},
  {title: '仓库', dataIndex: 'warehouse', width: 150},
  {title: '商品规格', dataIndex: 'sku', width: 200},
  {title: '数量', dataIndex: 'quantity', align: 'right', width: 110},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 80},
  {title: '单位成本', dataIndex: 'unitCost', align: 'right', width: 120},
  {title: '结存', dataIndex: 'afterQuantity', align: 'right', width: 140},
  {title: '操作者', dataIndex: 'operator', width: 110},
]);

/**
 * 入 / 出方向。
 *
 * 流水表的 `quantity` 恒为正，方向**只能**由类型派生，因此这里读的是与后端
 * `ScmInventoryMovementTypeEnum.getInbound()` 同源的 `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES`，
 * 而不是在本页再写一份 IN / OUT 名单 —— 那样后端加类型时方向会判反，而数字看起来完全正常。
 */
function directionOf(record: InventoryMovement): '入' | '出' | undefined {
  const type = record.movementType;
  if (!type) {
    return undefined;
  }
  return SCM_INVENTORY_MOVEMENT_INBOUND_TYPES.includes(type) ? '入' : '出';
}

/** 入 = 库存增加（绿），出 = 库存减少（橙）。 */
function directionTone(direction: string | undefined): ScmStatusTone {
  return direction === '入' ? 'success' : 'warning';
}

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
  router.push({path: '/purchase/purchase-receipt-list', query: {receiptNo}});
}

onMounted(queryData);
</script>

<style scoped>
/* 方向标签与类型文案同格：标签不换行，文案溢出省略，避免撑高行 */
.movement-type {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.movement-type > span:last-child {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.movement-type :deep(.ant-tag) {
  margin-inline-end: 0;
}
</style>
