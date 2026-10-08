<template>
  <a-modal :open="open" :title="title" width="900px" :footer="null" destroy-on-close @cancel="close">
    <a-form class="smart-query-form" layout="inline" @submit.prevent>
      <a-row class="smart-query-form-row">
        <a-form-item label="业务日期">
          <a-date-picker v-model:value="startDate" value-format="YYYY-MM-DD" placeholder="开始日期"/>
          <span class="date-separator">至</span>
          <a-date-picker v-model:value="endDate" value-format="YYYY-MM-DD" placeholder="结束日期"/>
        </a-form-item>
        <a-form-item v-if="kind==='RECEIVABLE' || kind==='PAYABLE'" label="结清状态">
          <a-select v-model:value="settleState" :options="settleOptions" style="width: 150px"/>
        </a-form-item>
        <a-form-item label="单据 ID">
          <a-input-number v-model:value="recordId" :min="1" :precision="0" placeholder="可选" style="width: 140px"/>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="loading" @click="search">查询可用单据</a-button>
        </a-form-item>
      </a-row>
    </a-form>
    <a-table size="small" row-key="id" :data-source="rows" :columns="columns" :loading="loading"
             :pagination="false" :scroll="{x:830}">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex==='availableAmount'">{{ moneyText(record.availableAmount) }}</template>
        <template v-else-if="column.dataIndex==='select'">
          <a-button type="link" @click="choose(record)">选择</a-button>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination show-size-changer show-quick-jumper v-model:current="pageNum" v-model:page-size="pageSize"
                    :total="total" @change="search" :show-total="(count:number)=>`共${count}条`"/>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {SCM_FINANCE_SETTLE_STATE_ENUM} from '/@/constants/business/scm/finance-const';
import {financeError} from './finance-errors';
import {entryTypeText, initialFinanceDateRange, moneyText} from './finance-form-model';
import {useScmErrorToast} from '../common/scm-error-toast';
import type {FinanceCandidate, FinancePayable, FinancePayment, FinanceReceivable, FinanceReceipt} from './finance-types';

type PickKind = 'RECEIPT' | 'PAYMENT' | 'RECEIVABLE' | 'PAYABLE';

const props = defineProps<{open: boolean; kind: PickKind}>();
const emit = defineEmits<{(event: 'update:open', value: boolean): void; (event: 'select', value: FinanceCandidate): void}>();
const defaults = initialFinanceDateRange();
const startDate = ref(defaults.startDate);
const endDate = ref(defaults.endDate);
const settleState = ref<'OPEN' | 'PARTIAL'>('OPEN');
const recordId = ref<number>();
const pageNum = ref(1);
const pageSize = ref(10);
const rows = ref<FinanceCandidate[]>([]);
const total = ref(0);
const loading = ref(false);
const error = useScmErrorToast();

const title = computed(() => ({
    RECEIPT: '选择待核销收款',
    PAYMENT: '选择待核销付款',
    RECEIVABLE: '选择未结清应收',
    PAYABLE: '选择未结清应付',
}[props.kind]));
const settleOptions = Object.values(SCM_FINANCE_SETTLE_STATE_ENUM)
    .filter((item) => item.value !== 'SETTLED')
    .map((item) => ({label: item.desc, value: item.value}));
const columns = computed<TableColumnsType<FinanceCandidate>>(() => [
    {title: '单据编号', dataIndex: 'documentNo', width: 220},
    {title: props.kind === 'RECEIPT' || props.kind === 'RECEIVABLE' ? '客户' : '往来方', dataIndex: 'partyName', width: 180},
    {title: props.kind === 'RECEIVABLE' || props.kind === 'PAYABLE' ? '关联单号' : '方向', dataIndex: 'linkedDocumentNo', width: 180},
    {title: '可核销金额', dataIndex: 'availableAmount', align: 'right', width: 160},
    {title: '选择', dataIndex: 'select', align: 'right', width: 90},
]);

function toCandidate(row: FinanceReceipt | FinancePayment | FinanceReceivable | FinancePayable): FinanceCandidate {
    if (props.kind === 'RECEIPT') {
        const value = row as FinanceReceipt;
        return {id: value.receiptId, documentNo: value.receiptNo, partyName: value.customerName,
            linkedDocumentNo: entryTypeText(value.entryType), availableAmount: value.pendingWriteOffAmount, entryType: value.entryType};
    }
    if (props.kind === 'PAYMENT') {
        const value = row as FinancePayment;
        return {id: value.paymentId, documentNo: value.paymentNo, partyName: value.counterpartyName,
            linkedDocumentNo: entryTypeText(value.entryType), availableAmount: value.pendingWriteOffAmount, entryType: value.entryType};
    }
    if (props.kind === 'RECEIVABLE') {
        const value = row as FinanceReceivable;
        return {id: value.receivableId, documentNo: value.receivableNo, partyName: value.customerName,
            linkedDocumentNo: value.orderNo, availableAmount: value.openAmount, entryType: value.entryType};
    }
    const value = row as FinancePayable;
    return {id: value.payableId, documentNo: value.payableNo, partyName: value.supplierName,
        linkedDocumentNo: value.purchaseOrderNo, availableAmount: value.openAmount, entryType: value.entryType};
}

async function search() {
    loading.value = true;
    error.value = '';
    try {
        const base = {pageNum: pageNum.value, pageSize: pageSize.value, startDate: startDate.value, endDate: endDate.value};
        let response;
        if (props.kind === 'RECEIPT') {
            response = await financeApi.receiptQuery({...base, pendingOnly: true, receiptId: recordId.value});
        } else if (props.kind === 'PAYMENT') {
            response = await financeApi.paymentQuery({...base, counterpartyType: 'SUPPLIER', pendingOnly: true, paymentId: recordId.value});
        } else if (props.kind === 'RECEIVABLE') {
            response = await financeApi.receivableQuery({...base, settleState: settleState.value, receivableId: recordId.value});
        } else {
            response = await financeApi.payableQuery({...base, settleState: settleState.value, payableId: recordId.value});
        }
        rows.value = response.data.list.map(toCandidate);
        total.value = response.data.total;
    } catch (cause) {
        error.value = financeError(cause);
    } finally {
        loading.value = false;
    }
}

function choose(candidate: FinanceCandidate) {
    emit('select', candidate);
    close();
}

function close() {
    emit('update:open', false);
}

watch(() => props.open, (isOpen) => {
    if (isOpen) {
        pageNum.value = 1;
        recordId.value = undefined;
        search();
    }
});
</script>

<style scoped>
.date-separator {
  margin: 0 8px;
  color: #667085;
}
</style>
