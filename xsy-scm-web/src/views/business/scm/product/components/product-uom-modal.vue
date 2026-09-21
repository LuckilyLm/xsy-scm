<!-- 计量单位新增 / 编辑弹窗：编辑态只放开量纲、小数位、状态与排序，编码与名称留灰显示。 -->
<template>
  <a-modal v-model:open="visible" :title="form.uomId ? '编辑计量单位' : '新增计量单位'" :confirm-loading="saving"
           @ok="submit" @cancel="visible = false">
    <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10"/>
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="单位编码" name="uomCode"
                       :rules="[{ required: true, whitespace: true, message: '请输入单位编码' }]"
                       :help="form.uomId ? undefined : '如 UOM-KG，活动单位内唯一'">
            <a-input v-model:value="form.uomCode" :maxlength="64" :disabled="!!form.uomId"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="单位名称" name="name"
                       :rules="[{ required: true, whitespace: true, message: '请输入单位名称' }]"
                       :help="form.uomId ? '业务表按名称记账，改名会让历史数据失去真值来源' : '如 kg / 箱，活动单位内唯一'">
            <a-input v-model:value="form.name" :maxlength="32" :disabled="!!form.uomId"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="量纲" name="category" :rules="[{ required: true, message: '请选择量纲' }]">
            <a-select v-model:value="form.category" :options="UOM_CATEGORY_ENUM"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="建议小数位" name="precisionScale" help="只约束前端输入，不改变数据库精度">
            <a-input-number v-model:value="form.precisionScale" :min="0" :max="6" :precision="0" style="width: 100%"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="排序" name="sortOrder">
            <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 100%"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="状态" name="status" help="停用后既有商品照旧显示，只是新配置选不到">
            <a-select v-model:value="form.status" :options="ENABLE_STATUS_ENUM"/>
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {nextTick, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {FormInstance} from 'ant-design-vue';
import {productUomApi} from '/@/api/business/scm/product-assistant-api';
import type {AssistantStatus, ProductUom, UomCategory} from '/@/types/business/scm/product';
import {ENABLE_STATUS_ENUM, UOM_CATEGORY_ENUM} from '/@/constants/business/scm/product-const';
import {productError} from '../product-errors';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), saving = ref(false), error = ref(''), formRef = ref<FormInstance>();
// uomId 决定新增还是编辑；编码与名称只在新增时可填，编辑时后端表单根本没有这两个字段。
const form = reactive({
  uomId: undefined as ProductUom['uomId'] | undefined,
  version: 0,
  uomCode: '',
  name: '',
  category: 'WEIGHT' as UomCategory,
  precisionScale: 4,
  status: 'ENABLED' as AssistantStatus,
  sortOrder: 0
});

function open(row?: ProductUom) {
  Object.assign(form, {
        uomId: undefined,
        version: 0,
        uomCode: '',
        name: '',
        category: 'WEIGHT',
        precisionScale: 4,
        status: 'ENABLED',
        sortOrder: 0
      },
      row ? {
        uomId: row.uomId,
        version: row.version,
        uomCode: row.uomCode,
        name: row.name,
        category: row.category,
        precisionScale: row.precisionScale,
        status: row.status,
        sortOrder: row.sortOrder
      } : {});
  visible.value = true;
  error.value = '';
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  saving.value = true;
  error.value = '';
  try {
    if (form.uomId) await productUomApi.update({
      uomId: form.uomId,
      version: form.version,
      category: form.category,
      precisionScale: form.precisionScale,
      status: form.status,
      sortOrder: form.sortOrder
    });
    else await productUomApi.add({
      uomCode: form.uomCode,
      name: form.name,
      category: form.category,
      precisionScale: form.precisionScale,
      status: form.status,
      sortOrder: form.sortOrder
    });
    message.success('计量单位已保存');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = productError(e);
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
