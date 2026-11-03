<template>
  <a-modal
    v-model:open="visible"
    title="确认采购"
    :width="960"
    :confirm-loading="saving"
    :closable="!saving"
    :mask-closable="!saving"
    :cancel-button-props="{disabled: saving}"
    :ok-button-props="{disabled: loading || !!error || !acknowledged || !order || hasPendingPrice || priceChangeForbidden}"
    @ok="confirm"
    @cancel="requestId++"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon
      ><template #action><a-button @click="load">重试</a-button></template></a-alert
    >
    <p class="scm-note">核对实际采购数量与单价后即可生成供应商应付。未采购的商品填写0，至少一项实际数量大于0。</p>
    <a-alert v-if="pendingPriceBlocked" type="warning" show-icon message="存在待定价或未回填的商品，需要补价或改价权限后才能确认。" />
    <a-alert v-else-if="hasPendingPrice" type="warning" show-icon message="请补齐本次确认单价；采购价格未经核对不能确认应付。" />
    <a-alert v-if="priceChangeForbidden" type="warning" show-icon message="采用回填报价需要补价或改价权限，请由有权限的人员核对。" />
    <p v-if="fromFeedback" class="scm-note">已带入各商品最新一次现场回填，不累加多次回填；缺失的数量和报价请核对后补齐。</p>
    <a-table
      bordered
      :data-source="lines"
      :columns="columns"
      row-key="id"
      size="small"
      :loading="loading"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'purchasePrice'"
          ><span class="scm-money">{{ record.pricePending ? '待定价' : price(record.purchasePrice) }}</span></template
        >
        <template v-else-if="column.dataIndex === 'plannedQuantity'"
          ><span class="scm-quantity">{{ quantity(record.plannedQuantity) }}</span></template
        >
        <template v-else-if="column.dataIndex === 'confirmedPrice'">
          <a-input-number
            v-model:value="record.confirmedPrice"
            string-mode
            :precision="4"
            :min="0"
            :disabled="saving || !canChangePrice(record)"
            style="width: 100%"
          />
        </template>
        <template v-else-if="column.dataIndex === 'feedbackAt'">{{
          record.feedbackAt ? dayjs(record.feedbackAt).format('YYYY-MM-DD HH:mm') : '—'
        }}</template>
        <template v-else-if="column.dataIndex === 'confirmedQuantity'"
          ><a-input-number
            v-model:value="record.confirmedQuantity"
            string-mode
            :precision="4"
            :min="0"
            :disabled="saving"
            style="width: 100%"
            @blur="
              record.confirmedQuantity != null && record.confirmedQuantity !== '' && (record.confirmedQuantity = fixed(record.confirmedQuantity))
            "
        /></template>
      </template>
    </a-table>
    <a-checkbox v-model:checked="acknowledged" :disabled="loading || saving || hasPendingPrice" class="confirmation-check"
      >已核对实际数量和采购单价，确认登记应付</a-checkbox
    >
  </a-modal>
