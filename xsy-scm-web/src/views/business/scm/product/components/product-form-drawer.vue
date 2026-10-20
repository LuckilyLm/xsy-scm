<template>
  <a-drawer v-model:open="visible" :title="form.spuId ? '编辑商品' : '新增商品'" :width="scmDrawerWidth('l')"
            :mask-closable="!saving" :closable="!saving" :destroy-on-close="true">
    <a-spin :spinning="loading">
      <div class="product-form-body">
        <div ref="feedbackRef" aria-live="polite">
          <a-alert v-if="error" :message="error" type="error" show-icon class="smart-margin-bottom10">
            <template #action>
              <a-button v-if="loadFailed" size="small" @click="load(form.spuId)">重新加载</a-button>
            </template>
          </a-alert>
        </div>
        <a-form v-if="!loadFailed" ref="formRef" :model="form" layout="vertical"
                class="app-drawer-form product-form">
          <section class="product-form-section">
            <h3 class="product-form-title">基础信息</h3>
            <div class="product-form-grid">
              <div class="product-form-field">
                <a-form-item label="商品名称" name="name"
                             :rules="[{ required: true, whitespace: true, message: '请输入商品名称' }]">
                  <a-input v-model:value="form.name" :maxlength="150"/>
                </a-form-item>
              </div>
              <div class="product-form-field">
                <a-form-item label="商品分类" name="categoryId"
                             :rules="[{ required: true, message: '请选择已启用的三级分类' }]">
                  <CategorySelect v-model:value="form.categoryId" :categories="categories" mode="product"/>
                </a-form-item>
              </div>
              <div class="product-form-field">
                <a-form-item label="商品状态" name="status">
                  <a-select v-model:value="form.status" :options="SHELF_STATUS_ENUM"/>
                </a-form-item>
              </div>
              <div class="product-form-field">
                <a-form-item label="主档状态" name="masterStatus">
                  <a-select v-model:value="form.masterStatus" :options="MASTER_STATUS_ENUM"/>
                </a-form-item>
              </div>
            </div>
          </section>
          <section class="product-form-section">
            <div class="product-form-heading">
              <h3 class="product-form-title">商品规格</h3>
              <span class="product-form-hint">共 {{ form.skuList.length }} 个</span>
            </div>
            <SkuEditor v-model="form.skuList" :units="units"/>
          </section>
          <a-collapse v-model:active-key="expandedSections" :bordered="false" class="product-form-extra">
            <a-collapse-panel key="attributes" header="商品属性与简介" :force-render="true">
              <div class="product-form-grid product-form-grid--three">
                <div class="product-form-field">
                  <a-form-item label="别名" name="alias">
                    <a-input v-model:value="form.alias" :maxlength="150"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="助记码" name="mnemonicCode">
                    <a-input v-model:value="form.mnemonicCode" :maxlength="64"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="品牌" name="brandName">
                    <a-input v-model:value="form.brandName" :maxlength="100"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="产地" name="origin">
                    <a-input v-model:value="form.origin" :maxlength="100"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="储存方式" name="storageMethod">
                    <a-select v-model:value="form.storageMethod" :options="STORAGE_METHOD_ENUM" allow-clear
                              placeholder="未维护"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="保质期" name="shelfLifeDays">
                    <a-input-number v-model:value="form.shelfLifeDays" :min="0" :max="36500" :precision="0"
                                    addon-after="天" style="width: 100%" placeholder="未维护"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="损耗率" name="lossRate">
                    <a-input-number v-model:value="form.lossRate" :min="0" :max="100" :precision="4" :controls="false"
                                    addon-after="%" style="width: 100%"/>
                  </a-form-item>
                </div>
                <div class="product-form-field product-form-field--wide">
                  <a-form-item label="商品标签" name="tagIds" class="app-drawer-field--multi">
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
                </div>
                <div class="product-form-field product-form-field--wide">
                  <a-form-item label="商品简介" name="description" class="app-drawer-field--wide">
                    <a-textarea v-model:value="form.description" :maxlength="1000" :rows="2" show-count/>
                  </a-form-item>
                </div>
              </div>
            </a-collapse-panel>
            <a-collapse-panel key="images" :header="`商品图片（${form.images.length}）`" :force-render="true">
              <ImageUpload v-model="form.images" :can-edit="canEditImages" @uploading="uploading = $event">
                <template #head>
                  <ScmFieldHelp label="商品图片" text="最多上传 20 张，可拖动排序"/>
                </template>
              </ImageUpload>
            </a-collapse-panel>
            <a-collapse-panel key="tax" header="采购与税务" :force-render="true">
              <div class="product-form-grid product-form-grid--three">
                <div class="product-form-field">
                  <a-form-item label="采购预警" name="purchaseWarningDays">
                    <a-input-number v-model:value="form.purchaseWarningDays" :min="0" :max="365" :precision="0"
                                    addon-after="天" style="width: 100%" placeholder="未维护"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="开票品名" name="invoiceName">
                    <a-input v-model:value="form.invoiceName" :maxlength="100"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="税收分类编码" name="taxCategoryCode">
                    <a-input v-model:value="form.taxCategoryCode" :maxlength="32"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="税率" name="taxRate">
                    <a-input-number v-model:value="form.taxRate" :min="0" :max="100" :precision="4" :controls="false"
                                    addon-after="%" style="width: 100%"/>
                  </a-form-item>
                </div>
                <div class="product-form-field">
                  <a-form-item label="是否免税" name="taxExempt">
                    <a-switch v-model:checked="form.taxExempt" checked-children="是" un-checked-children="否"/>
                  </a-form-item>
                </div>
              </div>
            </a-collapse-panel>
          </a-collapse>
        </a-form>
      </div>
    </a-spin>
    <template #footer>
      <div class="product-form-footer">
        <a-button :disabled="saving" @click="visible = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="loading || loadFailed || uploading" @click="submit">
          保存商品
        </a-button>
      </div>
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
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import SkuEditor from './product-sku-editable-table.vue';
import {emptyProduct, productFormOf, validateProduct} from '../product-form-model';
import {productError} from '../product-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const emit = defineEmits<{ saved: [] }>();
const form = ref<ProductForm>(emptyProduct()), categories = ref<ProductCategory[]>([]), formRef = ref<FormInstance>();
const units = ref<ProductUom[]>([]), tagChoices = ref<ProductTag[]>([]), boundTags = ref<ProductTagRef[]>([]);
const visible = ref(false), loading = ref(false), saving = ref(false), uploading = ref(false), error = ref(''),
    loadFailed = ref(false);
