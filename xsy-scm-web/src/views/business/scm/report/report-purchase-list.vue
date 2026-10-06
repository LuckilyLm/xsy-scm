<!--
  采购分析（含价格波动）：
  采购概览 / 按商品 / 按供应商 / 按采购员 / 采购明细 / 价格波动。

  本页刻意没有「应付金额」：采购单金额是采购承诺，收货参考金额是履约事实，
  两者都不等于应付 —— 应付需要收货/入账时点与核销规则， 没有这些事实，不能提前命名。

  日期是「提交日期」：草稿没有承诺量也没有价格事实，因此默认统计
  SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED，排除 DRAFT 与 CANCELLED（SQL 固定）。
-->
<template>
  <a-form v-if="activeTab !== 'daily'" class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="提交日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange"/>
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="filters.supplierId" width="200px"/>
      </a-form-item>
      <a-form-item label="采购员" class="smart-query-form-item">
        <EmployeeSelect v-model:value="filters.purchaserId" width="160px"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.PURCHASE_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="状态" class="smart-query-form-item" extra="只在四个已提交状态内收窄">
        <SmartEnumSelect v-model:value="filters.status" enum-name="SCM_PURCHASE_STATUS_ENUM" width="160px"/>
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / 商品编码 / 商品规格编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item v-if="activeTab === 'trend'" label="商品规格" class="smart-query-form-item">
        <SkuSelect v-model:value="filters.skuId" width="240px" placeholder="按商品规格看价格曲线"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="chartError && activeTab !== 'daily'" :message="chartError" type="error" show-icon>
    <template #action>
      <a-button @click="queryActiveTab">重试</a-button>
    </template>
  </a-alert>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <a-tab-pane key="daily" tab="每日清单">
      <PurchaseDailyReport/>
    </a-tab-pane>
    <!-- ==================== 采购概览 ==================== -->
    <a-tab-pane key="overview" tab="采购概览">
      <a-row class="smart-table-btn-block">
        <div class="smart-table-operate-block">
          <a-button v-privilege="PERM.EXPORT" @click="exportOverview">导出</a-button>
          <ReportNote title="口径说明"
                      :points="['导出的是本页指标卡单行', '没有授权仓库时指标不可知，导出为空而不是 0']"/>
        </div>
      </a-row>
      <a-row :gutter="[12, 12]">
        <a-col v-for="card in overviewCards" :key="card.label" :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
          <ReportKpiCard
              :label="card.label"
              :value="card.value"
              :hint="card.hint"
              :warning="card.warning"
          />
        </a-col>
      </a-row>
      <ReportBarChart
          class="smart-margin-top10"
          title="供应商采购入库成本 TOP10"
          :items="topItems(supplierTopRows)"
          series-name="采购入库成本金额"
          extra="由采购入库形成，不是采购单金额"
      />
    </a-tab-pane>

    <!-- ==================== 按商品 ==================== -->
    <a-tab-pane key="product" tab="按商品">
      <PurchaseProductTab
          v-model:columns="productColumns"
          :rows="product.rows"
          :loading="product.loading"
          :error="product.error"
          :can-view-cost="canViewCost"
          :page-num="product.pageNum"
          :page-size="product.pageSize"
          :total="product.total"
          @export="exportProduct"
          @reload="loadProduct"
          @page-change="loadProductPage"
      />
    </a-tab-pane>

    <!-- ==================== 按供应商 ==================== -->
    <a-tab-pane key="supplier" tab="按供应商">
  <PurchaseSupplierTab
      :items="topItems(supplierTopRows)"
      :rows="supplier.rows"
      :loading="supplier.loading"
      :error="supplier.error"
      :can-view-cost="canViewCost"
      :page-num="supplier.pageNum"
      :page-size="supplier.pageSize"
      :total="supplier.total"
      @export="exportSupplier"
      @reload="loadSupplier"
      @page-change="loadSupplierPage"
      @open-supplier="openSupplierDrilldown"
  />
