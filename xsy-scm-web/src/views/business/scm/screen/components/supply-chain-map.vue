<template>
  <screen-panel title="供应链网络" flex>
    <template #extra>
      <span class="scm-map-hint">第一阶段为抽象网络，接入高德后整体替换本区域</span>
    </template>

    <div v-if="!nodes.length" class="scm-state">
      <span class="scm-state-icon">—</span>
      <span>暂无启用仓库</span>
    </div>

    <div v-else class="scm-map">
      <!-- ① 仓库节点层 -->
      <div class="scm-map-nodes">
        <div v-for="node in nodes" :key="node.name" class="scm-map-node">
          <span class="scm-map-diamond" />
          <span class="scm-map-node-name" :title="node.name">{{ node.name }}</span>
          <div class="scm-map-node-stats">
            <span class="scm-map-node-stat">
              库存 <b>{{ node.qtyText }}</b>
            </span>
            <span class="scm-map-node-stat is-dim">
              今日出库量 <b>{{ node.outText }}</b>
            </span>
          </div>
        </div>
      </div>

      <!-- ② 汇聚连线 + 配送线路 -->
      <div class="scm-map-links">
        <svg class="scm-map-svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <line
            v-for="(node, index) in nodes"
            :key="node.name"
            :x1="nodeX(index)"
            y1="0"
            x2="50"
            y2="46"
            class="scm-map-link"
            vector-effect="non-scaling-stroke"
          />
          <line x1="50" y1="54" x2="50" y2="100" class="scm-map-link" vector-effect="non-scaling-stroke" />
        </svg>
        <div class="scm-map-route">
          <span class="scm-map-route-text">配送线路</span>
        </div>
      </div>

      <!-- ③ 客户节点层（今日成交客户） -->
      <div v-if="customers.length" class="scm-map-customers">
        <div v-for="customer in customers" :key="customer.name" class="scm-map-customer-chip">
          <span class="scm-map-customer-dot" />
          <span class="scm-map-customer-name" :title="customer.name">{{ customer.name }}</span>
          <span class="scm-map-customer-amount">{{ customer.amountText }}</span>
        </div>
      </div>

      <!-- ④ 客户网络汇总 -->
      <div class="scm-map-summary">
        <span class="scm-map-summary-dot" />
        <span class="scm-map-summary-text">
          客户网络 共 <b>{{ formatInt(business?.customerCount) }}</b> 家 ·
          今日成交 <b>{{ formatInt(business?.todayCustomerCount) }}</b> 家
        </span>
      </div>

      <!-- ⑤ 底部统计条 -->
      <div class="scm-map-stats">
        <div class="scm-map-stat">
          <span class="scm-map-stat-value">{{ formatInt(nodes.length) }}</span>
          <span class="scm-map-stat-label">启用仓库</span>
        </div>
        <div class="scm-map-stat">
          <span class="scm-map-stat-value">{{ formatInt(business?.customerCount) }}</span>
          <span class="scm-map-stat-label">客户总数</span>
        </div>
        <div class="scm-map-stat">
          <span class="scm-map-stat-value">{{ formatInt(inventory?.todayOutboundCount) }}</span>
          <span class="scm-map-stat-label">出库次数</span>
        </div>
        <div class="scm-map-stat">
          <span class="scm-map-stat-value">{{ formatInt(inventory?.todayInboundCount) }}</span>
          <span class="scm-map-stat-label">入库次数</span>
        </div>
      </div>
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ScreenPanel from './screen-panel.vue';
import { formatAmount, formatInt, formatQty } from '../format';
import type { BusinessData, InventoryData, WarehouseNode } from '../types';

/**
 * 供应链网络（大屏主视觉）。
 *
 * <p><b>为什么是抽象网络而不是地图</b>：{@code warehouse} 与 {@code customer} 目前只有
 * 自由文本 {@code address}，没有经纬度、也没有省市区结构化字段，做不出真实地理分布。
 * 设计稿也明确「第一阶段不要卡住大屏」——先用抽象网络占位，
 * 等配送模块落地后再整体替换本区域（高德 Marker / LineLayer），
 * 上层布局与接口都不受影响。
 *
 * <p>五层结构自上而下：仓库节点 → 汇聚连线 + 配送线路 → 今日成交客户 → 客户网络汇总 → 统计条。
 * 用 {@code justify-content: space-between} 把五层摊满整个面板，
 * 否则仓库多、客户少时下半屏会整片留白。
 *
 * <p><b>连线用 SVG 的 {@code vector-effect="non-scaling-stroke"}</b>：
 * 这个 SVG 用 {@code preserveAspectRatio="none"} 拉伸到整个容器，
 * 线宽会被非等比缩放拉成粗细不一；non-scaling-stroke 让线宽恒定 1px。
 */
