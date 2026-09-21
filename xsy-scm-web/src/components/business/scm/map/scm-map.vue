<template>
  <div class="scm-map-frame">
    <div ref="container" class="scm-map-canvas" role="region"
         :aria-label="route ? '配送计划路线地图' : '地址选点地图'"/>
    <div v-if="error || loading" class="scm-map-state">
      <a-spin v-if="loading" tip="地图加载中"/>
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
import type {MapPoint, ScmLocation} from './types';

const props = defineProps<{ points: MapPoint[]; route?: boolean; pickable?: boolean }>();
const emit = defineEmits<{ pick: [ScmLocation] }>();
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
  }
}

async function initialize() {
  const current = ++generation;
  map?.destroy();
  map = undefined;
  error.value = '';
  loading.value = true;
  try {
    const result = await createMap(container.value!, props.pickable ? (point) => emit('pick', point) : undefined);
    if (disposed || current !== generation) {
      result.destroy();
      return;
    }
    map = result;
    await draw();
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : '地图加载失败';
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
  background: var(--ant-color-fill-quaternary, #f5f7f9);
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
  background: var(--ant-color-bg-container, #fff);
  padding: 24px;
}
</style>
<style>
.scm-map-label {
  display: inline-block;
  padding: 3px 6px;
  background: #fff;
  color: #1f2329;
  font-size: 12px;
}

.scm-map-info {
  white-space: pre-line;
  color: #1f2329;
  max-width: 260px;
}
</style>
