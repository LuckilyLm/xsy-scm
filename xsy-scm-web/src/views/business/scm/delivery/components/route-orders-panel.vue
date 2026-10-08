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
    <span class="order-count">共 {{ orders.length }} 单</span>
  </div>
  <!-- 1~3 行用轻量列表：一两条数据铺一张宽表格，下面会空出一大片 -->
  <a-empty v-if="!orders.length" description="还没有订单，请先加入订单"/>
  <div v-else-if="orders.length <= 3" class="order-cards">
    <div v-for="record in orders" :key="record.id" class="order-card">
      <div class="order-card__head">
        <strong class="scm-mono">{{ record.orderNoSnapshot }}</strong>
        <span v-if="canViewAmount" class="scm-money">{{ money(record.orderAmountSnapshot) }}</span>
      </div>
      <p class="order-card__line">{{ stopOf(record.stopId)?.stopSeq }} · {{ stopOf(record.stopId)?.customerNameSnapshot }}</p>
      <p class="order-card__line order-card__line--muted">{{ stopOf(record.stopId)?.addressSnapshot }}</p>
      <p class="order-card__line order-card__line--muted">期望配送 {{ datetime(record.expectDeliveryTimeSnapshot) }}</p>
      <div class="order-card__foot">
        <a-tag :color="isLocated(stopOf(record.stopId) ?? {}) ? 'green' : 'default'">
          {{ isLocated(stopOf(record.stopId) ?? {}) ? '已定位' : '未定位' }}
        </a-tag>
        <a-button
            v-if="routeStatus === 'DRAFT'"
            type="link"
            danger
            size="small"
            v-privilege="'scm:delivery:route:update'"
            :disabled="busy"
            @click="emit('removeOrder', record.orderId)"
        >移除
        </a-button>
      </div>
    </div>
  </div>
  <a-table
      v-else
      id="scm-delivery-route-orders"
      size="small"
      :columns="orderColumns"
      :data-source="orders"
      row-key="id"
      :pagination="false"
      :scroll="{ x: 1170 }"
      bordered
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.dataIndex === 'orderNoSnapshot'">
        <span class="scm-mono">{{ record.orderNoSnapshot || '—' }}</span>
      </template>
      <template v-else-if="column.dataIndex === 'stop'">
        {{ stopOf(record.stopId)?.stopSeq ?? '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'customer'">
        {{ stopOf(record.stopId)?.customerNameSnapshot || '—' }}
      </template>
      <template v-else-if="column.dataIndex === 'address'">
        <span v-if="stopOf(record.stopId)?.addressSnapshot" class="scm-cell-wrap">
          {{ stopOf(record.stopId)?.addressSnapshot }}
        </span>
        <span v-else>—</span>
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
  {title: '停靠点', dataIndex: 'stop', width: 80, align: 'center' as const},
  {title: '客户', dataIndex: 'customer', width: 170},
  {title: '配送地址', dataIndex: 'address', width: 240},
  {title: '期望配送', dataIndex: 'expectDeliveryTimeSnapshot', width: 180},
  ...(props.canViewAmount
      ? [{title: '订单金额', dataIndex: 'orderAmountSnapshot', align: 'right' as const, width: 120}]
      : []),
  {title: '定位', dataIndex: 'location', width: 90},
  {title: '操作', dataIndex: 'action', align: 'center' as const, width: 80},
]);

function stopOf(id: Id) {
  return props.stops.find((stop) => String(stop.id) === String(id));
}
</script>

<style scoped>
.order-count {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.order-cards {
  display: grid;
  gap: 12px;
}

.order-card {
  padding: 12px 16px;
  border: 1px solid var(--scm-border, #f0f0f0);
  border-radius: 8px;
}

.order-card__head,
.order-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.order-card__line {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
}

.order-card__line--muted {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 13px;
}

.order-card__foot {
  margin-top: 8px;
  justify-content: flex-start;
}
</style>
