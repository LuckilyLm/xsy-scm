<!-- 采购操作日志。先按采购单号找到采购单，再查询它的日志。 -->
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
      <span v-if="orderNo" class="scm-query-table-context">当前采购单：{{ orderNo }}</span>
      <div class="scm-query-table-tools">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_LOG" :refresh="queryData" />
      </div>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-table
      :id="SCM_PURCHASE_TABLE_ID.LOG"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'operationType'">
          {{ SCM_PURCHASE_OPERATION_ENUM[record.operationType]?.desc || record.operationType }}
        </template>
        <template v-else-if="column.dataIndex === 'purchaseReceiptId'">
          {{ record.purchaseReceiptId ?? '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'reason'">{{ record.reason || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate scm-table-actions">
            <a-button
              type="link"
              @click="
                active = record;
                visible = true;
              "
              >变更前后</a-button
            >
          </div>
        </template>
      </template>
    </a-table>

    <a-empty v-if="!loading && !tableData.length" description="请先输入采购单号查询操作日志" />
  </a-card>

  <a-modal :open="visible" title="变更前后" width="900px" :footer="null" @cancel="visible = false">
    <ScmDiffTable :before="active?.beforeData" :after="active?.afterData" :scope="active?.operationType" />
  </a-modal>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_OPERATION_ENUM, SCM_PURCHASE_TABLE_ID} from '/@/constants/business/scm/purchase-const';
import type {LogRow} from './purchase-types';
import {purchaseError} from './purchase-errors';
import {datetime} from '../common/scm-display';

const orderNo = ref<string | undefined>(undefined);
const operationType = ref<string | undefined>(undefined);
const tableData = ref<LogRow[]>([]);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const active = ref<LogRow>();
let requestId = 0;

/** 日志已按采购单筛选，不再显示采购单 ID；保留关联收货单。 */
const columns = ref<TableColumnsType<LogRow>>([
  {title: '时间', dataIndex: 'createdAt', width: 200, customRender: ({text}) => datetime(text)},
  {title: '操作', dataIndex: 'operationType', align: 'center', width: 150},
  {title: '操作人', dataIndex: 'operator', width: 130},
  {title: '关联收货单（内部编号）', dataIndex: 'purchaseReceiptId', width: 200},
  {title: '原因', dataIndex: 'reason', width: 200},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 120},
]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  tableData.value = [];
  try {
    if (!orderNo.value?.trim()) {
      return;
    }
    // 查询失败由页头提示并允许重试
    const found = await purchaseOrderApi.query({pageNum: 1, pageSize: 1, orderNo: orderNo.value.trim()}, {suppressGlobalErrorMessage: true});
    const order = found.data.list[0];
    if (!order) {
      throw new Error(`采购单 ${orderNo.value} 不存在`);
    }
    const r = await purchaseOrderApi.logs(order.id!, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      // 操作类型是客户端过滤：后端只按采购单维度提供日志，没有按类型过滤的入参。
      tableData.value = operationType.value ? r.data.filter((row) => row.operationType === operationType.value) : r.data;
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
