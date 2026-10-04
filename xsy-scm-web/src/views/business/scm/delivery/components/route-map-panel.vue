<template>
  <a-alert message="计划路线按仓库起点和停靠顺序连线，展示大致配送方向。" type="info" show-icon/>
  <a-alert
      v-if="!allLocated"
      :message="`尚有 ${route.stopCount - route.locatedCount} 个停靠点未定位${
      !isLocated(startPoint) ? '，仓库起点也未定位' : ''
    }。补齐后才能确认规划。`"
      type="warning"
      show-icon
  />
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
    <ScmMap :points="mapPoints" route/>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import ScmMap from '/@/components/business/scm/map/scm-map.vue';
import {isLocated, type MapPoint} from '/@/components/business/scm/map/types';
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
  border-bottom: 1px solid var(--ant-color-border, #e5e6eb);
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
