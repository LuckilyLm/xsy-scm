<!--
  * 首页 KPI 卡
  *
  * 卡片由后端按「入口权限 ∩ 各卡领域权限」裁剪，返回数量不固定，因此列数自适应：
  * 宽屏按实际数量铺满（最多 5 列），较窄桌面最多 3 列，平板 2 列，手机 1 列；
  * 只有一张卡时限宽，避免一个数字横铺整屏。
  * 数字与点进去的明细同源，点击直接跳后端给的路由。
-->
<template>
  <section class="home-metrics">
    <region-error v-if="error" :message="error" @retry="load"/>
    <a-spin v-else :spinning="loading">
      <a-empty v-if="!loading && rows.length === 0" description="当前账号暂无可查看的指标"/>
      <a-skeleton v-else-if="loading && rows.length === 0" active :paragraph="{rows: 2}"/>
      <div v-else class="home-metrics__grid" :class="{'is-single': rows.length === 1}"
           :style="{
             '--kpi-columns': Math.min(rows.length || 1, 5),
             '--kpi-compact-columns': Math.min(rows.length || 1, 3),
             '--kpi-tablet-columns': Math.min(rows.length || 1, 2),
           }">
        <a-card
          v-for="row in rows"
          :key="row.key"
          class="home-kpi"
          :class="`tone-${row.tone}`"
          :bordered="false"
          hoverable
          role="link"
          tabindex="0"
          :aria-label="`${row.label}，${row.money ? '¥ ' : ''}${row.text}，查看明细`"
          @click="goto(row.route)"
          @keydown.enter="goto(row.route)"
        >
          <div class="home-kpi__head">
            <span class="home-kpi__visual" :class="`tone-${row.tone}`">
              <img :src="homeAsset(row.asset)" alt="" aria-hidden="true" class="home-kpi__asset"/>
              <component :is="row.icon" class="home-kpi__icon" aria-hidden="true"/>
            </span>
            <span class="home-kpi__label">{{ row.label }}</span>
          </div>
          <div class="home-kpi__value">
            <span v-if="row.money" class="home-kpi__prefix">¥</span>
            <span class="home-kpi__number">{{ row.text }}</span>
          </div>
        </a-card>
      </div>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import {computed, onMounted} from 'vue';
import {useRouter} from 'vue-router';
import {scmDashboardApi, type ScmDashboardCard} from '/@/api/business/scm/dashboard-api';
import {formatAmount, formatInt, toNumber} from '/@/views/business/scm/screen/format';
import {kpiMetaOf, kpiTone} from '../home-metric-meta';
import {homeAsset} from '../home-assets';
import RegionError from './region-error.vue';
import {useRegionData} from './use-region-data';

const router = useRouter();

const {data, loading, error, load} = useRegionData<ScmDashboardCard[]>(
    scmDashboardApi.overview,
    '指标加载失败'
);

/** 后端只给标识与数字，中文名 / 图标 / 语义色 / 展示文本都在这里补齐。 */
const rows = computed(() =>
    (data.value ?? []).map((card) => {
        const meta = kpiMetaOf(card.key);
        const money = card.unit === 'CNY';
        return {
            key: card.key,
            route: card.route,
            label: meta.label,
            icon: meta.icon,
            asset: meta.asset,
            tone: kpiTone(card.key, toNumber(card.value)),
            money,
            text: money ? formatAmount(card.value) : formatInt(card.value),
        };
    })
);

function goto(route: string) {
    void router.push(route);
}

onMounted(load);

defineExpose({load});
</script>

<style lang="less" scoped>
.home-metrics__grid {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(var(--kpi-columns), minmax(0, 1fr));

    &.is-single {
        grid-template-columns: minmax(0, 320px);
    }

    @media (max-width: 1599px) {
        grid-template-columns: repeat(var(--kpi-compact-columns), minmax(0, 1fr));

        &.is-single {
            grid-template-columns: minmax(0, 320px);
        }
    }

    @media (max-width: 991px) {
        grid-template-columns: repeat(var(--kpi-tablet-columns), minmax(0, 1fr));

        &.is-single {
            grid-template-columns: minmax(0, 320px);
        }
    }

    @media (max-width: 575px) {
        &, &.is-single {
            grid-template-columns: minmax(0, 1fr);
        }
    }
}

.home-kpi {
    min-width: 0;
    border: 1px solid var(--scm-border);
    border-radius: 12px;
    cursor: pointer;
    background: var(--scm-bg-container);

    &:hover {
        border-color: var(--scm-primary);
        box-shadow: none;
    }

    &:focus-visible {
        outline: 2px solid var(--scm-primary);
        outline-offset: 2px;
    }

    :deep(.ant-card-body) {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding: 20px;
    }

    .home-kpi__head {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    .home-kpi__icon {
        display: none;
    }

    .home-kpi__visual {
        display: inline-flex;
        flex: 0 0 auto;
        align-items: center;
        justify-content: center;
        width: 42px;
        height: 42px;
        border-radius: 14px;
        background: var(--scm-fill);

        &.tone-primary { background: #e7f7ef; }
        &.tone-ok { background: #e8f8f0; }
        &.tone-warn { background: #fff6e1; }
        &.tone-danger { background: #ffeded; }
    }

    .home-kpi__asset {
        width: 42px;
        height: 42px;
        object-fit: contain;
    }

    .home-kpi__label {
        font-size: 13px;
        color: var(--scm-text-secondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .home-kpi__value {
        display: flex;
        align-items: baseline;
        gap: 3px;
        font-weight: 700;
        line-height: 1.1;
        flex-wrap: wrap;
    }

    .home-kpi__prefix {
        font-size: 15px;
        color: var(--scm-text-secondary);
    }

    .home-kpi__number {
        min-width: 0;
        overflow-wrap: anywhere;
        font-size: 28px;
        font-variant-numeric: tabular-nums;
        color: var(--scm-text);
    }

    &.tone-primary .home-kpi__number {
        color: var(--scm-primary);
    }

    &.tone-ok .home-kpi__number {
        color: var(--scm-success);
    }

    &.tone-warn .home-kpi__number {
        color: var(--scm-warning);
    }

    &.tone-danger .home-kpi__number {
        color: var(--scm-error);
    }
}
</style>
