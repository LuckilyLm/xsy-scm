<template>
  <screen-panel title="客户销售排行" flex>
    <template #extra>今日</template>
    <rank-bar-list :items="rows" :limit="6" empty-text="今日暂无成交"/>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import ScreenPanel from './screen-panel.vue';
import RankBarList, {type RankRow} from './rank-bar-list.vue';
import {formatAmount, toNumber} from '../format';
import type {BusinessData} from '../types';

/** 客户销售排行（今日，按订单结算金额）。 */
const props = defineProps<{ business: BusinessData | null }>();

const rows = computed<RankRow[]>(() =>
    (props.business?.topCustomers ?? []).map((item) => ({
      name: item.name,
      value: toNumber(item.amount),
      text: `¥${formatAmount(item.amount)}`,
    }))
);
</script>
