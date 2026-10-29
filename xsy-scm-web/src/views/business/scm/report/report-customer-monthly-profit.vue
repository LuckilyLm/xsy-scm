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
  <p class="scm-note">
    费用按客户和月份登记实际金额，不参与应收核销。客户月利润按完整订单与仓库范围统计；未归属客户的采购价差单列，不自动分摊到客户。
  </p>
  <a-alert v-if="error" type="error" show-icon :message="error"
    ><template #action><a-button @click="load">重试</a-button></template></a-alert
  >
  <a-alert
    v-if="(summary?.costMissingCount ?? 0) > 0"
    type="warning"
    show-icon
    message="部分销售成本尚未确认，相关客户的毛利及扣费后利润暂不完整，请核对采购成本。"
  />
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
        <a-button v-if="canAdd" type="primary" @click="openAdd">登记费用</a-button>
        <a-button v-if="canQueryExpenses" @click="openExpenses()">费用明细</a-button>
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
        <template v-else-if="column.dataIndex === 'costMissingCount'">{{ record.costMissingCount || 0 }}</template>
        <template v-else-if="column.dataIndex === 'action'"
          ><a-button v-if="canQueryExpenses && record.dimensionId != null" type="link" size="small" @click="openExpenses(record.dimensionId)"
            >费用明细</a-button
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
    v-model:open="addOpen"
    title="登记客户月费用"
    :confirm-loading="saving"
    :mask-closable="!saving"
    :closable="!saving"
    :cancel-button-props="{disabled: saving}"
    @ok="saveExpense"
  >
    <a-form ref="addFormRef" :model="expenseForm" layout="vertical" :disabled="saving">
      <a-form-item name="customerId" label="客户" :rules="[{required: true, message: '请选择客户'}]"
        ><CustomerSelect v-model:value="expenseForm.customerId"
      /></a-form-item>
      <a-form-item name="month" label="费用月份" :rules="[{required: true, message: '请选择费用月份'}]"
        ><a-date-picker v-model:value="expenseForm.month" picker="month" value-format="YYYY-MM" :allow-clear="false"
      /></a-form-item>
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
      <a-form-item name="reason" label="费用说明" :rules="[{required: true, whitespace: true, message: '请填写费用说明'}]"
        ><a-textarea v-model:value="expenseForm.reason" :maxlength="500" :rows="3" show-count
      /></a-form-item>
    </a-form>
    <p class="scm-note">填写实际费用金额。登记错误时请作废原记录，再重新登记。</p>
  </a-modal>

  <a-modal v-model:open="expenseOpen" title="客户月费用明细" :width="1100" :footer="null" @cancel="expenseRequestId++">
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
        <span v-if="amountKeys.includes(column.dataIndex)" class="scm-money">{{ moneyText(record[column.dataIndex]) }}</span>
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
    title="作废费用"
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
import type {FinanceId, CustomerExpense} from '../finance/finance-types';
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
    {key: 'salesCostAmount', label: '商品销售成本'},
    {key: 'giftCostAmount', label: '赠品成本'},
    {key: 'grossProfit', label: '销售毛利'},
    ...amountFields,
    {key: 'expenseAmount', label: '费用合计'},
    {key: 'netProfit', label: '扣费后利润'},
  ].map((field) => ({title: field.label, dataIndex: field.key, width: 144, align: 'right' as const})),
  {title: '待确认成本数', dataIndex: 'costMissingCount', width: 110, align: 'right'},
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
  if (!applied.value || exporting.value) return;
  const exportColumns = columns.value.map((column) => String('dataIndex' in column ? column.dataIndex : '')).filter((key) => key && key !== 'action');
  if (!exportColumns.length) {
    message.warning('请至少选择一个数据列');
    return;
  }
  exporting.value = true;
  try {
    await financeProfitApi.monthlyExport({...applied.value, exportColumns});
  } catch {
    /* 全局提示 */
  } finally {
    exporting.value = false;
  }
}
const expenseForm = reactive({
  customerId: undefined as FinanceId | undefined,
  month: filters.month,
  taxAmount: '0.0000',
  serviceAmount: '0.0000',
  freightAmount: '0.0000',
  commissionAmount: '0.0000',
  reason: '',
});
const addOpen = ref(false),
  saving = ref(false),
  addFormRef = ref<FormInstance>();
function openAdd() {
  Object.assign(expenseForm, {
    customerId: filters.customerId,
    month: filters.month,
    taxAmount: '0.0000',
    serviceAmount: '0.0000',
    freightAmount: '0.0000',
    commissionAmount: '0.0000',
    reason: '',
  });
  addFormRef.value?.clearValidate();
  addOpen.value = true;
}
async function saveExpense() {
  if (saving.value) return;
  try {
    await addFormRef.value?.validate();
  } catch {
    return;
  }
  if (expenseForm.customerId == null) return;
  if (!amountFields.some((field) => Number(expenseForm[field.key]) > 0)) {
    message.warning('费用合计必须大于0');
    return;
  }
  saving.value = true;
  try {
    await financeApi.customerExpenseAdd({
      customerId: expenseForm.customerId,
      expenseMonth: expenseForm.month + '-01',
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
const expenseOpen = ref(false),
  expenseLoading = ref(false),
  expenseError = ref(''),
  expenses = ref<CustomerExpense[]>([]),
  expenseTotal = ref(0),
  expensePage = ref(1);
let expenseRequestId = 0;
const expenseQuery = reactive({expenseMonth: '', customerId: undefined as FinanceId | undefined});
const expenseColumns: TableColumnsType<CustomerExpense> = [
  {title: '费用单号', dataIndex: 'expenseNo', width: 190},
  {title: '客户名称', dataIndex: 'customerName', width: 180},
  ...amountFields.map((field) => ({title: field.label, dataIndex: field.key, width: 132, align: 'right' as const})),
  {title: '状态', dataIndex: 'voided', width: 80, align: 'center'},
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
  } catch {
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
</style>
