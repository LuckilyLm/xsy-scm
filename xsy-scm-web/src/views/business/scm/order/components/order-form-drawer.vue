<template>
  <a-drawer
    :title="exchangeReturnId ? '创建关联换货订单' : form.orderId ? '编辑销售订单' : '新建销售订单'"
    :open="visible"
    :width="scmDrawerWidth('xl')"
    @close="closeDrawer"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <p v-if="exchangeReturnId" class="scm-note">原货已接收。替换订单重新定价并从草稿开始；后续提交、预留和配送按正常订单办理，不自动抵扣退款。</p>
    <a-alert
      v-if="form.status === 'PENDING'"
      type="warning"
      show-icon
      class="pending-edit-note"
      message="该订单已提交、等待确认：保存后仍为待确认，未改动的商品沿用提交时的报价；需要按最新价格重算请用「批量核价」。"
    />
    <a-alert
      v-if="form.status === 'CONFIRMED' && form.lockedFlag"
      type="error"
      show-icon
      class="pending-edit-note"
      message="该订单已锁单：保存需要「锁单后改单」权限；普通用户请新建加单。"
    />
    <a-alert
      v-else-if="form.status === 'CONFIRMED'"
      type="warning"
      show-icon
      class="pending-edit-note"
      :message="
        (form.dispatchedAt ? '该订单已发车出库，保存需要「发车后改单」权限。' : '该订单已确认：') +
        '改单沿用原有的锁定单价重算金额，不静默改价；需称重的新增商品请新建加单。'
      "
    />
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="flatModel" :rules="formRules" layout="vertical" class="app-drawer-form">
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="客户" name="customerId" required>
              <CustomerSelect v-if="!form.orderId" v-model:value="form.customerId" :disabled="!!exchangeReturnId" @change="customerChanged" />
              <a-input v-else :value="form.customerNameSnapshot" disabled />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="订单来源" name="orderSource">
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
          </a-col>
          <a-col :span="12" v-if="form.orderSource === 'SUPPLEMENT'">
            <a-form-item label="关联原订单" name="originalOrderId">
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
          </a-col>
          <a-col :span="12" v-if="form.orderSource === 'SUPPLEMENT'">
            <a-form-item label="补单原因" name="supplementReason" required>
              <a-textarea v-model:value="form.supplementReason" :maxlength="500" :rows="2" show-count />
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="!form.orderId">
            <a-form-item label="配送仓库" name="deliveryWarehouseId">
              <WarehouseSelect v-model:value="warehouseValue" placeholder="组合商品 / 成本公式须明确选仓" width="100%" />
              <p class="scm-note">仅用于本次订单，选仓后重新计算该仓的成本公式价格。</p>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="期望配送时间" name="expectDeliveryTime">
              <a-date-picker v-model:value="form.expectDeliveryTime" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark">
              <a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" show-count />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="收货人" name="receiverName" required>
              <a-input v-model:value="form.address.receiverName" :disabled="!!form.orderId" maxlength="100" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="联系电话" name="receiverPhone" required class="app-drawer-field--compact">
              <a-input v-model:value="form.address.receiverPhone" :disabled="!!form.orderId" maxlength="32" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="收货地址" name="address" required class="app-drawer-field--wide">
              <a-input v-model:value="form.address.address" :disabled="!!form.orderId" maxlength="500" />
            </a-form-item>
          </a-col>
        </a-row>
        <ItemTable ref="itemTableRef" :items="form.items" :customer-id="form.customerId" @price="preview" />
        <div class="item-actions">
          <a-button :disabled="!form.customerId" @click="pasteOpen = true">整单粘贴</a-button>
          <a-button @click="preview" :loading="pricing">重新解析价格</a-button>
        </div>
        <p class="item-actions__hint">粘贴失败的行不会追加；也可继续用 Excel 导入整张送货单。</p>
        <OrderPasteModal v-model:open="pasteOpen" :customer-id="form.customerId" :existing-sku-ids="existingSkuIds" @append="appendPasted" />
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">关闭</a-button>
        <a-button v-if="!form.orderId && !exchangeReturnId" :loading="saving" :disabled="loading" @click="save(true)">保存草稿</a-button>
        <a-button v-if="!form.orderId && !exchangeReturnId" :loading="saving" :disabled="loading" @click="save(false, true)">
          创建并继续录单
        </a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save(false)">
          {{ exchangeReturnId ? '创建换货草稿' : form.orderId ? '保存修改' : '创建订单' }}
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {onBeforeRouteLeave} from 'vue-router';
import {message, Modal, type FormInstance} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {orderApi} from '/@/api/business/scm/order-api';
import {
  newOrder,
  nextOrderForCustomer,
  payload,
  validateOrder,
  fixed,
  draftKey,
  serializeDraft,
  applyDraft,
  fromHistory,
  type Draft,
} from '../order-form-model';
import {localSave, localRead, localRemove} from '/@/utils/local-util';
import {useUserStore} from '/@/store/modules/system/user';
import {orderError} from '../order-errors';
import type {Order, Id, Item} from '../order-types';
import ItemTable from './order-item-editable-table.vue';
import OrderPasteModal from './order-paste-modal.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const emit = defineEmits<{saved: []}>();
const form = ref<Order>(newOrder()),
  visible = ref(false),
  loading = ref(false),
  saving = ref(false),
  pricing = ref(false),
  error = ref(''),
  originals = ref<{value: Id; label: string}[]>([]);
