<template>
  <screen-panel title="库存健康度" flex>
    <div class="scm-health-total" title="参与评估数量按仓库和商品规格组合统计">
      <span>参与评估</span>
      <fit-number :value="formatInt(health?.totalSkuCount)" unit="个" align="end" />
    </div>

    <div v-if="!health || !health.totalSkuCount" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>暂无库存记录</span>
    </div>

    <template v-else>
      <div class="scm-health-list scm-scroll">
        <div v-for="row in rows" :key="row.key" class="scm-health-row">
          <div class="scm-health-line">
            <span class="scm-health-dot" :style="{background: row.color}" />
            <span class="scm-health-label">{{ row.label }}</span>
            <fit-number class="scm-health-count" :value="formatInt(row.count)" align="end" />
            <span class="scm-health-pct">{{ percent(row.count) }}</span>
          </div>
          <div class="scm-health-bar">
            <span class="scm-health-bar-fill" :style="{width: barWidth(row.count), background: row.color}" />
          </div>
        </div>
      </div>

      <div v-if="unconfigured > 0" class="scm-health-foot" :title="`另有 ${formatInt(unconfigured)} 个未配置阈值，无法判定健康度`">
        <span>未配置阈值，无法评估</span>
        <fit-number :value="formatInt(unconfigured)" unit="个" :size="13" align="end" />
      </div>
    </template>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FitNumber from './fit-number.vue';
import ScreenPanel from './screen-panel.vue';
import {formatInt, toNumber} from '../format';
import type {InventoryHealth} from '../types';

/** 库存健康度：五档互斥，条形长度是各档占比；未配置阈值单独做脚注。 */
const props = defineProps<{health: InventoryHealth | null}>();

const health = computed(() => props.health);
const unconfigured = computed(() => toNumber(props.health?.unconfiguredCount));

const rows = computed(() => [
  {key: 'normal', label: '正常', count: props.health?.normalCount ?? 0, color: '#34d399'},
  {key: 'low', label: '预警', count: props.health?.lowCount ?? 0, color: '#f6c344'},
  {key: 'out', label: '缺货', count: props.health?.outOfStockCount ?? 0, color: '#ff6b6b'},
  {key: 'high', label: '积压', count: props.health?.highCount ?? 0, color: '#ff8a65'},
]);

function percent(count: number): string {
  const total = toNumber(props.health?.totalSkuCount);
  if (total <= 0) {
    return '0%';
  }
  return `${((toNumber(count) / total) * 100).toFixed(1)}%`;
}

/** 条形长度用占比，为 0 时不显示。 */
function barWidth(count: number): string {
  return percent(count);
}
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-health-total,
.scm-health-foot {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  color: @text-2;
}

.scm-health-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.scm-health-row {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.scm-health-line {
  display: grid;
  grid-template-columns: 7px minmax(2em, 1fr) minmax(0, 45%) 52px;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
  line-height: 1.2;
}

.scm-health-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  flex: 0 0 auto;
  transform: translateY(-1px);
}

.scm-health-label {
  flex: 1;
  min-width: 0;
  color: @text-1;
}

.scm-health-count {
  font-family: @font-num;
  font-size: 15px;
  font-weight: 700;
  color: @text-1;
}

.scm-health-pct {
  text-align: right;
  font-family: @font-num;
  font-size: 12px;
  color: @text-2;
}

.scm-health-bar {
  height: 4px;
  margin-left: 13px;
  background: rgba(27, 77, 122, 0.42);
  border-radius: 2px;
  overflow: hidden;

  .scm-health-bar-fill {
    display: block;
    height: 100%;
    border-radius: 2px;
  }
}

.scm-health-foot {
  padding-top: 5px;
  border-top: 1px solid @panel-border;
}
</style>
