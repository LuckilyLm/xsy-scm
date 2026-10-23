<template>
  <screen-panel title="供应链分布" flex>
    <template #extra>
      <span class="scm-map-legend">气泡大小 = 该市主档数 · 着色 = 客户数</span>
      <span v-if="unlocatedText" class="scm-map-unlocated">{{ unlocatedText }}</span>
    </template>

    <div v-if="baseMapError" class="scm-state">
      <span class="scm-state-icon">!</span>
      <span>{{ baseMapError }}</span>
      <button class="scm-map-retry" type="button" @click="retryBaseMap">重新加载底图</button>
    </div>

    <div v-else class="scm-map">
      <div class="scm-map-main">
        <div ref="mapEl" class="scm-map-canvas" />
        <div v-if="!cities.length" class="scm-map-empty">暂无已归属到市的客户 / 供应商 / 仓库</div>
      </div>

      <!-- 仓库明细：地图只到市，逐仓数据单独列出 -->
      <div class="scm-map-rail">
        <div class="scm-map-rail-title">启用仓库</div>
        <div v-if="!nodes.length" class="scm-map-rail-empty">暂无启用仓库</div>
        <div v-else class="scm-map-rail-list">
          <div v-for="node in nodes" :key="node.warehouseName" class="scm-map-rail-row">
            <span class="scm-map-rail-name" :title="node.warehouseName">{{ node.warehouseName }}</span>
            <span class="scm-map-rail-figure">
              库存 <b>{{ formatQty(node.quantity) }}</b>
            </span>
            <span class="scm-map-rail-figure is-dim">
              今日出库 <b>{{ formatQty(node.todayOutboundQuantity) }}</b>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="scm-map-stats">
      <div class="scm-map-stat">
        <span class="scm-map-stat-value">{{ formatInt(cities.length) }}</span>
        <span class="scm-map-stat-label">覆盖城市</span>
      </div>
      <div class="scm-map-stat">
        <span class="scm-map-stat-value">{{ formatInt(business?.customerCount) }}</span>
        <span class="scm-map-stat-label">客户总数</span>
      </div>
      <div class="scm-map-stat">
        <span class="scm-map-stat-value">{{ formatInt(inventory?.todayOutboundCount) }}</span>
        <span class="scm-map-stat-label">出库次数</span>
      </div>
      <div class="scm-map-stat">
        <span class="scm-map-stat-value">{{ formatInt(inventory?.todayInboundCount) }}</span>
        <span class="scm-map-stat-label">入库次数</span>
      </div>
    </div>
  </screen-panel>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue';
import * as echarts from 'echarts';
import ScreenPanel from './screen-panel.vue';
import {useEcharts} from '../composables/use-echarts';
import {formatInt, formatQty, toNumber} from '../format';
import type {BusinessData, GeoData, GeoCityNode, InventoryData} from '../types';

/** 供应链地图展示已提供的位置与统计数据；缺少地理位置时提供空状态，不臆造坐标。 */
const props = defineProps<{
  inventory: InventoryData | null;
  business: BusinessData | null;
  geo: GeoData;
}>();

const MAP_NAME = 'xsy-china';
const C = {
  text1: '#eaf6ff',
  text2: '#8fb7d9',
  cyan: '#27d7fe',
  blue: '#2f80ed',
  border: '#1b4d7a',
};

const mapEl = ref<HTMLElement>();
const chart = useEcharts(mapEl);
const baseMapError = ref('');

const cities = computed(() => props.geo.cities ?? []);
const nodes = computed(() => props.inventory?.warehouseNodes ?? []);

/** 未归属到市的量要单独显示，否则图会被当成全部业务量。 */
const unlocatedText = computed(() => {
  const c = props.geo.coverage;
  if (!c) {
    return '';
  }
  const parts: string[] = [];
  const customer = c.customerTotal - c.customerLocated;
  const supplier = c.supplierTotal - c.supplierLocated;
  const warehouse = c.warehouseTotal - c.warehouseLocated;
  if (customer > 0) {
    parts.push(`客户 ${formatInt(customer)}`);
  }
  if (supplier > 0) {
    parts.push(`供应商 ${formatInt(supplier)}`);
  }
  if (warehouse > 0) {
    parts.push(`仓库 ${formatInt(warehouse)}`);
  }
  return parts.length ? `未归属：${parts.join(' · ')}` : '';
});

