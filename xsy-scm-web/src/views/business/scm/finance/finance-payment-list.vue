<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="付款日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期" />
      </a-form-item>
      <a-form-item label="往来方类型">
        <a-select v-model:value="query.counterpartyType" allow-clear :options="partyTypeOptions" placeholder="全部" style="width: 140px" />
      </a-form-item>
      <a-form-item label="往来方"
        ><a-input v-model:value="query.counterpartyName" allow-clear placeholder="按名称搜索" @press-enter="onSearch"
      /></a-form-item>
      <a-form-item label="方式"
        ><a-select v-model:value="query.method" allow-clear :options="methodOptions" placeholder="全部" style="width: 140px"
      /></a-form-item>
      <a-form-item label="来源"
        ><a-select v-model:value="query.sourceType" allow-clear :options="sourceOptions" placeholder="全部" style="width: 150px"
      /></a-form-item>
      <a-form-item label="方向"
        ><a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width: 120px"
      /></a-form-item>
      <a-form-item label="待核销"
        ><a-select v-model:value="query.pendingOnly" allow-clear :options="pendingOptions" placeholder="全部" style="width: 120px"
      /></a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.PAYMENT_QUERY" @click="onSearch">查询</a-button>
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
      <div class="smart-table-operate-block">付款明细</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.PAYMENT_ADD" type="primary" @click="openAdd">登记付款</a-button>
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_PAYMENT" :refresh="queryData" />
      </div>
    </a-row>
    <div class="finance-mobile-balance-list">
      <div v-for="record in page.tableData.value" :key="record.paymentId" class="finance-mobile-balance-row">
        <div class="mobile-balance-heading">
          <strong>{{ record.paymentNo }}</strong
          ><a-button type="link" @click="showDetail(record)">明细</a-button>
        </div>
        <div class="mobile-balance-party">
          {{ record.counterpartyName }} · {{ record.counterpartyType === 'CUSTOMER' ? '客户' : '供应商' }} · {{ entryTypeText(record.entryType) }}
        </div>
        <div class="mobile-balance-values">
          <span
            >待核销 <strong>{{ moneyText(record.pendingWriteOffAmount) }}</strong></span
          ><span>有效 {{ moneyText(record.effectiveAmount) }}</span>
        </div>
      </div>
    </div>
    <a-table
      id="scm-finance-payment-table"
      class="finance-table"
      size="small"
      :data-source="page.tableData.value"
      :columns="columns"
      row-key="paymentId"
      :loading="page.loading.value"
      :pagination="false"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column, text}">
        <template v-if="column.dataIndex === 'entryType'">
          <ScmStatusTag :color="SCM_FINANCE_ENTRY_COLOR[text]" :label="entryTypeText(text)" />
        </template>
        <template v-else-if="column.dataIndex === 'counterpartyName'">{{ record.counterpartyName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'counterpartyType'">{{ counterpartyTypeText(record.counterpartyType) }}</template>
        <template v-else-if="column.dataIndex === 'method'">{{ paymentMethodText(text) }}</template>
        <template v-else-if="column.dataIndex === 'sourceType'">
          <span v-if="text === 'ORDER_REFUND'">退款</span>
          <span v-else class="scm-cell-hint">—</span>
        </template>
        <template v-else-if="['amount', 'effectiveAmount', 'usedAmount', 'pendingWriteOffAmount'].includes(column.dataIndex)">
          <span :class="moneyClass(text, ['usedAmount', 'pendingWriteOffAmount'].includes(column.dataIndex) ? 'balance' : 'fact')">{{
            moneyText(text)
          }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'paidAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="showDetail(record)">明细</a-button>
            <a-button
              v-if="record.entryType === 'NORMAL'"
              type="link"
              size="small"
              danger
              v-privilege="PERM.PAYMENT_REVERSE"
              :disabled="record.usedAmount !== '0.0000'"
              @click="openReverse(record)"
              >撤销付款</a-button
            >
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        show-quick-jumper
        v-model:current="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="page.total.value"
        @change="queryData"
        :show-total="(count: number) => `共${count}条`"
      />
    </div>
  </a-card>

  <FinanceDetailDrawer
    v-model:open="detailOpen"
    kind="PAYMENT"
    :loading="detailLoading"
    :detail="detailData"
    :error="detailError"
    @retry="reloadDetail"
  />

  <a-modal v-model:open="addOpen" title="登记付款" :confirm-loading="addSaving" @ok="submitAdd">
    <a-alert v-if="addError" class="form-error" type="error" show-icon :message="addError" />
    <a-form ref="addFormRef" :model="addForm" :rules="addRules" layout="vertical">
      <a-form-item name="counterpartyType">
        <template #label>
          往来方类型
          <ScmFieldHelp label="往来方类型" text="客户付款仅可关联已完成的退款" />
        </template>
        <a-select v-model:value="addForm.counterpartyType" :options="partyTypeOptions" @change="onPartyTypeChange" />
      </a-form-item>
      <a-form-item v-if="addForm.counterpartyType === 'SUPPLIER'" label="供应商" name="supplierId">
        <SupplierSelect v-model:value="addForm.supplierId" placeholder="搜索供应商名称" width="100%" />
      </a-form-item>
      <a-form-item v-if="addForm.counterpartyType === 'CUSTOMER'" label="已完成退款来源" name="selectedRefund">
        <a-input
          :value="
            addForm.selectedRefund
              ? `${addForm.selectedRefund.refundNo} · ${addForm.selectedRefund.customerName} · ${moneyText(addForm.selectedRefund.refundAmount)}`
              : ''
          "
          readonly
          placeholder="选择已完成退款"
        >
          <template #addonAfter><a-button type="link" @click="refundPickerOpen = true">选择退款</a-button></template>
        </a-input>
      </a-form-item>
      <a-form-item label="付款金额" name="amount">
        <a-input-number
          v-if="addForm.counterpartyType === 'SUPPLIER'"
          v-model:value="addForm.amount"
          string-mode
          :min="0"
          :precision="4"
          :max="99999999999999"
          style="width: 100%"
        />
        <a-input
          v-else
          :value="addForm.selectedRefund ? moneyText(addForm.selectedRefund.refundAmount) : ''"
          disabled
          placeholder="选择退款来源后自动带入"
        />
      </a-form-item>
      <a-form-item label="付款方式" name="method"
        ><a-select v-model:value="addForm.method" :options="addMethodOptions" placeholder="选择方式"
      /></a-form-item>
      <a-form-item label="付款时间" name="paidAt"
        ><a-date-picker v-model:value="addForm.paidAt" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%"
      /></a-form-item>
      <a-form-item label="资金凭据号"><a-input v-model:value="addForm.externalReference" :maxlength="128" /></a-form-item>
      <a-form-item label="备注"><a-textarea v-model:value="addForm.remark" :maxlength="500" :rows="2" show-count /></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="reverseOpen" title="撤销付款" :confirm-loading="reverseSaving" @ok="submitReverse">
    <a-alert
      v-if="reverseRow"
      type="warning"
      show-icon
      :message="`将登记一笔等额冲回记录，金额 ${moneyText(reverseRow.amount)}。原付款记录会保留。`"
    />
    <a-alert v-if="reverseError" class="form-error" type="error" show-icon :message="reverseError" />
    <a-form layout="vertical"
      ><a-form-item label="撤销原因" required><a-textarea v-model:value="reverseReason" :maxlength="500" :rows="3" show-count /></a-form-item
    ></a-form>
  </a-modal>

  <FinanceRefundPicker v-model:open="refundPickerOpen" @select="selectRefund" />
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {
  SCM_FINANCE_COUNTERPARTY_TYPE_ENUM,
  SCM_FINANCE_ENTRY_COLOR,
  SCM_FINANCE_ENTRY_TYPE_ENUM,
  SCM_FINANCE_CUSTOMER_REFUND_METHOD_ENUM,
  SCM_FINANCE_PAYMENT_METHOD_ENUM,
  SCM_FINANCE_PERMISSION as PERM,
} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import FinanceDetailDrawer from './finance-detail-drawer.vue';
import FinanceRefundPicker from './finance-refund-picker.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import type {ScmId} from '/@/types/business/scm/customer';
import {
  dateTimeText,
  entryTypeText,
  initialFinanceDateRange,
  isValidPositiveAmount,
  moneyClass,
  moneyText,
  nowDateTimeValue,
  paymentMethodText,
  trimOptional,
} from './finance-form-model';
import type {FinancePayment, FinancePaymentAddForm, FinancePaymentDetail, FinanceRefundOption, PaymentQuery} from './finance-types';
import {useFinancePage} from './use-finance-page';
import {useFinanceDetail} from './use-finance-detail';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const query = reactive<PaymentQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinancePayment, PaymentQuery>(financeApi.paymentQuery, financeApi.paymentExport);
const {
  open: detailOpen,
  loading: detailLoading,
  data: detailData,
  error: detailError,
  show: openDetail,
  load: reloadDetail,
} = useFinanceDetail<FinancePaymentDetail>((id) => financeApi.paymentDetail(id, {suppressGlobalErrorMessage: true}));
const addOpen = ref(false),
  addSaving = ref(false),
  addError = ref(''),
  refundPickerOpen = ref(false);
const reverseOpen = ref(false),
  reverseSaving = ref(false),
  reverseError = ref(''),
  reverseReason = ref(''),
  reverseRow = ref<FinancePayment | null>(null);
/** 表单模型必须保持同一个对象；每次渲染新建对象会让校验读到旧值。付款对象与退款来源也放在这个模型里。 */
const addForm = reactive<
  Omit<FinancePaymentAddForm, 'counterpartyId'> & {
    counterpartyType: 'CUSTOMER' | 'SUPPLIER';
    supplierId?: ScmId;
    selectedRefund: FinanceRefundOption | null;
  }
>({
  counterpartyType: 'SUPPLIER',
  amount: '',
  method: 'BANK_TRANSFER',
  paidAt: nowDateTimeValue(),
  externalReference: '',
  sourceType: undefined,
  sourceId: undefined,
  remark: '',
  supplierId: undefined,
  selectedRefund: null,
});
const addFormRef = ref<FormInstance>();
/** 必填项逐项校验，错误显示在输入框下方。 */
const addRules = {
  counterpartyType: [{required: true, message: '请选择往来方类型', trigger: 'change'}],
  supplierId: [{required: true, message: '请选择供应商', trigger: 'change'}],
  selectedRefund: [
    {
      validator: () => (addForm.selectedRefund ? Promise.resolve() : Promise.reject(new Error('请选择已完成退款'))),
      trigger: 'change',
    },
  ],
  amount: [
    {
      // 客户付款的金额由退款来源带入，不走这里校验
      validator: () =>
        addForm.counterpartyType !== 'SUPPLIER' || isValidPositiveAmount(addForm.amount)
          ? Promise.resolve()
          : Promise.reject(new Error('请输入大于 0 的金额，最多 4 位小数')),
      trigger: 'blur',
    },
  ],
  method: [{required: true, message: '请选择付款方式', trigger: 'change'}],
  paidAt: [{required: true, message: '请选择付款时间', trigger: 'change'}],
};
// 查询跨客户和供应商，两组方式都要给
const methodOptions = Object.values({...SCM_FINANCE_PAYMENT_METHOD_ENUM, ...SCM_FINANCE_CUSTOMER_REFUND_METHOD_ENUM}).map((item) => ({
  label: item.desc,
  value: item.value,
}));
// 新增表单按往来方类型给选项，与后端约束一致
const addMethodOptions = computed(() =>
  Object.values(addForm.counterpartyType === 'SUPPLIER' ? SCM_FINANCE_PAYMENT_METHOD_ENUM : SCM_FINANCE_CUSTOMER_REFUND_METHOD_ENUM).map((item) => ({
    label: item.desc,
    value: item.value,
  }))
);
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const partyTypeOptions = Object.values(SCM_FINANCE_COUNTERPARTY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const sourceOptions = [{label: '退款', value: 'ORDER_REFUND'}];
const pendingOptions = [
  {label: '仅待核销', value: true},
  {label: '全部', value: false},
];
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

/** 往来方类型：客户或供应商。 */
function counterpartyTypeText(value?: string | null): string {
  return value ? SCM_FINANCE_COUNTERPARTY_TYPE_ENUM[value]?.desc ?? value : '—';
}

const columns = ref<TableColumnsType<FinancePayment>>([
  {title: '付款单号', dataIndex: 'paymentNo', width: 170, ellipsis: true},
  {title: '待核销', dataIndex: 'pendingWriteOffAmount', width: 148},
  {title: '往来方', dataIndex: 'counterpartyName', width: 190},
  {title: '往来方类型', dataIndex: 'counterpartyType', align: 'center', width: 112},
  {title: '方向', dataIndex: 'entryType', align: 'center', width: 80},
  {title: '金额', dataIndex: 'amount', width: 136},
  {title: '有效金额', dataIndex: 'effectiveAmount', width: 148},
  {title: '已核销', dataIndex: 'usedAmount', width: 148},
  {title: '方式', dataIndex: 'method', width: 105, align: 'center'},
  {title: '来源', dataIndex: 'sourceType', align: 'center', width: 95},
  {title: '付款时点', dataIndex: 'paidAt', width: 180},
  {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'center', width: 140},
]);
useFinanceMobileActionColumn(
  (compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
  },
  () => columns.value.length
);

async function queryData() {
  await page.queryData(query);
}
function onSearch() {
  query.pageNum = 1;
  queryData();
}
function resetQuery() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 20,
    ...initialFinanceDateRange(),
    counterpartyType: undefined,
    counterpartyId: undefined,
    counterpartyName: undefined,
    method: undefined,
    sourceType: undefined,
    entryType: undefined,
    pendingOnly: undefined,
  });
  queryData();
}
async function exportData() {
  await page.exportData(query);
}

function showDetail(row: FinancePayment) {
  return openDetail(row.paymentId);
}

function openAdd() {
  addError.value = '';
  Object.assign(addForm, {
    counterpartyType: 'SUPPLIER',
    amount: '',
    method: 'BANK_TRANSFER',
    paidAt: nowDateTimeValue(),
    externalReference: '',
    sourceType: undefined,
    sourceId: undefined,
    remark: '',
  });
  addForm.selectedRefund = null;
  addForm.supplierId = undefined;
  addOpen.value = true;
}

function onPartyTypeChange() {
  addForm.sourceType = undefined;
  addForm.sourceId = undefined;
  addForm.amount = '';
  addForm.selectedRefund = null;
  addForm.supplierId = undefined;
}

function selectRefund(option: FinanceRefundOption) {
  addForm.selectedRefund = option;
  addForm.amount = option.refundAmount;
}

async function submitAdd() {
  // 必填项由表单校验，顶部 alert 只显示服务端错误
  try {
    await addFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, addFormRef.value);
    return;
  }
  const counterpartyId = addForm.counterpartyType === 'CUSTOMER' ? addForm.selectedRefund?.customerId : addForm.supplierId;
  if (counterpartyId === undefined) {
    addError.value = '请选择付款对象';
    return;
  }
  const amount = addForm.counterpartyType === 'CUSTOMER' ? addForm.selectedRefund?.refundAmount : addForm.amount;
  addSaving.value = true;
  addError.value = '';
  try {
    const payload: FinancePaymentAddForm = {
      counterpartyType: addForm.counterpartyType,
      counterpartyId,
      amount: amount!,
      method: addForm.method,
      paidAt: addForm.paidAt,
      externalReference: trimOptional(addForm.externalReference),
      remark: trimOptional(addForm.remark),
      sourceType: addForm.counterpartyType === 'CUSTOMER' ? 'ORDER_REFUND' : null,
      sourceId: addForm.counterpartyType === 'CUSTOMER' ? addForm.selectedRefund?.refundId : null,
    };
    await financeApi.paymentAdd(payload);
    message.success('付款已登记');
    addOpen.value = false;
    await queryData();
  } catch {
    // 登记失败由全局 toast 提示；addError 只用于本地校验
  } finally {
    addSaving.value = false;
  }
}

