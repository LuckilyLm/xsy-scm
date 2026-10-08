<!--
  * 首页库存健康卡
  *
  * 五档互斥且之和等于参与评估总数（仓库与商品规格的组合数）。它与顶部的「库存预警」
  * 卡不是同一个指标：预警列表只收低于下限 / 高于上限，这里还含缺货与未配置阈值，
  * 所以两个数字不互相求和、也不对齐。
  *
  * 视觉是一条环形图 + 右侧五档明细：环形只表达占比结构，明细行给绝对数，
  * 两者同源（都来自同一份后端数据），不存在两套口径。
-->
<template>
  <default-home-card icon-name="section-inventory-health" title="库存健康">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else class="home-health__content" :spinning="loading">
      <a-empty v-if="!loading && !health" class="home-health__empty" description="暂无库存记录"/>
      <div v-else-if="health" class="home-health__body">
        <div class="home-health__chart">
          <div ref="chartEl" class="home-health__donut"/>
          <div class="home-health__center">
            <span class="home-health__center-value">{{ formatInt(health.total) }}</span>
            <span class="home-health__center-label">参与评估</span>
          </div>
        </div>
        <ul class="home-health__list">
          <li v-for="bucket in INVENTORY_HEALTH_BUCKETS" :key="bucket.key" class="home-health__item">
            <span class="home-health__dot" :class="`tone-${bucket.tone}`"/>
            <span class="home-health__label">{{ bucket.label }}</span>
            <span class="home-health__count">{{ formatInt(countOf(bucket)) }}</span>
            <span class="home-health__pct">{{ percentOf(bucket) }}</span>
          </li>
        </ul>
      </div>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {scmDashboardApi, type ScmDashboardInventoryHealth} from '/@/api/business/scm/dashboard-api';
import {formatInt, toNumber} from '/@/views/business/scm/screen/format';
import {useEcharts} from '/@/views/business/scm/screen/composables/use-echarts';
import DefaultHomeCard from './default-home-card.vue';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';
import {INVENTORY_HEALTH_BUCKETS, type HealthBucket} from '../home-metric-meta';

/** 与明细行的圆点同色：环形扇区与右侧文字必须一一对应，否则图例失去意义。 */
const BUCKET_COLORS: Record<string, string> = {
    normal: '#22c55e',
    low: '#fa8c16',
    high: '#f5b301',
    outOfStock: '#f5222d',
    unconfigured: '#bfbfbf',
};

const {data, loading, error, load} = useRegionData<ScmDashboardInventoryHealth>(
    scmDashboardApi.inventoryHealth,
    '库存健康加载失败'
);

const health = computed(() => data.value);

function countOf(bucket: HealthBucket): number {
    return toNumber(health.value?.[bucket.field]);
}

/** 占比以参与评估总数为分母；未配置也占一格，它是「无法判定」而不是「正常」。 */
function percentOf(bucket: HealthBucket): string {
    const total = toNumber(health.value?.total);
    if (total <= 0) {
        return '0%';
    }
    return `${((countOf(bucket) / total) * 100).toFixed(1)}%`;
}

const chartEl = ref<HTMLElement>();
const {setOption} = useEcharts(chartEl);

/**
 * 只画有数据的档位：环形图里出现 0 值扇区会让图例和空白扇区对不上号。
 * 全为 0 时（后端返回了对象但没有参与评估的记录）退化成一条灰色空环 ——
 * 否则环里什么都不画，看着像渲染失败。
 */
function render() {
    const current = health.value;
    if (!current) {
        return;
    }
    const slices = INVENTORY_HEALTH_BUCKETS
        .map((bucket) => ({name: bucket.label, value: countOf(bucket), color: BUCKET_COLORS[bucket.key]}))
        .filter((slice) => slice.value > 0);

    const empty = slices.length === 0;
    setOption({
        animation: false,
        color: empty ? [BUCKET_COLORS.unconfigured] : slices.map((slice) => slice.color),
        tooltip: {
            trigger: 'item',
            formatter: '{b}：{c}（{d}%）',
            show: !empty,
        },
        series: [
            {
                type: 'pie',
                radius: ['62%', '88%'],
                center: ['50%', '50%'],
                avoidLabelOverlap: false,
                label: {show: false},
                labelLine: {show: false},
                silent: empty,
                itemStyle: {borderColor: '#fff', borderWidth: empty ? 0 : 2},
                data: empty ? [{name: '无数据', value: 1}] : slices.map((slice) => ({name: slice.name, value: slice.value})),
            },
        ],
    });
}

watch(() => data.value, render, {flush: 'post'});

onMounted(load);

defineExpose({load});
</script>

<style lang="less" scoped>
.home-health__content {
    min-height: 300px;
}

.home-health__empty {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 300px;
}

.home-health__body {
    min-height: 300px;
    display: flex;
    align-items: center;
    gap: 16px;
}

.home-health__chart {
    position: relative;
    flex: 0 0 48%;
    max-width: 190px;
    aspect-ratio: 1 / 1;
}

.home-health__donut {
    width: 100%;
    height: 100%;
}

/* 环心数字：绝对定位压在扇区中间，图里读得到总量，右侧读得到结构 */
.home-health__center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    pointer-events: none;
}

.home-health__center-value {
    font-size: 24px;
    font-weight: 700;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    color: var(--scm-text);
}

.home-health__center-label {
    font-size: 12px;
    color: var(--scm-text-secondary);
}

.home-health__list {
    flex: 1;
    min-width: 0;
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 12px;
}

.home-health__item {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 13px;
    line-height: 1.2;
}

.home-health__dot {
    flex: 0 0 auto;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    transform: translateY(-1px);

    &.tone-ok {
        background: #22c55e;
    }

    &.tone-warn {
        background: #fa8c16;
    }

    &.tone-danger {
        background: #f5222d;
    }

    &.tone-muted {
        background: #bfbfbf;
    }
}

.home-health__label {
    flex: 1;
    min-width: 0;
    color: var(--scm-text);
}

.home-health__count {
    font-size: 15px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    color: var(--scm-text);
}

.home-health__pct {
    flex: 0 0 46px;
    text-align: right;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    color: var(--scm-text-secondary);
}

@media (max-width: 1199px) {
    .home-health__body {
        flex-direction: column;
        justify-content: center;
        gap: 20px;
    }

    .home-health__chart {
        flex: 0 0 auto;
        width: 150px;
    }

    .home-health__list {
        width: 100%;
        flex: 0 0 auto;
    }
}
</style>