const exchangeReturnId = ref<Id>();
const pasteOpen = ref(false);
const formRef = ref<FormInstance>();
const existingSkuIds = computed(() => form.value.items.map((item) => item.skuId).filter((skuId): skuId is Id => skuId != null && skuId !== ''));
/** 收货信息在 `form.address.*` 里，展平一份给表单校验用（顶层 `address` 就是详细地址）。 */
const flatModel = computed(() => ({...form.value, ...form.value.address}));
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
const userStore = useUserStore();
let requestId = 0;
let previewRequestId = 0;

/* 草稿只存本地、按登录用户隔离，不存解析价和版本。 */
function draftStorageKey() {
  return draftKey(userStore.employeeId);
}

function readDraft(): Draft | null {
  try {
    const raw = localRead(draftStorageKey());
    return raw ? (JSON.parse(raw) as Draft) : null;
  } catch {
    return null;
  }
}

function writeDraft(draft: Draft) {
  localSave(draftStorageKey(), JSON.stringify(draft));
}

function clearDraft() {
  localRemove(draftStorageKey());
}

async function open(id?: Id) {
  const current = ++requestId;
  exchangeReturnId.value = undefined;
  error.value = '';
  form.value = newOrder();
  visible.value = true;
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
    } catch (e) {
      if (current === requestId) error.value = orderError(e);
    } finally {
      if (current === requestId) loading.value = false;
    }
  } else {
    await promptRestoreDraft();
  }
}

/** 新建时若存在本地草稿，让用户选择恢复或丢弃；恢复后重新获取客户和价格。 */
async function promptRestoreDraft() {
  const draft = readDraft();
  if (!draft || (!draft.customerId && !draft.items.length)) return;
  Modal.confirm({
    title: '发现上次未提交的订单草稿',
    content: '是否恢复草稿内容？选择「丢弃」将清空并新建空白订单。',
    okText: '恢复',
    cancelText: '丢弃',
    maskClosable: false,
    // 抽屉晚于确认框挂载，同层级时可能盖住确认框，抬高一层保证能点
    zIndex: 9999,
    onOk: async () => {
      const restored = applyDraft(draft);
      form.value = restored;
      if (restored.customerId) {
        const supplementReason = restored.supplementReason;
        const originalOrderId = restored.originalOrderId;
        // customerChanged 会用客户默认地址覆盖并按当前价格重新解析，同时清空 originalOrderId
        await customerChanged(restored.customerId);
        form.value.supplementReason = supplementReason;
        form.value.originalOrderId = originalOrderId;
      } else {
        await preview();
      }
    },
    onCancel: () => clearDraft(),
  });
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
  if (opened) window.addEventListener('beforeunload', persistUnsavedDraft);
  else {
    window.removeEventListener('beforeunload', persistUnsavedDraft);
    itemTableRef.value?.closeRecentPopover();
  }
});

/** 只为未保存的新建单写草稿；已关闭或已保存的都不写。 */
function persistUnsavedDraft() {
  if (!visible.value) return;
  if (exchangeReturnId.value) return;
  if (!form.value.orderId && (form.value.customerId || form.value.items.length)) {
    writeDraft(serializeDraft(form.value));
  }
}

/** 关闭时只给未保存的新建单存草稿，编辑已有单不产生草稿。 */
function closeDrawer() {
  persistUnsavedDraft();
  requestId++;
  visible.value = false;
}

// 刷新、路由切换、卸载不走 closeDrawer，这里各补一次存草稿
onBeforeRouteLeave(() => persistUnsavedDraft());
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', persistUnsavedDraft);
  persistUnsavedDraft();
});

/** 复用历史订单：只复制基本信息，价格和可用性重新解析。 */
async function openFromHistory(id: Id) {
  const current = ++requestId;
  exchangeReturnId.value = undefined;
  error.value = '';
  visible.value = true;
  loading.value = true;
  try {
    const detail = (await orderApi.detail(id, {suppressGlobalErrorMessage: true})).data;
    if (current !== requestId) return;
    form.value = fromHistory(detail);
    await preview();
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

/** 整单粘贴只追加已匹配行；数量、改价原因等仍按正常订单校验。 */
function appendPasted(items: Item[]) {
  form.value.items.push(...items);
  message.success(`已追加 ${items.length} 行，正在按当前价格重新解析`);
  void preview();
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

async function save(draft = false, continueCreating = false) {
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
      message.success('换货草稿已创建，请按正常订单流程提交和确认');
    } else if (draft) {
      await orderApi.create(f);
      message.success('草稿已保存');
    } else {
      const result = await orderApi.createAndProgress(f);
      message.success(result.data.status === 'CONFIRMED' ? '订单已创建并确认' : '订单已创建，非标品等待称重后确认');
    }
    if (isNew && !exchangeReturnId.value) clearDraft();
    emit('saved');
    if (isNew && !exchangeReturnId.value && !draft && continueCreating && visible.value) {
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
/* 让明细区的「添加商品」和「重新解析价格」排在同一行、间距一致 */
.item-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  vertical-align: top;
}

.item-actions__hint {
  margin: 8px 0 0;
  color: var(--scm-text-secondary, #667085);
  font-size: 12px;
}

.app-drawer-form :deep(.add-line) {
  margin: 12px 12px 0 0;
}

.pending-edit-note {
  margin-bottom: 12px;
}
</style>
