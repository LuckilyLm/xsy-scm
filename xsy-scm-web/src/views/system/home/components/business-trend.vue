<!-- 经营趋势：指标切换只列出有权限的指标；金额与笔数用双轴。 -->
<template>
  <default-home-card icon-name="section-business-trend" title="经营趋势">
    <div class="home-trend__bar">
      <a-radio-group
        class="home-trend__metric-switch"
        v-if="metrics.length > 1"
        v-model:value="active"
        size="small"
        button-style="solid"
        @change="load"
      >
        <a-radio-button v-for="metric in metrics" :key="metric" :value="metric">
          {{ TREND_META[metric].label }}
        </a-radio-button>
      </a-radio-group>
      <span v-else class="home-trend__only">{{ TREND_META[active].label }}</span>

      <a-radio-group class="home-trend__range-switch" v-model:value="range" size="small" @change="load">
        <a-radio-button v-for="item in TREND_RANGES" :key="item.value" :value="item.value">
          {{ item.label }}
        </a-radio-button>
      </a-radio-group>
    </div>

    <region-error v-if="error" :message="error" :min-height="264" @retry="load" />
    <a-spin v-show="!error" class="home-trend__content" :spinning="loading">
      <!-- 容器必须一直存在：隐藏时尺寸为 0，图表的尺寸观察者要等它出现后才初始化 -->
      <div v-show="hasData && !allZero" ref="chartEl" class="home-trend__chart" />
      <a-empty v-if="!loading && !hasData" class="home-trend__empty" description="暂无趋势数据" />
      <a-empty v-else-if="!loading && allZero" class="home-trend__empty" :description="allZeroDescription" />
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {theme} from 'ant-design-vue';
import {scmDashboardApi, type ScmDashboardTrend, type ScmTrendMetric, type ScmTrendRange} from '/@/api/business/scm/dashboard-api';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import {formatAmount, formatCompact, formatQty, toNumber} from '/@/views/business/scm/screen/format';
import {REPORT_CHART_COLORS} from '/@/views/business/scm/report/report-components/chart-theme';
import DefaultHomeCard from './default-home-card.vue';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';
import {TREND_META, TREND_RANGES} from '../home-metric-meta';

const props = defineProps<{metrics: ScmTrendMetric[]}>();

const active = ref<ScmTrendMetric>(props.metrics[0]);
const range = ref<ScmTrendRange>('7d');

const {data, loading, error, load} = useRegionData<ScmDashboardTrend>(() => scmDashboardApi.trend(active.value, range.value), '趋势加载失败');

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);
const {token} = theme.useToken();

const hasData = computed(() => {
  const trend = data.value;
  return !!trend && trend.metric === active.value && trend.range === range.value && trend.dates.length > 0;
});

const allZero = computed(() => {
  const trend = data.value;
  if (!hasData.value || !trend || trend.primarySeries.length !== trend.dates.length || trend.secondarySeries.length !== trend.dates.length) {
    return false;
  }
  const values = [...trend.primarySeries, ...trend.secondarySeries];
  return values.every((value) => value.trim() !== '' && Number.isFinite(Number(value)) && Number(value) === 0);
});

const allZeroDescription = computed(() => {
  const period = range.value === '7d' ? '近 7 天' : '近 30 天';
  const description: Record<ScmTrendMetric, string> = {
    sales: '暂无成交记录',
    purchase: '暂无采购记录',
    inventory: '暂无库存流转记录',
  };
  return `${period}${description[active.value]}`;
});

function chartColor(variable: string, fallback: string): string {
  return chartEl.value ? getComputedStyle(chartEl.value).getPropertyValue(variable).trim() || fallback : fallback;
}

/** 金额轴按「万」缩写，否则七位数会把轴标签挤成两行。 */
function valueAxis(splitLine: boolean, money: boolean) {
  return {
    type: 'value',
    axisLabel: {
      color: chartColor('--scm-text-secondary', REPORT_CHART_COLORS.axisText),
      fontSize: 11,
      formatter: money ? (value: number) => formatCompact(value) : undefined,
    },
    axisLine: {show: false},
    axisTick: {show: false},
    splitNumber: 4,
    splitLine: {show: splitLine, lineStyle: {color: chartColor('--scm-border', REPORT_CHART_COLORS.splitLine), type: 'dashed'}},
  };
}

/** 第一序列为柱、第二序列为折线；同量纲时共用一根轴。 */
function seriesOf(index: number, trend: ScmDashboardTrend) {
  const meta = TREND_META[trend.metric];
  const color = index === 0 ? REPORT_CHART_COLORS.primary : REPORT_CHART_COLORS.secondary;
  const common = {
    name: index === 0 ? meta.primaryLabel : meta.secondaryLabel,
    yAxisIndex: meta.dualAxis ? index : 0,
    // 后端小数是 string，画图前转成 number，否则类目轴上的比较会退化成字符串
    data: (index === 0 ? trend.primarySeries : trend.secondarySeries).map(toNumber),
  };
  if (index === 0) {
    return {
      ...common,
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: {color, opacity: 0.88, borderRadius: [5, 5, 0, 0]},
      emphasis: {itemStyle: {opacity: 1}},
    };
  }
  return {
    ...common,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: {width: 2.5, color},
    itemStyle: {color},
  };
}

