<template>
  <a-drawer :open="open" :title="title" width="min(1120px, 96vw)" :destroy-on-close="true" @close="close">
    <a-spin :spinning="loading">
      <template v-if="detail">
        <section class="detail-section">
          <h3>单据概要</h3>
          <a-descriptions bordered size="small" :column="2">
            <a-descriptions-item label="单号">{{ headerNo }}</a-descriptions-item>
            <a-descriptions-item label="方向">{{ entryTypeText(header.entryType) }}</a-descriptions-item>
            <a-descriptions-item v-if="header.settlementCustomerName" label="单据结算方">{{ header.settlementCustomerName }}</a-descriptions-item>
            <a-descriptions-item v-if="isAccount" label="冻结到期日">{{ header.dueDate || '未设置（不推算历史账期）' }}</a-descriptions-item>
            <a-descriptions-item label="往来方">{{ partyName }}</a-descriptions-item>
            <a-descriptions-item label="关联单号">{{ linkedNo }}</a-descriptions-item>
            <a-descriptions-item label="金额">{{ moneyText(header.amount) }}</a-descriptions-item>
            <a-descriptions-item v-if="isAccount" label="净应收 / 净应付">{{ moneyText(header.netAmount) }}</a-descriptions-item>
            <a-descriptions-item v-if="!isAccount" label="有效金额">{{ moneyText(header.effectiveAmount) }}</a-descriptions-item>
            <a-descriptions-item v-if="isAccount" label="已核销">{{ moneyText(header.writtenOffAmount) }}</a-descriptions-item>
            <a-descriptions-item v-else label="已核销">{{ moneyText(header.usedAmount) }}</a-descriptions-item>
            <a-descriptions-item v-if="isAccount" label="未核销">{{ moneyText(header.openAmount) }}</a-descriptions-item>
            <a-descriptions-item v-else label="待核销">{{ moneyText(header.pendingWriteOffAmount) }}</a-descriptions-item>
            <a-descriptions-item label="业务时点">{{ dateTimeText(header.eventAt ?? header.receivedAt ?? header.paidAt) }}</a-descriptions-item>
            <a-descriptions-item label="原因">{{ header.reason || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="header.reverseOfNo" label="原单号">{{ header.reverseOfNo }}</a-descriptions-item>
          </a-descriptions>
          <a-alert v-if="header.overAppliedAmount && header.overAppliedAmount !== '0.0000'"
                   class="over-applied" type="warning" show-icon message="超额核销待处理"/>
          <a-alert v-if="header.netAmount?.startsWith('-')" class="over-applied" type="error" show-icon
                   message="净应收为负数，请结合红字和核销记录核对。"/>
        </section>

        <a-alert v-if="relationText" class="over-applied" type="info" show-icon :message="relationText"/>

        <section v-if="receivableItems.length || payableItems.length" class="detail-section">
          <h3>明细行</h3>
          <a-table v-if="isReceivable" size="small" :data-source="receivableItems" :columns="receivableColumns"
                   row-key="receivableItemId" :pagination="false" :scroll="{x:760}"/>
          <a-table v-else size="small" :data-source="payableItems" :columns="payableColumns"
                   row-key="payableItemId" :pagination="false" :scroll="{x:760}"/>
        </section>

        <section v-if="redEntries.length" class="detail-section">
          <h3>红字关联</h3>
          <a-table size="small" :data-source="redEntries" :columns="redColumns" row-key="id" :pagination="false"/>
        </section>

        <section v-if="writeOffs.length" class="detail-section">
          <h3>核销记录</h3>
          <a-table size="small" :data-source="writeOffs" :columns="writeOffColumns" row-key="writeOffId"
                   :pagination="false" :scroll="{x:900}"/>
        </section>

        <section v-if="operationLogs.length" class="detail-section">
          <h3>财务操作记录</h3>
          <a-table size="small" :data-source="operationLogs" :columns="logColumns" row-key="id"
                   :pagination="false" :scroll="{x:960}">
            <template #bodyCell="{record,column}">
              <template v-if="column.dataIndex==='snapshot'">
                <details v-if="record.beforeData || record.afterData">
                  <summary>查看金额快照</summary>
                  <pre>{{ JSON.stringify({before: record.beforeData, after: record.afterData}, null, 2) }}</pre>
                </details>
                <span v-else>—</span>
              </template>
            </template>
          </a-table>
        </section>

        <a-empty v-if="!writeOffs.length && !operationLogs.length" description="暂无核销或操作记录"/>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import type {
    FinanceOperationLog,
    FinancePayable,
    FinancePayableDetail,
    FinancePayableItem,
    FinancePaymentDetail,
    FinanceReceivable,
    FinanceReceivableDetail,
    FinanceReceivableItem,
    FinanceReceiptDetail,
    FinanceWriteOff,
} from './finance-types';
import {dateTimeText, entryTypeText, moneyText, numberText} from './finance-form-model';

type FinanceDetail = FinanceReceivableDetail | FinancePayableDetail | FinanceReceiptDetail | FinancePaymentDetail;
const props = defineProps<{open: boolean; loading: boolean; kind: 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT'; detail?: FinanceDetail | null}>();
const emit = defineEmits<{(event: 'update:open', value: boolean): void}>();
const detail = computed(() => props.detail ?? null);
const isReceivable = computed(() => props.kind === 'RECEIVABLE');
const isPayable = computed(() => props.kind === 'PAYABLE');
const isAccount = computed(() => isReceivable.value || isPayable.value);
const title = computed(() => ({RECEIVABLE: '应收明细', PAYABLE: '应付明细', RECEIPT: '收款明细', PAYMENT: '付款明细'}[props.kind]));
const header = computed(() => {
    if (!detail.value) return {} as Record<string, string | null | undefined>;
    if ('receivable' in detail.value) return detail.value.receivable as unknown as Record<string, string | null | undefined>;
    if ('payable' in detail.value) return detail.value.payable as unknown as Record<string, string | null | undefined>;
    if ('receipt' in detail.value) return detail.value.receipt as unknown as Record<string, string | null | undefined>;
    return detail.value.payment as unknown as Record<string, string | null | undefined>;
});
const headerNo = computed(() => header.value.receivableNo ?? header.value.payableNo ?? header.value.receiptNo ?? header.value.paymentNo ?? '—');
const partyName = computed(() => header.value.customerName ?? header.value.supplierName ?? header.value.counterpartyName ?? '—');
const linkedNo = computed(() => header.value.orderNo ?? header.value.purchaseOrderNo ?? header.value.sourceType ?? '—');
const receivableItems = computed(() => detail.value && 'items' in detail.value && 'receivable' in detail.value
    ? detail.value.items as FinanceReceivableItem[] : []);
const payableItems = computed(() => detail.value && 'items' in detail.value && 'payable' in detail.value
    ? detail.value.items as FinancePayableItem[] : []);
const redEntries = computed(() => detail.value && 'redEntries' in detail.value ? detail.value.redEntries : []);
const writeOffs = computed(() => detail.value?.writeOffs ?? []);
const operationLogs = computed(() => detail.value?.operationLogs ?? []);
const relationText = computed(() => {
    if (!detail.value) return '';
    if ('original' in detail.value && detail.value.original) {
        const row = detail.value.original;
        return `原单：${('receiptNo' in row ? row.receiptNo : row.paymentNo) ?? '—'}`;
    }
    if ('reversal' in detail.value && detail.value.reversal) {
        const row = detail.value.reversal;
        return `反向记录：${('receiptNo' in row ? row.receiptNo : row.paymentNo) ?? '—'}`;
    }
    return '';
});

const receivableColumns: TableColumnsType<FinanceReceivableItem> = [
    {title: '出库来源', dataIndex: 'sourceId', width: 120}, {title: '商品', dataIndex: 'skuName', width: 200},
    {title: '数量', dataIndex: 'quantity', align: 'right', customRender: ({text}) => numberText(text)},
    {title: '单价', dataIndex: 'unitPrice', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '金额', dataIndex: 'amount', align: 'right', customRender: ({text}) => moneyText(text)},
];
const payableColumns: TableColumnsType<FinancePayableItem> = [
    {title: '收货来源', dataIndex: 'sourceId', width: 120}, {title: '商品', dataIndex: 'skuName', width: 200},
    {title: '数量', dataIndex: 'quantity', align: 'right', customRender: ({text}) => numberText(text)},
    {title: '单价', dataIndex: 'unitPrice', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '金额', dataIndex: 'amount', align: 'right', customRender: ({text}) => moneyText(text)},
];
const redColumns: TableColumnsType<FinanceReceivable | FinancePayable> = [
    {title: '红字单号', dataIndex: 'receivableNo', customRender: ({record}) => ('receivableNo' in record ? record.receivableNo : record.payableNo)},
    {title: '金额', dataIndex: 'amount', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '原因', dataIndex: 'reason'}, {title: '业务时点', dataIndex: 'eventAt', customRender: ({text}) => dateTimeText(text)},
];
const writeOffColumns: TableColumnsType<FinanceWriteOff> = [
    {title: '核销单号', dataIndex: 'writeOffNo', width: 190}, {title: '资金单', dataIndex: 'sourceNo', width: 180},
    {title: '目标单', dataIndex: 'targetNo', width: 180}, {title: '方向', dataIndex: 'entryType', width: 90, customRender: ({text}) => entryTypeText(text)},
    {title: '金额', dataIndex: 'amount', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '原因', dataIndex: 'reason'},
];
const logColumns: TableColumnsType<FinanceOperationLog> = [
    {title: '时间', dataIndex: 'createdAt', width: 180, customRender: ({text}) => dateTimeText(text)},
    {title: '操作', dataIndex: 'operationType', width: 160}, {title: '操作人', dataIndex: 'operator', width: 130},
    {title: '原因', dataIndex: 'reason', width: 200}, {title: '快照', dataIndex: 'snapshot', width: 150},
];

function close() {
    emit('update:open', false);
}
</script>

<style scoped>
.detail-section {
  margin-bottom: 24px;
}

.detail-section h3 {
  margin: 0 0 12px;
  font-weight: 600;
}

.over-applied {
  margin-top: 12px;
}

pre {
  max-width: 560px;
  margin: 8px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