const props = defineProps<{
  inventory: InventoryData | null;
  business: BusinessData | null;
}>();

const nodes = computed(() =>
  (props.inventory?.warehouseNodes ?? []).map((node: WarehouseNode) => ({
    name: node.warehouseName,
    qtyText: formatQty(node.quantity),
    outText: formatQty(node.todayOutboundQuantity),
  }))
);

/** 今日成交客户（取排行前 5），作为网络里的客户节点。 */
const customers = computed(() =>
  (props.business?.topCustomers ?? []).slice(0, 5).map((item) => ({
    name: item.name,
    amountText: `¥${formatAmount(item.amount)}`,
  }))
);

/** 第 index 个节点在 100 宽坐标系里的中心横坐标（与上方 flex 等分布局对齐）。 */
function nodeX(index: number): number {
  const total = nodes.value.length || 1;
  return ((index + 0.5) / total) * 100;
}
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-map-hint {
  font-size: 11px;
  color: @text-3;
}

.scm-map {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
}

// ---------- ① 仓库节点 ----------
.scm-map-nodes {
  flex: 0 0 auto;
  display: flex;
  justify-content: space-around;
  gap: 10px;
}

.scm-map-node {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 9px 8px;
  background: rgba(47, 128, 237, 0.1);
  border: 1px solid @panel-border;
  border-radius: 4px;
}

.scm-map-diamond {
  width: 10px;
  height: 10px;
  background: @tech-cyan;
  transform: rotate(45deg);
  box-shadow: 0 0 8px rgba(39, 215, 254, 0.7);
  margin-bottom: 3px;
}

.scm-map-node-name {
  max-width: 100%;
  font-size: 14px;
  font-weight: 600;
  color: @text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scm-map-node-stats {
  display: flex;
  gap: 14px;
}

.scm-map-node-stat {
  font-size: 11px;
  color: @text-2;
  white-space: nowrap;

  b {
    font-family: @font-num;
    font-size: 13px;
    color: @tech-cyan;
  }

  &.is-dim b {
    color: @text-2;
  }
}

// ---------- ② 汇聚连线 ----------
.scm-map-links {
  position: relative;
  flex: 1;
  min-height: 56px;
  margin: 0 6px;
}

.scm-map-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.scm-map-link {
  stroke: @panel-border-strong;
  stroke-width: 1;
  stroke-dasharray: 4 4;
  opacity: 0.75;
}

.scm-map-route {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  padding: 3px 14px;
  background: @panel-bg-solid;
  border: 1px solid @panel-border-strong;
  border-radius: 10px;
  white-space: nowrap;

  .scm-map-route-text {
    font-size: 11px;
    letter-spacing: 2px;
    color: @tech-cyan;
  }
}

// ---------- ③ 客户节点 ----------
.scm-map-customers {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
}

.scm-map-customer-chip {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 7px 6px;
  background: rgba(39, 215, 254, 0.07);
  border: 1px solid @panel-border;
  border-radius: 4px;
}

.scm-map-customer-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: @brand-blue;
  box-shadow: 0 0 7px rgba(47, 128, 237, 0.9);
}

.scm-map-customer-name {
  max-width: 100%;
  font-size: 12px;
  color: @text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scm-map-customer-amount {
  font-family: @font-num;
  font-size: 12px;
  font-weight: 600;
  color: @tech-cyan;
}

// ---------- ④ 客户网络汇总 ----------
.scm-map-summary {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 7px 12px;
  background: rgba(39, 215, 254, 0.08);
  border: 1px solid @panel-border;
  border-radius: 4px;
  margin: 0 6px;
}

.scm-map-summary-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: @brand-blue;
  box-shadow: 0 0 10px rgba(47, 128, 237, 0.9);
  flex: 0 0 auto;
}

.scm-map-summary-text {
  font-size: 13px;
  color: @text-2;

  b {
    font-family: @font-num;
    font-size: 15px;
    color: @tech-cyan;
  }
}

// ---------- ⑤ 底部统计 ----------
.scm-map-stats {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-top: 1px solid rgba(27, 77, 122, 0.6);
  padding-top: 8px;
}

.scm-map-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;

  & + & {
    border-left: 1px solid rgba(27, 77, 122, 0.5);
  }

  .scm-map-stat-value {
    font-family: @font-num;
    font-size: 20px;
    font-weight: 700;
    color: @text-1;
  }

  .scm-map-stat-label {
    font-size: 11px;
    color: @text-3;
  }
}
</style>
