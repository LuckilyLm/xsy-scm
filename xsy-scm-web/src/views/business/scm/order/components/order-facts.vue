<template>
  <section v-if="order.inventoryFactsVisible" class="order-fact-section">
    <h3>库存预留</h3>
    <a-table :data-source="order.inventoryReservations ?? []" :columns="reservationColumns"
             row-key="reservationId" :pagination="false" size="small" bordered :scroll="{ x: scmColumnsWidth(reservationColumns) }">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'quantity'">{{ record.quantity }} {{ record.unit || '' }}</template>
        <template v-else-if="column.dataIndex === 'status'">
          {{ SCM_INVENTORY_RESERVATION_STATUS_ENUM[record.status]?.desc ?? record.status }}
        </template>
        <template v-else-if="column.dataIndex === 'occurredAt'">{{ datetime(record.occurredAt) }}</template>
      </template>
    </a-table>
  </section>

  <section v-if="order.sortingFactsVisible" class="order-fact-section">
    <h3>分拣进度</h3>
    <a-table :data-source="order.sortingProgress ?? []" :columns="sortingColumns"
             :row-key="(record: OrderSorting) => `${record.taskId}-${record.orderItemId}`"
             :pagination="false" size="small" bordered :scroll="{x: scmColumnsWidth(sortingColumns)}">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'taskStatus'">
          {{ SCM_SORTING_TASK_STATUS_ENUM[record.taskStatus]?.desc ?? record.taskStatus }}
        </template>
        <template v-else-if="column.dataIndex === 'result'">
          {{ record.result ? SCM_SORTING_RESULT_ENUM[record.result]?.desc ?? record.result : '待录入' }}
        </template>
        <template v-else-if="column.dataIndex === 'plannedQuantity'">
          {{ record.plannedQuantity }} {{ record.unit || '' }}
        </template>
        <template v-else-if="column.dataIndex === 'sortedQuantity'">
          {{ record.sortedQuantity == null ? '—' : `${record.sortedQuantity} ${record.unit || ''}` }}
        </template>
      </template>
    </a-table>
  </section>

  <section v-if="order.deliveryFactsVisible" class="order-fact-section">
    <h3>配送进度</h3>
    <a-descriptions v-if="order.deliveryProgress" size="small" bordered :column="{xs: 1, sm: 2}">
      <a-descriptions-item label="线路">{{ order.deliveryProgress.routeNo }}</a-descriptions-item>
      <a-descriptions-item label="仓库">{{ order.deliveryProgress.warehouseName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="线路状态">
        {{ routeStatuses[order.deliveryProgress.routeStatus as RouteStatus]?.label ?? order.deliveryProgress.routeStatus }}
      </a-descriptions-item>
      <a-descriptions-item label="本单履约">
        {{ fulfillmentStatuses[order.deliveryProgress.fulfillmentStatus as FulfillmentStatus]?.label ?? order.deliveryProgress.fulfillmentStatus }}
      </a-descriptions-item>
      <a-descriptions-item label="发车时间">{{ datetime(order.deliveryProgress.dispatchedAt) }}</a-descriptions-item>
      <a-descriptions-item label="签收时间">{{ datetime(order.deliveryProgress.signedAt) }}</a-descriptions-item>
      <a-descriptions-item v-if="order.deliveryProgress.signReason" label="签收说明" :span="2">
        {{ order.deliveryProgress.signReason }}
      </a-descriptions-item>
    </a-descriptions>
    <a-empty v-else description="当前授权范围内暂无配送记录"/>
  </section>

  <section v-if="order.returnFactsVisible" class="order-fact-section">
    <h3>退货与应收更正</h3>
    <a-table :data-source="order.returns ?? []" :columns="returnColumns" row-key="returnId"
             :pagination="false" size="small" bordered :scroll="{ x: scmColumnsWidth(returnColumns) }">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          {{ SCM_ORDER_RETURN_STATUS_ENUM[record.status]?.desc ?? record.status }}
        </template>
        <template v-else-if="column.dataIndex === 'approvedAmount'">
          {{ record.status === 'APPROVED' ? amount(record.approvedAmount) : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'approvedAt'">{{ datetime(record.approvedAt) }}</template>
        <template v-else-if="column.dataIndex === 'redReceivable'">
          <template v-if="!order.receivableFactsVisible">无应收查看权限</template>
          <template v-else-if="record.redReceivableRecorded">已记红字应收 {{ amount(record.redReceivableAmount) }}</template>
          <template v-else>{{ record.status === 'APPROVED' ? '未形成红字应收' : '待审批或未批准' }}</template>
        </template>
      </template>
    </a-table>
  </section>

  <section v-if="order.fundingFactsVisible" class="order-fact-section">
    <h3>订单资金</h3>
    <a-descriptions v-if="order.funding" size="small" bordered :column="{xs: 1, sm: 2}">
      <a-descriptions-item label="当前应付">{{ amount(order.funding.payableAmount) }}</a-descriptions-item>
      <a-descriptions-item label="累计实收">{{ amount(order.funding.receivedAmount) }}</a-descriptions-item>
      <a-descriptions-item label="剩余待付">{{ amount(order.funding.remainingAmount) }}</a-descriptions-item>
      <a-descriptions-item label="超出当前应付的已收金额">
        {{ amount(order.funding.excessReceivedAmount) }}
      </a-descriptions-item>
    </a-descriptions>
    <a-empty v-else description="订单确认后可查看应付和实收"/>
  </section>

  <section v-if="order.refundFactsVisible" class="order-fact-section">
    <h3>退款与资金返还</h3>
    <a-alert message="退款单完成与资金到账分开记录；资金结果以原资金来源返还事实为准。"
             type="info" show-icon class="order-fact-note"/>
    <a-table :data-source="order.refunds ?? []" :columns="refundColumns" row-key="refundId"
             :pagination="false" size="small" bordered :scroll="{ x: scmColumnsWidth(refundColumns) }">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          {{ SCM_ORDER_REFUND_STATUS_ENUM[record.status]?.desc ?? record.status }}
        </template>
        <template v-else-if="column.dataIndex === 'refundAmount'">{{ amount(record.refundAmount) }}</template>
        <template v-else-if="column.dataIndex === 'completedAt'">{{ datetime(record.completedAt) }}</template>
        <template v-else-if="column.dataIndex === 'fundingStatus'">
          {{ fundingLabel(record.fundingStatus) }}
          <span v-if="record.fundingSucceededAmount != null">（已返还 {{ amount(record.fundingSucceededAmount) }}）</span>
        </template>
      </template>
    </a-table>
  </section>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import type {TableColumnsType} from 'ant-design-vue';
import {SCM_INVENTORY_RESERVATION_STATUS_ENUM} from '/@/constants/business/scm/inventory-const';
import {SCM_SORTING_RESULT_ENUM, SCM_SORTING_TASK_STATUS_ENUM} from '/@/constants/business/scm/sorting-const';
import {SCM_ORDER_REFUND_STATUS_ENUM, SCM_ORDER_RETURN_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import {fulfillmentStatuses, routeStatuses, type FulfillmentStatus, type RouteStatus} from '../../delivery/delivery-types';
import {datetime} from '../../common/scm-display';
import {amount} from '../order-form-model';
import type {Order, OrderRefundFact, OrderReservation, OrderReturnFact, OrderSorting} from '../order-types';

defineProps<{order: Order}>();

const reservationColumns: TableColumnsType<OrderReservation> = [
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'skuName', width: 150},
  {title: '仓库', dataIndex: 'warehouseName', width: 140},
  {title: '预留数量', dataIndex: 'quantity', width: 130},
  {title: '状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '预留时间', dataIndex: 'occurredAt', width: 170},
];
const sortingColumns: TableColumnsType<OrderSorting> = [
  {title: '任务', dataIndex: 'taskNo', width: 160},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'skuName', width: 140},
  {title: '任务状态', dataIndex: 'taskStatus', align: 'center', width: 96},
  {title: '计划量', dataIndex: 'plannedQuantity', width: 110},
  {title: '实拣量', dataIndex: 'sortedQuantity', width: 110},
  {title: '分拣结果', dataIndex: 'result', align: 'center', width: 110},
];
const returnColumns: TableColumnsType<OrderReturnFact> = [
  {title: '退货单号', dataIndex: 'returnNo', width: 170},
  {title: '审批状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '原因', dataIndex: 'reason', width: 180},
  {title: '批准金额', dataIndex: 'approvedAmount', width: 130},
  {title: '批准时间', dataIndex: 'approvedAt', width: 170},
  {title: '应收红冲', dataIndex: 'redReceivable', width: 190},
];
const refundColumns: TableColumnsType<OrderRefundFact> = [
  {title: '退款单号', dataIndex: 'refundNo', width: 170},
  {title: '关联退货单', dataIndex: 'returnNo', width: 170},
  {title: '退款单状态', dataIndex: 'status', align: 'center', width: 106},
  {title: '退款金额', dataIndex: 'refundAmount', width: 130},
  {title: '完成时间', dataIndex: 'completedAt', width: 170},
  {title: '资金返还', dataIndex: 'fundingStatus', align: 'center', width: 250},
];

function fundingLabel(status?: OrderRefundFact['fundingStatus']): string {
  if (status === 'PENDING') return '待执行';
  if (status === 'PROCESSING') return '处理中';
  if (status === 'SUCCEEDED') return '已返还';
  if (status === 'FAILED') return '返还失败';
  return '暂无资金返还事实';
}
</script>

<style scoped>
.order-fact-section { margin-top: 24px; }
.order-fact-section h3 { margin-bottom: 12px; }
.order-fact-note { margin-bottom: 12px; }
</style>