</template>
<script setup lang="ts">
import {computed, onBeforeUnmount, ref} from 'vue';
import dayjs from 'dayjs';
import {hasPermission} from '../../common/scm-permission';
import {message, type TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import type {Id, Order, OrderItem} from '../purchase-types';
import {fixed, price, quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import {scmColumnsWidth} from '../../common/scm-column';

type ConfirmLine = OrderItem & {confirmedQuantity: string; confirmedPrice?: string | null; feedbackId?: Id; feedbackAt?: string};
const fromFeedback = ref(false);
const emit = defineEmits<{saved: []}>();
const visible = ref(false),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  acknowledged = ref(false);
const order = ref<Order>(),
  orderId = ref<Id>(),
  lines = ref<ConfirmLine[]>([]);
let requestId = 0;
const hasPendingPrice = computed(() => lines.value.some((line) => line.confirmedPrice == null || line.confirmedPrice === ''));
function canChangePrice(line: ConfirmLine) {
  return hasPermission('scm:purchase:price:adjust') || (line.pricePending && hasPermission('scm:purchase:price:fill'));
}
const priceChangeForbidden = computed(() =>
  lines.value.some(
    (line) =>
      !canChangePrice(line) && line.confirmedPrice != null && (line.purchasePrice == null || fixed(line.confirmedPrice) !== fixed(line.purchasePrice))
  )
);
/** 待定价行没有可回退的原价，又无权补价/改价时，输入框是禁用的，要给出权限提示而不是让用户反复补空。 */
const pendingPriceBlocked = computed(() =>
  lines.value.some((line) => (line.confirmedPrice == null || line.confirmedPrice === '') && !canChangePrice(line))
);
const columns: TableColumnsType<ConfirmLine> = [
  {title: '商品名称', dataIndex: 'productName', width: 200},
  {title: '规格', dataIndex: 'skuName', width: 140},
  {title: '单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '计划采购数量', dataIndex: 'plannedQuantity', width: 120},
  {title: '原采购单价', dataIndex: 'purchasePrice', width: 132},
  {title: '本次确认单价', dataIndex: 'confirmedPrice', width: 144},
  {title: '回填时间', dataIndex: 'feedbackAt', width: 176},
  {title: '实际采购数量', dataIndex: 'confirmedQuantity', width: 160},
];
function open(id: Id, useFeedback = false) {
  fromFeedback.value = useFeedback;
  orderId.value = id;
  visible.value = true;
  acknowledged.value = false;
  order.value = undefined;
  lines.value = [];
  void load();
}
async function load() {
  if (orderId.value == null) return;
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  acknowledged.value = false;
  try {
    const response = await purchaseOrderApi.detail(orderId.value, {suppressGlobalErrorMessage: true});
    if (id !== requestId) return;
    if (response.data.status !== 'SUBMITTED' || response.data.accountId != null) {
      error.value = '该采购单已确认、已有收货或已关闭，请刷新后查看。';
      return;
    }
    order.value = response.data;
    const feedback = fromFeedback.value ? (await purchaseOrderApi.confirmationFeedback(orderId.value)).data ?? [] : [];
    if (id !== requestId) return;
    const byItem = new Map(feedback.map((row) => [String(row.purchaseOrderItemId), row]));
    lines.value = (response.data.items ?? []).map((line) => {
      const row = byItem.get(String(line.id));
      return {
        ...line,
        confirmedQuantity: fromFeedback.value ? row?.receivedQuantity ?? '' : fixed(line.plannedQuantity),
        // 回填没给单价时沿用采购单原价；只有待定价行才留空等有权限的人补价。
        confirmedPrice: fromFeedback.value ? row?.unitPrice ?? line.purchasePrice ?? null : line.purchasePrice,
        feedbackId: row?.id,
        feedbackAt: row?.submittedAt,
      };
    });
  } catch (e) {
    if (id === requestId) error.value = purchaseError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}
async function confirm() {
  if (saving.value || !order.value?.id || !acknowledged.value || hasPendingPrice.value || priceChangeForbidden.value) return;
  if (
    !lines.value.length ||
    lines.value.some(
      (line) =>
        !line.id ||
        line.version == null ||
        !line.purchaseUnit ||
        line.confirmedQuantity == null ||
        line.confirmedQuantity === '' ||
        !Number.isFinite(Number(line.confirmedQuantity)) ||
        Number(line.confirmedQuantity) < 0
    ) ||
    !lines.value.some((line) => Number(line.confirmedQuantity) > 0)
  ) {
    message.warning('请填写非负的实际采购数量，至少一项大于0');
    return;
  }
  saving.value = true;
  try {
    await purchaseOrderApi.financeConfirm({
      purchaseOrderId: order.value.id,
      version: order.value.version!,
      items: lines.value.map((line) => ({
        orderItemId: line.id!,
        version: line.version!,
        quantity: fixed(line.confirmedQuantity),
        expectedPrice: line.purchasePrice == null ? null : fixed(line.purchasePrice),
        confirmedPrice: fixed(line.confirmedPrice),
        feedbackId: line.feedbackId,
        purchaseUnit: line.purchaseUnit!,
      })),
    });
    message.success('采购已确认');
    visible.value = false;
    emit('saved');
  } catch {
    /* 全局提示 */
  } finally {
    saving.value = false;
  }
}
onBeforeUnmount(() => {
  requestId++;
});
defineExpose({open});
</script>
<style scoped>
.confirmation-check {
  margin-top: 16px;
}
</style>
