<!--
  * 首页库存健康卡
  *
  * 五档互斥且之和等于参与评估总数（仓库与商品规格的组合数）。它与顶部的「库存预警」
  * 卡不是同一个指标：预警列表只收低于下限 / 高于上限，这里还含缺货与未配置阈值，
  * 所以两个数字不互相求和、也不对齐。
-->
<template>
  <default-home-card asset="icons/inventory-health.png" title="库存健康">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else class="home-health__content" :spinning="loading">
      <a-empty v-if="!loading && !health" class="home-health__empty" description="暂无库存记录"/>
      <div v-else-if="health" class="home-health__body">
        <div class="home-health__total">参与评估 {{ formatInt(health?.total) }} 项</div>
        <ul class="home-health__list">
          <li v-for="bucket in INVENTORY_HEALTH_BUCKETS" :key="bucket.key" class="home-health__item">
            <div class="home-health__line">
              <span class="home-health__dot" :class="`tone-${bucket.tone}`"/>
              <span class="home-health__label">{{ bucket.label }}</span>
              <span class="home-health__count">{{ formatInt(countOf(bucket)) }}</span>
              <span class="home-health__pct">{{ percentOf(bucket) }}</span>
            </div>
            <div class="home-health__bar">
              <span
                class="home-health__fill"
                :class="`tone-${bucket.tone}`"
                :style="{width: percentOf(bucket)}"
              />
            </div>
          </li>
        </ul>
      </div>
    </a-spin>
  </default-home-card>
</template>

<script setup lang="ts">
import {computed, onMounted} from 'vue';
import {scmDashboardApi, type ScmDashboardInventoryHealth} from '/@/api/business/scm/dashboard-api';
import {formatInt, toNumber} from '/@/views/business/scm/screen/format';
import DefaultHomeCard from './default-home-card.vue';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';
import {INVENTORY_HEALTH_BUCKETS, type HealthBucket} from '../home-metric-meta';

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
    flex-direction: column;
    gap: 10px;
}

.home-health__total {
    flex: 0 0 auto;
    text-align: right;
    font-size: 12px;
    color: var(--scm-text-secondary);
}

.home-health__list {
    flex: 1;
    min-height: 0;
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    justify-content: space-around;
}

.home-health__item {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.home-health__line {
    display: flex;
    align-items: baseline;
    gap: 6px;
    font-size: 13px;
    line-height: 1.2;
}

.home-health__dot {
    flex: 0 0 auto;
    width: 7px;
    height: 7px;
    border-radius: 2px;
    transform: translateY(-1px);

    &.tone-ok {
        background: var(--scm-success);
    }

    &.tone-warn {
        background: var(--scm-warning);
    }

    &.tone-danger {
        background: var(--scm-error);
    }

    &.tone-muted {
        background: var(--scm-text-disabled);
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
    flex: 0 0 48px;
    text-align: right;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    color: var(--scm-text-secondary);
}

.home-health__bar {
    height: 5px;
    margin-left: 13px;
    border-radius: 3px;
    background: var(--scm-fill);
    overflow: hidden;

    .home-health__fill {
        display: block;
        height: 100%;
        border-radius: 3px;
        transition: width 0.6s ease;

        &.tone-ok {
            background: var(--scm-success);
        }

        &.tone-warn {
            background: var(--scm-warning);
        }

        &.tone-danger {
            background: var(--scm-error);
        }

        &.tone-muted {
            background: var(--scm-text-disabled);
        }
    }
}
</style>
