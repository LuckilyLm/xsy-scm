<!--
  * 首页唯一主图：经营趋势
  *
  * 指标切换按钮只列出当前登录人有权查看的指标（由上层按权限传入），默认选中第一个。
  * 销售额与采购额是「金额 + 笔数」两种量纲，用左右双轴；库存流转的入库量与出库量同量纲，
  * 共用一根轴。
-->
<template>
  <default-home-card icon-name="section-business-trend" title="经营趋势">
    <div class="home-trend__bar">
      <a-radio-group
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

      <a-radio-group v-model:value="range" size="small" @change="load">
        <a-radio-button v-for="item in TREND_RANGES" :key="item.value" :value="item.value">
          {{ item.label }}
        </a-radio-button>
      </a-radio-group>
    </div>

    <region-error v-if="error" :message="error" :min-height="264" @retry="load"/>
    <a-spin v-show="!error" class="home-trend__content" :spinning="loading">
      <!-- 容器必须一直存在：隐藏时尺寸为 0，图表的尺寸观察者要等它出现后才初始化 -->
      <div v-show="hasData && !allZero" ref="chartEl" class="home-trend__chart"/>
      <a-empty v-if="!loading && !hasData" class="home-trend__empty" description="暂无趋势数据"/>
      <a-empty v-else-if="!loading && allZero" class="home-trend__empty" :description="allZeroDescription"/>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {
    scmDashboardApi,
    type ScmDashboardTrend,
    type ScmTrendMetric,
    type ScmTrendRange,
} from '/@/api/business/scm/dashboard-api';
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

const {data, loading, error, load} = useRegionData<ScmDashboardTrend>(
    () => scmDashboardApi.trend(active.value, range.value),
    '趋势加载失败'
);

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);

const hasData = computed(() => {
    const trend = data.value;
    return !!trend && trend.metric === active.value && trend.range === range.value && trend.dates.length > 0;
});

const allZero = computed(() => {
    const trend = data.value;
    if (!hasData.value || !trend || trend.primarySeries.length !== trend.dates.length ||
        trend.secondarySeries.length !== trend.dates.length) {
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

/** 金额轴按「万」缩写，否则七位数会把轴标签挤成两行。 */
function valueAxis(splitLine: boolean, money: boolean) {
    return {
        type: 'value',
        axisLabel: {
            color: REPORT_CHART_COLORS.axisText,
            formatter: money ? (value: number) => formatCompact(value) : undefined,
        },
        splitLine: {show: splitLine, lineStyle: {color: REPORT_CHART_COLORS.splitLine}},
    };
}

/**
 * 第一条序列恒为柱、第二条恒为折线：主量级（金额/入库量）看柱，辅量级（笔数/出库量）看线。
 * 同量纲时（库存流转）两条轴合并，柱线仍用两种形状区分，不靠颜色单打独斗。
 */
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
            barMaxWidth: 22,
            itemStyle: {color, borderRadius: [4, 4, 0, 0]},
        };
    }
    return {
        ...common,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {width: 2, color},
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
        grid: {top: 44, right: meta.dualAxis ? 56 : 24, bottom: 28, left: 8, containLabel: true},
        legend: {top: 4, left: 'center', icon: 'roundRect', textStyle: {color: REPORT_CHART_COLORS.axisText}},
        tooltip: {
            trigger: 'axis',
            axisPointer: {type: 'shadow'},
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
            axisLabel: {color: REPORT_CHART_COLORS.axisText},
        },
        yAxis: meta.dualAxis
            ? [valueAxis(true, money), valueAxis(false, false)]
            : [valueAxis(true, false)],
        series: [seriesOf(0, trend), seriesOf(1, trend)],
    });
}

watch(() => data.value, render, {flush: 'post'});

onMounted(load);

defineExpose({load});
</script>

<style lang="less" scoped>
.home-trend__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-bottom: 8px;
}

.home-trend__only {
    font-size: 13px;
    color: var(--scm-text-secondary);
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
}
</style>
