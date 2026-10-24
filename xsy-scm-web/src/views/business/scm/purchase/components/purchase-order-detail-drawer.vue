<!-- 采购单详情抽屉。分配明细由后端展开好；操作记录最新在前。 -->
<template>
  <a-drawer :open="visible" :title="order?.orderNo || '采购单详情'" :width="scmDrawerWidth('xl')" @close="visible = false">
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button @click="load">重试</a-button>
      </template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="order">
        <div class="actions scm-detail-actions">
          <a-tag :color="STATUS_COLOR[order.status ?? '']">
            {{ SCM_PURCHASE_STATUS_ENUM[order.status ?? '']?.desc }}
          </a-tag>
          <template v-if="order.status === 'DRAFT'">
            <a-button type="primary" :loading="submitting" v-privilege="'scm:purchase:submit'" @click="submit"> 提交 </a-button>
          </template>
          <template v-if="['DRAFT', 'SUBMITTED'].includes(order.status ?? '')">
            <a-button danger v-privilege="'scm:purchase:cancel'" @click="cancelOpen = true"> 取消采购单 </a-button>
          </template>
          <template v-if="order.status === 'PARTIALLY_RECEIVED'">
            <a-button danger v-privilege="'scm:purchase:short-close'" @click="shortCloseOpen = true"> 少收关单 </a-button>
          </template>
        </div>

        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="供应商">{{ order.supplierName }}</a-descriptions-item>
          <a-descriptions-item label="采购员">{{ order.purchaserName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收货仓库">{{ order.warehouseName }}</a-descriptions-item>
          <a-descriptions-item label="计划到货日期">{{ order.plannedArrivalDate || '—' }}</a-descriptions-item>
          <a-descriptions-item label="采购金额">
            <span class="scm-money">{{ amount(order.totalAmount) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="收货进度">
            <span class="scm-quantity">{{ progress(order.receivedProgress) }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ datetime(order.submittedAt) }}</a-descriptions-item>
          <a-descriptions-item label="取消时间">{{ datetime(order.cancelledAt) }}</a-descriptions-item>
          <a-descriptions-item label="少收关单时间">{{ datetime(order.shortClosedAt) }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ datetime(order.createdAt) }}</a-descriptions-item>
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
          :scroll="{x: scmColumnsWidth(itemColumns)}"
          size="small"
          bordered
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'purchaseUnit'">{{ record.purchaseUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'plannedQuantity'">
              <span class="scm-quantity">{{ quantity(record.plannedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'receivedQuantity'">
              <span class="scm-quantity">{{ quantity(record.receivedQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'remainingQuantity'">
              <span class="scm-quantity">{{ quantity(record.remainingQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'overReceiptQuantity'">
              <span class="scm-quantity">{{ quantity(record.overReceiptQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'purchasePrice'">
              <span class="scm-money">{{ price(record.purchasePrice) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'lineAmount'">
              <span class="scm-money">{{ amount(record.lineAmount) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'allocationCount'">
              {{ (record.allocations ?? []).length }}
            </template>
          </template>
        </a-table>

        <a-divider orientation="left">需求分配明细</a-divider>
        <a-table :data-source="order.allocations ?? []" :columns="allocationColumns" row-key="allocationId" :pagination="false" size="small" bordered>
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'salesOrderNo'">{{ record.salesOrderNo || '—' }}</template>
            <template v-else-if="column.dataIndex === 'quantity'">
              <span class="scm-quantity">{{ quantity(record.quantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ record.demandUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'demandStatus'">
              <a-tag>{{ SCM_DEMAND_STATUS_ENUM[record.demandStatus ?? '']?.desc || '—' }}</a-tag>
            </template>
          </template>
        </a-table>

        <a-divider orientation="left">操作记录（最新在前）</a-divider>
        <a-button class="logs-trigger" :loading="logsLoading" v-privilege="'scm:purchase:log:query'" @click="loadLogs">
          {{ logs.length ? '重新加载操作记录' : '查看操作记录' }}
        </a-button>
        <a-empty v-if="!logs.length && logsLoaded" description="暂无操作记录" />
        <a-timeline v-else class="logs">
          <a-timeline-item v-for="log in logs" :key="log.id" :color="logColor(log.operationType)">
            <div class="log-head">
              <span class="log-time">{{ datetime(log.createdAt) }}</span>
              <a-tag :color="logColor(log.operationType)">
                {{ SCM_PURCHASE_OPERATION_ENUM[log.operationType]?.desc || log.operationType }}
              </a-tag>
              <span class="log-operator">{{ log.operator || '—' }}</span>
            </div>
            <div v-if="log.reason" class="log-reason">原因：{{ log.reason }}</div>
            <!-- 只列出变化的字段，不直接展示完整 JSON -->
            <a-collapse class="log-audit" ghost>
              <a-collapse-panel key="audit" header="变更前后">
                <ScmDiffTable :before="log.beforeData" :after="log.afterData" :scope="log.operationType" />
              </a-collapse-panel>
            </a-collapse>
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-spin>

    <a-modal :open="cancelOpen" title="取消采购单" :confirm-loading="saving" @ok="cancel" @cancel="cancelOpen = false">
      <a-form-item label="取消原因" name="cancelReason" required>
        <a-input v-model:value="cancelReason" maxlength="500" />
      </a-form-item>
    </a-modal>

    <a-modal :open="shortCloseOpen" title="少收关单" :confirm-loading="saving" @ok="shortClose" @cancel="shortCloseOpen = false">
      <a-form-item label="少收关单原因" name="shortCloseReason" required>
        <a-input v-model:value="shortCloseReason" maxlength="500" />
      </a-form-item>
    </a-modal>
  </a-drawer>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {ref} from 'vue';
import {Modal, message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import {SCM_DEMAND_STATUS_ENUM, SCM_PURCHASE_OPERATION_ENUM, SCM_PURCHASE_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import type {Allocation, Id, LogRow, Order, OrderItem} from '../purchase-types';
import {amount, price, progress, quantity} from '../purchase-form-model';
import {datetime} from '../../common/scm-display';
import {purchaseError} from '../purchase-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const emit = defineEmits<{saved: []}>();

/** 状态标签配色。共享枚举只放 value/desc，颜色留在页面里。 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  SUBMITTED: 'blue',
  PARTIALLY_RECEIVED: 'orange',
  RECEIVED: 'green',
  SHORT_CLOSED: 'purple',
  CANCELLED: 'red',
};

/** 操作类型配色：终态动作（取消）用红，推进类动作用蓝，其余中性。 */
const OPERATION_COLOR: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  SUBMIT: 'blue',
  CANCEL: 'red',
  SHORT_CLOSE: 'purple',
};

const visible = ref(false);
const loading = ref(false);
/** 提交单独用 loading，避免和刷新互相影响。 */
const submitting = ref(false);
const logsLoading = ref(false);
/** 区分“还没查”和“查过但没记录”。 */
const logsLoaded = ref(false);
const saving = ref(false);
const error = ref('');
const order = ref<Order>();
const logs = ref<LogRow[]>([]);
const cancelOpen = ref(false);
const shortCloseOpen = ref(false);
const cancelReason = ref('');
const shortCloseReason = ref('');

function logColor(operationType: string | undefined): string {
  return OPERATION_COLOR[operationType ?? ''] ?? 'gray';
}

const itemColumns: TableColumnsType<OrderItem> = [
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '采购单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '采购数量', dataIndex: 'plannedQuantity', width: 120},
  {title: '已收数量', dataIndex: 'receivedQuantity', width: 120},
  {title: '剩余可收', dataIndex: 'remainingQuantity', width: 120},
  {title: '超收数量', dataIndex: 'overReceiptQuantity', width: 120},
  {title: '采购单价', dataIndex: 'purchasePrice', width: 130},
  {title: '金额', dataIndex: 'lineAmount', width: 130},
  {title: '需求来源', dataIndex: 'allocationCount', width: 92},
];

const allocationColumns: TableColumnsType<Allocation> = [
  {title: '来源销售单', dataIndex: 'salesOrderNo', width: 210},
  {title: '分配数量', dataIndex: 'quantity', width: 130},
  {title: '需求单位', dataIndex: 'demandUnit', width: 100},
  {title: '需求状态', dataIndex: 'demandStatus', align: 'center', width: 96},
];

let requestId = 0;

async function open(id: Id) {
  const current = ++requestId;
  visible.value = true;
  logs.value = [];
  logsLoaded.value = false;
  order.value = {id} as Order;
  await load(current);
}

async function load(request = ++requestId) {
  if (!order.value?.id) {
    return;
  }
  const current = request;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由抽屉内的 Alert 提示并可重试
    const detail = (await purchaseOrderApi.detail(order.value.id, {suppressGlobalErrorMessage: true})).data;
    if (current !== requestId) return;
    order.value = detail;
  } catch (e) {
    if (current === requestId) error.value = purchaseError(e);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function loadLogs() {
  logsLoading.value = true;
  error.value = '';
  try {
    logs.value = (await purchaseOrderApi.logs(order.value!.id!, {suppressGlobalErrorMessage: true})).data;
    logsLoaded.value = true;
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    logsLoading.value = false;
  }
}

function submit() {
  Modal.confirm({
    title: '提交这张采购单？提交后不可再改行与分配。',
    onOk: async () => {
      submitting.value = true;
      try {
        await purchaseOrderApi.submit({id: order.value!.id!, version: order.value!.version!});
        await load();
        emit('saved');
      } finally {
        // 异常继续抛出，让确认框保持打开；失败原因由全局提示给出
        submitting.value = false;
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
  } catch {
    // 操作失败只走全局 toast
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
  } catch {
    // 操作失败只走全局 toast
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>

<style scoped>
.actions,
.items,
.logs {
  margin: 16px 0;
}

/* 动作条：状态标签与动作按钮对齐，并在窄屏下自然换行。 */
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.logs-trigger {
  margin-bottom: 12px;
}

/* 一条日志的抬头：时间 / 操作类型 / 操作人，等宽时间便于纵向比对。 */
.log-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.log-time {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.65);
}

.log-operator {
  color: rgba(0, 0, 0, 0.45);
}

.log-reason {
  margin-top: 4px;
  color: rgba(0, 0, 0, 0.65);
}

/* 折叠面板去掉内边距，让差异表贴边，视觉上归属于这条日志。 */
.log-audit {
  margin-top: 4px;
}

.log-audit :deep(.ant-collapse-header) {
  padding: 4px 0 !important;
}

.log-audit :deep(.ant-collapse-content-box) {
  padding: 8px 0 !important;
}
</style>
