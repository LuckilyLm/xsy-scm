<!--
  销售分析：按商品 / 按分类 / 按客户 / 按销售员 / 订单明细。

  全页只有一个口径，五个 Tab 都不得偏离（后端也保证同一筛选在各维度下一致）：
  订单 `status=CONFIRMED`、业务日 `confirmed_at`、金额只取 `settlement_*`。
  因此页面上出现的是「确认订单金额」，**不是**「营业收入」；`ordered_*` 只在订单明细里
  以「订购数量」出现，与「实际数量」并排，绝不合成一个数。

  五个 Tab 共享筛选、各自一份分页与错误状态（切 Tab 不串页码）。
  本页**没有合计行**：后端只给分页行与 TOP，前端把当页金额相加会被读成区间合计，
  而区间合计就是概览页的「已确认订单金额」。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="确认日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange"/>
      </a-form-item>
      <a-form-item label="客户" class="smart-query-form-item">
        <CustomerSelect v-model:value="filters.customerId" width="200px"/>
      </a-form-item>
      <a-form-item label="销售员" class="smart-query-form-item">
        <EmployeeSelect v-model:value="filters.sellerId" width="160px"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.SALES_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="订单来源" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.orderSource" enum-name="SCM_ORDER_SOURCE_ENUM" width="150px"/>
      </a-form-item>
      <a-form-item label="商品分类" class="smart-query-form-item">
        <CategorySelect v-model:value="filters.categoryId" :categories="categories" style="width: 220px"/>
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / 商品编码 / 商品规格编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="chartError" :message="chartError" type="error" show-icon>
    <template #action>
      <a-button @click="queryActiveTab">重试</a-button>
    </template>
  </a-alert>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <!-- ==================== 按商品 ==================== -->
    <a-tab-pane key="product" tab="按商品">
      <SalesProductTab
          :items="topItems(productTop)"
          :rows="product.rows"
          :loading="product.loading"
          :error="product.error"
          :page-num="product.pageNum"
          :page-size="product.pageSize"
          :total="product.total"
          @export="exportProduct"
          @reload="loadProduct"
          @page-change="loadProductPage"
      />
    </a-tab-pane>

    <!-- ==================== 按分类 ==================== -->
    <a-tab-pane key="category" tab="按分类">
      <SalesCategoryTab
          :items="topItems(categoryTop)"
          :rows="category.rows"
          :loading="category.loading"
          :error="category.error"
          :page-num="category.pageNum"
          :page-size="category.pageSize"
          :total="category.total"
          @export="exportCategory"
          @reload="loadCategory"
          @page-change="loadCategoryPage"
      />
    </a-tab-pane>

    <!-- ==================== 按客户 ==================== -->
    <a-tab-pane key="customer" tab="按客户">
      <SalesCustomerTab
          :items="topItems(customerTop)"
          :rows="customer.rows"
          :loading="customer.loading"
          :error="customer.error"
          :page-num="customer.pageNum"
          :page-size="customer.pageSize"
          :total="customer.total"
          @export="exportCustomer"
          @reload="loadCustomer"
          @page-change="loadCustomerPage"
      />
    </a-tab-pane>

    <!-- ==================== 按销售员 ==================== -->
    <a-tab-pane key="seller" tab="按销售员">
      <SalesSellerTab
          :rows="seller.rows"
          :loading="seller.loading"
          :error="seller.error"
          :page-num="seller.pageNum"
          :page-size="seller.pageSize"
          :total="seller.total"
          @export="exportSeller"
          @reload="loadSeller"
          @page-change="loadSellerPage"
      />
    </a-tab-pane>

    <!-- ==================== 订单明细 ==================== -->
    <a-tab-pane key="item" tab="订单明细">
      <SalesItemTab
          :rows="item.rows"
          :loading="item.loading"
          :error="item.error"
          :page-num="item.pageNum"
          :page-size="item.pageSize"
          :total="item.total"
          @export="exportItem"
          @reload="loadItem"
          @page-change="loadItemPage"
          @open-order="openOrder"
      />
    </a-tab-pane>
    <!-- ==================== 客户订单明细（订单表头级） ==================== -->
    <a-tab-pane key="order" tab="客户订单明细">
      <SalesOrderTab
          :rows="order.rows"
          :loading="order.loading"
          :error="order.error"
          :page-num="order.pageNum"
          :page-size="order.pageSize"
          :total="order.total"
          @export="exportOrder"
          @reload="loadOrder"
          @page-change="loadOrderPage"
          @open-order="openOrder"
      />
    </a-tab-pane>
  </a-tabs>

  <OrderDetail ref="orderDetail"/>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import OrderDetail from '../order/order-detail.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import SalesProductTab from './report-components/sales-product-tab.vue';
