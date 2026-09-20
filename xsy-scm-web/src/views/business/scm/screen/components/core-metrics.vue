<template>
  <screen-panel flex flush>
    <div class="scm-core">
      <!-- 全屏最大数字：今日销售额 -->
      <div class="scm-core-hero">
        <metric-card
          label="今日销售额"
          prefix="¥"
          :value="formatAmount(business?.todaySettlementAmount)"
          :delta="salesDelta"
          size="hero"
          tone="primary"
        />
      </div>

      <!-- 次级指标：层级明显低于 hero -->
      <div class="scm-core-sub">
        <metric-card
          label="今日订单"
          :value="formatInt(business?.todayOrderCount)"
          unit="张"
          :delta="orderDelta"
          size="sm"
        />
        <metric-card
          label="今日客户"
          :value="formatInt(business?.todayCustomerCount)"
          unit="家"
          :delta="null"
          size="sm"
        />
        <metric-card
          label="今日出库"
          :value="formatInt(inventory?.todayOutboundCount)"
          unit="次"
          :delta="null"
          size="sm"
        />
        <metric-card
          label="今日采购"
          :value="formatInt(purchase?.todayPurchaseOrderCount)"
          unit="张"
          :delta="purchaseDelta"
          size="sm"
        />
      </div>
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ScreenPanel from './screen-panel.vue';
import MetricCard from './metric-card.vue';
import { formatAmount, formatDelta, formatInt, toNumber } from '../format';
import type { BusinessData, InventoryData, PurchaseData, TrendData } from '../types';

/**
 * 今日核心指标（整屏视觉中心）。
 *
 * <p><b>环比从趋势接口取「昨天」</b>：{@code /business} 只给当天与累计，没有昨日基线，
 * 而趋势接口的序列最后一项是今天、倒数第二项是昨天，且语义与 KPI 一致
 * （销售额对销售额、订单数对订单数）。
 *
 * <p><b>为什么「今日客户」「今日出库」不显示环比</b>：趋势序列里没有可比的同口径历史
 * （{@code outboundQuantity} 是数量、不是出库次数；客户数没有历史序列）。
 * 传 {@code null} 会渲染成「较昨日 —」，这是刻意的 —— 宁可显示「无法比较」，
 * 也不要拿一个口径不同的数字算出一个看起来合理的百分比。
 */
const props = defineProps<{
  business: BusinessData | null;
  inventory: InventoryData | null;
  purchase: PurchaseData | null;
  trend: TrendData;
}>();

/** 取趋势序列里「昨天」的值（最后一项是今天）。 */
function yesterday(series: Array<string | number> | undefined): number | null {
  if (!series || series.length < 2) {
    return null;
  }
  return toNumber(series[series.length - 2]);
}

function deltaOf(current: string | number | null | undefined, series: Array<string | number> | undefined) {
  const prev = yesterday(series);
  if (prev === null) {
    return null;
  }
  return formatDelta(toNumber(current), prev);
}

const salesDelta = computed(() =>
  deltaOf(props.business?.todaySettlementAmount, props.trend.sales)
);

const orderDelta = computed(() => deltaOf(props.business?.todayOrderCount, props.trend.orders));

const purchaseDelta = computed(() =>
  deltaOf(props.purchase?.todayPurchaseOrderCount, props.trend.purchaseOrders)
);
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-core {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 8px 16px 10px;
}

.scm-core-hero {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  // hero 底部一条渐隐分隔线，把「最大数字」和次级指标分开
  border-bottom: 1px solid rgba(27, 77, 122, 0.5);
  padding-bottom: 6px;
  margin-bottom: 8px;
}

.scm-core-sub {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;

  > * + * {
    padding-left: 12px;
    border-left: 1px solid rgba(27, 77, 122, 0.5);
  }
}
</style>
