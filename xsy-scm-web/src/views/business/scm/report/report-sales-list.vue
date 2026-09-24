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
      <ReportBarChart
          class="smart-margin-bottom10"
          title="商品确认订单金额 TOP5"
          :items="topItems(productTop)"
          series-name="确认订单金额"
          extra="按已确认订单金额降序，由数据库直接取前 5 名"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportProduct">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              一行 = SKU × 销售单位；确认数量按各自单位统计，不做跨单位合计。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="productColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_PRODUCT"
                :refresh="loadProduct"
            />
          </div>
        </a-row>
        <a-alert v-if="product.error" :message="product.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadProduct">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_PRODUCT"
            size="small"
            :data-source="product.rows"
            :columns="productColumns"
            row-key="skuId"
            bordered
            :loading="product.loading"
            :pagination="false"
            :locale="{emptyText: '暂无商品销售数据'}"
            :scroll="{x: 1720}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'customerCount'">
              <span class="num">{{ countText(record.customerCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'confirmedQuantity'">
              <span class="num">{{ quantityText(record.confirmedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'avgTransactionPrice'">
              <span class="num">{{ moneyText(record.avgTransactionPrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'settlementAmount'">
              <span class="num">{{ moneyText(record.settlementAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'amountRank'">
              <span class="num">{{ countText(record.amountRank) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="product.pageNum"
              v-model:page-size="product.pageSize"
              :total="product.total"
              @change="loadProduct"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 按分类 ==================== -->
    <a-tab-pane key="category" tab="按分类">
      <ReportBarChart
          class="smart-margin-bottom10"
          title="分类确认订单金额 TOP5"
          :items="topItems(categoryTop)"
          series-name="确认订单金额"
          extra="一级 + 末级分类；不含「实际金额」这类当前无定义的字段"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              三级分类无 path 列，按 parent_id 上卷；金额是该分类节点及其子孙的合计。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="categoryColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_CATEGORY"
                :refresh="loadCategory"
            />
          </div>
        </a-row>
        <a-alert v-if="category.error" :message="category.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadCategory">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_CATEGORY"
            size="small"
            :data-source="category.rows"
            :columns="categoryColumns"
            row-key="categoryId"
            bordered
            :loading="category.loading"
            :pagination="false"
            :locale="{emptyText: '暂无分类销售数据'}"
            :scroll="{x: 900}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'settlementAmount'">
              <span class="num">{{ moneyText(record.settlementAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'customerCount'">
              <span class="num">{{ countText(record.customerCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'amountRank'">
              <span class="num">{{ countText(record.amountRank) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="category.pageNum"
              v-model:page-size="category.pageSize"
              :total="category.total"
              @change="loadCategory"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 按客户 ==================== -->
    <a-tab-pane key="customer" tab="按客户">
      <ReportBarChart
          class="smart-margin-bottom10"
          title="客户确认订单金额 TOP5"
          :items="topItems(customerTop)"
          series-name="确认订单金额"
          extra="退款按 order_refund.customer_id 独立聚合，不经订单行 JOIN"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportCustomer">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              本页不展示已收 / 未收 / 应收余额：R0 还没有收款与核销事实。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="customerColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_CUSTOMER"
                :refresh="loadCustomer"
            />
          </div>
        </a-row>
        <a-alert v-if="customer.error" :message="customer.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadCustomer">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_CUSTOMER"
            size="small"
            :data-source="customer.rows"
            :columns="customerColumns"
            row-key="customerId"
            bordered
            :loading="customer.loading"
            :pagination="false"
            :locale="{emptyText: '暂无客户销售数据'}"
            :scroll="{x: 1400}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
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
            <template v-else-if="column.dataIndex === 'lastConfirmedAt'">
              {{ datetime(record.lastConfirmedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'amountRank'">
              <span class="num">{{ countText(record.amountRank) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="customer.pageNum"
              v-model:page-size="customer.pageSize"
              :total="customer.total"
              @change="loadCustomer"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 按销售员 ==================== -->
    <a-tab-pane key="seller" tab="按销售员">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              这是「销售员订单业绩」，不是收入、利润或提成；`seller_id` 为空的单归入「未分配销售员」。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="sellerColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_SALES_SELLER"
                :refresh="loadSeller"
            />
          </div>
        </a-row>
        <a-alert v-if="seller.error" :message="seller.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadSeller">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.SALES_SELLER"
            size="small"
            :data-source="seller.rows"
            :columns="sellerColumns"
            row-key="sellerId"
            bordered
            :loading="seller.loading"
            :pagination="false"
            :locale="{emptyText: '暂无销售员业绩数据'}"
            :scroll="{x: 1000}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'customerCount'">
              <span class="num">{{ countText(record.customerCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'settlementAmount'">
              <span class="num">{{ moneyText(record.settlementAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'completedRefundAmount'">
              <span class="num">{{ moneyText(record.completedRefundAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lastConfirmedAt'">
              {{ datetime(record.lastConfirmedAt) }}
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="seller.pageNum"
              v-model:page-size="seller.pageSize"
              :total="seller.total"
              @change="loadSeller"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
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
import ReportBarChart from './report-components/report-bar-chart.vue';
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

type SalesTab = 'product' | 'category' | 'customer' | 'seller' | 'item';

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

/** 有导出口径的是商品 / 客户 / 明细三个 Tab（计划 §30 只给了这三个导出端点），
 *  分类与销售员 Tab 因此不放导出按钮，避免点出 404。 */

const productColumns = ref<TableColumnsType<SalesProductRow>>([
    {title: '商品名称', dataIndex: 'productName', width: 200},
    {title: '一级分类', dataIndex: 'rootCategoryName', width: 140},
    {title: '末级分类', dataIndex: 'leafCategoryName', width: 140},
    {title: 'SPU 编码', dataIndex: 'spuCode', width: 150},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: '规格', dataIndex: 'specName', width: 140},
    {title: '销售单位', dataIndex: 'saleUnit', align: 'center', width: 100},
    {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 110},
    {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 100},
    {title: '确认数量', dataIndex: 'confirmedQuantity', align: 'right', width: 130},
    {title: '成交均价', dataIndex: 'avgTransactionPrice', align: 'right', width: 130},
    {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 160},
    {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

const categoryColumns = ref<TableColumnsType<SalesCategoryRow>>([
    {title: '一级分类', dataIndex: 'rootCategoryName', width: 180},
    {title: '末级分类', dataIndex: 'leafCategoryName', width: 180},
    {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 170},
    {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 120},
    {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 110},
    {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

const customerColumns = ref<TableColumnsType<SalesCustomerRow>>([
    {title: '客户编码', dataIndex: 'customerCode', width: 150},
    {title: '客户名称', dataIndex: 'customerName', width: 200},
    {title: '销售员', dataIndex: 'sellerName', width: 120},
    {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 110},
    {title: 'SKU 种类数', dataIndex: 'skuKindCount', align: 'right', width: 120},
    {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 160},
    {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 160},
    {title: '最近确认时间', dataIndex: 'lastConfirmedAt', width: 190},
    {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

const sellerColumns = ref<TableColumnsType<SalesSellerRow>>([
    {title: '销售员', dataIndex: 'sellerName', width: 160},
    {title: '订单笔数', dataIndex: 'orderCount', align: 'right', width: 120},
    {title: '客户数', dataIndex: 'customerCount', align: 'right', width: 110},
    {title: '确认订单金额', dataIndex: 'settlementAmount', align: 'right', width: 170},
    {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 170},
    {title: '最近确认时间', dataIndex: 'lastConfirmedAt', width: 190},
]);

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

function exportCustomer() {
    void reportSalesApi.customerExport(exportQuery());
}

function exportItem() {
    void reportSalesApi.itemExport(exportQuery());
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
