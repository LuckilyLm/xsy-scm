<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent="search">
    <a-row class="smart-query-form-row">
      <a-form-item label="分析日期"><ReportDateRangePicker v-model:value="dateRange"/></a-form-item>
      <a-form-item label="分析维度">
        <a-select v-model:value="filters.dimension" :options="dimensionOptions" style="width: 130px"/>
      </a-form-item>
      <a-form-item label="客户"><CustomerSelect v-model:value="filters.customerId" width="190px"/></a-form-item>
      <a-form-item label="销售员"><EmployeeSelect v-model:value="filters.sellerId" width="150px"/></a-form-item>
      <a-form-item label="仓库"><WarehouseSelect v-model:value="filters.warehouseId" width="170px"/></a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" :loading="loading" v-privilege="PERM.FINANCE_PROFIT_QUERY" @click="search">查询</a-button>
          <a-button @click="reset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-row>
    <a-row class="smart-query-form-row">
      <a-form-item label="商品分类">
        <CategorySelect v-model:value="filters.categoryId" :categories="categories" style="width: 220px"/>
      </a-form-item>
      <a-form-item label="商品 / 单号"><a-input v-model:value="filters.keyword" allow-clear placeholder="商品名、SKU 编码或订单号" @pressEnter="search"/></a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon class="profit-alert">
    <template #action><a-button @click="load">重试</a-button></template>
  </a-alert>
  <a-alert type="info" show-icon class="profit-alert"
           message="毛利 = Finance 应收收入 − 商品销售成本 − 促销赠品成本"
           description="收入按应收/红字应收事件时点，成本按销售出库流水时点；退货红字先冲收入，实物接收后按原出库仓及原成本冲回。未接收红字仅全仓授权可见，后续接收会改变历史期间的仓库分组；受限仓库结果不保证与全仓红字总额一致。跨期收入与成本不按订单配比，历史成本缺失时不按零补算。促销赠品成本单独一列：赠品不减收入，但它的出库成本是订单的履约成本，所以要从毛利里扣。"/>
  <a-alert v-if="(summary?.costMissingCount ?? 0) > 0" type="warning" show-icon class="profit-alert"
           :message="`有 ${summary?.costMissingCount} 行历史销售成本缺失，毛利和毛利率暂不完整`"
           description="销售成本金额仅显示已知部分；毛利相关指标显示为 —，请先核对对应出库流水的成本事实。"/>

  <a-row :gutter="12" class="profit-kpis">
    <a-col :xs="24" :sm="12" :lg="8"><a-card size="small"><div class="kpi-label">销售收入</div><div class="kpi-value">{{ moneyText(summary?.revenueAmount) }}</div></a-card></a-col>
    <a-col :xs="24" :sm="12" :lg="8"><a-card size="small"><div class="kpi-label">商品销售成本（净额）</div><div class="kpi-value">{{ moneyText(summary?.salesCostAmount) }}</div></a-card></a-col>
    <a-col :xs="24" :sm="12" :lg="8"><a-card size="small"><div class="kpi-label">促销赠品成本</div><div class="kpi-value">{{ moneyText(summary?.giftCostAmount) }}</div></a-card></a-col>
    <a-col :xs="24" :sm="12" :lg="8"><a-card size="small"><div class="kpi-label">销售毛利</div><div class="kpi-value">{{ moneyText(summary?.grossProfit) }}</div></a-card></a-col>
    <a-col :xs="24" :sm="12" :lg="8"><a-card size="small"><div class="kpi-label">毛利率</div><div class="kpi-value">{{ rateText(summary?.grossMarginRate) }}</div></a-card></a-col>
  </a-row>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="PERM.EXPORT" :disabled="!applied || loading || !!error" :loading="exporting" @click="exportRows">导出当前结果</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">汇总卡片按全部筛选结果计算，不是当前页小计。</a-typography-text>
      </div>
    </a-row>
    <a-table id="scm-report-finance-profit-table" size="small" :data-source="rows" :columns="columns"
             :row-key="rowKey" bordered :pagination="false" :loading="loading" :scroll="{x: 1250}"
             :locale="{emptyText: '当前筛选范围没有销售毛利事实'}">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'bizDate'">{{ record.bizDate || '—' }}</template>
        <template v-else-if="column.dataIndex === 'revenueAmount'">{{ moneyText(record.revenueAmount) }}</template>
        <template v-else-if="column.dataIndex === 'salesCostAmount'">{{ moneyText(record.salesCostAmount) }}</template>
        <template v-else-if="column.dataIndex === 'giftCostAmount'">{{ moneyText(record.giftCostAmount) }}</template>
        <template v-else-if="column.dataIndex === 'grossProfit'">{{ moneyText(record.grossProfit) }}</template>
        <template v-else-if="column.dataIndex === 'grossMarginRate'">{{ rateText(record.grossMarginRate) }}</template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination v-model:current="pageNum" v-model:page-size="pageSize" :total="total"
                    show-size-changer show-quick-jumper :show-total="(n:number) => `共 ${n} 条`" @change="load"/>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {financeProfitApi} from '/@/api/business/scm/finance-profit-api';
