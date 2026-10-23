<template>
  <div class="scm-rank">
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
          <span class="scm-rank-value">{{ item.text }}</span>
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
import {toNumber} from '../format';

export interface RankRow {
  name: string;
  /** 原始数值，只用于算条形长度 */
  value: string | number;
  /** 已格式化好的展示文本 */
  text: string;
}

/** 排行使用紧凑的名称、金额与独立进度条布局。前三名只高亮序号，不使用大面积装饰色。 */
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
function barWidth(value: string | number): string {
  const max = props.items.reduce((acc, cur) => Math.max(acc, toNumber(cur.value)), 0);
  if (max <= 0) {
    return '0%';
  }
  const ratio = toNumber(value) / max;
  // 最小 2%：数值很小时也留一条可见的线，否则看起来像没渲染
  return `${Math.max(ratio * 100, 2).toFixed(2)}%`;
}
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-rank {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  gap: 2px;
}

.scm-rank-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 3px 0;
}

.scm-rank-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
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
  font-size: 11px;
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
  margin-left: 28px;
  background: rgba(27, 77, 122, 0.42);
  border-radius: 2px;
  overflow: hidden;

  .scm-rank-bar-fill {
    display: block;
    height: 100%;
    border-radius: 2px;
    background: linear-gradient(90deg, @brand-blue, @tech-cyan);
    transition: width 0.6s ease;
  }
}
</style>
