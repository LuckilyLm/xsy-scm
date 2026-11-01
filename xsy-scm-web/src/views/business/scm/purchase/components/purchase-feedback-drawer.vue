<template>
  <a-drawer :open="visible" title="采购现场回填" :width="scmDrawerWidth('xl')" @close="close">
    <a-space class="scm-detail-actions">
      <a-button :loading="loading" @click="load(false)">刷新</a-button>
      <a-button @click="showOrder">查看采购单</a-button>
      <a-button v-privilege="'scm:purchase:finance-confirm'" @click="confirmPurchase">确认采购</a-button>
    </a-space>
    <p class="scm-note">
      以下为现场回填记录，尚未成为实际采购。请核对数量和单价后点击「确认采购」，系统会保存实采数量并生成应付；回填不会自动改写采购单。
    </p>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading" :pagination="false" :scroll="{x: 1060}">
      <template #bodyCell="{column, record}">
        <span v-if="column.dataIndex === 'unitPrice'" class="scm-money">{{ formatPrice(record.unitPrice, 'purchase') }}</span>
        <span v-else-if="column.dataIndex === 'receivedQuantity'" class="scm-quantity">{{ record.receivedQuantity ?? '—' }}</span>
      </template>
    </a-table>
    <a-button v-if="more" :loading="loading" @click="load(true)">加载更早记录</a-button>
  </a-drawer>
  <PurchaseFinanceConfirmModal ref="financeConfirm" @saved="load(false)" />
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {formatPrice} from '/@/utils/scm-amount';
import {purchaseShareApi, type PurchaseShareFeedback} from '/@/api/business/scm/purchase-share-api';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {purchaseError} from '../purchase-errors';
import PurchaseFinanceConfirmModal from './purchase-finance-confirm-modal.vue';
const emit = defineEmits<{detail: [id: Id]}>();
const visible = ref(false),
  loading = ref(false),
  more = ref(false),
  error = ref('');
const orderId = ref<Id>();
const rows = ref<PurchaseShareFeedback[]>([]);
const financeConfirm = ref<InstanceType<typeof PurchaseFinanceConfirmModal>>();
let generation = 0;
const columns = [
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '规格', dataIndex: 'skuName', width: 140},
  {title: '单位', dataIndex: 'purchaseUnit', width: 76},
  {title: '回填单价', dataIndex: 'unitPrice', width: 140},
  {title: '回填实收量', dataIndex: 'receivedQuantity', width: 128},
  {title: '回填时间', dataIndex: 'submittedAt', width: 180},
  {title: '备注', dataIndex: 'remark', width: 220},
];
async function load(append = false) {
  if (orderId.value == null) return;
  const current = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const result = await purchaseShareApi.feedback(orderId.value, append ? rows.value.at(-1)?.id : undefined);
    if (current !== generation) return;
    const page = result.data ?? [];
    rows.value = append ? rows.value.concat(page) : page;
    more.value = page.length === 100;
  } catch (cause) {
    if (current === generation) error.value = purchaseError(cause);
  } finally {
    if (current === generation) loading.value = false;
  }
}
function open(id: Id) {
  orderId.value = id;
  rows.value = [];
  more.value = false;
  visible.value = true;
  void load();
}
function close() {
  ++generation;
  loading.value = false;
  visible.value = false;
}
function showOrder() {
  if (orderId.value != null) emit('detail', orderId.value);
}
function confirmPurchase() {
  if (orderId.value != null) financeConfirm.value?.open(orderId.value);
}
defineExpose({open});
</script>
