<template>
  <a-drawer :title="form.orderId?'编辑销售订单':'新建销售订单'" :open="visible" width="min(1200px, 96vw)"
            @close="closeDrawer">
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <a-spin :spinning="loading">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="20">
          <a-col :span="12">
            <a-form-item label="客户" name="customerId" required>
              <CustomerSelect v-if="!form.orderId" v-model:value="form.customerId" @change="customerChanged"/>
              <a-input v-else :value="form.customerNameSnapshot" disabled/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="订单来源" name="orderSource">
              <a-select v-model:value="form.orderSource"
                        :options="[{value:'ADMIN',label:'后台录单'},{value:'SUPPLEMENT',label:'补单'}]"
                        style="width:100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.orderSource==='SUPPLEMENT'">
            <a-form-item label="关联原订单" name="originalOrderId">
              <a-select v-model:value="form.originalOrderId" show-search allow-clear :filter-option="false"
                        :options="originals" @search="loadOriginals" placeholder="搜索已确认订单号"/>
            </a-form-item>
          </a-col>
          <a-col :span="12" v-if="form.orderSource==='SUPPLEMENT'">
            <a-form-item label="补单原因" name="supplementReason" required>
              <a-input v-model:value="form.supplementReason" maxlength="500"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="期望配送时间" name="expectDeliveryTime">
              <a-date-picker v-model:value="form.expectDeliveryTime" show-time value-format="YYYY-MM-DDTHH:mm:ssZ"
                             style="width:100%"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="备注" name="remark">
              <a-input v-model:value="form.remark" maxlength="500"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="收货人" name="receiverName" required>
              <a-input v-model:value="form.address.receiverName" :disabled="!!form.orderId" maxlength="100"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="联系电话" name="receiverPhone" required>
              <a-input v-model:value="form.address.receiverPhone" :disabled="!!form.orderId" maxlength="32"/>
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="收货地址" name="address" required>
              <a-input v-model:value="form.address.address" :disabled="!!form.orderId" maxlength="500"/>
            </a-form-item>
          </a-col>
        </a-row>
        <ItemTable ref="itemTableRef" :items="form.items" :customer-id="form.customerId" @price="preview"/>
        <a-button @click="preview" :loading="pricing">重新解析价格</a-button>
      </a-form>
    </a-spin>
    <template #footer>
      <a-space>
        <a-button @click="closeDrawer">关闭</a-button>
        <a-button v-if="!form.orderId" :loading="saving" :disabled="loading" @click="save(true)">保存草稿</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save(false)">
          {{ form.orderId ? '保存修改' : '创建订单' }}
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';
import {onBeforeRouteLeave} from 'vue-router';
import {message, Modal} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {orderApi} from '/@/api/business/scm/order-api';
import {newOrder, payload, validateOrder, fixed, draftKey, serializeDraft, applyDraft, fromHistory, type Draft} from '../order-form-model';
import {localSave, localRead, localRemove} from '/@/utils/local-util';
import {useUserStore} from '/@/store/modules/system/user';
import {orderError} from '../order-errors';
import type {Order, Id} from '../order-types';
import ItemTable from './order-item-editable-table.vue';

const emit = defineEmits<{ saved: [] }>();
const form = ref<Order>(newOrder()), visible = ref(false), loading = ref(false), saving = ref(false),
    pricing = ref(false), error = ref(''), originals = ref<{ value: Id; label: string }[]>([]);
const userStore = useUserStore();
let requestId = 0;

/* 草稿只存本地、按登录用户隔离（§7.3）；解析价 / version / 快照一律不落，恢复后重新请求。 */
function draftStorageKey() {
  return draftKey(userStore.employeeId);
}