</a-tab-pane>

    <!-- ==================== 按采购员 ==================== -->
    <a-tab-pane key="purchaser" tab="按采购员">
      <PurchasePurchaserTab
          :rows="purchaser.rows"
          :loading="purchaser.loading"
          :error="purchaser.error"
          :can-view-cost="canViewCost"
          :page-num="purchaser.pageNum"
          :page-size="purchaser.pageSize"
          :total="purchaser.total"
          @export="exportPurchaser"
          @reload="loadPurchaser"
          @page-change="loadPurchaserPage"
          @open-purchaser="openPurchaserDrilldown"
      />
    </a-tab-pane>

    <!-- ==================== 采购明细 ==================== -->
    <a-tab-pane key="item" tab="采购明细">
      <PurchaseItemTab
          :rows="item.rows"
          :loading="item.loading"
          :error="item.error"
          :page-num="item.pageNum"
          :page-size="item.pageSize"
          :total="item.total"
          @export="exportItem"
          @reload="loadItem"
          @page-change="loadItemPage"
          @open-purchase-order="openPurchaseOrder"
      />
    </a-tab-pane>

    <!-- ==================== 价格波动（-B） ==================== -->
    <a-tab-pane key="trend" tab="价格波动">
      <PurchasePriceTrendTab
          :rows="trend.rows"
          :loading="trend.loading"
          :error="trend.error"
          @export="exportPriceTrend"
          @reload="loadTrend"
      />
    </a-tab-pane>
  </a-tabs>

  <ReportDrilldownDrawer
      v-model:open="drilldown.open"
      :title="drilldown.title"
      :columns="visibleDrilldownColumns"
      :rows="drilldown.rows"
      :loading="drilldown.loading"
      :error="drilldown.error"
      :total="drilldown.total"
      v-model:page-num="drilldown.pageNum"
      v-model:page-size="drilldown.pageSize"
      @change="loadDrilldown"
  >
    <template #bodyCell="{ record, column }">
      <template v-if="column.dataIndex === 'orderCount'">
        <span class="scm-quantity">{{ countText(record.orderCount) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'plannedQuantity'">
        <span class="scm-quantity">{{ quantityText(record.plannedQuantity) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'receivedQuantity'">
        <span class="scm-quantity">{{ quantityText(record.receivedQuantity) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'orderAmount'">
        <span class="scm-money">{{ moneyText(record.orderAmount) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'avgPurchasePrice'">
        <span class="scm-money">{{ moneyText(record.avgPurchasePrice) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'inboundQuantityText'">
        {{ textOrDash(record.inboundQuantityText) }}
      </template>
      <template v-else-if="column.dataIndex === 'inboundCostAmount'">
        <span class="scm-money">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
      </template>
      <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
    </template>
  </ReportDrilldownDrawer>

  <PurchaseOrderDetail ref="purchaseOrderDetail"/>
</template>

<script setup lang="ts">
import PurchaseDailyReport from './report-components/purchase-daily-report.vue';
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import PurchaseOrderDetail from '../purchase/components/purchase-order-detail-drawer.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import ReportNote from '/@/components/business/scm/report-note/index.vue';
import ReportBarChart from './report-components/report-bar-chart.vue';
import ReportDrilldownDrawer from './report-components/report-drilldown-drawer.vue';
import PurchasePriceTrendTab from './report-components/purchase-price-trend-tab.vue';
import PurchaseItemTab from './report-components/purchase-item-tab.vue';
import PurchasePurchaserTab from './report-components/purchase-purchaser-tab.vue';
import PurchaseProductTab from './report-components/purchase-product-tab.vue';
import PurchaseSupplierTab from './report-components/purchase-supplier-tab.vue';
import {reportPurchaseApi} from '/@/api/business/scm/report-api';
import {SCM_REPORT_PERMISSION} from '/@/constants/business/scm/report-const';
import type {
    PurchaseItemRow,
    PurchaseOverview,
    PurchasePriceTrendPoint,
    PurchaseProductRow,
    PurchaseQuery,
    PurchasePurchaserRow,
    PurchaseSupplierRow,
    PurchaseTopItem,
    ReportChartBar,
    ReportId,
} from './report-types';
import {
    buildReportQuery,
    chartValue,
    costText,
    countText,
    createTabView,
    defaultDateRange,
    enterTab,
    filterCostColumns,
    incompleteCostHint,
    rangeFromQuery,
    rangeOverLimitError,
    textOrDash,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import type {TabView} from './report-model';
import {useReportPermission} from './use-report-permission';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();
const {canViewCost} = useReportPermission();

type PurchaseTab = 'overview' | 'product' | 'supplier' | 'purchaser' | 'item' | 'trend' | 'daily';

const activeTab = ref<PurchaseTab>('overview');
const dateRange = ref<DateRange | undefined>();
/** 共享筛选：六个维度共用同一张后端表单。 */
const filters = reactive<Omit<PurchaseQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const chartError = ref('');

const overview = ref<PurchaseOverview>();
const supplierTopRows = ref<PurchaseTopItem[]>([]);

const product = reactive(createTabView<PurchaseProductRow>());
const supplier = reactive(createTabView<PurchaseSupplierRow>());
const purchaser = reactive(createTabView<PurchasePurchaserRow>());
const item = reactive(createTabView<PurchaseItemRow>());
const trend = reactive(createTabView<PurchasePriceTrendPoint>());

/** 下钻抽屉的商品明细：带一个实体筛选（供应商或采购员），与主表分页彼此独立。 */
const drilldown = reactive({...createTabView<PurchaseProductRow>(), open: false, title: ''});
/**
 * 下钻实体筛选。
 *
 * 类型比 `PurchaseQuery` 的 `supplierId / purchaserId` 宽一档：这里的值直接来自行数据
 * （后端 `Long` 可能是字符串形状），而装配进 `buildReportQuery` 时不逐字段比对。
 * 用 `Number()` 归一反而会藏掉「id 超出安全整数」这类真实问题。
 */
const drilldownTarget = reactive<{supplierId?: ReportId; purchaserId?: ReportId}>({});

/** 成本列集中在 `inboundCostAmount`；无成本权限时整列不出现。 */
const COST_INDEXES = ['inboundCostAmount'];

const productColumns = ref<TableColumnsType<PurchaseProductRow>>([
    {title: '商品名称', dataIndex: 'productName', width: 200},
    {title: '商品编码', dataIndex: 'spuCode', width: 150},
    {title: '商品规格编码', dataIndex: 'skuCode', width: 170},
    {title: '商品规格名称', dataIndex: 'skuName', width: 160},
    {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 100},
    {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 110},
    {title: '计划采购数量', dataIndex: 'plannedQuantity', align: 'right', width: 140},
    {title: '已收数量', dataIndex: 'receivedQuantity', align: 'right', width: 120},
    {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 160},
    {title: '采购成交均价', dataIndex: 'avgPurchasePrice', align: 'right', width: 140},
    {title: '采购入库数量', dataIndex: 'inboundQuantityText', width: 180},
    {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
]);

const visibleDrilldownColumns = computed(() =>
    filterCostColumns(productColumns.value, COST_INDEXES, canViewCost.value)
);

const overviewCards = computed(() => {
    const data = overview.value ?? {};
    const cards: Array<{label: string; value: string; hint?: string; warning?: string; cost?: boolean}> = [
        {
            label: '已提交采购单',
            value: countText(data.submittedOrderCount),
            hint: 'SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED；不含草稿与取消',
        },
        {
            label: '已提交采购金额',
            value: moneyText(data.submittedAmount),
            hint: 'SUM(purchase_order.total_amount)：这是采购承诺，不是应付',
        },
        {label: '已确认收货单', value: countText(data.confirmedReceiptCount), hint: 'receipt.status = CONFIRMED'},
        {
            label: '收货参考金额',
            value: moneyText(data.receiptReferenceAmount),
            hint: '收货数量 × 采购单价，只用于交叉核对价格与数量，不等于应付',
        },
        {
            label: '采购入库成本金额',
            value: costText(data.purchaseInCostAmount, canViewCost.value),
            hint: 'PURCHASE_IN 流水的 SUM(quantity × unit_cost)',
            warning: incompleteCostHint(data.purchaseInCostMissingCount, '采购入库成本金额'),
            cost: true,
        },
        {
            label: '待入库收货单',
            value: countText(data.pendingPutawayReceiptCount),
            hint: 'WAREHOUSE_CONFIRM + 已确认收货 + 入库状态 PENDING；入库动作在采购收货页办理',
        },
    ];
    return cards.filter((card) => !card.cost || canViewCost.value);
});

function topItems(rows: PurchaseTopItem[]): ReportChartBar[] {
    return (rows ?? []).map((row) => ({
        name: row.name || '未命名',
        value: chartValue(row.amount),
        text: moneyText(row.amount),
    }));
}

function purchaseQuery(tab: {pageNum: number; pageSize: number}): PurchaseQuery {
    return buildReportQuery<PurchaseQuery>(dateRange.value, {...filters}, tab);
}

function exportQuery(): Partial<PurchaseQuery> {
    return buildReportQuery<Partial<PurchaseQuery>>(dateRange.value, {...filters});
}

const loadProduct = createTabLoader(product, () => purchaseQuery(product), reportPurchaseApi.product);
const loadSupplier = createTabLoader(supplier, () => purchaseQuery(supplier), reportPurchaseApi.supplier);
const loadPurchaser = createTabLoader(purchaser, () => purchaseQuery(purchaser), reportPurchaseApi.purchaser);
const loadItem = createTabLoader(item, () => purchaseQuery(item), reportPurchaseApi.item);

function loadProductPage(pageNum: number, pageSize: number) {
    product.pageNum = pageNum;
    product.pageSize = pageSize;
    void loadProduct();
}

function loadSupplierPage(pageNum: number, pageSize: number) {
    supplier.pageNum = pageNum;
    supplier.pageSize = pageSize;
    void loadSupplier();
}

function loadItemPage(pageNum: number, pageSize: number) {
    item.pageNum = pageNum;
    item.pageSize = pageSize;
    void loadItem();
}

function loadPurchaserPage(pageNum: number, pageSize: number) {
    purchaser.pageNum = pageNum;
    purchaser.pageSize = pageSize;
    void loadPurchaser();
}

const loadOverview = createGuardedLoader(
    () => reportPurchaseApi.overview(buildReportQuery<PurchaseQuery>(dateRange.value, {...filters})),
    (data) => (overview.value = data),
    chartError
);
const loadSupplierTop = createGuardedLoader(
    () => reportPurchaseApi.supplierTop(buildReportQuery<PurchaseQuery>(dateRange.value, {...filters})),
    (rows) => (supplierTopRows.value = rows ?? []),
    chartError
);
/** 价格波动不分页（一次给完整个区间的按日点），表格只渲染返回的行。 */
const loadTrend = createGuardedLoader(
    () => reportPurchaseApi.priceTrend(purchaseQuery(trend)),
    (rows) => {
        trend.rows = rows ?? [];
        trend.total = (rows ?? []).length;
    },
    chartError
);

const loadDrilldown = createTabLoader(
    drilldown as TabView<PurchaseProductRow>,
    () =>
        buildReportQuery<PurchaseQuery>(dateRange.value, {...filters, ...drilldownTarget}, drilldown)
    ,
    reportPurchaseApi.product
);

function queryActiveTab() {
    if (activeTab.value === 'daily') return;
    const overLimit = rangeOverLimitError(dateRange.value);
    if (overLimit) {
        chartError.value = overLimit;
        return;
    }
    chartError.value = '';
    switch (activeTab.value) {
        case 'overview':
            void loadOverview();
            void loadSupplierTop();
            break;
        case 'product':
            void loadProduct();
            break;
        case 'supplier':
            void loadSupplier();
            void loadSupplierTop();
            break;
        case 'purchaser':
            void loadPurchaser();
            break;
        case 'item':
            void loadItem();
            break;
        case 'trend':
            void loadTrend();
            break;
        default:
            break;
    }
}

function onTabChange() {
    enterTabByActiveTab();
    queryActiveTab();
}

function enterTabByActiveTab() {
    switch (activeTab.value) {
        case 'overview':
            return;
        case 'product':
            enterTab(product);
            return;
        case 'supplier':
            enterTab(supplier);
            return;
        case 'purchaser':
            enterTab(purchaser);
            return;
        case 'item':
            enterTab(item);
            return;
        case 'trend':
            enterTab(trend);
            return;
        default:
            return;
    }
}

function onSearch() {
    queryActiveTab();
}

function resetQuery() {
    filters.supplierId = undefined;
    filters.purchaserId = undefined;
    filters.warehouseId = undefined;
    filters.status = undefined;
    filters.keyword = undefined;
    filters.skuId = undefined;
    dateRange.value = defaultDateRange();
    enterTab(product);
    enterTab(supplier);
    enterTab(purchaser);
    enterTab(item);
    enterTab(trend);
    onSearch();
}

/** ：点供应商 → 右侧抽屉看该供应商的商品维度明细（带当前日期区间）。 */
function openSupplierDrilldown(row: PurchaseSupplierRow) {
    drilldownTarget.supplierId = row.supplierId;
    drilldownTarget.purchaserId = undefined;
    drilldown.title = `供应商「${row.supplierName ?? '—'}」商品采购明细`;
    enterTab(drilldown as TabView<PurchaseProductRow>);
    drilldown.open = true;
    void loadDrilldown();
}

/** ：点采购员 → 同一张抽屉、同一套列。 */
function openPurchaserDrilldown(row: PurchasePurchaserRow) {
    drilldownTarget.supplierId = undefined;
    drilldownTarget.purchaserId = row.purchaserId;
    drilldown.title = `采购员「${row.purchaserName ?? '未分配采购员'}」商品采购明细`;
    enterTab(drilldown as TabView<PurchaseProductRow>);
    drilldown.open = true;
    void loadDrilldown();
}

function exportProduct() {
    void reportPurchaseApi.productExport(exportQuery());
}

function exportSupplier() {
    void reportPurchaseApi.supplierExport(exportQuery());
}

function exportItem() {
    void reportPurchaseApi.itemExport(exportQuery());
}

function exportOverview() {
    void reportPurchaseApi.overviewExport(exportQuery());
}

function exportPurchaser() {
    void reportPurchaseApi.purchaserExport(exportQuery());
}

function exportPriceTrend() {
    void reportPurchaseApi.priceTrendExport(exportQuery());
}

const purchaseOrderDetail = ref<InstanceType<typeof PurchaseOrderDetail>>();

function openPurchaseOrder(purchaseOrderId: ReportId) {
    purchaseOrderDetail.value?.open(purchaseOrderId);
}

onMounted(() => {
    dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
    queryActiveTab();
});
</script>

<style scoped>
.report-warn-icon {
  color: var(--scm-warning);
  margin-left: 4px;
}
</style>
