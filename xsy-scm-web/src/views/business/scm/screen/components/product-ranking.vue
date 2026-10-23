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

/** 商品排行按订单结算金额比较。不同商品的销售单位可能不同，不能简单把 kg、箱、件相加成销量。 */
const props = defineProps<{business: BusinessData | null}>();

const rows = computed<RankRow[]>(() =>
  (props.business?.topProducts ?? []).map((item) => ({
    name: item.name,
    value: toNumber(item.amount),
    text: `¥${formatAmount(item.amount)}`,
  }))
);
</script>