/** 底图只加载一次；失败时清掉缓存，便于重试。 */
let baseMap: Promise<void> | null = null;

/** 省份按行政区划编码匹配，不按名称。 */
const boundaryNameByAdcode = new Map<number, string>();

interface GeoFeature {
  properties?: {adcode?: number | string; name?: string};
}

function loadBaseMap(): Promise<void> {
  if (!baseMap) {
    baseMap = fetch(`${import.meta.env.BASE_URL}screen/china-province.json`)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`底图加载失败（HTTP ${res.status}）`);
        }
        return res.json();
      })
      .then((geoJson) => {
        for (const feature of (geoJson as {features?: GeoFeature[]}).features ?? []) {
          const adcode = Number(feature.properties?.adcode);
          const name = feature.properties?.name;
          if (name && Number.isFinite(adcode)) {
            boundaryNameByAdcode.set(adcode, name);
          }
        }
        echarts.registerMap(MAP_NAME, geoJson as Parameters<typeof echarts.registerMap>[1]);
      })
      .catch((e) => {
        baseMap = null;
        throw e;
      });
  }
  return baseMap;
}

function bubbleSize(total: number, max: number): number {
  // 用开根号，让气泡面积与数量成正比
  return 7 + 24 * Math.sqrt(max > 0 ? total / max : 0);
}

function totalOf(city: GeoCityNode): number {
  return city.customerCount + city.supplierCount + city.warehouseCount;
}

interface TipData {
  customerCount: number;
  supplierCount: number;
  warehouseCount: number;
  cityCount?: number;
}

interface TipParams {
  name: string;
  data?: TipData;
}

function tooltip(params: unknown): string {
  const item = params as TipParams;
  const data = item.data;
  if (!data) {
    return '';
  }
  const rows = [`客户 ${formatInt(data.customerCount)}`, `供应商 ${formatInt(data.supplierCount)}`, `启用仓库 ${formatInt(data.warehouseCount)}`];
  if (data.cityCount !== undefined) {
    rows.push(`覆盖市 ${formatInt(data.cityCount)}`);
  }
  return `<b>${item.name}</b><br/>${rows.join('<br/>')}`;
}

function render() {
  const list = cities.value;
  const maxTotal = list.reduce((acc, city) => Math.max(acc, totalOf(city)), 0);
  const provinceMax = (props.geo.provinces ?? []).reduce((acc, p) => Math.max(acc, p.customerCount), 0);

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(7, 30, 66, 0.94)',
      borderColor: C.border,
      textStyle: {color: C.text1, fontSize: 12},
    },
    // 底色固定表示客户数，气泡另带明细。
    visualMap: {
      type: 'continuous',
      min: 0,
      max: provinceMax || 1,
      left: 10,
      bottom: 12,
      itemWidth: 8,
      itemHeight: 60,
      text: [`${provinceMax}`, '0'],
      textStyle: {color: C.text2, fontSize: 10},
      inRange: {color: ['#0b2f52', '#1565b8', C.blue, C.cyan]},
    },
    geo: {
      map: MAP_NAME,
      roam: false,
      zoom: 1.16,
      label: {show: false},
      itemStyle: {
        areaColor: 'rgba(9, 40, 81, 0.9)',
        borderColor: 'rgba(47, 111, 158, 0.8)',
        borderWidth: 1,
      },
      emphasis: {
        label: {show: false},
        itemStyle: {areaColor: 'rgba(27, 77, 122, 1)'},
      },
      select: {disabled: true},
    },
    series: [
      {
        type: 'map',
        geoIndex: 0,
        tooltip: {formatter: tooltip},
        data: (props.geo.provinces ?? []).map((province) => ({
          // 底图没有的码退回原名，不隐藏数据
          name: boundaryNameByAdcode.get(province.provinceCode) ?? province.provinceName,
          value: province.customerCount,
          customerCount: province.customerCount,
          supplierCount: province.supplierCount,
          warehouseCount: province.warehouseCount,
          cityCount: province.cityCount,
        })),
      },
      {
        type: 'effectScatter',
        coordinateSystem: 'geo',
        // 只有已归属到市的数据才画气泡
        rippleEffect: {brushType: 'stroke', scale: 3},
        symbolSize: (_value: unknown, params: {data?: {total: number}}) => bubbleSize(params.data?.total ?? 0, maxTotal),
        tooltip: {formatter: tooltip},
        data: list.map((city) => ({
          name: city.cityName,
          value: [toNumber(city.centerLng), toNumber(city.centerLat), totalOf(city)],
          total: totalOf(city),
          customerCount: city.customerCount,
          supplierCount: city.supplierCount,
          warehouseCount: city.warehouseCount,
        })),
        itemStyle: {color: C.cyan, opacity: 0.9},
      },
    ],
  });
}

