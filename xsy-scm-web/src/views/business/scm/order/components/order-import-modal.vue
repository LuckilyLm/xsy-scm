<template>
  <a-modal v-model:open="visible" title="导入销售订单" width="860px" :confirm-loading="loading"
           :ok-button-props="{disabled: loading || imported}" :cancel-button-props="{disabled: loading}"
           :closable="!loading" :mask-closable="!loading" :keyboard="!loading" ok-text="开始导入" @ok="submit"
           @cancel="reset">
    <!--
      导入规则做成紧凑面板，不用整块 a-alert：那个蓝底块有 16px 内边距 + 16px 外边距，
      在 860px 宽的弹窗里占掉 133px 高，是弹窗里最重的元素，而它只是静态说明。
    -->
    <section class="import-rules">
      <p class="import-rules__title">导入规则</p>
      <ul class="import-rules__list">
        <li>同一「导入订单标识」的多行会合并为一张订单；请删除或替换模板示例行。</li>
        <li>人工单价留空用系统定价；填写时必须同时填写改价原因并具备改价权限。</li>
        <li>纯标品自动确认；含非标品时整单待确认，等待电子秤回写实重。</li>
      </ul>
    </section>

    <!-- 模板下载：按钮与说明同一水平线居中，说明不再做成绿色胶囊（和上面的信息色打架） -->
    <div class="import-template">
      <a-button :loading="downloading" :disabled="loading" @click="downloadTemplate">
        <template #icon>
          <DownloadOutlined/>
        </template>
        下载 Excel 模板
      </a-button>
      <span class="import-template__note">仅支持 .xlsx，最大 5 MiB · 任一错误均不会创建订单</span>
    </div>

    <a-upload-dragger :file-list="fileList" :before-upload="beforeUpload" :disabled="loading" :max-count="1"
                      accept=".xlsx" @remove="removeFile">
      <p class="ant-upload-drag-icon">
        <InboxOutlined/>
      </p>
      <p class="ant-upload-text">点击或拖拽订单 Excel 到此处</p>
      <p class="ant-upload-hint">请使用最新模板，按客户编码与商品规格编码填写</p>
    </a-upload-dragger>

    <a-result v-if="result && !result.totalErrors" status="success" title="订单导入完成"
              :sub-title="`共 ${result.totalOrders} 张：已确认 ${result.confirmedOrders} 张，待称重 ${result.pendingOrders} 张`"/>
    <div v-else-if="result?.totalErrors" class="import-errors">
      <a-alert type="error" show-icon :message="`发现 ${result.totalErrors} 个问题，订单未写入`"
               class="error-summary"/>
      <a-table size="small" bordered :pagination="false" :data-source="result.errors" :columns="columns"
               :scroll="{y:300}" :row-key="(row:ImportError)=>`${row.rowNumber}-${row.column}-${row.code}`"/>
    </div>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import {message, Upload} from 'ant-design-vue';
import {DownloadOutlined, InboxOutlined} from '@ant-design/icons-vue';
import type {UploadFile, UploadProps, TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import type {ImportError, ImportResult} from '../order-types';
const emit = defineEmits<{ saved: [] }>();
const visible = ref(false), loading = ref(false), downloading = ref(false),
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
    message.warning('仅支持 .xlsx 文件');
    return Upload.LIST_IGNORE;
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('导入文件不能超过 5 MiB');
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
    message.warning('请先选择订单 Excel 文件');
    return;
  }
  loading.value = true;
  try {
    const response = await orderApi.importOrders(file);
    result.value = response.data;
    if (response.data.totalErrors) {
      message.error('导入文件存在错误，请修正后重试');
      return;
    }
    message.success('订单导入完成');
    emit('saved');
  } catch {
    // 导入失败只走全局 toast
  } finally {
    loading.value = false;
  }
}

defineExpose({open});
</script>
<style scoped>
.import-rules {
  margin-bottom: 14px;
  padding: 10px 14px 12px;
  border: 1px solid var(--scm-border, rgba(0, 0, 0, 0.06));
  border-left: 3px solid var(--scm-primary, #00b96b);
  border-radius: 6px;
  background: var(--scm-fill, rgba(0, 0, 0, 0.02));
}

.import-rules__title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--scm-text, rgba(0, 0, 0, 0.88));
}

.import-rules__list {
  margin: 0;
  padding-left: 16px;
  font-size: 12px;
  line-height: 20px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.import-rules__list li + li {
  margin-top: 2px;
}

.import-template {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.import-template__note {
  font-size: 12px;
  line-height: 20px;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.import-errors {
  margin-top: 14px;
}

.error-summary {
  margin-bottom: 12px;
}
</style>
