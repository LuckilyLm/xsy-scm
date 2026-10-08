<template>
  <a-drawer :open="open" :title="title" :width="scmDrawerWidth('xl')" :destroy-on-close="true" @close="close">
    <a-spin class="finance-detail-content" :spinning="loading">
      <template v-if="detail">
        <!-- 1. 单据概要：只放「这是什么单」。金额不在这里，见「金额组成」。 -->
        <section class="detail-section">
          <h3>单据概要</h3>
          <a-descriptions bordered size="small" :column="{xs: 1, sm: 2}">
            <a-descriptions-item label="单号">{{ headerNo }}</a-descriptions-item>
            <a-descriptions-item label="方向">{{ entryTypeText(header.entryType) }}</a-descriptions-item>
            <a-descriptions-item label="业务时点">{{ dateTimeText(header.eventAt ?? header.receivedAt ?? header.paidAt) }}</a-descriptions-item>
            <a-descriptions-item label="原因">{{ header.reason || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="header.reverseOfNo" label="原单号">{{ header.reverseOfNo }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <!-- 2. 对象信息：跟谁发生关系、挂在哪张业务单上。 -->
        <section class="detail-section">
          <h3>对象信息</h3>
          <a-descriptions bordered size="small" :column="{xs: 1, sm: 2}">
            <a-descriptions-item label="往来方">{{ partyName }}</a-descriptions-item>
            <a-descriptions-item v-if="header.settlementCustomerName" label="单据结算方">{{ header.settlementCustomerName }}</a-descriptions-item>
            <a-descriptions-item label="关联单号">{{ linkedNo }}</a-descriptions-item>
            <a-descriptions-item v-if="isAccount" label="冻结到期日">{{ header.dueDate || '未设置（不推算历史账期）' }}</a-descriptions-item>
          </a-descriptions>

          <!-- 3. 金额组成：金额摘要比 ID、编码更突出，所以单独成段并用数值强调。 -->
          <h3 class="detail-section--nested">金额组成</h3>
          <div class="amount-grid">
            <div class="amount-cell">
              <span class="amount-cell__label">{{ isAccount ? '净应收 / 净应付' : '有效金额' }}</span>
              <span class="amount-cell__value scm-money">{{ moneyText(isAccount ? header.netAmount : header.effectiveAmount) }}</span>
            </div>
            <div class="amount-cell">
              <span class="amount-cell__label">金额</span>
              <span class="amount-cell__value scm-money">{{ moneyText(header.amount) }}</span>
            </div>
            <div class="amount-cell">
              <span class="amount-cell__label">已核销</span>
              <span class="amount-cell__value scm-money">{{ moneyText(isAccount ? header.writtenOffAmount : header.usedAmount) }}</span>
            </div>
            <div class="amount-cell">
              <span class="amount-cell__label">{{ isAccount ? '未核销' : '待核销' }}</span>
              <span class="amount-cell__value scm-money">{{ moneyText(isAccount ? header.openAmount : header.pendingWriteOffAmount) }}</span>
            </div>
          </div>
          <a-descriptions v-if="!isAccount && walletFunding" size="small" :column="1" class="smart-margin-top10">
            <a-descriptions-item label="资金用途">已转钱包权益，通过余额支付结算订单</a-descriptions-item>
          </a-descriptions>
          <a-alert v-if="header.overAppliedAmount && header.overAppliedAmount !== '0.0000'"
                   class="over-applied" type="warning" show-icon :message="`超额核销 ${moneyText(header.overAppliedAmount)}，不代表已退款或钱包余额。`"/>
          <a-tag v-if="isReceivable && header.overAppliedAmount && header.overAppliedAmount !== '0.0000'"
                 color="orange">超额核销待处理</a-tag>
          <!-- 净应收为负是这张单的数据异常，不是某次请求失败：要留在纸上供对账复核，所以不走 toast。 -->
          <a-alert v-if="header.netAmount?.startsWith('-')" class="over-applied" type="warning" show-icon
                   message="净应收为负数，请结合红字和核销记录核对。"/>
        </section>

        <!-- 4. 来源单据：这张财务单是从哪张业务单派生出来的明细。 -->
        <section v-if="receivableItems.length || payableItems.length" class="detail-section">
          <h3>来源单据</h3>
          <a-table v-if="isReceivable" size="small" :data-source="receivableItems" :columns="receivableColumns"
                   row-key="receivableItemId" :pagination="false" :scroll="{x:760}"/>
          <a-table v-else size="small" :data-source="payableItems" :columns="payableColumns"
                   row-key="payableItemId" :pagination="false" :scroll="{x:760}"/>
        </section>

        <!-- 5. 核销 / 退款 / 红字关系：把互相抵消的事实放在一起，便于对账。 -->
        <section v-if="relationText || writeOffs.length || redEntries.length" class="detail-section">
          <h3>核销 / 红字关系</h3>
          <a-alert v-if="relationText" class="relation-alert" type="info" show-icon :message="relationText"/>
          <h4 v-if="writeOffs.length" class="detail-subtitle">核销记录</h4>
          <a-table v-if="writeOffs.length" size="small" :data-source="writeOffs" :columns="writeOffColumns"
                   row-key="writeOffId" :pagination="false" :scroll="{x:900}"/>
          <h4 v-if="redEntries.length" class="detail-subtitle">红字关联</h4>
          <a-table v-if="redEntries.length" size="small" :data-source="redEntries" :columns="redColumns"
                   :row-key="redEntryKey" :pagination="false"/>
        </section>

        <!-- 6. 流水记录：财务操作审计流水。 -->
        <section v-if="operationLogs.length" class="detail-section">
          <h3>流水记录</h3>
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

        <section class="detail-section">
          <h3>系统信息</h3>
          <a-descriptions bordered size="small" :column="{xs: 1, sm: 2}">
            <a-descriptions-item label="创建时间">{{ dateTimeText(header.createdAt) }}</a-descriptions-item>
            <a-descriptions-item label="创建人">{{ header.createdBy || '—' }}</a-descriptions-item>
            <a-descriptions-item label="更新时间">{{ dateTimeText(header.updatedAt) }}</a-descriptions-item>
            <a-descriptions-item label="更新人">{{ header.updatedBy || '—' }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <a-empty v-if="!writeOffs.length && !operationLogs.length && !redEntries.length" description="暂无核销或操作记录"/>
      </template>
      <a-empty v-else-if="!loading" description="暂无单据详情"/>
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
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

type FinanceDetail = FinanceReceivableDetail | FinancePayableDetail | FinanceReceiptDetail | FinancePaymentDetail;
const props = defineProps<{open: boolean; loading: boolean; kind: 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT'; detail?: FinanceDetail | null}>();
const emit = defineEmits<{(event: 'update:open', value: boolean): void}>();
const detail = computed(() => props.detail ?? null);
const isReceivable = computed(() => props.kind === 'RECEIVABLE');
const isPayable = computed(() => props.kind === 'PAYABLE');
const walletFunding = computed(() => !!(detail.value && 'receipt' in detail.value && detail.value.receipt.walletFunding));
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
    // 毛额 − 优惠 = 净额：三列一起看才能解释「这行为什么只记这么多应收」
    {title: '毛额', dataIndex: 'grossAmount', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '订单优惠', dataIndex: 'discountAmount', align: 'right', customRender: ({text}) => moneyText(text)},
    {title: '净额', dataIndex: 'amount', align: 'right', customRender: ({text}) => moneyText(text)},
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

function redEntryKey(row: FinanceReceivable | FinancePayable) {
    return 'receivableId' in row ? row.receivableId : row.payableId;
}
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
.finance-detail-content {
  min-height: 160px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h3 {
  margin: 0 0 12px;
  font-weight: 600;
}

/* 金额组成是同一段里的第二个小标题（要求金额比 ID、编码更突出） */
.detail-section--nested {
  margin-top: 20px;
}

/* 对象信息段里的金额组：四格摘要，数值比标签大一号 */
.amount-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.amount-cell {
  min-width: 0;
  background: var(--scm-fill, #fafafa);
  border-radius: 6px;
  padding: 10px 12px;
}

.amount-cell__label {
  color: var(--scm-text-secondary);
  display: block;
  font-size: 12px;
}

.amount-cell__value {
  overflow-wrap: anywhere;
  display: block;
  font-size: 18px;
  line-height: 1.5;
  margin-top: 4px;
}

.detail-subtitle {
  color: var(--scm-text-secondary);
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 8px;
}

.relation-alert {
  margin-bottom: 12px;
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
