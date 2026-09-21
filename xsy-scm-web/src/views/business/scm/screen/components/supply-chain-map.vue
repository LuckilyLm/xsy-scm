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
        <div ref="mapEl" class="scm-map-canvas"/>
        <div v-if="!cities.length" class="scm-map-empty">暂无已归属到市的客户 / 供应商 / 仓库</div>
      </div>

      <!-- 仓库明细：地图只到市，逐仓的库存与今日出库量仍然要看得见 -->
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

/**
 * 供应链分布（大屏主视觉，地图 M1）。
 *
 * <p>省界着色与市级气泡来自**同一个** `/scm/screen/data/geo` 返回，前端不做二次聚合：
 * 省级数值由后端从市级事实上卷，图例与气泡因此不会各自漂移。
 *
 * <p><b>底图是仓库里存档的省界 GeoJSON</b>（`public/screen/china-province.json`，
 * 属性只留 `adcode` 与 `name`），零网络依赖、零授权。省级节点按 `adcode` 与底图对齐
 * （两者同为 GB/T 2260 六位码），不按名称对齐 —— 字典里港澳用简称、边界数据用官方全称，
 * 按名称匹配会静默丢省。
 *
 * <p><b>气泡坐标是区划质心（GCJ-02）</b>，代表「这个市」而不是某个单位的实际位置；
 * 真正的点位/街道底图属于 M2，届时才需要地图服务商授权。
 */
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

/** 覆盖度差额：没归属到市的量画不出气泡，必须显性化，否则图会被当成全部业务量。 */
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

/**
 * 底图只注册一次：面板每 30 秒静默刷新一次数据，GeoJSON 却不该跟着重复下载 576 KB。
 * 失败时把缓存清掉，让「重新加载底图」真的会重试。
 */
let baseMap: Promise<void> | null = null;

/**
 * 底图要素名按 `adcode` 索引。
 *
 * <p>省名**不能**直接当 ECharts map series 的匹配键：`scm_region` 存的是简称
 * （香港 / 澳门），官方边界数据用的是全称（香港特别行政区 / 澳门特别行政区），
 * 按名称匹配会让这两省静默不着色。`adcode` 与 `province_code` 同为 GB/T 2260 六位码，
 * 是两份数据唯一稳定的共同身份，因此名称只在加载时按码回查一次。
 */
const boundaryNameByAdcode = new Map<number, string>();

interface GeoFeature {
  properties?: { adcode?: number | string; name?: string };
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
          for (const feature of (geoJson as { features?: GeoFeature[] }).features ?? []) {
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
  // 开根号而不是线性：气泡面积才与数量成正比，线性放大会让最大城市糊掉整张图
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
  const rows = [
    `客户 ${formatInt(data.customerCount)}`,
    `供应商 ${formatInt(data.supplierCount)}`,
    `启用仓库 ${formatInt(data.warehouseCount)}`,
  ];
  if (data.cityCount !== undefined) {
    rows.push(`覆盖市 ${formatInt(data.cityCount)}`);
  }
  return `<b>${item.name}</b><br/>${rows.join('<br/>')}`;
}

function render() {
  const list = cities.value;
  const maxTotal = list.reduce((acc, city) => Math.max(acc, totalOf(city)), 0);
  const provinceMax = (props.geo.provinces ?? []).reduce(
      (acc, p) => Math.max(acc, p.customerCount),
      0
  );

  chart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(7, 30, 66, 0.94)',
      borderColor: C.border,
      textStyle: {color: C.text1, fontSize: 12},
    },
    // 着色口径固定在「客户数」：大屏上同一张图同时表达三种量，气泡已经带了明细，
    // 底色再换成综合值就会让颜色和位置说两件不同的事。
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
          // 按码取底图名；字典里冒出底图没有的码时退回原名（宁可少着色，不隐藏数据）
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
        // 只有真拿到归属数据的市才会成为气泡，图上不会出现空转的涟漪
        rippleEffect: {brushType: 'stroke', scale: 3},
        symbolSize: (_value: unknown, params: { data?: { total: number } }) =>
            bubbleSize(params.data?.total ?? 0, maxTotal),
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
    // 容器在底图下载期间已经拿到尺寸，注册完成后必须再喂一次配置
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

// 底图已画出、但一个市都没归属上时的说明，浮在图上而不是替换掉图
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

// ---------- 右侧仓库明细 ----------
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

// ---------- 底部统计条 ----------
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
