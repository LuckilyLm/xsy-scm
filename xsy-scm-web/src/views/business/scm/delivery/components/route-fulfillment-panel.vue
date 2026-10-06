<template>
  <a-alert
      message="发车后订单进入在途，客户到手才登记签收；「异常签收」含拒收，但货已真实出库，因此不冲减库存——冲销必须走后续退货流程新增反向事实。完成线路要求全部在途订单都已登记结果。"
      type="info"
      show-icon
  />
  <a-table
      id="scm-delivery-route-fulfillment"
      size="small"
      :columns="columns"
      :data-source="orders"
      row-key="id"
      :loading="loading"
      :pagination="false"
      :locale="{emptyText}"
      :scroll="{x: 1160}"
      bordered
  >
    <template #bodyCell="{column, record}">
      <template v-if="column.dataIndex === 'customer'">
        {{ stopOf(record.stopId)?.customerNameSnapshot || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'fulfillmentStatus'">
        <a-tag :color="fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].color">
          {{ fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].label }}
        </a-tag>
      </template>
      <template v-else-if="column.dataIndex === 'signedAt'">
        {{ datetime(record.signedAt) }}
      </template>
      <template v-else-if="column.dataIndex === 'signedBy'">
        {{ record.signedBy || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'signReason'">
        {{ record.signReason || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'action'">
        <a-space :size="0">
          <a-button
              v-if="canSignOrder(record)"
              type="link"
              v-privilege="DELIVERY_PERM.ORDER_SIGN"
              :disabled="busy"
              @click="emit('sign', record, 'SIGNED')"
          >签收</a-button>
          <a-button
              v-if="canSignOrder(record)"
              type="link"
              danger
              v-privilege="DELIVERY_PERM.ORDER_SIGN"
              :disabled="busy"
              @click="emit('sign', record, 'EXCEPTION')"
          >异常签收</a-button>
          <span v-if="!canSignOrder(record)">{{ signHintOf(record) }}</span>
        </a-space>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {DELIVERY_PERM} from '../use-delivery-permission';
import {datetime} from '../../common/scm-display';
import {fulfillmentStatuses} from '../delivery-types';
import type {DeliveryStop, FulfillmentStatus, Id, RouteOrder, SignResult} from '../delivery-types';

const props = defineProps<{
  orders: RouteOrder[];
  stops: DeliveryStop[];
  loading: boolean;
  busy: boolean;
  canSign: boolean;
  emptyText: string;
  canSignOrder: (record: RouteOrder) => boolean;
  signHintOf: (record: RouteOrder) => string;
}>();

const emit = defineEmits<{
  sign: [record: RouteOrder, result: SignResult];
}>();

const columns = computed<TableColumnsType<RouteOrder>>(() => [
  {title: '订单号', dataIndex: 'orderNoSnapshot', width: 180},
  {title: '客户', dataIndex: 'customer', width: 200},
  {title: '履约状态', dataIndex: 'fulfillmentStatus', width: 110, align: 'center'},
  {title: '签收时间', dataIndex: 'signedAt', width: 170},
  {title: '签收人', dataIndex: 'signedBy', width: 140},
  {title: '原因', dataIndex: 'signReason', width: 240, ellipsis: true},
  ...(props.canSign
    ? [{title: '操作', dataIndex: 'action', width: 160, align: 'center' as const, fixed: 'right' as const}]
    : []),
]);

function stopOf(id: Id) {
  return props.stops.find((stop) => String(stop.id) === String(id));
}
</script>