function openReverse(row: FinancePayment) {
  reverseRow.value = row;
  reverseReason.value = '';
  reverseOpen.value = true;
}
async function submitReverse() {
  if (!reverseRow.value || !reverseReason.value.trim()) {
    reverseError.value = '请填写反向原因。';
    return;
  }
  reverseSaving.value = true;
  reverseError.value = '';
  try {
    await financeApi.paymentReverse({paymentId: reverseRow.value.paymentId, reason: reverseReason.value.trim()});
    message.success('反向付款已追加');
    reverseOpen.value = false;
    await queryData();
  } catch {
    // 反向失败由全局 toast 提示；reverseError 只用于本地校验
  } finally {
    reverseSaving.value = false;
  }
}

onMounted(queryData);
</script>

<style scoped>
.date-separator {
  margin: 0 8px;
  color: #667085;
}
.page-error,
.form-error {
  margin-bottom: 12px;
}
.finance-mobile-balance-list {
  display: none;
}

.finance-mobile-balance-row {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.mobile-balance-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mobile-balance-heading strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mobile-balance-party {
  margin: 0 0 6px;
  color: #667085;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.mobile-balance-values {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 14px;
  font-variant-numeric: tabular-nums;
}
.mobile-balance-values span {
  color: #667085;
  font-size: 12px;
}
.mobile-balance-values strong {
  color: #1d2939;
  font-weight: 600;
}
@media (max-width: 768px) {
  .finance-mobile-balance-list {
    display: block;
  }
  /* 窄屏只保留卡片列表，避免表格和卡片重复展示 */
  .finance-table {
    display: none;
  }
  .finance-table :deep(.ant-table-cell-fix-right) {
    position: static !important;
    right: auto !important;
  }
}
</style>
