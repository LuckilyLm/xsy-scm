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
        </a-form-item>
      </a-row>
    </a-form>

    <a-alert v-if="rangeError" class="report-error" type="warning" show-icon :message="rangeError"/>

    <div class="finance-kpis">
      <ReportKpiCard v-for="card in kpiCards" :key="card.label" :label="card.label" :value="card.value"/>
    </div>

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
          <a-table :id="SCM_REPORT_TABLE_ID.FINANCE_RECEIVABLE" class="finance-detail-table" size="small"
                   :data-source="receivableView.rows" :columns="receivableColumns"
                   row-key="receivableId" :loading="receivableView.loading" :pagination="false"
                   bordered :scroll="{x: 1580, y: tableBodyHeight}">
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
          <a-table :id="SCM_REPORT_TABLE_ID.FINANCE_PAYABLE" class="finance-detail-table" size="small"
                   :data-source="payableView.rows" :columns="payableColumns"
                   row-key="payableId" :loading="payableView.loading" :pagination="false"
                   bordered :scroll="{x: 1580, y: tableBodyHeight}">
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
import {reportError} from './report-errors';
import {useScmErrorToast} from '../common/scm-error-toast';
import type {
    FinanceOverviewQuery,
    FinancePayableDetailRow,
    FinanceReceivableDetailRow,
    FinanceReportOverview,
    FinanceReportQuery,
} from './report-types';
import {
    buildReportQuery,
    defaultDateRange,
    rangeOverLimitError,
    type DateRange,
    type TabView,
} from './report-model';
import {createGuardedLoader, createTabLoader, createToastingTabView} from './use-report-query';
import {moneyText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';

const dateRange = ref<DateRange>(defaultDateRange());

const activeTab = ref<'receivable' | 'payable'>('receivable');
const keyword = ref('');
const overview = ref<FinanceReportOverview | null>(null);
const overviewError = useScmErrorToast();
const rangeError = ref('');
const exportingOverview = ref(false), exportingReceivable = ref(false), exportingPayable = ref(false);
const receivableView = reactive(createToastingTabView<FinanceReceivableDetailRow>(10));
const payableView = reactive(createToastingTabView<FinancePayableDetailRow>(10));
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
    {label: '应收发生额', value: moneyText(overview.value?.receivableOccurredAmount)},
    {label: '应收已核销（非实收）', value: moneyText(overview.value?.receivableWrittenOffAmount)},
    {label: '期末待收（含期初未结）', value: moneyText(overview.value?.endingReceivableAmount)},
    {label: '应付发生额', value: moneyText(overview.value?.payableOccurredAmount)},
    {label: '应付已核销（非实付）', value: moneyText(overview.value?.payableWrittenOffAmount)},
    {label: '期末待付（含期初未结）', value: moneyText(overview.value?.endingPayableAmount)},
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
    {title: '超额核销待处理', dataIndex: 'overAppliedAmount', align: 'right', width: 240},
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
    {title: '超额核销待处理', dataIndex: 'overAppliedAmount', align: 'right', width: 240},
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
/* 六张卡按内容自适应排布：写死三列会让最后一行的卡几乎空着。 */
.finance-kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin: 10px 0;
}
.smart-table-operate-block { display: flex; align-items: center; gap: 8px; }
/* allow-clear 会把输入包进 .ant-input-affix-wrapper：宽度要设在它身上，
   只设内层 .ant-input 时外层仍按 100% 撑开（实测 441px），工具栏被挤到换行。 */
.smart-table-operate-block :deep(.ant-input-affix-wrapper) { width: 260px; }
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
  .smart-table-operate-block :deep(.ant-input-affix-wrapper) { width: min(260px, 100%); }
  .finance-detail-table,
  .finance-table-operator { display: none; }
  .finance-detail-mobile-list { display: grid; gap: 10px; }
  .report-finance-overview { padding-bottom: 48px; }
}
</style>
