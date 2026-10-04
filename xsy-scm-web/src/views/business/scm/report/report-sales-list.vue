<!--
  销售分析（Finance R0 计划 §5–§10）：按商品 / 按分类 / 按客户 / 按销售员 / 订单明细。

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
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / SPU 编码 / SKU 编码" allow-clear @pressEnter="onSearch"/>
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
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportItem">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              一行 = 一个订单行，名称与价格全部取订单行快照，不回查当前商品主档。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="itemColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_ITEM"
                :refresh="loadItem"
            />
          </div>
        </a-row>
        <a-alert v-if="item.error" :message="item.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadItem">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_ITEM"
            size="small"
            :data-source="item.rows"
            :columns="itemColumns"
            row-key="orderItemId"
            bordered
            :loading="item.loading"
            :pagination="false"
            :locale="{emptyText: '暂无订单明细'}"
            :scroll="{x: 2600}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderNo'">
              <!-- 复用订单详情，不造第二套详情页 -->
              <a v-if="record.orderId" @click="openOrder(record.orderId)">{{ record.orderNo }}</a>
              <span v-else>{{ record.orderNo ?? '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'confirmedAt'">
              {{ datetime(record.confirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'orderSource'">
              {{ enumDescText(record.orderSource, SCM_ORDER_SOURCE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'settleMode'">
              {{ enumDescText(record.settleMode, SETTLE_MODE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'productType'">
              {{ optionLabel(PRODUCT_TYPE_ENUM, record.productType) }}
            </template>
            <template v-else-if="column.dataIndex === 'orderedQuantity'">
              <span class="num">{{ quantityText(record.orderedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'actualQuantity'">
              <span class="num">{{ quantityText(record.actualQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lockedUnitPrice'">
              <span class="num">{{ moneyText(record.lockedUnitPrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lockedPriceSource'">
              {{ enumDescText(record.lockedPriceSource, SCM_ORDER_PRICE_SOURCE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'settlementLineAmount'">
              <span class="num">{{ moneyText(record.settlementLineAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'manualPriceOverride'">
              {{ yesNoText(record.manualPriceOverride) }}
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="item.pageNum"
              v-model:page-size="item.pageSize"
              :total="item.total"
              @change="loadItem"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
    <!-- ==================== 客户订单明细（订单表头级） ==================== -->
    <a-tab-pane key="order" tab="客户订单明细">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportOrder">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              一行 = 一个订单：回答「这个客户有几单、每单多少」。分类与关键词只判定订单是否命中，
              命中后金额仍按整单汇总，不会只算匹配到的行。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="orderColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_ORDER"
                :refresh="loadOrder"
            />
          </div>
        </a-row>
        <a-alert v-if="order.error" :message="order.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadOrder">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_ORDER"
            size="small"
            :data-source="order.rows"
            :columns="orderColumns"
            row-key="orderId"
            bordered
            :loading="order.loading"
            :pagination="false"
            :locale="{emptyText: '暂无客户订单明细'}"
            :scroll="{x: 1600}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderNo'">
              <!-- 复用订单详情，不造第二套详情页 -->
              <a v-if="record.orderId" @click="openOrder(record.orderId)">{{ record.orderNo }}</a>
              <span v-else>{{ record.orderNo ?? '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'confirmedAt'">
              {{ datetime(record.confirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'orderSource'">
              {{ enumDescText(record.orderSource, SCM_ORDER_SOURCE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'settleMode'">
              {{ enumDescText(record.settleMode, SETTLE_MODE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'lineCount'">
              <span class="num">{{ countText(record.lineCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'skuKindCount'">
              <span class="num">{{ countText(record.skuKindCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'settlementAmount'">
              <span class="num">{{ moneyText(record.settlementAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'completedRefundAmount'">
              <span class="num">{{ moneyText(record.completedRefundAmount) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="order.pageNum"
              v-model:page-size="order.pageSize"
              :total="order.total"
              @change="loadOrder"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
  </a-tabs>

  <OrderDetail ref="orderDetail"/>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
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
import {reportSalesApi} from '/@/api/business/scm/report-api';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {SCM_ORDER_PRICE_SOURCE_ENUM, SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import {SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {PRODUCT_TYPE_ENUM} from '/@/constants/business/scm/product-const';
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
    countText,
    createTabView,
    defaultDateRange,
    enumDescText,
    enterTab,
    optionLabel,
    rangeFromQuery,
    rangeOverLimitError,
    yesNoText,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';
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

const itemColumns = ref<TableColumnsType<SalesItemRow>>([
    {title: '确认时间', dataIndex: 'confirmedAt', width: 190},
    {title: '订单号', dataIndex: 'orderNo', width: 190},
    {title: '客户编码', dataIndex: 'customerCode', width: 140},
    {title: '客户名称', dataIndex: 'customerName', width: 180},
    {title: '销售员', dataIndex: 'sellerName', width: 110},
    {title: '订单来源', dataIndex: 'orderSource', width: 110},
    {title: '结算方式', dataIndex: 'settleMode', width: 110},
    {title: 'SPU 编码', dataIndex: 'spuCode', width: 140},
    {title: '商品名称', dataIndex: 'productName', width: 180},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: '规格', dataIndex: 'specName', width: 130},
    {title: '商品类型', dataIndex: 'productType', width: 100},
    {title: '销售单位', dataIndex: 'saleUnit', align: 'center', width: 90},
    {title: '订购数量', dataIndex: 'orderedQuantity', align: 'right', width: 120},
    {title: '实际数量', dataIndex: 'actualQuantity', align: 'right', width: 120},
    {title: '锁定成交单价', dataIndex: 'lockedUnitPrice', align: 'right', width: 140},
    {title: '价格来源', dataIndex: 'lockedPriceSource', width: 130},
    {title: '结算金额', dataIndex: 'settlementLineAmount', align: 'right', width: 140},
    {title: '是否手工改价', dataIndex: 'manualPriceOverride', align: 'center', width: 120},
    {title: '手工改价原因', dataIndex: 'manualPriceReason', width: 200},
]);

const orderColumns = ref<TableColumnsType<SalesOrderRow>>([
    {title: '确认时间', dataIndex: 'confirmedAt', width: 180},
    {title: '订单号', dataIndex: 'orderNo', width: 190},
    {title: '客户编码', dataIndex: 'customerCode', width: 150},
    {title: '客户名称', dataIndex: 'customerName', width: 200},
    {title: '销售员', dataIndex: 'sellerName', width: 120},
    {title: '订单来源', dataIndex: 'orderSource', width: 110},
    {title: '结算方式', dataIndex: 'settleMode', width: 120},
    {title: '订单行数', dataIndex: 'lineCount', align: 'right', width: 110},
    {title: 'SKU 种类数', dataIndex: 'skuKindCount', align: 'right', width: 120},
    {title: '结算金额', dataIndex: 'settlementAmount', align: 'right', width: 150},
    {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 160},
]);

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
    // 重置回到「本月」（计划 §28 的统一默认值），并把每个 Tab 的页码归零位
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

<style scoped>
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
