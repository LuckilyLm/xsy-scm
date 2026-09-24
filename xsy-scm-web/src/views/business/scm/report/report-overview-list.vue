<!--
  经营概览（Finance R0 计划 §4）：只读报表中心首页。

  三条不可动摇的口径，写在这里以免后来被「顺手改个名」破坏：
  1. 销售侧只统计 `CONFIRMED` + `confirmed_at` + `settlement_*`，因此卡片叫「已确认订单金额」，
     **不是**营业收入 / 销售收入 / 实收金额 —— R0 没有签收与收款事实；
  2. 「已完成退款金额」独立展示，不自动冲减订单金额；
  3. 「当前库存账面金额」是**当前时点**快照，与查询区间无关，必须带「截至 …」一起读。

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
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / SPU 编码 / SKU 编码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryAll">重试</a-button>
    </template>
  </a-alert>

  <a-row :gutter="[12, 12]" class="smart-margin-top10">
    <a-col v-for="card in kpiCards" :key="card.label" :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
      <ReportKpiCard
          :label="card.label"
          :value="card.value"
          :hint="card.hint"
          :sub="card.sub"
          :warning="card.warning"
          :current-point="card.currentPoint"
      />
    </a-col>
  </a-row>

  <ReportLineChart
      class="smart-margin-top10"
      title="每日金额趋势"
      :x-axis="trendAxis"
      :series="trendSeries"
      extra="已确认订单金额 / 已完成退款金额 / 采购入库成本金额，按日；无单据的日也是零值"
  />

  <a-card size="small" :bordered="false" class="smart-margin-top10">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-typography-text type="secondary">
          每行的数与上方指标卡同一口径；点击行末链接可带着这一天跳到对应分析页。
        </a-typography-text>
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
        :data-source="dailyRows"
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
          <span class="num">{{ countText(record.confirmedOrderCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'customerCount'">
          <span class="num">{{ countText(record.customerCount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'confirmedOrderAmount'">
          <span class="num">{{ moneyText(record.confirmedOrderAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'completedRefundAmount'">
          <span class="num">{{ moneyText(record.completedRefundAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'submittedPurchaseAmount'">
          <span class="num">{{ moneyText(record.submittedPurchaseAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'purchaseInCostAmount'">
          <span class="num">{{ costText(record.purchaseInCostAmount, canViewCost) }}</span>
          <a-tooltip v-if="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')"
                     :title="incompleteCostHint(record.purchaseInCostMissingCount, '采购入库成本金额')">
            <ExclamationCircleOutlined class="report-warn-icon" aria-hidden="true"/>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button type="link" @click="goAnalysis('sales', record.bizDate)">销售</a-button>
            <a-button type="link" @click="goAnalysis('purchase', record.bizDate)">采购</a-button>
            <a-button type="link" @click="goAnalysis('receipt', record.bizDate)">收货入库</a-button>
            <a-button type="link" @click="goAnalysis('inventory', record.bizDate)">库存</a-button>
          </div>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
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
const error = ref('');

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
    {title: '下钻', dataIndex: 'action', align: 'right', fixed: 'right', width: 260},
]);

/** 成本列：无 `scm:report:cost:query` 时整列不出现（不是显示一串 `—`）。 */
const COST_INDEXES = ['purchaseInCostAmount'];
const visibleColumns = computed(() => filterCostColumns(columns.value, COST_INDEXES, canViewCost.value));

const query = computed(() => buildReportQuery<OverviewQuery>(dateRange.value, {...filters}));

const kpiCards = computed(() => {
    const data = overview.value ?? {};
    const cards: Array<{
        label: string;
        value: string;
        hint?: string;
        sub?: string;
        warning?: string;
        currentPoint?: boolean;
        cost?: boolean;
    }> = [
        {
            label: '已确认订单数',
            value: countText(data.confirmedOrderCount),
            hint: 'status=CONFIRMED 且确认时间落在区间内；不含待确认与已取消',
        },
        {
            label: '下单客户数',
            value: countText(data.customerCount),
            hint: '同一确认范围内的去重客户数',
        },
        {
            label: '已确认订单金额',
            value: moneyText(data.confirmedOrderAmount),
            hint: 'SUM(settlement_total_amount)：结算口径，不是下单口径，也不是收入',
        },
        {
            label: '已完成退款金额',
            value: moneyText(data.completedRefundAmount),
            hint: 'order_refund 中 COMPLETED 的退款额；独立展示，不冲减已确认订单金额',
        },
        {
            label: '已提交采购金额',
            value: moneyText(data.submittedPurchaseAmount),
            hint: '已提交 / 部分收货 / 已收货 / 少关单的采购单 SUM(total_amount)；不含草稿与取消',
            sub: data.submittedPurchaseOrderCount === undefined
                ? undefined
                : `已提交采购单 ${countText(data.submittedPurchaseOrderCount)} 张`,
        },
        {
            label: '采购入库成本金额',
            value: costText(data.purchaseInCostAmount, canViewCost.value),
            hint: 'PURCHASE_IN 流水的 SUM(quantity × unit_cost)，以入库时冻结的单位成本计',
            warning: incompleteCostHint(data.purchaseInCostMissingCount, '采购入库成本金额'),
            cost: true,
        },
        {
            label: '当前库存账面金额',
            value: costText(data.inventoryBookValue, canViewCost.value),
            hint: 'SUM(inventory_balance.quantity × avg_cost)：当前时点值，不受查询日期区间影响',
            sub: `截至 ${datetime(data.snapshotAt)}｜有账面库存 ${countText(data.stockedSkuCount)} 行`,
            currentPoint: true,
            cost: true,
        },
    ];
    return cards.filter((card) => !card.cost || canViewCost.value);
});

const trendAxis = computed(() => trendRows.value.map((row) => row.bizDate));

/**
 * 趋势固定三条：已确认订单金额 / 已完成退款金额 / 采购入库成本金额（计划 §4.3）。
 * 不加「收入」「毛利」第四条线 —— 那些事实在 R0 还不存在。
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
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.report-warn-icon {
  color: var(--ant-color-warning);
  margin-left: 4px;
}
</style>
