<template>
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
        <a-button v-privilege="SCM_REPORT_PERMISSION.EXPORT" @click="emit('export')">导出</a-button>
        <a-typography-text type="secondary" class="smart-margin-left10">
          一行 = 业务日 × 商品规格 × 采购单位。曲线太多时先用商品规格筛选收窄。
        </a-typography-text>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_REPORT_PURCHASE_PRICE_TREND"
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
        :id="SCM_REPORT_TABLE_ID.PURCHASE_PRICE_TREND"
        size="small"
        :data-source="rows"
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
        <template v-else-if="column.dataIndex === 'weightedAvgPrice'">
          <span class="scm-money">{{ moneyText(record.weightedAvgPrice) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'sampleLineCount'">
          <span class="scm-quantity">{{ countText(record.sampleLineCount) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
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
const trendSeries = computed<ReportChartLine[]>(() => {
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

function refresh() {
  emit('reload');
}
</script>
