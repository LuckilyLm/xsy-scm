<!-- 经营概览：只读。库存金额是当前时点数据，不受日期区间限制。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="确认日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange" />
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px" />
      </a-form-item>
      <a-form-item label="客户" class="smart-query-form-item">
        <CustomerSelect v-model:value="filters.customerId" width="200px" />
      </a-form-item>
      <a-form-item label="销售员" class="smart-query-form-item">
        <EmployeeSelect v-model:value="filters.sellerId" width="160px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.OVERVIEW_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="订单来源" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.orderSource" enum-name="SCM_ORDER_SOURCE_ENUM" width="150px" />
      </a-form-item>
      <a-form-item label="商品分类" class="smart-query-form-item">
        <CategorySelect v-model:value="filters.categoryId" :categories="categories" style="width: 220px" />
      </a-form-item>
      <a-form-item label="商品关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / 商品编码 / 商品规格编码" allow-clear @pressEnter="onSearch" />
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryAll">重试</a-button>
    </template>
  </a-alert>

  <!-- 区间指标和当前时点指标分开排，避免两种数字混在一起 -->
  <section v-for="band in kpiBands" :key="band.key" class="report-kpi-band">
    <h3 class="report-kpi-band__title">{{ band.title }}</h3>
    <div class="report-kpi-grid" :class="`report-kpi-grid--${band.key}`">
      <ReportKpiCard v-for="card in band.cards" :key="card.label" :label="card.label" :value="card.value" :sub="card.sub" :warning="card.warning" />
    </div>
  </section>

  <ReportLineChart class="smart-margin-top10" title="每日金额趋势" :x-axis="trendAxis" :series="trendSeries" />

  <a-card size="small" :bordered="false" class="smart-margin-top10">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="PERM.EXPORT" :loading="dailyExport.exporting" @click="exportDaily">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_OVERVIEW_DAILY" :refresh="queryAll" />
      </div>
    </a-row>

    <a-table
      :id="SCM_REPORT_TABLE_ID.OVERVIEW_DAILY"
      size="small"
      :data-source="dailyRows"
      :columns="visibleColumns"
      row-key="bizDate"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '暂无每日统计'}"
      :scroll="{x: scmColumnsWidth(visibleColumns), y: 420}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'bizDate'">
          <a @click="goAnalysis('sales', record.bizDate)">{{ record.bizDate }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedOrderCount'">
          <span class="scm-quantity">{{ countText(record.confirmedOrderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'customerCount'">
          <span class="scm-quantity">{{ countText(record.customerCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedOrderAmount'">
          <span class="scm-money">{{ moneyText(record.confirmedOrderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'completedRefundAmount'">
          <span class="scm-money">{{ moneyText(record.completedRefundAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'submittedPurchaseAmount'">
          <span class="scm-money">{{ moneyText(record.submittedPurchaseAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'purchaseInCostAmount'">
          <span class="scm-money">{{ costText(record.purchaseInCostAmount, canViewCost) }}</span>
          <a-tooltip
            v-if="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')"
            :title="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')"
          >
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true" />
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 四个下钻入口平级，收进一个菜单；只做跳转，无需按权限裁剪 -->
          <div class="scm-table-actions">
            <ScmActionMore label="下钻" :actions="drilldownActions" @select="onDrilldown($event, record)" />
          </div>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {ExclamationCircleOutlined} from '@ant-design/icons-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import ReportLineChart from './report-components/report-line-chart.vue';
import {reportOverviewApi} from '/@/api/business/scm/report-api';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import type {ProductCategory} from '/@/types/business/scm/product';
import type {OverviewQuery, ReportDailyStat, ReportOverview} from './report-types';
import {
  buildReportQuery,
  chartValue,
  costText,
  countText,
  dayRange,
  defaultDateRange,
  filterCostColumns,
  incompleteCostHint,
  rangeFromQuery,
  rangeOverLimitError,
} from './report-model';
import {moneyText} from '../inventory/inventory-model';
import {datetime} from '../common/scm-display';
import {useScmExport} from '../common/use-scm-export';
import {reportError} from './report-errors';
import {useReportPermission} from './use-report-permission';
import type {DateRange} from './report-model';

/** 权限码统一从常量取，按钮、列裁剪和页签可见性共用。 */
const PERM = SCM_REPORT_PERMISSION;

const router = useRouter();
const route = useRoute();
const {canViewCost} = useReportPermission();

const dateRange = ref<DateRange | undefined>();
const filters = reactive<Omit<OverviewQuery, 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const categories = ref<ProductCategory[]>([]);

const overview = ref<ReportOverview>();
const dailyRows = ref<ReportDailyStat[]>([]);
const trendRows = ref<ReportDailyStat[]>([]);
const loading = ref(false);
const error = ref('');

/** 只采用最后一次刷新结果，三个请求共用一个令牌。 */
let requestId = 0;

const columns = ref<TableColumnsType<ReportDailyStat>>([
  {title: '业务日期', dataIndex: 'bizDate', width: 130},
  {title: '已确认订单数', dataIndex: 'confirmedOrderCount', width: 92},
  {title: '下单客户数', dataIndex: 'customerCount', width: 92},
  {title: '已确认订单金额', dataIndex: 'confirmedOrderAmount', width: 148},
  {title: '已完成退款金额', dataIndex: 'completedRefundAmount', width: 148},
  {title: '已提交采购金额', dataIndex: 'submittedPurchaseAmount', width: 148},
  {title: '采购入库成本金额', dataIndex: 'purchaseInCostAmount', width: 148},
  {title: '下钻', dataIndex: 'action', align: 'center', fixed: 'right', width: 110},
]);

/** 四个下钻维度：销售、采购、收货入库、库存。只读跳转，不做权限裁剪。 */
const drilldownActions: ScmActionItem[] = [
  {key: 'sales', label: '销售分析'},
  {key: 'purchase', label: '采购分析'},
  {key: 'receipt', label: '收货入库'},
  {key: 'inventory', label: '库存分析'},
];

function onDrilldown(key: string, record: ReportDailyStat) {
  if (key === 'sales' || key === 'purchase' || key === 'receipt' || key === 'inventory') {
    goAnalysis(key, record.bizDate);
  }
}

/** 没有成本权限时整列不显示。 */
const COST_INDEXES = ['purchaseInCostAmount'];
const visibleColumns = computed(() => filterCostColumns(columns.value, COST_INDEXES, canViewCost.value));

const query = computed(() => buildReportQuery<OverviewQuery>(dateRange.value, {...filters}));
const dailyExport = useScmExport();

const kpiCards = computed(() => {
  const data = overview.value ?? {};
  const cards: Array<{
    label: string;
    value: string;
    sub?: string;
    warning?: string;
    /** 当前时点余额，不随查询区间变化。 */
    point?: boolean;
    cost?: boolean;
  }> = [
    {
      label: '已确认订单数',
      value: countText(data.confirmedOrderCount),
    },
    {
      label: '下单客户数',
      value: countText(data.customerCount),
    },
    {
      label: '已确认订单金额',
      value: moneyText(data.confirmedOrderAmount),
    },
    {
      label: '已完成退款金额',
      value: moneyText(data.completedRefundAmount),
    },
    {
      label: '已提交采购金额',
      value: moneyText(data.submittedPurchaseAmount),
      sub: data.submittedPurchaseOrderCount === undefined ? undefined : `已提交采购单 ${countText(data.submittedPurchaseOrderCount)} 张`,
    },
    {
      label: '采购入库成本金额',
      value: costText(data.purchaseInCostAmount, canViewCost.value),
      warning: incompleteCostHint(data.purchaseInCostMissingCount, '采购入库成本金额'),
      cost: true,
    },
    {
      label: '当前库存账面金额',
      value: costText(data.inventoryBookValue, canViewCost.value),
      sub: `截至 ${datetime(data.snapshotAt)}｜有账面库存 ${countText(data.stockedSkuCount)} 行`,
      point: true,
      cost: true,
    },
  ];
  return cards.filter((card) => !card.cost || canViewCost.value);
});

/** 区间统计随筛选变化，当前时点是余额；某段被裁空就整段不渲染。 */
const kpiBands = computed(() => {
  const cards = kpiCards.value;
  return [
    {key: 'range', title: '区间统计', cards: cards.filter((card) => !card.point)},
    {key: 'point', title: '当前时点', cards: cards.filter((card) => card.point)},
  ].filter((band) => band.cards.length > 0);
});

const trendAxis = computed(() => trendRows.value.map((row) => row.bizDate));

/** 趋势三条线：订单金额、退款金额、采购入库成本；没有成本权限时不加第三条。 */
const trendSeries = computed(() => {
  const rows = trendRows.value;
  const series = [
    {name: '已确认订单金额', pick: (row: ReportDailyStat) => row.confirmedOrderAmount},
    {name: '已完成退款金额', pick: (row: ReportDailyStat) => row.completedRefundAmount},
  ];
  if (canViewCost.value) {
    series.push({name: '采购入库成本金额', pick: (row: ReportDailyStat) => row.purchaseInCostAmount});
  }
  return series.map((item) => ({
    name: item.name,
    data: rows.map((row) => chartValue(item.pick(row))),
    texts: rows.map((row) => moneyText(item.pick(row))),
  }));
});

async function queryAll() {
  const id = ++requestId;
  const overLimit = rangeOverLimitError(dateRange.value);
  if (overLimit) {
    // 前端先拦：超限后端也会拒绝，这里先说清楚怎么改
    loading.value = false;
    error.value = overLimit;
    return;
  }
  loading.value = true;
  error.value = '';
  const payload = query.value;
  try {
    // 加载失败由页内 Alert 提示，不再弹全局 toast
    const quiet = {suppressGlobalErrorMessage: true};
    const [overviewResponse, trendResponse, dailyResponse] = await Promise.all([
      reportOverviewApi.overview(payload, quiet),
      reportOverviewApi.trend(payload, quiet),
      reportOverviewApi.daily(payload, quiet),
    ]);
    if (id !== requestId) {
      return;
    }
    overview.value = overviewResponse.data;
    trendRows.value = trendResponse.data ?? [];
    dailyRows.value = dailyResponse.data ?? [];
  } catch (e) {
    if (id === requestId) {
      error.value = reportError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryAll();
}

/** 导出每日统计：与列表同筛选，成本字段按权限处理。 */
function exportDaily() {
  void dailyExport.run(() => reportOverviewApi.dailyExport(query.value));
}

function resetQuery() {
  filters.warehouseId = undefined;
  filters.customerId = undefined;
  filters.sellerId = undefined;
  filters.orderSource = undefined;
  filters.categoryId = undefined;
  filters.keyword = undefined;
  dateRange.value = defaultDateRange();
  onSearch();
}

type AnalysisKey = 'sales' | 'purchase' | 'receipt' | 'inventory';

const ANALYSIS_PATH: Record<AnalysisKey, string> = {
  sales: '/report/report-sales-list',
  purchase: '/report/report-purchase-list',
  receipt: '/report/report-receipt-list',
  inventory: '/report/report-inventory-list',
};

/** 带日期跳到分析页；参数不合法时目标页回落到本月。 */
function goAnalysis(target: AnalysisKey, bizDate: string) {
  const [startDate, endDate] = dayRange(bizDate);
  void router.push({path: ANALYSIS_PATH[target], query: {startDate, endDate}});
}

/** 默认本月；从分析页返回时可带日期，取值交给 rangeFromQuery 校验。 */
onMounted(async () => {
  dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
  try {
    const tree = await productCategoryApi.tree();
    categories.value = tree.data ?? [];
  } catch {
    // 分类拉不到不影响概览，只是少一个筛选
    categories.value = [];
  }
  await queryAll();
});
</script>

<style scoped>
/* KPI 分段：段标题说明这组数字是区间统计还是当前时点 */
.report-kpi-band {
  margin-top: 12px;
}

.report-kpi-band__title {
  margin: 0 0 8px;
  color: var(--scm-text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.report-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

/* 时点指标通常只有一张，固定宽度 */
.report-kpi-grid--point {
  grid-template-columns: minmax(0, 320px);
  justify-content: start;
}

.report-warn-icon {
  color: var(--scm-warning);
  margin-left: 4px;
}
</style>
