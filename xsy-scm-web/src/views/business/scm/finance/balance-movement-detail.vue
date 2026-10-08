<template>
  <a-drawer v-model:open="visible" title="余额来源流水" :width="scmDrawerWidth('s')">
    <a-spin :spinning="loading">
      <a-empty v-if="!loading && !error && !rows.length" description="没有可查看的来源流水"/>
      <a-descriptions v-for="row in rows" :key="row.id" bordered size="small" :column="1">
        <a-descriptions-item label="流水号">{{ row.movementNo }}</a-descriptions-item>
        <a-descriptions-item label="业务客户">{{ row.customerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="钱包结算主体">{{ row.settlementCustomerName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="变动">{{ row.direction === 'DEBIT' ? '支出' : '收入' }} {{ moneyText(row.amount) }}</a-descriptions-item>
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
import {useScmErrorToast} from '../common/scm-error-toast';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
const visible = ref(false), loading = ref(false), error = useScmErrorToast();
const rows = ref<BalanceMovement[]>([]);
let generation = 0;
async function open(filter: {movementId?: Id; sourceType?: string; sourceId?: Id}) {
  const current = ++generation;
  visible.value = true; loading.value = true; error.value = ''; rows.value = [];
  try {
    const response = await paymentApi.movements(filter);
    if (current === generation) rows.value = response.data.list;
  } catch (cause) { if (current === generation) error.value = financeError(cause); }
  finally { if (current === generation) loading.value = false; }
}
defineExpose({open});
</script>
