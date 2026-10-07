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
      <div v-else class="home-metrics__grid" :class="{'is-single': rows.length === 1}">
        <a-card
          v-for="row in rows"
          :key="row.key"
          class="home-kpi"
          :class="`tone-${row.tone}`"
          :bordered="false"
          hoverable
          @click="goto(row.route)"
        >
          <div class="home-kpi__head">
            <component :is="row.icon" class="home-kpi__icon"/>
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
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));

    &.is-single {
        grid-template-columns: minmax(0, 320px);
    }

    @media (max-width: 1599px) {
        grid-template-columns: repeat(auto-fit, minmax(calc((100% - 32px) / 3), 1fr));

        &.is-single {
            grid-template-columns: minmax(0, 320px);
        }
    }

    @media (max-width: 991px) {
        grid-template-columns: repeat(auto-fit, minmax(calc((100% - 16px) / 2), 1fr));

        &.is-single {
            grid-template-columns: minmax(0, 320px);
        }
    }

    @media (max-width: 575px) {
        grid-template-columns: minmax(0, 1fr);
    }
}

.home-kpi {
    border: 1px solid var(--scm-border);
    border-radius: 12px;
    cursor: pointer;
    transition: box-shadow 0.2s ease;

    &:hover {
        box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
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
        font-size: 16px;
        color: var(--scm-text-secondary);
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
        white-space: nowrap;
    }

    .home-kpi__prefix {
        font-size: 15px;
        color: var(--scm-text-secondary);
    }

    .home-kpi__number {
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
