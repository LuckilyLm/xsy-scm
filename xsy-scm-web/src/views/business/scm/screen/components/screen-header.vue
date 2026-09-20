<template>
  <header class="scm-header">
    <!-- 左：品牌 -->
    <div class="scm-header-side is-left">
      <div class="scm-brand">
        <span class="scm-brand-name">鲜蔬源智链</span>
        <span class="scm-brand-sub">SUPPLY CHAIN INTELLIGENCE</span>
      </div>
    </div>

    <!-- 中：中心标题（带左右引导线，不用龙纹/跑马灯） -->
    <div class="scm-header-center">
      <span class="scm-header-line" />
      <h1 class="scm-header-title">供应链运营中心</h1>
      <span class="scm-header-line" />
    </div>

    <!-- 右：日期时钟 + 状态 -->
    <div class="scm-header-side is-right">
      <div class="scm-clock">
        <span class="scm-clock-date">{{ dateText }} {{ weekdayText }}</span>
        <span class="scm-clock-time">{{ timeText }}</span>
      </div>
      <div class="scm-status">
        <span class="scm-status-item" :class="statusClass">
          <i class="scm-dot" />{{ statusText }}
        </span>
        <span class="scm-status-item is-muted">
          <svg class="scm-icon" viewBox="0 0 16 16" aria-hidden="true">
            <path
              d="M13.6 8a5.6 5.6 0 1 1-1.64-3.96"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
            />
            <path d="M13.8 1.6v3.2h-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          </svg>
          数据更新时间 {{ updatedText }}
        </span>
        <button class="scm-btn" type="button" title="立即刷新" @click="emit('refresh')">
          <svg class="scm-icon" viewBox="0 0 16 16" aria-hidden="true" :class="{ 'is-spin': refreshing }">
            <path
              d="M13.6 8a5.6 5.6 0 1 1-1.64-3.96"
              fill="none"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
            />
            <path d="M13.8 1.6v3.2h-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          </svg>
        </button>
        <button class="scm-btn" type="button" :title="fullscreen ? '退出全屏' : '进入全屏'" @click="emit('fullscreen')">
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
import { computed } from 'vue';
import { useScreenClock, formatClock } from '../composables/use-screen-clock';

/**
 * 大屏状态栏。
 *
 * <p>设计稿明确要求「不要只是一个标题条」，且不要龙纹/六边形/跑马灯 ——
 * 所以中间标题只用两条引导线，状态用一个小圆点。
 *
 * <p><b>「系统运行正常」不是装饰</b>：它的依据是「最近一次数据拉取是否成功」。
 * 静默刷新失败时这里变成黄色「数据可能已过期」，把问题放在用户视线里，
 * 而不是把已经渲染好的数据换成错误页。
 */
const props = defineProps<{
  /** 最近一次成功刷新的时间 */
  updatedAt: Date | null;
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

const { dateText, weekdayText, timeText } = useScreenClock();

const updatedText = computed(() => formatClock(props.updatedAt));

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

  &.is-right {
    justify-content: flex-end;
  }
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

  .scm-brand-sub {
    font-size: 9px;
    letter-spacing: 1.6px;
    color: @text-3;
    transform: scale(0.98);
    transform-origin: left center;
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
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.25;
  margin-right: 14px;

  .scm-clock-date {
    font-size: 12px;
    color: @text-2;
  }

  .scm-clock-time {
    font-family: @font-num;
    font-size: 20px;
    font-weight: 600;
    color: @text-1;
    letter-spacing: 1px;
  }
}

.scm-status {
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

  &.is-muted {
    color: @text-3;
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
