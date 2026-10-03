<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="收款日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期"/>
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期"/>
      </a-form-item>
      <a-form-item label="客户">
        <a-select v-model:value="query.customerId" allow-clear show-search option-filter-prop="label"
                  :options="customerOptions" placeholder="全部客户" style="width: 190px"/>
      </a-form-item>
      <a-form-item label="方式">
        <a-select v-model:value="query.method" allow-clear :options="methodOptions" placeholder="全部" style="width: 140px"/>
      </a-form-item>
      <a-form-item label="方向">
        <a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width: 120px"/>
      </a-form-item>
      <a-form-item label="待核销">
        <a-select v-model:value="query.pendingOnly" allow-clear :options="pendingOptions" placeholder="全部" style="width: 120px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.RECEIPT_QUERY" @click="onSearch">查询</a-button>
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
      <div class="smart-table-operate-block">收款明细</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.RECEIPT_ADD" type="primary" @click="openAdd">登记收款</a-button>
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_RECEIPT" :refresh="queryData"/>
      </div>
    </a-row>
    <div class="table-scroll-hint">左右滑动表格查看其余金额、凭据和操作列</div>
    <div class="finance-mobile-balance-list">
      <div v-for="record in page.tableData.value" :key="record.receiptId" class="finance-mobile-balance-row">
        <div class="mobile-balance-heading"><strong>{{ record.receiptNo }}</strong><a-button type="link" @click="showDetail(record)">明细</a-button></div>
        <div class="mobile-balance-party">{{ record.customerName }} · {{ paymentMethodText(record.method) }} · {{ entryTypeText(record.entryType) }}</div>
        <div class="mobile-balance-values"><span>待核销 <strong>{{ moneyText(record.pendingWriteOffAmount) }}</strong></span><span>有效 {{ moneyText(record.effectiveAmount) }}</span></div>
      </div>
    </div>
    <a-table id="scm-finance-receipt-table" class="finance-table" size="small" :data-source="page.tableData.value" :columns="columns"
             row-key="receiptId" :loading="page.loading.value" :pagination="false" bordered :scroll="{x:1440}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='entryType'"><a-tag :color="SCM_FINANCE_ENTRY_COLOR[text]">{{ entryTypeText(text) }}</a-tag></template>
        <template v-else-if="column.dataIndex==='method'">{{ paymentMethodText(text) }}</template>
        <template v-else-if="['amount','effectiveAmount','usedAmount','pendingWriteOffAmount'].includes(column.dataIndex)">{{ moneyText(text) }}</template>
        <template v-else-if="column.dataIndex==='receivedAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <a-button type="link" @click="showDetail(record)">明细</a-button>
          <a-button v-if="record.entryType==='NORMAL'" type="link" danger v-privilege="PERM.RECEIPT_REVERSE"
                    :disabled="record.usedAmount!=='0.0000'" @click="openReverse(record)">反向</a-button>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="query.pageNum" v-model:page-size="query.pageSize"
                    :total="page.total.value" @change="queryData" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-card>

  <FinanceDetailDrawer v-model:open="detailOpen" kind="RECEIPT" :loading="detailLoading" :detail="detailData"/>

  <a-modal v-model:open="addOpen" title="登记收款" :confirm-loading="addSaving" @ok="submitAdd">
    <a-alert v-if="addError" class="form-error" type="error" show-icon :message="addError"/>
    <a-form layout="vertical">
      <a-form-item label="客户" required>
        <a-select v-model:value="addForm.customerId" show-search option-filter-prop="label" :options="customerOptions" placeholder="选择客户"/>
      </a-form-item>
      <a-form-item label="收款金额" required>
        <a-input-number v-model:value="addForm.amount" string-mode :min="0" :precision="4" :max="99999999999999" style="width:100%"/>
      </a-form-item>
      <a-form-item label="收款方式" required><a-select v-model:value="addForm.method" :options="methodOptions" placeholder="选择方式"/></a-form-item>
      <a-form-item label="收款时间" required><a-date-picker v-model:value="addForm.receivedAt" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" style="width:100%"/></a-form-item>
      <a-form-item label="资金凭据号"><a-input v-model:value="addForm.externalReference" :maxlength="128"/></a-form-item>
      <a-form-item label="备注"><a-textarea v-model:value="addForm.remark" :maxlength="500" :rows="2" show-count/></a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="reverseOpen" title="反向收款" :confirm-loading="reverseSaving" @ok="submitReverse">
    <a-alert v-if="reverseRow" type="warning" show-icon :message="`将追加一笔反向收款，金额 ${moneyText(reverseRow.amount)}。原收款事实会保留。`"/>
    <a-alert v-if="reverseError" class="form-error" type="error" show-icon :message="reverseError"/>
    <a-form layout="vertical"><a-form-item label="反向原因" required>
      <a-textarea v-model:value="reverseReason" :maxlength="500" :rows="3" show-count/>
    </a-form-item></a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {financeApi} from '/@/api/business/scm/finance-api';
