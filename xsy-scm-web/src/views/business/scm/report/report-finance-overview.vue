<template>
  <section class="report-finance-overview">
    <a-form class="smart-query-form" layout="inline" @submit.prevent="queryAll">
      <a-row class="smart-query-form-row">
        <a-form-item label="业务日期" class="smart-query-form-item">
          <ReportDateRangePicker v-model:value="dateRange"/>
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-button-group>
            <a-button type="primary" v-privilege="PERM.FINANCE_QUERY" @click="queryAll">查询</a-button>
            <a-button @click="resetQuery">重置</a-button>
          </a-button-group>
          <a-button class="smart-margin-left10" v-privilege="PERM.EXPORT" :loading="exportingOverview"
                    @click="exportOverview">导出概览</a-button>
          <ReportNote title="口径说明" :sections="overviewNoteSections"/>
        </a-form-item>
      </a-row>
    </a-form>

    <a-alert v-if="rangeError" class="report-error" type="warning" show-icon :message="rangeError"/>
    <a-alert v-if="overviewError" class="report-error" type="error" show-icon :message="overviewError">
      <template #action><a-button @click="loadOverview">重试</a-button></template>
    </a-alert>

    <a-row :gutter="[12, 12]" class="smart-margin-top10">
      <a-col v-for="card in kpiCards" :key="card.label" :xs="24" :sm="12" :lg="8">
        <ReportKpiCard :label="card.label" :value="card.value" :hint="card.hint"/>
      </a-col>
    </a-row>

    <a-card size="small" :bordered="false" class="smart-margin-top10">
      <a-tabs v-model:active-key="activeTab" @change="onTabChange">
        <a-tab-pane key="receivable" tab="应收明细（期末）">
          <a-row class="smart-table-btn-block">
            <div class="smart-table-operate-block">
              <a-input v-model:value="keyword" allow-clear placeholder="应收单号 / 订单号 / 客户" @press-enter="searchDetails"/>
              <a-button type="primary" v-privilege="PERM.FINANCE_QUERY" @click="searchDetails">查询</a-button>
              <a-button @click="clearKeyword">清除关键字</a-button>
            </div>
            <div class="smart-table-setting-block">
              <a-button v-privilege="PERM.EXPORT" :loading="exportingReceivable"
                        @click="exportReceivables">导出应收</a-button>
              <div class="finance-table-operator">
                <TableOperator v-model="receivableColumns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_FINANCE_RECEIVABLE"
                               :refresh="loadReceivables"/>
              </div>
            </div>
          </a-row>
          <a-alert v-if="receivableView.error" class="report-error" type="error" show-icon
                   :message="receivableView.error">
            <template #action><a-button @click="loadReceivables">重试</a-button></template>
          </a-alert>
          <a-table :id="SCM_REPORT_TABLE_ID.FINANCE_RECEIVABLE" class="finance-detail-table" size="small"
                   :data-source="receivableView.rows" :columns="receivableColumns"
                   row-key="receivableId" :loading="receivableView.loading" :pagination="false"
                   bordered :scroll="{x: 1500, y: tableBodyHeight}">
            <template #bodyCell="{column,text}">
              <template v-if="['amount','redAmount','netAmount','writtenOffAmount','openAmount','overAppliedAmount'].includes(column.dataIndex)">
                <span class="scm-money">{{ moneyText(text) }}</span>
                <a-tag v-if="column.dataIndex==='overAppliedAmount' && text && text!=='0.0000'" color="orange">
                  超额核销待处理
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex==='eventAt'">{{ datetime(text) }}</template>
            </template>
          </a-table>
          <div class="finance-detail-mobile-list" role="list" aria-label="应收明细">
            <a-card v-for="row in receivableView.rows" :key="row.receivableId" role="listitem" size="small" class="finance-detail-mobile-card">
              <div class="finance-detail-mobile-heading">
                <div>
                  <span class="finance-detail-mobile-label">应收单号</span>
                  <strong>{{ row.receivableNo }}</strong>
                </div>
                <div class="finance-detail-mobile-balance">
                  <span>期末待收</span>
                  <strong>{{ moneyText(row.openAmount) }}</strong>
                </div>
              </div>
              <dl class="finance-detail-mobile-fields">
                <div><dt>订单号</dt><dd>{{ row.orderNo }}</dd></div>
                <div><dt>客户</dt><dd>{{ row.customerName }}</dd></div>
                <div><dt>原应收金额</dt><dd>{{ moneyText(row.amount) }}</dd></div>
                <div><dt>红字金额</dt><dd>{{ moneyText(row.redAmount) }}</dd></div>
                <div><dt>净应收</dt><dd>{{ moneyText(row.netAmount) }}</dd></div>
                <div><dt>已核销金额</dt><dd>{{ moneyText(row.writtenOffAmount) }}</dd></div>
                <div v-if="row.overAppliedAmount !== '0.0000'" class="finance-detail-mobile-over-applied">
                  <dt>超额核销待处理</dt><dd>{{ moneyText(row.overAppliedAmount) }}</dd>
                </div>
                <div><dt>事件时点</dt><dd>{{ datetime(row.eventAt) }}</dd></div>
              </dl>
            </a-card>
          </div>
          <div class="smart-query-table-page">
            <a-pagination show-size-changer show-quick-jumper :current="receivableView.pageNum"
                          :page-size="receivableView.pageSize" :total="receivableView.total"
                          :show-total="(count:number)=>`共${count}条`"
                          @change="(page:number,size:number)=>changePage(receivableView,page,size,loadReceivables)"/>
          </div>
        </a-tab-pane>

        <a-tab-pane key="payable" tab="应付明细（期末）">
          <a-row class="smart-table-btn-block">
            <div class="smart-table-operate-block">
              <a-input v-model:value="keyword" allow-clear placeholder="应付单号 / 采购单号 / 供应商" @press-enter="searchDetails"/>
              <a-button type="primary" v-privilege="PERM.FINANCE_QUERY" @click="searchDetails">查询</a-button>
              <a-button @click="clearKeyword">清除关键字</a-button>
            </div>
            <div class="smart-table-setting-block">
              <a-button v-privilege="PERM.EXPORT" :loading="exportingPayable" @click="exportPayables">导出应付</a-button>
              <div class="finance-table-operator">
                <TableOperator v-model="payableColumns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_FINANCE_PAYABLE"
                               :refresh="loadPayables"/>
              </div>
            </div>
          </a-row>
          <a-alert v-if="payableView.error" class="report-error" type="error" show-icon :message="payableView.error">
            <template #action><a-button @click="loadPayables">重试</a-button></template>
          </a-alert>
          <a-table :id="SCM_REPORT_TABLE_ID.FINANCE_PAYABLE" class="finance-detail-table" size="small"
                   :data-source="payableView.rows" :columns="payableColumns"
                   row-key="payableId" :loading="payableView.loading" :pagination="false"
                   bordered :scroll="{x: 1500, y: tableBodyHeight}">
            <template #bodyCell="{column,text}">
              <template v-if="['amount','redAmount','netAmount','writtenOffAmount','openAmount','overAppliedAmount'].includes(column.dataIndex)">
                <span class="scm-money">{{ moneyText(text) }}</span>
                <a-tag v-if="column.dataIndex==='overAppliedAmount' && text && text!=='0.0000'" color="orange">
                  超额核销待处理
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex==='eventAt'">{{ datetime(text) }}</template>
            </template>
          </a-table>
          <div class="finance-detail-mobile-list" role="list" aria-label="应付明细">
            <a-card v-for="row in payableView.rows" :key="row.payableId" role="listitem" size="small" class="finance-detail-mobile-card">
              <div class="finance-detail-mobile-heading">
                <div>
                  <span class="finance-detail-mobile-label">应付单号</span>
                  <strong>{{ row.payableNo }}</strong>
                </div>
                <div class="finance-detail-mobile-balance">
                  <span>期末待付</span>
                  <strong>{{ moneyText(row.openAmount) }}</strong>
                </div>
              </div>
              <dl class="finance-detail-mobile-fields">
                <div><dt>采购单号</dt><dd>{{ row.purchaseOrderNo }}</dd></div>
                <div><dt>供应商</dt><dd>{{ row.supplierName }}</dd></div>
                <div><dt>原应付金额</dt><dd>{{ moneyText(row.amount) }}</dd></div>
                <div><dt>红字金额</dt><dd>{{ moneyText(row.redAmount) }}</dd></div>
                <div><dt>净应付</dt><dd>{{ moneyText(row.netAmount) }}</dd></div>
                <div><dt>已核销金额</dt><dd>{{ moneyText(row.writtenOffAmount) }}</dd></div>
                <div v-if="row.overAppliedAmount !== '0.0000'" class="finance-detail-mobile-over-applied">
                  <dt>超额核销待处理</dt><dd>{{ moneyText(row.overAppliedAmount) }}</dd>
                </div>
                <div><dt>事件时点</dt><dd>{{ datetime(row.eventAt) }}</dd></div>
              </dl>
            </a-card>
          </div>
          <div class="smart-query-table-page">
            <a-pagination show-size-changer show-quick-jumper :current="payableView.pageNum"
                          :page-size="payableView.pageSize" :total="payableView.total"
                          :show-total="(count:number)=>`共${count}条`"
                          @change="(page:number,size:number)=>changePage(payableView,page,size,loadPayables)"/>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </section>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {reportFinanceApi} from '/@/api/business/scm/report-api';
