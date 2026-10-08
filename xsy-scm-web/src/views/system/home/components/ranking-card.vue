<!--
  * 销售排行（客户 / 商品共用）
  *
  * 两个维度的数据形状与展示方式完全一致，只有标题与空态文案不同，所以做成一个带维度的组件，
  * 而不是两份只差一个字符串的文件。条形长度按本列表最大值取相对比例，不表达绝对量级。
-->
<template>
  <default-home-card
    :icon-name="dimension === 'customer' ? 'section-customer-ranking' : 'section-product-ranking'"
    :title="meta.title">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <a-empty v-if="!loading && items.length === 0" class="home-rank__empty" :description="meta.emptyText"/>
      <ul v-else class="home-rank__list">
        <li v-for="(item, index) in items" :key="`${dimension}-${index}-${item.name}`" class="home-rank__item">
          <div class="home-rank__line">
            <span class="home-rank__index" :class="{'is-top1': index === 0}">
              {{ index + 1 }}
            </span>
            <span class="home-rank__name" :title="item.name">{{ item.name }}</span>
            <span class="home-rank__amount">¥ {{ formatAmount(item.amount) }}</span>
          </div>
          <div class="home-rank__bar">
            <span class="home-rank__fill" :style="{width: barWidth(item.amount)}"/>
          </div>
        </li>
      </ul>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted} from 'vue';
import {scmDashboardApi, type ScmRankDimension, type ScmRankItem} from '/@/api/business/scm/dashboard-api';
import {formatAmount, toNumber} from '/@/views/business/scm/screen/format';
import DefaultHomeCard from './default-home-card.vue';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';
import {RANK_META} from '../home-metric-meta';

/** 首页只展示前 5，后端上限是它已经取回的条数。 */
const RANK_LIMIT = 5;

const props = defineProps<{dimension: ScmRankDimension}>();

const meta = computed(() => RANK_META[props.dimension]);

const {data, loading, error, load} = useRegionData<ScmRankItem[]>(
    () => scmDashboardApi.ranking(props.dimension, RANK_LIMIT),
    '排行加载失败'
);

const items = computed(() => data.value ?? []);

/** 条形长度以本列表最大值为满格；最小值留 2%，否则极小值看起来像没渲染。 */
function barWidth(amount: string): string {
    const max = items.value.reduce((acc, item) => Math.max(acc, toNumber(item.amount)), 0);
    if (max <= 0) {
        return '0%';
    }
    return `${Math.max((toNumber(amount) / max) * 100, 2).toFixed(2)}%`;
}

onMounted(load);

defineExpose({load});
</script>

<style lang="less" scoped>
.home-rank__empty {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 300px;
}

.home-rank__list {
    margin: 0;
    padding: 0;
    list-style: none;
    min-height: 300px;
    display: flex;
    flex-direction: column;
    justify-content: space-around;
}

.home-rank__item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 4px 0;
}

.home-rank__line {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 13px;
    line-height: 1.2;
}

.home-rank__index {
    flex: 0 0 auto;
    width: 18px;
    height: 18px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 600;
    color: var(--scm-text-secondary);
    background: var(--scm-fill);

    &.is-top1 {
        color: #fff;
        background: var(--scm-warning);
    }
}

.home-rank__name {
    flex: 1;
    min-width: 0;
    color: var(--scm-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.home-rank__amount {
    flex: 0 0 auto;
    font-size: 13px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--scm-text);
}

.home-rank__bar {
    height: 4px;
    margin-left: 26px;
    border-radius: 2px;
    background: var(--scm-fill);
    overflow: hidden;

    .home-rank__fill {
        display: block;
        height: 100%;
        border-radius: 2px;
        background: var(--scm-primary);
        transition: width 0.6s ease;
    }
}
</style>
