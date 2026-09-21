<template>
  <screen-panel title="仓库库存分布" flex>
    <template #extra>库存总量 {{ formatQty(total) }}</template>

    <div v-if="!rows.length" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>暂无库存</span>
    </div>

    <div v-else class="scm-wh-list">
      <div v-for="row in rows" :key="row.name" class="scm-wh-row">
        <div class="scm-wh-line">
          <span class="scm-wh-name" :title="row.name">{{ row.name }}</span>
          <span class="scm-wh-qty">{{ row.qtyText }}</span>
          <span class="scm-wh-pct">{{ row.pctText }}</span>
        </div>
        <div class="scm-wh-bar">
          <span class="scm-wh-bar-fill" :style="{ width: row.pctText }"/>
        </div>
      </div>
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import ScreenPanel from './screen-panel.vue';
import {formatQty, toNumber} from '../format';
import type {WarehouseDistribution} from '../types';

/**
 * 仓库库存分布。
 *
 * <p><b>刻意不用饼图</b>：设计稿指出仓库数量一多，饼图的扇区标签会互相遮挡并被截断
 * （上一版就在 420px 面板里把「默认仓库」截成了「默…」）。横向条形 + 百分比
 * 在固定宽度下永远可读，且能并排给出绝对数量。
 *
 * <p>注意数量是各 SKU 之和，而 SKU 单位不统一（kg / 箱 / 把 …），
 * 所以这里只用于**相对比较**，不当作可换算的重量口径。
 */
const props = defineProps<{ distribution: WarehouseDistribution[] }>();

const total = computed(() =>
    props.distribution.reduce((acc, cur) => acc + toNumber(cur.quantity), 0)
);

const rows = computed(() => {
  const sum = total.value;
  return [...props.distribution]
      .sort((a, b) => toNumber(b.quantity) - toNumber(a.quantity))
      .map((item) => {
        const qty = toNumber(item.quantity);
        const pct = sum > 0 ? (qty / sum) * 100 : 0;
        return {
          name: item.warehouseName,
          qtyText: formatQty(item.quantity),
          // 条形长度与百分比文案共用同一个值，避免「条很长但写着 0%」
          pctText: `${pct.toFixed(1)}%`,
        };
      });
});
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-wh-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}

.scm-wh-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.scm-wh-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  line-height: 1.2;
}

.scm-wh-name {
  flex: 1;
  min-width: 0;
  color: @text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scm-wh-qty {
  flex: 0 0 auto;
  font-family: @font-num;
  font-size: 13px;
  font-weight: 600;
  color: @tech-cyan;
}

// 百分比与名称同行（贴在右侧），不压在条形上 —— 条形短时压在条上的文字看不清
.scm-wh-pct {
  flex: 0 0 48px;
  text-align: right;
  font-family: @font-num;
  font-size: 12px;
  color: @text-2;
}

.scm-wh-bar {
  height: 6px;
  background: rgba(27, 77, 122, 0.42);
  border-radius: 3px;
  overflow: hidden;

  .scm-wh-bar-fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: linear-gradient(90deg, @brand-blue, @tech-cyan);
    transition: width 0.6s ease;
  }
}
</style>
