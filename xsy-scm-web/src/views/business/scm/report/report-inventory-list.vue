<!-- 库存分析：流水、损耗、库存价值和收发存。成本字段按权限显示。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <!-- 当前库存价值是当前时点数据，这里不显示日期筛选；请求仍带日期，后端会忽略 -->
      <a-form-item v-if="activeTab !== 'value'" label="发生日期" class="smart-query-form-item">
        <ReportDateRangePicker v-model:value="dateRange" />
      </a-form-item>
      <a-form-item v-else label="库存价值" class="smart-query-form-item">
        <a-tag color="blue">当前时点，与查询区间无关</a-tag>
      </a-form-item>
      <a-form-item label="仓库" class="smart-query-form-item">
        <WarehouseSelect v-model:value="filters.warehouseId" width="180px" />
      </a-form-item>
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="filters.keyword" placeholder="商品名称 / 商品规格" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.INVENTORY_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
        <a-button class="smart-margin-left10" @click="advanced = !advanced">
          {{ advanced ? '收起高级筛选' : '展开高级筛选' }}
        </a-button>
      </a-form-item>
    </a-row>
    <a-row v-if="advanced" class="smart-query-form-row">
      <a-form-item label="流水类型" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.movementType" enum-name="SCM_INVENTORY_MOVEMENT_TYPE_ENUM" width="150px" />
      </a-form-item>
      <a-form-item label="来源单据类型" class="smart-query-form-item">
        <SmartEnumSelect v-model:value="filters.sourceDocumentType" enum-name="SCM_INVENTORY_SOURCE_TYPE_ENUM" width="180px" />
      </a-form-item>
      <a-form-item label="商品规格" class="smart-query-form-item">
        <SkuSelect v-model:value="filters.skuId" width="240px" />
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="chartError" :message="chartError" type="error" show-icon>
    <template #action>
      <a-button @click="queryActiveTab">重试</a-button>
    </template>
  </a-alert>

  <a-tabs v-model:activeKey="activeTab" class="smart-margin-top10" @change="onTabChange">
    <a-tab-pane key="movement" tab="库存流水">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" :loading="movementExport.exporting" @click="exportMovement">导出</a-button>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator
              v-model="movementColumns"
              :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_MOVEMENT"
              :refresh="loadMovement"
              :exclude-column-keys="canViewCost ? [] : COST_INDEXES"
            />
          </div>
        </a-row>
        <a-alert v-if="movement.error" :message="movement.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadMovement">重试</a-button>
          </template>
        </a-alert>
        <a-table
          :id="SCM_REPORT_TABLE_ID.INVENTORY_MOVEMENT"
          size="small"
          :data-source="movement.rows"
          :columns="visibleMovementColumns"
          row-key="movementId"
          bordered
          :loading="movement.loading"
          :pagination="false"
          :locale="{emptyText: '暂无库存流水'}"
          :scroll="{x: scmColumnsWidth(visibleMovementColumns)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'occurredAt'">
              {{ datetime(record.occurredAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'movementType'">
              {{ enumDescText(record.movementType, SCM_INVENTORY_MOVEMENT_TYPE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'direction'">
              <a-tag :color="directionOf(record.movementType) === 'IN' ? 'green' : 'orange'">
                {{ directionText(directionOf(record.movementType)) }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'sourceDocumentType'">
              {{ enumDescText(record.sourceDocumentType, SCM_INVENTORY_SOURCE_TYPE_ENUM) }}
            </template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'unitCost'">
              <span class="scm-money">{{ costText(record.unitCost, canViewCost) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'costAmount'">
              <span class="scm-money">{{ costText(record.costAmount, canViewCost, 'amount') }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'beforeQuantity'">
              <span class="scm-quantity">{{ quantityText(record.beforeQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'afterQuantity'">
              <span class="scm-quantity">{{ quantityText(record.afterQuantity) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
            show-quick-jumper
            v-model:current="movement.pageNum"
            v-model:page-size="movement.pageSize"
            :total="movement.total"
            :exporting="movement.exporting"
            @change="loadMovement"
            :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <a-tab-pane key="loss" tab="损耗分析">
      <InventoryLossAnalysisTab
        :summary="lossSummary"
        :rows="loss.rows"
        :loading="loss.loading"
        :error="loss.error"
        :can-view-cost="canViewCost"
        :page-num="loss.pageNum"
        :page-size="loss.pageSize"
        :total="loss.total"
        :exporting="loss.exporting"
        @export="exportLoss"
        @reload="loadLoss"
        @page-change="loadLossPage"
      />
    </a-tab-pane>

    <a-tab-pane v-if="canViewCost" key="value" tab="当前库存价值">
      <a-row v-if="hasValueSummary" :gutter="[12, 12]">
        <a-col v-for="card in valueCards" :key="card.label" :xs="24" :sm="12" :md="8">
          <ReportKpiCard :label="card.label" :value="card.value" :sub="card.sub" current-point />
        </a-col>
      </a-row>
      <a-card size="small" :bordered="false" :class="hasValueSummary ? 'smart-margin-top10' : ''">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button v-privilege="PERM.EXPORT" :loading="valueExport.exporting" @click="exportValue">导出</a-button>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator v-model="valueColumns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_VALUE" :refresh="loadValue" />
          </div>
        </a-row>
        <a-alert v-if="value.error" :message="value.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadValue">重试</a-button>
          </template>
        </a-alert>
        <a-table
          :id="SCM_REPORT_TABLE_ID.INVENTORY_VALUE"
          size="small"
          :data-source="value.rows"
          :columns="valueColumns"
          row-key="balanceId"
          bordered
          :loading="value.loading"
          :pagination="false"
          :locale="{emptyText: '暂无库存价值'}"
          :scroll="{x: scmColumnsWidth(valueColumns)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'quantity'">
              <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'reservedQuantity'">
              <span class="scm-quantity">{{ quantityText(record.reservedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'availableQuantity'">
              <span class="scm-quantity">{{ quantityText(record.availableQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'avgCost'">
              <span class="scm-money">{{ formatPrice(record.avgCost, 'cost') }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'amount'">
              <span class="scm-money">{{ moneyText(record.amount) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
            show-quick-jumper
            v-model:current="value.pageNum"
            v-model:page-size="value.pageSize"
            :total="value.total"
            :exporting="value.exporting"
            @change="loadValue"
            :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <a-tab-pane key="flow" tab="收发存（数量版）">
      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block"></div>
          <div class="smart-table-setting-block">
            <TableOperator v-model="flowColumns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_FLOW_SUMMARY" :refresh="loadFlow" />
          </div>
        </a-row>
        <a-alert v-if="flow.error" :message="flow.error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button @click="loadFlow">重试</a-button>
          </template>
        </a-alert>
        <a-table
          :id="SCM_REPORT_TABLE_ID.INVENTORY_FLOW_SUMMARY"
          size="small"
          :data-source="flow.rows"
          :columns="flowColumns"
          :row-key="(row: InventoryFlowSummaryRow) => `${row.warehouseId}-${row.skuId}-${row.unit}`"
          bordered
          :loading="flow.loading"
          :pagination="false"
          :locale="{emptyText: '暂无收发存数据'}"
          :scroll="{x: scmColumnsWidth(flowColumns)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="QUANTITY_INDEXES.includes(String(column.dataIndex))">
              <span class="scm-quantity">{{ quantityText(record[column.dataIndex]) }}</span>
            </template>
            <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
          </template>
        </a-table>
        <div class="smart-query-table-page">
          <a-pagination
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
            show-quick-jumper
            v-model:current="flow.pageNum"
            v-model:page-size="flow.pageSize"
            :total="flow.total"
            @change="loadFlow"
            :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import {message, type TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ReportDateRangePicker from './report-components/report-date-range-picker.vue';
import ReportKpiCard from './report-components/report-kpi-card.vue';
import InventoryLossAnalysisTab from './report-components/inventory-loss-analysis-tab.vue';
import {reportInventoryApi} from '/@/api/business/scm/report-api';
import {useScmExport} from '../common/use-scm-export';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {
  SCM_INVENTORY_MOVEMENT_INBOUND_TYPES,
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
} from '/@/constants/business/scm/inventory-const';
import type {
  InventoryFlowSummaryRow,
  InventoryLossRow,
  InventoryLossSummary,
  InventoryMovementRow,
  InventoryReportQuery,
  InventoryValueRow,
  ReportFilterQuery,
} from './report-types';
import {
  buildReportQuery,
  costText,
  countText,
  createTabView,
  defaultDateRange,
  directionText,
  enumDescText,
  enterTab,
  filterCostColumns,
  movementDirection,
  rangeFromQuery,
  rangeOverLimitError,
} from './report-model';
import {moneyText, quantityText} from '../inventory/inventory-model';
import {formatPrice} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';
import {useReportPermission} from './use-report-permission';
import {createGuardedLoader, createTabLoader} from './use-report-query';
import type {DateRange} from './report-model';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

const PERM = SCM_REPORT_PERMISSION;
const route = useRoute();
const {canViewCost} = useReportPermission();

type InventoryTab = 'movement' | 'loss' | 'value' | 'flow';

const activeTab = ref<InventoryTab>('movement');
const dateRange = ref<DateRange | undefined>();
const filters = reactive<Omit<InventoryReportQuery, 'pageNum' | 'pageSize' | 'startDate' | 'endDate'>>({});
const advanced = ref(false);
const chartError = ref('');

const lossSummary = ref<InventoryLossSummary>();
const valueSummary = ref<{
  bookValue?: string | null;
  stockedSkuCount?: number | null;
  zeroStockSkuCount?: number | null;
  snapshotAt?: string | null;
}>({});

const movement = reactive(createTabView<InventoryMovementRow>());
const loss = reactive(createTabView<InventoryLossRow>());
const value = reactive(createTabView<InventoryValueRow>());
const flow = reactive(createTabView<InventoryFlowSummaryRow>());
const lossExport = useScmExport(loss);
const movementExport = useScmExport(movement);
const valueExport = useScmExport(value);

/** 成本列受权限控制。 */
const COST_INDEXES = ['unitCost', 'costAmount'];

const movementColumns = ref<TableColumnsType<InventoryMovementRow>>([
  {title: '发生时间', dataIndex: 'occurredAt', width: 180},
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuCode', width: 170},
  {title: '流水类型', dataIndex: 'movementType', width: 120, align: 'center'},
  {title: '方向', dataIndex: 'direction', align: 'center', width: 80},
  {title: '来源单据类型', dataIndex: 'sourceDocumentType', width: 150, align: 'center'},
  {title: '来源单号', dataIndex: 'sourceDocumentNo', width: 190},
  {title: '数量', dataIndex: 'quantity', width: 120},
  {title: '单位', dataIndex: 'unitSnapshot', width: 90},
  {title: '单位成本', dataIndex: 'unitCost', width: 130},
  {title: '成本金额', dataIndex: 'costAmount', width: 140},
  {title: '变动前数量', dataIndex: 'beforeQuantity', width: 140},
  {title: '变动后数量', dataIndex: 'afterQuantity', width: 140},
  {title: '操作人', dataIndex: 'operator', width: 120},
]);

const valueColumns = ref<TableColumnsType<InventoryValueRow>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuCode', width: 170},
  {title: '单位', dataIndex: 'unit', width: 76},
  {title: '当前数量', dataIndex: 'quantity', width: 130},
  {title: '预留数量', dataIndex: 'reservedQuantity', width: 130},
  {title: '可用数量', dataIndex: 'availableQuantity', width: 130},
  {title: '当前移动平均成本', dataIndex: 'avgCost', width: 170},
  {title: '当前账面金额', dataIndex: 'amount', width: 148},
]);

/** 收发存的全部数量列；不同单位不可相加。 */
const QUANTITY_INDEXES = [
  'purchaseInQuantity',
  'salesOutQuantity',
  'stocktakeGainQuantity',
  'stocktakeLossQuantity',
  'gainReportQuantity',
  'lossReportQuantity',
  'transferInQuantity',
  'transferOutQuantity',
  'convertInQuantity',
  'convertOutQuantity',
  'netChangeQuantity',
];

const flowColumns = ref<TableColumnsType<InventoryFlowSummaryRow>>([
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuCode', width: 170},
  {title: '单位', dataIndex: 'unit', width: 76},
  {title: '期内采购入库数量', dataIndex: 'purchaseInQuantity', width: 160},
  {title: '期内销售出库数量', dataIndex: 'salesOutQuantity', width: 160},
  {title: '期内盘盈数量', dataIndex: 'stocktakeGainQuantity', width: 140},
  {title: '期内盘亏数量', dataIndex: 'stocktakeLossQuantity', width: 140},
  {title: '期内报溢数量', dataIndex: 'gainReportQuantity', width: 140},
  {title: '期内报损数量', dataIndex: 'lossReportQuantity', width: 140},
  {title: '期内调拨入', dataIndex: 'transferInQuantity', width: 130},
  {title: '期内调拨出', dataIndex: 'transferOutQuantity', width: 130},
  {title: '期内转换入', dataIndex: 'convertInQuantity', width: 130},
  {title: '期内转换出', dataIndex: 'convertOutQuantity', width: 130},
  {title: '期内净变动量', dataIndex: 'netChangeQuantity', width: 150},
]);

const visibleMovementColumns = computed(() => filterCostColumns(movementColumns.value, COST_INDEXES, canViewCost.value));

function directionOf(movementType: string | null | undefined): 'IN' | 'OUT' | null {
  return movementDirection(movementType, SCM_INVENTORY_MOVEMENT_INBOUND_TYPES);
}

/** 后端没给汇总就不显示指标卡，不用当页数据加总。 */
const hasValueSummary = computed(() => Object.values(valueSummary.value).some((item) => item !== undefined && item !== null));
const valueCards = computed(() => {
  const data = valueSummary.value;
  return [
    {
      label: '当前库存账面金额',
      value: costText(data.bookValue, canViewCost.value, 'amount'),
      sub: data.snapshotAt ? `截至 ${datetime(data.snapshotAt)}` : undefined,
    },
    {label: '有库存商品规格数', value: countText(data.stockedSkuCount)},
    {label: '零库存商品规格数', value: countText(data.zeroStockSkuCount)},
  ];
});

function inventoryQuery(tab: {pageNum: number; pageSize: number}): InventoryReportQuery {
  return buildReportQuery<InventoryReportQuery>(dateRange.value, {...filters}, tab);
}

function exportQuery(): Partial<InventoryReportQuery> {
  return buildReportQuery<Partial<InventoryReportQuery>>(dateRange.value, {...filters});
}

const loadMovement = createTabLoader(movement, () => inventoryQuery(movement), reportInventoryApi.movementQuery);
const loadLoss = createTabLoader(loss, () => inventoryQuery(loss), reportInventoryApi.lossQuery);
const loadFlow = createTabLoader(flow, () => inventoryQuery(flow), reportInventoryApi.flowSummaryQuery);

function loadLossPage(pageNum: number, pageSize: number) {
  loss.pageNum = pageNum;
  loss.pageSize = pageSize;
  void loadLoss();
}

const loadLossSummary = createGuardedLoader(
  (options) => reportInventoryApi.lossSummary(buildReportQuery<ReportFilterQuery<InventoryReportQuery>>(dateRange.value, {...filters}), options),
  (data) => (lossSummary.value = data),
  chartError
);

/** 价值页按当前时点取数；日期仍要带，后端会忽略。 */
const loadValue = createTabLoader(
  value,
  () => inventoryQuery(value),
  (query) =>
    reportInventoryApi.valueQuery(query).then((response) => {
      valueSummary.value = {
        bookValue: response.data.bookValue,
        stockedSkuCount: response.data.stockedSkuCount,
        zeroStockSkuCount: response.data.zeroStockSkuCount,
        snapshotAt: response.data.snapshotAt,
      };
      return response;
    })
);

function queryActiveTab() {
  const overLimit = rangeOverLimitError(dateRange.value);
  if (overLimit) {
    chartError.value = overLimit;
    return;
  }
  chartError.value = '';
  switch (activeTab.value) {
    case 'movement':
      void loadMovement();
      break;
    case 'loss':
      void loadLoss();
      void loadLossSummary();
      break;
    case 'value':
      void loadValue();
      break;
    case 'flow':
      void loadFlow();
      break;
    default:
      break;
  }
}

function enterActiveTab() {
  switch (activeTab.value) {
    case 'movement':
      enterTab(movement);
      break;
    case 'loss':
      enterTab(loss);
      break;
    case 'value':
      enterTab(value);
      break;
    case 'flow':
      enterTab(flow);
      break;
    default:
      break;
  }
}

function onTabChange() {
  // 没有成本权限时价值页不渲染；权限被收回就回落到流水
  if (activeTab.value === 'value' && !canViewCost.value) {
    activeTab.value = 'movement';
    enterTab(movement);
    queryActiveTab();
    return;
  }
  enterActiveTab();
  queryActiveTab();
}

function onSearch() {
  // 筛选条件变化后从第一页开始，避免停留在旧页码得到空表。
  enterActiveTab();
  queryActiveTab();
}

function resetQuery() {
  filters.warehouseId = undefined;
  filters.skuId = undefined;
  filters.keyword = undefined;
  filters.movementType = undefined;
  filters.sourceDocumentType = undefined;
  dateRange.value = defaultDateRange();
  enterTab(movement);
  enterTab(loss);
  enterTab(value);
  enterTab(flow);
  onSearch();
}

/** 导出与查询共用同一区间上限，避免用超限区间导出大文件。 */
function guardExport(): boolean {
  const overLimit = rangeOverLimitError(dateRange.value);
  if (overLimit) void message.warning(overLimit);
  return !overLimit;
}

function exportMovement() {
  if (!guardExport()) return;
  void movementExport.run(() => reportInventoryApi.movementExport(exportQuery()));
}

function exportLoss() {
  if (!guardExport()) return;
  void lossExport.run(() => reportInventoryApi.lossExport(exportQuery()));
}

function exportValue() {
  if (!guardExport()) return;
  void valueExport.run(() => reportInventoryApi.valueExport(exportQuery()));
}

onMounted(() => {
  dateRange.value = rangeFromQuery(route.query) ?? defaultDateRange();
  queryActiveTab();
});
</script>
