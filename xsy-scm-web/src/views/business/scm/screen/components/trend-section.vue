<template>
  <div class="scm-trend">
    <!-- 统一区间切换：切换后三张图同时更新 -->
    <div class="scm-trend-head">
      <span class="scm-trend-title">趋势分析</span>
      <div class="scm-trend-toggle">
        <button
          v-for="item in RANGES"
          :key="item.value"
          type="button"
          class="scm-trend-tab"
          :class="{'is-active': range === item.value}"
          :aria-pressed="range === item.value"
          @click="emit('update:range', item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <div class="scm-trend-charts">
      <screen-panel title="销售趋势" flex flush>
        <template #extra>销售额 / 订单数</template>
        <div v-if="salesEmpty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-show="!salesEmpty" ref="salesEl" class="scm-chart" />
      </screen-panel>

      <screen-panel title="采购趋势" flex flush>
        <template #extra>采购额 / 采购单</template>
        <div v-if="purchaseEmpty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-show="!purchaseEmpty" ref="purchaseEl" class="scm-chart" />
      </screen-panel>

      <screen-panel title="库存趋势" flex flush>
        <template #extra>库存量 / 出入库</template>
        <div v-if="inventoryEmpty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-show="!inventoryEmpty" ref="inventoryEl" class="scm-chart" />
      </screen-panel>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import ScreenPanel from './screen-panel.vue';
import {useEcharts} from '../composables/use-echarts';
import {escapeScreenHtml, formatAmount, formatQty, formatScreenCompact, toChartValue} from '../format';
import type {ScreenRange, TrendData} from '../types';

/** 三张趋势图共用一个日期区间。 */
const props = defineProps<{
  trend: TrendData;
  range: ScreenRange;
}>();

const emit = defineEmits<{
  (e: 'update:range', value: ScreenRange): void;
}>();

const RANGES: Array<{label: string; value: ScreenRange}> = [
  {label: '近 7 天', value: '7d'},
  {label: '近 30 天', value: '30d'},
];

type SeriesKey = 'sales' | 'orders' | 'purchaseAmounts' | 'purchaseOrders' | 'inventoryQuantity' | 'inboundQuantity' | 'outboundQuantity';

function values(key: SeriesKey) {
  return (props.trend.dates ?? []).map((_, index) => toChartValue(props.trend[key]?.[index]));
}

function hasValues(...keys: SeriesKey[]) {
  return keys.some((key) => values(key).some((value) => value !== null));
}

const salesEmpty = computed(() => !hasValues('sales', 'orders'));
const purchaseEmpty = computed(() => !hasValues('purchaseAmounts', 'purchaseOrders'));
const inventoryEmpty = computed(() => !hasValues('inventoryQuantity', 'inboundQuantity', 'outboundQuantity'));

const salesEl = ref<HTMLElement>();
const purchaseEl = ref<HTMLElement>();
const inventoryEl = ref<HTMLElement>();

const salesChart = useEcharts(salesEl, {resizeOnAnimationFrame: true});
const purchaseChart = useEcharts(purchaseEl, {resizeOnAnimationFrame: true});
const inventoryChart = useEcharts(inventoryEl, {resizeOnAnimationFrame: true});

// 颜色与 variables.less 保持一致
const C = {
  text1: '#eaf6ff',
  text2: '#8fb7d9',
  text3: '#7d9dbd',
  cyan: '#27d7fe',
  blue: '#2f80ed',
  border: 'rgba(27, 77, 122, 0.6)',
};

const AXIS = {
  axisLine: {lineStyle: {color: C.border}},
  axisTick: {show: false},
  axisLabel: {color: C.text2, fontSize: 11, hideOverlap: true},
};

interface SeriesLabel {
  key: SeriesKey;
  name: string;
  money?: boolean;
}

function chartLayout(fields: SeriesLabel[], boundaryGap: boolean) {
  const t = props.trend;
  return {
    animation: false,
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(7, 30, 66, 0.96)',
      borderColor: '#2f6f9e',
      padding: [8, 12],
      textStyle: {color: C.text1, fontSize: 12},
      extraCssText: 'max-width:min(280px,100%);white-space:normal;overflow-wrap:anywhere;border-radius:6px;',
      formatter: (params: unknown) => {
        const index = (params as Array<{dataIndex?: number}>)[0]?.dataIndex;
        if (index === undefined) return '';
        const rows = fields.map(({key, name, money}) => {
          const value = t[key]?.[index];
          const text = toChartValue(value) === null ? '—' : money ? `¥${formatAmount(value)}` : formatQty(value);
          return `${name}：${text}`;
        });
        return `<b>${escapeScreenHtml(t.fullDates?.[index] ?? t.dates[index] ?? '')}</b><br/>${rows.join('<br/>')}`;
      },
    },
    legend: {
      top: 4,
      left: 10,
      itemWidth: 12,
      itemHeight: 6,
      itemGap: 14,
      textStyle: {color: C.text2, fontSize: 11},
      data: fields.map(({name}) => name),
    },
    grid: {left: 8, right: 8, top: 28, bottom: 6, containLabel: true},
    xAxis: {
      type: 'category',
      data: t.dates,
      boundaryGap,
      ...AXIS,
      axisLabel: {...AXIS.axisLabel, interval: Math.max(0, Math.ceil(t.dates.length / 7) - 1), showMaxLabel: true},
    },
    yAxis: [valueAxis(true), valueAxis(false)],
  };
}

