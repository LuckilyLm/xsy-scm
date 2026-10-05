<template>
  <div class="smart-table-btn-block">
    <a-button
        v-if="routeStatus === 'DRAFT'"
        type="primary"
        v-privilege="'scm:delivery:route:update'"
        :disabled="busy"
        @click="emit('addOrders')"
    >加入订单
    </a-button>
  </div>
  <a-table
      id="scm-delivery-route-orders"
      size="small"
      :columns="orderColumns"
      :data-source="orders"
      row-key="id"
      :pagination="false"
      :scroll="{ x: 1100 }"
      bordered
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'stop'">
        {{ stopOf(record.stopId)?.stopSeq }} · {{ stopOf(record.stopId)?.customerNameSnapshot }}
      </template>
      <template v-else-if="column.dataIndex === 'address'">
        {{ stopOf(record.stopId)?.addressSnapshot }}
      </template>
      <template v-else-if="column.dataIndex === 'orderAmountSnapshot'">
        {{ money(record.orderAmountSnapshot) }}
      </template>
      <template v-else-if="column.dataIndex === 'expectDeliveryTimeSnapshot'">
        {{ datetime(record.expectDeliveryTimeSnapshot) }}
      </template>
      <template v-else-if="column.dataIndex === 'location'">
        <a-tag :color="isLocated(stopOf(record.stopId) ?? {}) ? 'green' : 'default'">
          {{ isLocated(stopOf(record.stopId) ?? {}) ? '已定位' : '未定位' }}
        </a-tag>
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <a-button
            v-if="routeStatus === 'DRAFT'"
            type="link"
            danger
            v-privilege="'scm:delivery:route:update'"
            :disabled="busy"
            @click="emit('removeOrder', record.orderId)"
        >移除
        </a-button>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {isLocated} from '/@/components/business/scm/map/types';
import {datetime} from '../../common/scm-display';
import {money} from '../delivery-display';
import type {DeliveryStop, Id, RouteOrder} from '../delivery-types';

const props = defineProps<{
  routeStatus: string;
  orders: RouteOrder[];
  stops: DeliveryStop[];
  busy: boolean;
  canViewAmount: boolean;
}>();

const emit = defineEmits<{
  addOrders: [];
  removeOrder: [orderId: Id];
}>();

const orderColumns = computed<TableColumnsType<RouteOrder>>(() => [
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 170},
  {title: '停靠点 / 客户', dataIndex: 'stop', width: 200},
  {title: '配送地址', dataIndex: 'address', width: 240},
  {title: '期望配送', dataIndex: 'expectDeliveryTimeSnapshot', width: 180},
  ...(props.canViewAmount
      ? [{title: '订单金额', dataIndex: 'orderAmountSnapshot', align: 'right' as const, width: 120}]
      : []),
  {title: '定位', dataIndex: 'location', width: 90},
  {title: '操作', dataIndex: 'action', align: 'right' as const, width: 80},
]);

function stopOf(id: Id) {
  return props.stops.find((stop) => String(stop.id) === String(id));
}
</script>
