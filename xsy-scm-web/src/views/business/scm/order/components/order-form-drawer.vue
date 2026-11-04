<template>
  <a-drawer
    :title="exchangeReturnId ? '创建关联换货订单' : form.orderId ? '编辑销售订单' : '新建销售订单'"
    :open="visible"
    :width="scmDrawerWidth('xl')"
    :mask-closable="!saving"
    :keyboard="!saving && !itemModalOpen"
    @close="closeDrawer"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <p v-if="exchangeReturnId" class="scm-note">原货已接收。替换订单重新定价后按正常订单办理，不自动抵扣退款。</p>
    <p v-if="form.status === 'PENDING'" class="scm-note pending-edit-note">
      该订单已提交，等待确认。保存后仍为待确认；未修改的商品沿用提交时报价，如需按最新价格重算请使用「批量核价」。
    </p>
    <p v-if="isConfirmedOrderStatus(form.status) && form.lockedFlag" class="scm-note pending-edit-note">
      该订单已锁单，修改需具备「锁单后改单」权限；无权限时请新建加单。
    </p>
    <p v-else-if="isConfirmedOrderStatus(form.status)" class="scm-note pending-edit-note">
      {{
        form.dispatchedAt ? '该订单已发车出库，改单需具备「发车后改单」权限。' : ''
      }}改单按原订单单价重算金额，不会自动改价；需称重的新增商品请新建加单。
    </p>
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="flatModel" :rules="formRules" layout="vertical" class="app-drawer-form">
        <!-- 表头字段按内容宽度紧凑排布，不跟随下方商品明细表的整宽拉伸 -->
        <div class="order-head">
          <a-form-item label="客户" name="customerId" required class="order-head__customer">
            <CustomerSelect v-if="!form.orderId" v-model:value="form.customerId" :disabled="!!exchangeReturnId" @change="customerChanged" />
            <a-input v-else :value="form.customerNameSnapshot" disabled />
          </a-form-item>
          <a-form-item label="订单来源" name="orderSource" class="order-head__source">
            <a-select
              v-model:value="form.orderSource"
              :disabled="!!exchangeReturnId"
              :options="[
                {value: 'ADMIN', label: '后台录单'},
                {value: 'SUPPLEMENT', label: '补单'},
              ]"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item v-if="form.orderSource === 'SUPPLEMENT'" label="关联原订单" name="originalOrderId" class="order-head__original">
            <a-select
              v-model:value="form.originalOrderId"
              show-search
              allow-clear
              :filter-option="false"
              :disabled="!!exchangeReturnId"
              :options="originals"
              @search="loadOriginals"
              placeholder="搜索已确认订单号"
            />
          </a-form-item>
          <a-form-item v-if="form.orderSource === 'SUPPLEMENT'" label="补单原因" name="supplementReason" required class="order-head__reason">
            <a-textarea v-model:value="form.supplementReason" :maxlength="500" :rows="2" show-count />
          </a-form-item>
          <a-form-item v-if="!form.orderId" name="deliveryWarehouseId" class="order-head__warehouse">
            <template #label>
              配送仓库
              <ScmFieldHelp label="配送仓库" text="仅用于本次订单，选仓后重新计算该仓的成本公式价格。" />
            </template>
            <WarehouseSelect v-model:value="warehouseValue" placeholder="组合商品 / 成本公式须明确选仓" width="100%" />
          </a-form-item>
          <a-form-item label="期望配送时间" name="expectDeliveryTime" class="order-head__time">
            <a-date-picker v-model:value="form.expectDeliveryTime" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
          </a-form-item>
          <a-form-item label="收货人" name="receiverName" required class="order-head__receiver">
            <a-input v-model:value="form.address.receiverName" :disabled="!!form.orderId" :maxlength="100" />
          </a-form-item>
          <a-form-item label="联系电话" name="receiverPhone" required class="order-head__phone app-drawer-field--compact">
            <a-input v-model:value="form.address.receiverPhone" :disabled="!!form.orderId" :maxlength="32" />
          </a-form-item>
          <a-form-item label="收货地址" name="address" required class="order-head__address app-drawer-field--wide">
            <a-input v-model:value="form.address.address" :disabled="!!form.orderId" :maxlength="500" />
          </a-form-item>
          <a-form-item label="备注" name="remark" class="order-head__remark">
            <a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" show-count />
          </a-form-item>
        </div>
        <ItemTable
          ref="itemTableRef"
          :items="form.items"
          :customer-id="form.customerId"
          :order-id="form.orderId"
          @price="preview"
          @modal-open="itemModalOpen = $event"
        />
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">关闭</a-button>
        <a-button v-if="!form.orderId && !exchangeReturnId" :loading="saving" :disabled="loading" @click="save(true)"> 创建并继续录单 </a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save()">
          {{ exchangeReturnId ? '创建换货单' : form.orderId ? '保存修改' : '创建订单' }}
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {isConfirmedOrderStatus} from '/@/constants/business/scm/order-const';

