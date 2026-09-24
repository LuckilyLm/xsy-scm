<template>
  <a-card size="small" class="report-chart">
    <template #title>{{ title }}</template>
    <template v-if="extra" #extra>
      <a-typography-text type="secondary">{{ extra }}</a-typography-text>
    </template>
    <a-empty
        v-if="!hasPoint"
        :image-style="{height: '40px'}"
        :description="emptyText"
        class="report-chart-empty"
    />
    <!-- 同 `report-bar-chart`：容器必须一直存在，否则 `useEcharts` 观察不到后来才创建的元素 -->
    <div v-show="hasPoint" ref="chartEl" :style="{height}"/>
  </a-card>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import {REPORT_CHART_COLORS, REPORT_CHART_HEIGHT, REPORT_CHART_SERIES} from './chart-theme';
import type {ReportChartLine} from '../report-types';

/**
 * 多折线趋势图（概览每日趋势、采购价格波动、损耗金额按日趋势）。
 *
 * 每条线的 `data` 与 `texts` 等长：`data` 只用于定位（`null` 保留为断点），
 * `texts` 是后端 4 位定点原文，tooltip 只用它（见 `report-types.ReportChartLine`）。
 */
const props = withDefaults(
    defineProps<{
        title: string;
        /** x 轴类目：按日的 `yyyy-MM-dd`（日期轴由后端补齐，不跳天）。 */
        xAxis: string[];
        series: ReportChartLine[];
        extra?: string;
        emptyText?: string;
        height?: string;
    }>(),
    {extra: '', emptyText: '暂无趋势数据', height: REPORT_CHART_HEIGHT}
);

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);

/** 「全空」与「有零」不是一回事：某天金额为 0 也要把点画出来，只有整段无值才显示空态。 */
const hasPoint = computed(
    () => props.xAxis.length > 0 && props.series.some((item) => item.data.some((value) => value !== null))
);

function render() {
    if (!hasPoint.value) {
        return;
    }
    setOption({
        animation: false,
        color: [...REPORT_CHART_SERIES],
        grid: {top: 40, right: 20, bottom: 30, left: 10, containLabel: true},
        legend: {top: 4, left: 'center', textStyle: {color: REPORT_CHART_COLORS.axisText}},
        tooltip: {
            trigger: 'axis',
            formatter: (params: unknown) => {
                const points = params as Array<{seriesIndex?: number; dataIndex?: number; axisValue?: string}>;
                if (!points.length) {
                    return '';
                }
                const lines = points.map((point) => {
                    const serie = props.series[point.seriesIndex ?? 0];
                    const text = serie?.texts[point.dataIndex ?? 0] ?? '—';
                    return `${serie?.name ?? ''}：${text}`;
                });
                return [points[0].axisValue ?? '', ...lines].join('<br/>');
            },
        },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: props.xAxis,
            axisLabel: {color: REPORT_CHART_COLORS.axisText},
        },
        yAxis: {
            type: 'value',
            axisLabel: {color: REPORT_CHART_COLORS.axisText},
            splitLine: {lineStyle: {color: REPORT_CHART_COLORS.splitLine}},
        },
        series: props.series.map((item, index) => ({
            name: item.name,
            type: 'line',
            smooth: false,
            // null 处断开而不是连成一条直线：连起来等于宣称「这两天之间平滑过渡」，
            // 而事实是这两天没有成本数据
            connectNulls: false,
            symbol: 'circle',
            symbolSize: 5,
            lineStyle: {width: 2, color: REPORT_CHART_SERIES[index % REPORT_CHART_SERIES.length]},
            itemStyle: {color: REPORT_CHART_SERIES[index % REPORT_CHART_SERIES.length]},
            data: item.data,
        })),
    });
}

watch([() => props.xAxis, () => props.series, hasPoint], render, {deep: true});
render();
</script>

<style scoped>
.report-chart-empty {
  align-items: center;
  display: flex;
  height: 240px;
  justify-content: center;
}
</style>