import {SCM_FINANCE_ENTRY_COLOR, SCM_FINANCE_ENTRY_TYPE_ENUM, SCM_FINANCE_RECEIPT_METHOD_ENUM, SCM_FINANCE_PERMISSION as PERM} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import FinanceDetailDrawer from './finance-detail-drawer.vue';
import {dateTimeText, entryTypeText, initialFinanceDateRange, isValidPositiveAmount, moneyText, nowDateTimeValue, paymentMethodText, trimOptional} from './finance-form-model';
import {financeError} from './finance-errors';
import type {FinanceReceipt, FinanceReceiptDetail, ReceiptQuery} from './finance-types';
import type {CustomerOption} from '/@/types/business/scm/customer';
import {useFinancePage} from './use-finance-page';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';

const query = reactive<ReceiptQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinanceReceipt, ReceiptQuery>(financeApi.receiptQuery, financeApi.receiptExport);
const customerOptions = ref<Array<{label: string; value: string | number}>>([]);
const detailOpen = ref(false), detailLoading = ref(false), detailData = ref<FinanceReceiptDetail | null>(null);
const addOpen = ref(false), addSaving = ref(false), addError = ref('');
const reverseOpen = ref(false), reverseSaving = ref(false), reverseError = ref(''), reverseReason = ref(''), reverseRow = ref<FinanceReceipt | null>(null);
const addForm = reactive({customerId: undefined as string | number | undefined, amount: '', method: undefined as 'CASH' | 'BANK_TRANSFER' | 'OTHER' | undefined,
    receivedAt: nowDateTimeValue(), externalReference: '', remark: ''});
const methodOptions = Object.values(SCM_FINANCE_RECEIPT_METHOD_ENUM).map((item) => ({label: item.desc, value: item.value}));
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const pendingOptions = [{label: '仅待核销', value: true}, {label: '全部', value: false}];
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

const columns = ref<TableColumnsType<FinanceReceipt>>([
    {title: '收款单号', dataIndex: 'receiptNo', fixed: 'left', width: 110, ellipsis: true},
    {title: '待核销', dataIndex: 'pendingWriteOffAmount', fixed: 'left', align: 'right', width: 90},
    {title: '客户', dataIndex: 'customerName', width: 170}, {title: '方向', dataIndex: 'entryType', width: 80},
    {title: '金额', dataIndex: 'amount', align: 'right', width: 125},
    {title: '有效金额', dataIndex: 'effectiveAmount', align: 'right', width: 125},
    {title: '已核销', dataIndex: 'usedAmount', align: 'right', width: 115}, {title: '方式', dataIndex: 'method', width: 110},
    {title: '凭据号', dataIndex: 'externalReference', width: 160}, {title: '收款时点', dataIndex: 'receivedAt', width: 165},
    {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'right', width: 95},
]);
useFinanceMobileActionColumn((compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
}, () => columns.value.length);

