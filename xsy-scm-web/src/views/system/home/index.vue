<!--
  * 首页「供应链工作台」
  *
  * 装配顺序：欢迎区 → KPI 卡 → 经营趋势 + 业务待办 → 客户与商品排行 + 库存健康 → 通知公告 + 系统更新。
  *
  * 每个区块各自决定发不发请求：没有对应权限的区块既不渲染也不请求，因此不会出现
  * 「打开首页先收到一串无权限失败」。刷新按钮只刷新当前真正挂载的区块。
-->
<template>
  <div class="home-workbench">
    <home-header :can-screen="canScreen" :refreshing="refreshing" @refresh="refreshVisible"/>

    <metric-cards v-if="canDashboard" ref="metricCards"/>

    <a-row v-if="showTrend || canTodo" :gutter="[16, 16]">
      <a-col v-if="showTrend" :xs="24" :xl="16">
        <business-trend ref="businessTrend" :metrics="trendMetrics"/>
      </a-col>
      <a-col v-privilege="'scm:todo:query'" :xs="24" :xl="showTrend ? 8 : 24">
        <HomeBusinessTodo ref="businessTodo"/>
      </a-col>
    </a-row>

    <a-row v-if="canRanking || canHealth" :gutter="[16, 16]">
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="8">
        <ranking-card ref="customerRank" dimension="customer"/>
      </a-col>
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="8">
        <ranking-card ref="productRank" dimension="product"/>
      </a-col>
      <a-col v-if="canHealth" :xs="24" :md="12" :xl="8">
        <inventory-health ref="inventoryHealth"/>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <HomeNotice :notice-type-id="1"/>
      </a-col>
      <a-col :xs="24" :xl="12">
        <ChangelogCard/>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import type {ScmTrendMetric} from '/@/api/business/scm/dashboard-api';
import {hasPermission} from '/@/views/business/scm/common/scm-permission';
import HomeHeader from './home-header.vue';
import HomeNotice from './home-notice.vue';
import ChangelogCard from './components/changelog-card.vue';
import HomeBusinessTodo from './components/business-todo-card/home-business-todo.vue';
import MetricCards from './components/metric-cards.vue';
import BusinessTrend from './components/business-trend.vue';
import RankingCard from './components/ranking-card.vue';
import InventoryHealth from './components/inventory-health.vue';

const canDashboard = hasPermission('scm:dashboard:query');
const canOrder = hasPermission('scm:order:query');
const canPurchase = hasPermission('scm:purchase:query');
const canInventoryMovement = hasPermission('scm:inventory:movement:query');
const canInventoryWarning = hasPermission('scm:inventory:warning:query');
const canTodo = hasPermission('scm:todo:query');
const canScreen = hasPermission('scm:screen:query');

/**
 * 趋势里能切到哪些指标，由当前人持有的领域权限决定；默认选中第一个。
 * 三种都没有时整块趋势不显示 —— 单指标端点没有「省略」的形态，缺权会直接被拒绝。
 */
const trendMetrics = computed<ScmTrendMetric[]>(() => {
  const metrics: ScmTrendMetric[] = [];
  if (canOrder) {
    metrics.push('sales');
  }
  if (canPurchase) {
    metrics.push('purchase');
  }
  if (canInventoryMovement) {
    metrics.push('inventory');
  }
  return metrics;
});

const showTrend = computed(() => canDashboard && trendMetrics.value.length > 0);

/**
 * 排行与库存健康都挂在工作台入口权限之下：后端这四个端点都要求 scm:dashboard:query，
 * 没有它就会直接拒绝。领域权限只决定「入口之内还能看哪一块」，不能单独放行请求 ——
 * 否则无权的人打开首页会看到一排「没有权限」的失败提示。
 */
const canRanking = canDashboard && canOrder;
const canHealth = canDashboard && canInventoryWarning;

const refreshing = ref(false);
const metricCards = ref();
const businessTrend = ref();
const businessTodo = ref();
const customerRank = ref();
const productRank = ref();
const inventoryHealth = ref();

/** 只刷新当前真正挂载的区块：无权限的区块不会渲染，引用自然为空。 */
async function refreshVisible() {
  refreshing.value = true;
  try {
    await Promise.all(
      [metricCards, businessTrend, businessTodo, customerRank, productRank, inventoryHealth].map((region) =>
        region.value?.load?.()
      )
    );
  } finally {
    refreshing.value = false;
  }
}
</script>

<style lang="less" scoped>
@import './index.less';
</style>
