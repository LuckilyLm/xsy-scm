<!--
  经营概览：只读报表中心首页。

  三条不可动摇的口径，写在这里以免后来被「顺手改个名」破坏：
  1. 销售侧只统计 `CONFIRMED` + `confirmed_at` + `settlement_*`，因此卡片叫「已确认订单金额」，
     不是营业收入 / 销售收入 / 实收金额 —— 没有签收与收款事实；
  2. 「已完成退款金额」独立展示，不自动冲减订单金额；
  3. 「当前库存账面金额」是当前时点快照，与查询区间无关，必须带「截至 …」一起读。

  本页不提供任何写入口，也不做合计行：区间合计就是顶部指标卡（由后端按同一口径聚合），
  在前端把日统计再累加一遍会造出第二个真相，而「下单客户数」这类计数相加本身就是错的。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="确认日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange"/>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px"/>
      </a-form-item>
      <a-form-item label="客户" class="smart-query-form-item">
        <CustomerSelect v-model:value="filters.customerId" width="200px"/>
      </a-form-item>
      <a-form-item label="销售员" class="smart-query-form-item">
        <EmployeeSelect v-model:value="filters.sellerId" width="160px"/>
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

  <div class="overview-kpis smart-margin-top10">
    <ReportKpiCard v-for="card in kpiCards" :key="card.label"
                   :label="card.label" :value="card.value" :sub="card.sub" :warning="card.warning"
                   :current-point="card.currentPoint"/>
  </div>

  <ReportLineChart
      class="smart-margin-top10"
      title="每日金额趋势"
      :x-axis="trendAxis"
      :series="trendSeries"
  />

  <a-card size="small" :bordered="false" class="smart-margin-top10">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="PERM.EXPORT" @click="exportDaily">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_OVERVIEW_DAILY"
            :refresh="queryAll"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_REPORT_TABLE_ID.OVERVIEW_DAILY"
        size="small"
        :data-source="pagedDailyRows"
        :columns="visibleColumns"
        row-key="bizDate"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无每日统计'}"
        :scroll="{x: 1240, y: 420}"
    >
      <template #bodyCell="{ record, column }">
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
          <a-tooltip v-if="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')"
                     :title="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')">
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 下钻的四个维度（销售 / 采购 / 收货入库 / 库存）是完全平级的只读分析入口，
               没有任何依据说明其中两个更高频，因此不人为制造信息层级：
               四个入口统一收进一个「下钻」菜单，行内只留一个入口。
               跳转不涉及写操作，因此没有权限需要裁剪。 -->
          <div class="scm-table-actions">
            <ScmActionMore label="下钻" :actions="drilldownActions" @select="onDrilldown($event, record)"/>
          </div>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :current="pageNum"
          :page-size="pageSize"
          :total="dailyRows.length"
          :show-total="(n: number) => `共${n}条`"
          @change="changePage"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref, watch} from 'vue';
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
import {useScmErrorToast} from '../common/scm-error-toast';
import {reportError} from './report-errors';
import {useReportPermission} from './use-report-permission';
import type {DateRange} from './report-model';

/**
 * 权限码取自 `SCM_REPORT_PERMISSION` 常量而不是散落字面量：
 * 同一份码要在「按钮 `v-privilege`」「列裁剪」「Tab 可见性」三处判断，
 * 写三遍字面量就意味着改一次权限要找三个地方。
 */
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
const error = useScmErrorToast();

/** 每日统计由后端整段返回（一天一行），分页在前端切片，总数就是返回行数。 */
const pageNum = ref(1);
const pageSize = ref(20);
const pagedDailyRows = computed(() => {
    const start = (pageNum.value - 1) * pageSize.value;
    return dailyRows.value.slice(start, start + pageSize.value);
});

// 重新查询后留在上一页会看到空表
watch(dailyRows, () => {
    pageNum.value = 1;
});

function changePage(page: number, size: number) {
    pageNum.value = page;
    pageSize.value = size;
}

/** 竞态保护：慢的旧响应不得覆盖新结果（三个请求共用一次刷新令牌）。 */
let requestId = 0;

