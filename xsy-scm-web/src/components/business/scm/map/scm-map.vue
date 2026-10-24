<template>
  <div class="scm-map-frame">
    <div ref="container" class="scm-map-canvas" role="region" :aria-label="route ? '配送计划路线地图' : '地址选点地图'" />
    <div v-if="error || loading" class="scm-map-state">
      <a-spin v-if="loading" tip="地图加载中" />
      <a-result v-else status="info" :title="error">
        <template #extra>
          <a-button v-if="mapConfigured()" @click="initialize">重新加载</a-button>
        </template>
      </a-result>
    </div>
  </div>
</template>
<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {createMap, mapConfigured} from './map-provider';
import type {MapPoint, RouteMapStatus, ScmLocation} from './types';

const props = defineProps<{points: MapPoint[]; route?: boolean; pickable?: boolean}>();
const emit = defineEmits<{pick: [ScmLocation]; 'route-status': [RouteMapStatus]}>();
const container = ref<HTMLElement>();
const loading = ref(false);
const error = ref('');
let map: Awaited<ReturnType<typeof createMap>> | undefined;
let disposed = false;
let generation = 0;

async function draw() {
  if (!map) return;
  try {
    await map.draw(props.points, props.route);
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '地图绘制失败';
    emit('route-status', {state: 'idle'});
  }
}

async function initialize() {
  const current = ++generation;
  map?.destroy();
  map = undefined;
  error.value = '';
  loading.value = true;
  try {
    const result = await createMap(container.value!, props.pickable ? (point) => emit('pick', point) : undefined, (status) =>
      emit('route-status', status)
    );
    if (disposed || current !== generation) {
      result.destroy();
      return;
    }
    map = result;
    await draw();
  } catch (e) {
    if (current === generation) {
      error.value = e instanceof Error ? e.message : '地图加载失败';
      emit('route-status', {state: 'idle'});
    }
  } finally {
    if (current === generation) loading.value = false;
  }
}

watch(() => props.points, draw, {deep: true});
onMounted(initialize);
onBeforeUnmount(() => {
  disposed = true;
  generation++;
  map?.destroy();
});
</script>
<style scoped>
.scm-map-frame {
  position: relative;
  min-height: 360px;
  height: 100%;
  background: var(--scm-fill, #f5f7f9);
}

.scm-map-canvas {
  height: 100%;
  min-height: 360px;
}

.scm-map-state {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: var(--scm-bg-container, #fff);
  padding: 24px;
}
</style>
<style>
/*
 * 自定义地图标记：上方胶囊标签 + 下方品牌绿图钉。
 * 这些类名由 map-provider 以 HTML 字符串注入，作用域样式碰不到，必须留在全局块里。
 */
.scm-map-pin {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  user-select: none;
}

.scm-map-pin__label {
  max-width: 180px;
  padding: 3px 10px;
  overflow: hidden;
  border: 1px solid rgba(21, 128, 61, 0.16);
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(29, 37, 30, 0.16);
  color: #1d251e;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scm-map-pin__pin {
  width: 28px;
  height: 36px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='28' height='36' viewBox='0 0 28 36'%3E%3Cpath d='M14 1.5c-6.1 0-11 4.9-11 11 0 8.2 9.6 20.4 10 20.9.5.6 1.5.6 2 0 .4-.5 10-12.7 10-20.9 0-6.1-4.9-11-11-11Z' fill='%2315803D' stroke='%23fff' stroke-width='2'/%3E%3Ccircle cx='14' cy='12.5' r='4.3' fill='%23fff'/%3E%3C/svg%3E")
    center / contain no-repeat;
  filter: drop-shadow(0 2px 4px rgba(29, 37, 30, 0.28));
}

.scm-map-info {
  white-space: pre-line;
  color: #1f2329;
  max-width: 260px;
}
</style>
