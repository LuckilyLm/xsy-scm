<template>
  <a-alert v-if="droppedSeriesCount > 0" type="warning" show-icon class="smart-margin-bottom10"
           :message="`${seriesCount} 条价格曲线，图中只画样本行数最多的 ${TREND_SERIES_LIMIT} 条`"
           description="展开高级筛选并按「商品规格」指定，可以把要看的那条曲线单独画出来。"/>
  <ReportLineChart
      class="smart-margin-bottom10"
      title="采购成交价波动"
      :x-axis="trendAxis"
      :series="trendSeries"
  />
  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PRICE_TREND"
            :refresh="refresh"
        />
      </div>
    </a-row>
    <a-table
        :id="SCM_REPORT_TABLE_ID.PURCHASE_PRICE_TREND"
        size="small"
        :data-source="pagedRows"
        :columns="columns"
        :row-key="(row: PurchasePriceTrendPoint) => `${row.bizDate}-${row.skuId}-${row.purchaseUnit}`"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{emptyText: '暂无价格波动数据'}"
        :scroll="{x: 1000}"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'bizDate'">
          {{ dateOnly(record.bizDate) }}
        </template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode ?? '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'weightedAvgPrice'">
          <span class="scm-money">{{ moneyText(record.weightedAvgPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sampleLineCount'">
          <span class="scm-quantity">{{ countText(record.sampleLineCount) }}</span>
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
          :total="rows.length"
          :show-total="(n: number) => `共${n}条`"
          @change="changePage"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import ReportLineChart from './report-line-chart.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_REPORT_PERMISSION, SCM_REPORT_TABLE_ID} from '/@/constants/business/scm/report-const';
import {dateOnly} from '../../common/scm-display';
import {moneyText} from '../../inventory/inventory-model';
import {chartValue, countText} from '../report-model';
import type {ReportChartLine, PurchasePriceTrendPoint} from '../report-types';

const props = defineProps<{
  rows: PurchasePriceTrendPoint[];
  loading: boolean;
  error: string;
}>();

const emit = defineEmits<{
  export: [];
  reload: [];
}>();

/**
 * 曲线条数上限。
 *
 * 其余的不合并成一条「其他」：不同采购单位的价格不能同线（箱价与公斤价取平均没有意义），
 * 造一条混合单位的线等于伪造事实，所以超出的直接不画，并由上方的 Warning 说明还剩几条。
 */
const TREND_SERIES_LIMIT = 12;

const columns = ref<TableColumnsType<PurchasePriceTrendPoint>>([
  {title: '业务日期', dataIndex: 'bizDate', width: 130},
  {title: '商品', dataIndex: 'productName', width: 200},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 170},
  {title: '采购单位', dataIndex: 'purchaseUnit', align: 'center', width: 110},
  {title: '加权平均成交价', dataIndex: 'weightedAvgPrice', align: 'right', width: 160},
  {title: '样本行数', dataIndex: 'sampleLineCount', align: 'right', width: 110},
]);

/** x 轴只包含返回结果中的业务日，按日期升序且不补空日。 */
const trendAxis = computed(() => [...new Set(props.rows.map((row) => row.bizDate ?? ''))].sort());

/** 每条线对应一个 SKU 与采购单位，避免把不可比的单位绘到同一条线上。 */
const trendGroups = computed(() => {
  const groups = new Map<string, PurchasePriceTrendPoint[]>();
  for (const row of props.rows) {
    const key = `${row.skuId ?? '未知'}|${row.purchaseUnit ?? '无单位'}`;
    const bucket = groups.get(key);
    if (bucket) {
      bucket.push(row);
    } else {
      groups.set(key, [row]);
    }
  }
  return [...groups.entries()];
});

/** 样本行数多寡代表这条曲线有多少笔成交支撑，行数相同的按规格编码排，保证每次刷新同序。 */
function groupSampleCount(rows: PurchasePriceTrendPoint[]): number {
  return rows.reduce((total, row) => total + (row.sampleLineCount ?? 0), 0);
}

const rankedGroups = computed(() => [...trendGroups.value].sort((left, right) => {
  const difference = groupSampleCount(right[1]) - groupSampleCount(left[1]);
  return difference !== 0 ? difference : String(left[1][0]?.skuCode ?? '').localeCompare(String(right[1][0]?.skuCode ?? ''));
}));

const seriesCount = computed(() => rankedGroups.value.length);
const droppedSeriesCount = computed(() => Math.max(0, seriesCount.value - TREND_SERIES_LIMIT));

const trendSeries = computed<ReportChartLine[]>(() => {
  const axis = trendAxis.value;
  return rankedGroups.value.slice(0, TREND_SERIES_LIMIT).map(([key, rows]) => {
    const byDate = new Map(rows.map((row) => [row.bizDate ?? '', row]));
    const label = rows[0]?.productName ?? key;
    return {
      name: `${label}（${rows[0]?.purchaseUnit ?? '无单位'}）`,
      data: axis.map((date) => chartValue(byDate.get(date)?.weightedAvgPrice)),
      texts: axis.map((date) => moneyText(byDate.get(date)?.weightedAvgPrice)),
    };
  });
});

/** 明细表是一次性返回的全量点，按当前页切片，总数就是返回行数。 */
const pageNum = ref(1);
const pageSize = ref(20);
const pagedRows = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value;
  return props.rows.slice(start, start + pageSize.value);
});

// 重新查询等于换了一份结果，页码留在上一页会显示成空表
watch(() => props.rows, () => {
  pageNum.value = 1;
});

function changePage(page: number, size: number) {
  pageNum.value = page;
  pageSize.value = size;
}

function refresh() {
  emit('reload');
}
</script>