function render() {
  const trend = data.value;
  if (!trend || trend.dates.length === 0) {
    return;
  }
  const meta = TREND_META[trend.metric];
  const money = trend.metric !== 'inventory';
  setOption({
    animation: false,
    color: [REPORT_CHART_COLORS.primary, REPORT_CHART_COLORS.secondary],
    grid: {top: 44, right: meta.dualAxis ? 40 : 12, bottom: 16, left: 4, containLabel: true},
    legend: {
      top: 0,
      left: 4,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 6,
      itemGap: 20,
      textStyle: {color: chartColor('--scm-text-secondary', REPORT_CHART_COLORS.axisText), fontSize: 12},
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: chartColor('--scm-bg-container', '#ffffff'),
      borderColor: chartColor('--scm-border', REPORT_CHART_COLORS.splitLine),
      borderWidth: 1,
      padding: [10, 14],
      textStyle: {color: chartColor('--scm-text', '#1f2937'), fontSize: 12, lineHeight: 22},
      extraCssText: 'border-radius:10px;box-shadow:0 6px 20px rgba(15,44,32,0.09);',
      axisPointer: {type: 'shadow', shadowStyle: {color: 'rgba(15,158,99,0.04)'}},
      formatter: (params: unknown) => {
        const points = params as Array<{seriesIndex?: number; dataIndex?: number; axisValue?: string}>;
        if (!points.length) {
          return '';
        }
        const lines = points.map((point) => {
          const primary = (point.seriesIndex ?? 0) === 0;
          const index = point.dataIndex ?? 0;
          const value = primary ? trend.primarySeries[index] : trend.secondarySeries[index];
          const name = primary ? meta.primaryLabel : meta.secondaryLabel;
          return `${name}：${primary && money ? `¥ ${formatAmount(value)}` : formatQty(value)}`;
        });
        return [points[0].axisValue ?? '', ...lines].join('<br/>');
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trend.dates,
      axisTick: {show: false},
      axisLine: {lineStyle: {color: chartColor('--scm-border', REPORT_CHART_COLORS.splitLine)}},
      axisLabel: {color: chartColor('--scm-text-secondary', REPORT_CHART_COLORS.axisText), fontSize: 11, margin: 12},
    },
    yAxis: meta.dualAxis ? [valueAxis(true, money), valueAxis(false, false)] : [valueAxis(true, false)],
    series: [seriesOf(0, trend), seriesOf(1, trend)],
  });
}

// Canvas 不会继承后续 CSS 变量变化；主题更新后重新取色，只重绘，不重新请求数据。
watch([() => data.value, token], render, {flush: 'post'});

onMounted(load);

defineExpose({load});
</script>

<style lang="less" scoped>
.home-trend__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  min-height: 32px;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.home-trend__metric-switch,
.home-trend__range-switch {
  display: inline-flex;
  max-width: 100%;
  padding: 3px;
  border: 1px solid var(--scm-border);
  border-radius: 9px;
  background: var(--scm-fill);

  :deep(.ant-radio-button-wrapper) {
    height: 28px;
    padding: 0 10px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--scm-text-secondary);
    font-size: 12px;
    line-height: 28px;
    white-space: nowrap;
    transition:
      color 0.18s ease,
      background 0.18s ease,
      box-shadow 0.18s ease;

    &::before {
      display: none;
    }

    &:hover {
      color: var(--scm-primary);
    }

    &:focus-within {
      outline: 2px solid var(--scm-primary);
      outline-offset: 1px;
    }
  }

  :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
    color: var(--scm-primary);
    font-weight: 600;
    background: var(--scm-bg-container);
    box-shadow: 0 1px 4px rgba(15, 44, 32, 0.08);
  }
}

.home-trend__metric-switch {
  :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
    background: var(--scm-primary-bg);
  }
}

.home-trend__only {
  color: var(--scm-primary);
  font-size: 13px;
  font-weight: 600;
}

.home-trend__chart {
  height: 264px;
}

.home-trend__content {
  min-height: 264px;
}

.home-trend__empty {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 264px;
}

@media (max-width: 767px) {
  .home-trend__chart,
  .home-trend__content,
  .home-trend__empty {
    height: 232px;
    min-height: 232px;
  }

  .home-trend__bar {
    align-items: flex-start;
    gap: 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-trend__metric-switch,
  .home-trend__range-switch {
    :deep(.ant-radio-button-wrapper) {
      transition: none;
    }
  }
}
</style>
