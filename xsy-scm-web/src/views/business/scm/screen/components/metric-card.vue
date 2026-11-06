<template>
  <div class="scm-metric" :class="[`is-${size}`, tone && `tone-${tone}`]">
    <div class="scm-metric-label" :title="label">
      {{ label }}
    </div>
    <div class="scm-metric-value">
      <fit-number
        :value="value"
        :prefix="prefix"
        :unit="unit"
        :size="numberSize"
        :min-size="size === 'hero' ? 32 : 16"
        :align="size === 'hero' ? 'center' : 'start'"
      />
    </div>
    <div v-if="delta !== undefined" class="scm-metric-delta" :class="`is-${direction}`">
      <template v-if="direction === 'unknown'">
        <span class="scm-metric-delta-label">较昨日 —</span>
      </template>
      <template v-else>
        <svg class="scm-metric-arrow" viewBox="0 0 10 10" aria-hidden="true">
          <path v-if="direction === 'up'" d="M5 1.5 9 8H1z" fill="currentColor" />
          <path v-else-if="direction === 'down'" d="M5 8.5 1 2h8z" fill="currentColor" />
          <rect v-else x="1.5" y="4.2" width="7" height="1.6" fill="currentColor" />
        </svg>
        <span v-if="size === 'hero'" class="scm-metric-delta-text">{{ deltaText }}</span>
        <fit-number v-else class="scm-metric-delta-value" :value="deltaText" :size="12" :min-size="10" />
        <span class="scm-metric-delta-label">较昨日</span>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import FitNumber from './fit-number.vue';
import {deltaDirection, formatDeltaText} from '../format';

/** 指标卡：用 size 表达视觉层级；环比固定是今天对比昨天。 */
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
  }>(),
  {
    size: 'md',
    delta: undefined,
  }
);

const numberSize = computed(() => ({hero: 60, lg: 30, md: 24, sm: 19})[props.size]);

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
    min-width: 0;
    max-width: 100%;
    font-size: 13px;
    line-height: 1.2;
    color: @text-2;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .scm-metric-value {
    width: 100%;
    min-width: 0;
    font-weight: 700;
    color: @text-1;
  }

  .scm-metric-delta {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    line-height: 1.25;
    white-space: nowrap;

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

  .scm-metric-delta-value {
    flex: 1 1 0%;
  }

  .scm-metric-delta-label {
    flex: 0 0 auto;
    color: @text-3;
    font-size: 12px;
  }

  &.is-hero {
    align-items: center;
    gap: 6px;
    .scm-metric-label {
      font-size: 15px;
    }
    .scm-metric-delta {
      font-size: 14px;
    }
  }

  // 语义色
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
