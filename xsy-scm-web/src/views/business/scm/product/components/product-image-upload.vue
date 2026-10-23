<template>
  <div class="image-upload">
    <div class="image-upload__head">
      <slot name="head" />
      <div class="image-upload__actions">
        <a-upload
          v-if="canEdit"
          accept="image/png,image/jpeg,image/webp,image/gif"
          :show-upload-list="false"
          :custom-request="upload"
          :disabled="uploading || modelValue.length >= 20"
        >
          <a-button v-privilege="'scm:product:image'" class="scm-btn-secondary" :loading="uploading">上传图片</a-button>
        </a-upload>
      </div>
    </div>
    <a-alert v-if="uploadError" type="error" :message="uploadError" show-icon closable @close="uploadError = ''" />
    <a-alert v-if="hasBrokenPreview" type="warning" message="部分图片无法预览，请检查图片访问地址或网络连接。" show-icon />
    <div v-if="modelValue.length" class="images">
      <figure
        v-for="(image, index) in modelValue"
        :key="image.fileKey"
        class="image-item"
        :draggable="canEdit"
        @dragstart="dragIndex = index"
        @dragover.prevent
        @drop.prevent="move(dragIndex, index)"
      >
        <div class="image-item__frame">
          <a-image
            v-if="image.fileUrl && !failedPreviews.has(image.fileKey)"
            :src="image.fileUrl"
            :width="112"
            :height="112"
            :alt="image.fileName || `商品图 ${index + 1}`"
            @error="markPreviewFailed(image.fileKey)"
          />
          <div v-else class="image-item__fallback">图片无法预览</div>
          <span v-if="image.primaryFlag" class="image-item__badge">主图</span>
        </div>
        <div v-if="canEdit" class="image-item__actions">
          <a-button v-if="!image.primaryFlag" size="small" class="scm-btn-tertiary" @click="setPrimary(index)"> 设为主图 </a-button>
          <a-button
            size="small"
            class="scm-btn-tertiary scm-btn-icon"
            :disabled="index === 0"
            :aria-label="`将图片 ${index + 1} 前移`"
            @click="move(index, index - 1)"
          >
            <ArrowLeftOutlined />
          </a-button>
          <a-button
            size="small"
            class="scm-btn-tertiary scm-btn-icon"
            :disabled="index === modelValue.length - 1"
            :aria-label="`将图片 ${index + 1} 后移`"
            @click="move(index, index + 1)"
          >
            <ArrowRightOutlined />
          </a-button>
          <a-popconfirm title="从商品图集中移除此图片？" @confirm="remove(index)">
            <a-button size="small" class="scm-btn-icon-danger scm-btn-icon" :aria-label="`移除图片 ${index + 1}`">
              <DeleteOutlined />
            </a-button>
          </a-popconfirm>
        </div>
      </figure>
    </div>
  </div>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {UploadProps} from 'ant-design-vue';
import {ArrowLeftOutlined, ArrowRightOutlined, DeleteOutlined} from '@ant-design/icons-vue';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';
import type {ProductImage} from '/@/types/business/scm/product';
import {productError} from '../product-errors';
const props = defineProps<{modelValue: ProductImage[]; canEdit: boolean}>();
const emit = defineEmits<{'update:modelValue': [images: ProductImage[]]; uploading: [busy: boolean]}>();
const uploading = ref(false),
  dragIndex = ref(-1);
const uploadError = ref('');
const failedPreviews = ref(new Set<string>());
const hasBrokenPreview = computed(() => props.modelValue.some((image) => !image.fileUrl || failedPreviews.value.has(image.fileKey)));
function markPreviewFailed(fileKey: string) {
  failedPreviews.value = new Set([...failedPreviews.value, fileKey]);
}
const upload: UploadProps['customRequest'] = async (options) => {
  if (!props.canEdit || !(options.file instanceof File)) return;
  uploading.value = true;
  uploadError.value = '';
  emit('uploading', true);
  try {
    const data = new FormData();
    data.append('file', options.file);
    const response = await fileApi.uploadFile<{fileKey: string; fileUrl: string; fileName?: string; fileSize?: number}>(
      data,
      FILE_FOLDER_TYPE_ENUM.PUBLIC_IMAGE.value,
      {suppressGlobalErrorMessage: true}
    );
    if (!response.data?.fileKey) throw new Error('上传接口未返回文件信息');
    if (!props.modelValue.some((image) => image.fileKey === response.data.fileKey)) {
      emit('update:modelValue', [
        ...props.modelValue,
        {
          ...response.data,
          fileName: response.data.fileName || options.file.name,
          primaryFlag: props.modelValue.length === 0,
          sortOrder: props.modelValue.length,
        },
      ]);
    }
    options.onSuccess?.(response.data);
    message.success('图片已上传，请保存商品以完成关联');
  } catch (e) {
    const reason = productError(e);
    uploadError.value = reason === 'Network Error' ? '网络连接失败，图片未上传，请稍后重试' : reason;
    options.onError?.(e instanceof Error ? e : new Error(uploadError.value));
  } finally {
    uploading.value = false;
    emit('uploading', false);
  }
};

function setPrimary(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.map((image, i) => ({...image, primaryFlag: i === index}))
  );
}

function move(from: number, to: number) {
  if (!props.canEdit || from < 0 || to < 0 || to >= props.modelValue.length) return;
  const images = [...props.modelValue];
  images.splice(to, 0, images.splice(from, 1)[0]);
  emit(
    'update:modelValue',
    images.map((image, i) => ({...image, sortOrder: i}))
  );
  dragIndex.value = -1;
}

function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue
      .filter((_, i) => i !== index)
      .map((image, i) => ({
        ...image,
        sortOrder: i,
      }))
  );
}
</script>
<style scoped>
.image-upload {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 标题和上传按钮同一行 */
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

.image-item__fallback {
  display: flex;
  width: 112px;
  height: 112px;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border: 1px dashed var(--scm-border, #e5e6eb);
  border-radius: 6px;
  background: var(--scm-fill, #f5f5f5);
  color: var(--scm-text-secondary, #888);
  font-size: 12px;
  text-align: center;
}

.image-item__badge {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  color: #fff;
  background: var(--scm-primary);
  border-radius: 4px;
}

/* 操作按钮横向排开，尺寸小于图片 */
.image-item__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
}
</style>