async function retryBaseMap() {
  baseMapError.value = '';
  await mountBaseMap();
}

async function mountBaseMap() {
  try {
    await loadBaseMap();
    // 底图注册完成后重新应用配置
    await nextTick();
    render();
  } catch (e: any) {
    baseMapError.value = String(e?.message ?? '地图底图加载失败');
  }
}

onMounted(mountBaseMap);
watch(() => props.geo, render);
</script>

<style lang="less" scoped>
@import '../styles/variables.less';

.scm-map-legend {
  font-size: 11px;
  color: @text-3;
}

.scm-map-unlocated {
  margin-left: 10px;
  font-size: 11px;
  color: @state-warn;
}

.scm-map-retry {
  padding: 3px 12px;
  font-size: 12px;
  color: @text-1;
  background: rgba(47, 128, 237, 0.18);
  border: 1px solid @panel-border-strong;
  border-radius: 3px;
  cursor: pointer;
}

.scm-map {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 10px;
}

.scm-map-main {
  position: relative;
  flex: 1;
  min-width: 0;
}

.scm-map-canvas {
  width: 100%;
  height: 100%;
}

// 没有归属数据时浮在图上提示
.scm-map-empty {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  padding: 5px 14px;
  font-size: 12px;
  color: @text-2;
  background: rgba(6, 21, 47, 0.82);
  border: 1px solid @panel-border;
  border-radius: 3px;
  white-space: nowrap;
}

// 右侧仓库明细
.scm-map-rail {
  flex: 0 0 186px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-left: 10px;
  border-left: 1px solid rgba(27, 77, 122, 0.6);
}

.scm-map-rail-title {
  flex: 0 0 auto;
  font-size: 11px;
  letter-spacing: 1px;
  color: @text-3;
}

.scm-map-rail-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.scm-map-rail-empty {
  font-size: 12px;
  color: @text-3;
}

.scm-map-rail-row {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  background: rgba(47, 128, 237, 0.08);
  border: 1px solid @panel-border;
  border-radius: 3px;
}

.scm-map-rail-name {
  font-size: 12px;
  font-weight: 600;
  color: @text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scm-map-rail-figure {
  font-size: 11px;
  color: @text-2;
  white-space: nowrap;

  b {
    font-family: @font-num;
    font-size: 12px;
    color: @tech-cyan;
  }

  &.is-dim b {
    color: @text-2;
  }
}

// 底部统计条
.scm-map-stats {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-top: 1px solid rgba(27, 77, 122, 0.6);
  padding-top: 8px;
  margin-top: 6px;
}

.scm-map-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;

  & + & {
    border-left: 1px solid rgba(27, 77, 122, 0.5);
  }

  .scm-map-stat-value {
    font-family: @font-num;
    font-size: 20px;
    font-weight: 700;
    color: @text-1;
  }

  .scm-map-stat-label {
    font-size: 11px;
    color: @text-3;
  }
}
</style>