function readDraft(): Draft | null {
  try {
    const raw = localRead(draftStorageKey());
    return raw ? JSON.parse(raw) as Draft : null;
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
  requestId++;
  error.value = '';
  form.value = newOrder();
  visible.value = true;
  if (id) {
    loading.value = true;
    try {
      form.value = (await orderApi.detail(id)).data;
      form.value.items.forEach(i => {
        i.unitPrice = i.manualPriceOverride ? i.draftUnitPrice : null;
        i.overrideReason = i.manualPriceReason;
      });
    } catch (e) {
      error.value = orderError(e);
    } finally {
      loading.value = false;
    }
  } else {
    await promptRestoreDraft();
  }
}

/**
 * 打开新建时若存在本地草稿，弹窗让用户选择恢复或丢弃（§7.3）。
 * 恢复后必须重新请求客户 / 价格：草稿只存用户录入，不含解析价、快照与 version。
 */
async function promptRestoreDraft() {
  const draft = readDraft();
  if (!draft || (!draft.customerId && !draft.items.length)) return;
  Modal.confirm({
    title: '发现上次未提交的订单草稿',
    content: '是否恢复草稿内容？选择「丢弃」将清空并新建空白订单。',
    okText: '恢复',
    cancelText: '丢弃',
    maskClosable: false,
    // 本次会话首次打开时，抽屉容器在 Vue 异步更新后才挂到 body，可能排在确认框之后并以同级的 z-index 1000 盖住它；
    // 抬高一层保证「恢复 / 丢弃」始终可点（与仓库内浮于抽屉之上的对话框取同一量级）。
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
    onCancel: () => clearDraft()
  });
}

/** 明细表暴露的收起价签入口；只用到这一个契约，不依赖组件实例的具体类型。 */
const itemTableRef = ref<{ closeRecentPopover: () => void } | null>(null);

// 关闭路径不止一条（关闭按钮、Esc、创建成功自动关），所以在 visible 落 false 时统一收起，
// 而不是在各处分别补调用。
watch(visible, opened => {
  if (opened) window.addEventListener('beforeunload', persistUnsavedDraft);
  else {
    window.removeEventListener('beforeunload', persistUnsavedDraft);
    itemTableRef.value?.closeRecentPopover();
  }
});

/** 尚未保存的新建单才落草稿；抽屉已关闭时不写，避免创建成功后又把已保存的单写回草稿。 */
function persistUnsavedDraft() {
  if (!visible.value) return;
  if (!form.value.orderId && (form.value.customerId || form.value.items.length)) {
    writeDraft(serializeDraft(form.value));
  }
}

/** 关闭 Drawer：仅对「尚未保存的新建单」落本地草稿，编辑既有单不产生草稿。 */
function closeDrawer() {
  persistUnsavedDraft();
  visible.value = false;
}

// 刷新、路由切换、组件卸载都不经过 closeDrawer()，所以离页各补一次落草稿。
onBeforeRouteLeave(() => persistUnsavedDraft());
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', persistUnsavedDraft);
  persistUnsavedDraft();
});

/**
 * 历史订单复用（§7.4）：读取历史单，仅复制允许字段构造新的新增草稿，价格 / 可用性重新解析。
 * 不进入草稿恢复提示，也不复用历史价 / 状态 / version。
 */
async function openFromHistory(id: Id) {
  requestId++;
  error.value = '';
  visible.value = true;
  loading.value = true;
  try {
    const detail = (await orderApi.detail(id)).data;
    form.value = fromHistory(detail);
    await preview();
  } catch (e) {
    error.value = orderError(e);
  } finally {
    loading.value = false;
  }
}

async function customerChanged(id?: Id) {
  if (!id) return;
  const current = ++requestId;
  try {
    const c = (await customerApi.detail(id)).data;
    if (current !== requestId) return;
    form.value.address = {
      receiverName: c.contactName ?? '',
      receiverPhone: c.contactPhone ?? '',
      address: c.address ?? ''
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
  const skuIds = form.value.items.flatMap(i => i.skuId ? [i.skuId] : []);
  if (!customerId || !skuIds.length) return;
  pricing.value = true;
  try {
    const r = await orderApi.preview({customerId, skuIds});
    for (const i of form.value.items) {
      const p = r.data.items.find(p => String(p.skuId) === String(i.skuId));
      i.draftUnitPrice = p?.unitPrice ?? null;
      i.draftPriceSource = p?.priceSource ?? null;
    }
  } catch (e) {
    error.value = orderError(e);
  } finally {
    pricing.value = false;
  }
}

async function loadOriginals(keyword: string) {
  try {
    const r = await orderApi.query({
      pageNum: 1,
      pageSize: 20,
      status: 'CONFIRMED',
      customerId: form.value.customerId,
      keyword
    });
    originals.value = r.data.list.map(o => ({value: o.orderId!, label: o.orderNo!}));
  } catch (e) {
    error.value = orderError(e);
  }
}

async function save(draft = false) {
  error.value = '';
  try {
    form.value.items.forEach(i => {
      i.orderedQuantity = fixed(i.orderedQuantity);
      if (i.unitPrice != null) i.unitPrice = fixed(i.unitPrice);
    });
    const invalid = validateOrder(form.value);
    if (invalid) {
      error.value = invalid;
      return;
    }
    saving.value = true;
    const f = payload(form.value);
    const isNew = !f.orderId;
    if (f.orderSource !== 'SUPPLEMENT') {
      f.originalOrderId = null;
      f.supplementReason = null;
    }
    if (f.orderId) {
      await orderApi.update(f);
      message.success('订单修改已保存');
    } else if (draft) {
      await orderApi.create(f);
      message.success('草稿已保存');
    } else {
      const result = await orderApi.createAndProgress(f);
      message.success(result.data.status === 'CONFIRMED' ? '订单已创建并确认' : '订单已创建，非标品等待称重后确认');
    }
    if (isNew) clearDraft();
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open, openFromHistory});
</script>
