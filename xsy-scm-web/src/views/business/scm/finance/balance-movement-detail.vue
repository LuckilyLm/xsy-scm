<template>
  <a-drawer v-model:open="visible" title="余额来源流水" :width="scmDrawerWidth('s')">
    <a-alert v-if="error" type="error" show-icon :message="error">
      <template #action>
        <a-button size="small" :loading="loading" @click="retry">重试</a-button>
      </template>
    </a-alert>
    <a-spin :spinning="loading">
      <a-empty v-if="!loading && !error && !rows.length" description="没有可查看的来源流水" />
      <a-descriptions v-for="row in rows" :key="row.id" bordered size="small" :column="1">
        <a-descriptions-item label="流水号">{{ row.movementNo }}</a-descriptions-item>
        <a-descriptions-item label="业务客户">{{ row.customerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="钱包结算主体">{{ row.settlementCustomerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变动">
          {{ row.direction === 'DEBIT' ? '支出' : '收入' }}
          <span class="scm-money">{{ moneyText(row.amount) }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="发生时间">{{ dateTimeText(row.occurredAt) }}</a-descriptions-item>
        <a-descriptions-item label="原因">{{ row.reason || '—' }}</a-descriptions-item>
      </a-descriptions>
    </a-spin>
  </a-drawer>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {paymentApi, type BalanceMovement} from '/@/api/business/scm/payment-api';
import type {Id} from '../order/order-types';
import {financeError} from './finance-errors';
import {moneyText, dateTimeText} from './finance-form-model';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
const visible = ref(false),
  loading = ref(false),
  error = ref('');
const rows = ref<BalanceMovement[]>([]);
let generation = 0;
let lastFilter: {movementId?: Id; sourceType?: string; sourceId?: Id} = {};
async function open(filter: {movementId?: Id; sourceType?: string; sourceId?: Id}) {
  lastFilter = filter;
  const current = ++generation;
  visible.value = true;
  loading.value = true;
  error.value = '';
  rows.value = [];
  try {
    // 加载失败由弹窗内 Alert 提示，不再弹全局 toast
    const response = await paymentApi.movements(filter, {suppressGlobalErrorMessage: true});
    if (current === generation) rows.value = response.data.list;
  } catch (cause) {
    if (current === generation) error.value = financeError(cause);
  } finally {
    if (current === generation) loading.value = false;
  }
}
function retry() {
  void open(lastFilter);
}
defineExpose({open});
</script>
