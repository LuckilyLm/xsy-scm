<template>
  <screen-panel title="今日经营" flex>
    <div class="scm-grid2">
      <metric-card label="今日订单" :value="formatInt(business?.todayOrderCount)" unit="张" size="lg" />
      <metric-card label="今日销售额" prefix="¥" :value="formatAmount(business?.todaySettlementAmount)" size="lg" tone="primary" />
      <metric-card label="成交客户" :value="formatInt(business?.todayCustomerCount)" unit="家" size="lg" />
      <metric-card label="客单价" prefix="¥" :value="avgOrderText" size="lg" />
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import ScreenPanel from './screen-panel.vue';
import MetricCard from './metric-card.vue';
import {formatAmount, formatInt, toNumber} from '../format';
import type {BusinessData} from '../types';

/** 今日经营 2×2；累计数放在后台报表，不上大屏。 */
const props = defineProps<{business: BusinessData | null}>();

/** 客单价 = 今日销售额 / 今日订单数；没有订单时显示 —。 */
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

  // 细分割线分隔四格
  > * {
    padding-left: 12px;
    border-left: 1px solid rgba(27, 77, 122, 0.55);
  }
}
</style>
