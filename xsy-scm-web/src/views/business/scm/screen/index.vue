<template>
  <div class="screen-wrapper" ref="wrapperRef">
    <div class="screen-container" ref="containerRef">
      <!-- 顶部标题栏 -->
      <header class="screen-header">
        <div class="screen-title">鲜蔬源智链 · 数据大屏</div>
        <div class="screen-time">{{ currentTime }}</div>
        <a-button type="primary" ghost size="small" @click="loadAll" :loading="loading">
          刷新
        </a-button>
      </header>

      <!-- 主体 -->
      <main class="screen-main">
        <!-- 左列：经营 KPI -->
        <section class="screen-left">
          <div class="panel kpi-panel">
            <div class="panel-title">经营数据</div>
            <div class="kpi-grid">
              <div class="kpi-item">
                <div class="kpi-label">今日订单</div>
                <div class="kpi-value">{{ businessData.todayOrderCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">今日销售额</div>
                <div class="kpi-value">¥{{ formatAmount(businessData.todaySettlementAmount) }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">累计订单</div>
                <div class="kpi-value">{{ businessData.totalOrderCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">累计销售额</div>
                <div class="kpi-value">¥{{ formatAmount(businessData.totalSettlementAmount) }}</div>
              </div>
            </div>
          </div>

          <div class="panel">
            <div class="panel-title">库存概览</div>
            <div class="kpi-grid">
              <div class="kpi-item">
                <div class="kpi-label">库存总量</div>
                <div class="kpi-value">{{ inventoryData.totalQuantity ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">在库 SKU</div>
                <div class="kpi-value">{{ inventoryData.skuCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">启用仓库</div>
                <div class="kpi-value">{{ inventoryData.warehouseCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">今日出入库</div>
                <div class="kpi-value">{{ (inventoryData.todayInboundCount ?? 0) + (inventoryData.todayOutboundCount ?? 0) }}</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 中列：图表 -->
        <section class="screen-center">
          <div class="panel chart-panel">
            <div class="panel-title">客户销售额排行（今日）</div>
            <div ref="customerChartRef" class="chart"></div>
          </div>
          <div class="panel chart-panel">
            <div class="panel-title">商品销售额排行（今日）</div>
            <div ref="productChartRef" class="chart"></div>
          </div>
        </section>

        <!-- 右列：采购与分布 -->
        <section class="screen-right">
          <div class="panel kpi-panel">
            <div class="panel-title">采购数据</div>
            <div class="kpi-grid">
              <div class="kpi-item">
                <div class="kpi-label">今日采购单</div>
                <div class="kpi-value">{{ purchaseData.todayPurchaseOrderCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">今日采购金额</div>
                <div class="kpi-value">¥{{ formatAmount(purchaseData.todayPurchaseAmount) }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">累计采购单</div>
                <div class="kpi-value">{{ purchaseData.totalPurchaseOrderCount ?? 0 }}</div>
              </div>
              <div class="kpi-item">
                <div class="kpi-label">今日收货单</div>
                <div class="kpi-value">{{ purchaseData.todayReceiptCount ?? 0 }}</div>
              </div>
            </div>
          </div>

          <div class="panel chart-panel">
            <div class="panel-title">仓库库存分布</div>
            <div ref="warehouseChartRef" class="chart"></div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import * as echarts from 'echarts';
import { screenApi } from '/@/api/business/screen-api';

defineOptions({ name: 'ScmDataScreen' });

const wrapperRef = ref<HTMLElement>();
const containerRef = ref<HTMLElement>();
const customerChartRef = ref<HTMLElement>();
const productChartRef = ref<HTMLElement>();
const warehouseChartRef = ref<HTMLElement>();

const loading = ref(false);
const currentTime = ref('');
const businessData = ref<any>({});
const inventoryData = ref<any>({});
const purchaseData = ref<any>({});

let customerChart: echarts.ECharts | null = null;
let productChart: echarts.ECharts | null = null;
let warehouseChart: echarts.ECharts | null = null;
let timer: number | undefined;

function formatAmount(val: number | string | null | undefined): string {
  const num = Number(val ?? 0);
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function updateTime() {
  currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false });
}

function initCharts() {
  if (customerChartRef.value) {
    customerChart = echarts.init(customerChartRef.value);
  }
  if (productChartRef.value) {
    productChart = echarts.init(productChartRef.value);
  }
  if (warehouseChartRef.value) {
    warehouseChart = echarts.init(warehouseChartRef.value);
  }
}

function renderCharts() {
  const topCustomers = businessData.value.topCustomers || [];
  const topProducts = businessData.value.topProducts || [];
  const distribution = inventoryData.value.warehouseDistribution || [];

  customerChart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: '#8FB7D9' } },
    yAxis: {
      type: 'category',
      data: topCustomers.map((i: any) => i.name).reverse(),
      axisLabel: { color: '#EAF6FF' },
    },
    series: [
      {
        name: '销售额',
        type: 'bar',
        data: topCustomers.map((i: any) => Number(i.amount)).reverse(),
        itemStyle: { color: '#00A8FF' },
      },
    ],
  });

  productChart?.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: '#8FB7D9' } },
    yAxis: {
      type: 'category',
      data: topProducts.map((i: any) => i.name).reverse(),
      axisLabel: { color: '#EAF6FF' },
    },
    series: [
      {
        name: '销售额',
        type: 'bar',
        data: topProducts.map((i: any) => Number(i.amount)).reverse(),
        itemStyle: { color: '#20E3FF' },
      },
    ],
  });

  warehouseChart?.setOption({
    tooltip: { trigger: 'item' },
    series: [
      {
        name: '库存量',
        type: 'pie',
        radius: ['40%', '70%'],
        label: { color: '#EAF6FF' },
        data: distribution.map((i: any) => ({
          name: i.warehouseName,
          value: Number(i.quantity),
        })),
      },
    ],
  });
}

async function loadAll() {
  loading.value = true;
  try {
    const [business, inventory, purchase] = await Promise.all([
      screenApi.getBusinessData(),
      screenApi.getInventoryData(),
      screenApi.getPurchaseData(),
    ]);
    businessData.value = business.data || {};
    inventoryData.value = inventory.data || {};
    purchaseData.value = purchase.data || {};
    renderCharts();
  } finally {
    loading.value = false;
  }
}

function resizeCharts() {
  customerChart?.resize();
  productChart?.resize();
  warehouseChart?.resize();
}

onMounted(() => {
  updateTime();
  timer = window.setInterval(updateTime, 1000);
  initCharts();
  loadAll();
  window.addEventListener('resize', resizeCharts);
});

onUnmounted(() => {
  window.clearInterval(timer);
  window.removeEventListener('resize', resizeCharts);
  customerChart?.dispose();
  productChart?.dispose();
  warehouseChart?.dispose();
});
</script>

<style lang="less" scoped>
.screen-wrapper {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #06152f;
}

.screen-container {
  width: 1920px;
  height: 1080px;
  transform-origin: center center;
  color: #eaf6ff;
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-sizing: border-box;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 64px;
  background: #071e42;
  border: 1px solid #1565b8;
  border-radius: 4px;
  margin-bottom: 16px;

  .screen-title {
    font-size: 24px;
    font-weight: bold;
    color: #00a8ff;
  }

  .screen-time {
    font-size: 16px;
    color: #8fb7d9;
  }
}

.screen-main {
  flex: 1;
  display: grid;
  grid-template-columns: 420px 1fr 420px;
  gap: 16px;
  min-height: 0;
}

.panel {
  background: #071e42;
  border: 1px solid #1565b8;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;

  .panel-title {
    font-size: 16px;
    font-weight: bold;
    color: #00a8ff;
    margin-bottom: 12px;
  }
}

.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.kpi-item {
  background: #092851;
  padding: 12px;
  border-radius: 4px;
  text-align: center;

  .kpi-label {
    font-size: 14px;
    color: #8fb7d9;
    margin-bottom: 8px;
  }

  .kpi-value {
    font-size: 24px;
    font-weight: bold;
    color: #ffd166;
  }
}

.chart-panel {
  height: calc(50% - 8px);
  display: flex;
  flex-direction: column;

  .chart {
    flex: 1;
    min-height: 0;
  }
}
</style>
