<template>
  <div class="image-upload" :style="themeVars">
    <div class="image-upload__head">
      <slot name="head"/>
      <div class="image-upload__actions">
        <a-upload v-if="canEdit" accept="image/png,image/jpeg,image/webp,image/gif" :show-upload-list="false"
                  :custom-request="upload" :disabled="uploading || modelValue.length >= 20">
          <a-button v-privilege="'scm:product:image'" class="btn-secondary" :loading="uploading">上传图片</a-button>
        </a-upload>
      </div>
    </div>
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <div v-if="modelValue.length" class="images">
      <figure v-for="(image, index) in modelValue" :key="image.fileKey" class="image-item" :draggable="canEdit"
              @dragstart="dragIndex = index" @dragover.prevent @drop.prevent="move(dragIndex, index)">
        <div class="image-item__frame">
          <a-image :src="image.fileUrl" :width="112" :height="112" :alt="image.fileName || `商品图 ${index + 1}`"/>
          <span v-if="image.primaryFlag" class="image-item__badge">主图</span>
        </div>
        <div v-if="canEdit" class="image-item__actions">
          <a-button v-if="!image.primaryFlag" size="small" class="btn-tertiary" @click="setPrimary(index)">
            设为主图
          </a-button>
          <a-button size="small" class="btn-tertiary image-item__icon" :disabled="index === 0"
                    :aria-label="`将图片 ${index + 1} 前移`" @click="move(index, index - 1)">
            <ArrowLeftOutlined/>
          </a-button>
          <a-button size="small" class="btn-tertiary image-item__icon" :disabled="index === modelValue.length - 1"
                    :aria-label="`将图片 ${index + 1} 后移`" @click="move(index, index + 1)">
            <ArrowRightOutlined/>
          </a-button>
          <a-popconfirm title="从商品图集中移除此图片？" @confirm="remove(index)">
            <a-button size="small" class="btn-icon-danger image-item__icon" :aria-label="`移除图片 ${index + 1}`">
              <DeleteOutlined/>
            </a-button>
          </a-popconfirm>
        </div>
      </figure>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import type { CSSProperties } from 'vue';
import { theme } from 'ant-design-vue';
import type { UploadProps } from 'ant-design-vue';
import { ArrowLeftOutlined, ArrowRightOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { fileApi } from '/@/api/support/file-api';
import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';
import type { ProductImage, ScmResponse } from '/@/types/business/scm/product';
import { productError } from '../product-errors';
const props = defineProps<{ modelValue: ProductImage[]; canEdit: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [images: ProductImage[]]; uploading: [busy: boolean] }>();
const uploading = ref(false), error = ref(''), dragIndex = ref(-1);
// 主题色挂在本组件根节点上，按钮的浅色底/描边从这里取
const {useToken} = theme;
const {token} = useToken();
const themeVars = computed<CSSProperties>(() => ({
  '--pf-primary': token.value.colorPrimary,
  '--pf-primary-bg': token.value.colorPrimaryBg,
  '--pf-primary-border': token.value.colorPrimaryBorder,
  '--pf-primary-bg-hover': token.value.colorPrimaryBgHover,
  '--pf-fill': token.value.colorFillTertiary,
  '--pf-error': token.value.colorError,
  '--pf-error-bg': token.value.colorErrorBg,
  '--pf-error-border': token.value.colorErrorBorder,
}));
const upload: UploadProps['customRequest'] = async options => {
  if (!props.canEdit || !(options.file instanceof File)) return;
  uploading.value = true;
  emit('uploading', true);
  error.value = '';
  try {
    const data = new FormData(); data.append('file', options.file);
    const response = await fileApi.uploadFile(data, FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value) as unknown as ScmResponse<{ fileKey: string; fileUrl: string; fileName?: string; fileSize?: number }>;
    if (!props.modelValue.some(image => image.fileKey === response.data.fileKey)) emit('update:modelValue', [...props.modelValue, { ...response.data, fileName: response.data.fileName || options.file.name, primaryFlag: props.modelValue.length === 0, sortOrder: props.modelValue.length }]);
    options.onSuccess?.(response.data);
  } catch (e) {
    error.value = productError(e);
    options.onError?.(new Error(error.value));
  } finally {
    uploading.value = false;
    emit('uploading', false);
  }
};

function setPrimary(index: number) {
  emit('update:modelValue', props.modelValue.map((image, i) => ({...image, primaryFlag: i === index})));
}

function move(from: number, to: number) {
  if (!props.canEdit || from < 0 || to < 0 || to >= props.modelValue.length) return;
  const images = [...props.modelValue];
  images.splice(to, 0, images.splice(from, 1)[0]);
  emit('update:modelValue', images.map((image, i) => ({...image, sortOrder: i})));
  dragIndex.value = -1;
}

function remove(index: number) {
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index).map((image, i) => ({
    ...image,
    sortOrder: i
  })));
}
</script>
<style scoped>
.image-upload {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 标题 + 说明（由抽屉通过 #head 传入）与上传按钮排在同一行 */
.image-upload__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.image-upload__actions {
  margin-left: auto;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.image-item {
  margin: 0;
  width: 148px;
}

.image-item__frame {
  position: relative;
  width: 112px;
  height: 112px;
}

.image-item__badge {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: #fff;
  background: var(--pf-primary);
  border-radius: 4px;
}

/* 操作横向排列，尺寸克制，不与图片本身抢视觉 */
.image-item__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}

.image-item__icon {
  width: 24px;
  height: 24px;
  padding: 0;
}

.btn-secondary {
  color: var(--pf-primary);
  background: var(--pf-primary-bg);
  border-color: var(--pf-primary-border);
}

.btn-secondary:hover,
.btn-secondary:focus {
  color: var(--pf-primary);
  background: var(--pf-primary-bg-hover);
  border-color: var(--pf-primary);
}

.btn-tertiary {
  color: var(--ant-color-text, rgba(0, 0, 0, 0.88));
  background: var(--pf-fill);
  border-color: transparent;
}

.btn-tertiary:hover,
.btn-tertiary:focus {
  color: var(--pf-primary);
  background: var(--pf-primary-bg);
  border-color: transparent;
}

.btn-tertiary:disabled {
  color: var(--ant-color-text-disabled, rgba(0, 0, 0, 0.25));
  background: var(--pf-fill);
}

.btn-icon-danger {
  color: var(--pf-error);
  background: var(--pf-error-bg);
  border-color: transparent;
}

.btn-icon-danger:hover,
.btn-icon-danger:focus {
  color: var(--pf-error);
  background: var(--pf-error-border);
  border-color: transparent;
}
</style>
