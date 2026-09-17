<!-- 仿 W4 `order-log-list.vue`（新增文件）。
**与 W4 的关键差异**：W5 **没有**全局日志分页端点 —— 只有 `GET /scm/purchase/log/{orderId}`
（设计 §8.1 的 27 个端点里没有 `log/query`）。因此本页按**采购单维度**查询：
先用单号定位采购单，再拉它的日志。不为了对齐 W4 的形状而伪造一个不存在的接口。
适配：`scm:purchase:log:query`（A22）、`scm-purchase-log-table`（A23）、
      `purchase-errors`（A24）、loading/empty/error/retry（A27）、
      日志按 `created_at DESC` 返回（**最新在前**，与 W4 一致）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="采购单号" class="smart-query-form-item">
        <a-input v-model:value="orderNo" placeholder="采购单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="操作类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_PURCHASE_OPERATION_ENUM" v-model:value="operationType" width="170px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:log:query'">查询</a-button>
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
        操作日志<span v-if="orderNo">：{{ orderNo }}</span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_LOG" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_PURCHASE_TABLE_ID.LOG"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 1200 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'operationType'">
          {{ SCM_PURCHASE_OPERATION_ENUM[record.operationType]?.desc || record.operationType }}
        </template>
        <template v-else-if="column.dataIndex === 'purchaseOrderId'">
          {{ record.purchaseOrderId ?? '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'purchaseReceiptId'">
          {{ record.purchaseReceiptId ?? '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'reason'">{{ record.reason || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" @click="active = record; visible = true">变更前后</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <a-empty v-if="!loading && !tableData.length" description="请先输入采购单号查询操作日志" />
  </a-card>

  <a-modal :open="visible" title="变更前后" width="900px" :footer="null" @cancel="visible = false">
    <ScmDiffTable :before="active?.beforeData" :after="active?.afterData" />
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { TableColumnsType } from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import { purchaseOrderApi } from '/@/api/business/scm/purchase-order-api';
import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
import { SCM_PURCHASE_OPERATION_ENUM, SCM_PURCHASE_TABLE_ID } from '/@/constants/business/scm/purchase-const';
import type { LogRow } from './purchase-types';
import { purchaseError } from './purchase-errors';
import { datetime } from '../common/scm-display';

const orderNo = ref<string | undefined>(undefined);
const operationType = ref<string | undefined>(undefined);
const tableData = ref<LogRow[]>([]);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const active = ref<LogRow>();
let requestId = 0;

const columns: TableColumnsType<LogRow> = [
  { title: '时间', dataIndex: 'createdAt', width: 200, customRender: ({ text }) => datetime(text) },
  { title: '操作', dataIndex: 'operationType', width: 150 },
  { title: '操作人', dataIndex: 'operator', width: 130 },
  { title: '采购单 id', dataIndex: 'purchaseOrderId', width: 120 },
  { title: '收货单 id', dataIndex: 'purchaseReceiptId', width: 120 },
  { title: '原因', dataIndex: 'reason', width: 200 },
  { title: '操作', dataIndex: 'action', align: 'right', fixed: 'right', width: 120 },
];

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  tableData.value = [];
  try {
    if (!orderNo.value?.trim()) {
      return;
    }
    const found = await purchaseOrderApi.query({ pageNum: 1, pageSize: 1, orderNo: orderNo.value.trim() });
    const order = found.data.list[0];
    if (!order) {
      throw new Error(`采购单 ${orderNo.value} 不存在`);
    }
    const r = await purchaseOrderApi.logs(order.id!);
    if (id === requestId) {
      // 操作类型是**客户端过滤**：后端只按采购单维度提供日志，没有按类型过滤的入参。
      tableData.value = operationType.value
        ? r.data.filter((row) => row.operationType === operationType.value)
        : r.data;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryData();
}

function resetQuery() {
  orderNo.value = undefined;
  operationType.value = undefined;
  queryData();
}

onMounted(queryData);
</script>

<style scoped>
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
