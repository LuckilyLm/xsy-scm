<!--
  * 客户类型 新建 / 编辑 弹窗
  *
  * 来源：**W1 派生** —— 结构照抄 `views/business/scm/product/components/category-form-modal.vue`。
  * C（project-reference-examples/xsy-scm）**没有**客户类型管理页：它把客户类型做成前端硬编码枚举
  * （`CUSTOMER_TYPE_ENUM` = 1/2/3），因此没有可复制的源码。V2 的客户类型是可维护字典表
  * （`customer_type`），必须单独做一个 CRUD 页。
  *
  * 与 W1 分类弹窗的差异（剪枝 + 适配）：
  * - 去掉「上级分类」与「排序」：V2 客户类型是**平铺字典表**，没有层级也没有排序字段；
  * - 状态改用 V2 SmartEnum（`CUSTOMER_TYPE_STATUS_ENUM`）走 `SmartEnumSelect`，
  *   而不是 W1 分类用的普通数组 + `a-select :options=`（W1 既有偏差，不回头改 W1）；
  * - 提交前把 `typeCode` 去空白并大写，与后端 `CustomerTypeValidator.normalizeCode` 保持一致，
  *   避免用户输入 `group` 后第二次输入 `GROUP` 撞唯一索引却看不出原因。
-->
<template>
  <a-modal
      v-model:open="visible"
      :title="form.typeId ? '编辑客户类型' : '新增客户类型'"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="visible = false"
  >
    <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item label="类型编码" name="typeCode"
                   :rules="[{ required: true, whitespace: true, message: '请输入类型编码' }]">
        <a-input v-model:value="form.typeCode" :maxlength="64" placeholder="例如 GROUP"/>
        <div class="ant-form-item-extra">编码全局唯一，保存时自动转为大写</div>
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
import {customerError} from '../customer-errors';

const emit = defineEmits<{ saved: [] }>();

const visible = ref(false);
const saving = ref(false);
const error = ref('');
const formRef = ref<FormInstance>();

function defaults(): CustomerTypeForm {
  return {typeCode: '', name: '', status: 'ENABLED', typeId: undefined, version: undefined};
}

const form = reactive<CustomerTypeForm>(defaults());

/** 打开弹窗；传 `row` 即编辑模式，不传为新增。 */
async function open(row?: CustomerType) {
  Object.assign(form, defaults(), row ?? {});
  visible.value = true;
  error.value = '';
  await nextTick();
  formRef.value?.clearValidate();
}

async function save() {
  saving.value = true;
  error.value = '';
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
  } catch (e) {
    error.value = customerError(e);
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
