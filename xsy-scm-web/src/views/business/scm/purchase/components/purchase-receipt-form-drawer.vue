<!-- 采购收货单表单。草稿只能改备注，数量在确认收货时一次填写。
     表单里没有状态字段；采购单下拉只列可收货的单。 -->
<template>
  <a-drawer :title="form.id ? '编辑收货单备注' : '新建收货单'" :open="visible" :width="scmDrawerWidth('s')" @close="visible = false">
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <a-spin :spinning="loading">
      <a-form ref="formRef" :model="form" :rules="formRules" layout="vertical" class="app-drawer-form">
        <a-form-item name="purchaseOrderId">
          <template #label>
            采购单
            <ScmFieldHelp label="采购单" text="收货明细会按所选采购单自动带入" />
          </template>
          <a-select
            v-if="!form.id"
            v-model:value="form.purchaseOrderId"
            show-search
            allow-clear
            :filter-option="false"
            :loading="orderLoading"
            :options="orderOptions"
            placeholder="输入采购单号搜索（只列已提交 / 部分收货的单）"
            @search="loadOrders"
          />
          <a-input v-else :value="form.purchaseOrderNo" disabled />
        </a-form-item>

        <a-form-item v-if="!form.id" name="receiptMode">
          <template #label>
            入库方式
            <ScmFieldHelp label="入库方式" text="直接入库在确认收货后入账；仓库确认入库需再由仓库确认" />
          </template>
          <a-radio-group v-model:value="form.receiptMode">
            <a-radio value="DIRECT">直接入库</a-radio>
            <a-radio value="WAREHOUSE_CONFIRM">仓库确认入库</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="form.remark" :maxlength="500" :rows="2" show-count />
        </a-form-item>

        <a-descriptions v-if="form.id" bordered size="small" :column="1">
          <a-descriptions-item label="供应商">{{ form.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收货仓库">{{ form.warehouseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ SCM_RECEIPT_STATUS_ENUM[form.status ?? '']?.desc || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="入库方式">
            {{ SCM_RECEIPT_MODE_ENUM[form.receiptMode ?? '']?.desc || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="入库状态">
            {{ SCM_PUTAWAY_STATUS_ENUM[form.putawayStatus ?? '']?.desc || '—' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-form>
    </a-spin>

    <template #footer>
      <a-space>
        <a-button @click="visible = false">关闭</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading" @click="save">
          {{ form.id ? '保存备注' : '创建草稿' }}
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import {message, type FormInstance} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import {purchaseReceiptApi} from '/@/api/business/scm/purchase-receipt-api';
import {SCM_PUTAWAY_STATUS_ENUM, SCM_RECEIPT_MODE_ENUM, SCM_RECEIPT_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {Id, Receipt} from '../purchase-types';
import {purchaseError} from '../purchase-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

const emit = defineEmits<{saved: []; created: [id: Id]}>();

/** 只有已提交和部分收货的采购单可以收货。 */
const RECEIVABLE = ['SUBMITTED', 'PARTIALLY_RECEIVED'];

const form = ref<Receipt>({});
const formRef = ref<FormInstance>();
/** 必填项逐项校验：错误显示在输入框下方，不再用顶部一条汇总红条。 */
const formRules = {
  purchaseOrderId: [{required: true, message: '请选择采购单', trigger: 'change'}],
  receiptMode: [{required: true, message: '请选择入库方式', trigger: 'change'}],
};
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const orderLoading = ref(false);
const orderOptions = ref<{value: Id; label: string}[]>([]);
let requestId = 0;

async function loadOrders(keyword: string) {
  orderLoading.value = true;
  try {
    const r = await purchaseOrderApi.query({pageNum: 1, pageSize: 20, orderNo: keyword || undefined}, {suppressGlobalErrorMessage: true});
    orderOptions.value = r.data.list
      .filter((o) => RECEIVABLE.includes(o.status ?? ''))
      .map((o) => ({value: o.id!, label: `${o.orderNo}（${o.supplierName ?? ''}）`}));
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    orderLoading.value = false;
  }
}

async function open(id?: Id) {
  const current = ++requestId;
  error.value = '';
  form.value = {};
  visible.value = true;
  if (!id) {
    await loadOrders('');
    return;
  }
  loading.value = true;
  try {
    const r = await purchaseReceiptApi.detail(id, {suppressGlobalErrorMessage: true});
    if (current === requestId) {
      form.value = r.data;
    }
  } catch (e) {
    if (current === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (current === requestId) {
      loading.value = false;
    }
  }
}

async function save() {
  error.value = '';
  // 必填项走表单校验：错误显示在对应输入框下方
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  saving.value = true;
  try {
    if (form.value.id) {
      await purchaseReceiptApi.update({
        id: form.value.id,
        version: form.value.version!,
        remark: form.value.remark ?? null,
      });
      message.success('备注已保存');
    } else {
      const result = await purchaseReceiptApi.create({
        purchaseOrderId: form.value.purchaseOrderId!,
        receiptMode: form.value.receiptMode!,
        remark: form.value.remark ?? null,
      });
      message.success('草稿收货单已创建');
      if (result.data.id != null) emit('created', result.data.id);
    }
    visible.value = false;
    emit('saved');
  } catch {
    // 保存失败只走全局 toast；抽屉里的 error 留给加载失败
  } finally {
    saving.value = false;
  }
}

async function openForOrder(orderId: Id) {
  const current = ++requestId;
  error.value = '';
  form.value = {purchaseOrderId: orderId};
  visible.value = true;
  loading.value = true;
  try {
    const result = await purchaseOrderApi.detail(orderId);
    if (current !== requestId) return;
    orderOptions.value = [{value: orderId, label: result.data.orderNo ?? String(orderId)}];
    if (!RECEIVABLE.includes(result.data.status ?? '')) error.value = '该采购单当前不能登记收货';
  } catch (cause) {
    if (current === requestId) error.value = purchaseError(cause);
  } finally {
    if (current === requestId) loading.value = false;
  }
}

defineExpose({open, openForOrder});
</script>
