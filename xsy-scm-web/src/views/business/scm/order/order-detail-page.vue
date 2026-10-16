<template>
  <div class="order-detail-page">
    <div class="order-detail-header">
      <div>
        <a-button type="link" class="order-back" @click="router.push('/order/order-list')">返回订单列表</a-button>
        <h2>{{ order?.orderNo || '销售订单详情' }}</h2>
      </div>
      <a-space>
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button v-if="order && canOperate" type="primary" @click="operation?.open(order.orderId!)">订单操作</a-button>
      </a-space>
    </div>

    <a-alert v-if="error" :message="error" type="error" show-icon class="order-detail-error">
      <template #action><a-button @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="order">
        <a-card size="small" :bordered="false">
          <a-tag>{{ SCM_ORDER_STATUS_ENUM[order.status ?? '']?.desc ?? order.status }}</a-tag>
          <a-descriptions class="order-detail-overview" size="small" bordered :column="{xs: 1, sm: 2, lg: 3}">
            <a-descriptions-item label="客户">{{ order.customerNameSnapshot || '—' }}</a-descriptions-item>
            <a-descriptions-item label="客户编码">{{ order.customerCodeSnapshot || '—' }}</a-descriptions-item>
            <a-descriptions-item label="订单来源">
              {{ SCM_ORDER_SOURCE_ENUM[order.orderSource]?.desc ?? order.orderSource }}
            </a-descriptions-item>
            <a-descriptions-item label="下单金额">{{ amount(order.orderedTotalAmount, true) }}</a-descriptions-item>
            <a-descriptions-item label="结算金额">{{ amount(order.settlementTotalAmount) }}</a-descriptions-item>
            <a-descriptions-item label="结算约定">{{ settlementLabel(order.settlementMethod) }}</a-descriptions-item>
            <a-descriptions-item label="确认时间">{{ datetime(order.confirmedAt) }}</a-descriptions-item>
            <a-descriptions-item label="期望配送时间">{{ datetime(order.expectDeliveryTime) }}</a-descriptions-item>
            <a-descriptions-item label="收货人">
              {{ order.address?.receiverName || '—' }} / {{ order.address?.receiverPhone || '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="收货地址" :span="3">{{ order.address?.address || '—' }}</a-descriptions-item>
          </a-descriptions>

          <h3 class="order-section-title">商品明细</h3>
          <a-table :data-source="order.items ?? []" :columns="itemColumns" row-key="itemId"
                   :pagination="false" size="small" bordered :scroll="{x: 980}">
            <template #bodyCell="{record, column}">
              <template v-if="column.dataIndex === 'draftUnitPrice'">
                {{ amount(record.draftUnitPrice, true) }}
              </template>
              <template v-else-if="column.dataIndex === 'lockedUnitPrice'">
                {{ amount(record.lockedUnitPrice) }}
              </template>
              <template v-else-if="column.dataIndex === 'orderedLineAmount'">
                {{ amount(record.orderedLineAmount, true) }}
              </template>
              <template v-else-if="column.dataIndex === 'settlementLineAmount'">
                {{ amount(record.settlementLineAmount) }}
              </template>
            </template>
          </a-table>

          <template v-if="order.discount || order.gifts?.length">
            <h3 class="order-section-title">优惠与赠品</h3>
            <a-descriptions v-if="order.discount" size="small" bordered :column="{xs: 1, sm: 2}">
              <a-descriptions-item label="优惠合计">{{ amount(order.discount.discountAmount) }}</a-descriptions-item>
              <a-descriptions-item label="限时特价让利">{{ amount(order.discount.specialDiscountAmount) }}</a-descriptions-item>
              <a-descriptions-item label="优惠券">
                {{ order.discount.couponSnapshot?.couponName || '未使用' }}
              </a-descriptions-item>
              <a-descriptions-item label="券优惠">{{ amount(order.discount.couponSnapshot?.couponDiscount) }}</a-descriptions-item>
            </a-descriptions>
            <div v-for="gift in order.gifts ?? []" :key="`${gift.activityId}-${gift.skuId}`" class="order-gift">
              {{ gift.productName }}（{{ gift.specName }}）× {{ gift.quantity }} {{ gift.saleUnit }}
            </div>
          </template>

          <OrderFacts :order="order"/>
        </a-card>
      </template>
    </a-spin>
    <OrderDetail ref="operation" @saved="load"/>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {SCM_ORDER_SOURCE_ENUM, SCM_ORDER_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import {datetime} from '../common/scm-display';
import {hasPermission} from '../common/scm-permission';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import type {Item, Order} from './order-types';
import OrderDetail from './order-detail.vue';
import OrderFacts from './components/order-facts.vue';

const route = useRoute();
const router = useRouter();
const order = ref<Order | null>(null);
const operation = ref<InstanceType<typeof OrderDetail>>();
const loading = ref(false);
const error = ref('');
let requestId = 0;

const canOperate = computed(() => {
  const status = order.value?.status;
  if (status === 'PENDING' && (hasPermission('scm:order:confirm')
      || hasPermission('scm:order:actual-quantity'))) return true;
  if ((status === 'DRAFT' || status === 'PENDING') && hasPermission('scm:order:cancel')) return true;
  if (status === 'CONFIRMED' && (hasPermission('scm:order:return:add')
      || (order.value?.settlementMethod === 'COD' && hasPermission('scm:finance:receipt:add'))
      || ((!order.value?.settlementMethod || order.value?.settlementMethod === 'WALLET')
          && hasPermission('scm:payment:intent:create')))) return true;
  return hasPermission('scm:payment:transaction:query');
});

const itemColumns: TableColumnsType<Item> = [
  {title: '商品', dataIndex: 'productNameSnapshot', width: 170},
  {title: '商品规格', dataIndex: 'specNameSnapshot', width: 150},
  {title: '单位', dataIndex: 'saleUnitSnapshot', width: 80},
  {title: '下单量', dataIndex: 'orderedQuantity', align: 'right', width: 110},
  {title: '实数量', dataIndex: 'actualQuantity', align: 'right', width: 110},
  {title: '草稿单价', dataIndex: 'draftUnitPrice', align: 'right', width: 120},
  {title: '锁定单价', dataIndex: 'lockedUnitPrice', align: 'right', width: 120},
  {title: '下单金额', dataIndex: 'orderedLineAmount', align: 'right', width: 130},
  {title: '结算金额', dataIndex: 'settlementLineAmount', align: 'right', width: 130},
];

function settlementLabel(method?: Order['settlementMethod']): string {
  if (method === 'WALLET') return '余额支付';
  if (method === 'CREDIT') return '账期结算';
  if (method === 'COD') return '货到付款';
  return '—';
}

async function load() {
  const rawId = route.params.orderId;
  const id = typeof rawId === 'string' ? rawId : '';
  if (!/^\d+$/.test(id)) {
    order.value = null;
    error.value = '订单编号不正确';
    return;
  }
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response = await orderApi.detail(id, {suppressGlobalErrorMessage: true});
    if (current === requestId) order.value = response.data;
  } catch (cause) {
    if (current === requestId) {
      order.value = null;
      error.value = orderError(cause);
    }
  } finally {
    if (current === requestId) loading.value = false;
  }
}

watch(() => route.params.orderId, () => { void load(); }, {immediate: true});
</script>

<style scoped>
.order-detail-page { padding: 16px; }
.order-detail-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.order-detail-header h2 { margin: 0; }
.order-back { padding-left: 0; }
.order-detail-error { margin-bottom: 12px; }
.order-detail-overview { margin-top: 16px; }
.order-section-title { margin: 24px 0 12px; }
.order-gift { margin-top: 8px; }
</style>