import {SCM_REPORT_PERMISSION as PERM, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import ReportNote from '/@/components/business/scm/report-note/index.vue';
import type {ReportNoteSection} from '/@/components/business/scm/report-note/index.vue';
import {reportError} from './report-errors';
import type {
    FinanceOverviewQuery,
    FinancePayableDetailRow,
    FinanceReceivableDetailRow,
    FinanceReportOverview,
    FinanceReportQuery,
} from './report-types';
import {
    buildReportQuery,
    createTabView,
    defaultDateRange,
    rangeOverLimitError,
    type DateRange,
    type TabView,
} from './report-model';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import {moneyText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';

const dateRange = ref<DateRange>(defaultDateRange());

// 口径说明：只讲「数字是什么意思」，不讲实现。
const overviewNoteSections: ReportNoteSection[] = [
  {
    label: '指标定义',
    items: [
      '发生额与已核销金额按所选区间统计',
      '期末待收 / 待付按结束日结算，包含起始日前形成的单据'
    ]
  },
  {
    label: '统计范围',
    items: [
      '已核销来自收付款与应收应付之间的分配关系，不代表实际现金收付',
      '明细按结束日列示普通单据，红字按原单汇总，超额核销单独显示'
    ]
  },
  {
    label: '明细列示',
    items: [
      '应收、应付列表均按期末日期列示，红字金额与核销余额逐单计算'
    ]
  }
];
const activeTab = ref<'receivable' | 'payable'>('receivable');
const keyword = ref('');
const overview = ref<FinanceReportOverview | null>(null);
const overviewError = ref('');
const rangeError = ref('');
const exportingOverview = ref(false), exportingReceivable = ref(false), exportingPayable = ref(false);
const receivableView = reactive(createTabView<FinanceReceivableDetailRow>(10));
const payableView = reactive(createTabView<FinancePayableDetailRow>(10));
const viewportHeight = ref(window.innerHeight);
const tableBodyHeight = computed(() => Math.max(220, viewportHeight.value - 650));

const overviewQuery = (): FinanceOverviewQuery => buildReportQuery<FinanceOverviewQuery>(dateRange.value, {});
const detailQuery = (view: TabView<unknown>): FinanceReportQuery => buildReportQuery<FinanceReportQuery>(
    dateRange.value,
    {keyword: keyword.value.trim() || undefined},
    {pageNum: view.pageNum, pageSize: view.pageSize},
);
const receivableLoader = createTabLoader<FinanceReceivableDetailRow, FinanceReportQuery>(
    receivableView, () => detailQuery(receivableView), reportFinanceApi.receivableDetails);
const payableLoader = createTabLoader<FinancePayableDetailRow, FinanceReportQuery>(
    payableView, () => detailQuery(payableView), reportFinanceApi.payableDetails);
const overviewLoader = createGuardedLoader<FinanceReportOverview>(
    () => reportFinanceApi.overview(overviewQuery()),
    (data) => { overview.value = data; overviewError.value = ''; },
    overviewError,
);

const overviewExportQuery = () => ({startDate: dateRange.value[0], endDate: dateRange.value[1]});
const detailExportQuery = () => ({...overviewExportQuery(), keyword: keyword.value.trim() || undefined});
const kpiCards = computed(() => [
    {label: '应收发生额', value: moneyText(overview.value?.receivableOccurredAmount),
        hint: '所选期间新形成的净应收：正常应收减红字应收。它是发生额，不含核销。'},
    {label: '应收已核销', value: moneyText(overview.value?.receivableWrittenOffAmount),
        hint: '所选期间的应收核销：正常核销减反向核销。核销是分配关系，不等于实际收款。'},
    {label: '期末待收', value: moneyText(overview.value?.endingReceivableAmount),
        hint: '截至结束日按每张应收逐单计算 max(净应收−有效核销, 0)，包含起始日前的未结单据。'},
    {label: '应付发生额', value: moneyText(overview.value?.payableOccurredAmount),
        hint: '所选期间新形成的净应付：正常应付减红字应付。它是发生额，不含核销。'},
    {label: '应付已核销', value: moneyText(overview.value?.payableWrittenOffAmount),
        hint: '所选期间的应付核销：正常核销减反向核销。核销是分配关系，不等于实际付款。'},
    {label: '期末待付', value: moneyText(overview.value?.endingPayableAmount),
        hint: '截至结束日按每张应付逐单计算 max(净应付−有效核销, 0)，包含起始日前的未结单据。'},
]);

const receivableColumns = ref<TableColumnsType<FinanceReceivableDetailRow>>([
    {title: '应收单号', dataIndex: 'receivableNo', fixed: 'left', width: 190, ellipsis: true},
    {title: '订单号', dataIndex: 'orderNo', width: 160},
    {title: '客户', dataIndex: 'customerName', width: 180},
    {title: '原应收金额', dataIndex: 'amount', align: 'right', width: 125},
    {title: '红字金额', dataIndex: 'redAmount', align: 'right', width: 115},
    {title: '净应收', dataIndex: 'netAmount', align: 'right', width: 115},
    {title: '已核销金额', dataIndex: 'writtenOffAmount', align: 'right', width: 125},
    {title: '期末待收', dataIndex: 'openAmount', align: 'right', width: 125},
    {title: '超额核销待处理', dataIndex: 'overAppliedAmount', align: 'right', width: 160},
    {title: '事件时点', dataIndex: 'eventAt', width: 170},
]);
const payableColumns = ref<TableColumnsType<FinancePayableDetailRow>>([
    {title: '应付单号', dataIndex: 'payableNo', fixed: 'left', width: 190, ellipsis: true},
    {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 160},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '原应付金额', dataIndex: 'amount', align: 'right', width: 125},
    {title: '红字金额', dataIndex: 'redAmount', align: 'right', width: 115},
    {title: '净应付', dataIndex: 'netAmount', align: 'right', width: 115},
    {title: '已核销金额', dataIndex: 'writtenOffAmount', align: 'right', width: 125},
    {title: '期末待付', dataIndex: 'openAmount', align: 'right', width: 125},
    {title: '超额核销待处理', dataIndex: 'overAppliedAmount', align: 'right', width: 160},
    {title: '事件时点', dataIndex: 'eventAt', width: 170},
]);

function validateRange() {
    rangeError.value = rangeOverLimitError(dateRange.value);
    return rangeError.value === '';
}

async function loadOverview() {
    if (!validateRange()) return;
    await overviewLoader();
}

async function loadReceivables() {
    if (!validateRange()) return;
    await receivableLoader();
}

async function loadPayables() {
    if (!validateRange()) return;
    await payableLoader();
}

async function queryAll() {
    receivableView.pageNum = 1;
    payableView.pageNum = 1;
    await loadOverview();
    await (activeTab.value === 'receivable' ? loadReceivables() : loadPayables());
}

async function onTabChange(tab: string | number) {
    activeTab.value = String(tab) === 'payable' ? 'payable' : 'receivable';
    await (activeTab.value === 'receivable' ? loadReceivables() : loadPayables());
}

async function searchDetails() {
    if (activeTab.value === 'receivable') {
        receivableView.pageNum = 1;
        await loadReceivables();
    } else {
        payableView.pageNum = 1;
        await loadPayables();
    }
}

async function clearKeyword() {
    keyword.value = '';
    await searchDetails();
}

async function changePage<T>(view: TabView<T>, page: number, size: number, load: () => Promise<void>) {
    view.pageNum = page;
    view.pageSize = size;
    await load();
}

async function resetQuery() {
    dateRange.value = defaultDateRange();
    keyword.value = '';
    await queryAll();
}

async function exportOverview() {
    if (!validateRange()) return;
    exportingOverview.value = true;
    try { await reportFinanceApi.overviewExport(overviewExportQuery()); }
    catch (cause) { overviewError.value = reportError(cause); }
    finally { exportingOverview.value = false; }
}

async function exportReceivables() {
    if (!validateRange()) return;
    exportingReceivable.value = true;
    try { await reportFinanceApi.receivableExport(detailExportQuery()); }
    catch (cause) { receivableView.error = reportError(cause); }
    finally { exportingReceivable.value = false; }
}

async function exportPayables() {
    if (!validateRange()) return;
    exportingPayable.value = true;
    try { await reportFinanceApi.payableExport(detailExportQuery()); }
    catch (cause) { payableView.error = reportError(cause); }
    finally { exportingPayable.value = false; }
}

function updateViewportHeight() {
    viewportHeight.value = window.innerHeight;
}

onMounted(() => {
    window.addEventListener('resize', updateViewportHeight);
    queryAll();
});

onUnmounted(() => window.removeEventListener('resize', updateViewportHeight));
</script>

<style scoped>
.report-error { margin: 10px 0; }
.report-table-hint { color: var(--scm-text-secondary); font-size: 12px; margin: 8px 0; }
.report-metric-note { line-height: 1.8; }
.smart-table-operate-block { display: flex; align-items: center; gap: 8px; }
.smart-table-operate-block :deep(.ant-input) { width: 260px; }
.finance-detail-mobile-list { display: none; }
.finance-detail-mobile-heading { display: flex; justify-content: space-between; gap: 12px; }
.finance-detail-mobile-heading > div { min-width: 0; }
.finance-detail-mobile-heading strong { display: block; overflow-wrap: anywhere; }
.finance-detail-mobile-label,
.finance-detail-mobile-balance > span { display: block; color: var(--scm-text-secondary); font-size: 12px; }
.finance-detail-mobile-balance { text-align: right; }
.finance-detail-mobile-balance strong { color: var(--scm-primary); font-size: 16px; font-variant-numeric: tabular-nums; }
.finance-detail-mobile-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 12px; margin: 14px 0 0; }
.finance-detail-mobile-fields > div { min-width: 0; }
.finance-detail-mobile-fields dt { color: var(--scm-text-secondary); font-size: 12px; }
.finance-detail-mobile-fields dd { margin: 2px 0 0; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.finance-detail-mobile-over-applied dd { color: var(--scm-warning); font-weight: 600; }
@media (max-width: 768px) {
  .smart-table-operate-block { align-items: stretch; flex-wrap: wrap; }
  .smart-table-operate-block :deep(.ant-input) { width: min(260px, 100%); }
  .finance-detail-table,
  .finance-table-operator { display: none; }
  .finance-detail-mobile-list { display: grid; gap: 10px; }
  .report-finance-overview { padding-bottom: 48px; }
}
</style>
