<template>
  <a-drawer v-model:open="visible" :title="`订单支付 · ${order?.orderNo || ''}`" :width="scmDrawerWidth('m')"
            :closable="!saving" :mask-closable="!saving" :keyboard="!saving">
    <a-alert v-if="error" type="error" show-icon :message="error" class="payment-message"/>
    <a-alert v-if="created" type="success" show-icon
             :message="`${created.intentNo}：${statusText(created.status)}`" class="payment-message"/>
    <a-form v-if="order?.status === 'CONFIRMED'" v-privilege="'scm:payment:intent:create'" layout="vertical">
      <a-form-item label="支付方式">
        <a-radio-group v-model:value="method" :disabled="saving">
          <a-radio value="BALANCE">余额支付</a-radio><a-radio value="ONLINE">在线支付（模拟渠道）</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="本次支付金额" required>
        <a-input-number v-model:value="amount" string-mode :min="'0.0001'" :max="'99999999999999.9999'"
                        :precision="4" :disabled="saving" placeholder="输入本次金额"/>
      </a-form-item>
      <p v-if="method === 'BALANCE'">从订单结算主体的钱包扣款。签收形成应收后自动核销，超额单独显示，不自动退款。</p>
      <p v-else>当前仅接入开发模拟渠道，不会发起真实微信收款。</p>
      <a-button type="primary" html-type="button" :loading="saving" @click="submit">{{ method === 'BALANCE' ? '确认余额支付' : '发起模拟支付' }}</a-button>
    </a-form>
    <a-divider/>
    <a-button v-privilege="'scm:payment:transaction:query'" :loading="loading" @click="load(1)">查询支付记录</a-button>
    <a-table v-if="queried" class="payment-records" size="small" :columns="columns" :data-source="rows"
             row-key="id" :loading="loading" :scroll="{x: 680}" :pagination="false">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'method'">{{ record.method === 'BALANCE' ? '余额 / 内部结算' : '在线 / ' + providerText(record.provider) }}</template>
        <template v-else-if="column.dataIndex === 'status'">{{ statusText(record.status) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" v-privilege="'scm:payment:transaction:query'" @click="showDetail(record)">交易</a-button>
          <a-button v-if="record.method === 'BALANCE' && record.status === 'SUCCEEDED'" type="link"
                    v-privilege="'scm:balance:movement:query'" @click="movement?.open({sourceType: 'PAYMENT_INTENT', sourceId: record.id})">余额流水</a-button>
        </template>
      </template>
    </a-table>
    <a-pagination v-if="queried && total > 10" :current="pageNum" :page-size="10" :total="total" @change="load"/>
    <a-descriptions v-for="transaction in detail?.transactions || []" :key="transaction.id" bordered size="small" :column="1" class="payment-records">
      <a-descriptions-item :label="detail?.method === 'BALANCE' ? '内部结算编号' : '渠道交易号'">{{ transaction.providerTransactionNo }}</a-descriptions-item>
      <a-descriptions-item label="实际支付金额">{{ transaction.providerAmount || '等待支付结果' }}</a-descriptions-item>
      <a-descriptions-item label="支付状态">{{ statusText(transaction.status) }}</a-descriptions-item>
    </a-descriptions>
    <BalanceMovementDetail ref="movement"/>
  </a-drawer>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {paymentApi, type PaymentIntent, type PaymentCreate} from '/@/api/business/scm/payment-api';
import type {Order} from '../order-types';
import {financeError} from '../../finance/finance-errors';
import {isValidPositiveAmount} from '../../finance/finance-form-model';
import BalanceMovementDetail from '../../finance/balance-movement-detail.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
const visible = ref(false), saving = ref(false), loading = ref(false), error = ref(''), queried = ref(false);
const order = ref<Order>(), amount = ref(''), method = ref<'BALANCE' | 'ONLINE'>('BALANCE');
const created = ref<PaymentIntent>(), detail = ref<PaymentIntent>(), rows = ref<PaymentIntent[]>([]);
const pageNum = ref(1), total = ref(0), movement = ref<InstanceType<typeof BalanceMovementDetail>>();
const keys = new Map<string, string>();
let generation = 0;
const columns: TableColumnsType<PaymentIntent> = [
  {title: '支付单号', dataIndex: 'intentNo', width: 200}, {title: '方式 / 渠道', dataIndex: 'method', width: 170, align: 'center'},
  {title: '申请金额', dataIndex: 'amount', width: 120, align: 'right'},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'}, {title: '追溯', dataIndex: 'action', width: 150, align: 'center'},
];
function statusText(value: string) {
  return ({CREATED: '已创建', PENDING: '待支付', SUCCEEDED: '支付成功', FAILED: '支付失败', CLOSED: '已关闭'} as Record<string, string>)[value] || value;
}
function providerText(value: string) { return ({MOCK: '本地模拟', WECHAT: '微信支付', INTERNAL_BALANCE: '内部余额结算'} as Record<string, string>)[value] || value; }
function open(value: Order) {
  if (saving.value) return;
  generation++; order.value = value; visible.value = true; amount.value = ''; error.value = '';
  created.value = undefined; detail.value = undefined; rows.value = []; queried.value = false; loading.value = false;
}
async function submit() {
  if (saving.value || !order.value?.orderId || !order.value.customerId) return;
  if (!isValidPositiveAmount(amount.value)) { error.value = '请输入大于 0 的金额，最多 4 位小数。'; return; }
  const data: PaymentCreate = {customerId: order.value.customerId, sourceType: 'SALES_ORDER', sourceId: order.value.orderId,
    amount: amount.value, method: method.value, provider: method.value === 'BALANCE' ? 'INTERNAL_BALANCE' : 'MOCK'};
  const signature = JSON.stringify(data);
  const key = keys.get(signature) || crypto.randomUUID(); keys.set(signature, key);
  saving.value = true; error.value = ''; created.value = undefined;
  try {
    created.value = (await paymentApi.create(data, key)).data;
    keys.delete(signature); amount.value = '';
    if (queried.value) await load(1);
  } catch (cause) { error.value = financeError(cause); }
  finally { saving.value = false; }
}
async function load(page: number) {
  if (!order.value?.orderId) return;
  const current = ++generation;
  loading.value = true; error.value = ''; queried.value = true; detail.value = undefined;
  try {
    const response = await paymentApi.query(order.value.orderId, page);
    if (current === generation) { rows.value = response.data.list; total.value = response.data.total; pageNum.value = page; }
  } catch (cause) { if (current === generation) error.value = financeError(cause); }
  finally { if (current === generation) loading.value = false; }
}
async function showDetail(row: PaymentIntent) {
  const current = ++generation; loading.value = false; error.value = ''; detail.value = undefined;
  try { const response = await paymentApi.detail(row.id); if (current === generation) detail.value = response.data; }
  catch (cause) { if (current === generation) error.value = financeError(cause); }
}
defineExpose({open});
</script>
<style scoped>
.payment-message, .payment-records { margin-block: 16px; }
</style>
