<template>
  <div class="scm-rank scm-scroll">
    <div v-if="!items.length" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>{{ emptyText }}</span>
    </div>
    <template v-else>
      <div v-for="(item, index) in visible" :key="item.name" class="scm-rank-item">
        <div class="scm-rank-line">
          <span class="scm-rank-index" :class="index < 3 ? `is-top${index + 1}` : ''">
            {{ String(index + 1).padStart(2, '0') }}
          </span>
          <span class="scm-rank-name" :title="item.name">{{ item.name }}</span>
          <fit-number class="scm-rank-value" :value="item.text" align="end" />
        </div>
        <div class="scm-rank-bar">
          <span class="scm-rank-bar-fill" :style="{width: barWidth(item.value)}" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FitNumber from './fit-number.vue';
import {toNumber} from '../format';

export interface RankRow {
  name: string;
  /** 原始数值，只用于算条形长度 */
  value: string | number;
  /** 已格式化好的展示文本 */
  text: string;
}

/** 排行条：前三名只高亮序号。 */
const props = withDefaults(
  defineProps<{
    items: RankRow[];
    /** 最多显示几条 */
    limit?: number;
    /** 无数据时的文案 */
    emptyText?: string;
  }>(),
  {limit: 6, emptyText: '今日暂无数据'}
);

const visible = computed(() => props.items.slice(0, props.limit));

/** 条形长度以本列表最大值为 100%（相对长度，不是绝对量级）。 */
const maximum = computed(() => props.items.reduce((max, row) => Math.max(max, toNumber(row.value)), 0));

function barWidth(value: string | number): string {
  const max = maximum.value;
  if (max <= 0) {
    return '0%';
  }
  const ratio = toNumber(value) / max;
  return `${Math.min(100, Math.max(0, ratio * 100))}%`;
}
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-rank {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}

.scm-rank-item {
  flex: 0 0 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 3px 0;
}

.scm-rank-line {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) minmax(0, 46%);
  align-items: center;
  gap: 8px;
  font-size: 14px;
  line-height: 1.2;
}

.scm-rank-index {
  flex: 0 0 auto;
  width: 20px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: @font-num;
  font-size: 12px;
  font-weight: 700;
  color: @text-3;
  border: 1px solid rgba(88, 122, 154, 0.45);
  border-radius: 2px;

  &.is-top1 {
    color: @rank-1;
    border-color: @rank-1;
  }

  &.is-top2 {
    color: @rank-2;
    border-color: @rank-2;
  }

  &.is-top3 {
    color: @rank-3;
    border-color: @rank-3;
  }
}

.scm-rank-name {
  flex: 1;
  min-width: 0;
  color: @text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scm-rank-value {
  flex: 0 0 auto;
  font-family: @font-num;
  font-size: 13px;
  font-weight: 600;
  color: @tech-cyan;
}

.scm-rank-bar {
  height: 4px;
  margin-left: 32px;
  background: rgba(27, 77, 122, 0.42);
  border-radius: 2px;
  overflow: hidden;

  .scm-rank-bar-fill {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: linear-gradient(90deg, @brand-blue, @tech-cyan);
  }
}
</style>
