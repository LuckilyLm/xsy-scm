<template>
  <a-modal
    v-model:open="visible"
    title="采购成本更正"
    :width="1120"
    :footer="null"
    :closable="!saving"
    :mask-closable="!saving"
    @cancel="requestId++"
  >
    <p class="scm-note">
      按已确认的收货明细登记价差，更正计入当期应付与采购成本调整。原采购价、库存流水和历史报表保持原值；未归属客户的价差不自动分摊。
    </p>
    <a-alert v-if="error" :message="error" type="error" show-icon
      ><template #action><a-button @click="load">重试</a-button></template></a-alert
    >
    <a-table
      :columns="sourceColumns"
      :data-source="sources"
      row-key="receiptItemId"
      :loading="loading"
      size="small"
      :pagination="false"
      :scroll="{x: scmColumnsWidth(sourceColumns)}"
    >
      <template #bodyCell="{column, record}">
        <span v-if="column.dataIndex === 'quantity'" class="scm-quantity">{{ quantity(record.quantity) }}</span>
        <span v-else-if="column.dataIndex === 'previousPrice'" class="scm-money">{{ price(record.previousPrice) }}</span>
        <template v-else-if="column.dataIndex === 'action'"
          ><a-button type="link" size="small" @click="openEdit(record)">更正单价</a-button></template
        >
      </template>
    </a-table>
    <a-divider orientation="left">更正记录</a-divider>
    <a-table
      :columns="historyColumns"
      :data-source="history"
      row-key="id"
      size="small"
      :loading="loading"
      :pagination="{pageSize: 10}"
      :scroll="{x: scmColumnsWidth(historyColumns)}"
    >
      <template #bodyCell="{column, record}">
        <span v-if="['previousPrice', 'newPrice', 'amount'].includes(column.dataIndex)" class="scm-money">{{ price(record[column.dataIndex]) }}</span>
        <template v-else-if="column.dataIndex === 'direction'">{{ SCM_PURCHASE_COST_DIRECTION_ENUM[record.direction]?.desc || '—' }}</template>
        <template v-else-if="column.dataIndex === 'occurredAt'">{{ datetime(record.occurredAt) }}</template>
      </template>
    </a-table>
  </a-modal>
  <a-modal
    v-model:open="editOpen"
    title="登记采购价差"
    :confirm-loading="saving"
    :closable="!saving"
    :mask-closable="!saving"
    :cancel-button-props="{disabled: saving}"
    @ok="submit"
  >
    <p v-if="target" class="scm-note">
      {{ target.productNameSnapshot }}，实收 {{ quantity(target.quantity) }} {{ target.unitSnapshot }}，当前结算单价
      {{ price(target.previousPrice) }}。
    </p>
    <a-form ref="formRef" :model="form" layout="vertical" :disabled="saving">
      <a-form-item name="newPrice" label="更正后单价" :rules="[{required: true, message: '请填写更正后的单价'}]"
        ><a-input-number v-model:value="form.newPrice" string-mode :min="0" :precision="4" style="width: 100%"
      /></a-form-item>
      <a-form-item name="reason" label="更正原因" :rules="[{required: true, whitespace: true, message: '请填写更正原因'}]"
        ><a-textarea v-model:value="form.reason" :maxlength="500" :rows="3" show-count
      /></a-form-item>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {onBeforeUnmount, reactive, ref} from 'vue';
import {message, type FormInstance, type TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import type {Id, PurchaseCostAdjustment} from '../purchase-types';
import {SCM_PURCHASE_COST_DIRECTION_ENUM} from '/@/constants/business/scm/purchase-const';
import {fixed, price, quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import {scmColumnsWidth} from '../../common/scm-column';
import {datetime} from '../../common/scm-display';

const visible = ref(false),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  editOpen = ref(false);
const orderId = ref<Id>(),
  sources = ref<PurchaseCostAdjustment[]>([]),
  history = ref<PurchaseCostAdjustment[]>([]),
  target = ref<PurchaseCostAdjustment>();
const form = reactive({newPrice: '', reason: ''}),
  formRef = ref<FormInstance>();
let requestId = 0;
const sourceColumns: TableColumnsType<PurchaseCostAdjustment> = [
  {title: '收货单号', dataIndex: 'receiptNo', width: 190},
  {title: '商品名称', dataIndex: 'productNameSnapshot', width: 180},
  {title: '单位', dataIndex: 'unitSnapshot', width: 76, align: 'center'},
  {title: '实收数量', dataIndex: 'quantity', width: 120, align: 'right'},
  {title: '当前结算单价', dataIndex: 'previousPrice', width: 144, align: 'right'},
  {title: '操作', dataIndex: 'action', width: 100, align: 'center', fixed: 'right'},
];
const historyColumns: TableColumnsType<PurchaseCostAdjustment> = [
  {title: '更正单号', dataIndex: 'adjustmentNo', width: 190},
  {title: '商品名称', dataIndex: 'productNameSnapshot', width: 180},
  {title: '原单价', dataIndex: 'previousPrice', width: 132, align: 'right'},
  {title: '更正单价', dataIndex: 'newPrice', width: 132, align: 'right'},
  {title: '方向', dataIndex: 'direction', width: 76, align: 'center'},
  {title: '价差金额', dataIndex: 'amount', width: 144, align: 'right'},
  {title: '更正时间', dataIndex: 'occurredAt', width: 180},
  {title: '原因', dataIndex: 'reason', width: 220},
];
function open(id: Id) {
  orderId.value = id;
  sources.value = [];
  history.value = [];
  visible.value = true;
  void load();
}
async function load() {
  if (orderId.value == null) return;
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const [options, records] = await Promise.all([
      purchaseOrderApi.costAdjustmentOptions(orderId.value, {suppressGlobalErrorMessage: true}),
      purchaseOrderApi.costAdjustmentHistory(orderId.value, {suppressGlobalErrorMessage: true}),
    ]);
    if (id !== requestId) return;
    sources.value = options.data;
    history.value = records.data;
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
      sources.value = [];
      history.value = [];
    }
  } finally {
    if (id === requestId) loading.value = false;
  }
}
function openEdit(row: PurchaseCostAdjustment) {
  target.value = row;
  form.newPrice = row.previousPrice;
  form.reason = '';
  formRef.value?.clearValidate();
  editOpen.value = true;
}
async function submit() {
  if (!target.value || saving.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  if (!(Number(form.newPrice) >= 0) || fixed(form.newPrice) === fixed(target.value.previousPrice)) {
    message.warning('请填写与当前结算单价不同的有效价格');
    return;
  }
  saving.value = true;
  try {
    await purchaseOrderApi.costAdjustmentAdd({
      receiptItemId: target.value.receiptItemId,
      previousAdjustmentId: target.value.previousAdjustmentId,
      expectedPrice: fixed(target.value.previousPrice),
      newPrice: fixed(form.newPrice),
      reason: form.reason.trim(),
    });
    message.success('采购价差已登记，应付已同步调整');
    editOpen.value = false;
    await load();
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
