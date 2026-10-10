<!-- 商品标签新增 / 编辑弹窗：编码与名称可改，改名不影响已绑定商品的展示。 -->
<template>
  <a-modal v-model:open="visible" :title="form.tagId ? '编辑商品标签' : '新增商品标签'" :confirm-loading="saving"
           @ok="submit" @cancel="visible = false">
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="标签编码" name="tagCode"
                       :rules="[{ required: true, whitespace: true, message: '请输入标签编码' }]">
            <a-input v-model:value="form.tagCode" :maxlength="64" placeholder="如 TAG-ORGANIC"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="标签名称" name="name"
                       :rules="[{ required: true, whitespace: true, message: '请输入标签名称' }]">
            <a-input v-model:value="form.name" :maxlength="64" placeholder="如 有机"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="排序" name="sortOrder">
            <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" style="width: 100%"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="状态" name="status">
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
import {productTagApi} from '/@/api/business/scm/product-assistant-api';
import type {AssistantStatus, ProductTag} from '/@/types/business/scm/product';
import {ENABLE_STATUS_ENUM} from '/@/constants/business/scm/product-const';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), saving = ref(false), formRef = ref<FormInstance>();
const form = reactive({
  tagId: undefined as ProductTag['tagId'] | undefined,
  version: 0,
  tagCode: '',
  name: '',
  status: 'ENABLED' as AssistantStatus,
  sortOrder: 0
});

function open(row?: ProductTag) {
  Object.assign(form, {
    tagId: row?.tagId,
    version: row?.version ?? 0,
    tagCode: row?.tagCode ?? '',
    name: row?.name ?? '',
    status: row?.status ?? 'ENABLED',
    sortOrder: row?.sortOrder ?? 0
  });
  visible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

async function save() {
  saving.value = true;
  try {
    // 新增不接受主键与版本，编辑必须带回乐观锁版本。
    if (form.tagId) await productTagApi.update({
      tagId: form.tagId,
      version: form.version,
      tagCode: form.tagCode,
      name: form.name,
      status: form.status,
      sortOrder: form.sortOrder
    });
    else await productTagApi.add({
      tagCode: form.tagCode,
      name: form.name,
      status: form.status,
      sortOrder: form.sortOrder
    });
    message.success('标签已保存');
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
