<template>
  <a-card size="small" :bordered="false">
    <header class="scm-detail-header">
      <div class="scm-detail-header__main">
        <a-button type="link" class="scm-detail-header__back" @click="router.push('/order/order-list')">
          <ArrowLeftOutlined />
          返回订单列表
        </a-button>
        <div class="scm-detail-header__title-row">
          <h1 class="scm-detail-header__title">{{ order?.orderNo || '销售订单详情' }}</h1>
          <ScmStatusTag v-if="order" :tone="orderStatusTone(order.status)" :label="SCM_ORDER_STATUS_ENUM[order.status ?? '']?.desc ?? order.status" />
          <a-tag v-for="tag in order?.tags ?? []" :key="String(tag.tagId)" :color="tagColor(tag.color)">{{ tag.name }}</a-tag>
        </div>
        <p v-if="order" class="scm-detail-header__meta">
          <span>{{ order.customerNameSnapshot || '—' }}</span>
          <template v-if="order.customerCodeSnapshot">
            · <span class="scm-mono">{{ order.customerCodeSnapshot }}</span></template
          >
          <template v-if="order.orderSource"> · {{ SCM_ORDER_SOURCE_ENUM[order.orderSource]?.desc ?? order.orderSource }}</template>
        </p>
      </div>
      <div class="scm-detail-header__actions">
        <a-button v-if="order && canPrint" @click="printOpen = true">打印订单</a-button>
        <a-button v-if="order && canOperate" type="primary" @click="operation?.open(order.orderId!)">订单操作</a-button>
      </div>
    </header>

    <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
      <template #action><a-button @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="order">
        <div class="scm-detail-read">
          <section class="scm-summary-section">
            <div class="scm-summary">
              <div class="scm-summary__item">
                <span class="scm-summary__label">下单金额</span>
                <span class="scm-summary__value scm-money">{{ amount(order.orderedTotalAmount, true) }}</span>
              </div>
              <div class="scm-summary__item">
                <span class="scm-summary__label">结算金额</span>
                <span class="scm-summary__value scm-money">{{ amount(order.settlementTotalAmount) }}</span>
              </div>
              <div class="scm-summary__item">
                <span class="scm-summary__label">结算约定</span>
                <span class="scm-summary__value">{{ settlementLabel(order.settlementMethod) }}</span>
              </div>
              <div class="scm-summary__item">
                <span class="scm-summary__label">期望配送时间</span>
                <span class="scm-summary__value">{{ datetime(order.expectDeliveryTime) }}</span>
              </div>
            </div>
          </section>

          <div class="scm-detail-grid">
            <section class="scm-detail-card">
              <h3 class="scm-detail-card__title">客户与订单</h3>
              <dl class="scm-field-list">
                <div class="scm-field">
                  <dt class="scm-field__label">客户</dt>
                  <dd class="scm-field__value">{{ order.customerNameSnapshot || '—' }}</dd>
                </div>
                <div class="scm-field">
                  <dt class="scm-field__label">客户编码</dt>
                  <dd class="scm-field__value scm-mono">{{ order.customerCodeSnapshot || '—' }}</dd>
                </div>
                <div class="scm-field">
                  <dt class="scm-field__label">订单来源</dt>
                  <dd class="scm-field__value">{{ SCM_ORDER_SOURCE_ENUM[order.orderSource]?.desc ?? order.orderSource }}</dd>
                </div>
                <div class="scm-field">
                  <dt class="scm-field__label">确认时间</dt>
                  <dd class="scm-field__value">{{ datetime(order.confirmedAt) }}</dd>
                </div>
              </dl>
            </section>

            <section class="scm-detail-card">
              <h3 class="scm-detail-card__title">收货信息</h3>
              <dl class="scm-field-list">
                <div class="scm-field">
                  <dt class="scm-field__label">收货人</dt>
                  <dd class="scm-field__value">{{ order.address?.receiverName || '—' }} / {{ order.address?.receiverPhone || '—' }}</dd>
                </div>
                <div class="scm-field scm-field--wide">
                  <dt class="scm-field__label">收货地址</dt>
                  <dd class="scm-field__value">{{ order.address?.address || '—' }}</dd>
                </div>
              </dl>
            </section>
          </div>

          <section class="scm-detail-card scm-detail-block">
            <h3 class="scm-detail-card__title">商品明细</h3>
            <a-table
              :data-source="order.items ?? []"
              :columns="itemColumns"
              row-key="itemId"
              :pagination="false"
              size="small"
              bordered
              :scroll="{x: scmColumnsWidth(itemColumns)}"
            >
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
          </section>

          <section v-if="order.discount || order.gifts?.length" class="scm-detail-card scm-detail-block">
            <h3 class="scm-detail-card__title">优惠与赠品</h3>
            <dl v-if="order.discount" class="scm-field-list scm-field-list--2">
              <div class="scm-field">
                <dt class="scm-field__label">优惠合计</dt>
                <dd class="scm-field__value scm-money">{{ amount(order.discount.discountAmount) }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">限时特价让利</dt>
                <dd class="scm-field__value scm-money">{{ amount(order.discount.specialDiscountAmount) }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">优惠券</dt>
                <dd class="scm-field__value">{{ order.discount.couponSnapshot?.couponName || '未使用' }}</dd>
              </div>
              <div class="scm-field">
                <dt class="scm-field__label">券优惠</dt>
                <dd class="scm-field__value scm-money">{{ amount(order.discount.couponSnapshot?.couponDiscount) }}</dd>
              </div>
            </dl>
            <div v-for="gift in order.gifts ?? []" :key="`${gift.activityId}-${gift.skuId}`" class="order-gift">
              {{ gift.productName }}（{{ gift.specName }}）× {{ gift.quantity }} {{ gift.saleUnit }}
            </div>
          </section>

          <OrderFacts :order="order" />
          <OrderReceivedCorrections :order="order" />
        </div>
      </template>
    </a-spin>
    <OrderDetail ref="operation" @saved="load" />
    <PrintDocumentModal
      :open="printOpen"
      document-type="SALES_ORDER"
      :business-ids="order?.orderId == null ? [] : [order.orderId]"
      @close="printOpen = false"
    />
  </a-card>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {ArrowLeftOutlined} from '@ant-design/icons-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import {tagColor} from '/@/api/business/scm/order-tag-api';
import {SCM_ORDER_SOURCE_ENUM, SCM_ORDER_STATUS_ENUM} from '/@/constants/business/scm/order-const';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {datetime} from '../common/scm-display';
import {hasPermission} from '../common/scm-permission';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import {orderStatusTone} from './order-status-tone';
import type {Item, Order} from './order-types';
import OrderDetail from './order-detail.vue';
import OrderFacts from './components/order-facts.vue';
import OrderReceivedCorrections from './components/order-received-corrections.vue';
import PrintDocumentModal from '../print/print-document-modal.vue';

const route = useRoute();
const router = useRouter();
const order = ref<Order | null>(null);
const operation = ref<InstanceType<typeof OrderDetail>>();
const printOpen = ref(false);
const canPrint = computed(() => hasPermission('scm:order:query'));
const loading = ref(false);
const error = ref('');
let requestId = 0;

const canOperate = computed(() => {
  const status = order.value?.status;
  if (status === 'PENDING' && (hasPermission('scm:order:confirm') || hasPermission('scm:order:actual-quantity'))) return true;
  if ((status === 'DRAFT' || status === 'PENDING') && hasPermission('scm:order:cancel')) return true;
  if (
    status === 'CONFIRMED' &&
    (hasPermission('scm:order:return:add') ||
      (order.value?.settlementMethod === 'COD' && hasPermission('scm:finance:receipt:add')) ||
      ((!order.value?.settlementMethod || order.value?.settlementMethod === 'WALLET') && hasPermission('scm:payment:intent:create')))
  )
    return true;
  return hasPermission('scm:payment:transaction:query');
});

const itemColumns: TableColumnsType<Item> = [
  {title: '商品', dataIndex: 'productNameSnapshot', width: 170},
  {title: '商品规格', dataIndex: 'specNameSnapshot', width: 150},
  {title: '单位', dataIndex: 'saleUnitSnapshot', width: 80},
  {title: '下单量', dataIndex: 'orderedQuantity', width: 110},
  {title: '实数量', dataIndex: 'actualQuantity', width: 110},
  {title: '草稿单价', dataIndex: 'draftUnitPrice', width: 120},
  {title: '锁定单价', dataIndex: 'lockedUnitPrice', width: 120},
  {title: '下单金额', dataIndex: 'orderedLineAmount', width: 130},
  {title: '结算金额', dataIndex: 'settlementLineAmount', width: 130},
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

watch(
  () => route.params.orderId,
  () => {
    printOpen.value = false;
    void load();
  },
  {immediate: true}
);
</script>

<style scoped>
.order-gift {
  margin-top: 8px;
}
</style>
