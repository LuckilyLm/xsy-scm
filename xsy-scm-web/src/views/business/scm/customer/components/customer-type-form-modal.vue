<!--
  客户类型 新建 / 编辑 弹窗。
  提交前 `typeCode` 去空白并大写，与后端 `CustomerTypeValidator.normalizeCode` 保持一致 ——
  否则用户先输 `group` 再输 `GROUP` 会撞唯一索引却看不出原因。
-->
<template>
  <a-modal
      v-model:open="visible"
      :title="form.typeId ? '编辑客户类型' : '新增客户类型'"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="visible = false"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item name="typeCode"
                   :rules="[{ required: true, whitespace: true, message: '请输入类型编码' }]">
        <template #label>
          类型编码
          <ScmFieldHelp label="类型编码" text="编码需全局唯一，保存时自动转为大写"/>
        </template>
        <a-input v-model:value="form.typeCode" :maxlength="64" placeholder="例如 GROUP"/>
      </a-form-item>
      <a-form-item label="类型名称" name="name"
                   :rules="[{ required: true, whitespace: true, message: '请输入类型名称' }]">
        <a-input v-model:value="form.name" :maxlength="64"/>
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect v-model:value="form.status" enum-name="CUSTOMER_TYPE_STATUS_ENUM" width="100%"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {nextTick, reactive, ref} from 'vue';
import type {FormInstance} from 'ant-design-vue';
import {message} from 'ant-design-vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import {customerTypeApi} from '/@/api/business/scm/customer-type-api';
import type {CustomerType, CustomerTypeForm} from '/@/types/business/scm/customer';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();

function defaults(): CustomerTypeForm {
  return {typeCode: '', name: '', status: 'ENABLED', typeId: undefined, version: undefined};
}

const form = reactive<CustomerTypeForm>(defaults());

/** 打开弹窗；传 `row` 即编辑模式，不传为新增。 */
async function open(row?: CustomerType) {
  Object.assign(form, defaults(), row ?? {});
  visible.value = true;
  await nextTick();
  formRef.value?.clearValidate();
}

async function save() {
  saving.value = true;
  // 归一化后再提交：与后端 CustomerTypeValidator.normalizeCode / normalizeName 同构。
  const payload: CustomerTypeForm = {
    ...form,
    typeCode: (form.typeCode ?? '').trim().toUpperCase(),
    name: (form.name ?? '').trim(),
  };
  try {
    await (form.typeId ? customerTypeApi.update(payload) : customerTypeApi.add(payload));
    message.success('客户类型已保存');
    visible.value = false;
    emit('saved');
  } catch {
    // 保存失败只走全局 toast
  } finally {
    saving.value = false;
  }
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  await save();
}

defineExpose({open});
</script>