import SalesCategoryTab from './report-components/sales-category-tab.vue';
import SalesCustomerTab from './report-components/sales-customer-tab.vue';
import SalesSellerTab from './report-components/sales-seller-tab.vue';
import SalesItemTab from './report-components/sales-item-tab.vue';
import SalesOrderTab from './report-components/sales-order-tab.vue';
import {reportSalesApi} from '/@/api/business/scm/report-api';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {SCM_REPORT_PERMISSION} from '/@/constants/business/scm/report-const';
import type {ProductCategory} from '/@/types/business/scm/product';
import type {
    ReportChartBar,
    SalesCategoryRow,
    SalesCustomerRow,
    SalesItemRow,
    SalesOrderRow,
    SalesProductRow,
    SalesQuery,
    SalesSellerRow,
    SalesTopItem,
} from './report-types';
import type {Id} from '../inventory/inventory-types';
import {
    buildReportQuery,
    chartValue,
    createTabView,
    defaultDateRange,
    enterTab,
    rangeFromQuery,
    rangeOverLimitError,
} from './report-model';
import {moneyText} from '../inventory/inventory-model';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();

type SalesTab = 'product' | 'category' | 'customer' | 'seller' | 'item' | 'order';

const activeTab = ref<SalesTab>('product');
const dateRange = ref<DateRange | undefined>();
/** 共享筛选：五个维度共用同一个后端表单，切 Tab 不重置它，只各自归页码。 */
const filters = reactive<Omit<SalesQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const chartError = ref('');
const categories = ref<ProductCategory[]>([]);

/** TOP5 图的原始行（后端已按金额降序 LIMIT）。 */
const productTop = ref<SalesTopItem[]>([]);
const categoryTop = ref<SalesTopItem[]>([]);
const customerTop = ref<SalesTopItem[]>([]);

const product = reactive(createTabView<SalesProductRow>());
const category = reactive(createTabView<SalesCategoryRow>());
const customer = reactive(createTabView<SalesCustomerRow>());
const seller = reactive(createTabView<SalesSellerRow>());
const item = reactive(createTabView<SalesItemRow>());
const order = reactive(createTabView<SalesOrderRow>());

/** 六个 Tab 都有对应导出端点（后端逐个 AND 上 `scm:report:export`），按钮按 `PERM.EXPORT` 显示。 */

/** 请求体：日期区间 + 共享筛选 + 该 Tab 的分页。 */
function salesQuery(tab: {pageNum: number; pageSize: number}): SalesQuery {
    return buildReportQuery<SalesQuery>(dateRange.value, {...filters}, tab);
}

/** 导出口径 = 列表口径，且不带分页（后端强制第 1 页与行数上限）。 */
function exportQuery(): Partial<SalesQuery> {
    return buildReportQuery<Partial<SalesQuery>>(dateRange.value, {...filters});
}

const loadProduct = createTabLoader(product, () => salesQuery(product), reportSalesApi.product);
const loadCategory = createTabLoader(category, () => salesQuery(category), reportSalesApi.category);
const loadCustomer = createTabLoader(customer, () => salesQuery(customer), reportSalesApi.customer);
const loadSeller = createTabLoader(seller, () => salesQuery(seller), reportSalesApi.seller);
const loadItem = createTabLoader(item, () => salesQuery(item), reportSalesApi.item);
const loadOrder = createTabLoader(order, () => salesQuery(order), reportSalesApi.order);

function loadProductPage(pageNum: number, pageSize: number) {
    product.pageNum = pageNum;
    product.pageSize = pageSize;
    void loadProduct();
}

function loadCategoryPage(pageNum: number, pageSize: number) {
    category.pageNum = pageNum;
    category.pageSize = pageSize;
    void loadCategory();
}

function loadCustomerPage(pageNum: number, pageSize: number) {
    customer.pageNum = pageNum;
    customer.pageSize = pageSize;
    void loadCustomer();
}

