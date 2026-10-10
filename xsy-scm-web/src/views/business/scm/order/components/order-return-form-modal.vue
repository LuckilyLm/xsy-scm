<template>
  <a-modal :open="visible" title="申请退货" :confirm-loading="saving" @ok="save" @cancel="visible=false" width="760px">
    <a-alert v-if="error" :message="error" type="error"/>
    <a-form ref="formRef" :model="{reason}" :rules="formRules" layout="vertical">
      <a-form-item label="退货原因" name="reason">
        <a-input v-model:value="reason" maxlength="500"/>
      </a-form-item>
      <a-table :data-source="rows" :columns="columns" :pagination="false" row-key="itemId">
        <template #bodyCell="{record,column}">
          <template v-if="column.dataIndex==='quantity'">
            <a-input-number v-model:value="record.quantity" string-mode :precision="4" :min="'0'"
                            :max="record.actualQuantity" aria-label="申请退货数量"/>
          </template>
        </template>
      </a-table>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {orderReturnApi} from '/@/api/business/scm/order-return-api';
import type {Order, Item, Id} from '../order-types';
import {fixed} from '../order-form-model';
import {orderError} from '../order-errors';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), saving = ref(false), reason = ref(''), error = ref(''), orderId = ref<Id>(),
    rows = ref<(Item & { quantity: string })[]>([]);
const formRef = ref();
/** 必填项逐项校验：错误显示在输入框下方，不再用顶部一条汇总红条。 */
const formRules = {reason: [{required: true, message: '请输入退货原因', trigger: 'blur'}]};
const columns = [{title: '商品', dataIndex: 'productNameSnapshot'}, {
  title: '实数量',
  dataIndex: 'actualQuantity',
  align: 'right'
}, {title: '本次申请数量', dataIndex: 'quantity', align: 'right'}];

function open(order: Order) {
  orderId.value = order.orderId;
  rows.value = order.items.map(i => ({...i, quantity: '0.0000'}));
  reason.value = '';
  error.value = '';
  visible.value = true;
}

async function save() {
  // 必填项走表单校验：错误显示在输入框下方
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  error.value = '';
  const items = rows.value.filter(i => Number(i.quantity) > 0).map(i => ({
    orderItemId: i.itemId,
    requestedQuantity: fixed(i.quantity)
  }));
  if (!items.length) {
    error.value = '至少选择一件退货商品';
    return;
  }
  saving.value = true;
  try {
    await orderReturnApi.create({orderId: orderId.value, reason: reason.value, items});
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});
</script>
