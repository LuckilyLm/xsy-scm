<template>
  <a-modal
    v-model:open="visible"
    :title="form.categoryId ? '编辑分类' : '新增分类'"
    :confirm-loading="saving"
    :ok-button-props="{disabled: uploading}"
    @ok="submit"
    @cancel="visible = false"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item name="parentId">
        <template #label>
          上级分类
          <ScmFieldHelp label="上级分类" text="不选上级时创建一级分类" />
        </template>
        <CategorySelect v-model:value="form.parentId" :categories="categories" mode="parent" :exclude-id="form.categoryId" />
      </a-form-item>
      <a-form-item label="分类名称" name="name" :rules="[{required: true, whitespace: true, message: '请输入分类名称'}]">
        <a-input v-model:value="form.name" :maxlength="100" />
      </a-form-item>
      <a-form-item label="小程序分类图标">
        <div class="category-icon-current">
          <a-image v-if="iconUrl" :src="iconUrl" :width="56" :height="56" style="object-fit: contain" />
          <span v-else>未设置，显示通用图标</span>
          <a-upload :accept="IMAGE_ACCEPT" :show-upload-list="false" :custom-request="uploadIcon" :disabled="uploading">
            <a-button :loading="uploading">上传图片</a-button>
          </a-upload>
          <a-button v-if="form.iconFileKey" type="link" :disabled="uploading" @click="clearIcon">清除</a-button>
        </div>
        <a-collapse ghost class="category-icon-library">
          <a-collapse-panel key="icons" header="选择现有图标">
            <div class="category-icon-presets" role="group" aria-label="分类图标">
              <button v-for="item in CATEGORY_ICON_PRESETS" :key="item.name" type="button" :disabled="uploading" @click="selectIcon(item)">
                <img :src="item.url" alt="" /><span>{{ item.name }}</span>
              </button>
            </div>
          </a-collapse-panel>
        </a-collapse>
        <span class="category-icon-help">各分类可独立设置；保存后首页与分类页同步使用，没有商品也显示入口。</span>
      </a-form-item>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="排序" name="sortOrder">
            <a-input-number v-model:value="form.sortOrder" :min="0" :precision="0" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="状态" name="status">
            <a-select v-model:value="form.status" :options="ENABLE_STATUS_ENUM" />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import {reactive, ref, nextTick} from 'vue';
import type {FormInstance} from 'ant-design-vue';
import {message, type UploadProps} from 'ant-design-vue';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import {IMAGE_ACCEPT, imageFileError} from '/@/constants/business/scm/image-const';
import {CATEGORY_ICON_PRESETS} from '/@/constants/business/scm/category-icon-const';
import CategorySelect from '/@/components/business/scm/product-category-tree-select/index.vue';
import {productCategoryApi} from '/@/api/business/scm/product-category-api';
import {ENABLE_STATUS_ENUM} from '/@/constants/business/scm/product-const';
import type {ProductCategory, ProductCategoryForm, ProductId} from '/@/types/business/scm/product';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';

defineProps<{categories: ProductCategory[]}>();
const emit = defineEmits<{saved: []}>();
const visible = ref(false),
  saving = ref(false);
const uploading = ref(false);
const iconUrl = ref('');
let editSession = 0;
const formRef = ref<FormInstance>();
const defaults = (): ProductCategoryForm => ({
  categoryCode: '',
  name: '',
  sortOrder: 0,
  status: 'ENABLED',
  parentId: undefined,
  categoryId: undefined,
  version: undefined,
  iconFileKey: null,
});
const form = reactive<ProductCategoryForm>(defaults());

async function open(row?: ProductCategory, parentId?: ProductId) {
  editSession++;
  iconUrl.value = row?.iconUrl || '';
  Object.assign(form, defaults(), row ?? {}, {parentId: row?.parentId ?? parentId});
  visible.value = true;
  await nextTick();
  formRef.value?.clearValidate();
}

async function save() {
  if (uploading.value) return;
  saving.value = true;
  try {
    const {categoryCode: _readOnly, ...values} = form;
    const payload = {...values, parentId: form.parentId ?? null};
    await (form.categoryId ? productCategoryApi.update(payload) : productCategoryApi.add(payload));
    message.success('分类已保存');
    visible.value = false;
    emit('saved');
  } catch {
    // 保存失败由全局 toast 提示
  } finally {
    saving.value = false;
  }
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, formRef.value);
    return;
  }
  await save();
}

defineExpose({open});

/** 取消编辑或切换分类后，旧上传结果不能覆盖新表单。 */
async function storeIcon(file: File, session = editSession) {
  const invalid = imageFileError(file);
  if (invalid) throw new Error(invalid);
  uploading.value = true;
  try {
    const body = new FormData();
    body.append('file', file);
    const result = await fileApi.uploadFile<{fileKey: string; fileUrl?: string}>(body, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value);
    const uploadedUrl = result.data.fileUrl || (await fileApi.getUrl(result.data.fileKey)).data;
    if (session === editSession && visible.value) {
      form.iconFileKey = result.data.fileKey;
      iconUrl.value = uploadedUrl;
    }
  } finally {
    uploading.value = false;
  }
}

const uploadIcon: UploadProps['customRequest'] = async (options) => {
  if (!(options.file instanceof File)) return;
  try {
    await storeIcon(options.file);
    options.onSuccess?.({});
  } catch (error) {
    message.error((error as Error).message || '图片上传失败');
    options.onError?.(error as Error);
  }
};

/** 预设原图同样经过文件服务，分类只保存正式文件标识。 */
async function selectIcon(item: {name: string; url: string}) {
  const session = editSession;
  uploading.value = true;
  try {
    const response = await fetch(item.url);
    if (!response.ok) throw new Error('图标加载失败，请重试');
    await storeIcon(new File([await response.blob()], `分类图标-${item.name}.png`, {type: 'image/png'}), session);
  } catch (error) {
    message.error((error as Error).message || '图标选择失败');
  } finally {
    uploading.value = false;
  }
}
function clearIcon() {
  form.iconFileKey = null;
  iconUrl.value = '';
}
</script>
<style scoped>
.category-icon-current {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.category-icon-current > span,
.category-icon-help {
  color: var(--scm-text-secondary);
  font-size: 12px;
}
.category-icon-presets {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}
.category-icon-presets button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 2px;
  border: 1px solid var(--scm-line);
  border-radius: 8px;
  background: var(--scm-surface);
  cursor: pointer;
  font-size: 12px;
}
.category-icon-presets img {
  width: 48px;
  height: 48px;
  object-fit: contain;
}
.category-icon-presets button:hover,
.category-icon-presets button:focus-visible {
  border-color: var(--scm-primary);
}
.category-icon-presets button:disabled {
  opacity: 0.5;
  cursor: wait;
}
</style>
