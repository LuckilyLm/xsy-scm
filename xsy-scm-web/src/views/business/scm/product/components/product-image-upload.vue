<template>
  <div>
    <a-upload v-if="canEdit" accept="image/png,image/jpeg,image/webp,image/gif" :show-upload-list="false" :custom-request="upload" :disabled="uploading || modelValue.length >= 20">
      <a-button v-privilege="'scm:product:image'" :loading="uploading">上传图片</a-button>
    </a-upload>
    <p class="image-hint">商品图集，最多 20 张。可拖动排序，或用移动按钮调整。</p>
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <div class="images">
      <figure v-for="(image, index) in modelValue" :key="image.fileKey" :draggable="canEdit" @dragstart="dragIndex = index" @dragover.prevent @drop.prevent="move(dragIndex, index)">
        <a-image :src="image.fileUrl" :width="96" :height="96" :alt="image.fileName || `商品图 ${index + 1}`" />
        <figcaption><a-tag v-if="image.primaryFlag" color="green">主图</a-tag><span v-else>第 {{ index + 1 }} 张</span></figcaption>
        <a-space v-if="canEdit" direction="vertical" :size="2">
          <a-button size="small" :disabled="image.primaryFlag" @click="setPrimary(index)">设为主图</a-button>
          <a-space :size="2"><a-button size="small" :disabled="index === 0" :aria-label="`将图片 ${index + 1} 前移`" @click="move(index, index - 1)">←</a-button><a-button size="small" :disabled="index === modelValue.length - 1" :aria-label="`将图片 ${index + 1} 后移`" @click="move(index, index + 1)">→</a-button></a-space>
          <a-popconfirm title="从商品图集中移除此图片？" @confirm="remove(index)"><a-button size="small" danger>移除图片</a-button></a-popconfirm>
        </a-space>
      </figure>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import type { UploadProps } from 'ant-design-vue';
import { fileApi } from '/@/api/support/file-api';
import type { ProductImage, ScmResponse } from '/@/types/business/scm/product';
import { productError } from '../product-errors';
const props = defineProps<{ modelValue: ProductImage[]; canEdit: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [images: ProductImage[]]; uploading: [busy: boolean] }>();
const uploading = ref(false), error = ref(''), dragIndex = ref(-1);
const upload: UploadProps['customRequest'] = async options => {
  if (!props.canEdit || !(options.file instanceof File)) return;
  uploading.value = true; emit('uploading', true); error.value = '';
  try {
    const data = new FormData(); data.append('file', options.file);
    const response = await fileApi.uploadFile(data, 1) as unknown as ScmResponse<{ fileKey: string; fileUrl: string; fileName?: string; fileSize?: number }>;
    if (!props.modelValue.some(image => image.fileKey === response.data.fileKey)) emit('update:modelValue', [...props.modelValue, { ...response.data, fileName: response.data.fileName || options.file.name, primaryFlag: props.modelValue.length === 0, sortOrder: props.modelValue.length }]);
    options.onSuccess?.(response.data);
  } catch (e) { error.value = productError(e); options.onError?.(new Error(error.value)); }
  finally { uploading.value = false; emit('uploading', false); }
};
function setPrimary(index: number) { emit('update:modelValue', props.modelValue.map((image, i) => ({ ...image, primaryFlag: i === index }))); }
function move(from: number, to: number) {
  if (!props.canEdit || from < 0 || to < 0 || to >= props.modelValue.length) return;
  const images = [...props.modelValue]; images.splice(to, 0, images.splice(from, 1)[0]);
  emit('update:modelValue', images.map((image, i) => ({ ...image, sortOrder: i }))); dragIndex.value = -1;
}
function remove(index: number) { emit('update:modelValue', props.modelValue.filter((_, i) => i !== index).map((image, i) => ({ ...image, sortOrder: i }))); }
</script>
<style scoped>.images { display: flex; flex-wrap: wrap; gap: 16px; } figure { margin: 0; width: 112px; } figcaption { margin: 6px 0; } .image-hint { color: var(--ant-color-text-secondary, #666); margin: 8px 0 12px; }</style>