import {computed, reactive, ref, watch} from 'vue';
import {onBeforeRouteLeave} from 'vue-router';
import {message, type FormInstance} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {orderApi} from '/@/api/business/scm/order-api';
import {newOrder, nextOrderForCustomer, payload, validateOrder, fixed, fromHistory} from '../order-form-model';
import {orderError} from '../order-errors';
import type {Order, Id} from '../order-types';
import ItemTable from './order-item-editable-table.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';
import {createUnsavedGuard} from '../../common/scm-unsaved-guard';

const emit = defineEmits<{saved: []}>();
const form = ref<Order>(newOrder()),
  visible = ref(false),
  loading = ref(false),
  saving = ref(false),
  pricing = ref(false),
  error = ref(''),
  /** 明细弹窗打开时接管 Esc，避免直接触发抽屉关闭确认。 */
  itemModalOpen = ref(false),
  originals = ref<{value: Id; label: string}[]>([]);
const exchangeReturnId = ref<Id>();
/** 关闭前拦截未保存修改；加载失败时基线为空，不拦截。 */
const unsaved = createUnsavedGuard(() => ({form: form.value, exchangeReturnId: exchangeReturnId.value, originals: originals.value}));
const formRef = ref<FormInstance>();
/**
 * 收货信息在 `form.address.*` 里，展平一份给表单校验用（顶层 `address` 就是详细地址）。
 *
 * 必须是稳定的 reactive 对象、并在 form 变化时同步刷新：antd 校验读的是 form 的 `model` prop，
 * 而 prop 要等重新渲染才更新。若这里写成 computed，每次重算都是新对象，change 事件里拿到的仍是
 * 上一轮的 model，客户已经选好也会挂着「请选择客户」。
 */
const flatModel = reactive<Record<string, unknown>>({});
function syncFlatModel() {
  Object.assign(flatModel, form.value, form.value.address);
}
watch(form, syncFlatModel, {deep: true, flush: 'sync', immediate: true});
const warehouseValue = computed<Id | undefined>({
  get: () => form.value.deliveryWarehouseId ?? undefined,
  set: (value) => {
    form.value.deliveryWarehouseId = value ?? null;
  },
});
/** 必填项逐项校验：错误显示在对应输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  customerId: [{required: true, message: '请选择客户', trigger: 'change'}],
  receiverName: [{required: true, message: '请填写收货人', trigger: 'blur'}],
  receiverPhone: [{required: true, message: '请填写联系电话', trigger: 'blur'}],
  address: [{required: true, message: '请填写收货地址', trigger: 'blur'}],
  supplementReason: [
    {
      validator: () =>
        form.value.orderSource !== 'SUPPLEMENT' || form.value.supplementReason?.trim()
          ? Promise.resolve()
          : Promise.reject(new Error('请填写补单原因')),
      trigger: 'blur',
    },
  ],
};
let requestId = 0;
let previewRequestId = 0;

async function open(id?: Id) {
  const current = ++requestId;
  exchangeReturnId.value = undefined;
  error.value = '';
  unsaved.clear();
  form.value = newOrder();
  visible.value = true;
  if (!id) {
    unsaved.capture();
    return;
  }
  if (id) {
    loading.value = true;
    try {
      const detail = (await orderApi.detail(id, {suppressGlobalErrorMessage: true})).data;
      if (current !== requestId) return;
      form.value = detail;
      form.value.items.forEach((i) => {
        i.unitPrice = i.manualPriceOverride ? i.draftUnitPrice : null;
        i.overrideReason = i.manualPriceReason;
      });
      unsaved.capture();
    } catch (e) {
      if (current === requestId) error.value = orderError(e);
    } finally {
      if (current === requestId) loading.value = false;
    }
  }
}

/** 只用到明细表的 closeRecentPopover，不依赖组件实例的具体类型。 */
const itemTableRef = ref<{closeRecentPopover: () => void} | null>(null);

