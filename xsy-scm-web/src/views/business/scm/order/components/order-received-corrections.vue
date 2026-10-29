<template>
  <section>
    <div class="order-section-head">
      <div class="order-section-head__main">
        <h3 class="order-section-title">客户实收更正</h3>
        <span class="order-section-desc">只作记录；补偿需走正式流程</span>
      </div>
      <a-button v-if="canCorrect" v-privilege="'scm:order:update'" :disabled="loading" @click="open">记录实收更正</a-button>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error">
      <template #action><a-button size="small" @click="load">重试</a-button></template>
    </a-alert>
    <a-table
      :data-source="rows"
      :columns="columns"
      row-key="id"
      size="small"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
      class="correction-table"
    >
      <template #bodyCell="{column, record, text}">
        <span v-if="quantityKeys.has(String(column.dataIndex))" class="scm-quantity">{{ text }} {{ record.saleUnitSnapshot }}</span>
        <span v-else>{{ text ?? '—' }}</span>
      </template>
    </a-table>
    <a-modal
      :open="visible"
      title="记录客户实收更正"
      :confirm-loading="saving"
      :ok-button-props="{disabled: !candidate || !reason.trim()}"
      @ok="save"
      @cancel="visible = false"
    >
      <a-form layout="vertical">
        <a-form-item label="商品规格" required>
          <a-select v-model:value="itemId" :options="itemOptions" placeholder="选择订单明细" @change="loadCandidate" />
        </a-form-item>
        <a-alert v-if="candidateError" type="error" show-icon :message="candidateError" />
        <a-spin :spinning="candidateLoading">
          <template v-if="candidate">
            <p>
              已出库：{{ candidate.shippedQuantity }} {{ candidate.saleUnit }}；当前客户实收记录：{{ candidate.currentReceivedQuantity }}
              {{ candidate.saleUnit }}
            </p>
            <a-form-item label="更正后的客户实收数量" required>
              <a-input-number
                v-model:value="receivedQuantity"
                string-mode
                :precision="4"
                :min="'0'"
                :max="candidate.shippedQuantity"
                style="width: 100%"
              />
            </a-form-item>
          </template>
        </a-spin>
        <a-form-item label="更正原因" required>
          <a-textarea v-model:value="reason" :maxlength="500" :rows="3" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import {orderApi, type ReceivedCorrection, type ReceivedCorrectionCandidate} from '/@/api/business/scm/order-api';
import {scmColumnsWidth} from '../../common/scm-column';
import {hasPermission} from '../../common/scm-permission';
import {datetime} from '../../common/scm-display';
import type {Id, Order} from '../order-types';
import {orderError} from '../order-errors';

const props = defineProps<{order: Order}>();
const rows = ref<ReceivedCorrection[]>([]),
  loading = ref(false),
  error = ref('');
const visible = ref(false),
  saving = ref(false),
  candidateLoading = ref(false),
  candidateError = ref('');
const candidate = ref<ReceivedCorrectionCandidate>(),
  itemId = ref<Id>(),
  receivedQuantity = ref(''),
  reason = ref('');
const canCorrect = computed(() => props.order.status === 'CONFIRMED' && hasPermission('scm:order:update'));
const itemOptions = computed(() =>
  props.order.items
    .filter((item) => item.itemId != null)
    .map((item) => ({
      value: item.itemId!,
      label: `${item.productNameSnapshot ?? '商品'} · ${item.specNameSnapshot ?? '商品规格'}`,
    }))
);
const quantityKeys = new Set(['shippedQuantity', 'previousReceivedQuantity', 'receivedQuantity']);
const columns: TableColumnsType<ReceivedCorrection> = [
  {title: '记录时间', dataIndex: 'createdAt', width: 180, customRender: ({text}) => datetime(text)},
  {title: '商品名称', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 140},
  {title: '已出库', dataIndex: 'shippedQuantity', width: 150, align: 'left'},
  {title: '更正前', dataIndex: 'previousReceivedQuantity', width: 150, align: 'left'},
  {title: '更正后', dataIndex: 'receivedQuantity', width: 150, align: 'left'},
  {title: '原因', dataIndex: 'reason', width: 260, ellipsis: true},
  {title: '记录人', dataIndex: 'createdBy', width: 130},
];
let requestId = 0;
async function load(): Promise<void> {
  if (props.order.orderId == null) return;
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const result = await orderApi.receivedCorrections(props.order.orderId, {suppressGlobalErrorMessage: true});
    if (current === requestId) rows.value = result.data;
  } catch (cause) {
    if (current === requestId) error.value = orderError(cause);
  } finally {
    if (current === requestId) loading.value = false;
  }
}
function open(): void {
  ++candidateRequestId;
  itemId.value = undefined;
  candidate.value = undefined;
  candidateError.value = '';
  reason.value = '';
  receivedQuantity.value = '';
  visible.value = true;
}
let candidateRequestId = 0;
async function loadCandidate(): Promise<void> {
  if (props.order.orderId == null || itemId.value == null) return;
  const current = ++candidateRequestId;
  candidateLoading.value = true;
  candidateError.value = '';
  candidate.value = undefined;
  try {
    const result = await orderApi.receivedCorrectionCandidate(props.order.orderId, itemId.value, {suppressGlobalErrorMessage: true});
    if (current === candidateRequestId) {
      candidate.value = result.data;
      receivedQuantity.value = result.data.currentReceivedQuantity;
    }
  } catch (cause) {
    if (current === candidateRequestId) candidateError.value = orderError(cause);
  } finally {
    if (current === candidateRequestId) candidateLoading.value = false;
  }
}
async function save(): Promise<void> {
  if (saving.value || props.order.orderId == null || itemId.value == null || !candidate.value || !reason.value.trim()) return;
  saving.value = true;
  try {
    await orderApi.recordReceivedCorrection({
      orderId: props.order.orderId,
      orderItemId: itemId.value,
      expectedLastCorrectionId: candidate.value.lastCorrectionId,
      receivedQuantity: String(receivedQuantity.value),
      reason: reason.value.trim(),
    });
    visible.value = false;
    message.success('客户实收更正已记录');
    await load();
  } catch {
    // 命令失败由统一请求层提示；保留表单供修正或重试。
  } finally {
    saving.value = false;
  }
}
watch(
  () => props.order.orderId,
  () => {
    void load();
  },
  {immediate: true}
);
</script>

<style scoped>
/* 标题、说明和操作收在一行：说明不再占一条提示条，按钮也不再漂在表格上方 */
.order-section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 24px 0 12px;
}
.order-section-head__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}
.order-section-title {
  margin: 0;
}
.order-section-desc {
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}
.correction-table {
  margin-top: 10px;
}
</style>
