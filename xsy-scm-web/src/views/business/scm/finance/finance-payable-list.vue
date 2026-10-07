<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="业务日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期"/>
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期"/>
      </a-form-item>
      <a-form-item label="供应商"><a-input v-model:value="query.supplierName" allow-clear placeholder="按供应商名称搜索" @press-enter="onSearch"/></a-form-item>
      <a-form-item label="采购单号"><a-input v-model:value="query.purchaseOrderNo" allow-clear @press-enter="onSearch"/></a-form-item>
      <a-form-item label="方向">
        <a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width: 120px"/>
      </a-form-item>
      <a-form-item label="结清状态">
        <a-select v-model:value="query.settleState" allow-clear :options="settleOptions" placeholder="全部" style="width: 140px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.PAYABLE_QUERY" @click="onSearch">查询</a-button>
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
      <div class="smart-table-operate-block">应付明细</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_PAYABLE" :refresh="queryData"/>
      </div>
    </a-row>
    <div class="finance-mobile-balance-list">
      <div v-for="record in page.tableData.value" :key="record.payableId" class="finance-mobile-balance-row">
        <div class="mobile-balance-heading"><strong>{{ record.payableNo }}</strong><a-button type="link" @click="showDetail(record)">明细</a-button></div>
        <div class="mobile-balance-party">{{ record.supplierName }} · {{ record.purchaseOrderNo }}</div>
        <div class="mobile-balance-values"><span>未核销 <strong>{{ moneyText(record.openAmount) }}</strong></span><span>金额 {{ moneyText(record.amount) }}</span></div>
        <a-tag v-if="record.overAppliedAmount && record.overAppliedAmount!=='0.0000'" color="orange">超额核销待处理</a-tag>
      </div>
    </div>
    <a-table id="scm-finance-payable-table" class="finance-table" size="small" :data-source="page.tableData.value" :columns="columns"
             row-key="payableId" :loading="page.loading.value" :pagination="false" bordered :scroll="{x:1375}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='entryType'"><ScmStatusTag :color="SCM_FINANCE_ENTRY_COLOR[text]" :label="entryTypeText(text)"/></template>
        <template v-else-if="column.dataIndex==='settleState'"><ScmStatusTag :tone="settleStateTone(text)" :label="settleStateText(text)"/></template>
        <template v-else-if="column.dataIndex==='overAppliedAmount'">
          <span :class="moneyClass(text,'anomaly')">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="['amount','writtenOffAmount','openAmount'].includes(column.dataIndex)">
          <span :class="moneyClass(text, column.dataIndex==='amount'?'fact':'balance')">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="column.dataIndex==='eventAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <a-space :size="0" class="scm-table-actions">
            <a-button type="link" @click="showDetail(record)">明细</a-button>
            <a-button v-if="record.entryType==='NORMAL'" type="link" danger v-privilege="PERM.PAYABLE_RED" @click="openRed(record)">登记红字</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="query.pageNum" v-model:page-size="query.pageSize"
                    :total="page.total.value" @change="queryData" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-card>

  <FinanceDetailDrawer v-model:open="detailOpen" kind="PAYABLE" :loading="detailLoading" :detail="detailData"/>

  <a-drawer v-model:open="redOpen" title="登记红字应付" :width="scmDrawerWidth('l')" :destroy-on-close="true">
    <a-spin :spinning="redLoading">
      <a-alert type="info" show-icon message="红字金额必须等于数量 × 单价，累计不超过原单金额。"/>
      <a-alert v-if="redError" class="form-error" type="error" show-icon :message="redError"/>
      <a-descriptions v-if="redSource" class="red-source" bordered size="small" :column="2">
        <a-descriptions-item label="原应付单">{{ redSource.payableNo }}</a-descriptions-item>
        <a-descriptions-item label="供应商">{{ redSource.supplierName }}</a-descriptions-item>
        <a-descriptions-item label="原应付金额">{{ moneyText(redSource.amount) }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical">
        <a-form-item label="红字原因" required><a-textarea v-model:value="redReason" :maxlength="500" :rows="2" show-count/></a-form-item>
      </a-form>
      <a-table size="small" :data-source="redDrafts" :columns="redDraftColumns" row-key="purchaseOrderItemId"
               :pagination="false" :scroll="{x:850}">
        <template #bodyCell="{record,column}">
          <template v-if="column.dataIndex==='redQuantity'">
            <a-input-number v-model:value="record.redQuantity" string-mode :min="0" :precision="4" :max="99999999999999" style="width:125px"/>
          </template>
          <template v-else-if="column.dataIndex==='redUnitPrice'">
            <a-input-number v-model:value="record.redUnitPrice" string-mode :min="0" :precision="4" :max="99999999999999" style="width:125px"/>
          </template>
          <template v-else-if="column.dataIndex==='redAmount'">
            <a-input-number v-model:value="record.redAmount" string-mode :min="0" :precision="4" :max="99999999999999" style="width:135px"/>
          </template>
        </template>
      </a-table>
    </a-spin>
    <div class="drawer-footer">
      <a-button @click="redOpen=false">取消</a-button>
      <a-button type="primary" :loading="redSaving" @click="submitRed">提交红字</a-button>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {SCM_FINANCE_ENTRY_COLOR, SCM_FINANCE_ENTRY_TYPE_ENUM, SCM_FINANCE_PERMISSION as PERM, SCM_FINANCE_SETTLE_STATE_ENUM} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import FinanceDetailDrawer from './finance-detail-drawer.vue';
import {dateTimeText, entryTypeText, initialFinanceDateRange, moneyClass, moneyText, settleStateText, settleStateTone} from './finance-form-model';
import {financeError} from './finance-errors';
import type {FinancePayable, FinancePayableDetail, FinancePayableItem, PayableQuery} from './finance-types';
import {useFinancePage} from './use-finance-page';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

interface RedDraft extends FinancePayableItem {
    redQuantity: string;
    redUnitPrice: string;
    redAmount: string;
}

const query = reactive<PayableQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinancePayable, PayableQuery>(financeApi.payableQuery, financeApi.payableExport);
const detailOpen = ref(false), detailLoading = ref(false), detailData = ref<FinancePayableDetail | null>(null);
const redOpen = ref(false), redLoading = ref(false), redSaving = ref(false), redError = ref(''), redReason = ref('');
const redSource = ref<FinancePayable | null>(null);
const redDrafts = ref<RedDraft[]>([]);
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const settleOptions = Object.values(SCM_FINANCE_SETTLE_STATE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

const columns = ref<TableColumnsType<FinancePayable>>([
    {title: '应付单号', dataIndex: 'payableNo', fixed: 'left', width: 110, ellipsis: true},
    {title: '未核销', dataIndex: 'openAmount', fixed: 'left', align: 'right', width: 90},
    {title: '供应商', dataIndex: 'supplierName', width: 150}, {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 150},
    {title: '方向', dataIndex: 'entryType', align: 'center', width: 80}, {title: '金额', dataIndex: 'amount', align: 'right', width: 120},
    {title: '已核销', dataIndex: 'writtenOffAmount', align: 'right', width: 110},
    {title: '超额核销', dataIndex: 'overAppliedAmount', align: 'right', width: 120},
    {title: '结清状态', dataIndex: 'settleState', align: 'center', width: 110}, {title: '事件时点', dataIndex: 'eventAt', width: 165},
    {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'center', width: 130},
]);
const redDraftColumns: TableColumnsType<RedDraft> = [
    {title: '商品', dataIndex: 'skuName', width: 190}, {title: '单位', dataIndex: 'unit', width: 90},
    {title: '原数量', dataIndex: 'quantity', width: 130, customRender: ({text}) => text},
    {title: '红字数量', dataIndex: 'redQuantity'}, {title: '红字单价', dataIndex: 'redUnitPrice'}, {title: '红字金额', dataIndex: 'redAmount'},
];
useFinanceMobileActionColumn((compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
}, () => columns.value.length);

async function queryData() { await page.queryData(query); }
function onSearch() { query.pageNum = 1; queryData(); }
function resetQuery() {
    Object.assign(query, {pageNum: 1, pageSize: 20, ...initialFinanceDateRange(), supplierId: undefined, supplierName: undefined,
        purchaseOrderNo: undefined, entryType: undefined, settleState: undefined});
    queryData();
}
async function exportData() { await page.exportData(query); }

async function showDetail(row: FinancePayable) {
    detailOpen.value = true;
    detailLoading.value = true;
    detailData.value = null;
    try { detailData.value = (await financeApi.payableDetail(row.payableId)).data; }
    catch (cause) { page.error.value = financeError(cause); detailOpen.value = false; }
    finally { detailLoading.value = false; }
}

async function openRed(row: FinancePayable) {
    redOpen.value = true;
    redLoading.value = true;
    redError.value = '';
    redReason.value = '';
    redSource.value = row;
    redDrafts.value = [];
    try {
        const detail = (await financeApi.payableDetail(row.payableId)).data;
        redDrafts.value = detail.items.map((item) => ({...item, redQuantity: '', redUnitPrice: item.unitPrice, redAmount: ''}));
    } catch (cause) {
        redError.value = financeError(cause);
    } finally {
        redLoading.value = false;
    }
}

async function submitRed() {
    const lines = redDrafts.value.filter((item) => item.redQuantity && item.redAmount).map((item) => ({
        purchaseOrderItemId: item.purchaseOrderItemId,
        quantity: item.redQuantity,
        unitPrice: item.redUnitPrice,
        amount: item.redAmount,
    }));
    if (!redSource.value || !redReason.value.trim() || !lines.length) {
        redError.value = '请填写原因，并至少录入一条红字明细。';
        return;
    }
    redSaving.value = true;
    redError.value = '';
    try {
        await financeApi.payableRed({originalPayableId: redSource.value.payableId, reason: redReason.value.trim(), items: lines});
        message.success('红字应付已登记');
        redOpen.value = false;
        await queryData();
    } catch (cause) {
        redError.value = financeError(cause);
    } finally {
        redSaving.value = false;
    }
}

onMounted(queryData);
</script>

<style scoped>
.date-separator { margin: 0 8px; color: #667085; }
.page-error,.form-error { margin-bottom: 12px; }
.money-alert { font-weight: 600; }
.red-source { margin: 16px 0; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
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
