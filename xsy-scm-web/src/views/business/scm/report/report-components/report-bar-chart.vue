<template>
  <a-card size="small" class="report-chart">
    <template #title>{{ title }}</template>
    <a-empty v-if="!items.length" :image-style="{height: '40px'}" :description="emptyText" class="report-chart-empty" />
    <!-- 容器用 v-show：useEcharts 挂载时就要观察到这个元素，用 v-if 图表会一直空白 -->
    <div v-show="items.length" ref="chartEl" :style="{height}" />
  </a-card>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import {REPORT_CHART_COLORS, REPORT_CHART_HEIGHT} from './chart-theme';
import type {ReportChartBar} from '../report-types';

/** 无值的条目也保留，占位显示，避免 TOP5 变成 TOP4。 */
const props = withDefaults(
  defineProps<{
    title: string;
    items: ReportChartBar[];
    seriesName?: string;
    emptyText?: string;
    height?: string;
  }>(),
  {
    seriesName: '金额',
    emptyText: '暂无排名数据',
    height: REPORT_CHART_HEIGHT,
  }
);

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);

function render() {
  const items = props.items;
  setOption({
    animation: false,
    grid: {top: 10, right: 96, bottom: 10, left: 8, containLabel: true},
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const point = params as {dataIndex?: number};
        const item = items[point.dataIndex ?? 0];
        return item ? `${item.name}<br/>${props.seriesName}：${item.text}` : '';
      },
    },
    xAxis: {
      type: 'value',
      axisLabel: {color: REPORT_CHART_COLORS.axisText},
      splitLine: {lineStyle: {color: REPORT_CHART_COLORS.splitLine}},
    },
    yAxis: {
      type: 'category',
      // 第 1 名画在最上面，和表格顺序一致
      inverse: true,
      data: items.map((item) => item.name),
      axisLabel: {color: REPORT_CHART_COLORS.axisText, width: 140, overflow: 'truncate'},
      axisTick: {show: false},
    },
    series: [
      {
        name: props.seriesName,
        type: 'bar',
        barMaxWidth: 14,
        itemStyle: {color: REPORT_CHART_COLORS.primary},
        data: items.map((item) => item.value),
        label: {
          show: true,
          position: 'right',
          color: REPORT_CHART_COLORS.axisText,
          formatter: (params: unknown) => {
            const point = params as {dataIndex?: number};
            return items[point.dataIndex ?? 0]?.text ?? '—';
          },
        },
      },
    ],
  });
}

watch(() => props.items, render, {deep: true});
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
