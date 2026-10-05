<template>
  <a-drawer v-model:open="visible" :title="form.spuId ? '编辑商品' : '新增商品'" :width="'min(920px, 96vw)'"
            :mask-closable="!saving" :closable="!saving" :destroy-on-close="true">
    <a-spin :spinning="loading">
      <div class="drawer-body">
        <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
          <template #action>
            <a-button v-if="loadFailed" size="small" @click="load(form.spuId)">重新加载</a-button>
          </template>
        </a-alert>
        <a-form v-if="!loadFailed" ref="formRef" :model="form" layout="vertical">
          <section class="scm-form-section">
            <div class="scm-form-section__head">
              <h3 class="scm-form-section__title">基础信息</h3>
            </div>
            <a-row :gutter="20">
              <a-col :xs="24" :sm="12">
                <a-form-item label="商品名称" name="name"
                             :rules="[{ required: true, whitespace: true, message: '请输入商品名称' }]">
                  <a-input v-model:value="form.name" :maxlength="150"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="商品编码" name="spuCode"
                             :rules="[{ required: true, whitespace: true, message: '请输入商品编码' }]">
                  <a-input v-model:value="form.spuCode" :maxlength="64"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="商品分类" name="categoryId"
                             :rules="[{ required: true, message: '请选择已启用的三级分类' }]">
                  <CategorySelect v-model:value="form.categoryId" :categories="categories" mode="product"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="别名" name="alias">
                  <a-input v-model:value="form.alias" :maxlength="150"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="商品状态" name="status">
                  <a-select v-model:value="form.status" :options="SHELF_STATUS_ENUM"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="主档状态" name="masterStatus">
                  <a-select v-model:value="form.masterStatus" :options="MASTER_STATUS_ENUM"/>
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="商品简介" name="description">
                  <a-textarea v-model:value="form.description" :maxlength="1000" :rows="2" show-count/>
                </a-form-item>
              </a-col>
            </a-row>
          </section>

          <section class="scm-form-section">
            <div class="scm-form-section__head">
              <h3 class="scm-form-section__title">商品属性</h3>
            </div>
            <a-row :gutter="20">
              <a-col :xs="24" :sm="12">
                <a-form-item label="助记码" name="mnemonicCode">
                  <a-input v-model:value="form.mnemonicCode" :maxlength="64"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="品牌" name="brandName">
                  <a-input v-model:value="form.brandName" :maxlength="100"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="产地" name="origin">
                  <a-input v-model:value="form.origin" :maxlength="100"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="储存方式" name="storageMethod">
                  <a-select v-model:value="form.storageMethod" :options="STORAGE_METHOD_ENUM" allow-clear
                            placeholder="未维护"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="保质期" name="shelfLifeDays">
                  <a-input-number v-model:value="form.shelfLifeDays" :min="0" :max="36500" :precision="0"
                                  addon-after="天" style="width: 100%" placeholder="未维护"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="采购预警" name="purchaseWarningDays">
                  <a-input-number v-model:value="form.purchaseWarningDays" :min="0" :max="365" :precision="0"
                                  addon-after="天" style="width: 100%" placeholder="未维护"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="损耗率" name="lossRate">
                  <a-input-number v-model:value="form.lossRate" :min="0" :max="100" :precision="4" :controls="false"
                                  addon-after="%" style="width: 100%"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="税率" name="taxRate">
                  <a-input-number v-model:value="form.taxRate" :min="0" :max="100" :precision="4" :controls="false"
                                  addon-after="%" style="width: 100%"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="开票品名" name="invoiceName">
                  <a-input v-model:value="form.invoiceName" :maxlength="100"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="税收分类编码" name="taxCategoryCode">
                  <a-input v-model:value="form.taxCategoryCode" :maxlength="32"/>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="12">
                <a-form-item label="是否免税" name="taxExempt">
                  <a-switch v-model:checked="form.taxExempt" checked-children="是" un-checked-children="否"/>
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="商品标签" name="tagIds">
                  <div class="tag-picker">
                    <a-tag v-for="tagId in form.tagIds" :key="tagId" closable class="tag-picker__tag"
                           @close="removeTag(tagId)">{{ tagLabel(tagId) }}</a-tag>
                    <a-popover v-model:open="tagPickerOpen" trigger="click" placement="bottomLeft"
                               :overlay-style="{width: '280px'}" @open-change="onTagPickerToggle">
                      <template #content>
                        <div class="tag-picker__panel">
                          <a-input v-model:value="tagKeyword" size="small" allow-clear placeholder="搜索标签"/>
                          <div class="tag-picker__list">
                            <a-checkbox v-for="option in filteredTagOptions" :key="option.value"
                                        :checked="form.tagIds.includes(option.value)"
                                        @change="toggleTag(option.value)">{{ option.label }}
                            </a-checkbox>
                            <p v-if="!filteredTagOptions.length" class="tag-picker__empty">没有匹配的标签</p>
                          </div>
                        </div>
                      </template>
                      <a-button size="small" type="dashed" class="scm-btn-secondary">
                        <PlusOutlined/>
                        添加标签
                      </a-button>
                    </a-popover>
                  </div>
                </a-form-item>
              </a-col>
            </a-row>
          </section>

          <section class="scm-form-section">
            <ImageUpload v-model="form.images" :can-edit="canEditImages" @uploading="uploading = $event">
              <template #head>
                <h3 class="scm-form-section__title">商品图片</h3>
                <span class="scm-form-section__hint">最多 20 张，可拖动排序。</span>
              </template>
            </ImageUpload>
          </section>

          <section class="scm-form-section">
            <div class="scm-form-section__head">
              <h3 class="scm-form-section__title">商品规格</h3>
              <span class="scm-form-section__hint">规格项用于描述不同商品规格；采购、销售与库存都按具体的商品规格记录。</span>
            </div>
            <SkuEditor v-model="form.skuList" :units="units"/>
          </section>
        </a-form>
      </div>
    </a-spin>
    <template #footer>
      <a-space style="float: right">
        <a-button :disabled="saving" @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading || loadFailed || uploading" @click="submit">
          保存商品
        </a-button>
      </a-space>
    </template>
  </a-drawer>
