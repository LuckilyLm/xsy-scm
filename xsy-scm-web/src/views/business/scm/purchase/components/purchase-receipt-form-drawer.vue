<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/purchase/purchase-receive-list.vue 的行内抽屉表单
复制日期：2026-09-16。Copy First + Adapt（C 把表单内嵌在列表页里，这里拆成独立组件）。
剪枝：C 的 `receiveQuantity` / `receiveWeight` / `unitPrice` / `receiveBy` / `receiveTime` / `directStock`
      —— **收货数量只在 `confirm` 一次性落库**（A16：草稿态不允许改数量，否则会出现
      「草稿数量」与「实际收货」两套真相）；`directStock` 属库存，W5 不做。
适配：**状态由命令驱动**（A16：表单里没有 `status`）、`version`（A8）、`/scm/purchase/receipt/**`（A6）、
      采购单选择器只列**可收货状态**、`a-form-item` 带 `name`（A29）、loading/error/retry（A27）。
验收：W5 单测、TS 棘轮与 Playwright。 -->
<template>
  <a-drawer
    :title="form.id ? '编辑收货单备注' : '新建收货单'"
    :open="visible"
    width="min(720px, 96vw)"
    @close="visible = false"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <a-spin :spinning="loading">
      <a-form :model="form" layout="vertical">
        <a-form-item label="采购单" name="purchaseOrderId" required>
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

        <a-alert
          v-if="!form.id"
          type="info"
          show-icon
          message="收货明细由服务端按采购单的全部活动行自动生成，无需手工添加"
        />

        <a-form-item label="备注" name="remark">
          <a-input v-model:value="form.remark" maxlength="500" />
        </a-form-item>

        <a-descriptions v-if="form.id" bordered size="small" :column="1">
          <a-descriptions-item label="供应商">{{ form.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="收货仓库">{{ form.warehouseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ SCM_RECEIPT_STATUS_ENUM[form.status ?? '']?.desc || '—' }}
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
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { purchaseOrderApi } from '/@/api/business/scm/purchase-order-api';
import { purchaseReceiptApi } from '/@/api/business/scm/purchase-receipt-api';
import { SCM_RECEIPT_STATUS_ENUM } from '/@/constants/business/scm/purchase-const';
import type { Id, Receipt } from '../purchase-types';
import { purchaseError } from '../purchase-errors';

const emit = defineEmits<{ saved: [] }>();

/** 只有这两种状态的采购单可以收货（其余一律 40991）。 */
const RECEIVABLE = ['SUBMITTED', 'PARTIALLY_RECEIVED'];

const form = ref<Receipt>({});
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const orderLoading = ref(false);
const orderOptions = ref<{ value: Id; label: string }[]>([]);
let requestId = 0;

async function loadOrders(keyword: string) {
  orderLoading.value = true;
  try {
    const r = await purchaseOrderApi.query({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined });
    orderOptions.value = r.data.list
      .filter((o) => RECEIVABLE.includes(o.status ?? ''))
      .map((o) => ({ value: o.id!, label: `${o.orderNo}（${o.supplierName ?? ''}）` }));
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
    const r = await purchaseReceiptApi.detail(id);
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
  if (!form.value.id && !form.value.purchaseOrderId) {
    error.value = '请选择采购单';
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
      await purchaseReceiptApi.create({
        purchaseOrderId: form.value.purchaseOrderId!,
        remark: form.value.remark ?? null,
      });
      message.success('草稿收货单已创建');
    }
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = purchaseError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>
