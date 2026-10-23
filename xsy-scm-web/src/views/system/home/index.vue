<!-- 首页根据权限分别加载数据卡片、趋势与待办；无权限的模块不显示，也不发送对应请求。 -->
<template>
  <div class="home-workbench">
    <home-header />

    <metric-cards v-if="canDashboard" />

    <a-row v-if="showTrend || canTodo" :gutter="[16, 16]">
      <a-col v-if="showTrend" :xs="24" :xl="canTodo ? 16 : 24">
        <business-trend :metrics="trendMetrics" />
      </a-col>
      <a-col v-if="canTodo" :xs="24" :xl="showTrend ? 8 : 24">
        <HomeBusinessTodo />
      </a-col>
    </a-row>

    <a-row v-if="canRanking || canHealth" :gutter="[16, 16]">
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="canHealth ? 8 : 12">
        <ranking-card dimension="customer" />
      </a-col>
      <a-col v-if="canRanking" :xs="24" :md="12" :xl="canHealth ? 8 : 12">
        <ranking-card dimension="product" />
      </a-col>
      <a-col v-if="canHealth" :xs="24" :md="canRanking ? 12 : 24" :xl="canRanking ? 8 : 24">
        <inventory-health />
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :xl="12">
        <HomeNotice />
      </a-col>
      <a-col :xs="24" :xl="12">
        <ChangelogCard />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
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

/** 登录权限异步加载，首页必须用 computed 实时更新可见区块，避免误判无权限。 */
const canDashboard = computed(() => hasPermission('scm:dashboard:query'));
const canOrder = computed(() => hasPermission('scm:order:query'));
const canPurchase = computed(() => hasPermission('scm:purchase:query'));
const canInventoryMovement = computed(() => hasPermission('scm:inventory:movement:query'));
const canInventoryWarning = computed(() => hasPermission('scm:inventory:warning:query'));
const canTodo = computed(() => hasPermission('scm:todo:query'));

/** 趋势可切换的指标由领域权限决定，全都没有时整块不显示。 */
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

/** 排行与健康度还要工作台入口权限；领域权限只决定入口内显示哪一块。 */
const canRanking = computed(() => canDashboard.value && canOrder.value);
const canHealth = computed(() => canDashboard.value && canInventoryWarning.value);
</script>

<style lang="less" scoped>
@import './index.less';
</style>
