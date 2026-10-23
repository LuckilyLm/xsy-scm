<template>
  <screen-panel title="仓库库存分布" flex>
    <template #extra>库存总量 {{ formatQty(total) }}</template>

    <div v-if="!rows.length" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>暂无库存</span>
    </div>

    <div v-else class="scm-wh-list scm-scroll">
      <div v-for="row in rows" :key="row.name" class="scm-wh-row">
        <div class="scm-wh-line">
          <span class="scm-wh-name" :title="row.name">{{ row.name }}</span>
          <span class="scm-wh-qty">{{ row.qtyText }}</span>
          <span class="scm-wh-pct">{{ row.pctText }}</span>
        </div>
        <div class="scm-wh-bar">
          <span class="scm-wh-bar-fill" :style="{width: row.pctText}" />
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

/** 横向条形便于显示长仓库名；数量只用于相对比较。 */
const props = defineProps<{distribution: WarehouseDistribution[]}>();

const total = computed(() => props.distribution.reduce((acc, cur) => acc + toNumber(cur.quantity), 0));

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
        // 条形长度和百分比用同一个值
        pctText: `${pct.toFixed(1)}%`,
      };
    });
});
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

// 仓库多时在面板内滚动，不撑出边界。
.scm-wh-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
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

// 百分比放在名称行右侧
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
