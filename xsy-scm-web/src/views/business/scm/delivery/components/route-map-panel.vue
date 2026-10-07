<template>
  <a-alert
      v-if="!allLocated"
      :message="`尚有 ${route.stopCount - route.locatedCount} 个停靠点未定位${
      !isLocated(startPoint) ? '，仓库起点也未定位' : ''
    }。补齐后才能确认规划。`"
      type="warning"
      show-icon
  />
  <div v-if="routeStatus.state !== 'idle'" class="route-plan-summary">
    <strong>计划配送路线</strong>
    <p v-if="routeStatus.state === 'planning'"><a-spin size="small"/> 正在规划道路路线...</p>
    <template v-else-if="drivingResult">
      <p>起点仓库 → {{ route.stopCount }} 个停靠点</p>
      <p class="route-plan-metrics">
        <span class="scm-quantity">路程 {{ distanceText }}</span>
        <span class="scm-quantity">预计 {{ durationText }}</span>
        <span>高德驾车规划</span>
      </p>
      <p v-if="!allLocated" class="route-plan-note">未定位停靠点处线路中断，未计入路程与耗时。</p>
    </template>
    <p v-else>道路路线暂时不可用，当前显示停靠点直线示意。</p>
  </div>
  <div class="route-map-layout">
    <div class="route-stops">
      <div class="warehouse-stop">
        <strong>起点 · {{ route.warehouseNameSnapshot }}</strong>
        <p>{{ route.warehouseAddressSnapshot || '未填写地址' }}</p>
      </div>
      <p v-if="canEdit">拖动停靠点排序，或使用上移 / 下移。顺序调整后自动保存。</p>
      <a-empty v-if="!stops.length" description="还没有停靠点，请先在线路订单中加入订单"/>
      <ol>
        <li
            v-for="(stop, index) in stops"
            :key="stop.id"
            :draggable="canEdit && !busy"
            @dragstart="dragFrom = index"
            @dragend="dragFrom = undefined"
            @dragover.prevent
            @drop.prevent="drop(index)"
        >
          <div class="stop-heading">
            <strong>{{ stop.stopSeq }}. {{ stop.customerNameSnapshot }}</strong>
            <a-tag :color="isLocated(stop) ? 'green' : 'default'">{{ isLocated(stop) ? '已定位' : '未定位' }}</a-tag>
          </div>
          <p>{{ stop.addressSnapshot }}</p>
          <p>{{ stop.receiverNameSnapshot || '—' }} · {{ stop.receiverPhoneSnapshot || '—' }}</p>
          <p>
            {{ stop.orderCount }} 张订单<span v-if="canViewAmount"> · {{ money(stop.totalAmount) }}</span><span
              v-if="stop.geomCrs"> · {{ stop.geomCrs }}</span>
          </p>
          <p v-if="stop.plannedArrivalTime">计划到达：{{ datetime(stop.plannedArrivalTime) }}</p>
          <a-space v-if="canEdit">
            <a-button
                size="small"
                :disabled="index === 0 || busy"
                :aria-label="`上移${stop.customerNameSnapshot}`"
                @click="move(index, index - 1)"
            >上移</a-button>
            <a-button
                size="small"
                :disabled="index === stops.length - 1 || busy"
                :aria-label="`下移${stop.customerNameSnapshot}`"
                @click="move(index, index + 1)"
            >下移</a-button>
            <a-button size="small" :disabled="busy" @click="emit('editStop', stop)">定位 / 备注</a-button>
          </a-space>
        </li>
      </ol>
    </div>
    <ScmMap :points="mapPoints" route @route-status="routeStatus = $event"/>
  </div>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import ScmMap from '/@/components/business/scm/map/scm-map.vue';
import {isLocated, type MapPoint, type RouteMapStatus} from '/@/components/business/scm/map/types';
import {datetime} from '../../common/scm-display';
import {money} from '../delivery-display';
import type {DeliveryRoute, DeliveryStop} from '../delivery-types';

defineProps<{
  route: DeliveryRoute;
  stops: DeliveryStop[];
  startPoint: MapPoint;
  mapPoints: MapPoint[];
  allLocated: boolean;
  canEdit: boolean;
  busy: boolean;
  canViewAmount: boolean;
}>();

const emit = defineEmits<{
  moveStop: [from: number, to: number];
  editStop: [stop: DeliveryStop];
}>();

const routeStatus = ref<RouteMapStatus>({state: 'idle'});
const drivingResult = computed(() => (routeStatus.value.state === 'ready' ? routeStatus.value.result : undefined));
const distanceText = computed(() => (drivingResult.value ? `${(drivingResult.value.distanceMeters / 1000).toFixed(1)} km` : ''));
const durationText = computed(() => (drivingResult.value ? formatDuration(drivingResult.value.durationSeconds) : ''));

/** 耗时按分钟取整；跨小时给出「X 小时 Y 分」。 */
function formatDuration(seconds: number): string {
  const minutes = Math.round(seconds / 60);
  if (minutes <= 0) return '1 分钟内';
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} 小时 ${rest} 分` : `${hours} 小时`;
}

const dragFrom = ref<number>();

function drop(index: number) {
  if (dragFrom.value !== undefined) move(dragFrom.value, index);
  dragFrom.value = undefined;
}

function move(from: number, to: number) {
  if (from !== to) emit('moveStop', from, to);
}
</script>

<style scoped>
.route-map-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 360px minmax(0, 1fr);
  margin-top: 16px;
}

.route-plan-summary {
  margin-top: 16px;
  padding: 12px 16px;
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 8px;
}

.route-plan-summary strong {
  display: block;
}

.route-plan-summary p {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 8px 0 0;
  flex-wrap: wrap;
}

.route-plan-note {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.route-stops {
  max-height: 620px;
  overflow: auto;
  padding-right: 8px;
}

.route-stops ol {
  list-style: none;
  margin: 0;
  padding: 0;
}

.route-stops li,
.warehouse-stop {
  border-bottom: 1px solid var(--scm-border, #e5e6eb);
  padding: 16px 0;
}

.route-stops li[draggable='true'] {
  cursor: grab;
}

.route-stops p {
  margin: 8px 0;
  overflow-wrap: anywhere;
}

.stop-heading {
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

@media (max-width: 900px) {
  .route-map-layout {
    grid-template-columns: 1fr;
  }

  .route-stops {
    max-height: none;
  }
}
</style>
