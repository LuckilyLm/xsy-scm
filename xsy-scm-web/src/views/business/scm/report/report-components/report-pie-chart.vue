<template>
  <a-card size="small" class="report-chart">
    <template #title>{{ title }}</template>
    <template v-if="extra" #extra>
      <a-typography-text type="secondary">{{ extra }}</a-typography-text>
    </template>
    <a-empty
        v-if="!hasData"
        :image-style="{height: '40px'}"
        :description="emptyText"
        class="report-chart-empty"
    />
    <!-- 同 `report-bar-chart`：容器必须一直存在，否则 `useEcharts` 观察不到后来才创建的元素 -->
    <div v-show="hasData" ref="chartEl" :style="{height}"/>
  </a-card>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import {REPORT_CHART_COLORS, REPORT_CHART_HEIGHT, REPORT_CHART_SERIES} from './chart-theme';
import type {ReportChartSlice} from '../report-types';

/**
 * 分类占比饼图（损耗类型金额占比）。
 *
 * 切片值只做**角度分配**：占比由 ECharts 自己按值算，文本一律用 `text`
 * （后端 4 位定点原文，见 `report-types.ReportChartSlice`）。饼图刻意不显示「合计」，
 * 因为合计是聚合事实，需要的时候由 KPI 卡给出，不在图例里再算一遍。
 */
const props = withDefaults(
    defineProps<{
        title: string;
        slices: ReportChartSlice[];
        extra?: string;
        emptyText?: string;
        height?: string;
    }>(),
    {extra: '', emptyText: '暂无占比数据', height: REPORT_CHART_HEIGHT}
);

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);

/** 全零 / 全无值时饼图只会画出一个空圈，那不如给空态；这里判断的是「画不画」，不是业务数字。 */
const hasData = computed(() => props.slices.some((slice) => slice.value !== null && slice.value > 0));

function render() {
    if (!hasData.value) {
        return;
    }
    const slices = props.slices;
    setOption({
        animation: false,
        color: [...REPORT_CHART_SERIES],
        tooltip: {
            trigger: 'item',
            formatter: (params: unknown) => {
                const point = params as {dataIndex?: number; percent?: number};
                const item = slices[point.dataIndex ?? 0];
                if (!item) {
                    return '';
                }
                const percent = typeof point.percent === 'number' ? ` (${point.percent.toFixed(1)}%)` : '';
                return `${item.name}：${item.text}${percent}`;
            },
        },
        legend: {bottom: 0, left: 'center', textStyle: {color: REPORT_CHART_COLORS.axisText}},
        series: [
            {
                type: 'pie',
                radius: ['45%', '68%'],
                center: ['50%', '45%'],
                itemStyle: {borderColor: '#fff', borderWidth: 2},
                label: {color: REPORT_CHART_COLORS.axisText, formatter: '{b}'},
                data: slices.map((slice, index) => ({
                    name: slice.name,
                    value: slice.value,
                    itemStyle: {color: REPORT_CHART_SERIES[index % REPORT_CHART_SERIES.length]},
                })),
            },
        ],
    });
}

watch(() => props.slices, render, {deep: true});
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