</template>
<script setup lang="ts">
import {computed, nextTick, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {FormInstance} from 'ant-design-vue';
import {PlusOutlined} from '@ant-design/icons-vue';
import {productApi} from '/@/api/business/scm/product-api';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {productTagApi, productUomApi} from '/@/api/business/scm/product-assistant-api';
import {useUserStore} from '/@/store/modules/system/user';
import {MASTER_STATUS_ENUM, SHELF_STATUS_ENUM, STORAGE_METHOD_ENUM} from '/@/constants/business/scm/product-const';
import type {
  ProductCategory,
  ProductForm,
  ProductId,
  ProductTag,
  ProductTagRef,
  ProductUom
} from '/@/types/business/scm/product';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import ImageUpload from './product-image-upload.vue';
import SkuEditor from './product-sku-editable-table.vue';
import {emptyProduct, productFormOf, validateProduct} from '../product-form-model';
import {productError} from '../product-errors';

const emit = defineEmits<{ saved: [] }>();
const form = ref<ProductForm>(emptyProduct()), categories = ref<ProductCategory[]>([]), formRef = ref<FormInstance>();
const units = ref<ProductUom[]>([]), tagChoices = ref<ProductTag[]>([]), boundTags = ref<ProductTagRef[]>([]);
const visible = ref(false), loading = ref(false), saving = ref(false), uploading = ref(false), error = ref(''),
    loadFailed = ref(false);
const tagPickerOpen = ref(false), tagKeyword = ref('');
// 停用标签仍要出现在下拉里：编辑只校验新增绑定，摘不掉就等于历史标签永远清不掉。
const tagOptions = computed(() => {
  const active = tagChoices.value.map((tag) => ({value: tag.tagId, label: tag.name}));
  const known = new Set(tagChoices.value.map((tag) => String(tag.tagId)));
  return [...active, ...boundTags.value.filter((tag) => !known.has(String(tag.tagId))).map((tag) => ({
    value: tag.tagId,
    label: `${tag.name}（已停用）`
  }))];
});
const filteredTagOptions = computed(() => {
  const keyword = tagKeyword.value.trim().toLowerCase();
  return keyword ? tagOptions.value.filter((option) => option.label.toLowerCase().includes(keyword)) : tagOptions.value;
});
const user = useUserStore();
const canEditImages = computed(() => user.administratorFlag || user.getPointList?.some((point: {
  webPerms: string
}) => point.webPerms === 'scm:product:image'));
let session = 0;

function tagLabel(tagId: ProductId) {
  return tagOptions.value.find((option) => String(option.value) === String(tagId))?.label ?? `#${tagId}`;
}

function removeTag(tagId: ProductId) {
  form.value.tagIds = form.value.tagIds.filter((id) => String(id) !== String(tagId));
}

function toggleTag(tagId: ProductId) {
  form.value.tagIds = form.value.tagIds.includes(tagId)
      ? form.value.tagIds.filter((id) => String(id) !== String(tagId))
      : [...form.value.tagIds, tagId];
}

function onTagPickerToggle(open: boolean) {
  if (open) tagKeyword.value = '';
}

async function load(id?: ProductId) {
  const current = ++session;
  loading.value = true;
  error.value = '';
  loadFailed.value = false;
  try {
    const [tree, detail, uomOptions, tagOptionsResponse] = await Promise.all([
      productCategoryApi.tree(), id ? productApi.detail(id) : Promise.resolve(undefined), productUomApi.options(), productTagApi.options(),
    ]);
    if (current !== session) return;
    categories.value = tree.data;
    units.value = uomOptions.data;
    tagChoices.value = tagOptionsResponse.data;
    boundTags.value = detail ? detail.data.tags : [];
    form.value = detail ? productFormOf(detail.data) : emptyProduct();
    await nextTick();
    formRef.value?.clearValidate();
  } catch (e) {
    if (current === session) {
      error.value = productError(e);
      loadFailed.value = true;
    }
  } finally {
    if (current === session) loading.value = false;
  }
}

function open(id?: ProductId) {
  form.value = {...emptyProduct(), spuId: id};
  visible.value = true;
  void load(id);
}

async function save() {
  error.value = validateProduct(form.value) || '';
  if (error.value) return;
  saving.value = true;
  try {
    const payload = JSON.parse(JSON.stringify(form.value)) as ProductForm;
    await (payload.spuId ? productApi.update(payload) : productApi.add(payload));
    message.success('商品已保存');
    visible.value = false;
    emit('saved');
  } catch (e) {
    error.value = productError(e);
  } finally {
    saving.value = false;
  }
}

defineExpose({open});

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  await save();
}
</script>
<style scoped>
/* 商品标签：已选项以 Tag 呈现，新增走弹出面板 */
.tag-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.tag-picker__tag {
  margin: 0;
}

.tag-picker__panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-picker__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow-y: auto;
}

.tag-picker__empty {
  margin: 0;
  font-size: 13px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}
</style>
