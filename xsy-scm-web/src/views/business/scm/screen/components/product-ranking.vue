<template>
  <screen-panel title="商品销售排行" flex>
    <template #extra>今日</template>
    <rank-bar-list :items="rows" :limit="6" empty-text="今日暂无销售" />
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import ScreenPanel from './screen-panel.vue';
import RankBarList, {type RankRow} from './rank-bar-list.vue';
import {formatAmount, toNumber} from '../format';
import type {BusinessData} from '../types';

/** 按订单结算金额排行；销售单位不同，不能把数量相加。 */
const props = defineProps<{business: BusinessData | null}>();

const rows = computed<RankRow[]>(() =>
  (props.business?.topProducts ?? []).map((item) => ({
    name: item.name,
    value: toNumber(item.amount),
    text: `¥${formatAmount(item.amount)}`,
  }))
);
</script>