function loadSellerPage(pageNum: number, pageSize: number) {
    seller.pageNum = pageNum;
    seller.pageSize = pageSize;
    void loadSeller();
}

function loadItemPage(pageNum: number, pageSize: number) {
    item.pageNum = pageNum;
    item.pageSize = pageSize;
    void loadItem();
}

function loadOrderPage(pageNum: number, pageSize: number) {
    order.pageNum = pageNum;
    order.pageSize = pageSize;
    void loadOrder();
}

const loadProductTop = createGuardedLoader(
    () => reportSalesApi.productTop(salesQuery(product)),
    (rows) => (productTop.value = rows ?? []),
    chartError
);
const loadCategoryTop = createGuardedLoader(
    () => reportSalesApi.categoryTop(salesQuery(category)),
    (rows) => (categoryTop.value = rows ?? []),
    chartError
);
const loadCustomerTop = createGuardedLoader(
    () => reportSalesApi.customerTop(salesQuery(customer)),
    (rows) => (customerTop.value = rows ?? []),
    chartError
);

/** TOP 行 → 图数据：`value` 只决定条长，标签与 tooltip 用后端原文。 */
function topItems(rows: SalesTopItem[]): ReportChartBar[] {
    return (rows ?? []).map((row) => ({
        name: row.name || '未命名',
        value: chartValue(row.amount),
        text: moneyText(row.amount),
    }));
}

/** 只查当前 Tab：五个维度各查一遍会把同一个页面打成五次聚合扫描。 */
function queryActiveTab() {
    const overLimit = rangeOverLimitError(dateRange.value);
    if (overLimit) {
        chartError.value = overLimit;
        return;
    }
    chartError.value = '';
    switch (activeTab.value) {
        case 'product':
            void loadProduct();
            void loadProductTop();
            break;
        case 'category':
            void loadCategory();
            void loadCategoryTop();
            break;
        case 'customer':
            void loadCustomer();
            void loadCustomerTop();
            break;
        case 'seller':
            void loadSeller();
            break;
        case 'item':
            void loadItem();
            break;
        case 'order':
            void loadOrder();
            break;
        default:
            break;
    }
}

/**
 * 切 Tab：共享筛选保持不变，只把**目标 Tab** 的页码归 1 再重查。
 *
 * 不接 `a-tabs` 传出的 key（它的类型是 `Key`，且 `v-model:activeKey` 已经先更新过
 * `activeTab`），否则等于把同一个状态存两处。
 */
function onTabChange() {
    switch (activeTab.value) {
        case 'product':
            enterTab(product);
            break;
        case 'category':
            enterTab(category);
            break;
        case 'customer':
            enterTab(customer);
            break;
        case 'seller':
            enterTab(seller);
            break;
        case 'item':
            enterTab(item);
            break;
        case 'order':
            enterTab(order);
            break;
        default:
            break;
    }
    queryActiveTab();
}

function onSearch() {
    queryActiveTab();
}

function resetQuery() {
    filters.customerId = undefined;
    filters.sellerId = undefined;
    filters.orderSource = undefined;
    filters.categoryId = undefined;
    filters.keyword = undefined;
    // 重置回到「本月」（的统一默认值），并把每个 Tab 的页码归零位
    dateRange.value = defaultDateRange();
    resetAllTabsPage();
    onSearch();
}

function resetAllTabsPage() {
    enterTab(product);
    enterTab(category);
    enterTab(customer);
    enterTab(seller);
    enterTab(item);
}

function exportProduct() {
    void reportSalesApi.productExport(exportQuery());
}

function exportCategory() {
    void reportSalesApi.categoryExport(exportQuery());
}

function exportCustomer() {
    void reportSalesApi.customerExport(exportQuery());
}

function exportSeller() {
    void reportSalesApi.sellerExport(exportQuery());
}

function exportItem() {
    void reportSalesApi.itemExport(exportQuery());
}

function exportOrder() {
    void reportSalesApi.orderExport(exportQuery());
}

const orderDetail = ref<InstanceType<typeof OrderDetail>>();

function openOrder(orderId: Id) {
    orderDetail.value?.open(orderId);
}

onMounted(async () => {
    dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
    try {
        const tree = await productCategoryApi.tree();
        categories.value = tree.data ?? [];
    } catch {
        // 分类字典拉不到只影响高级筛选的一个下拉，不该挡住销售分析本身
        categories.value = [];
    }
    queryActiveTab();
});
</script>
