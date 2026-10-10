<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-form-item label="往来类型">
      <a-radio-group v-model:value="query.accountType" @change="changeAccount">
        <a-radio-button value="RECEIVABLE">应收账龄</a-radio-button>
        <a-radio-button value="PAYABLE">应付账龄</a-radio-button>
      </a-radio-group>
    </a-form-item>
    <a-form-item label="截止日" required>
      <a-date-picker v-model:value="query.asOfDate" value-format="YYYY-MM-DD" :allow-clear="false"/>
    </a-form-item>
    <a-form-item v-if="query.accountType === 'RECEIVABLE'" label="客户">
      <CustomerSelect v-model:value="query.customerId" width="190px"/>
    </a-form-item>
    <a-form-item v-if="query.accountType === 'RECEIVABLE'" label="结算方">
      <CustomerSelect v-model:value="query.settlementCustomerId" width="190px"/>
    </a-form-item>
    <a-form-item v-else label="供应商">
      <SupplierSelect v-model:value="query.supplierId" width="190px"/>
    </a-form-item>
    <a-form-item label="仓库"><WarehouseSelect v-model:value="query.warehouseId" width="190px"/></a-form-item>
    <a-form-item label="账龄分组">
      <a-select v-model:value="query.agingBucket" :options="bucketOptions" allow-clear placeholder="全部分组" class="bucket-select"/>
    </a-form-item>
    <a-form-item label="关键字"><a-input v-model:value="query.keyword" :maxlength="120" placeholder="单号或往来方" allow-clear @pressEnter="search"/></a-form-item>
    <a-form-item>
      <a-space><a-button type="primary" :loading="loading" v-privilege="PERM.FINANCE_AGING_QUERY" @click="search">查询</a-button>
        <a-button @click="reset">重置</a-button></a-space>
    </a-form-item>
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon class="aging-note">
    <template #action><a-button @click="load">重试</a-button></template>
  </a-alert>
  <a-card :bordered="false" size="small">
    <div class="aging-toolbar">
      <a-typography-text v-if="applied">截至 {{ applied.asOfDate }} · {{ applied.accountType === 'RECEIVABLE' ? '应收' : '应付' }}未核销余额</a-typography-text>
      <a-typography-text v-else type="secondary">请选择截止日后查询</a-typography-text>
      <a-button v-privilege="PERM.EXPORT" :loading="exporting" :disabled="!applied || loading || !!error" @click="exportRows">导出当前结果</a-button>
    </div>
    <a-table size="small" :data-source="summaryRows" :columns="summaryColumns" row-key="agingBucket"
             :pagination="false" :loading="loading" :scroll="{x: 450}" class="aging-summary"/>
    <a-table id="scm-report-finance-aging-table" size="small" :data-source="rows" :columns="columns"
             row-key="documentId" bordered :pagination="false" :loading="loading" :scroll="{x: 1760}"
             :locale="{emptyText: '当前范围没有未核销余额'}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex === 'agingBucket'">{{ bucketLabel(record.agingBucket) }}</template>
        <template v-else-if="column.dataIndex === 'dueDate'">{{ record.dueDate || '未设置' }}</template>
        <template v-else-if="column.dataIndex === 'overdueDays'">{{ record.overdueDays ?? '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" v-privilege="record.accountType === 'RECEIVABLE' ? 'scm:finance:receivable:query' : 'scm:finance:payable:query'" @click="openDetail(record)">单据详情</a-button>
        </template>
        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                    show-size-changer show-quick-jumper :show-total="(n:number) => `共 ${n} 张单据`" @change="changePage"/>
    </div>
  </a-card>
  <FinanceDetailDrawer v-model:open="detailOpen" :loading="detailLoading" :kind="detailKind" :detail="detail"/>
</template>
<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import {message, type TableColumnsType} from 'ant-design-vue';
import {financeAgingApi, type AgingAccountType, type AgingBucket, type AgingQuery, type AgingRow, type AgingSummary} from '/@/api/business/scm/finance-aging-api';
import {financeApi} from '/@/api/business/scm/finance-api';
import type {FinancePayableDetail, FinanceReceivableDetail} from '../finance/finance-types';
import FinanceDetailDrawer from '../finance/finance-detail-drawer.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import {moneyText} from '../inventory/inventory-model';
import {reportError} from './report-errors';

const bucketOptions: {value: AgingBucket; label: string}[] = [
  {value: 'NOT_DUE', label: '未逾期'}, {value: 'DAYS_1_30', label: '逾期1至30天'},
  {value: 'DAYS_31_60', label: '逾期31至60天'}, {value: 'DAYS_61_90', label: '逾期61至90天'},
  {value: 'DAYS_91_180', label: '逾期91至180天'}, {value: 'OVER_180', label: '逾期超过180天'},
  {value: 'UNKNOWN', label: '到期日未设置'},
];

const defaults = (): AgingQuery => ({accountType: 'RECEIVABLE', asOfDate: dayjs().format('YYYY-MM-DD'), pageNum: 1, pageSize: 20});
const query = reactive<AgingQuery>(defaults());
const rows = ref<AgingRow[]>([]), summary = ref<AgingSummary[]>([]), total = ref(0);
const loading = ref(false), exporting = ref(false), error = ref(''), applied = ref<AgingQuery>();
const detailOpen = ref(false), detailLoading = ref(false), detailKind = ref<AgingAccountType>('RECEIVABLE');
const detail = ref<FinanceReceivableDetail | FinancePayableDetail>();
let requestId = 0, detailRequestId = 0;
const bucketLabel = (bucket: AgingBucket) => bucketOptions.find(option => option.value === bucket)?.label ?? bucket;
const summaryRows = computed(() => summary.value.map(row => ({...row, label: bucketLabel(row.agingBucket)}))
  .sort((a, b) => bucketOptions.findIndex(option => option.value === a.agingBucket) - bucketOptions.findIndex(option => option.value === b.agingBucket)));
const summaryColumns = [
  {title: '账龄分组', dataIndex: 'label'}, {title: '单据数', dataIndex: 'documentCount', align: 'right'},
  {title: '未核销余额', dataIndex: 'openAmount', customRender: ({text}: {text: string}) => moneyText(text), align: 'right'},
];
const columns: TableColumnsType<AgingRow> = [
  {title: '财务单号', dataIndex: 'documentNo', width: 190}, {title: '来源单号', dataIndex: 'sourceNo', width: 190},
  {title: '往来方', dataIndex: 'counterpartyName', width: 160}, {title: '结算方', dataIndex: 'settlementCustomerName', width: 160},
  {title: '冻结到期日', dataIndex: 'dueDate', width: 120}, {title: '逾期天数', dataIndex: 'overdueDays', width: 100},
  {title: '账龄分组', dataIndex: 'agingBucket', width: 140},
  ...(['amount', 'redAmount', 'netAmount', 'writtenOffAmount', 'openAmount'] as const).map((key, index) => ({
    title: ['原金额', '红字金额', '净额', '已核销', '未核销余额'][index], dataIndex: key, width: 130,
    align: 'right' as const, customRender: ({text}: {text: string}) => moneyText(text),
  })),
  {title: '操作', dataIndex: 'action', fixed: 'right', width: 100, align: 'center'},
];

async function load() {
  const id = ++requestId;
  if (!query.asOfDate) { loading.value = false; message.warning('请选择截止日'); return; }
  const payload = {...query};
  loading.value = true;
  error.value = '';
  rows.value = [];
  summary.value = [];
  total.value = 0;
  try {
    // 加载失败由页内 Alert 承担，不再让全局 toast 重复说一遍
    const quiet = {suppressGlobalErrorMessage: true};
    const [page, totals] = await Promise.all([financeAgingApi.query(payload, quiet), financeAgingApi.summary(payload, quiet)]);
    if (id !== requestId) return;
    rows.value = page.data.list;
    total.value = page.data.total;
    summary.value = totals.data;
    applied.value = payload;
  } catch (e) { if (id === requestId) error.value = reportError(e); }
  finally { if (id === requestId) loading.value = false; }
}
function search() { query.pageNum = 1; void load(); }
function changePage() {
  if (applied.value) {
    const pagination = {pageNum: query.pageNum, pageSize: query.pageSize};
    Object.assign(query, applied.value, pagination);
  }
  void load();
}
function changeAccount() {
  query.customerId = undefined;
  query.settlementCustomerId = undefined;
  query.supplierId = undefined;
  search();
}
function reset() {
  Object.assign(query, defaults(), {customerId: undefined, settlementCustomerId: undefined, supplierId: undefined,
    warehouseId: undefined, agingBucket: undefined, keyword: undefined});
  void load();
}
async function exportRows() {
  if (!applied.value || exporting.value) return;
  exporting.value = true;
  try { await financeAgingApi.export({...applied.value}); }
  catch {
    // 导出失败只走全局 toast
  }
  finally { exporting.value = false; }
}
async function openDetail(row: AgingRow) {
  const id = ++detailRequestId;
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = undefined;
  detailKind.value = row.accountType;
  try {
    const response = row.accountType === 'RECEIVABLE'
      ? await financeApi.receivableDetail(row.documentId, {suppressGlobalErrorMessage: true})
      : await financeApi.payableDetail(row.documentId, {suppressGlobalErrorMessage: true});
    if (id === detailRequestId) detail.value = response.data;
  } catch (e) {
    if (id === detailRequestId) { error.value = reportError(e); detailOpen.value = false; }
  } finally { if (id === detailRequestId) detailLoading.value = false; }
}
onMounted(load);
</script>
<style scoped>
.aging-note, .aging-summary { margin-bottom: 16px; }
.aging-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.bucket-select { min-width: 170px; }
</style>