function valueAxis(gridLines: boolean) {
  return {
    type: 'value',
    ...AXIS,
    splitNumber: 3,
    splitLine: {show: gridLines, lineStyle: {color: C.border, type: 'dashed'}},
    axisLabel: {...AXIS.axisLabel, color: gridLines ? C.text2 : C.text3, formatter: (value: number) => formatScreenCompact(formatQty(value))},
  };
}

/** 折线/面积用的公共 series 片段。 */
function areaStyle(color: string) {
  return {
    type: 'line',
    smooth: true,
    symbol: 'none',
    lineStyle: {width: 2, color},
    itemStyle: {color},
  };
}

function renderSales() {
  salesChart.setOption({
    ...chartLayout(
      [
        {key: 'sales', name: '销售额', money: true},
        {key: 'orders', name: '订单数'},
      ],
      false
    ),
    series: [
      {
        name: '销售额',
        ...areaStyle(C.cyan),
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {offset: 0, color: 'rgba(39, 215, 254, 0.34)'},
              {offset: 1, color: 'rgba(39, 215, 254, 0.02)'},
            ],
          },
        },
        data: values('sales'),
      },
      {
        name: '订单数',
        ...areaStyle(C.blue),
        yAxisIndex: 1,
        lineStyle: {width: 1.5, color: C.blue, type: 'dashed'},
        data: values('orders'),
      },
    ],
  });
}

function renderPurchase() {
  purchaseChart.setOption({
    ...chartLayout(
      [
        {key: 'purchaseAmounts', name: '采购额', money: true},
        {key: 'purchaseOrders', name: '采购单'},
      ],
      true
    ),
    series: [
      {
        name: '采购额',
        type: 'bar',
        barMaxWidth: 14,
        itemStyle: {
          borderRadius: [2, 2, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {offset: 0, color: C.blue},
              {offset: 1, color: 'rgba(47, 128, 237, 0.25)'},
            ],
          },
        },
        data: values('purchaseAmounts'),
      },
      {
        name: '采购单',
        ...areaStyle(C.blue),
        yAxisIndex: 1,
        lineStyle: {width: 1.5, color: C.blue},
        data: values('purchaseOrders'),
      },
    ],
  });
}

function renderInventory() {
  inventoryChart.setOption({
    ...chartLayout(
      [
        {key: 'inventoryQuantity', name: '库存量'},
        {key: 'inboundQuantity', name: '入库'},
        {key: 'outboundQuantity', name: '出库'},
      ],
      false
    ),
    series: [
      {
        name: '库存量',
        ...areaStyle(C.cyan),
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {offset: 0, color: 'rgba(39, 215, 254, 0.3)'},
              {offset: 1, color: 'rgba(39, 215, 254, 0.02)'},
            ],
          },
        },
        data: values('inventoryQuantity'),
      },
      {
        name: '入库',
        ...areaStyle(C.blue),
        yAxisIndex: 1,
        lineStyle: {width: 1.5, color: C.blue},
        data: values('inboundQuantity'),
      },
      {
        name: '出库',
        ...areaStyle(C.text2),
        yAxisIndex: 1,
        lineStyle: {width: 1.5, color: C.text2},
        data: values('outboundQuantity'),
      },
    ],
  });
}

/** 趋势数据变化时重画三张图。 */
watch(
  () => props.trend,
  () => {
    if (!salesEmpty.value) renderSales();
    if (!purchaseEmpty.value) renderPurchase();
    if (!inventoryEmpty.value) renderInventory();
  },
  {immediate: true, deep: false}
);
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-trend {
  flex: 0 0 var(--screen-trend-height);
  height: var(--screen-trend-height);
  min-width: 0;
  margin-top: var(--screen-gap);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scm-trend-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.scm-trend-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: @text-1;
  letter-spacing: 0.5px;

  &::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 13px;
    margin-right: 7px;
    background: @tech-cyan;
    border-radius: 2px;
    vertical-align: -1px;
  }
}

.scm-trend-toggle {
  display: inline-flex;
  border: 1px solid @panel-border;
  border-radius: 6px;
  overflow: hidden;
}

.scm-trend-tab {
  padding: 3px 14px;
  font-size: 12px;
  color: @text-2;
  background: rgba(47, 128, 237, 0.08);
  border: 0;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s;

  & + & {
    border-left: 1px solid @panel-border;
  }

  &:hover {
    color: @tech-cyan;
  }

  &.is-active {
    color: @screen-bg;
    background: @tech-cyan;
    font-weight: 600;
  }
}

.scm-trend-charts {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: var(--screen-gap);

  > * {
    flex: 1 1 0%;
    min-width: 0;
  }
}

.scm-chart {
  flex: 1 1 0%;
  min-width: 0;
  min-height: 0;
  width: 100%;
}
</style>
