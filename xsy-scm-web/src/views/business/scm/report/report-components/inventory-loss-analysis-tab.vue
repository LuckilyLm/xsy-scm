<template>
  <a-row :gutter="[12, 12]">
    <a-col v-for="card in lossCards" :key="card.label" :xs="24" :sm="12" :md="8" :lg="6" :xl="4">
      <ReportKpiCard :label="card.label" :value="card.value" :hint="card.hint" :warning="card.warning"/>
    </a-col>
  </a-row>
  <a-row :gutter="[12, 12]" class="smart-margin-top10">
    <a-col :xs="24" :lg="12">
      <ReportPieChart
          title="损耗类型金额占比"
          :slices="lossPieSlices"
          extra="只统计盘亏与手工报损；盘盈与报溢是增益，不进损耗成本"
      />
    </a-col>
    <a-col :xs="24" :lg="12">
      <ReportLineChart
          title="损耗金额按日趋势"
          :x-axis="lossTrendAxis"
          :series="lossTrendSeries"
          empty-text="暂无按日损耗趋势"
      />
    </a-col>
  </a-row>
  <a-card size="small" :bordered="false" class="smart-margin-top10">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <ReportNote title="口径说明" :points="['只统计盘亏与手工报损两类损耗']"/>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_INVENTORY_LOSS"
            :refresh="refresh"
        />
      </div>
    </a-row>
    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action>
        <a-button @click="refresh">重试</a-button>
      </template>
    </a-alert>
    <a-table
        :id="SCM_REPORT_TABLE_ID.INVENTORY_LOSS"
        size="small"
        :data-source="rows"
        :columns="visibleColumns"
        row-key="movementId"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无损耗明细'}"
        :scroll="{x: 1800}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'movementType'">
          {{ enumDescText(record.movementType, SCM_REPORT_LOSS_TYPE_ENUM) }}
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitCost'">
          <span class="scm-money">{{ costText(record.unitCost, canViewCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'costAmount'">
          <span class="scm-money">{{ costText(record.costAmount, canViewCost) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'occurredAt'">
          {{ datetime(record.occurredAt) }}
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
          :total="total"
          @change="changePage"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ReportNote from '/@/components/business/scm/report-note/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_REPORT_LOSS_TYPE_ENUM,
  SCM_REPORT_PERMISSION,
  SCM_REPORT_TABLE_ID,
} from '/@/constants/business/scm/report-const';
import ReportKpiCard from './report-kpi-card.vue';
import ReportPieChart from './report-pie-chart.vue';
import ReportLineChart from './report-line-chart.vue';
import {datetime} from '../../common/scm-display';
import {moneyText, quantityText} from '../../inventory/inventory-model';
import {chartValue, costText, enumDescText, filterCostColumns, incompleteCostHint} from '../report-model';
import type {InventoryLossRow, InventoryLossSummary, ReportChartLine, ReportChartSlice} from '../report-types';

const props = defineProps<{
  summary?: InventoryLossSummary;
  rows: InventoryLossRow[];
  loading: boolean;
  error: string;
  canViewCost: boolean;
  pageNum: number;
  pageSize: number;
  total: number;
}>();

const emit = defineEmits<{
  export: [];
  reload: [];
  pageChange: [page: number, pageSize: number];
}>();

const columns = ref<TableColumnsType<InventoryLossRow>>([
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'skuName', width: 170},
  {title: '仓库', dataIndex: 'warehouseName', width: 150},
  {title: '损耗类型', dataIndex: 'movementType', width: 110},
  {title: '数量', dataIndex: 'quantity', align: 'right', width: 120},
  {title: '单位', dataIndex: 'unitSnapshot', align: 'center', width: 90},
  {title: '单位成本', dataIndex: 'unitCost', align: 'right', width: 130},
  {title: '损耗成本金额', dataIndex: 'costAmount', align: 'right', width: 150},
  {title: '来源单号', dataIndex: 'sourceDocumentNo', width: 190},
  {title: '发生时间', dataIndex: 'occurredAt', width: 190},
  {title: '操作人', dataIndex: 'operator', width: 120},
]);

const visibleColumns = computed(() => filterCostColumns(columns.value, ['unitCost', 'costAmount'], props.canViewCost));
const lossCards = computed(() => {
  const data = props.summary ?? {};
  const cards: Array<{label: string; value: string; hint?: string; warning?: string; cost?: boolean}> = [
    {
      label: '盘亏数量（按单位）',
      value: data.stocktakeLossQuantityText || '—',
      hint: 'STOCKTAKE_LOSS 流水的数量，按记账单位分组，不做跨单位相加',
    },
    {
      label: '盘亏成本金额',
      value: costText(data.stocktakeLossCostAmount, props.canViewCost),
      hint: 'SUM(quantity × unit_cost)',
      cost: true,
    },
    {
      label: '报损数量（按单位）',
      value: data.lossReportQuantityText || '—',
      hint: 'LOSS_REPORT 流水的数量，按记账单位分组',
    },
    {
      label: '报损成本金额',
      value: costText(data.lossReportCostAmount, props.canViewCost),
      hint: 'SUM(quantity × unit_cost)',
      cost: true,
    },
    {
      label: '损耗总成本金额',
      value: costText(data.totalLossCostAmount, props.canViewCost),
      hint: '盘亏 + 报损的合计，由后端聚合；不含盘盈与报溢',
      warning: incompleteCostHint(data.missingCostCount, '损耗成本金额'),
      cost: true,
    },
  ];
  return cards.filter((card) => !card.cost || props.canViewCost);
});

const lossPieSlices = computed<ReportChartSlice[]>(() => {
  const data = props.summary ?? {};
  if (!props.canViewCost) return [];
  return [
    {name: '盘亏', value: chartValue(data.stocktakeLossCostAmount), text: moneyText(data.stocktakeLossCostAmount)},
    {name: '报损', value: chartValue(data.lossReportCostAmount), text: moneyText(data.lossReportCostAmount)},
  ];
});

const lossTrendPoints = computed(() => props.summary?.dailyTrend ?? []);
const lossTrendAxis = computed(() => lossTrendPoints.value.map((point) => point.bizDate ?? ''));
const lossTrendSeries = computed<ReportChartLine[]>(() => {
  if (!props.canViewCost) return [];
  return [{
    name: '损耗金额',
    data: lossTrendPoints.value.map((point) => chartValue(point.totalLossCostAmount)),
    texts: lossTrendPoints.value.map((point) => moneyText(point.totalLossCostAmount)),
  }];
});

function refresh() {
  emit('reload');
}

function changePage(page: number, pageSize: number) {
  emit('pageChange', page, pageSize);
}
</script>
