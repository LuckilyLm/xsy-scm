<template>
  <screen-panel title="采购概览" flex>
    <div class="scm-grid2">
      <metric-card label="今日采购单" :value="formatInt(purchase?.todayPurchaseOrderCount)" unit="张" size="lg" />
      <metric-card
        label="今日采购额"
        prefix="¥"
        :value="formatAmount(purchase?.todayPurchaseAmount)"
        size="lg"
        tone="primary"
      />
      <metric-card label="今日收货单" :value="formatInt(purchase?.todayReceiptCount)" unit="张" size="lg" />
      <metric-card label="活跃供应商" :value="formatInt(business?.todaySupplierCount)" unit="家" size="lg" />
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import ScreenPanel from './screen-panel.vue';
import MetricCard from './metric-card.vue';
import { formatAmount, formatInt } from '../format';
import type { BusinessData, PurchaseData } from '../types';

/**
 * 采购概览 2×2。
 *
 * <p>「活跃供应商」取的是**今日有采购单的供应商数**（来自经营聚合的
 * {@code todaySupplierCount}），而不是供应商总数 —— 大屏关心「今天和谁在做生意」。
 */
defineProps<{
  purchase: PurchaseData | null;
  business: BusinessData | null;
}>();
</script>

<style lang="less" scoped>
.scm-grid2 {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 10px 14px;

  > * {
    padding-left: 12px;
    border-left: 1px solid rgba(27, 77, 122, 0.55);
  }
}
</style>