const columns = ref<TableColumnsType<ReportDailyStat>>([
    {title: '业务日期', dataIndex: 'bizDate', width: 130},
    {title: '已确认订单数', dataIndex: 'confirmedOrderCount', align: 'right', width: 130},
    {title: '下单客户数', dataIndex: 'customerCount', align: 'right', width: 120},
    {title: '已确认订单金额', dataIndex: 'confirmedOrderAmount', align: 'right', width: 160},
    {title: '已完成退款金额', dataIndex: 'completedRefundAmount', align: 'right', width: 160},
    {title: '已提交采购金额', dataIndex: 'submittedPurchaseAmount', align: 'right', width: 160},
    {title: '采购入库成本金额', dataIndex: 'purchaseInCostAmount', align: 'right', width: 180},
    {title: '下钻', dataIndex: 'action', align: 'center', fixed: 'right', width: 110},
]);

/**
 * 下钻的四个平级维度，按业务主线排序（销售 → 采购 → 收货入库 → 库存）。
 * 都是只读跳转、没有权限差异，因此不裁剪 `hidden` —— 与写操作型菜单不同。
 */
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

/** 成本列：无 `scm:report:cost:query` 时整列不出现（不是显示一串 `—`）。 */
const COST_INDEXES = ['purchaseInCostAmount'];
const visibleColumns = computed(() => filterCostColumns(columns.value, COST_INDEXES, canViewCost.value));

const query = computed(() => buildReportQuery<OverviewQuery>(dateRange.value, {...filters}));

const kpiCards = computed(() => {
    const data = overview.value ?? {};
    const cards: Array<{
        label: string;
        value: string;
        sub?: string;
        warning?: string;
        currentPoint?: boolean;
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
            sub: data.submittedPurchaseOrderCount === undefined
                ? undefined
                : `已提交采购单 ${countText(data.submittedPurchaseOrderCount)} 张`,
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
            currentPoint: true,
            cost: true,
        },
    ];
    return cards.filter((card) => !card.cost || canViewCost.value);
});

const trendAxis = computed(() => trendRows.value.map((row) => row.bizDate));

/**
 * 趋势固定三条：已确认订单金额 / 已完成退款金额 / 采购入库成本金额（.3）。
 * 不加「收入」「毛利」第四条线 —— 那些事实当前还不存在。
 */
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
        // 前端拦一道：跨度超过上限后端会以 41111 拒绝，先把「改哪里」说清楚
        loading.value = false;
        error.value = overLimit;
        return;
    }
    loading.value = true;
    error.value = '';
    const payload = query.value;
    try {
        const [overviewResponse, trendResponse, dailyResponse] = await Promise.all([
            reportOverviewApi.overview(payload),
            reportOverviewApi.trend(payload),
            reportOverviewApi.daily(payload),
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

/** 导出每日统计：与列表同一筛选、同一查询方法，成本 / 仓库字段按当前权限同样抹除。 */
function exportDaily() {
    void reportOverviewApi.dailyExport(query.value);
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

/** 带日期跳到分析页；目标页用 `rangeFromQuery` 还原区间，参数不合法时它自己回落本月。 */
function goAnalysis(target: AnalysisKey, bizDate: string) {
    const [startDate, endDate] = dayRange(bizDate);
    void router.push({path: ANALYSIS_PATH[target], query: {startDate, endDate}});
}

/**
 * 默认区间本月；从分析页「返回概览」时可以带 `?startDate&endDate` 回来，
 * 这样来回跳不需要重新选一次日期。URL 是可手改的输入，
 * 所以取值交给 `rangeFromQuery` 校验，半截或非法区间一律按未提供处理。
 */
onMounted(async () => {
    dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
    try {
        const tree = await productCategoryApi.tree();
        categories.value = tree.data ?? [];
    } catch {
        // 分类字典拉不到不该挡住概览：高级筛选少一个下拉，比整页空白好
        categories.value = [];
    }
    await queryAll();
});
</script>

<style scoped>
/* 七张卡（含成本权限才有的两张）按内容自适应，写死四列会让多出来的卡占满一整行。 */
.overview-kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.report-warn-icon {
  color: var(--scm-warning);
  margin-left: 4px;
}
</style>
