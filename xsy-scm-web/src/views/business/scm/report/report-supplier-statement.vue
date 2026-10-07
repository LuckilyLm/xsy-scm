<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="freeze">
    <a-form-item label="供应商" required><SupplierSelect v-model:value="supplierId" width="200px"/></a-form-item>
    <a-form-item label="采购仓库"><WarehouseSelect v-model:value="warehouseId" width="190px"/></a-form-item>
    <a-form-item label="对账期间" required><ReportDateRangePicker v-model:value="dateRange"/></a-form-item>
    <a-form-item>
      <a-space>
        <a-button v-privilege="PERM.SUPPLIER_STATEMENT_FREEZE" type="primary" :loading="busy" @click="freeze">生成并冻结新版本</a-button>
        <a-button v-privilege="PERM.SUPPLIER_STATEMENT_QUERY" :disabled="!supplierId" @click="loadHistory">查看历史版本</a-button>
      </a-space>
    </a-form-item>
  </a-form>
  <a-alert v-if="error" class="statement-note" type="error" show-icon :message="error"/>
  <a-card title="历史对账版本（当前操作人）" size="small" :bordered="false" class="statement-card">
    <a-table :columns="historyColumns" :data-source="history" row-key="id" size="small" :pagination="{pageSize: 10}" :loading="busy">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex === 'action'"><a-button type="link" @click="openVersion(record.id)">查看冻结内容</a-button></template>
        <template v-else-if="column.dataIndex === 'partialScope'">{{ record.partialScope ? '部分应付范围' : '完整供应商范围' }}</template>
      </template>
    </a-table>
  </a-card>
  <a-card v-if="selected" :title="`供应商对账单 · 版本 #${selected.id}`" size="small" :bordered="false">
    <template #extra>
      <a-space>
        <a-button v-privilege="PERM.EXPORT" @click="exportVersion">导出 Excel</a-button>
        <a-button @click="printVersion">打印 / 保存 PDF</a-button>
      </a-space>
    </template>
    <div ref="printArea" class="statement-print">
      <h2>{{ selected.supplierName }} · 供应商对账单</h2>
      <p>版本 #{{ selected.id }}　期间 {{ selected.startDate }} 至 {{ selected.endDate }}　冻结时间 {{ selected.generatedAt }}</p>
      <a-alert v-if="selected.partialScope" type="warning" show-icon class="statement-note"
               message="仅含授权仓库/采购员的应付；付款是全供应商金额，不能据此核算未分配资金或完整应付"/>
      <a-descriptions bordered size="small" :column="{xs: 1, sm: 2, lg: 4}">
        <a-descriptions-item label="期初净应付">{{ moneyText(selected.openingPayable) }}</a-descriptions-item>
        <a-descriptions-item label="本期新增应付">{{ moneyText(selected.payableIncrease) }}</a-descriptions-item>
        <a-descriptions-item label="本期红字">{{ moneyText(selected.payableRed) }}</a-descriptions-item>
        <a-descriptions-item label="本期核销净额">{{ moneyText(selected.writeOffNet) }}</a-descriptions-item>
        <a-descriptions-item label="期末净应付">{{ moneyText(selected.closingPayable) }}</a-descriptions-item>
        <a-descriptions-item label="期初未分配付款">{{ moneyText(selected.openingUnallocated) }}</a-descriptions-item>
        <a-descriptions-item label="全供应商付款净额">{{ moneyText(selected.paymentNet) }}</a-descriptions-item>
        <a-descriptions-item label="期末未分配付款">{{ moneyText(selected.closingUnallocated) }}</a-descriptions-item>
      </a-descriptions>
      <a-table class="statement-lines" :columns="lineColumns" :data-source="selected.items ?? []" row-key="lineNo"
               size="small" bordered :pagination="{pageSize: 30}" :scroll="{x: 1180}">
        <template #bodyCell="{record,column}">
          <template v-if="column.dataIndex === 'factType'">{{ factLabels[record.factType] ?? record.factType }}</template>
          <template v-else-if="moneyColumns.includes(String(column.dataIndex))">{{ moneyText(record[column.dataIndex]) }}</template>
        </template>
      </a-table>
    </div>
  </a-card>
