<!--
  采购分析（Finance R0 计划 §11–§16 + §26 的 R0-B 价格波动）：
  采购概览 / 按商品 / 按供应商 / 按采购员 / 采购明细 / 价格波动。

  本页刻意**没有**「应付金额」：采购单金额是采购承诺，收货参考金额是履约事实，
  两者都不等于应付 —— 应付需要收货/入账时点与核销规则，R0 没有这些事实，不能提前命名。

  日期是「提交日期」：草稿没有承诺量也没有价格事实，因此默认统计
  SUBMITTED / PARTIALLY_RECEIVED / RECEIVED / SHORT_CLOSED，排除 DRAFT 与 CANCELLED（SQL 固定）。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
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
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / SPU 编码 / SKU 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item v-if="activeTab === 'trend'" label="SKU" class="smart-query-form-item">
        <SkuSelect v-model:value="filters.skuId" width="240px" placeholder="按 SKU 看价格曲线"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="chartError" :message="chartError" type="error" show-icon>
    <template #action>
      <a-button @click="queryActiveTab">重试</a-button>
    </template>
  </a-alert>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <!-- ==================== 采购概览 ==================== -->
    <a-tab-pane key="overview" tab="采购概览">
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
          extra="来源 PURCHASE_IN 流水 → 收货行 → 采购行 → 采购单 → 供应商，不是采购单金额"
      />
    </a-tab-pane>

    <!-- ==================== 按商品 ==================== -->
    <a-tab-pane key="product" tab="按商品">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportProduct">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              采购入库数量按库存记账单位分组，与采购单位可能不同，因此是文本且不做合计。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="productColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PRODUCT"
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
            :id="SCM_REPORT_TABLE_ID.PURCHASE_PRODUCT"
            size="small"
            :data-source="product.rows"
            :columns="visibleProductColumns"
            row-key="skuId"
            bordered
            :loading="product.loading"
            :pagination="false"
            :locale="{emptyText: '暂无商品采购数据'}"
            :scroll="{x: 1900}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'plannedQuantity'">
              <span class="num">{{ quantityText(record.plannedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="num">{{ quantityText(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderAmount'">
              <span class="num">{{ moneyText(record.orderAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'avgPurchasePrice'">
              <span class="num">{{ moneyText(record.avgPurchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'inboundQuantityText'">
              {{ textOrDash(record.inboundQuantityText) }}
            </template>
            <template v-else-if="column.dataIndex === 'inboundCostAmount'">
              <span class="num">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
              <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                         :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
                <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
              </a-tooltip>
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

    <!-- ==================== 按供应商 ==================== -->
    <a-tab-pane key="supplier" tab="按供应商">
      <ReportBarChart
          class="smart-margin-bottom10"
          title="供应商采购入库成本 TOP10"
          :items="topItems(supplierTopRows)"
          series-name="采购入库成本金额"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportSupplier">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              点供应商名称，右侧抽屉看该供应商的商品维度明细。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="supplierColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_SUPPLIER"
                :refresh="loadSupplier"
            />
          </div>
        </a-row>
        <a-alert v-if="supplier.error" :message="supplier.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadSupplier">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.PURCHASE_SUPPLIER"
            size="small"
            :data-source="supplier.rows"
            :columns="visibleSupplierColumns"
            row-key="supplierId"
            bordered
            :loading="supplier.loading"
            :pagination="false"
            :locale="{emptyText: '暂无供应商采购数据'}"
            :scroll="{x: 1600}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'supplierName'">
              <a v-if="record.supplierId" @click="openSupplierDrilldown(record)">{{ record.supplierName }}</a>
              <span v-else>{{ record.supplierName ?? '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'skuKindCount'">
              <span class="num">{{ countText(record.skuKindCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderAmount'">
              <span class="num">{{ moneyText(record.orderAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
              <span class="num">{{ moneyText(record.receiptReferenceAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'inboundCostAmount'">
              <span class="num">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
              <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                         :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
                <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
              </a-tooltip>
            </template>
            <template v-else-if="column.dataIndex === 'lastSubmittedAt'">
              {{ datetime(record.lastSubmittedAt) }}
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
              v-model:current="supplier.pageNum"
              v-model:page-size="supplier.pageSize"
              :total="supplier.total"
              @change="loadSupplier"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 按采购员 ==================== -->
    <a-tab-pane key="purchaser" tab="按采购员">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              点采购员名称，右侧抽屉看该采购员的商品维度明细；这是业绩与成本视角，不是提成。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="purchaserColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PURCHASER"
                :refresh="loadPurchaser"
            />
          </div>
        </a-row>
        <a-alert v-if="purchaser.error" :message="purchaser.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadPurchaser">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.PURCHASE_PURCHASER"
            size="small"
            :data-source="purchaser.rows"
            :columns="visiblePurchaserColumns"
            row-key="purchaserId"
            bordered
            :loading="purchaser.loading"
            :pagination="false"
            :locale="{emptyText: '暂无采购员数据'}"
            :scroll="{x: 1400}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'purchaserName'">
              <a @click="openPurchaserDrilldown(record)">{{ record.purchaserName ?? '未分配采购员' }}</a>
            </template>
            <template v-else-if="column.dataIndex === 'orderCount'">
              <span class="num">{{ countText(record.orderCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'skuKindCount'">
              <span class="num">{{ countText(record.skuKindCount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'orderAmount'">
              <span class="num">{{ moneyText(record.orderAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receiptReferenceAmount'">
              <span class="num">{{ moneyText(record.receiptReferenceAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'inboundCostAmount'">
              <span class="num">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
              <a-tooltip v-if="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')"
                         :title="incompleteCostHint(record.inboundCostMissingCount, '采购入库成本金额')">
                <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
              </a-tooltip>
            </template>
            <template v-else-if="column.dataIndex === 'lastSubmittedAt'">
              {{ datetime(record.lastSubmittedAt) }}
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
              show-size-changer
              show-quick-jumper
              v-model:current="purchaser.pageNum"
              v-model:page-size="purchaser.pageSize"
              :total="purchaser.total"
              @change="loadPurchaser"
              :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <!-- ==================== 采购明细 ==================== -->
    <a-tab-pane key="item" tab="采购明细">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" @click="exportItem">导出</a-button>
            <a-typography-text type="secondary" class="smart-margin-left10">
              一行 = 一个采购订单行；采购单号可回原采购单详情，不造第二套详情页。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="itemColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_ITEM"
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
            :id="SCM_REPORT_TABLE_ID.PURCHASE_ITEM"
            size="small"
            :data-source="item.rows"
            :columns="itemColumns"
            row-key="purchaseOrderItemId"
            bordered
            :loading="item.loading"
            :pagination="false"
            :locale="{emptyText: '暂无采购明细'}"
            :scroll="{x: 2300}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'submittedAt'">
              {{ datetime(record.submittedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'orderNo'">
              <a v-if="record.purchaseOrderId" @click="openPurchaseOrder(record.purchaseOrderId)">{{ record.orderNo }}</a>
              <span v-else>{{ record.orderNo ?? '—' }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag>{{ enumDescText(record.status, SCM_PURCHASE_STATUS_ENUM) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'plannedArrivalDate'">
              {{ dateOnly(record.plannedArrivalDate) }}
            </template>
            <template v-else-if="column.dataIndex === 'plannedQuantity'">
              <span class="num">{{ quantityText(record.plannedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="num">{{ quantityText(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'purchasePrice'">
              <span class="num">{{ moneyText(record.purchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lineAmount'">
              <span class="num">{{ moneyText(record.lineAmount) }}</span>
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

    <!-- ==================== 价格波动（R0-B） ==================== -->
    <a-tab-pane key="trend" tab="价格波动">
      <ReportLineChart
          class="smart-margin-bottom10"
          title="采购成交价波动"
          :x-axis="trendAxis"
          :series="trendSeries"
          extra="同一天多笔按数量加权平均；不同采购单位永不合并成一条线"
      />
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-typography-text type="secondary">
              一行 = 业务日 × SKU × 采购单位。曲线太多时先用 SKU 筛选收窄。
            </a-typography-text>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
                v-model="trendColumns"
                :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PRICE_TREND"
                :refresh="loadTrend"
            />
          </div>
        </a-row>
        <a-alert v-if="trend.error" :message="trend.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadTrend">重试</a-button>
          </template>
        </a-alert>
        <a-table
            :id="SCM_REPORT_TABLE_ID.PURCHASE_PRICE_TREND"
            size="small"
            :data-source="trend.rows"
            :columns="trendColumns"
            :row-key="(row: PurchasePriceTrendPoint) => `${row.bizDate}-${row.skuId}-${row.purchaseUnit}`"
            bordered
            :loading="trend.loading"
            :pagination="false"
            :locale="{emptyText: '暂无价格波动数据'}"
            :scroll="{x: 1000}"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'bizDate'">
              {{ dateOnly(record.bizDate) }}
            </template>
            <template v-else-if="column.dataIndex === 'weightedAvgPrice'">
              <span class="num">{{ moneyText(record.weightedAvgPrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'sampleLineCount'">
              <span class="num">{{ countText(record.sampleLineCount) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
      </a-card>
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
        <span class="num">{{ countText(record.orderCount) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'plannedQuantity'">
        <span class="num">{{ quantityText(record.plannedQuantity) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'receivedQuantity'">
        <span class="num">{{ quantityText(record.receivedQuantity) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'orderAmount'">
        <span class="num">{{ moneyText(record.orderAmount) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'avgPurchasePrice'">
        <span class="num">{{ moneyText(record.avgPurchasePrice) }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'inboundQuantityText'">
        {{ textOrDash(record.inboundQuantityText) }}
      </template>
      <template v-else-if="column.dataIndex === 'inboundCostAmount'">
        <span class="num">{{ costText(record.inboundCostAmount, canViewCost) }}</span>
      </template>
      <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
    </template>
  </ReportDrilldownDrawer>

  <PurchaseOrderDetail ref="purchaseOrderDetail"/>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {ExclamationCircleOutlined} from '@ant-design/icons-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import PurchaseOrderDetail from '../purchase/components/purchase-order-detail-drawer.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import ReportBarChart from './report-components/report-bar-chart.vue';
import ReportLineChart from './report-components/report-line-chart.vue';
import ReportDrilldownDrawer from './report-components/report-drilldown-drawer.vue';
import {reportPurchaseApi} from '/@/api/business/scm/report-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {SCM_PURCHASE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
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
    ReportChartLine,
    ReportId,
} from './report-types';
import {
    buildReportQuery,
    chartValue,
    costText,
    countText,
    createTabView,
    defaultDateRange,
    enumDescText,
    enterTab,
    filterCostColumns,
    incompleteCostHint,
    rangeFromQuery,
    rangeOverLimitError,
    textOrDash,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import {dateOnly, datetime} from '../common/scm-display';
import type {TabView} from './report-model';
import {useReportPermission} from './use-report-permission';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();
const {canViewCost} = useReportPermission();

type PurchaseTab = 'overview' | 'product' | 'supplier' | 'purchaser' | 'item' | 'trend';

const activeTab = ref<PurchaseTab>('overview');
const dateRange = ref<DateRange | undefined>();
/** 共享筛选：六个维度共用同一张后端表单。 */
const filters = reactive<Omit<PurchaseQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const chartError = ref('');

const overview = ref<PurchaseOverview>();
const supplierTopRows = ref<PurchaseTopItem[]>([]);
const priceTrendRows = ref<PurchasePriceTrendPoint[]>([]);

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
    {title: 'SPU 编码', dataIndex: 'spuCode', width: 150},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: 'SKU 名称', dataIndex: 'skuName', width: 160},
    {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 100},
    {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 110},
    {title: '计划采购数量', dataIndex: 'plannedQuantity', align: 'right', width: 140},
    {title: '已收数量', dataIndex: 'receivedQuantity', align: 'right', width: 120},
    {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 160},
    {title: '采购成交均价', dataIndex: 'avgPurchasePrice', align: 'right', width: 140},
    {title: '采购入库数量', dataIndex: 'inboundQuantityText', width: 180},
    {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
]);

const supplierColumns = ref<TableColumnsType<PurchaseSupplierRow>>([
    {title: '供应商编码', dataIndex: 'supplierCode', width: 150},
    {title: '供应商名称', dataIndex: 'supplierName', width: 200},
    {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 110},
    {title: 'SKU 种类数', dataIndex: 'skuKindCount', align: 'right', width: 120},
    {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 160},
    {title: '已收参考金额', dataIndex: 'receiptReferenceAmount', align: 'right', width: 150},
    {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
    {title: '最近采购时间', dataIndex: 'lastSubmittedAt', width: 190},
    {title: '金额排名', dataIndex: 'amountRank', align: 'right', width: 110},
]);

const purchaserColumns = ref<TableColumnsType<PurchasePurchaserRow>>([
    {title: '采购员', dataIndex: 'purchaserName', width: 160},
    {title: '采购单数', dataIndex: 'orderCount', align: 'right', width: 120},
    {title: 'SKU 种类数', dataIndex: 'skuKindCount', align: 'right', width: 120},
    {title: '采购订单金额', dataIndex: 'orderAmount', align: 'right', width: 170},
    {title: '已收参考金额', dataIndex: 'receiptReferenceAmount', align: 'right', width: 160},
    {title: '采购入库成本金额', dataIndex: 'inboundCostAmount', align: 'right', width: 180},
    {title: '最近采购时间', dataIndex: 'lastSubmittedAt', width: 190},
]);

const itemColumns = ref<TableColumnsType<PurchaseItemRow>>([
    {title: '提交时间', dataIndex: 'submittedAt', width: 190},
    {title: '采购单号', dataIndex: 'orderNo', width: 190},
    {title: '状态', dataIndex: 'status', align: 'center', width: 110},
    {title: '供应商', dataIndex: 'supplierName', width: 180},
    {title: '采购员', dataIndex: 'purchaserName', width: 120},
    {title: '仓库', dataIndex: 'warehouseName', width: 150},
    {title: '计划到货日期', dataIndex: 'plannedArrivalDate', width: 130},
    {title: 'SPU 编码', dataIndex: 'spuCode', width: 140},
    {title: '商品', dataIndex: 'productName', width: 180},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: '规格', dataIndex: 'skuName', width: 140},
    {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 100},
    {title: '计划数量', dataIndex: 'plannedQuantity', align: 'right', width: 120},
    {title: '累计收货数量', dataIndex: 'receivedQuantity', align: 'right', width: 150},
    {title: '采购单价', dataIndex: 'purchasePrice', align: 'right', width: 130},
    {title: '采购行金额', dataIndex: 'lineAmount', align: 'right', width: 140},
]);

const trendColumns = ref<TableColumnsType<PurchasePriceTrendPoint>>([
    {title: '业务日期', dataIndex: 'bizDate', width: 130},
    {title: '商品', dataIndex: 'productName', width: 200},
    {title: 'SKU 编码', dataIndex: 'skuCode', width: 170},
    {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 110},
    {title: '加权平均成交价', dataIndex: 'weightedAvgPrice', align: 'right', width: 160},
    {title: '样本行数', dataIndex: 'sampleLineCount', align: 'right', width: 110},
]);

const visibleProductColumns = computed(() => filterCostColumns(productColumns.value, COST_INDEXES, canViewCost.value));
const visibleSupplierColumns = computed(() => filterCostColumns(supplierColumns.value, COST_INDEXES, canViewCost.value));
const visiblePurchaserColumns = computed(() =>
    filterCostColumns(purchaserColumns.value, COST_INDEXES, canViewCost.value)
);
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

/** x 轴取全部出现过的业务日并升序；后端按日聚合，不补空日。 */
const trendAxis = computed(() => [...new Set(priceTrendRows.value.map((row) => row.bizDate ?? ''))].sort());

/**
 * 一条线 = 一个 (SKU, 采购单位)。
 *
 * 单位必须进线的名字里：箱价与公斤价画在同一条线上没有任何意义，
 * 而把它们混在一张图里但不同名，用户看不出错在哪。
 */
const trendSeries = computed<ReportChartLine[]>(() => {
    const groups = new Map<string, PurchasePriceTrendPoint[]>();
    for (const row of priceTrendRows.value) {
        const key = `${row.skuId ?? '未知'}|${row.purchaseUnit ?? '无单位'}`;
        const bucket = groups.get(key);
        if (bucket) {
            bucket.push(row);
        } else {
            groups.set(key, [row]);
        }
    }
    const axis = trendAxis.value;
    return [...groups.entries()].map(([key, rows]) => {
        const byDate = new Map(rows.map((row) => [row.bizDate ?? '', row]));
        const label = rows[0]?.productName ?? key;
        return {
            name: `${label}（${rows[0]?.purchaseUnit ?? '无单位'}）`,
            data: axis.map((date) => chartValue(byDate.get(date)?.weightedAvgPrice)),
            texts: axis.map((date) => moneyText(byDate.get(date)?.weightedAvgPrice)),
        };
    });
});

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
    () => reportPurchaseApi.priceTrend(buildReportQuery<PurchaseQuery>(dateRange.value, {...filters})),
    (rows) => {
        priceTrendRows.value = rows ?? [];
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

/** §14：点供应商 → 右侧抽屉看该供应商的商品维度明细（带当前日期区间）。 */
function openSupplierDrilldown(row: PurchaseSupplierRow) {
    drilldownTarget.supplierId = row.supplierId;
    drilldownTarget.purchaserId = undefined;
    drilldown.title = `供应商「${row.supplierName ?? '—'}」商品采购明细`;
    enterTab(drilldown as TabView<PurchaseProductRow>);
    drilldown.open = true;
    void loadDrilldown();
}

/** §15：点采购员 → 同一张抽屉、同一套列。 */
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
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.report-warn-icon {
  color: var(--ant-color-warning);
  margin-left: 4px;
}
</style>