async function queryData() { await page.queryData(query); }
function onSearch() { query.pageNum = 1; queryData(); }
function resetQuery() {
    Object.assign(query, {pageNum: 1, pageSize: 20, ...initialFinanceDateRange(), customerId: undefined,
        method: undefined, entryType: undefined, pendingOnly: undefined});
    queryData();
}
async function exportData() { await page.exportData(query); }

async function showDetail(row: FinanceReceipt) {
    detailOpen.value = true; detailLoading.value = true; detailData.value = null;
    try { detailData.value = (await financeApi.receiptDetail(row.receiptId)).data; }
    catch (cause) { page.error.value = financeError(cause); detailOpen.value = false; }
    finally { detailLoading.value = false; }
}

function openAdd() {
    addError.value = '';
    Object.assign(addForm, {customerId: undefined, amount: '', method: undefined, receivedAt: nowDateTimeValue(), externalReference: '', remark: ''});
    addOpen.value = true;
}

async function submitAdd() {
    if (addForm.customerId == null || !isValidPositiveAmount(addForm.amount) || !addForm.method || !addForm.receivedAt) {
        addError.value = '请完整填写客户、正数金额、方式和收款时间。金额最多四位小数。';
        return;
    }
    addSaving.value = true; addError.value = '';
    try {
        await financeApi.receiptAdd({customerId: addForm.customerId, amount: addForm.amount, method: addForm.method,
            receivedAt: addForm.receivedAt, externalReference: trimOptional(addForm.externalReference), remark: trimOptional(addForm.remark)});
        message.success('收款已登记'); addOpen.value = false; await queryData();
    } catch (cause) { addError.value = financeError(cause); }
    finally { addSaving.value = false; }
}

function openReverse(row: FinanceReceipt) {
    reverseRow.value = row; reverseReason.value = ''; reverseError.value = ''; reverseOpen.value = true;
}

async function submitReverse() {
    if (!reverseRow.value || !reverseReason.value.trim()) { reverseError.value = '请填写反向原因。'; return; }
    reverseSaving.value = true; reverseError.value = '';
    try {
        await financeApi.receiptReverse({receiptId: reverseRow.value.receiptId, reason: reverseReason.value.trim()});
        message.success('反向收款已追加'); reverseOpen.value = false; await queryData();
    } catch (cause) { reverseError.value = financeError(cause); }
    finally { reverseSaving.value = false; }
}

onMounted(async () => {
    try {
        const response = await customerApi.optionList();
        customerOptions.value = response.data.map((item: CustomerOption) => ({label: `${item.customerCode} · ${item.name}`, value: item.customerId}));
    } catch {
        page.error.value = '客户选项暂不可用，请稍后重试。';
    }
    queryData();
});
</script>

<style scoped>
.date-separator { margin: 0 8px; color: #667085; }
.page-error,.form-error { margin-bottom: 12px; }
.table-scroll-hint { display: none; margin-bottom: 8px; color: #667085; font-size: 12px; }
.finance-mobile-balance-list { display: none; }

.finance-mobile-balance-row { padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.mobile-balance-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mobile-balance-heading strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-balance-party { margin: 0 0 6px; color: #667085; font-size: 12px; overflow-wrap: anywhere; }
.mobile-balance-values { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px 14px; font-variant-numeric: tabular-nums; }
.mobile-balance-values span { color: #667085; font-size: 12px; }
.mobile-balance-values strong { color: #1d2939; font-weight: 600; }
@media (max-width: 768px) { .table-scroll-hint { display: block; } .finance-mobile-balance-list { display: block; } }
@media (max-width: 768px) {
  .table-scroll-hint { display: block; }
  .finance-table :deep(.ant-table-cell-fix-right) { position: static !important; right: auto !important; }
}
</style>
