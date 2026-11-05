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
              <div class="home-kpi__value" :title="`${row.money ? '¥ ' : ''}${row.text}`">
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
  border-radius: var(--home-card-radius, 14px);
  cursor: pointer;
  background: linear-gradient(120deg, var(--scm-bg-container) 58%, color-mix(in srgb, var(--kpi-accent) 5%, var(--scm-bg-container)));
  box-shadow: var(--home-card-shadow, 0 3px 12px rgba(15, 44, 32, 0.05));
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--kpi-accent) 38%, var(--scm-border));
    box-shadow: var(--home-card-hover-shadow, 0 8px 20px rgba(15, 44, 32, 0.09));
    transform: translateY(-2px);
  }

  &:focus-visible {
    outline: 2px solid var(--scm-primary);
    outline-offset: 3px;
  }

  :deep(.ant-card-body) {
    position: relative;
    display: flex;
    align-items: center;
    min-height: 112px;
    padding: 20px 18px 20px 22px;
  }
}

.home-kpi__accent {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--kpi-accent);
  opacity: 0.85;
}

.home-kpi .home-kpi__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  gap: 12px;
}

.home-kpi .home-kpi__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 8px;
}

.home-kpi .home-kpi__visual {
  display: inline-flex;
  flex: 0 0 56px;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border: 1px solid color-mix(in srgb, var(--kpi-accent) 12%, transparent);
  border-radius: 16px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--kpi-accent) 6%, var(--scm-bg-container)),
    color-mix(in srgb, var(--kpi-accent) 13%, var(--scm-bg-container))
  );
  color: var(--kpi-accent);
}

.home-kpi .home-kpi__label {
  overflow: hidden;
  color: var(--scm-text-secondary);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-kpi .home-kpi__value {
  display: flex;
  align-items: baseline;
  min-width: 0;
  gap: 3px;
  font-weight: 700;
  line-height: 1.15;
}

.home-kpi .home-kpi__prefix {
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--kpi-accent);
}

.home-kpi .home-kpi__number {
  min-width: 0;
  overflow: hidden;
  color: var(--kpi-accent);
  font-size: clamp(22px, 1.7vw, 32px);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 没有库存预警时保持中性，红色只用于实际需要处理的预警。 */
.home-kpi.tone-ok {
  background: var(--scm-bg-container);

  .home-kpi__accent {
    background: var(--scm-text-disabled);
  }

  .home-kpi__number,
  .home-kpi__prefix {
    color: var(--scm-text);
  }

  .home-kpi__visual {
    border-color: var(--scm-border);
    color: var(--scm-text-tertiary);
    background: var(--scm-fill);
  }

  &:hover {
    border-color: var(--scm-border);
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-kpi {
    transition: none;

    &:hover {
      transform: none;
    }
  }
}
</style>