import type {FinanceProfitDimension, FinanceProfitQuery, FinanceProfitRow, FinanceProfitSummary} from '/@/api/business/scm/finance-profit-api';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import type {ProductCategory} from '/@/types/business/scm/product';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {SCM_REPORT_PERMISSION as PERM} from '/@/constants/business/scm/report-const';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import {buildReportQuery, defaultDateRange, rangeOverLimitError} from './report-model';
import type {DateRange} from './report-model';
import {moneyText} from '../inventory/inventory-model';
import {reportError} from './report-errors';

const dimensionOptions: Array<{value: FinanceProfitDimension; label: string}> = [
  {value: 'DAY', label: '按日期'}, {value: 'CUSTOMER', label: '按客户'}, {value: 'PRODUCT', label: '按商品'},
  {value: 'CATEGORY', label: '按分类'}, {value: 'SELLER', label: '按销售员'}, {value: 'WAREHOUSE', label: '按仓库'},
];
const dateRange = ref<DateRange>();
const filters = reactive<Omit<FinanceProfitQuery, 'startDate' | 'endDate' | 'pageNum' | 'pageSize'>>({dimension: 'PRODUCT'});
const pageNum = ref(1), pageSize = ref(20), total = ref(0);
const rows = ref<FinanceProfitRow[]>([]), summary = ref<FinanceProfitSummary>();
const categories = ref<ProductCategory[]>([]);
const loading = ref(false), exporting = ref(false), error = ref('');
const applied = ref<FinanceProfitQuery>();
let requestId = 0;

const columns: TableColumnsType<FinanceProfitRow> = [
  {title: '日期', dataIndex: 'bizDate', width: 120},
  {title: '分析对象', dataIndex: 'dimensionName', width: 210},
  {title: '编码', dataIndex: 'dimensionCode', width: 170},
  {title: '销售收入', dataIndex: 'revenueAmount', align: 'right', width: 150},
  {title: '商品销售成本（净额）', dataIndex: 'salesCostAmount', align: 'right', width: 180},
  {title: '促销赠品成本', dataIndex: 'giftCostAmount', align: 'right', width: 150},
  {title: '销售毛利', dataIndex: 'grossProfit', align: 'right', width: 150},
  {title: '毛利率', dataIndex: 'grossMarginRate', align: 'right', width: 130},
  {title: '缺失成本流水数', dataIndex: 'costMissingCount', align: 'right', width: 150},
];

function queryPayload(): FinanceProfitQuery {
  return buildReportQuery<FinanceProfitQuery>(dateRange.value, {...filters}, {pageNum: pageNum.value, pageSize: pageSize.value});
}

function rowKey(row: FinanceProfitRow): string {
  return `${row.bizDate ?? ''}:${row.dimensionId ?? row.dimensionCode ?? row.dimensionName}`;
}

function rateText(value?: string | null): string {
  return value == null ? '—' : `${value}%`;
}

async function load() {
  const overLimit = rangeOverLimitError(dateRange.value);
  if (overLimit) { error.value = overLimit; rows.value = []; summary.value = undefined; return; }
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) { error.value = '请选择完整日期范围'; return; }
  const id = ++requestId;
  const payload = queryPayload();
  loading.value = true;
  error.value = '';
  rows.value = [];
  summary.value = undefined;
  total.value = 0;
  try {
    const [page, totals] = await Promise.all([financeProfitApi.query(payload), financeProfitApi.summary(payload)]);
    if (id !== requestId) return;
    rows.value = page.data.list ?? [];
    total.value = page.data.total ?? 0;
    summary.value = totals.data;
    applied.value = payload;
  } catch (e) {
    if (id === requestId) error.value = reportError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search() {
  pageNum.value = 1;
  void load();
}

function reset() {
  Object.assign(filters, {dimension: 'PRODUCT', customerId: undefined, sellerId: undefined, skuId: undefined,
    categoryId: undefined, warehouseId: undefined, keyword: undefined});
  dateRange.value = defaultDateRange();
  pageNum.value = 1;
  pageSize.value = 20;
  void load();
}

async function exportRows() {
  if (!applied.value || exporting.value) return;
  exporting.value = true;
  try { await financeProfitApi.export(applied.value); }
  catch (e) { error.value = reportError(e); }
  finally { exporting.value = false; }
}

onMounted(async () => {
  dateRange.value = defaultDateRange();
  try { categories.value = (await productCategoryApi.tree()).data ?? []; }
  catch { categories.value = []; }
  void load();
});
</script>

<style scoped>
.profit-alert { margin-bottom: 14px; }
.profit-kpis { margin-bottom: 14px; }
.kpi-label { color: rgba(0, 0, 0, .65); font-size: 13px; }
.kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; font-variant-numeric: tabular-nums; }
</style>
