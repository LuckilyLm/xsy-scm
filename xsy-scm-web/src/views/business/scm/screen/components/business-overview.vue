<template>
  <screen-panel title="今日经营" flex>
    <div class="scm-grid2">
      <metric-card label="今日订单" :value="formatInt(business?.todayOrderCount)" unit="张" size="lg" />
      <metric-card
        label="今日销售额"
        prefix="¥"
        :value="formatAmount(business?.todaySettlementAmount)"
        size="lg"
        tone="primary"
      />
      <metric-card label="成交客户" :value="formatInt(business?.todayCustomerCount)" unit="家" size="lg" />
      <metric-card label="客单价" prefix="¥" :value="avgOrderText" size="lg" />
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import ScreenPanel from './screen-panel.vue';
import MetricCard from './metric-card.vue';
import { formatAmount, formatInt, toNumber } from '../format';
import type { BusinessData } from '../types';

/**
 * 今日经营 2×2。
 *
 * <p><b>这里刻意不显示「累计订单 / 累计销售额」</b>：设计稿指出累计数适合后台报表，
 * 大屏要表达的是「现在正在发生什么」。累计数仍然在接口里（{@code totalOrderCount}），
 * 需要时可作为副标题补上。
 */
const props = defineProps<{ business: BusinessData | null }>();

/**
 * 客单价 = 今日销售额 / 今日订单数。
 *
 * <p>订单数为 0 时显示「—」而不是「¥0.00」：没有订单时客单价**不存在**，
 * 而不是「客单价是零元」。
 */
const avgOrderText = computed(() => {
  const count = toNumber(props.business?.todayOrderCount);
  if (count <= 0) {
    return '—';
  }
  return formatAmount(toNumber(props.business?.todaySettlementAmount) / count);
});
</script>

<style lang="less" scoped>
.scm-grid2 {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 10px 14px;

  // 四个格子各自加一条细分割线，让 2×2 的结构在大屏上一眼可辨
  > * {
    padding-left: 12px;
    border-left: 1px solid rgba(27, 77, 122, 0.55);
  }
}
</style>
