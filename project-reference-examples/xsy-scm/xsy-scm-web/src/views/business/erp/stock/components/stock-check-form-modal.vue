<!--
  * 库存盘点单表单
-->
<template>
  <a-drawer :title="form.checkId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="仓库ID" name="warehouseId">
        <a-input-number style="width: 100%" placeholder="请输入仓库ID" v-model:value="form.warehouseId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="盘点类型" name="checkType">
        <SmartEnumSelect enum-name="CHECK_TYPE_ENUM" v-model:value="form.checkType" width="100%" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="CHECK_STATUS_ENUM" v-model:value="form.status" width="100%" />
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
  import { stockCheckApi } from '/@/api/business/stock/stock-check-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import { CHECK_STATUS_ENUM, CHECK_TYPE_ENUM } from '/@/constants/business/erp/stock-const';

  const emit = defineEmits(['reloadList']);

  // 组件ref
  const formRef = ref();

  const formDefault = {
    checkId: undefined,
    warehouseId: undefined,
    checkType: CHECK_TYPE_ENUM.FULL.value,
    status: CHECK_STATUS_ENUM.PENDING.value,
  };
  let form = reactive({ ...formDefault });

  const rules = {
    warehouseId: [{ required: true, message: '仓库ID不能为空' }],
    checkType: [{ required: true, message: '请选择盘点类型' }],
  };

  // 是否展示抽屉
  const visible = ref(false);

  function showDrawer(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.checkId) {
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
          if (form.checkId) {
            await stockCheckApi.update(form);
          } else {
            await stockCheckApi.add(form);
          }
          message.success(`${form.checkId ? '修改' : '添加'}成功`);
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