</template>
<script setup lang="ts">
import {ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {supplierStatementApi, type SupplierStatement, type SupplierStatementLine} from '/@/api/business/scm/supplier-statement-api';
import type {ReportId} from './report-types';
import {defaultDateRange, rangeOverLimitError, type DateRange} from './report-model';
import {moneyText} from '../inventory/inventory-model';
import {reportError} from './report-errors';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';

const supplierId = ref<ReportId>(), warehouseId = ref<ReportId>();
const dateRange = ref<DateRange>(defaultDateRange());

const selected = ref<SupplierStatement>(), history = ref<SupplierStatement[]>([]);
const printArea = ref<HTMLElement>(), busy = ref(false), error = ref('');
watch(supplierId, () => { selected.value = undefined; history.value = []; });
const factLabels: Record<string, string> = {
  PAYABLE: '确认收货应付', RED: '手工红字', PAYMENT: '实际付款', PAYMENT_REVERSE: '反向付款',
  WRITE_OFF: '付款核销', WRITE_OFF_REVERSE: '反向核销',
};
const moneyColumns = ['payableDelta', 'paymentDelta', 'writeOffDelta', 'payableBalance'];
const historyColumns: TableColumnsType<SupplierStatement> = [
  {title: '版本', dataIndex: 'id'}, {title: '供应商', dataIndex: 'supplierName'},
  {title: '起始日', dataIndex: 'startDate'}, {title: '截止日', dataIndex: 'endDate'},
  {title: '范围', dataIndex: 'partialScope'}, {title: '冻结时间', dataIndex: 'generatedAt'},
  {title: '操作', dataIndex: 'action', align: 'center', width: 130},
];
const lineColumns: TableColumnsType<SupplierStatementLine> = [
  {title: '业务时间', dataIndex: 'eventAt', width: 170}, {title: '类型', dataIndex: 'factType', width: 120},
  {title: '单号', dataIndex: 'documentNo', width: 170}, {title: '关联单号', dataIndex: 'relatedNo', width: 170},
  ...moneyColumns.map((key, index) => ({title: ['应付变动', '付款变动', '核销变动', '滚动净应付'][index], dataIndex: key, width: 135, align: 'right' as const})),
];
async function loadHistory() {
  const id = supplierId.value;
  if (!id) return;
  busy.value = true; error.value = '';
  try {
    const rows = (await supplierStatementApi.history(id)).data ?? [];
    if (supplierId.value === id) history.value = rows;
  } catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function freeze() {
  const id = supplierId.value;
  if (!id || !dateRange.value?.[0] || !dateRange.value?.[1]) {
    error.value = '请选择供应商和完整对账期间'; return;
  }
  const limitError = rangeOverLimitError(dateRange.value);
  if (limitError) { error.value = limitError; return; }
  busy.value = true; error.value = '';
  try {
    const version = (await supplierStatementApi.freeze({supplierId: id, warehouseId: warehouseId.value,
      startDate: dateRange.value[0], endDate: dateRange.value[1]})).data;
    if (supplierId.value === id) { selected.value = version; await loadHistory(); }
  } catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function openVersion(id: ReportId) {
  const expectedSupplierId = supplierId.value;
  busy.value = true; error.value = '';
  try {
    const version = (await supplierStatementApi.detail(id)).data;
    if (supplierId.value === expectedSupplierId && String(version.supplierId) === String(expectedSupplierId)) {
      selected.value = version;
    }
  } catch (e) { error.value = reportError(e); }
  finally { busy.value = false; }
}
async function exportVersion() {
  if (selected.value) await supplierStatementApi.export(selected.value.id);
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
  // Vue has already escaped all external text; populate the complete row set using textContent.
  doc.body.appendChild(printArea.value.cloneNode(true));
  doc.body.querySelector('.statement-lines')?.remove();
  const table = doc.createElement('table');
  const headings = ['业务时间', '类型', '单号', '关联单号', '应付变动', '付款变动', '核销变动', '滚动净应付'];
  const header = table.insertRow();
  headings.forEach(label => { const th = doc.createElement('th'); th.textContent = label; header.appendChild(th); });
  for (const row of selected.value.items ?? []) {
    const cells = [row.eventAt, factLabels[row.factType] ?? row.factType, row.documentNo,
      row.relatedNo ?? '', ...moneyColumns.map(key => moneyText(row[key as keyof SupplierStatementLine] as string))];
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
