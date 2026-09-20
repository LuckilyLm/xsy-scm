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
          :class="{ 'is-active': range === item.value }"
          @click="emit('update:range', item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <div class="scm-trend-charts">
      <screen-panel title="销售趋势" flex flush>
        <template #extra>销售额 / 订单数</template>
        <div v-if="empty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-else ref="salesEl" class="scm-chart" />
      </screen-panel>

      <screen-panel title="采购趋势" flex flush>
        <template #extra>采购额 / 采购单</template>
        <div v-if="empty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-else ref="purchaseEl" class="scm-chart" />
      </screen-panel>

      <screen-panel title="库存趋势" flex flush>
        <template #extra>库存量 / 出入库</template>
        <div v-if="empty" class="scm-state">
          <span class="scm-state-icon">—</span>
          <span>暂无趋势数据</span>
        </div>
        <div v-else ref="inventoryEl" class="scm-chart" />
      </screen-panel>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import ScreenPanel from './screen-panel.vue';
import { useEcharts } from '../composables/use-echarts';
import { toNumber } from '../format';
import type { ScreenRange, TrendData } from '../types';

/**
 * 底部趋势带。
 *
 * <p><b>三张图共用一个区间状态</b>（设计稿：「点击以后三张图同时切换」），
 * 所以区间切换按钮放在**带子顶部**而不是每个面板各放一个 —— 各放一个会出现
 * 「销售看 7 天、采购看 30 天」这种没法解读的对比。
 *
 * <p>数据来自单次 {@code /scm/screen/data/trend} 调用：设计稿要求
 * 「不要大屏为了 7 张图调用十几个接口」。
 */
const props = defineProps<{
  trend: TrendData;
  range: ScreenRange;
}>();

const emit = defineEmits<{
  (e: 'update:range', value: ScreenRange): void;
}>();

const RANGES: Array<{ label: string; value: ScreenRange }> = [
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
];

const empty = computed(() => !props.trend.dates?.length);

const salesEl = ref<HTMLElement>();
const purchaseEl = ref<HTMLElement>();
const inventoryEl = ref<HTMLElement>();

const salesChart = useEcharts(salesEl);
const purchaseChart = useEcharts(purchaseEl);
const inventoryChart = useEcharts(inventoryEl);

// 与 styles/variables.less 保持一致（less 变量无法在 JS 里 import）
const C = {
  text1: '#eaf6ff',
  text2: '#8fb7d9',
  text3: '#587a9a',
  cyan: '#27d7fe',
  blue: '#2f80ed',
  green: '#34d399',
  warn: '#f6c344',
  orange: '#ff8a65',
  border: 'rgba(27, 77, 122, 0.6)',
};

const AXIS = {
  axisLine: { lineStyle: { color: C.border } },
  axisLabel: { color: C.text2, fontSize: 10 },
  splitLine: { lineStyle: { color: 'rgba(27, 77, 122, 0.35)' } },
};

const TOOLTIP = {
  trigger: 'axis',
  backgroundColor: 'rgba(7, 30, 66, 0.94)',
  borderColor: '#1b4d7a',
  textStyle: { color: C.text1, fontSize: 11 },
};

const LEGEND = {
  top: 2,
  right: 6,
  itemWidth: 10,
  itemHeight: 6,
  textStyle: { color: C.text2, fontSize: 10 },
};

/** 折线/面积用的公共 series 片段。 */
function areaStyle(color: string) {
  return {
    type: 'line',
    smooth: true,
    symbol: 'none',
    lineStyle: { width: 2, color },
    itemStyle: { color },
  };
}

function renderSales() {
  const t = props.trend;
  salesChart.setOption({
    tooltip: TOOLTIP,
    legend: { ...LEGEND, data: ['销售额', '订单数'] },
    grid: { left: 6, right: 6, top: 26, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: t.dates, boundaryGap: false, ...AXIS },
    yAxis: [
      { type: 'value', ...AXIS, splitLine: { show: false } },
      { type: 'value', ...AXIS, splitLine: { show: false }, axisLabel: { ...AXIS.axisLabel, color: C.text3 } },
    ],
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
              { offset: 0, color: 'rgba(39, 215, 254, 0.34)' },
              { offset: 1, color: 'rgba(39, 215, 254, 0.02)' },
            ],
          },
        },
        data: t.sales.map(toNumber),
      },
      {
        name: '订单数',
        ...areaStyle(C.warn),
        yAxisIndex: 1,
        lineStyle: { width: 1.5, color: C.warn, type: 'dashed' },
        data: t.orders.map(toNumber),
      },
    ],
  });
}

function renderPurchase() {
  const t = props.trend;
  purchaseChart.setOption({
    tooltip: TOOLTIP,
    legend: { ...LEGEND, data: ['采购额', '采购单'] },
    grid: { left: 6, right: 6, top: 26, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: t.dates, ...AXIS },
    yAxis: [
      { type: 'value', ...AXIS, splitLine: { show: false } },
      { type: 'value', ...AXIS, splitLine: { show: false }, axisLabel: { ...AXIS.axisLabel, color: C.text3 } },
    ],
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
              { offset: 0, color: C.blue },
              { offset: 1, color: 'rgba(47, 128, 237, 0.25)' },
            ],
          },
        },
        data: t.purchaseAmounts.map(toNumber),
      },
      {
        name: '采购单',
        ...areaStyle(C.warn),
        yAxisIndex: 1,
        lineStyle: { width: 1.5, color: C.warn },
        data: t.purchaseOrders.map(toNumber),
      },
    ],
  });
}

function renderInventory() {
  const t = props.trend;
  inventoryChart.setOption({
    tooltip: TOOLTIP,
    legend: { ...LEGEND, data: ['库存量', '入库', '出库'] },
    grid: { left: 6, right: 6, top: 26, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: t.dates, boundaryGap: false, ...AXIS },
    yAxis: [
      { type: 'value', ...AXIS, splitLine: { show: false } },
      { type: 'value', ...AXIS, splitLine: { show: false }, axisLabel: { ...AXIS.axisLabel, color: C.text3 } },
    ],
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
              { offset: 0, color: 'rgba(39, 215, 254, 0.3)' },
              { offset: 1, color: 'rgba(39, 215, 254, 0.02)' },
            ],
          },
        },
        data: t.inventoryQuantity.map(toNumber),
      },
      {
        name: '入库',
        ...areaStyle(C.green),
        yAxisIndex: 1,
        lineStyle: { width: 1.5, color: C.green },
        data: t.inboundQuantity.map(toNumber),
      },
      {
        name: '出库',
        ...areaStyle(C.orange),
        yAxisIndex: 1,
        lineStyle: { width: 1.5, color: C.orange },
        data: t.outboundQuantity.map(toNumber),
      },
    ],
  });
}

/** 空↔有数据来回切换时用 notMerge=true，避免残留上一次的 series。 */
watch(
  () => props.trend,
  () => {
    if (empty.value) {
      return;
    }
    renderSales();
    renderPurchase();
    renderInventory();
  },
  { immediate: true, deep: false }
);
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-trend {
  flex: 0 0 @trend-h;
  height: @trend-h;
  margin-top: @gap;
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
  font-size: 15px;
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
  border-radius: 3px;
  overflow: hidden;
}

.scm-trend-tab {
  padding: 3px 14px;
  font-size: 12px;
  color: @text-2;
  background: rgba(47, 128, 237, 0.08);
  border: 0;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;

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
  gap: @gap;

  > * {
    flex: 1;
    min-width: 0;
  }
}

.scm-chart {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
