<template>
  <a-modal
    v-model:open="visible"
    title="确认采购"
    :width="960"
    :confirm-loading="saving"
    :closable="!saving"
    :mask-closable="!saving"
    :cancel-button-props="{disabled: saving}"
    :ok-button-props="{disabled: loading || !!error || !acknowledged || !order || hasPendingPrice}"
    @ok="confirm"
    @cancel="requestId++"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon
      ><template #action><a-button @click="load">重试</a-button></template></a-alert
    >
    <p class="scm-note">核对实际采购数量与单价后即可生成供应商应付。未采购的商品填写0，至少一项实际数量大于0。</p>
    <a-alert v-if="hasPendingPrice" type="warning" show-icon message="采购明细尚有待定价项，请返回采购单补价后重新打开。" />
    <a-table
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
        <template v-else-if="column.dataIndex === 'confirmedQuantity'"
          ><a-input-number
            v-model:value="record.confirmedQuantity"
            string-mode
            :precision="4"
            :min="0"
            :disabled="saving"
            style="width: 100%"
            @blur="record.confirmedQuantity = fixed(record.confirmedQuantity)"
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
import {message, type TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import type {Id, Order, OrderItem} from '../purchase-types';
import {fixed, price, quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import {scmColumnsWidth} from '../../common/scm-column';

type ConfirmLine = OrderItem & {confirmedQuantity: string};
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
const hasPendingPrice = computed(() => lines.value.some((line) => line.pricePending || line.purchasePrice == null));
const columns: TableColumnsType<ConfirmLine> = [
  {title: '商品名称', dataIndex: 'productName', width: 200},
  {title: '规格', dataIndex: 'skuName', width: 140},
  {title: '单位', dataIndex: 'purchaseUnit', width: 76, align: 'center'},
  {title: '计划采购数量', dataIndex: 'plannedQuantity', width: 120, align: 'right'},
  {title: '采购单价', dataIndex: 'purchasePrice', width: 132, align: 'right'},
  {title: '实际采购数量', dataIndex: 'confirmedQuantity', width: 160, align: 'right'},
];
function open(id: Id) {
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
    lines.value = (response.data.items ?? []).map((line) => ({...line, confirmedQuantity: fixed(line.plannedQuantity)}));
  } catch (e) {
    if (id === requestId) error.value = purchaseError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}
async function confirm() {
  if (saving.value || !order.value?.id || !acknowledged.value || hasPendingPrice.value) return;
  if (
    !lines.value.length ||
    lines.value.some(
      (line) =>
        !line.id ||
        line.version == null ||
        !line.purchaseUnit ||
        line.confirmedQuantity == null ||
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
        expectedPrice: fixed(line.purchasePrice),
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
