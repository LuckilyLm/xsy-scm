<!--
  * 库存调整单（报损报溢）表单
-->
<template>
  <a-drawer :title="form.adjustId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="调整类型" name="adjustType">
        <SmartEnumSelect enum-name="ADJUST_TYPE_ENUM" v-model:value="form.adjustType" width="100%" />
      </a-form-item>
      <a-form-item label="商品ID" name="productId">
        <a-input-number style="width: 100%" placeholder="请输入商品ID" v-model:value="form.productId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="规格ID" name="skuId">
        <a-input-number style="width: 100%" placeholder="请输入规格ID" v-model:value="form.skuId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="仓库ID" name="warehouseId">
        <a-input-number style="width: 100%" placeholder="单仓库模式可留空" v-model:value="form.warehouseId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="调整数量" name="quantity">
        <a-input-number style="width: 100%" placeholder="请输入调整数量（正数）" v-model:value="form.quantity" :min="0" />
      </a-form-item>
      <a-form-item label="调整重量" name="weight">
        <a-input-number style="width: 100%" placeholder="请输入调整重量kg（正数）" v-model:value="form.weight" :min="0" />
      </a-form-item>
      <a-form-item label="调整原因" name="reason">
        <a-textarea style="width: 100%" placeholder="请输入调整原因" v-model:value="form.reason" :rows="3" :maxlength="200" />
      </a-form-item>
    </a-form>
    <div
      :style="{
        position: 'absolute',
        right: 0,
        bottom: 0,
        width: '100%',
        borderTop: '1px solid #e9e9e9',
        padding: '10px 16px',
        background: '#fff',
        textAlign: 'right',
        zIndex: 1,
      }"
    >
      <a-button style="margin-right: 8px" @click="onClose">取消</a-button>
      <a-button type="primary" @click="onSubmit">提交</a-button>
    </div>
  </a-drawer>
</template>
<script setup lang="ts">
  import { ref, nextTick, reactive } from 'vue';
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { stockAdjustApi } from '/@/api/business/stock/stock-adjust-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import { ADJUST_TYPE_ENUM } from '/@/constants/business/erp/stock-const';

  const emit = defineEmits(['reloadList']);

  // 组件ref
  const formRef = ref();

  const formDefault = {
    adjustId: undefined,
    adjustType: ADJUST_TYPE_ENUM.LOSS.value,
    productId: undefined,
    skuId: undefined,
    warehouseId: undefined,
    quantity: undefined,
    weight: undefined,
    reason: '',
  };
  let form = reactive({ ...formDefault });

  const rules = {
    adjustType: [{ required: true, message: '请选择调整类型' }],
    productId: [{ required: true, message: '商品ID不能为空' }],
    skuId: [{ required: true, message: '规格ID不能为空' }],
    quantity: [{ required: true, message: '调整数量不能为空' }],
    weight: [{ required: true, message: '调整重量不能为空' }],
  };

  // 是否展示抽屉
  const visible = ref(false);

  function showDrawer(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.adjustId) {
      Object.assign(form, rowData);
    }
    visible.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function onClose() {
    Object.assign(form, formDefault);
    visible.value = false;
  }

  function onSubmit() {
    formRef.value
      .validate()
      .then(async () => {
        SmartLoading.show();
        try {
          if (form.adjustId) {
            await stockAdjustApi.update(form);
          } else {
            await stockAdjustApi.add(form);
          }
          message.success(`${form.adjustId ? '修改' : '添加'}成功`);
          onClose();
          emit('reloadList');
        } catch (error) {
          smartSentry.captureError(error);
        } finally {
          SmartLoading.hide();
        }
      })
      .catch(() => {
        message.error('参数验证错误，请仔细填写表单数据!');
      });
  }

  defineExpose({
    showDrawer,
  });
</script>
