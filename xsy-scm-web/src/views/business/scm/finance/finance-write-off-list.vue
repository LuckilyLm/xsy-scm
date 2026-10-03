<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="核销日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期"/>
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期"/>
      </a-form-item>
      <a-form-item label="资金类型"><a-select v-model:value="query.sourceType" allow-clear placeholder="全部" style="width:140px"
        :options="[{label:'收款',value:'RECEIPT'},{label:'付款',value:'PAYMENT'},{label:'余额消费',value:'BALANCE_MOVEMENT'}]"/></a-form-item>
      <a-form-item label="资金单号"><a-input v-model:value="query.sourceNo" allow-clear @press-enter="onSearch"/></a-form-item>
      <a-form-item label="目标单号"><a-input v-model:value="query.targetNo" allow-clear @press-enter="onSearch"/></a-form-item>
      <a-form-item label="方向"><a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width:120px"/></a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.WRITE_OFF_QUERY" @click="onSearch">查询</a-button>
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
      <div class="smart-table-operate-block">核销流水</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.WRITE_OFF_ADD" type="primary" @click="openAdd">登记核销</a-button>
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_WRITE_OFF" :refresh="queryData"/>
      </div>
    </a-row>
    <div class="table-scroll-hint">左右滑动表格查看目标方、原因、时点和操作列</div>
    <a-table id="scm-finance-write-off-table" class="finance-table" size="small" :data-source="page.tableData.value" :columns="columns"
             row-key="writeOffId" :loading="page.loading.value" :pagination="false" bordered :scroll="{x:1300}">
      <template #bodyCell="{record,column,text}">
        <template v-if="column.dataIndex==='sourceType'">{{ text==='BALANCE_MOVEMENT'?'余额消费':text==='RECEIPT'?'收款':'付款' }}</template>
        <template v-else-if="column.dataIndex==='sourceNo' && record.sourceType==='BALANCE_MOVEMENT'">
          <span>{{ text }}</span><a-button type="link" v-privilege="'scm:balance:movement:query'"
            @click="movementDetail?.open({movementId: record.sourceId})">来源流水</a-button>
        </template>
        <template v-else-if="column.dataIndex==='targetType'">{{ text==='RECEIVABLE'?'应收':'应付' }}</template>
        <template v-else-if="column.dataIndex==='entryType'"><a-tag :color="SCM_FINANCE_ENTRY_COLOR[text]">{{ entryTypeText(text) }}</a-tag></template>
        <template v-else-if="column.dataIndex==='amount'">{{ moneyText(text) }}</template>
        <template v-else-if="column.dataIndex==='writtenOffAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex==='action'">
          <a-button v-if="record.entryType==='NORMAL'" type="link" danger v-privilege="PERM.WRITE_OFF_REVERSE"
                    @click="openReverse(record)">撤销核销</a-button>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="query.pageNum" v-model:page-size="query.pageSize"
                    :total="page.total.value" @change="queryData" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-card>

  <a-drawer v-model:open="addOpen" title="登记多目标核销" width="min(860px, 96vw)" :destroy-on-close="true">
    <a-alert class="form-hint" type="info" show-icon message="一笔收款只能分配到应收，一笔付款只能分配到应付；逐条输入本次核销金额。"/>
    <a-alert v-if="addError" class="form-error" type="error" show-icon :message="addError"/>
    <a-form layout="vertical">
      <a-form-item label="资金类型" required>
        <a-select v-model:value="sourceType" :options="sourceOptions" placeholder="选择资金类型" @change="resetAllocation"/>
      </a-form-item>
      <a-form-item label="资金单" required>
        <a-input :value="source?.documentNo || ''" readonly placeholder="选择可核销资金单">
          <template #addonAfter><a-button type="link" :disabled="!sourceType" @click="openSourcePicker">选择</a-button></template>
        </a-input>
      </a-form-item>
    </a-form>
    <a-descriptions v-if="source" class="source-summary" bordered size="small" :column="3">
      <a-descriptions-item label="资金方">{{ source.partyName }}</a-descriptions-item>
      <a-descriptions-item label="方向">{{ entryTypeText(source.entryType) }}</a-descriptions-item>
      <a-descriptions-item label="可核销金额">{{ moneyText(source.availableAmount) }}</a-descriptions-item>
    </a-descriptions>
    <div class="target-toolbar">
      <h3>分配目标</h3>
      <a-button :disabled="!source" @click="openTargetPicker">添加目标单</a-button>
    </div>
    <a-empty v-if="!targets.length" description="先选择一笔资金单，再添加一个或多个目标单"/>
    <a-table v-else size="small" :data-source="targets" :columns="targetColumns" row-key="id" :pagination="false" :scroll="{x:690}">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex==='availableAmount'">{{ moneyText(record.availableAmount) }}</template>
        <template v-else-if="column.dataIndex==='amount'">
          <a-input-number v-model:value="record.amount" string-mode :min="0" :precision="4" :max="99999999999999" style="width:150px"/>
        </template>
        <template v-else-if="column.dataIndex==='action'"><a-button type="link" danger @click="removeTarget(record.id)">移除</a-button></template>
      </template>
    </a-table>
    <div class="drawer-footer">
      <a-button @click="addOpen=false">取消</a-button>
      <a-button type="primary" :loading="addSaving" :disabled="!source || !targets.length" @click="submitAdd">提交核销</a-button>
    </div>
  </a-drawer>

  <a-modal v-model:open="reverseOpen" title="撤销核销" :confirm-loading="reverseSaving" @ok="submitReverse">
    <a-alert v-if="reverseRow" type="warning" show-icon :message="`将追加一条反向核销，金额 ${moneyText(reverseRow.amount)}。`"/>
    <a-alert v-if="reverseError" class="form-error" type="error" show-icon :message="reverseError"/>
    <a-form layout="vertical"><a-form-item label="撤销原因" required><a-textarea v-model:value="reverseReason" :maxlength="500" :rows="3" show-count/></a-form-item></a-form>
  </a-modal>

  <BalanceMovementDetail ref="movementDetail"/>
  <FinanceRecordPicker v-model:open="pickerOpen" :kind="pickerKind" @select="onCandidateSelected"/>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {SCM_FINANCE_ENTRY_COLOR, SCM_FINANCE_ENTRY_TYPE_ENUM, SCM_FINANCE_PERMISSION as PERM, SCM_FINANCE_SOURCE_TYPE_ENUM} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import FinanceRecordPicker from './finance-record-picker.vue';
