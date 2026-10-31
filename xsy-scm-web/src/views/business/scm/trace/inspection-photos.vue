<template>
  <a-card title="检测报告">
    <template #extra>
      <a-upload
        v-privilege="'scm:trace:photo:upload'"
        multiple
        accept="image/*"
        :show-upload-list="false"
        :before-upload="beforeUpload"
        :custom-request="upload"
      >
        <a-button type="primary" :loading="uploading > 0">{{ uploading ? '上传中' : '拍照 / 上传报告' }}</a-button>
      </a-upload>
    </template>
    <p class="scm-note">上传市场提供的检测报告照片，点击照片即可放大查看。</p>
    <a-alert v-if="error || uploadError" :message="uploadError || error" type="error" show-icon class="photo-error" />
    <a-spin :spinning="loading">
      <a-empty v-if="!reports.length && !loading" description="暂无报告照片" />
      <div class="photo-grid">
        <article v-for="report in reports" :key="report.id" class="photo-card">
          <button type="button" class="photo-open" @click="preview(report)">
            <img v-if="urls[String(report.id)]" :src="urls[String(report.id)]" :alt="report.reportName" />
            <span v-else>点击查看照片</span>
          </button>
          <strong>{{ report.reportName }}</strong>
          <div class="photo-footer">
            <span>{{ dayjs(report.createdAt).format('YYYY-MM-DD HH:mm') }} · {{ report.createdBy }}</span>
            <a-popconfirm title="移除这份报告照片？" :disabled="removing" @confirm="remove(report)">
              <a-button v-privilege="'scm:trace:photo:remove'" type="text" danger :disabled="removing">移除</a-button>
            </a-popconfirm>
          </div>
        </article>
      </div>
    </a-spin>
    <a-button v-if="hasMore" :loading="loading" @click="load(true)">加载更多</a-button>
    <a-modal v-model:open="previewOpen" :footer="null" title="报告照片" width="900px">
      <img :src="previewUrl" alt="检测报告照片" style="width: 100%" />
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {message, Upload, type UploadProps} from 'ant-design-vue';
import dayjs from 'dayjs';
import {traceInspectionApi, type InspectionPhoto as PhotoReport} from '/@/api/business/scm/trace-inspection-api';
import {fileApi} from '/@/api/support/file-api';
import {FILE_FOLDER_TYPE_ENUM} from '/@/constants/support/file-const';

const reports = ref<PhotoReport[]>([]);
const urls = ref<Record<string, string>>({});
const error = ref('');
const uploadError = ref('');
const loading = ref(false);
const removing = ref(false);
const uploading = ref(0);
const hasMore = ref(false);
const previewOpen = ref(false);
const previewUrl = ref('');
let active = true;
let loadVersion = 0;

async function urlFor(report: PhotoReport) {
  const key = report.attachments[0]?.fileKey;
  if (!key) return '';
  const response = await fileApi.getUrl(key);
  return response.data as string;
}
async function load(more = false) {
  const version = ++loadVersion;
  loading.value = true;
  error.value = '';
  try {
    const response = await traceInspectionApi.photos(more ? reports.value[reports.value.length - 1]?.id : undefined);
    if (!active || version !== loadVersion) return;
    const rows = response.data ?? [];
    reports.value = more ? [...reports.value, ...rows] : rows;
    hasMore.value = rows.length === 50;
    await Promise.allSettled(
      rows.map(async (report) => {
        const url = await urlFor(report);
        if (active && version === loadVersion) urls.value[String(report.id)] = url;
      })
    );
  } catch {
    if (active && version === loadVersion) error.value = '报告加载失败，请刷新页面重试';
  } finally {
    if (active && version === loadVersion) loading.value = false;
  }
}
function beforeUpload(file: File) {
  if (!/\.(jpg|jpeg|png|webp)$/i.test(file.name) || file.size <= 0 || file.size > 20 * 1024 * 1024) {
    message.error('请上传20MB以内的 JPG、PNG 或 WebP 照片');
    return Upload.LIST_IGNORE;
  }
  return true;
}
const upload: NonNullable<UploadProps['customRequest']> = async (options) => {
  const file = options.file as File;
  if (uploading.value === 0) uploadError.value = '';
  uploading.value++;
  try {
    const form = new FormData();
    form.append('file', file);
    const uploaded = await fileApi.uploadFile<{fileKey: string}>(form, FILE_FOLDER_TYPE_ENUM.COMMON.value, {suppressGlobalErrorMessage: true});
    const result = await traceInspectionApi.uploadPhotos([uploaded.data.fileKey]);
    options.onSuccess?.(result.data);
  } catch (cause) {
    options.onError?.(cause as Error);
    if (active) uploadError.value = `“${file.name}”上传失败，请重新上传`;
  } finally {
    uploading.value--;
    if (active && uploading.value === 0) await load();
  }
};
async function preview(report: PhotoReport) {
  try {
    previewUrl.value = await urlFor(report);
    previewOpen.value = true;
  } catch {
    /* 文件接口统一提示错误 */
  }
}
async function remove(report: PhotoReport) {
  if (removing.value) return;
  removing.value = true;
  try {
    await traceInspectionApi.removePhotos(report);
    await load();
  } catch {
    /* 请求层统一提示 */
  } finally {
    removing.value = false;
  }
}
onMounted(() => load());
onBeforeUnmount(() => {
  active = false;
  loadVersion++;
});
</script>

<style scoped>
.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.photo-card {
  border: 1px solid var(--ant-color-border, #e8e8e8);
  border-radius: 8px;
  padding: 12px;
  min-width: 0;
}
.photo-open {
  display: block;
  width: 100%;
  height: 200px;
  padding: 0;
  margin-bottom: 12px;
  border: 0;
  background: #f6f7f6;
  cursor: pointer;
}
.photo-open img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.photo-card strong {
  display: block;
  overflow-wrap: anywhere;
}
.photo-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #666;
  font-size: 12px;
}
.photo-error {
  margin-bottom: 16px;
}
</style>
