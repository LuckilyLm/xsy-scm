<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="业务日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期"/>
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期"/>
      </a-form-item>
      <a-form-item label="客户">
        <a-select v-model:value="query.customerId" allow-clear show-search option-filter-prop="label"
                  :options="customerOptions" placeholder="全部客户" style="width: 190px"/>
      </a-form-item>
      <a-form-item label="订单号"><a-input v-model:value="query.orderNo" allow-clear @press-enter="onSearch"/></a-form-item>
      <a-form-item label="方向">
        <a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width: 120px"/>
      </a-form-item>
      <a-form-item label="结清状态">
        <a-select v-model:value="query.settleState" allow-clear :options="settleOptions" placeholder="全部" style="width: 140px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.RECEIVABLE_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="page.error.value" class="page-error" type="error" show-icon :message="page.error.value">
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">应收明细</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_RECEIVABLE" :refresh="queryData"/>
      </div>
    </a-row>
    <div class="finance-mobile-balance-list">
      <div v-for="record in page.tableData.value" :key="record.receivableId" class="finance-mobile-balance-row">
        <div class="mobile-balance-heading"><strong>{{ record.receivableNo }}</strong><a-button type="link" @click="showDetail(record)">明细</a-button></div>
        <div class="mobile-balance-party">{{ record.customerName }} · {{ record.orderNo }}</div>
        <div class="mobile-balance-values"><span>未核销 <strong>{{ moneyText(record.openAmount) }}</strong></span><span>金额 {{ moneyText(record.amount) }}</span></div>
        <a-tag v-if="record.overAppliedAmount && record.overAppliedAmount!=='0.0000'" color="orange">超额核销待处理</a-tag>
      </div>
    </div>
    <a-table id="scm-finance-receivable-table" class="finance-table" size="small" :data-source="page.tableData.value" :columns="columns"
             row-key="receivableId" :loading="page.loading.value" :pagination="false" bordered :scroll="{x:1375}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='entryType'">
          <ScmStatusTag :color="SCM_FINANCE_ENTRY_COLOR[text]" :label="entryTypeText(text)"/>
        </template>
        <template v-else-if="column.dataIndex==='settleState'">
          <ScmStatusTag :tone="settleStateTone(text)" :label="settleStateText(text)"/>
        </template>
        <template v-else-if="column.dataIndex==='overAppliedAmount'">
          <span :class="moneyClass(text,'anomaly')">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="['amount','writtenOffAmount','openAmount'].includes(column.dataIndex)">
          <span :class="moneyClass(text, column.dataIndex==='amount'?'fact':'balance')">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="column.dataIndex==='eventAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <div class="scm-table-actions"><a-button type="link" @click="showDetail(record)">明细</a-button></div>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="query.pageNum" v-model:page-size="query.pageSize"
                    :total="page.total.value" @change="queryData" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-card>

  <FinanceDetailDrawer v-model:open="detailOpen" kind="RECEIVABLE" :loading="detailLoading" :detail="detailData"
                       :error="detailError" @retry="reloadDetail"/>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {financeApi} from '/@/api/business/scm/finance-api';
import {SCM_FINANCE_ENTRY_COLOR, SCM_FINANCE_ENTRY_TYPE_ENUM, SCM_FINANCE_SETTLE_STATE_ENUM} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {dateTimeText, entryTypeText, initialFinanceDateRange, moneyClass, moneyText, settleStateText, settleStateTone} from './finance-form-model';
import FinanceDetailDrawer from './finance-detail-drawer.vue';
import type {FinanceReceivable, FinanceReceivableDetail, ReceivableQuery} from './finance-types';
import {useFinancePage} from './use-finance-page';
import {useFinanceDetail} from './use-finance-detail';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';
import {SCM_FINANCE_PERMISSION as PERM} from '/@/constants/business/scm/finance-const';

const query = reactive<ReceivableQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinanceReceivable, ReceivableQuery>(financeApi.receivableQuery, financeApi.receivableExport);
const customerOptions = ref<Array<{label: string; value: string | number}>>([]);
const {open: detailOpen, loading: detailLoading, data: detailData, error: detailError,
    show: openDetail, load: reloadDetail} = useFinanceDetail<FinanceReceivableDetail>(
    (id) => financeApi.receivableDetail(id, {suppressGlobalErrorMessage: true})
);
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const settleOptions = Object.values(SCM_FINANCE_SETTLE_STATE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

const columns = ref<TableColumnsType<FinanceReceivable>>([
    {title: '应收单号', dataIndex: 'receivableNo', fixed: 'left', width: 110, ellipsis: true},
    {title: '未核销', dataIndex: 'openAmount', fixed: 'left', align: 'right', width: 90},
    {title: '客户', dataIndex: 'customerName', width: 150}, {title: '订单号', dataIndex: 'orderNo', width: 150},
    {title: '方向', dataIndex: 'entryType', align: 'center', width: 80}, {title: '金额', dataIndex: 'amount', align: 'right', width: 120},
    {title: '已核销', dataIndex: 'writtenOffAmount', align: 'right', width: 110},
    {title: '超额核销', dataIndex: 'overAppliedAmount', align: 'right', width: 120},
    {title: '结清状态', dataIndex: 'settleState', align: 'center', width: 110}, {title: '事件时点', dataIndex: 'eventAt', width: 165},
    {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'center', width: 65},
]);
useFinanceMobileActionColumn((compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
}, () => columns.value.length);

async function queryData() {
    await page.queryData(query);
}

function onSearch() {
    query.pageNum = 1;
    queryData();
}

function resetQuery() {
    Object.assign(query, {pageNum: 1, pageSize: 20, ...initialFinanceDateRange(), customerId: undefined,
        orderNo: undefined, entryType: undefined, settleState: undefined});
    queryData();
}

async function exportData() {
    await page.exportData(query);
}

function showDetail(row: FinanceReceivable) {
    return openDetail(row.receivableId);
}

onMounted(async () => {
    try {
        const response = await customerApi.optionList();
        customerOptions.value = response.data.map((item) => ({label: `${item.customerCode} · ${item.name}`, value: item.customerId}));
    } catch {
        // Customer filter is optional; the scoped AR list remains available without it.
    }
    queryData();
});
</script>

<style scoped>
.date-separator { margin: 0 8px; color: #667085; }
.page-error { margin-bottom: 12px; }
.money-alert { font-weight: 600; }
.finance-mobile-balance-list { display: none; }

.finance-mobile-balance-row { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.mobile-balance-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mobile-balance-heading strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-balance-party { margin: 0 0 6px; color: #667085; font-size: 12px; overflow-wrap: anywhere; }
.mobile-balance-values { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 14px; font-variant-numeric: tabular-nums; }
.mobile-balance-values span { color: #667085; font-size: 12px; }
.mobile-balance-values strong { color: #1d2939; font-weight: 600; }
@media (max-width: 768px) {
  .finance-mobile-balance-list { display: block; }
  .finance-table :deep(.ant-table-cell-fix-right) { position: static !important; right: auto !important; }
}
</style>
