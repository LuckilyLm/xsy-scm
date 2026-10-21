<template>
  <header class="scm-header">
    <!-- 左：品牌 + 运行状态 -->
    <div class="scm-header-side is-left">
      <div class="scm-brand">
        <span class="scm-brand-name">鲜蔬园智链</span>
      </div>
      <span class="scm-brand-divider"/>
      <!--
        运行状态跟着品牌走：它是「系统级」信号，右侧 400px 留给「日期时间 + 更新时间 + 两个按钮」
        已经刚好够用，再挤一个徽章就会把「2026-10-11 星期日」压成两行。
      -->
      <span class="scm-status-item" :class="statusClass">
        <i class="scm-dot"/>{{ statusText }}
      </span>
    </div>

    <!-- 中：中心标题（带左右引导线，不用龙纹/跑马灯） -->
    <div class="scm-header-center">
      <span class="scm-header-line"/>
      <h1 class="scm-header-title">供应链运营中心</h1>
      <span class="scm-header-line"/>
    </div>

    <!-- 右：日期时钟 + 更新时间 + 操作 -->
    <div class="scm-header-side is-right">
      <div class="scm-clock">
        <span class="scm-clock-date">{{ dateText }} {{ weekdayText }}</span>
        <span class="scm-clock-time">{{ timeText }}</span>
      </div>
      <div class="scm-status">
        <button class="scm-btn" type="button" title="立即刷新" aria-label="刷新运营数据" :disabled="refreshing" @click="emit('refresh')">
          <svg class="scm-icon" viewBox="0 0 16 16" aria-hidden="true" :class="{ 'is-spin': refreshing }">
            <path
                d="M13.6 8a5.6 5.6 0 1 1-1.64-3.96"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
            />
            <path d="M13.8 1.6v3.2h-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
        </button>
        <button class="scm-btn" type="button" :title="fullscreen ? '退出全屏' : '进入全屏'"
                :aria-label="fullscreen ? '退出全屏' : '进入全屏'" @click="emit('fullscreen')">
          <svg v-if="!fullscreen" class="scm-icon" viewBox="0 0 16 16" aria-hidden="true">
            <path
                d="M6 2H2v4M10 2h4v4M10 14h4v-4M6 14H2v-4"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
                stroke-linejoin="round"
            />
          </svg>
          <svg v-else class="scm-icon" viewBox="0 0 16 16" aria-hidden="true">
            <path
                d="M2 6h4V2M14 6h-4V2M14 10h-4v4M2 10h4v4"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
                stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useScreenClock} from '../composables/use-screen-clock';

/** 大屏顶部显示时钟、刷新状态与全屏入口；静默刷新失败时必须提示数据可能过期。 */
const props = defineProps<{
  /** 静默刷新失败信息（空串表示正常） */
  staleError: string;
  /** 是否正在刷新 */
  refreshing: boolean;
  /** 当前是否全屏 */
  fullscreen: boolean;
}>();

const emit = defineEmits<{
  (e: 'refresh'): void;
  (e: 'fullscreen'): void;
}>();

const {dateText, weekdayText, timeText} = useScreenClock();

const statusText = computed(() => (props.staleError ? props.staleError : '系统运行正常'));
const statusClass = computed(() => (props.staleError ? 'is-warn' : 'is-ok'));
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-header {
  flex: 0 0 @header-h;
  height: @header-h;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  background: linear-gradient(180deg, rgba(11, 42, 84, 0.92) 0%, rgba(7, 30, 66, 0.62) 100%);
  border: 1px solid @panel-border;
  border-radius: 4px;
}

.scm-header-side {
  flex: 0 0 400px;
  display: flex;
  align-items: center;

  &.is-left {
    gap: 12px;
  }

  /*
    右侧装的是「日期 + 时间 + 更新时间 + 两个按钮」，比左侧的品牌长。
    400px 是下限而不是定值：内容再长也不会把「2026-10-11 星期日」压成两行。
  */
  &.is-right {
    flex: 0 0 auto;
    min-width: 400px;
    justify-content: flex-end;
  }
}

/* 品牌与运行状态之间的细分隔线：两件事，别被读成一句话 */
.scm-brand-divider {
  flex: 0 0 auto;
  width: 1px;
  height: 16px;
  background: @panel-border;
}

.scm-brand {
  display: flex;
  flex-direction: column;
  line-height: 1.2;

  .scm-brand-name {
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 2px;
    color: @text-1;
  }
}

.scm-header-center {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;

  .scm-header-line {
    flex: 1;
    height: 1px;
    background: linear-gradient(90deg, transparent, @panel-border-strong 60%, @panel-border-strong);
  }

  .scm-header-line:last-child {
    background: linear-gradient(90deg, @panel-border-strong, @panel-border-strong 40%, transparent);
  }

  .scm-header-title {
    flex: 0 0 auto;
    margin: 0;
    padding: 4px 18px;
    font-size: 24px;
    font-weight: 700;
    letter-spacing: 4px;
    color: @text-1;
    // 标题两侧的直角括号：克制，只有上下两条细线
    border-top: 1px solid @panel-border-strong;
    border-bottom: 1px solid @panel-border-strong;
    text-shadow: 0 0 14px rgba(39, 215, 254, 0.28);
  }
}

.scm-clock {
  flex: 0 0 auto;
  display: flex;
  /*
    日期与时间排同一行。拆成上下两行会占掉 55px 顶栏高度（顶栏只有 64px），
    而右侧本来就只有「现在几点」这一件事，一行说完更省地方也更像时钟。
  */
  align-items: baseline;
  gap: 8px;
  line-height: 1.25;
  margin-right: 14px;
  /*
    字号 / 字重 / 字体 / 颜色写在容器上，两段共用：日期与时间是同一条信息的两半，
    做成「小号弱色 + 大号粗体」会被读成主次关系，那不是这里要表达的。
  */
  font-family: @font-num;
  font-size: 20px;
  font-weight: 600;
  color: @text-1;
  letter-spacing: 1px;

  .scm-clock-date,
  .scm-clock-time {
    /* 日期 + 星期是一个整体，折行会变成「…星期」+「日」，两行都不像话 */
    white-space: nowrap;
  }
}

.scm-status {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.scm-status-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  white-space: nowrap;

  &.is-ok {
    color: @state-ok;
  }

  &.is-warn {
    color: @state-warn;
  }
}

.scm-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.scm-icon {
  width: 13px;
  height: 13px;
  display: block;

  &.is-spin {
    animation: scm-spin 0.9s linear infinite;
  }
}

@keyframes scm-spin {
  to {
    transform: rotate(360deg);
  }
}

.scm-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: @text-2;
  background: rgba(47, 128, 237, 0.12);
  border: 1px solid @panel-border;
  border-radius: 3px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, background 0.2s;

  &:hover {
    color: @tech-cyan;
    border-color: @panel-border-strong;
    background: rgba(39, 215, 254, 0.16);
  }
}
</style>
