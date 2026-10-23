<!-- 首页卡片由后端权限决定显示数量，前端根据容器宽度自适应布局，不假定固定卡片数。 -->
<template>
  <section class="home-metrics">
    <region-error v-if="error" :message="error" :min-height="100" @retry="load" />
    <a-spin v-else :spinning="loading">
      <a-empty v-if="!loading && rows.length === 0" description="当前账号暂无可查看的指标" />
      <a-skeleton v-else-if="loading && rows.length === 0" active :paragraph="{rows: 2}" />
      <div
        v-else
        class="home-metrics__grid"
        :class="{'is-single': rows.length === 1}"
        :style="{
          '--kpi-columns': Math.min(rows.length || 1, 5),
          '--kpi-compact-columns': Math.min(rows.length || 1, 3),
          '--kpi-tablet-columns': Math.min(rows.length || 1, 2),
        }"
      >
        <a-card
          v-for="row in rows"
          :key="row.key"
          class="home-kpi"
          :class="`tone-${row.tone}`"
          :style="{'--kpi-accent': row.accent, '--kpi-accent-soft': row.accentSoft}"
          :bordered="false"
          hoverable
          role="link"
          tabindex="0"
          :aria-label="`${row.label}，${row.money ? '¥ ' : ''}${row.text}，查看明细`"
          @click="goto(row.route)"
          @keydown.enter="goto(row.route)"
        >
          <span class="home-kpi__accent" aria-hidden="true" />
          <!-- 图标放右侧：左文字、右图标，避免右侧留白 -->
          <div class="home-kpi__body">
            <div class="home-kpi__text">
              <span class="home-kpi__label">{{ row.label }}</span>
              <div class="home-kpi__value">
                <span v-if="row.money" class="home-kpi__prefix">¥</span>
                <span class="home-kpi__number">{{ row.text }}</span>
              </div>
            </div>
            <span class="home-kpi__visual">
              <scm-icon :name="row.iconName" :size="32" />
            </span>
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
import ScmIcon from './scm-icon.vue';
import {useRegionData} from './use-region-data';

const router = useRouter();

const {data, loading, error, load} = useRegionData<ScmDashboardCard[]>(scmDashboardApi.overview, '指标加载失败');

/** 后端只给标识与数字，中文名 / 图标 / 语义色 / 展示文本都在这里补齐。 */
const rows = computed(() =>
  (data.value ?? []).map((card) => {
    const meta = kpiMetaOf(card.key);
    const money = card.unit === 'CNY';
    return {
      key: card.key,
      route: card.route,
      label: meta.label,
      iconName: meta.iconName,
      accent: meta.accent,
      accentSoft: meta.accentSoft,
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

  /* 断点按单卡最小可用宽度定，5 列在 1280px 以上即可排下 */
  @media (max-width: 1279px) {
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
    &,
    &.is-single {
      grid-template-columns: minmax(0, 1fr);
    }
  }
}

.home-kpi {
  position: relative;
  min-width: 0;
  border: 1px solid var(--scm-border);
  border-radius: 14px;
  cursor: pointer;
  background: var(--scm-bg-container);
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    border-color: var(--kpi-accent);
    box-shadow: 0 8px 20px rgba(15, 44, 32, 0.09);
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: 2px;
  }

  :deep(.ant-card-body) {
    position: relative;
    display: flex;
    align-items: center;
    /* 左边多留 4px 给色条让位 */
    padding: 16px 18px 16px 22px;
  }
}

/* 左侧色条：一眼区分五张卡，同时保持卡片本身仍是白底 */
.home-kpi__accent {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 0 3px 3px 0;
  background: var(--kpi-accent);
  opacity: 0.85;
}

/* 左文字块 + 右图标块：两端对齐，卡片宽度被真正用满 */
.home-kpi .home-kpi__body {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.home-kpi .home-kpi__text {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.home-kpi .home-kpi__visual {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: var(--kpi-accent-soft);
  color: var(--kpi-accent);
}

.home-kpi .home-kpi__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--scm-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-kpi .home-kpi__value {
  display: flex;
  align-items: baseline;
  gap: 3px;
  font-weight: 700;
  line-height: 1.1;
  flex-wrap: wrap;
}

.home-kpi .home-kpi__prefix {
  font-size: 16px;
  font-weight: 600;
  color: var(--kpi-accent);
}

.home-kpi .home-kpi__number {
  min-width: 0;
  overflow-wrap: anywhere;
  /* 5 列时单卡只有 ~185px，用 clamp 让长金额自己缩，不靠换行撑高卡片 */
  font-size: clamp(24px, 2vw, 38px);
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
  color: var(--scm-text);
}

/* 数值统一取卡片的主题色；库存预警的 tone 只影响左侧色条 */
.home-kpi.tone-primary .home-kpi__number,
.home-kpi.tone-ok .home-kpi__number,
.home-kpi.tone-warn .home-kpi__number,
.home-kpi.tone-danger .home-kpi__number {
  color: var(--kpi-accent);
}
</style>
