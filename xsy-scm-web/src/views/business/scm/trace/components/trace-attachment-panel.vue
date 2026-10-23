<!-- 溯源附件面板：商品资质与批次检测报告共用，上传只提交 fileKey，由后端绑定。 -->
<template>
  <div class="trace-attach">
    <div class="trace-attach__head">
      <span class="trace-attach__title">{{ title }}</span>
      <a-button v-if="canManage" size="small" @click="openEdit">管理</a-button>
    </div>

    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" show-icon :message="error" class="trace-attach__alert">
        <template #action>
          <a-button size="small" @click="load">重试</a-button>
        </template>
      </a-alert>
      <template v-else>
        <a v-for="file in list" :key="file.fileKey" class="trace-attach__file" href="javascript:void(0)" @click="download(file.fileKey)">
          {{ file.fileName }}
        </a>
        <div v-if="!list.length && !loading" class="trace-attach__empty">{{ emptyText }}</div>
      </template>
    </a-spin>

    <a-modal v-model:open="editing" :title="title" :width="520" :confirm-loading="saving" ok-text="保存" cancel-text="取消" @ok="save">
      <a-alert v-if="editError" type="error" show-icon :message="editError" class="trace-attach__alert" />
      <FileUpload
        :default-file-list="defaultFileList"
        :multiple="true"
        :max-upload-size="20"
        :max-size="20"
        accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
        list-type="text"
        button-text="选择文件"
        @change="onFilesChange"
      />
      <div class="trace-attach__hint">支持 PDF、Word 与图片，单个不超过 20MB。</div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import FileUpload from '/@/components/support/file-upload/index.vue';
import {fileApi} from '/@/api/support/file-api';
import {traceAttachmentApi} from '/@/api/business/scm/trace-attachment-api';
import type {TraceAttachment} from '/@/api/business/scm/trace-attachment-api';

const props = defineProps<{
  /** PRODUCT 挂商品资质，LOT 挂批次检测报告。 */
  scope: 'PRODUCT' | 'LOT';
  /** 对象主键：商品是 spuId，批次是 lotId。 */
  targetId?: number | string;
  title: string;
  canManage: boolean;
  emptyText?: string;
}>();

const list = ref<TraceAttachment[]>([]);
const loading = ref(false);
const error = ref('');
const editing = ref(false);
const saving = ref(false);
const editError = ref('');
const defaultFileList = ref<Record<string, unknown>[]>([]);
const pendingKeys = ref<string[]>([]);

async function load() {
  if (!props.targetId) {
    list.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    list.value = await traceAttachmentApi.list(props.scope, props.targetId);
  } catch (e) {
    list.value = [];
    error.value = e instanceof Error ? e.message : '附件加载失败，请重试';
  } finally {
    loading.value = false;
  }
}

function openEdit() {
  editError.value = '';
  pendingKeys.value = list.value.map((file) => file.fileKey);
  defaultFileList.value = list.value.map((file) => ({fileKey: file.fileKey, fileName: file.fileName, name: file.fileName}));
  editing.value = true;
}

/** 上传组件回传的是文件对象数组，只取 fileKey 交给后端重新绑定。 */
function onFilesChange(files: Array<Record<string, unknown>>) {
  pendingKeys.value = files.map((file) => String(file.fileKey ?? '')).filter((key) => key.length > 0);
}

async function save() {
  if (!props.targetId) return;
  saving.value = true;
  editError.value = '';
  try {
    await traceAttachmentApi.sync(props.scope, props.targetId, pendingKeys.value);
    editing.value = false;
    await load();
  } catch (e) {
    // 保持弹窗打开，错误留在弹窗里
    editError.value = e instanceof Error ? e.message : '保存失败，请重试';
    return Promise.reject(e);
  } finally {
    saving.value = false;
  }
}

async function download(fileKey: string) {
  await fileApi.downLoadFile(fileKey);
}

watch(() => [props.scope, props.targetId], load, {immediate: true});
</script>

<style scoped>
.trace-attach {
  margin-top: 12px;
}

.trace-attach__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.trace-attach__title {
  font-weight: 600;
}

.trace-attach__alert {
  margin-bottom: 8px;
}

.trace-attach__file {
  display: block;
  padding: 4px 0;
  word-break: break-all;
}

.trace-attach__empty {
  color: #98a2b3;
  font-size: 12px;
}

.trace-attach__hint {
  margin-top: 8px;
  color: #667085;
  font-size: 12px;
}
</style>
