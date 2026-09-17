<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-list.vue（C 无独立详情页）
复制日期：2026-09-16。Copy First + Adapt。仿 W4 `order-detail.vue` 的结构。
剪枝：履约 / 支付 / 库存抵扣 / C 的 `actualAmount`。
适配：6 值状态机命令（A13）、`scm:purchase:*`（A22）、右对齐 + 等宽（A17）、`null` → `—`（A18）、
      **单级分配平铺**（`PurchaseOrderVO.allocations`，前端不必自己拍平）、
      日志按 DESC 返回（最新在前）、loading/error/retry（A27）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-drawer :open="visible" :title="order?.orderNo || '采购单详情'" width="min(1240px, 96vw)" @close="visible = false">
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action><a-button @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="order">
        <a-space class="actions" wrap>
          <a-tag>{{ SCM_PURCHASE_STATUS_ENUM[order.status ?? '']?.desc }}</a-tag>
          <a-button @click="load">刷新</a-button>
          <a-button
            v-if="order.status === 'DRAFT'"
            type="primary"
            v-privilege="'scm:purchase:submit'"
            @click="submit"
          >
            提交
          </a-button>
          <a-button
            v-if="['DRAFT', 'SUBMITTED'].includes(order.status ?? '')"
            danger
            v-privilege="'scm:purchase:cancel'"
            @click="cancelOpen = true"
          >
            取消采购单
          </a-button>
          <a-button
            v-if="order.status === 'PARTIALLY_RECEIVED'"
            danger
            v-privilege="'scm:purchase:short-close'"
            @click="shortCloseOpen = true"
          >
            少收关单
          </a-button>
        </a-space>

        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="供应商">
            {{ order.supplierName }}（{{ order.supplierCode }}）
          </a-descriptions-item>
          <a-descriptions-item label="采购员">{{ order.purchaserName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收货仓库">
            {{ order.warehouseName }}（{{ order.warehouseCode }}）
          </a-descriptions-item>
          <a-descriptions-item label="计划到货日期">{{ order.plannedArrivalDate || '—' }}</a-descriptions-item>
          <a-descriptions-item label="采购金额">
            <span class="num">{{ amount(order.totalAmount) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="收货进度">
            <span class="num">{{ progress(order.receivedProgress) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ order.submittedAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="取消时间">{{ order.cancelledAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="少收关单时间">{{ order.shortClosedAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="取消原因">{{ order.cancelReason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="少收关单原因">{{ order.shortCloseReason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ order.remark || '—' }}</a-descriptions-item>
        </a-descriptions>

        <a-table
          class="items"
          :data-source="order.items ?? []"
          :columns="itemColumns"
          row-key="id"
          :pagination="false"
          :scroll="{ x: 1300 }"
          size="small"
          bordered
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'purchaseUnit'">{{ record.purchaseUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'plannedQuantity'">
              <span class="num">{{ quantity(record.plannedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="num">{{ quantity(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'remainingQuantity'">
              <span class="num">{{ quantity(record.remainingQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
              <span class="num">{{ quantity(record.overReceiptQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'purchasePrice'">
              <span class="num">{{ amount(record.purchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lineAmount'">
              <span class="num">{{ amount(record.lineAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'allocationCount'">
              {{ (record.allocations ?? []).length }}
            </template>
          </template>
        </a-table>

        <a-divider orientation="left">需求分配明细</a-divider>
        <a-table
          :data-source="order.allocations ?? []"
          :columns="allocationColumns"
          row-key="allocationId"
          :pagination="false"
          size="small"
          bordered
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'salesOrderNo'">{{ record.salesOrderNo || '—' }}</template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="num">{{ quantity(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ record.demandUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'demandStatus'">
              <a-tag>{{ SCM_DEMAND_STATUS_ENUM[record.demandStatus ?? '']?.desc || '—' }}</a-tag>
            </template>
          </template>
        </a-table>

        <a-divider orientation="left">操作记录（最新在前）</a-divider>
        <a-button v-privilege="'scm:purchase:log:query'" @click="loadLogs">查看操作记录</a-button>
        <a-timeline class="logs">
          <a-timeline-item v-for="log in logs" :key="log.id">
            {{ log.createdAt }} · {{ log.operator }} ·
            {{ SCM_PURCHASE_OPERATION_ENUM[log.operationType]?.desc || log.operationType }}
            <a-collapse>
              <a-collapse-panel key="audit" header="变更前后">
                <pre>{{ JSON.stringify({ before: log.beforeData, after: log.afterData }, null, 2) }}</pre>
              </a-collapse-panel>
            </a-collapse>
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-spin>

    <a-modal
      :open="cancelOpen"
      title="取消采购单"
      :confirm-loading="saving"
      @ok="cancel"
      @cancel="cancelOpen = false"
    >
      <a-form-item label="取消原因" name="cancelReason" required>
        <a-input v-model:value="cancelReason" maxlength="500" />
      </a-form-item>
    </a-modal>

    <a-modal
      :open="shortCloseOpen"
      title="少收关单"
      :confirm-loading="saving"
      @ok="shortClose"
      @cancel="shortCloseOpen = false"
    >
      <a-form-item label="少收关单原因" name="shortCloseReason" required>
        <a-input v-model:value="shortCloseReason" maxlength="500" />
      </a-form-item>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Modal, message } from 'ant-design-vue';
import type { TableColumnsType } from 'ant-design-vue';
import { purchaseOrderApi } from '/@/api/business/scm/purchase-order-api';
import {
  SCM_DEMAND_STATUS_ENUM,
  SCM_PURCHASE_OPERATION_ENUM,
  SCM_PURCHASE_STATUS_ENUM,
} from '/@/constants/business/scm/purchase-const';
import type { Allocation, Id, LogRow, Order, OrderItem } from '../purchase-types';
import { amount, progress, quantity } from '../purchase-form-model';
import { purchaseError } from '../purchase-errors';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const order = ref<Order>();
const logs = ref<LogRow[]>([]);
const cancelOpen = ref(false);
const shortCloseOpen = ref(false);
const cancelReason = ref('');
const shortCloseReason = ref('');

const itemColumns: TableColumnsType<OrderItem> = [
  { title: '商品', dataIndex: 'productName', width: 150 },
  { title: '规格', dataIndex: 'skuName', width: 130 },
  { title: '采购单位', dataIndex: 'purchaseUnit', width: 100 },
  { title: '采购数量', dataIndex: 'plannedQuantity', align: 'right', width: 120 },
  { title: '已收数量', dataIndex: 'receivedQuantity', align: 'right', width: 120 },
  { title: '剩余可收', dataIndex: 'remainingQuantity', align: 'right', width: 120 },
  { title: '超收数量', dataIndex: 'overReceiptQuantity', align: 'right', width: 120 },
  { title: '采购单价', dataIndex: 'purchasePrice', align: 'right', width: 130 },
  { title: '金额', dataIndex: 'lineAmount', align: 'right', width: 130 },
  { title: '需求来源', dataIndex: 'allocationCount', align: 'center', width: 100 },
];

const allocationColumns: TableColumnsType<Allocation> = [
  { title: '来源销售单', dataIndex: 'salesOrderNo', width: 210 },
  { title: '分配数量', dataIndex: 'quantity', align: 'right', width: 130 },
  { title: '需求单位', dataIndex: 'demandUnit', width: 100 },
  { title: '需求状态', dataIndex: 'demandStatus', align: 'center', width: 120 },
];

async function open(id: Id) {
  visible.value = true;
  logs.value = [];
  order.value = { id } as Order;
  await load();
}

async function load() {
  if (!order.value?.id) {
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    order.value = (await purchaseOrderApi.detail(order.value.id)).data;
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    loading.value = false;
  }
}

async function loadLogs() {
  try {
    logs.value = (await purchaseOrderApi.logs(order.value!.id!)).data;
  } catch (e) {
    error.value = purchaseError(e);
  }
}

function submit() {
  Modal.confirm({
    title: '提交这张采购单？提交后不可再改行与分配。',
    onOk: async () => {
      try {
        await purchaseOrderApi.submit({ id: order.value!.id!, version: order.value!.version! });
        await load();
        emit('saved');
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

async function cancel() {
  if (!cancelReason.value.trim()) {
    message.error('请填写取消原因');
    return;
  }
  saving.value = true;
  try {
    await purchaseOrderApi.cancel({
      id: order.value!.id!,
      version: order.value!.version!,
      cancelReason: cancelReason.value,
    });
    cancelOpen.value = false;
    await load();
    emit('saved');
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

async function shortClose() {
  if (!shortCloseReason.value.trim()) {
    message.error('请填写少收关单原因');
    return;
  }
  saving.value = true;
  try {
    await purchaseOrderApi.shortClose({
      id: order.value!.id!,
      version: order.value!.version!,
      shortCloseReason: shortCloseReason.value,
    });
    shortCloseOpen.value = false;
    await load();
    emit('saved');
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>

<style scoped>
.actions,
.items,
.logs {
  margin: 16px 0;
}
.num {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
