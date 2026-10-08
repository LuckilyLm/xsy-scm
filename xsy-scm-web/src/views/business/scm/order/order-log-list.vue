<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">

      <a-form-item label="操作类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_OPERATION_ENUM" v-model:value="queryForm.operationType" width="160px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:order:log:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="605" :refresh="queryData"/>
      </div>
    </a-row>
    <a-table id="order-log-table" size="small" :data-source="tableData" :columns="columns" row-key="logId"
             :loading="loading" bordered :pagination="false" :scroll="{x:920}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='operationType'">{{ SCM_ORDER_OPERATION_ENUM[text]?.desc }}</template>
        <template v-else-if="['approvedAmount','refundAmount'].includes(column.dataIndex)">{{ amount(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <div class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="active=record;visible=true">变更前后</a-button>
          </div>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum"
                    v-model:page-size="queryForm.pageSize" :total="total" @change="queryData"
                    :show-total="(n:number)=>`共${n}条`"/>
    </div>
  </a-card>
  <a-modal :open="visible" title="订单变更前后" width="900px" :footer="null" @cancel="visible=false">
    <ScmDiffTable :before="active?.beforeData" :after="active?.afterData"/>
  </a-modal>
</template>
<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderLogApi as api} from '/@/api/business/scm/order-log-api';
import {SCM_ORDER_OPERATION_ENUM} from '/@/constants/business/scm/order-const';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import type {LogRow, Query} from './order-types';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import {useScmErrorToast} from '../common/scm-error-toast';
import {datetime} from '../common/scm-display';

const queryForm = reactive<Query>({pageNum: 1, pageSize: 20}), tableData = ref<LogRow[]>([]), total = ref(0),
    loading = ref(false), error = useScmErrorToast(), visible = ref(false), active = ref<LogRow>();
let requestId = 0;
/**
 * 日志列。
 *
 * 本页只有「变更前后」一个动作，操作列却占 240px —— 普通操作列 120～160px、
 * 明细下载类单动作页可到 120px。收窄后把省下的宽度还给「原因」：
 * 排查时读的是原因，不是那一列空白。
 *
 * 时间（`createdAt`）必须保留：日志页是明确列出的时间例外，
 * 隐藏时间会让「谁在什么时候改的」这条审计链断掉。
 */
const columns = ref<TableColumnsType<LogRow>>([{
  title: '时间',
  dataIndex: 'createdAt',
  width: 210,
  customRender: ({text}) => datetime(text)
}, {title: '操作', dataIndex: 'operationType', width: 140}, {
  title: '操作人',
  dataIndex: 'operatorName',
  width: 140
}, {title: '原因', dataIndex: 'reason', width: 320}, {
  title: '操作',
  dataIndex: 'action',
  align: 'center',
  fixed: 'right',
  width: 110
}]);

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await api.query(queryForm);
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) error.value = orderError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  queryForm.operationType = undefined;
  onSearch();
}

onMounted(queryData);
</script>
<style scoped>pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}</style>
