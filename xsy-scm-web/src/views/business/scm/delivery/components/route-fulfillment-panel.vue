<template>
  <!-- 1~3 行用轻量列表：一两条数据铺一张宽表格，下面会空出一大片 -->
  <div v-if="orders.length && orders.length <= 3" class="fulfillment-cards">
    <div v-for="record in orders" :key="record.id" class="fulfillment-card">
      <div class="fulfillment-card__head">
        <strong class="scm-mono">{{ record.orderNoSnapshot }}</strong>
        <a-tag :color="fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].color">
          {{ fulfillmentStatuses[record.fulfillmentStatus as FulfillmentStatus].label }}
        </a-tag>
      </div>
      <p class="fulfillment-card__line">{{ stopOf(record.stopId)?.customerNameSnapshot || '—' }}</p>
      <p class="fulfillment-card__line fulfillment-card__line--muted">
        签收 {{ datetime(record.signedAt) }} · {{ record.signedBy || '—' }}
      </p>
      <p v-if="record.signReason" class="fulfillment-card__line fulfillment-card__line--muted">
        原因 {{ record.signReason }}
      </p>
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
        <span v-if="!canSignOrder(record)" class="fulfillment-card__line--muted">{{ signHintOf(record) }}</span>
      </a-space>
    </div>
  </div>
  <a-table
      v-else
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

<style scoped>
.fulfillment-cards {
  display: grid;
  gap: 12px;
}

.fulfillment-card {
  padding: 12px 16px;
  border: 1px solid var(--scm-border, #f0f0f0);
  border-radius: 8px;
}

.fulfillment-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fulfillment-card__line {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
}

.fulfillment-card__line--muted {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}
</style>
