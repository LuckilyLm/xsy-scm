<template>
  <div class="scm-metric" :class="[`is-${size}`, tone && `tone-${tone}`]">
    <div class="scm-metric-label">
      {{ label }}
      <span v-if="hint" class="scm-metric-hint">{{ hint }}</span>
    </div>
    <div class="scm-metric-value">
      <span v-if="prefix" class="scm-metric-prefix">{{ prefix }}</span>
      <span class="scm-metric-number">{{ value }}</span>
      <span v-if="unit" class="scm-metric-unit">{{ unit }}</span>
    </div>
    <div v-if="delta !== undefined" class="scm-metric-delta" :class="`is-${direction}`">
      <template v-if="direction === 'unknown'">
        <span class="scm-metric-delta-label">较昨日 —</span>
      </template>
      <template v-else>
        <svg class="scm-metric-arrow" viewBox="0 0 10 10" aria-hidden="true">
          <path
              v-if="direction === 'up'"
              d="M5 1.5 9 8H1z"
              fill="currentColor"
          />
          <path v-else-if="direction === 'down'" d="M5 8.5 1 2h8z" fill="currentColor"/>
          <rect v-else x="1.5" y="4.2" width="7" height="1.6" fill="currentColor"/>
        </svg>
        <span class="scm-metric-delta-text">{{ deltaText }}</span>
        <span class="scm-metric-delta-label">较昨日</span>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {deltaDirection, formatDeltaText} from '../format';

/**
 * 单个指标卡。
 *
 * <p>设计稿的关键要求是**视觉层级递减**：今日销售额 → 订单数 → 客户/出库/采购，
 * 而不是每个 KPI 一样大。所以这里用 {@link size} 表达层级，而不是让调用方各写样式。
 *
 * <p><b>环比用「较昨日」而不是「较上一期」</b>：目前唯一的数据源就是今天 vs 昨天。
 * 说「较上一期」会让用户以为可以选周期，而它其实不能。
 *
 * <p>基数为 0 时（昨天没营业）环比显示「—」而不是 0% —— 见 {@link deltaDirection}。
 */
const props = withDefaults(
    defineProps<{
      label: string;
      value: string | number;
      /** 单位后缀（kg / 张 / 人） */
      unit?: string;
      /** 前缀（金额用 ¥） */
      prefix?: string;
      /** 环比百分比；undefined 表示这张卡不显示环比 */
      delta?: number | null;
      /** 层级：hero 是整屏最大数字 */
      size?: 'hero' | 'lg' | 'md' | 'sm';
      /** 语义色；不传则用默认一级文字色 */
      tone?: 'primary' | 'ok' | 'warn' | 'danger';
      /** 标签右侧的补充说明 */
      hint?: string;
    }>(),
    {
      size: 'md',
      delta: undefined,
    }
);

const direction = computed(() => deltaDirection(props.delta ?? null));
const deltaText = computed(() => formatDeltaText(props.delta ?? null));
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-metric {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;

  .scm-metric-label {
    display: flex;
    align-items: baseline;
    gap: 6px;
    font-size: 13px;
    color: @text-2;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .scm-metric-hint {
    font-size: 11px;
    color: @text-3;
  }

  .scm-metric-value {
    display: flex;
    align-items: baseline;
    gap: 3px;
    font-family: @font-num;
    font-weight: 700;
    color: @text-1;
    line-height: 1.1;
    white-space: nowrap;
  }

  .scm-metric-prefix,
  .scm-metric-unit {
    font-size: 0.5em;
    font-weight: 500;
    color: @text-2;
  }

  .scm-metric-delta {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    line-height: 1;

    &.is-up {
      color: @state-ok;
    }

    &.is-down {
      color: @state-danger;
    }

    &.is-flat,
    &.is-unknown {
      color: @text-3;
    }
  }

  .scm-metric-arrow {
    width: 9px;
    height: 9px;
    flex: 0 0 auto;
  }

  .scm-metric-delta-label {
    color: @text-3;
    font-size: 11px;
  }

  // ---------- 层级 ----------
  &.is-hero {
    gap: 6px;

    .scm-metric-label {
      font-size: 15px;
      letter-spacing: 1px;
    }

    .scm-metric-value {
      font-size: 62px;
      letter-spacing: 1px;
    }

    .scm-metric-delta {
      font-size: 14px;
    }
  }

  &.is-lg .scm-metric-value {
    font-size: 30px;
  }

  &.is-md .scm-metric-value {
    font-size: 24px;
  }

  &.is-sm {
    .scm-metric-label {
      font-size: 12px;
    }

    .scm-metric-value {
      font-size: 19px;
    }
  }

  // ---------- 语义色 ----------
  &.tone-primary .scm-metric-value {
    color: @tech-cyan;
  }

  &.tone-ok .scm-metric-value {
    color: @state-ok;
  }

  &.tone-warn .scm-metric-value {
    color: @state-warn;
  }

  &.tone-danger .scm-metric-value {
    color: @state-danger;
  }
}
</style>
