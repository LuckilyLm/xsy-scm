<template>
  <a-drawer :open="visible" title="采购现场回填" :width="scmDrawerWidth('xl')" @close="close">
    <a-space class="scm-detail-actions">
      <a-button :loading="loading" @click="load(false)">刷新</a-button>
      <a-button @click="showOrder">查看采购单</a-button>
      <a-button v-privilege="'scm:purchase:receipt:add'" @click="receive">登记收货</a-button>
      <a-button v-if="createdReceiptId" v-privilege="'scm:purchase:receipt:confirm'" @click="confirmReceipt?.open(createdReceiptId)">
        核对并确认收货
      </a-button>
    </a-space>
    <a-alert
      type="info"
      show-icon
      message="以下为现场回填记录，尚未计入收货和库存。请核对数量及单价后，在正式收货单中登记；同一明细的多次回填不能直接相加。"
    />
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading" :pagination="false" :scroll="{x: 1060}">
      <template #bodyCell="{column, record}">
        <span v-if="column.dataIndex === 'unitPrice'" class="scm-money">{{ record.unitPrice ?? '—' }}</span>
        <span v-else-if="column.dataIndex === 'receivedQuantity'" class="scm-quantity">{{ record.receivedQuantity ?? '—' }}</span>
      </template>
    </a-table>
    <a-button v-if="more" :loading="loading" @click="load(true)">加载更早记录</a-button>
  </a-drawer>
  <PurchaseReceiptFormDrawer ref="receipt" @created="(id) => (createdReceiptId = id)" @saved="load(false)" />
  <PurchaseReceiptConfirm ref="confirmReceipt" @saved="load(false)" />
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {purchaseShareApi, type PurchaseShareFeedback} from '/@/api/business/scm/purchase-share-api';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {purchaseError} from '../purchase-errors';
import PurchaseReceiptFormDrawer from './purchase-receipt-form-drawer.vue';
import PurchaseReceiptConfirm from './purchase-receipt-confirm-modal.vue';
const emit = defineEmits<{detail: [id: Id]}>();
const visible = ref(false),
  loading = ref(false),
  more = ref(false),
  error = ref('');
const orderId = ref<Id>();
const rows = ref<PurchaseShareFeedback[]>([]);
const receipt = ref<InstanceType<typeof PurchaseReceiptFormDrawer>>();
const confirmReceipt = ref<InstanceType<typeof PurchaseReceiptConfirm>>();
const createdReceiptId = ref<Id>();
let generation = 0;
const columns = [
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '规格', dataIndex: 'skuName', width: 140},
  {title: '单位', dataIndex: 'purchaseUnit', width: 76, align: 'center' as const},
  {title: '回填单价', dataIndex: 'unitPrice', width: 140, align: 'right' as const},
  {title: '回填实收量', dataIndex: 'receivedQuantity', width: 128, align: 'right' as const},
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
  createdReceiptId.value = undefined;
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
function receive() {
  if (orderId.value != null) void receipt.value?.openForOrder(orderId.value);
}
defineExpose({open});
</script>
