<template>
  <a-drawer :open="visible" :title="order?.orderNo||'订单详情'" width="min(1180px,96vw)" @close="visible=false">
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <a-spin :spinning="loading">
      <template v-if="order">
        <a-space class="actions">
          <a-tag>{{ SCM_ORDER_STATUS_ENUM[order.status!]?.desc }}</a-tag>
          <a-button @click="load">刷新</a-button>
          <a-button v-if="order.status==='PENDING'" type="primary" v-privilege="'scm:order:confirm'" @click="confirm">
            确认订单
          </a-button>
          <a-button v-if="['DRAFT','PENDING'].includes(order.status!)" danger v-privilege="'scm:order:cancel'"
                    @click="cancelOpen=true">取消订单
          </a-button>
          <a-button v-if="order.status==='CONFIRMED'" v-privilege="'scm:order:return:add'"
                    @click="returnForm?.open(order)">申请退货
          </a-button>
          <a-button v-if="order.status === 'CONFIRMED'" v-privilege="'scm:payment:intent:create'"
                    @click="paymentDrawer?.open(order)">订单支付</a-button>
          <a-button v-privilege="'scm:payment:transaction:query'" @click="paymentDrawer?.open(order)">支付记录</a-button>
        </a-space>
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="客户">{{ order.customerNameSnapshot }}（{{ order.customerCodeSnapshot }}）
          </a-descriptions-item>
          <a-descriptions-item label="下单时结算方式">
            {{ SETTLE_MODE_ENUM[order.settleModeSnapshot ?? '']?.desc || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="收货人">{{ order.address?.receiverName }} / {{ order.address?.receiverPhone }}
          </a-descriptions-item>
          <a-descriptions-item label="收货地址">{{ order.address?.address }}</a-descriptions-item>
          <a-descriptions-item label="下单金额">{{ amount(order.orderedTotalAmount, true) }}</a-descriptions-item>
          <a-descriptions-item label="结算金额">{{ amount(order.settlementTotalAmount) }}</a-descriptions-item>
          <a-descriptions-item label="期望配送时间">{{ order.expectDeliveryTime || '—' }}</a-descriptions-item>
          <a-descriptions-item label="备注">{{ order.remark || '—' }}</a-descriptions-item>
        </a-descriptions>
        <!-- 优惠是独立事实：订单金额列仍是结算口径，这里回答「减了多少、按什么规则减的」 -->
        <a-descriptions v-if="order.discount" class="discount" bordered size="small" :column="2">
          <a-descriptions-item label="优惠合计">{{ amount(order.discount.discountAmount) }}</a-descriptions-item>
          <a-descriptions-item label="其中限时特价让利">{{ amount(order.discount.specialDiscountAmount) }}</a-descriptions-item>
          <a-descriptions-item label="优惠基数（下单金额口径）">{{ amount(order.discount.baseAmount) }}</a-descriptions-item>
          <a-descriptions-item label="券优惠">{{ amount(order.discount.couponSnapshot?.couponDiscount) }}</a-descriptions-item>
          <a-descriptions-item label="用券">
            <template v-if="order.discount.couponSnapshot">
              {{ order.discount.couponSnapshot.couponName || '—' }}
              <span v-if="order.discount.couponSnapshot.couponCode">（{{ order.discount.couponSnapshot.couponCode }}）</span>
            </template>
            <template v-else>未用券</template>
          </a-descriptions-item>
          <a-descriptions-item label="生效活动" :span="2">
            <div v-for="activity in appliedActivities" :key="activity.activityId">
              {{ activity.activityName || '—' }}（{{ activity.activityCode }} v{{ activity.version }}）：
              {{ amount(activity.discountAmount) }}
            </div>
            <span v-if="appliedActivities.length === 0">无活动优惠</span>
            <div v-if="(order.discount.activitySnapshot?.suppressed?.length ?? 0) > 0" class="discount-suppressed">
              被互斥组挤掉：{{ order.discount.activitySnapshot?.suppressed?.join('、') }}
            </div>
          </a-descriptions-item>
        </a-descriptions>
        <!-- 满赠赠品：非金额权益，不减订单金额，但必须发货；出库、分拣与小票都读这份冻结权益 -->
        <a-descriptions v-if="(order.gifts?.length ?? 0) > 0" class="discount" bordered size="small" :column="1">
          <a-descriptions-item label="赠品">
            <div v-for="gift in order.gifts ?? []" :key="`${gift.activityId}-${gift.skuId}`">
              {{ gift.productName }}（{{ gift.skuCode }} / {{ gift.specName }}）
              × {{ gift.quantity }} {{ gift.saleUnit }}
              <span class="gift-source">— 来自活动「{{ gift.activityName || gift.activityCode }}」v{{ gift.version }}</span>
            </div>
            <div class="gift-note">赠品金额恒为 0，不计入订单金额；出库成本单独计入本单履约成本。</div>
          </a-descriptions-item>
        </a-descriptions>
        <a-table class="items" :data-source="order.items" :columns="columns" row-key="itemId" :pagination="false"
                 :scroll="{x:1100}" size="small" bordered>
          <template #bodyCell="{record,column}">
            <template
                v-if="['draftUnitPrice','lockedUnitPrice','orderedLineAmount','settlementLineAmount'].includes(column.dataIndex)">
              {{ amount(record[column.dataIndex], column.dataIndex === 'draftUnitPrice' || column.dataIndex === 'orderedLineAmount') }}
            </template>
            <template v-else-if="column.dataIndex==='action'">
              <a-button v-if="order.status==='PENDING'&&record.productTypeSnapshot==='NON_STANDARD'" type="link"
                        v-privilege="'scm:order:actual-quantity'" @click="editActual(record)">录入实重
              </a-button>
            </template>
          </template>
        </a-table>
        <a-button v-privilege="'scm:order:log:query'" @click="loadLogs">查看操作记录</a-button>
        <a-timeline>
          <a-timeline-item v-for="log in logs" :key="log.logId">{{ datetime(log.createdAt) }} · {{ log.operatorName }} ·
            {{ SCM_ORDER_OPERATION_ENUM[log.operationType]?.desc }}
            <a-collapse>
              <a-collapse-panel key="audit" header="变更前后">
                <ScmDiffTable :before="log.beforeData" :after="log.afterData"/>
              </a-collapse-panel>
            </a-collapse>
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-spin>
    <OrderPaymentDrawer ref="paymentDrawer"/>
    <ReturnForm ref="returnForm" @saved="load"/>
    <a-modal :open="actualOpen" title="录入实际数量" :confirm-loading="saving" @ok="saveActual"
             @cancel="actualOpen=false">
      <a-form layout="vertical">
        <a-form-item label="实际数量" name="actualQuantity" required>
          <a-input-number v-model:value="actualQuantity" string-mode :precision="4" :min="'0.0001'"/>
        </a-form-item>
        <a-form-item label="修改原因" name="actualQuantityReason" required>
          <a-input v-model:value="actualReason" maxlength="500"/>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal :open="creditOpen" title="确认订单与授信检查" :confirm-loading="saving"
             @ok="confirmWithCredit" @cancel="creditOpen=false" :ok-button-props="{disabled: !creditCheck?.allowed && !creditOverride}">
      <a-alert v-if="error" :message="error" type="error" show-icon/>
      <template v-if="creditCheck">
        <a-alert :type="creditCheck.allowed ? 'success' : 'warning'" show-icon
                 :message="creditCheck.allowed ? '当前授信检查通过，提交时将再次校验' : '额度不足或存在逾期，订单暂不能确认'"/>
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="未核销应收">{{ amount(creditCheck.openReceivableAmount) }}</a-descriptions-item>
          <a-descriptions-item label="已确认未形成应收">{{ amount(creditCheck.confirmedOrderAmount) }}</a-descriptions-item>
          <a-descriptions-item label="本次实重结算金额">{{ amount(creditCheck.requestedOrderAmount) }}</a-descriptions-item>
          <a-descriptions-item label="确认后授信占用">{{ amount(creditCheck.projectedExposure) }}</a-descriptions-item>
          <a-descriptions-item label="授信额度">{{ creditCheck.creditLimit === '0.0000' ? '未设置金额上限' : amount(creditCheck.creditLimit) }}</a-descriptions-item>
          <a-descriptions-item v-if="creditCheck.overdue" label="最早逾期到期日">{{ creditCheck.earliestOverdueDate }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="creditCheck.amountThresholdHint">{{ creditCheck.amountThresholdHint }}</p>
        <a-divider style="margin: 12px 0"/>
        <a-form-item label="优惠券">
          <a-select
              v-model:value="couponInstanceId"
              :loading="couponLoading"
              :options="couponOptions"
              placeholder="不使用优惠券"
              allow-clear
              style="width: 100%"
          />
          <a-typography-text type="secondary" class="coupon-hint">
            活动优惠由服务端按当前生效规则自动计算；券只接受「用哪张」，确认时服务端会再验一次券状态。
            试算不占用券，确认成功才占用。
          </a-typography-text>
        </a-form-item>
        <a-alert v-if="previewError" :message="previewError" type="warning" show-icon class="coupon-hint"/>
        <a-descriptions v-if="discountPreview" :column="1" size="small" bordered>
          <a-descriptions-item label="行基础金额（下单量口径）">{{ amount(discountPreview.baseAmount) }}</a-descriptions-item>
          <a-descriptions-item label="限时特价让利">{{ amount(discountPreview.specialDiscount) }}</a-descriptions-item>
          <a-descriptions-item label="活动优惠">{{ amount(discountPreview.activityDiscount) }}</a-descriptions-item>
          <a-descriptions-item label="券优惠">{{ amount(discountPreview.couponDiscount) }}</a-descriptions-item>
          <a-descriptions-item label="合计优惠">{{ amount(discountPreview.discountAmount) }}</a-descriptions-item>
          <a-descriptions-item label="客户实付">{{ amount(discountPreview.finalAmount) }}</a-descriptions-item>
        </a-descriptions>
        <div v-if="!creditCheck.allowed" v-privilege="'scm:order:credit:override'">
          <a-checkbox v-model:checked="creditOverride">申请授信例外放行（将记录操作日志）</a-checkbox>
          <a-form-item v-if="creditOverride" label="例外原因" required>
            <a-textarea v-model:value="creditOverrideReason" :maxlength="500" :auto-size="{minRows: 2, maxRows: 5}"/>
          </a-form-item>
        </div>
      </template>
    </a-modal>
    <a-modal :open="cancelOpen" title="取消订单" :confirm-loading="saving" @ok="cancel" @cancel="cancelOpen=false">
      <a-form-item label="取消原因" name="cancelReason" required>
        <a-input v-model:value="cancelReason" maxlength="500"/>
      </a-form-item>
    </a-modal>
  </a-drawer>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi, type CreditCheck} from '/@/api/business/scm/order-api';
import {orderLogApi} from '/@/api/business/scm/order-log-api';
import {promotionApi} from '/@/api/business/scm/promotion-api';
import type {OrderDiscountAppliedActivity, PromotionCouponInstance, PromotionDiscount} from '/@/views/business/scm/promotion/promotion-types';
import {SCM_ORDER_STATUS_ENUM, SCM_ORDER_OPERATION_ENUM} from '/@/constants/business/scm/order-const';
import type {Order, Item, Id, LogRow} from './order-types';
import {amount, fixed} from './order-form-model';
import {datetime} from '../common/scm-display';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import {orderError} from './order-errors';
import ReturnForm from './components/order-return-form-modal.vue';
import OrderPaymentDrawer from './components/order-payment-drawer.vue';
const paymentDrawer = ref<InstanceType<typeof OrderPaymentDrawer>>();

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), saving = ref(false), error = ref(''), order = ref<Order>(),
    logs = ref<LogRow[]>([]), returnForm = ref<InstanceType<typeof ReturnForm>>();
const actualOpen = ref(false), cancelOpen = ref(false), actualQuantity = ref('1.0000'), actualReason = ref(''),
    cancelReason = ref(''), activeItem = ref<Item>();
const columns: TableColumnsType<Item> = [{title: '商品', dataIndex: 'productNameSnapshot', width: 140}, {
  title: '规格',
  dataIndex: 'specNameSnapshot',
  width: 100
}, {title: '单位', dataIndex: 'saleUnitSnapshot', width: 65}, {
  title: '下单量',
  dataIndex: 'orderedQuantity',
  align: 'right',
  width: 100
}, {title: '实数量', dataIndex: 'actualQuantity', align: 'right', width: 100}, {
  title: '草稿单价',
  dataIndex: 'draftUnitPrice',
  align: 'right',
  width: 110
}, {title: '锁定单价', dataIndex: 'lockedUnitPrice', align: 'right', width: 110}, {
  title: '下单金额',
  dataIndex: 'orderedLineAmount',
  align: 'right',
  width: 115
}, {title: '结算金额', dataIndex: 'settlementLineAmount', align: 'right', width: 115}, {
  title: '操作',
  dataIndex: 'action',
  width: 120
}];

async function open(id: Id) {
  visible.value = true;
  logs.value = [];
  order.value = {orderId: id} as Order;
  await load();
}

async function load() {
  if (!order.value?.orderId) return;
  loading.value = true;
  error.value = '';
  try {
    order.value = (await orderApi.detail(order.value.orderId)).data;
  } catch (e) {
    error.value = orderError(e);
  } finally {
    loading.value = false;
  }
}

async function loadLogs() {
  try {
    logs.value = (await orderLogApi.query({orderId: order.value!.orderId, pageNum: 1, pageSize: 100})).data.list;
  } catch (e) {
    error.value = orderError(e);
  }
}

const creditOpen = ref(false), creditCheck = ref<CreditCheck>(), creditOverride = ref(false), creditOverrideReason = ref('');

/**
 * 券选择与试算：券是客户自己的权益，必须显式指定券实例 id，服务端不自动挑券；
 * 活动优惠由服务端按当前生效规则算出，前端不拼优惠组合。
 */
const couponInstanceId = ref<Id | undefined>();
const couponOptions = ref<{value: Id; label: string}[]>([]);
const couponLoading = ref(false);
const discountPreview = ref<PromotionDiscount>();
const previewError = ref('');

/**
 * 已冻结优惠里实际生效的活动。**只渲染、不重算**：每条活动的优惠额是后端冻结时写下的字符串，
 * 前端不做求和（求和会引入第二个真相，与「优惠合计」对不上时无法判断哪个对）。
 */
const appliedActivities = computed<OrderDiscountAppliedActivity[]>(
    () => order.value?.discount?.activitySnapshot?.applied ?? []);

async function loadCoupons() {
  couponInstanceId.value = undefined;
  couponOptions.value = [];
  discountPreview.value = undefined;
  previewError.value = '';
  const customerId = order.value?.customerId;
  if (customerId === undefined || customerId === null) {
    return;
  }
  couponLoading.value = true;
  try {
    const rows: PromotionCouponInstance[] = (await promotionApi.couponInstances(customerId, 'AVAILABLE')).data ?? [];
    couponOptions.value = rows.map((row) => ({
      value: row.id,
      label: `${row.couponName ?? row.couponCode ?? '优惠券'}（${row.instanceNo}）`,
    }));
  } catch (e) {
    // 券列表拉不到只影响「用哪张券」，不该挡住确认本身
    previewError.value = orderError(e);
  } finally {
    couponLoading.value = false;
  }
}

/**
 * 试算只用于给操作人看清「减多少」，不占用券；行基础金额取订单行的下单金额
 * （`ordered_line_amount`，与确认时的冻结口径一致），未锁定价格的行不参与。
 */
async function loadPreview() {
  discountPreview.value = undefined;
  previewError.value = '';
  const customerId = order.value?.customerId;
  const lines = (order.value?.items ?? [])
      .filter((item) => item.itemId !== undefined && item.itemId !== null && item.skuId !== undefined
          && item.skuId !== null && item.orderedQuantity && item.orderedLineAmount)
      .map((item) => ({
        orderItemId: item.itemId as Id,
        // SKU 与数量是限时特价的命中依据：特价让利 = 基础金额 − 数量 × 特价
        skuId: item.skuId as Id,
        quantity: item.orderedQuantity as string,
        baseAmount: item.orderedLineAmount as string,
      }));
  if (customerId === undefined || customerId === null || lines.length === 0) {
    return;
  }
  try {
    discountPreview.value = (await promotionApi.discountPreview({
      customerId,
      couponInstanceId: couponInstanceId.value ?? null,
      lines,
    })).data;
  } catch (e) {
    previewError.value = orderError(e);
  }
}

watch(couponInstanceId, () => {
  void loadPreview();
});

async function confirm() {
  if (!order.value?.orderId || saving.value) return;
  saving.value = true;
  error.value = '';
  try {
    creditCheck.value = (await orderApi.orderCreditCheck(order.value.orderId)).data;
    creditOverride.value = false;
    creditOverrideReason.value = '';
    creditOpen.value = true;
    // 打开确认弹窗才拉券与试算：确认是低频动作，不值得在详情加载时预取
    await loadCoupons();
    await loadPreview();
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

async function confirmWithCredit() {
  if (!order.value || saving.value) return;
  if (!creditCheck.value?.allowed && (!creditOverride.value || !creditOverrideReason.value.trim())) {
    message.error('授信未通过；例外放行必须填写原因');
    return;
  }
  saving.value = true;
  try {
    await orderApi.confirm({orderId: order.value.orderId, version: order.value.version,
      creditOverride: creditOverride.value, creditOverrideReason: creditOverride.value ? creditOverrideReason.value.trim() : undefined,
      couponInstanceId: couponInstanceId.value ?? undefined});
    creditOpen.value = false;
    await load();
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

function editActual(i: Item) {
  activeItem.value = i;
  actualQuantity.value = i.actualQuantity ?? i.orderedQuantity;
  actualReason.value = '';
  actualOpen.value = true;
}

async function saveActual() {
  if (!actualReason.value.trim()) {
    message.error('请输入实重修改原因');
    return;
  }
  saving.value = true;
  try {
    await orderApi.actual({
      orderId: order.value!.orderId,
      itemId: activeItem.value!.itemId,
      version: activeItem.value!.version,
      actualQuantity: fixed(actualQuantity.value),
      reason: actualReason.value
    });
    actualOpen.value = false;
    await load();
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

async function cancel() {
  if (!cancelReason.value.trim()) {
    message.error('请输入取消原因');
    return;
  }
  saving.value = true;
  try {
    await orderApi.cancel({orderId: order.value!.orderId, version: order.value!.version, reason: cancelReason.value});
    cancelOpen.value = false;
    await load();
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>.actions, .items {
  margin: 16px 0;
}

pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.coupon-hint {
  font-size: 12px;
}

.discount {
  margin-top: 12px;
}

.discount-suppressed {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}

.gift-source {
  color: var(--ant-color-text-secondary);
}

.gift-note {
  color: var(--ant-color-text-secondary);
  font-size: 12px;
}</style>
