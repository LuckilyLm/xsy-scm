<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="freeze">
    <a-form-item label="结算方" required><CustomerSelect v-model:value="settlementCustomerId" width="200px"/></a-form-item>
    <a-form-item label="原客户"><CustomerSelect v-model:value="customerId" width="200px"/></a-form-item>
    <a-form-item label="对账期间" required><ReportDateRangePicker v-model:value="dateRange"/></a-form-item>
    <a-form-item>
      <a-space>
        <a-button v-privilege="PERM.CUSTOMER_STATEMENT_FREEZE" type="primary" :loading="busy" @click="freeze">生成并冻结新版本</a-button>
        <a-button v-privilege="PERM.CUSTOMER_STATEMENT_QUERY" :disabled="!settlementCustomerId" @click="loadHistory">查看历史版本</a-button>
      </a-space>
    </a-form-item>
  </a-form>
  <a-alert class="statement-note" type="info" show-icon message="应收、收款与核销分列，冻结后不随晚录事实改写"
           description="期末净应收 = 期初净应收 + 新增应收 − 红字 − 有效核销；收款不是再次抵扣应收，退款实际付款单列。集团按财务事实中的历史结算方归集；原客户筛选或授权受限时标记为部分范围，未分配资金不展示，期末净应收仍仅代表可见事实。完整范围的净额可能为负。打印可在浏览器选择另存为 PDF。"/>
  <a-alert v-if="error" class="statement-note" type="error" show-icon :message="error"/>
  <a-card title="历史对账版本（当前操作人）" size="small" :bordered="false" class="statement-card">
    <a-table :columns="historyColumns" :data-source="history" row-key="id" size="small" :pagination="{pageSize: 10}" :loading="busy">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex === 'action'"><a-button type="link" @click="openVersion(record.id)">查看冻结内容</a-button></template>
        <template v-else-if="column.dataIndex === 'partialScope'">{{ record.partialScope ? '授权范围内 / 非完整集团' : '完整结算方' }}</template>
      </template>
    </a-table>
  </a-card>
  <a-card v-if="selected" :title="`客户对账单 · 版本 #${selected.id}`" size="small" :bordered="false">
    <template #extra>
      <a-space>
        <a-button v-privilege="PERM.EXPORT" @click="exportVersion">导出 Excel</a-button>
        <a-button @click="printVersion">打印 / 保存 PDF</a-button>
      </a-space>
    </template>
    <div ref="printArea" class="statement-print">
      <h2>{{ selected.settlementCustomerName }} · 客户对账单</h2>
      <p>版本 #{{ selected.id }}　期间 {{ selected.startDate }} 至 {{ selected.endDate }}　冻结时间 {{ selected.generatedAt }}</p>
      <a-alert v-if="selected.partialScope" type="warning" show-icon message="仅涵盖当前授权及原客户筛选范围，不能作为完整集团对账额" class="statement-note"/>
      <a-descriptions bordered size="small" :column="{xs: 1, sm: 2, lg: 4}">
        <a-descriptions-item label="期初净应收">{{ moneyText(selected.openingReceivable) }}</a-descriptions-item>
        <a-descriptions-item label="本期新增应收">{{ moneyText(selected.receivableIncrease) }}</a-descriptions-item>
        <a-descriptions-item label="本期红字">{{ moneyText(selected.receivableRed) }}</a-descriptions-item>
        <a-descriptions-item label="本期核销净额">{{ moneyText(selected.writeOffNet) }}</a-descriptions-item>
        <a-descriptions-item label="期末净应收">{{ moneyText(selected.closingReceivable) }}</a-descriptions-item>
        <a-descriptions-item label="本期实收净额">{{ moneyText(selected.receiptNet) }}</a-descriptions-item>
        <a-descriptions-item label="期末未分配资金">{{ moneyText(selected.closingUnallocated) }}</a-descriptions-item>
        <a-descriptions-item label="本期实退净额">{{ moneyText(selected.refundNet) }}</a-descriptions-item>
      </a-descriptions>
      <a-table class="statement-lines" :columns="lineColumns" :data-source="selected.items ?? []" row-key="lineNo"
               size="small" bordered :pagination="{pageSize: 30}" :scroll="{x: 1370}">
        <template #bodyCell="{record,column}">
          <template v-if="column.dataIndex === 'factType'">{{ factLabels[record.factType] ?? record.factType }}</template>
          <template v-else-if="moneyColumns.includes(String(column.dataIndex))">{{ moneyText(record[column.dataIndex]) }}</template>
        </template>
      </a-table>
    </div>
  </a-card>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerStatementApi, type CustomerStatement, type CustomerStatementLine} from '/@/api/business/scm/customer-statement-api';
import type {ReportId} from './report-types';
import {defaultDateRange, rangeOverLimitError, type DateRange} from './report-model';
import {moneyText} from '../inventory/inventory-model';
import {reportError} from './report-errors';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';

