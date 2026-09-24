<template>
  <a-card size="small" class="report-chart">
    <template #title>{{ title }}</template>
    <template v-if="extra" #extra>
      <a-typography-text type="secondary">{{ extra }}</a-typography-text>
    </template>
    <a-empty
        v-if="!items.length"
        :image-style="{height: '40px'}"
        :description="emptyText"
        class="report-chart-empty"
    />
    <!--
      图表容器用 `v-show` 而不是 `v-if`：`useEcharts` 在 `onMounted` 时对被观察的那个元素
      注册 ResizeObserver，元素后来才被创建就永远等不到尺寸，图会一直空白。
      隐藏时容器尺寸是 0，composable 自己会把配置挂起，等显示后再补 init。
    -->
    <div v-show="items.length" ref="chartEl" :style="{height}"/>
  </a-card>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import {REPORT_CHART_COLORS, REPORT_CHART_HEIGHT} from './chart-theme';
import type {ReportChartBar} from '../report-types';

/**
 * TOP 排名条形图（商品 / 分类 / 客户 TOP5、供应商 TOP10）。
 *
 * 一条排名 = 一个 `{name, value, text}`（见 `report-types.ReportChartBar`：
 * `value` 只画长度、`text` 才是展示值）。
 *
 * `value` 为 `null` 的条目仍占一行（画不出条、标签显示 `—`）：静默丢掉它会让
 * 「TOP5」变成「TOP4」，而用户无从知道少的那条是没有事实还是被过滤了。
 */
const props = withDefaults(
    defineProps<{
        title: string;
        items: ReportChartBar[];
        /** 卡片右上角的口径说明。 */
        extra?: string;
        seriesName?: string;
        emptyText?: string;
        height?: string;
    }>(),
    {
        extra: '',
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
        xAxis: {type: 'value', axisLabel: {color: REPORT_CHART_COLORS.axisText}, splitLine: {lineStyle: {color: REPORT_CHART_COLORS.splitLine}}},
        yAxis: {
            type: 'category',
            // 倒序：第 1 名画在最上面，与表格「金额排名 1 在第一行」的阅读顺序一致
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
