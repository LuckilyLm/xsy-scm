<template>
  <screen-panel title="商品销售排行" flex>
    <template #extra>今日</template>
    <rank-bar-list :items="rows" :limit="6" empty-text="今日暂无销售"/>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import ScreenPanel from './screen-panel.vue';
import RankBarList, {type RankRow} from './rank-bar-list.vue';
import {formatAmount, toNumber} from '../format';
import type {BusinessData} from '../types';

/**
 * 商品销售排行（今日，按订单结算金额）。
 *
 * <p><b>指标是金额而不是设计稿写的「销量（kg）」</b>：销量需要把各 SKU 的数量相加，
 * 而 SKU 的记账单位各不相同（kg / 箱 / 把 / 颗 / 托 / 件），
 * 「100 kg + 50 箱 = 150」没有任何业务含义。金额是唯一能跨 SKU 比较的口径。
 */
const props = defineProps<{ business: BusinessData | null }>();

const rows = computed<RankRow[]>(() =>
    (props.business?.topProducts ?? []).map((item) => ({
      name: item.name,
      value: toNumber(item.amount),
      text: `¥${formatAmount(item.amount)}`,
    }))
);
</script>
