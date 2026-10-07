<template>
  <a-alert
      v-if="!allLocated"
      :message="`尚有 ${route.stopCount - route.locatedCount} 个停靠点未定位${
      !isLocated(startPoint) ? '，仓库起点也未定位' : ''
    }。补齐后才能确认规划。`"
      type="warning"
      show-icon
  />
  <div class="route-map-toolbar">
    <strong>计划路线</strong>
    <template v-if="allLocated && routeStatus.state === 'ready' && drivingResult">
      <span class="route-map-toolbar__legs">起点仓库 → {{ route.stopCount }} 个停靠点</span>
      <span class="scm-quantity">路程 {{ distanceText }}</span>
      <span class="scm-quantity">预计 {{ durationText }}</span>
      <span class="route-map-toolbar__provider">高德驾车规划</span>
    </template>
    <span v-else-if="routeStatus.state === 'planning'" class="route-map-toolbar__legs">
      <a-spin size="small"/> 正在规划道路路线...
    </span>
    <span v-else-if="routeStatus.state === 'fallback'" class="route-map-toolbar__legs">
      道路路线暂时不可用，当前显示停靠点直线示意。
    </span>
    <!-- 缺定位时算路只覆盖已定位的连续段，此时给出总里程会被读成整线里程 -->
    <span v-else-if="!allLocated && routeStatus.state === 'ready'" class="route-map-toolbar__legs">
      仅显示已定位路段，不作为整线里程。
    </span>
  </div>
  <div class="route-map-layout">
    <div class="route-stops">
      <div class="stop-card">
        <div class="stop-card__head">
          <strong>● 起点</strong>
        </div>
        <p class="stop-card__name">{{ route.warehouseNameSnapshot }}</p>
        <p class="stop-card__line stop-card__line--muted">{{ route.warehouseAddressSnapshot || '暂无详细地址' }}</p>
      </div>
      <p v-if="canEdit" class="stop-edit-note">拖动停靠点排序，或使用上移 / 下移。顺序调整后自动保存。</p>
      <a-empty v-if="!stops.length" description="还没有停靠点，请先在线路订单中加入订单"/>
      <div
          v-for="(stop, index) in stops"
          :key="stop.id"
          class="stop-card"
          :draggable="canEdit && !busy"
          @dragstart="dragFrom = index"
          @dragend="dragFrom = undefined"
          @dragover.prevent
          @drop.prevent="drop(index)"
      >
        <div class="stop-card__head">
          <strong>{{ stop.stopSeq }}. {{ stop.customerNameSnapshot }}</strong>
          <!-- 坐标系属于定位细节：悬停标签可查，不长期占版面 -->
          <a-tooltip :title="stop.geomCrs ? `坐标系 ${stop.geomCrs}` : undefined">
            <a-tag :color="isLocated(stop) ? 'green' : 'default'">{{ isLocated(stop) ? '已定位' : '未定位' }}</a-tag>
          </a-tooltip>
        </div>
        <p class="stop-card__line">{{ stop.addressSnapshot }}</p>
        <p class="stop-card__line stop-card__line--muted">
          {{ stop.receiverNameSnapshot || '—' }} · {{ stop.receiverPhoneSnapshot || '—' }}
        </p>
        <p class="stop-card__line">
          {{ stop.orderCount }} 张订单<span v-if="canViewAmount"> · <span class="scm-money">{{ money(stop.totalAmount) }}</span></span>
        </p>
        <p class="stop-card__line stop-card__line--muted">
          预计到达 {{ stop.plannedArrivalTime ? datetime(stop.plannedArrivalTime) : '—' }}
        </p>
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
      </div>
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
.route-map-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding: 10px 16px;
  background: var(--scm-fill, rgba(0, 0, 0, 0.04));
  border-radius: 8px;
}

.route-map-toolbar__legs,
.route-map-toolbar__provider {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.route-map-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 360px minmax(0, 1fr);
  height: calc(100vh - 330px);
  min-height: 520px;
  margin-top: 16px;
}

.route-stops {
  overflow: auto;
  padding-right: 8px;
}

.stop-card {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: var(--scm-bg-container, #fff);
  border: 1px solid var(--scm-border, #e5e6eb);
  border-radius: 8px;
}

.stop-card[draggable='true'] {
  cursor: grab;
}

.stop-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.stop-card__name {
  margin: 6px 0 0;
  font-weight: 500;
}

.stop-card__line {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
}

.stop-card__line--muted {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.stop-edit-note {
  margin: 0 0 12px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

@media (max-width: 900px) {
  .route-map-layout {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }
}
</style>
