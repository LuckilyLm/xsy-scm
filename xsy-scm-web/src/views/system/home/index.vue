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
      <a-col v-if="showTrend" :xs="24" :xl="canTodo ? 16 : 24">
        <business-trend ref="businessTrend" :metrics="trendMetrics"/>
      </a-col>
      <a-col v-if="canTodo" :xs="24" :xl="showTrend ? 8 : 24">
        <HomeBusinessTodo ref="businessTodo"/>
      </a-col>
    </a-row>

    <a-row v-if="canRanking || canHealth" :gutter="[16, 16]">
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="canHealth ? 8 : 12">
        <ranking-card ref="customerRank" dimension="customer"/>
      </a-col>
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="canHealth ? 8 : 12">
        <ranking-card ref="productRank" dimension="product"/>
      </a-col>
      <a-col v-if="canHealth" :xs="24" :md="canRanking ? 12 : 24" :xl="canRanking ? 8 : 24">
        <inventory-health ref="inventoryHealth"/>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <HomeNotice ref="homeNotice"/>
      </a-col>
      <a-col :xs="24" :xl="12">
        <ChangelogCard ref="changelogCard"/>
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

/**
 * 权限判定必须是 computed，不能是普通常量。
 *
 * <p>`hasPermission` 读的是 Pinia 里的 `administratorFlag` / `pointsList`，而登录信息是异步取回的
 * （`main.ts` 的 `getLoginfo` 与首页 setup 谁先谁后不确定）。普通常量只在 setup 里求值一次：
 * 若那一刻权限还没到，结果就固化成「全都无权」，之后数据到了也不会重新计算 ——
 * 首页会稳定地一片空白，且不会自我恢复。
 *
 * <p>模板里的 `v-if` 与「按权限决定是否挂载子组件（从而决定发不发请求）」都挂在这些值上，
 * 所以它们响应式之后，权限到达会让对应区块自己出现并各自去取数，不需要额外的重试逻辑。
 */
const canDashboard = computed(() => hasPermission('scm:dashboard:query'));
const canOrder = computed(() => hasPermission('scm:order:query'));
const canPurchase = computed(() => hasPermission('scm:purchase:query'));
const canInventoryMovement = computed(() => hasPermission('scm:inventory:movement:query'));
const canInventoryWarning = computed(() => hasPermission('scm:inventory:warning:query'));
const canTodo = computed(() => hasPermission('scm:todo:query'));
const canScreen = computed(() => hasPermission('scm:screen:query'));

/**
 * 趋势里能切到哪些指标，由当前人持有的领域权限决定；默认选中第一个。
 * 三种都没有时整块趋势不显示 —— 单指标端点没有「省略」的形态，缺权会直接被拒绝。
 */
const trendMetrics = computed<ScmTrendMetric[]>(() => {
  const metrics: ScmTrendMetric[] = [];
  if (canOrder.value) {
    metrics.push('sales');
  }
  if (canPurchase.value) {
    metrics.push('purchase');
  }
  if (canInventoryMovement.value) {
    metrics.push('inventory');
  }
  return metrics;
});

const showTrend = computed(() => canDashboard.value && trendMetrics.value.length > 0);

/**
 * 排行与库存健康都挂在工作台入口权限之下：后端这四个端点都要求 scm:dashboard:query，
 * 没有它就会直接拒绝。领域权限只决定「入口之内还能看哪一块」，不能单独放行请求 ——
 * 否则无权的人打开首页会看到一排「没有权限」的失败提示。
 */
const canRanking = computed(() => canDashboard.value && canOrder.value);
const canHealth = computed(() => canDashboard.value && canInventoryWarning.value);

const refreshing = ref(false);
const metricCards = ref();
const businessTrend = ref();
const businessTodo = ref();
const customerRank = ref();
const productRank = ref();
const inventoryHealth = ref();
const homeNotice = ref();
const changelogCard = ref();

/** 只刷新当前真正挂载的区块：无权限的区块不会渲染，引用自然为空。 */
async function refreshVisible() {
  if (refreshing.value) return;
  refreshing.value = true;
  try {
    await Promise.all(
      [metricCards, businessTrend, businessTodo, customerRank, productRank, inventoryHealth, homeNotice, changelogCard].map((region) =>
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