// 关闭方式有多种，统一在 visible 变为 false 时收起价签浮层
watch(
  () => form.value.deliveryWarehouseId,
  () => {
    if (visible.value && !form.value.orderId && form.value.customerId && form.value.items.length) {
      void preview();
    }
  }
);

watch(visible, (opened) => {
  if (!opened) itemTableRef.value?.closeRecentPopover();
});

function forceClose() {
  requestId++;
  visible.value = false;
}

/** 点遮罩、X 或关闭时，有未保存修改先确认。 */
async function closeDrawer() {
  if (saving.value) return;
  if (await unsaved.confirmDiscard()) forceClose();
}

// 列表页会被 keep-alive 缓存；切走菜单时必须收起抽屉，否则遮罩会一直盖在新页面上。
// 有未保存修改时先留在当前页确认，避免切页后数据被静默丢弃。
onBeforeRouteLeave(async () => {
  if (!(await unsaved.confirmDiscard())) return false;
  forceClose();
});

/** 复用历史订单：只复制基本信息，价格和可用性重新解析。 */
async function openFromHistory(id: Id) {
  const current = ++requestId;
  exchangeReturnId.value = undefined;
  error.value = '';
  unsaved.clear();
  visible.value = true;
  loading.value = true;
  try {
    const detail = (await orderApi.detail(id, {suppressGlobalErrorMessage: true})).data;
    if (current !== requestId) return;
    form.value = fromHistory(detail);
    await preview();
    unsaved.capture();
  } catch (e) {
    if (current === requestId) error.value = orderError(e);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function openForExchange(
  returnId: Id,
  returnNo: string,
  originalOrderId: Id,
  returnedLines: Array<{orderItemId: Id; receivedQuantity: string}>
): Promise<void> {
  const current = ++requestId;
  exchangeReturnId.value = returnId;
  error.value = '';
  unsaved.clear();
  visible.value = true;
  loading.value = true;
  try {
    const original = (await orderApi.detail(originalOrderId, {suppressGlobalErrorMessage: true})).data;
    if (current !== requestId || !visible.value || exchangeReturnId.value !== returnId) return;
    const next = fromHistory(original);
    const receivedByItem = new Map(returnedLines.map((line) => [String(line.orderItemId), line.receivedQuantity]));
    next.items = original.items.flatMap((source, index) => {
      const quantity = receivedByItem.get(String(source.itemId));
      return quantity ? [{...next.items[index], orderedQuantity: quantity}] : [];
    });
    next.orderSource = 'SUPPLEMENT';
    next.originalOrderId = originalOrderId;
    next.supplementReason = `换货：退货单${returnNo}`;
    form.value = next;
    originals.value = [{value: originalOrderId, label: original.orderNo ?? String(originalOrderId)}];
    await preview();
    unsaved.capture();
  } catch (cause) {
    if (current === requestId) error.value = orderError(cause);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

async function customerChanged(id?: Id) {
  if (!id) return;
  const current = ++requestId;
  try {
    const c = (await customerApi.detail(id, {suppressGlobalErrorMessage: true})).data;
    if (current !== requestId) return;
    form.value.address = {
      receiverName: c.contactName ?? '',
      receiverPhone: c.contactPhone ?? '',
      address: c.address ?? '',
    };
    form.value.originalOrderId = null;
    await preview();
    await loadOriginals('');
  } catch (e) {
    error.value = orderError(e);
  }
}

async function preview() {
  const customerId = form.value.customerId;
  const skuIds = form.value.items.flatMap((i) => (i.skuId ? [i.skuId] : []));
  if (!customerId || !skuIds.length) return;
  const current = ++previewRequestId;
  pricing.value = true;
  try {
    const r = await orderApi.preview({customerId, skuIds, warehouseId: form.value.deliveryWarehouseId ?? null}, {suppressGlobalErrorMessage: true});
    if (current !== previewRequestId) return;
    for (const i of form.value.items) {
      const p = r.data.items.find((p) => String(p.skuId) === String(i.skuId));
      i.draftUnitPrice = p?.unitPrice ?? null;
      i.draftPriceSource = p?.priceSource ?? null;
      // 单位用于参考价对比，只在解析结果给出时覆盖，避免清掉已保存行的快照
      if (p?.saleUnit) i.saleUnitSnapshot = p.saleUnit;
    }
  } catch (e) {
    if (current === previewRequestId) error.value = orderError(e);
  } finally {
    if (current === previewRequestId) pricing.value = false;
  }
}

async function loadOriginals(keyword: string) {
  try {
    const r = await orderApi.query(
      {
        pageNum: 1,
        pageSize: 20,
        status: 'CONFIRMED',
        customerId: form.value.customerId,
        keyword,
      },
      {suppressGlobalErrorMessage: true}
    );
    originals.value = r.data.list.map((o) => ({value: o.orderId!, label: o.orderNo!}));
  } catch (e) {
    error.value = orderError(e);
  }
}

/** 继续录下一单：只保留客户和仓库，地址重新获取，商品和价格重新录入。 */
async function continueWithNextOrder(previous: Order): Promise<void> {
  const token = ++requestId;
  form.value = nextOrderForCustomer(previous);
  originals.value = [];
  itemTableRef.value?.closeRecentPopover();
  error.value = '';
  formRef.value?.clearValidate();
  if (!form.value.customerId) return;
  loading.value = true;
  try {
    const c = (
      await customerApi.detail(form.value.customerId, {
        suppressGlobalErrorMessage: true,
      })
    ).data;
    if (token !== requestId || !visible.value) return;
    form.value.address = {
      receiverName: c.contactName ?? '',
      receiverPhone: c.contactPhone ?? '',
      address: c.address ?? '',
    };
  } catch {
    if (token === requestId && visible.value) {
      form.value.customerId = undefined;
      error.value = '上一单已创建成功，但未能取得客户最新收货信息，请重新选择客户';
    }
  } finally {
    if (token === requestId) loading.value = false;
  }
}

async function save(continueCreating = false) {
  if (saving.value || loading.value) return;
  saving.value = true;
  error.value = '';
  try {
    form.value.items.forEach((i) => {
      i.orderedQuantity = fixed(i.orderedQuantity);
    });
    // 必填项走表单校验：输入框下方提示 + toast，明细行问题在下面单独提示
    try {
      await formRef.value?.validate();
    } catch (err) {
      notifyFormInvalid(err, formRef.value);
      return;
    }
    // 明细行（不是表单字段）与其余业务规则用 toast
    const invalid = validateOrder(form.value);
    if (invalid) {
      message.warning(invalid);
      return;
    }
    const f = payload(form.value);
    const isNew = !f.orderId;
    if (f.orderSource !== 'SUPPLEMENT') {
      f.originalOrderId = null;
      f.supplementReason = null;
    }
    if (f.orderId) {
      await orderApi.update(f);
      message.success('订单修改已保存');
    } else if (exchangeReturnId.value) {
      await orderApi.createExchange(exchangeReturnId.value, f);
      message.success('换货单已创建，请按正常订单流程确认');
    } else {
      const result = await orderApi.createAndProgress(f);
      message.success(result.data.status === 'CONFIRMED' ? '订单已创建并确认' : '订单已创建，待确认');
    }
    emit('saved');
    if (isNew && !exchangeReturnId.value && continueCreating && visible.value) {
      await continueWithNextOrder(f);
    } else {
      visible.value = false;
    }
  } catch {
    // 保存失败只走全局 toast；抽屉里的 error 留给加载失败
  } finally {
    saving.value = false;
  }
}

defineExpose({open, openFromHistory, openForExchange});
</script>

<style scoped>
.pending-edit-note {
  margin-bottom: 12px;
}

/* 表头字段按自身内容定宽；只有商品明细表需要铺满抽屉 */
.order-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0 20px;
}

.order-head :deep(.ant-form-item) {
  max-width: 100%;
  margin-bottom: 16px;
}

.order-head__customer {
  flex: 0 0 300px;
}

.order-head__source {
  flex: 0 0 150px;
}

.order-head__original {
  flex: 0 0 220px;
}

.order-head__reason {
  flex: 0 0 300px;
}

.order-head__warehouse {
  flex: 0 0 230px;
}

.order-head__time {
  flex: 0 0 200px;
}

.order-head__receiver {
  flex: 0 0 160px;
}

.order-head__phone {
  flex: 0 0 180px;
}

.order-head__address,
.order-head__remark {
  flex: 0 0 480px;
}

@media (max-width: 900px) {
  .order-head :deep(.ant-form-item) {
    flex: 1 1 240px;
  }
}
</style>
