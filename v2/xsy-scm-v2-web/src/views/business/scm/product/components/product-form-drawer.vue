<template>
  <a-drawer v-model:open="visible" :title="form.spuId ? '编辑商品' : '新增商品'" :width="'min(1280px, 96vw)'" :mask-closable="!saving" :closable="!saving" :destroy-on-close="true">
    <a-spin :spinning="loading">
      <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10"><template #action><a-button v-if="loadFailed" size="small" @click="load(form.spuId)">重新加载</a-button></template></a-alert>
      <a-form v-if="!loadFailed" ref="formRef" :model="form" layout="vertical">
        <a-row :gutter="20">
          <a-col :xs="24" :md="12"><a-form-item label="商品名称" name="name" :rules="[{ required: true, whitespace: true, message: '请输入商品名称' }]"><a-input v-model:value="form.name" :maxlength="150" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item label="SPU 编码" name="spuCode" :rules="[{ required: true, whitespace: true, message: '请输入 SPU 编码' }]"><a-input v-model:value="form.spuCode" :maxlength="64" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item label="商品分类" name="categoryId" :rules="[{ required: true, message: '请选择已启用的三级分类' }]"><CategorySelect v-model:value="form.categoryId" :categories="categories" mode="product" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item label="别名" name="alias"><a-input v-model:value="form.alias" :maxlength="150" /></a-form-item></a-col>
          <a-col :xs="24" :md="12"><a-form-item label="商品状态" name="status"><a-select v-model:value="form.status" :options="SHELF_STATUS_ENUM" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="商品简介" name="description"><a-textarea v-model:value="form.description" :maxlength="1000" :rows="2" show-count /></a-form-item></a-col>
        </a-row>
        <a-divider orientation="left">商品图集</a-divider>
        <ImageUpload v-model="form.images" :can-edit="canEditImages" @uploading="uploading = $event" />
        <a-divider orientation="left">SKU 规格</a-divider><SkuEditor v-model="form.skuList" />
      </a-form>
    </a-spin>
    <template #footer><a-space style="float: right"><a-button :disabled="saving" @click="visible = false">取消</a-button><a-button type="primary" :loading="saving" :disabled="loading || loadFailed || uploading" @click="submit">保存商品</a-button></a-space></template>
  </a-drawer>
</template>
<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { FormInstance } from 'ant-design-vue';
import { productApi } from '/@/api/business/scm/product-api';
import { productCategoryApi } from '/@/api/business/scm/product-category-api';
import { useUserStore } from '/@/store/modules/system/user';
import { SHELF_STATUS_ENUM } from '/@/constants/business/scm/product-const';
import type { ProductCategory, ProductForm, ProductId } from '/@/types/business/scm/product';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import ImageUpload from './product-image-upload.vue';
import SkuEditor from './product-sku-editable-table.vue';
import { emptyProduct, validateProduct } from '../product-form-model';
import { productError } from '../product-errors';
const emit = defineEmits<{ saved: [] }>();
const form = ref<ProductForm>(emptyProduct()), categories = ref<ProductCategory[]>([]), formRef = ref<FormInstance>();
const visible = ref(false), loading = ref(false), saving = ref(false), uploading = ref(false), error = ref(''), loadFailed = ref(false);
const user = useUserStore();
const canEditImages = computed(() => user.administratorFlag || user.getPointList?.some((point: { webPerms: string }) => point.webPerms === 'scm:product:image'));
let session = 0;
async function load(id?: ProductId) {
  const current = ++session; loading.value = true; error.value = ''; loadFailed.value = false;
  try {
    const [tree, detail] = await Promise.all([productCategoryApi.tree(), id ? productApi.detail(id) : Promise.resolve(undefined)]);
    if (current !== session) return;
    categories.value = tree.data; form.value = detail ? structuredClone(detail.data) : emptyProduct();
    await nextTick(); formRef.value?.clearValidate();
  } catch (e) { if (current === session) { error.value = productError(e); loadFailed.value = true; } }
  finally { if (current === session) loading.value = false; }
}
function open(id?: ProductId) { form.value = { ...emptyProduct(), spuId: id }; visible.value = true; void load(id); }
async function save() {
  error.value = validateProduct(form.value) || ''; if (error.value) return;
  saving.value = true;
  try {
    const payload = JSON.parse(JSON.stringify(form.value)) as ProductForm;
    await (payload.spuId ? productApi.update(payload) : productApi.add(payload)); message.success('商品已保存'); visible.value = false; emit('saved');
  } catch (e) { error.value = productError(e); } finally { saving.value = false; }
}
defineExpose({ open });
async function submit() { try { await formRef.value?.validate(); } catch { return; } await save(); }
</script>
