<!-- 采购操作日志。先按采购单号找到采购单，再查询它的日志。 -->
<template>
  <a-form class="xsy-query-form" layout="inline" @submit.prevent>
    <a-row class="xsy-query-form-row">
      <a-form-item label="采购单号" class="xsy-query-form-item">
        <a-input v-model:value="orderNo" placeholder="采购单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="操作类型" class="xsy-query-form-item">
        <XsyEnumSelect enum-name="SCM_PURCHASE_OPERATION_ENUM" v-model:value="operationType" width="170px" />
      </a-form-item>
      <a-form-item class="xsy-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:log:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
      <span v-if="orderNo" class="scm-query-table-context">当前采购单：{{ orderNo }}</span>
    </a-row>
  </a-form>

  <RequestError v-if="error" :message="error">
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </RequestError>

  <a-card size="small" :bordered="false">
    <div class="xsy-table-btn-block">
      <div class="xsy-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_LOG" :refresh="queryData" />
      </div>
    </div>

    <!-- 查询过才出表格：未查询时表格自带空状态会和下面的提示叠成两块「没有数据」 -->
    <LocalTable
      :page-size-options="SCM_PAGE_SIZE_OPTIONS"
      v-if="queried"
      :id="SCM_PURCHASE_TABLE_ID.LOG"
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'operationType'">
          {{ SCM_PURCHASE_OPERATION_ENUM[record.operationType]?.desc || record.operationType }}
        </template>
        <template v-else-if="column.dataIndex === 'reason'">{{ record.reason || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="xsy-table-operate scm-table-actions">
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
    </LocalTable>

    <a-empty v-if="!queried && !loading" description="请先输入采购单号查询操作日志" />
  </a-card>

  <a-modal :open="visible" title="变更前后" width="min(760px, 92vw)" :footer="null" @cancel="visible = false">
    <ScmDiffTable :before="active?.beforeData" :after="active?.afterData" :scope="active?.operationType" />
  </a-modal>
</template>

<script setup lang="ts">
import LocalTable from '/@/components/support/local-table/index.vue';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import XsyEnumSelect from '/@/components/framework/xsy-enum-select/index.vue';
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
/** 是否已经查过一次采购单；没查过时只显示「先输入单号」的提示，不显示空表格 */
const queried = ref(false);
const loading = ref(false);
const error = ref('');
const visible = ref(false);
const active = ref<LogRow>();
let requestId = 0;

/** 日志已按采购单筛选；内部收货单 ID 对用户无意义，不展示。 */
const columns = ref<TableColumnsType<LogRow>>([
  {title: '时间', dataIndex: 'createdAt', width: 200, customRender: ({text}) => datetime(text)},
  // 与筛选条同名词；右侧还有一列「操作」放查看动作，两列不能同名
  {title: '操作类型', dataIndex: 'operationType', align: 'center', width: 150},
  {title: '操作人', dataIndex: 'operator', width: 130},
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
      queried.value = false;
      return;
    }
    queried.value = true;
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
