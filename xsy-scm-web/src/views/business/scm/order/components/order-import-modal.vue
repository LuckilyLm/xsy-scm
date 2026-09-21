<template>
  <a-modal v-model:open="visible" title="导入销售订单" width="860px" :confirm-loading="loading"
           :ok-button-props="{disabled: loading || imported}" :cancel-button-props="{disabled: loading}"
           :closable="!loading" :mask-closable="!loading" :keyboard="!loading" ok-text="开始导入" @ok="submit"
           @cancel="reset">
    <a-alert type="info" show-icon class="import-tip">
      <template #message>导入规则</template>
      <template #description>
        同一“导入订单标识”的多行会合并为一张订单。请删除或替换模板示例行；人工单价留空时使用系统定价，填写时必须同时填写改价原因并具备改价权限。纯标品自动确认；包含非标品时整单进入待确认，等待电子秤回写实重。
      </template>
    </a-alert>
    <a-space direction="vertical" size="middle" style="width:100%">
      <a-space>
        <a-button :loading="downloading" :disabled="loading" @click="downloadTemplate">下载 Excel 模板</a-button>
        <span class="hint">仅支持 .xlsx，最大 5 MiB；任一错误均不会创建订单</span>
      </a-space>
      <a-upload-dragger :file-list="fileList" :before-upload="beforeUpload" :disabled="loading" :max-count="1"
                        accept=".xlsx" @remove="removeFile">
        <p class="ant-upload-text">点击或拖拽订单 Excel 到此处</p>
        <p class="ant-upload-hint">请使用最新模板，按客户编码与 SKU 编码填写</p>
      </a-upload-dragger>
      <a-alert v-if="error" type="error" show-icon :message="error"/>
      <a-result v-if="result && !result.totalErrors" status="success" title="订单导入完成"
                :sub-title="`共 ${result.totalOrders} 张：已确认 ${result.confirmedOrders} 张，待称重 ${result.pendingOrders} 张`"/>
      <div v-else-if="result?.totalErrors">
        <a-alert type="error" show-icon :message="`发现 ${result.totalErrors} 个问题，订单未写入`"
                 class="error-summary"/>
        <a-table size="small" bordered :pagination="false" :data-source="result.errors" :columns="columns"
                 :scroll="{y:300}" :row-key="(row:ImportError)=>`${row.rowNumber}-${row.column}-${row.code}`"/>
      </div>
    </a-space>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import {message, Upload} from 'ant-design-vue';
import type {UploadFile, UploadProps, TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import type {ImportError, ImportResult} from '../order-types';
import {orderError} from '../order-errors';

const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), downloading = ref(false), error = ref(''),
    result = ref<ImportResult>(), fileList = ref<UploadFile[]>([]);
const selectedFile = ref<File>();
const imported = computed(() => !!result.value && result.value.totalErrors === 0);
const columns: TableColumnsType<ImportError> = [{title: '行号', dataIndex: 'rowNumber', width: 72}, {
  title: '订单标识',
  dataIndex: 'orderKey',
  width: 130
}, {title: '字段', dataIndex: 'column', width: 120}, {title: '问题', dataIndex: 'message'}];

function open() {
  reset();
  visible.value = true;
}

function reset() {
  fileList.value = [];
  selectedFile.value = undefined;
  error.value = '';
  result.value = undefined;
  loading.value = false;
}

/**
 * 模板下载沿用 scm:order:import 权限；失败时由请求层弹出后端文案，
 * 不会把错误 JSON 当成 .xlsx 保存。
 */
async function downloadTemplate() {
  if (downloading.value || loading.value) return;
  downloading.value = true;
  try {
    await orderApi.downloadImportTemplate();
  } finally {
    downloading.value = false;
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  reset();
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    error.value = '仅支持 .xlsx 文件';
    return Upload.LIST_IGNORE;
  }
  if (file.size > 5 * 1024 * 1024) {
    error.value = '导入文件不能超过 5 MiB';
    return Upload.LIST_IGNORE;
  }
  selectedFile.value = file;
  fileList.value = [{uid: file.uid, name: file.name, originFileObj: file}];
  return false;
};

function removeFile() {
  if (loading.value) return false;
  reset();
  return true;
}

async function submit() {
  if (loading.value || imported.value) return;
  const file = selectedFile.value;
  if (!file) {
    error.value = '请先选择订单 Excel 文件';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const response = await orderApi.importOrders(file);
    result.value = response.data;
    if (response.data.totalErrors) {
      message.error('导入文件存在错误，请修正后重试');
      return;
    }
    message.success('订单导入完成');
    emit('saved');
  } catch (e) {
    error.value = orderError(e);
  } finally {
    loading.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
.import-tip {
  margin-bottom: 16px
}

.hint {
  color: #4e5969
}

.error-summary {
  margin-bottom: 12px
}
</style>