import BalanceMovementDetail from './balance-movement-detail.vue';
const movementDetail = ref<InstanceType<typeof BalanceMovementDetail>>();
import {dateTimeText, entryTypeText, initialFinanceDateRange, moneyText, isValidPositiveAmount} from './finance-form-model';
import {financeError} from './finance-errors';
import type {FinanceCandidate, FinanceWriteOff, WriteOffQuery} from './finance-types';
import {useFinancePage} from './use-finance-page';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';

interface AllocationDraft extends FinanceCandidate { amount: string; }
const query = reactive<WriteOffQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinanceWriteOff, WriteOffQuery>(financeApi.writeOffQuery, financeApi.writeOffExport);
const addOpen = ref(false), addSaving = ref(false), addError = ref(''), sourceType = ref<'RECEIPT'|'PAYMENT'>();
const source = ref<FinanceCandidate | null>(null), targets = ref<AllocationDraft[]>([]);
const pickerOpen = ref(false), pickerKind = ref<'RECEIPT'|'PAYMENT'|'RECEIVABLE'|'PAYABLE'>('RECEIPT'), picking = ref<'SOURCE'|'TARGET'>('SOURCE');
const reverseOpen = ref(false), reverseSaving = ref(false), reverseError = ref(''), reverseReason = ref(''), reverseRow = ref<FinanceWriteOff | null>(null);
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const sourceOptions = Object.values(SCM_FINANCE_SOURCE_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

const columns = ref<TableColumnsType<FinanceWriteOff>>([
    {title: '核销单号', dataIndex: 'writeOffNo', width: 210}, {title: '资金类型', dataIndex: 'sourceType', width: 100},
    {title: '资金单号', dataIndex: 'sourceNo', width: 190}, {title: '资金方', dataIndex: 'sourceName', width: 180},
    {title: '目标类型', dataIndex: 'targetType', width: 100}, {title: '目标单号', dataIndex: 'targetNo', width: 190},
    {title: '目标方', dataIndex: 'targetName', width: 180}, {title: '金额', dataIndex: 'amount', align: 'right', width: 145},
    {title: '方向', dataIndex: 'entryType', width: 90}, {title: '原因', dataIndex: 'reason', width: 200},
    {title: '核销时点', dataIndex: 'writtenOffAt', width: 180}, {title: '操作人', dataIndex: 'operator', width: 130},
    {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'right', width: 120},
]);
const targetColumns: TableColumnsType<AllocationDraft> = [
    {title: '目标单号', dataIndex: 'documentNo', width: 210}, {title: '往来方', dataIndex: 'partyName', width: 170},
    {title: '关联单号', dataIndex: 'linkedDocumentNo', width: 180}, {title: '可核销金额', dataIndex: 'availableAmount', align: 'right', width: 150},
    {title: '本次金额', dataIndex: 'amount', align: 'right', width: 180}, {title: '操作', dataIndex: 'action', width: 90},
];
useFinanceMobileActionColumn((compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
}, () => columns.value.length);

async function queryData() { await page.queryData(query); }
function onSearch() { query.pageNum = 1; queryData(); }
function resetQuery() {
    Object.assign(query, {pageNum: 1, pageSize: 20, ...initialFinanceDateRange(), sourceNo: undefined,
        targetNo: undefined, entryType: undefined, sourceType: undefined});
    queryData();
}
async function exportData() { await page.exportData(query); }

function openAdd() { addError.value = ''; sourceType.value = undefined; source.value = null; targets.value = []; addOpen.value = true; }
function resetAllocation() { source.value = null; targets.value = []; }
function openSourcePicker() {
    if (!sourceType.value) return;
    picking.value = 'SOURCE'; pickerKind.value = sourceType.value; pickerOpen.value = true;
}
function openTargetPicker() {
    if (!sourceType.value || !source.value) return;
    picking.value = 'TARGET'; pickerKind.value = sourceType.value === 'RECEIPT' ? 'RECEIVABLE' : 'PAYABLE'; pickerOpen.value = true;
}
function onCandidateSelected(candidate: FinanceCandidate) {
    if (picking.value === 'SOURCE') {
        source.value = candidate;
        targets.value = [];
        return;
    }
    if (targets.value.some((item) => String(item.id) === String(candidate.id))) {
        addError.value = '同一目标单不能重复添加。';
        return;
    }
    addError.value = '';
    targets.value.push({...candidate, amount: ''});
}
function removeTarget(id: FinanceCandidate['id']) { targets.value = targets.value.filter((item) => String(item.id) !== String(id)); }

async function submitAdd() {
    const items = targets.value.filter((item) => isValidPositiveAmount(item.amount)).map((item) => ({targetId: item.id, amount: item.amount}));
    if (!sourceType.value || !source.value || !items.length) {
        addError.value = '请选择资金单和目标单，并为至少一条目标填写正数金额（最多 4 位小数）。';
        return;
    }
    addSaving.value = true; addError.value = '';
    try {
        await financeApi.writeOffAdd({sourceType: sourceType.value, sourceId: source.value.id, items});
        message.success('核销已登记'); addOpen.value = false; await queryData();
    } catch (cause) { addError.value = financeError(cause); }
    finally { addSaving.value = false; }
}

function openReverse(row: FinanceWriteOff) { reverseRow.value = row; reverseReason.value = ''; reverseError.value = ''; reverseOpen.value = true; }
async function submitReverse() {
    if (!reverseRow.value || !reverseReason.value.trim()) { reverseError.value = '请填写撤销原因。'; return; }
    reverseSaving.value = true; reverseError.value = '';
    try {
        await financeApi.writeOffReverse({writeOffId: reverseRow.value.writeOffId, reason: reverseReason.value.trim()});
        message.success('反向核销已追加'); reverseOpen.value = false; await queryData();
    } catch (cause) { reverseError.value = financeError(cause); }
    finally { reverseSaving.value = false; }
}

onMounted(queryData);
</script>

<style scoped>
.date-separator { margin: 0 8px; color: #667085; }
.page-error,.form-error { margin-bottom: 12px; }
.form-hint { margin-bottom: 16px; }
.table-scroll-hint { display: none; margin-bottom: 8px; color: #667085; font-size: 12px; }
@media (max-width: 768px) {
  .table-scroll-hint { display: block; }
  .finance-table :deep(.ant-table-cell-fix-right) { position: static !important; right: auto !important; }
}
.source-summary { margin-top: 12px; }
.target-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 20px 0 12px; }
.target-toolbar h3 { margin: 0; font-weight: 600; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
</style>
