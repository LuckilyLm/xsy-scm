<template>
  <a-modal v-model:open="visible" :title="form.categoryId ? '编辑分类' : '新增分类'" :confirm-loading="saving" @ok="submit" @cancel="visible = false">
    <a-alert v-if="error" type="error" :message="error" show-icon class="smart-margin-bottom10" />
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item label="上级分类" name="parentId">
        <CategorySelect v-model:value="form.parentId" :categories="categories" mode="parent" :exclude-id="form.categoryId" />
        <div class="ant-form-item-extra">不选上级时创建一级分类</div>
      </a-form-item>
      <a-form-item label="分类编码" name="categoryCode" :rules="[{ required: true, whitespace: true, message: '请输入分类编码' }]">
        <a-input v-model:value="form.categoryCode" :maxlength="64" />
      </a-form-item>
      <a-form-item label="分类名称" name="name" :rules="[{ required: true, whitespace: true, message: '请输入分类名称' }]">
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-row :gutter="16">
        <a-col :span="12"><a-form-item label="排序" name="sortOrder"><a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" /></a-form-item></a-col>
        <a-col :span="12"><a-form-item label="状态" name="status"><a-select v-model:value="form.status" :options="ENABLE_STATUS_ENUM" /></a-form-item></a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { reactive, ref, nextTick } from 'vue';
import type { FormInstance } from 'ant-design-vue';
import { message } from 'ant-design-vue';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import { productCategoryApi } from '/@/api/business/scm/product-category-api';
import { ENABLE_STATUS_ENUM } from '/@/constants/business/scm/product-const';
import type { ProductCategory, ProductCategoryForm, ProductId } from '/@/types/business/scm/product';
import { productError } from '../product-errors';
defineProps<{ categories: ProductCategory[] }>();
const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), saving = ref(false), error = ref('');
const formRef = ref<FormInstance>();
const defaults = (): ProductCategoryForm => ({ categoryCode: '', name: '', sortOrder: 0, status: 'ENABLED', parentId: undefined, categoryId: undefined, version: undefined });
const form = reactive<ProductCategoryForm>(defaults());
async function open(row?: ProductCategory, parentId?: ProductId) {
  Object.assign(form, defaults(), row ?? {}, { parentId: row?.parentId ?? parentId });
  visible.value = true; error.value = ''; await nextTick(); formRef.value?.clearValidate();
}
async function save() {
  saving.value = true; error.value = '';
  try {
    const payload = { ...form, parentId: form.parentId ?? null };
    await (form.categoryId ? productCategoryApi.update(payload) : productCategoryApi.add(payload));
    message.success('分类已保存'); visible.value = false; emit('saved');
  } catch (e) { error.value = productError(e); } finally { saving.value = false; }
}
async function submit() { try { await formRef.value?.validate(); } catch { return; } await save(); }
defineExpose({ open });
</script>
