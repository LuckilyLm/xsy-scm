<template>
  <a-form class="smart-query-form" layout="inline" :model="filters" @submit.prevent="search">
    <a-row class="smart-query-form-row">
      <a-form-item label="月份"
        ><a-date-picker v-model:value="filters.month" picker="month" value-format="YYYY-MM" :allow-clear="false"
      /></a-form-item>
      <a-form-item label="客户"><CustomerSelect v-model:value="filters.customerId" width="200px" /></a-form-item>
      <a-form-item
        ><a-space
          ><a-button type="primary" :loading="loading" @click="search">查询</a-button><a-button @click="reset">重置</a-button></a-space
        ></a-form-item
      >
    </a-row>
  </a-form>
  <a-alert v-if="error" type="error" show-icon :message="error"
    ><template #action><a-button @click="load">重试</a-button></template></a-alert
  >
  <a-alert v-if="(summary?.costMissingCount ?? 0) > 0" type="warning" show-icon message="部分客户尚未登记实际商品成本，请补齐后查看利润。" />
  <a-row :gutter="[12, 12]" class="monthly-summary">
    <a-col v-for="card in cards" :key="card.label" :xs="24" :sm="12" :lg="6"
      ><a-card size="small"
        ><span class="scm-note">{{ card.label }}</span>
        <div class="monthly-value scm-money">{{ moneyText(card.value) }}</div></a-card
      ></a-col
    >
  </a-row>
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block" justify="space-between">
      <a-space>
        <a-button v-if="canAdd" type="primary" @click="openAdd">登记成本费用</a-button>
        <a-button v-if="canAdd" @click="openBatch">批量登记</a-button>
        <a-button v-if="canQueryExpenses" @click="openExpenses()">成本费用明细</a-button>
        <a-button v-privilege="PERM.EXPORT" :loading="exporting" :disabled="!applied || loading || !!error" @click="exportRows">导出当前列</a-button>
      </a-space>
      <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_CUSTOMER_MONTHLY_PROFIT" :refresh="load" />
    </a-row>
    <a-table
      :columns="columns"
      :data-source="rows"
      :row-key="(row: CustomerMonthlyProfitRow) => String(row.dimensionId ?? 'unallocated')"
      size="small"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{column, record}">
        <template v-if="column.dataIndex === 'dimensionName'">{{ record.dimensionName }}</template>
        <template v-else-if="column.dataIndex === 'costMissingCount'">{{ record.costMissingCount > 0 ? '待补成本' : '可核算' }}</template>
        <template v-else-if="column.dataIndex === 'action'"
          ><a-button v-if="canQueryExpenses && record.dimensionId != null" type="link" size="small" @click="openExpenses(record.dimensionId)"
            >成本费用明细</a-button
          ></template
        >
        <span v-else class="scm-money">{{ moneyText(record[column.dataIndex]) }}</span>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        v-model:current="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        @change="load"
      />
    </div>
  </a-card>

  <a-modal
    :open="addOpen"
    title="登记客户月成本与费用"
    :confirm-loading="saving"
    :mask-closable="!saving"
    :closable="!saving"
    :cancel-button-props="{disabled: saving}"
    @ok="saveExpense"
    @cancel="requestCloseAdd"
  >
    <a-form ref="addFormRef" :model="expenseForm" layout="vertical" :disabled="saving">
      <a-form-item name="customerId" label="客户" :rules="[{required: true, message: '请选择客户'}]"
        ><CustomerSelect v-model:value="expenseForm.customerId"
      /></a-form-item>
      <a-form-item name="month" label="月份" :rules="[{required: true, message: '请选择月份'}]"
        ><a-date-picker v-model:value="expenseForm.month" picker="month" value-format="YYYY-MM" :allow-clear="false"
      /></a-form-item>
      <a-form-item name="goodsCostAmount" label="实际商品成本（选填）">
        <a-input-number
          v-model:value="expenseForm.goodsCostAmount"
          string-mode
          :min="0"
          :max="99999999999999"
          :precision="4"
          placeholder="未核实时留空，确认零成本填0"
          style="width: 100%"
        />
      </a-form-item>
      <a-row :gutter="16">
        <a-col v-for="field in amountFields" :key="field.key" :span="12"
          ><a-form-item :name="field.key" :label="field.label" :rules="[{required: true, message: '请填写金额，无费用时填0'}]"
            ><a-input-number
              v-model:value="expenseForm[field.key]"
              string-mode
              :min="0"
              :max="99999999999999"
              :precision="4"
              style="width: 100%" /></a-form-item
        ></a-col>
      </a-row>
      <a-form-item name="reason" label="登记说明" :rules="[{required: true, whitespace: true, message: '请填写登记说明'}]"
        ><a-textarea v-model:value="expenseForm.reason" :maxlength="500" :rows="3" show-count
      /></a-form-item>
    </a-form>
    <p class="scm-note">填写本次登记的实际金额，多笔记录会累加。商品成本含赠品及分摊价差，不重复填写整月合计。登记错误时作废原记录，再重新登记。</p>
  </a-modal>

  <a-modal
    :open="batchOpen"
    title="批量登记客户月成本与费用"
    :width="1080"
    :confirm-loading="batchSaving"
    :mask-closable="!batchSaving"
    :closable="!batchSaving"
    :cancel-button-props="{disabled: batchSaving}"
    ok-text="整批登记"
    @ok="saveBatch"
    @cancel="requestCloseBatch"
  >
    <a-form layout="vertical" :disabled="batchSaving">
      <a-row :gutter="16">
        <a-col :span="8"
          ><a-form-item label="月份"
            ><a-date-picker
              v-model:value="batchForm.month"
              picker="month"
              value-format="YYYY-MM"
              :allow-clear="false"
              style="width: 100%" /></a-form-item
        ></a-col>
        <a-col :span="16"
          ><a-form-item label="登记说明"
            ><a-input v-model:value="batchForm.reason" :maxlength="500" placeholder="例如：2026-10 月配送服务费"
          /></a-form-item>
        </a-col>
      </a-row>
    </a-form>
    <div class="batch-scroll">
      <div class="batch-grid batch-grid-head">
        <span>客户</span>
        <span>实际商品成本</span>
        <span v-for="field in amountFields" :key="field.key">{{ field.label }}</span>
        <span />
      </div>
      <div v-for="(row, index) in batchForm.rows" :key="index" class="batch-grid">
        <CustomerSelect v-model:value="row.customerId" :disabled="batchSaving" />
        <a-input-number
          v-model:value="row.goodsCostAmount"
          string-mode
          :min="0"
          :max="99999999999999"
          :precision="4"
          :disabled="batchSaving"
          placeholder="选填"
        />
        <a-input-number
          v-for="field in amountFields"
          :key="field.key"
          v-model:value="row[field.key]"
          string-mode
          :min="0"
          :max="99999999999999"
          :precision="4"
          :disabled="batchSaving"
        />
        <a-button type="text" danger :disabled="batchSaving || batchForm.rows.length <= 1" @click="removeBatchRow(index)"> 删除 </a-button>
      </div>
    </div>
    <a-button type="dashed" block :disabled="batchSaving || batchForm.rows.length >= 200" class="batch-add-row" @click="addBatchRow">
      添加一行
    </a-button>
    <p class="scm-note">整批使用同一月份与说明，每行一个客户；实际商品成本未核实时留空，费用无则填0。任一行不完整或同一客户重复时整批不会写入。</p>
  </a-modal>

  <a-modal v-model:open="expenseOpen" title="客户月成本与费用明细" :width="1100" :footer="null" @cancel="expenseRequestId++">
    <a-alert v-if="expenseError" type="error" show-icon :message="expenseError"
      ><template #action><a-button @click="loadExpenses">重试</a-button></template></a-alert
    >
    <a-table
      :columns="expenseColumns"
      :data-source="expenses"
      row-key="expenseId"
      size="small"
      :loading="expenseLoading"
      :scroll="{x: scmColumnsWidth(expenseColumns)}"
      :pagination="false"
    >
      <template #bodyCell="{column, record}">
        <span v-if="column.dataIndex === 'goodsCostAmount' || amountKeys.includes(column.dataIndex)" class="scm-money">{{
          moneyText(record[column.dataIndex])
        }}</span>
        <template v-else-if="column.dataIndex === 'voided'">{{ record.voided ? '已作废' : '有效' }}</template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ dateTimeText(record.createdAt) }}</template>
        <template v-else-if="column.dataIndex === 'action'"
          ><a-button v-if="canVoid && !record.voided" type="link" danger size="small" @click="openVoid(record)">作废</a-button></template
        >
        <template v-else>{{ record[column.dataIndex] || '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="expensePage" :page-size="20" :total="expenseTotal" @change="loadExpenses" />
    </div>
  </a-modal>
  <a-modal
    v-model:open="voidOpen"
    title="作废成本费用"
    :confirm-loading="voidSaving"
    :mask-closable="!voidSaving"
    :closable="!voidSaving"
    :cancel-button-props="{disabled: voidSaving}"
    @ok="voidExpense"
  >
    <p class="scm-note">作废后，该笔费用不再计入客户月利润，原记录与作废原因保留。</p>
    <a-form ref="voidFormRef" :model="voidForm" layout="vertical"
      ><a-form-item name="reason" label="作废原因" :rules="[{required: true, whitespace: true, message: '请填写作废原因'}]"
        ><a-textarea v-model:value="voidForm.reason" :disabled="voidSaving" :maxlength="500" :rows="3" /></a-form-item
    ></a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, onMounted, onBeforeUnmount, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import Decimal from 'decimal.js';
import {message, type FormInstance, type TableColumnsType} from 'ant-design-vue';
import {financeProfitApi, type FinanceProfitQuery, type CustomerMonthlyProfitRow} from '/@/api/business/scm/finance-profit-api';
import {financeApi} from '/@/api/business/scm/finance-api';
import type {FinanceId, CustomerExpense, CustomerExpenseBatchRow} from '../finance/finance-types';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import {SCM_FINANCE_PERMISSION as FINANCE_PERM} from '/@/constants/business/scm/finance-const';
import {hasPermission} from '../common/scm-permission';
import {scmColumnsWidth} from '../common/scm-column';
import {moneyText, dateTimeText} from '../finance/finance-form-model';
import {reportError} from './report-errors';
import {notifyFormInvalid} from '../common/scm-form-feedback';
import {createUnsavedGuard} from '../common/scm-unsaved-guard';

const filters = reactive({month: dayjs().format('YYYY-MM'), customerId: undefined as FinanceId | undefined});
const pageNum = ref(1),
  pageSize = ref(20),
  total = ref(0),
  loading = ref(false),
  exporting = ref(false),
  error = ref('');
const rows = ref<CustomerMonthlyProfitRow[]>([]),
  summary = ref<CustomerMonthlyProfitRow>(),
  applied = ref<FinanceProfitQuery>();
let requestId = 0;
const cards = computed(() => [
  {label: '销售收入', value: summary.value?.revenueAmount},
  {label: '销售毛利', value: summary.value?.grossProfit},
  {label: '费用合计', value: summary.value?.expenseAmount},
  {label: '扣费后利润', value: summary.value?.netProfit},
]);
const canAdd = computed(() => hasPermission(FINANCE_PERM.EXPENSE_ADD));
const canQueryExpenses = computed(() => hasPermission(FINANCE_PERM.EXPENSE_QUERY));
const canVoid = computed(() => hasPermission(FINANCE_PERM.EXPENSE_VOID));
type AmountKey = 'taxAmount' | 'serviceAmount' | 'freightAmount' | 'commissionAmount';
const amountFields: {key: AmountKey; label: string}[] = [
  {key: 'taxAmount', label: '税费'},
  {key: 'serviceAmount', label: '服务费'},
  {key: 'freightAmount', label: '运费'},
  {key: 'commissionAmount', label: '扣点金额'},
];
const amountKeys = amountFields.map((field) => field.key);
const columns = ref<TableColumnsType<CustomerMonthlyProfitRow>>([
  {title: '客户名称', dataIndex: 'dimensionName', width: 200},
  ...[
    {key: 'revenueAmount', label: '销售收入'},
    {key: 'salesCostAmount', label: '实际商品成本'},
    {key: 'grossProfit', label: '销售毛利'},
    ...amountFields,
    {key: 'expenseAmount', label: '费用合计'},
    {key: 'netProfit', label: '扣费后利润'},
    {key: 'statementAmount', label: '对账金额'},
    {key: 'paidAmount', label: '实付金额'},
    {key: 'unpaidAmount', label: '未付金额'},
    {key: 'invoiceAmount', label: '开票金额'},
    {key: 'postingAmount', label: '过账金额'},
    {key: 'postingTaxAmount', label: '过账税金'},
    {key: 'actualGoodsAmount', label: '实际货款'},
    {key: 'invoiceTaxAmount', label: '1%专票税费'},
    {key: 'serviceFeeAmount', label: '6%服务费'},
    {key: 'statementProfit', label: '利润'},
  ].map((field) => ({title: field.label, dataIndex: field.key, width: 144})),
  {title: '成本状态', dataIndex: 'costMissingCount', width: 110},
  {title: '操作', dataIndex: 'action', width: 100, fixed: 'right', align: 'center'},
]);
function payload(): FinanceProfitQuery {
  const month = dayjs(filters.month + '-01');
  return {
    startDate: month.format('YYYY-MM-DD'),
    endDate: month.endOf('month').format('YYYY-MM-DD'),
    dimension: 'CUSTOMER',
    customerId: filters.customerId,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  };
}
async function load() {
  const id = ++requestId;
  const query = payload();
  loading.value = true;
  error.value = '';
  try {
    const [page, totals] = await Promise.all([
      financeProfitApi.monthlyQuery(query, {suppressGlobalErrorMessage: true}),
      financeProfitApi.monthlySummary(query, {suppressGlobalErrorMessage: true}),
    ]);
    if (id !== requestId) return;
    rows.value = page.data.list;
    total.value = page.data.total;
    summary.value = totals.data;
    applied.value = query;
  } catch (e) {
    if (id === requestId) {
      error.value = reportError(e);
      rows.value = [];
      summary.value = undefined;
      total.value = 0;
      applied.value = undefined;
    }
  } finally {
    if (id === requestId) loading.value = false;
  }
}
function search() {
  pageNum.value = 1;
  void load();
}
function reset() {
  filters.month = dayjs().format('YYYY-MM');
  filters.customerId = undefined;
  search();
}
async function exportRows() {
  const query = applied.value;
  if (!query || exporting.value) return;
  const exportColumns = (columns.value as unknown as Array<{dataIndex?: unknown}>)
    .map((column) => String(column.dataIndex ?? ''))
    .filter((key) => key && key !== 'action');
  if (!exportColumns.length) {
    message.warning('请至少选择一个数据列');
    return;
  }
  exporting.value = true;
  const payload: FinanceProfitQuery & {exportColumns: string[]} = {
    startDate: query.startDate,
    endDate: query.endDate,
    dimension: query.dimension,
    customerId: query.customerId,
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    exportColumns,
  };
  try {
    await financeProfitApi.monthlyExport(payload);
  } catch {
    /* 全局提示 */
  } finally {
    exporting.value = false;
  }
}
const expenseForm = reactive({
  customerId: undefined as FinanceId | undefined,
  month: filters.month,
  goodsCostAmount: null as string | null,
  taxAmount: '0.0000',
  serviceAmount: '0.0000',
  freightAmount: '0.0000',
  commissionAmount: '0.0000',
  reason: '',
});
const addOpen = ref(false),
  saving = ref(false),
  addFormRef = ref<FormInstance>();
const addUnsaved = createUnsavedGuard(() => expenseForm);
function openAdd() {
  addUnsaved.clear();
  Object.assign(expenseForm, {
    customerId: filters.customerId,
    month: filters.month,
    goodsCostAmount: null as string | null,
    taxAmount: '0.0000',
    serviceAmount: '0.0000',
    freightAmount: '0.0000',
    commissionAmount: '0.0000',
    reason: '',
  });
  addFormRef.value?.clearValidate();
  addOpen.value = true;
  addUnsaved.capture();
}

/** 单笔登记关闭前确认未保存内容。 */
async function requestCloseAdd() {
  if (saving.value) return;
  if (await addUnsaved.confirmDiscard()) addOpen.value = false;
}
async function saveExpense() {
  if (saving.value) return;
  try {
    await addFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, addFormRef.value);
    return;
  }
  if (expenseForm.customerId == null) return;
  if (expenseForm.goodsCostAmount == null && !amountFields.some((field) => Number(expenseForm[field.key]) > 0)) {
    message.warning('请登记实际商品成本，或至少填写一项费用');
    return;
  }
  saving.value = true;
  try {
    await financeApi.customerExpenseAdd({
      customerId: expenseForm.customerId,
      expenseMonth: expenseForm.month + '-01',
      goodsCostAmount: expenseForm.goodsCostAmount == null ? null : new Decimal(expenseForm.goodsCostAmount).toFixed(4),
      taxAmount: new Decimal(expenseForm.taxAmount).toFixed(4),
      serviceAmount: new Decimal(expenseForm.serviceAmount).toFixed(4),
      freightAmount: new Decimal(expenseForm.freightAmount).toFixed(4),
      commissionAmount: new Decimal(expenseForm.commissionAmount).toFixed(4),
      reason: expenseForm.reason.trim(),
    });
    message.success('费用已登记');
    addOpen.value = false;
    await load();
  } catch {
    /* 全局提示 */
  } finally {
    saving.value = false;
  }
}
type BatchRow = {
  customerId?: FinanceId;
  goodsCostAmount: string | null;
  taxAmount: string;
  serviceAmount: string;
  freightAmount: string;
  commissionAmount: string;
};
const batchForm = reactive({month: filters.month, reason: '', rows: [] as BatchRow[]});
const batchOpen = ref(false),
  batchSaving = ref(false);
const batchUnsaved = createUnsavedGuard(() => batchForm);
function emptyBatchRow(customerId?: FinanceId): BatchRow {
  return {
    customerId,
    goodsCostAmount: null,
    taxAmount: '0.0000',
    serviceAmount: '0.0000',
    freightAmount: '0.0000',
    commissionAmount: '0.0000',
  };
}
function openBatch() {
  batchUnsaved.clear();
  batchForm.month = filters.month;
  batchForm.reason = '';
  batchForm.rows = [emptyBatchRow(filters.customerId), emptyBatchRow(), emptyBatchRow()];
  batchOpen.value = true;
  batchUnsaved.capture();
}

/** 批量登记关闭前确认未保存内容。 */
async function requestCloseBatch() {
  if (batchSaving.value) return;
  if (await batchUnsaved.confirmDiscard()) batchOpen.value = false;
}
function addBatchRow() {
  if (batchForm.rows.length < 200) batchForm.rows.push(emptyBatchRow());
}
function removeBatchRow(index: number) {
  if (batchForm.rows.length > 1) batchForm.rows.splice(index, 1);
}
function rowIsBlank(row: BatchRow) {
  return row.customerId == null && row.goodsCostAmount == null && amountFields.every((field) => !Number(row[field.key] || 0));
}
function fixedAmount(value: string | null | undefined) {
  return new Decimal(value ?? 0).toFixed(4);
}
async function saveBatch() {
  if (batchSaving.value) return;
  if (!batchForm.month) {
    message.warning('请选择费用月份');
    return;
  }
  if (!batchForm.reason.trim()) {
    message.warning('请填写登记说明');
    return;
  }
  const filled = batchForm.rows.filter((row) => !rowIsBlank(row));
  if (!filled.length) {
    message.warning('请至少填写一个客户');
    return;
  }
  const customerIds = new Set<string>();
  for (const [index, row] of filled.entries()) {
    if (row.customerId == null) {
      message.warning(`第 ${index + 1} 行还未选择客户`);
      return;
    }
    const key = String(row.customerId);
    if (customerIds.has(key)) {
      message.warning(`第 ${index + 1} 行的客户重复，请合并后再登记`);
      return;
    }
    customerIds.add(key);
    if (row.goodsCostAmount == null && amountFields.every((field) => !Number(row[field.key] || 0))) {
      message.warning(`第 ${index + 1} 行请填写实际商品成本，或至少填写一项费用`);
      return;
    }
  }
  const rows: CustomerExpenseBatchRow[] = filled.map((row) => ({
    customerId: row.customerId as FinanceId,
    goodsCostAmount: row.goodsCostAmount == null ? null : fixedAmount(row.goodsCostAmount),
    taxAmount: fixedAmount(row.taxAmount),
    serviceAmount: fixedAmount(row.serviceAmount),
    freightAmount: fixedAmount(row.freightAmount),
    commissionAmount: fixedAmount(row.commissionAmount),
  }));
  batchSaving.value = true;
  try {
    const result = await financeApi.customerExpenseBatchAdd({
      expenseMonth: batchForm.month + '-01',
      reason: batchForm.reason.trim(),
      rows,
    });
    message.success(`已登记 ${result.data.length} 个客户`);
    batchOpen.value = false;
    await load();
  } catch {
    /* 全局提示 */
  } finally {
    batchSaving.value = false;
  }
}

const expenseOpen = ref(false),
  expenseLoading = ref(false),
  expenseError = ref(''),
  expenses = ref<CustomerExpense[]>([]),
  expenseTotal = ref(0),
  expensePage = ref(1);
let expenseRequestId = 0;
const expenseQuery = reactive({expenseMonth: '', customerId: undefined as FinanceId | undefined});
const expenseColumns: TableColumnsType<CustomerExpense> = [
  {title: '登记单号', dataIndex: 'expenseNo', width: 190},
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  {title: '实际商品成本', dataIndex: 'goodsCostAmount', width: 144},
  ...amountFields.map((field) => ({title: field.label, dataIndex: field.key, width: 132, align: 'right' as const})),
  {title: '状态', dataIndex: 'voided', width: 80},
  {title: '说明', dataIndex: 'reason', width: 180},
  {title: '登记时间', dataIndex: 'createdAt', width: 180},
  {title: '作废原因', dataIndex: 'voidReason', width: 180},
  {title: '操作', dataIndex: 'action', width: 80, fixed: 'right', align: 'center'},
];
function openExpenses(customerId?: FinanceId | null) {
  expenseQuery.expenseMonth = applied.value?.startDate || filters.month + '-01';
  expenseQuery.customerId = customerId ?? filters.customerId;
  expensePage.value = 1;
  expenses.value = [];
  expenseTotal.value = 0;
  expenseOpen.value = true;
  void loadExpenses();
}
async function loadExpenses() {
  const id = ++expenseRequestId;
  expenseLoading.value = true;
  expenseError.value = '';
  try {
    const response = await financeApi.customerExpenseQuery(
      {...expenseQuery, pageNum: expensePage.value, pageSize: 20},
      {suppressGlobalErrorMessage: true}
    );
    if (id !== expenseRequestId) return;
    expenses.value = response.data.list;
    expenseTotal.value = response.data.total;
  } catch (e) {
    if (id === expenseRequestId) {
      expenseError.value = reportError(e);
      expenses.value = [];
      expenseTotal.value = 0;
    }
  } finally {
    if (id === expenseRequestId) expenseLoading.value = false;
  }
}
const voidOpen = ref(false),
  voidSaving = ref(false),
  voidFormRef = ref<FormInstance>();
const voidForm = reactive({expenseId: undefined as FinanceId | undefined, reason: ''});
function openVoid(row: CustomerExpense) {
  voidForm.expenseId = row.expenseId;
  voidForm.reason = '';
  voidFormRef.value?.clearValidate();
  voidOpen.value = true;
}
async function voidExpense() {
  if (voidSaving.value || voidForm.expenseId == null) return;
  try {
    await voidFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, voidFormRef.value);
    return;
  }
  voidSaving.value = true;
  try {
    await financeApi.customerExpenseVoid({expenseId: voidForm.expenseId, reason: voidForm.reason.trim()});
    message.success('费用已作废');
    voidOpen.value = false;
    await Promise.all([loadExpenses(), load()]);
  } catch {
    /* 全局提示 */
  } finally {
    voidSaving.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => {
  requestId++;
  expenseRequestId++;
});
</script>
<style scoped>
.monthly-summary {
  margin: 12px 0;
}
.monthly-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 8px;
}
.batch-scroll {
  max-height: 46vh;
  overflow: auto;
  padding-right: 4px;
}
.batch-grid {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) repeat(5, minmax(110px, 1fr)) 56px;
  gap: 8px;
  align-items: center;
  min-width: 900px;
  padding: 6px 0;
  border-bottom: 1px solid var(--scm-border, #f0f0f0);
}
.batch-grid :deep(.ant-input-number),
.batch-grid :deep(.ant-select) {
  width: 100%;
}
.batch-grid-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--scm-surface, #fff);
  color: var(--scm-text-secondary, #666);
  font-size: 12px;
  font-weight: 500;
}
.batch-add-row {
  margin-top: 12px;
}
</style>