const settlementCustomerId = ref<ReportId>(), customerId = ref<ReportId>();
const dateRange = ref<DateRange>(defaultDateRange());
const selected = ref<CustomerStatement>(), history = ref<CustomerStatement[]>([]);
const printArea = ref<HTMLElement>(), busy = ref(false), error = ref('');
const factLabels: Record<string, string> = {
  RECEIVABLE: '销售应收', RED: '退货红字', RECEIPT: '实际收款', RECEIPT_REVERSE: '反向收款',
  WRITE_OFF: '核销分配', WRITE_OFF_REVERSE: '反向核销', REFUND: '实际退款', REFUND_REVERSE: '反向退款',
};
const moneyColumns = ['receivableDelta', 'receiptDelta', 'writeOffDelta', 'refundDelta', 'receivableBalance'];
const historyColumns: TableColumnsType<CustomerStatement> = [
  {title: '版本', dataIndex: 'id'}, {title: '结算方', dataIndex: 'settlementCustomerName'},
  {title: '起始日', dataIndex: 'startDate'}, {title: '截止日', dataIndex: 'endDate'},
  {title: '范围', dataIndex: 'partialScope'}, {title: '冻结时间', dataIndex: 'generatedAt'},
  {title: '操作', dataIndex: 'action', align: 'center', width: 130},
];
const lineColumns: TableColumnsType<CustomerStatementLine> = [
  {title: '业务时间', dataIndex: 'eventAt', width: 175}, {title: '类型', dataIndex: 'factType', width: 110},
  {title: '单号', dataIndex: 'documentNo', width: 170}, {title: '关联单号', dataIndex: 'relatedNo', width: 170},
  {title: '原客户', dataIndex: 'customerName', width: 150},
  ...moneyColumns.map((key, index) => ({title: ['应收变动', '收款变动', '核销变动', '退款变动', '滚动净应收'][index], dataIndex: key, width: 135, align: 'right' as const})),
];
async function loadHistory() {
  if (!settlementCustomerId.value) return;
  busy.value = true; error.value = '';
  try { history.value = (await customerStatementApi.history(settlementCustomerId.value)).data ?? []; }
  catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function freeze() {
  if (!settlementCustomerId.value || !dateRange.value?.[0] || !dateRange.value?.[1]) {
    error.value = '请选择结算方和完整对账期间'; return;
  }
  const limitError = rangeOverLimitError(dateRange.value);
  if (limitError) { error.value = limitError; return; }
  busy.value = true; error.value = '';
  try {
    selected.value = (await customerStatementApi.freeze({settlementCustomerId: settlementCustomerId.value,
      customerId: customerId.value, startDate: dateRange.value[0], endDate: dateRange.value[1]})).data;
    await loadHistory();
  } catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function openVersion(id: ReportId) {
  busy.value = true; error.value = '';
  try { selected.value = (await customerStatementApi.detail(id)).data; }
  catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function exportVersion() {
  if (selected.value) await customerStatementApi.export(selected.value.id);
}
function printVersion() {
  if (!selected.value || !printArea.value) return;
  const frame = document.createElement('iframe');
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0;';
  document.body.appendChild(frame);
  const doc = frame.contentDocument;
  if (!doc || !frame.contentWindow) { frame.remove(); return; }
  doc.open();
  doc.write('<!doctype html><html><head><meta charset="utf-8"><style>body{font:12px Arial,sans-serif;color:#222;padding:20px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #aaa;padding:5px}h2{text-align:center}</style></head><body></body></html>');
  doc.close();
  // Clone the already escaped Vue DOM instead of injecting raw customer names into HTML.
  doc.body.appendChild(printArea.value.cloneNode(true));
  doc.body.querySelector('.statement-lines')?.remove();
  const table = doc.createElement('table');
  const headings = ['业务时间', '类型', '单号', '关联单号', '原客户', '应收变动', '收款变动', '核销变动', '退款变动', '滚动净应收'];
  const header = table.insertRow();
  headings.forEach(label => { const th = doc.createElement('th'); th.textContent = label; header.appendChild(th); });
  for (const row of selected.value.items ?? []) {
    const cells = [row.eventAt, factLabels[row.factType] ?? row.factType, row.documentNo,
      row.relatedNo ?? '', row.customerName, ...moneyColumns.map(key => moneyText(row[key as keyof CustomerStatementLine] as string))];
    const tr = table.insertRow();
    cells.forEach(value => { const td = tr.insertCell(); td.textContent = value; });
  }
  doc.body.appendChild(table);
  frame.contentWindow.focus();
  frame.contentWindow.print();
  window.setTimeout(() => frame.remove(), 1000);
}
</script>
<style scoped>
.statement-note, .statement-card, .statement-lines { margin-bottom: 16px; }
.statement-print h2 { text-align: center; }
</style>