const tagPickerOpen = ref(false), tagKeyword = ref('');
const expandedSections = ref<string[]>([]);
const feedbackRef = ref<HTMLElement>();
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
    // 加载失败由抽屉内 Alert 承担（loadFailed 决定是否给重试），不再让全局 toast 重复说一遍
    const [tree, detail, uomOptions, tagOptionsResponse] = await Promise.all([
      productCategoryApi.tree({suppressGlobalErrorMessage: true}),
      id ? productApi.detail(id, {suppressGlobalErrorMessage: true}) : Promise.resolve(undefined),
      productUomApi.options({suppressGlobalErrorMessage: true}),
      productTagApi.options({suppressGlobalErrorMessage: true}),
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
  expandedSections.value = [];
  visible.value = true;
  void load(id);
}

async function save() {
  if (saving.value) return;
  error.value = validateProduct(form.value) || '';
  if (error.value) {
    await nextTick();
    feedbackRef.value?.scrollIntoView({block: 'start'});
    return;
  }
  saving.value = true;
  try {
    const payload = JSON.parse(JSON.stringify(form.value)) as ProductForm;
    // 只提交业务属性；内部 SPU/SKU 编码由后端发号或原样保留。
    delete payload.spuCode;
    payload.skuList.forEach(sku => { delete sku.skuCode; });
    await (payload.spuId ? productApi.update(payload) : productApi.add(payload));
    message.success('商品已保存');
    visible.value = false;
    emit('saved');
  } catch {
    // 保存失败只走全局 toast；抽屉里的 error 留给本地校验与加载失败
  } finally {
    saving.value = false;
  }
}

defineExpose({open});

async function submit() {
  if (saving.value || loading.value || loadFailed.value || uploading.value) return;
  try {
    await formRef.value?.validate();
  } catch (validationError) {
    // 折叠区始终挂载；展开后再定位，避免校验失败的字段不可见。
    expandedSections.value = ['attributes', 'images', 'tax'];
    await nextTick();
    const firstField = (validationError as {errorFields?: {name: (string | number)[]}[]})?.errorFields?.[0];
    if (firstField) formRef.value?.scrollToField(firstField.name, {block: 'center'});
    return;
  }
  await save();
}
</script>
<style scoped>
.product-form-body {
  container-type: inline-size;
}

.product-form {
  --drawer-text-width: 100%;
  --drawer-select-width: 100%;
  --drawer-number-width: 100%;
}

.product-form-section {
  padding-bottom: 20px;
}

.product-form-section + .product-form-section {
  padding-top: 16px;
  border-top: 1px solid var(--scm-border);
}

.product-form-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--scm-text);
}

.product-form-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.product-form-hint {
  color: var(--scm-text-secondary);
  font-size: 13px;
}

.product-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.product-form-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.product-form-field {
  min-width: 0;
}

.product-form-field--wide {
  grid-column: 1 / -1;
}

.product-form-grid :deep(.ant-form-item) {
  margin-bottom: 0;
}

.product-form :deep(.ant-form-item-label) {
  padding-bottom: 4px;
}

.product-form :deep(.ant-form-item-control-input-content > .ant-select),
.product-form :deep(.ant-form-item-control-input-content > .ant-input-number-group-wrapper) {
  width: 100%;
}

.product-form-extra.ant-collapse {
  background: transparent;
  border-top: 1px solid var(--scm-border);
}

.product-form-extra :deep(.ant-collapse-header) {
  padding: 14px 0;
  font-weight: 600;
}

.product-form-extra :deep(.ant-collapse-content-box) {
  padding: 0 0 16px;
}

.product-form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@container (max-width: 680px) {
  .product-form-grid--three {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container (max-width: 480px) {
  .product-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

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
