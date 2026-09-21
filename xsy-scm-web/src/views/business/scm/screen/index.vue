<template>
  <div ref="wrapperRef" class="scm-screen-wrapper">
    <div ref="containerRef" class="scm-screen">
      <screen-header
          :updated-at="updatedAt"
          :stale-error="staleError"
          :refreshing="refreshing"
          :fullscreen="fullscreen"
          @refresh="refresh"
          @fullscreen="toggleFullscreen"
      />

      <!-- 首屏加载：只有第一次才显示，后续静默刷新不闪屏 -->
      <div v-if="loading" class="scm-screen-state">
        <div class="scm-screen-state-inner">
          <span class="scm-spinner"/>
          <span>正在加载运营数据…</span>
        </div>
      </div>

      <!-- 首屏失败：此时页面上没有任何可用数据，显示错误态才是诚实的 -->
      <div v-else-if="error && !business && !inventory" class="scm-screen-state">
        <div class="scm-screen-state-inner is-error">
          <span class="scm-state-icon">!</span>
          <span>{{ error }}</span>
          <button class="scm-retry" type="button" @click="refresh">重新加载</button>
        </div>
      </div>

      <template v-else>
        <main class="scm-body">
          <!-- 左列：销售经营线 -->
          <section class="scm-col-side">
            <business-overview :business="business"/>
            <customer-ranking :business="business"/>
            <product-ranking :business="business"/>
          </section>

          <!-- 中列：核心指标 + 供应链分布（主视觉） -->
          <section class="scm-col-center">
            <div class="scm-core-slot">
              <core-metrics
                  :business="business"
                  :inventory="inventory"
                  :purchase="purchase"
                  :trend="trend"
              />
            </div>
            <supply-chain-map :inventory="inventory" :business="business" :geo="geo"/>
          </section>

          <!-- 右列：采购 + 库存线（与左列镜像） -->
          <section class="scm-col-side">
            <purchase-overview :purchase="purchase" :business="business"/>
            <inventory-health :health="inventory?.health ?? null"/>
            <warehouse-ranking :distribution="inventory?.warehouseDistribution ?? []"/>
          </section>
        </main>

        <!-- 底部趋势带（7 / 30 天切换，三图联动） -->
        <trend-section :trend="trend" :range="range" @update:range="setRange"/>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import ScreenHeader from './components/screen-header.vue';
import BusinessOverview from './components/business-overview.vue';
import CustomerRanking from './components/customer-ranking.vue';
import ProductRanking from './components/product-ranking.vue';
import CoreMetrics from './components/core-metrics.vue';
import SupplyChainMap from './components/supply-chain-map.vue';
import PurchaseOverview from './components/purchase-overview.vue';
import InventoryHealth from './components/inventory-health.vue';
import WarehouseRanking from './components/warehouse-ranking.vue';
import TrendSection from './components/trend-section.vue';
import {useScreenData} from './composables/use-screen-data';
import {useScreenScale} from './composables/use-screen-scale';

/**
 * 供应链运营中心（数据大屏）。
 *
 * <p>本文件**只负责布局**：三列（420 / 1000 / 420）+ 顶部状态栏 + 底部趋势带。
 * 所有数据获取在 {@link useScreenData}，所有缩放适配在 {@link useScreenScale}，
 * 每个面板的渲染细节在自己的组件里。
 *
 * <p><b>这是 Layout 之外的独立路由</b>（不带侧边栏/标签页），
 * 所以：① 任何新窗口打开它的链接都必须带 hash（`#/screen`）；
 * ② 页面内的全局监听（resize / visibilitychange）必须在卸载时注销，
 * 否则离开大屏后仍会触发。
 */
const wrapperRef = ref<HTMLElement>();
const containerRef = ref<HTMLElement>();

useScreenScale(wrapperRef, containerRef);

const {
  business,
  inventory,
  purchase,
  geo,
  trend,
  loading,
  refreshing,
  error,
  staleError,
  updatedAt,
  range,
  refresh,
  setRange,
} = useScreenData();

// ---------- 全屏 ----------
const fullscreen = ref(false);

function syncFullscreen() {
  fullscreen.value = document.fullscreenElement === wrapperRef.value;
}

async function toggleFullscreen() {
  const wrapper = wrapperRef.value;
  if (!wrapper) {
    return;
  }
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else {
      await wrapper.requestFullscreen();
    }
  } catch {
    // 浏览器拒绝全屏（非用户手势、iframe 未授权）时静默降级：
    // 大屏数据不受影响，没必要弹错误打断演示
  }
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen);
});

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen);
});
</script>

<style lang="less">
// 不带 scoped：本文件要作用在 screen-panel / metric-card 等子组件内部，
// scoped 会给选择器加 data-v 属性，父组件的 scoped 样式作用不到子组件内部。
@import './styles/screen.less';
@import './styles/panel.less';
</style>

<style lang="less" scoped>
@import './styles/variables.less';

// 外层 wrapper 占满视口并负责居中；缩放作用在内层的 1920×1080 设计稿容器上
.scm-screen-wrapper {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: @screen-bg;
}

// 中列核心指标固定高度，剩余空间全部给供应链分布地图
.scm-core-slot {
  flex: 0 0 @core-h;
  height: @core-h;
  display: flex;
  min-height: 0;
}

// ---------- 首屏三态（加载 / 失败）----------
.scm-screen-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scm-screen-state-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  color: @text-2;

  &.is-error {
    color: @state-danger;
  }
}

.scm-spinner {
  width: 26px;
  height: 26px;
  border: 2px solid rgba(47, 128, 237, 0.3);
  border-top-color: @tech-cyan;
  border-radius: 50%;
  animation: scm-rotate 0.9s linear infinite;
}

@keyframes scm-rotate {
  to {
    transform: rotate(360deg);
  }
}

.scm-retry {
  padding: 4px 16px;
  font-size: 13px;
  color: @text-1;
  background: rgba(47, 128, 237, 0.18);
  border: 1px solid @panel-border-strong;
  border-radius: 3px;
  cursor: pointer;

  &:hover {
    color: @tech-cyan;
    background: rgba(39, 215, 254, 0.18);
  }
}
</style>
