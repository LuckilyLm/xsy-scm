<template>
  <screen-panel title="库存健康度" flex>
    <template #extra>
      <span class="scm-health-total">
        参与评估 <b>{{ formatInt(health?.totalSkuCount) }}</b> 个 (仓库,SKU)
      </span>
    </template>

    <div v-if="!health || !health.totalSkuCount" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>暂无库存记录</span>
    </div>

    <template v-else>
      <div class="scm-health-list">
        <div v-for="row in rows" :key="row.key" class="scm-health-row">
          <div class="scm-health-line">
            <span class="scm-health-dot" :style="{ background: row.color }" />
            <span class="scm-health-label">{{ row.label }}</span>
            <span class="scm-health-count">{{ formatInt(row.count) }}</span>
            <span class="scm-health-pct">{{ percent(row.count) }}</span>
          </div>
          <div class="scm-health-bar">
            <span
              class="scm-health-bar-fill"
              :style="{ width: barWidth(row.count), background: row.color }"
            />
          </div>
        </div>
      </div>

      <div v-if="unconfigured > 0" class="scm-health-foot">
        <span class="scm-health-foot-dot" />
        另有 {{ formatInt(unconfigured) }} 个未配置阈值，无法判定健康度
      </div>
    </template>
  </screen-panel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ScreenPanel from './screen-panel.vue';
import { formatInt, toNumber } from '../format';
import type { InventoryHealth } from '../types';

/**
 * 库存健康度。
 *
 * <p><b>五档互斥</b>（后端保证之和 = `totalSkuCount`），每行一根**独立**条形，
 * 宽度 = 该档占参与评估总数的比例 —— 不是一根堆叠条，所以「未配置」不占占比也不会让画面残缺。
 *
 * <p>判定规则完全复用预警枚举，大屏不自己编算法：
 * <ul>
 *   <li>缺货 —— 可用量 ≤ 0，**优先判定**，不要求配了阈值；</li>
 *   <li>未配置阈值 —— 有余额但没配阈值，单独成档（塞进「正常」会让健康度虚高），
 *       放在底部做脚注而不是占一段占比；</li>
 *   <li>预警 / 积压 / 正常 —— 可用量与 [下限, 上限] 比较，**取等号算正常**。</li>
 * </ul>
 *
 * <p><b>有未配置时，四行占比之和会小于 100%</b>（如 6+3+2+2 / 15 = 86.7%）。这是刻意的：
 * 「未配置」无法判定健康度，既不该被算进任何一档，也不该让占比看起来是满的。
 *
 * <p><b>口径是「(仓库, SKU) 组合」而不是「SKU」</b>：阈值是按 (仓库, SKU) 配的，
 * 同一个 SKU 在两个仓库可以一个正常一个缺货。所以文案里写明「(仓库,SKU)」，
 * 避免和「SKU 总数」混淆。
 */
const props = defineProps<{ health: InventoryHealth | null }>();

const health = computed(() => props.health);
const unconfigured = computed(() => toNumber(props.health?.unconfiguredCount));

const rows = computed(() => [
  { key: 'normal', label: '正常', count: props.health?.normalCount ?? 0, color: '#34d399' },
  { key: 'low', label: '预警', count: props.health?.lowCount ?? 0, color: '#f6c344' },
  { key: 'out', label: '缺货', count: props.health?.outOfStockCount ?? 0, color: '#ff6b6b' },
  { key: 'high', label: '积压', count: props.health?.highCount ?? 0, color: '#ff8a65' },
]);

function percent(count: number): string {
  const total = toNumber(props.health?.totalSkuCount);
  if (total <= 0) {
    return '0%';
  }
  return `${((toNumber(count) / total) * 100).toFixed(1)}%`;
}

/** 条形长度用占比；为 0 时不留宽度（四档里 0 是常见且正常的状态）。 */
function barWidth(count: number): string {
  return percent(count);
}
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-health-total {
  font-size: 11px;
  color: @text-3;

  b {
    color: @tech-cyan;
    font-family: @font-num;
    font-size: 13px;
  }
}

.scm-health-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.scm-health-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.scm-health-line {
  display: flex;
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
  flex: 0 0 46px;
  text-align: right;
  font-family: @font-num;
  font-size: 12px;
  color: @text-2;
}

.scm-health-bar {
  height: 5px;
  margin-left: 13px;
  background: rgba(27, 77, 122, 0.42);
  border-radius: 3px;
  overflow: hidden;

  .scm-health-bar-fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    transition: width 0.6s ease;
  }
}

.scm-health-foot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 5px;
  padding-top: 6px;
  border-top: 1px dashed rgba(27, 77, 122, 0.7);
  font-size: 11px;
  color: @text-3;

  .scm-health-foot-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: @text-3;
  }
}
</style>
