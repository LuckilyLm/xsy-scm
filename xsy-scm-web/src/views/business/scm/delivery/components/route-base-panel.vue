<template>
  <a-descriptions bordered :column="2" size="small">
    <a-descriptions-item label="仓库">{{ route.warehouseNameSnapshot }}</a-descriptions-item>
    <a-descriptions-item label="仓库定位">
      {{ isLocated(startPoint) ? `已定位 · ${route.startGeomCrs}` : '未定位' }}
    </a-descriptions-item>
    <a-descriptions-item label="仓库地址" :span="2">{{ route.warehouseAddressSnapshot || '—' }}</a-descriptions-item>
    <a-descriptions-item label="司机">{{ route.driverNameSnapshot || '未分配' }} {{ route.driverPhoneSnapshot }}</a-descriptions-item>
    <a-descriptions-item label="车辆">{{ route.vehicleNoSnapshot || '未分配' }}</a-descriptions-item>
    <a-descriptions-item label="计划发车">{{ datetime(route.plannedDepartureTime) }}</a-descriptions-item>
    <a-descriptions-item label="备注">{{ route.remark || '—' }}</a-descriptions-item>
    <a-descriptions-item v-if="route.dispatchedAt" label="发车">
      {{ datetime(route.dispatchedAt) }} · {{ route.dispatchedBy || '—' }}
    </a-descriptions-item>
    <a-descriptions-item v-if="route.completedAt" label="完成">
      {{ datetime(route.completedAt) }} · {{ route.completedBy || '—' }}
    </a-descriptions-item>
    <!-- 发车在库存域生成出库事实；配送侧只读单号，不重复展示数量或金额。 -->
    <a-descriptions-item v-if="showOutbound" label="出库单">
      <a-button v-if="route.outboundNo" type="link" size="small" @click="emit('outbound', route.outboundNo)">
        {{ route.outboundNo }}
      </a-button>
      <span v-else>—（整条线路实发为 0，未生成出库单）</span>
    </a-descriptions-item>
    <a-descriptions-item v-if="route.cancelReason" label="取消原因" :span="2">
      {{ route.cancelReason }}
    </a-descriptions-item>
  </a-descriptions>
</template>

<script setup lang="ts">
import {isLocated, type MapPoint} from '/@/components/business/scm/map/types';
import {datetime} from '../../common/scm-display';
import type {DeliveryRoute} from '../delivery-types';

defineProps<{
  route: DeliveryRoute;
  startPoint: MapPoint;
  showOutbound: boolean;
}>();

const emit = defineEmits<{
  outbound: [outboundNo: string];
}>();
</script>
