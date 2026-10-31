<template>
  <a-modal
    v-model:open="visible"
    title="导入销售订单"
    width="860px"
    :confirm-loading="loading"
    :ok-button-props="{disabled: loading || imported}"
    :cancel-button-props="{disabled: loading}"
    :closable="!loading"
    :mask-closable="!loading"
    :keyboard="!loading"
    ok-text="开始导入"
    @ok="submit"
    @cancel="reset"
  >
    <FormNote title="导入规则" :lines="IMPORT_RULES" />

    <!-- 模板下载按钮和文件格式说明 -->
    <div class="import-template">
      <a-button class="scm-btn-secondary import-template__download" :loading="downloading" :disabled="loading" @click="downloadTemplate">
        <template #icon>
          <DownloadOutlined />
        </template>
        下载客户订单模板
      </a-button>
      <span class="import-template__note">仅支持 .xlsx，最大 5 MiB</span>
    </div>

    <a-upload-dragger :file-list="fileList" :before-upload="beforeUpload" :disabled="loading" :max-count="1" accept=".xlsx" @remove="removeFile">
      <p class="ant-upload-drag-icon">
        <InboxOutlined />
      </p>
      <p class="ant-upload-text">点击或拖拽客户订单 Excel 到此处</p>
      <p class="ant-upload-hint">请使用最新模板：收货单位填客户名称，商品名称、规格、单位须与商品档案一致</p>
    </a-upload-dragger>

    <a-result
      v-if="result && !result.totalErrors"
      status="success"
      title="订单导入完成"
      :sub-title="`共 ${result.totalOrders} 张：已确认 ${result.confirmedOrders} 张，待称重 ${result.pendingOrders} 张`"
    >
      <template #extra>
        <a-table :data-source="result.orders" :columns="importedColumns" row-key="orderId" size="small" :pagination="{pageSize: 5}">
          <template #bodyCell="{column, record}">
            <a-button v-if="column.dataIndex === 'review'" v-privilege="'scm:order:update'" type="link" @click="reviewImported(record.orderId)"
              >核对商品与价格</a-button
            >
          </template>
        </a-table>
      </template>
    </a-result>
  </a-modal>
  <OrderForm ref="reviewForm" @saved="emit('saved')" />
</template>
<script setup lang="ts">
import {computed, nextTick, ref} from 'vue';
import OrderForm from './order-form-drawer.vue';
import {message, Upload} from 'ant-design-vue';
import {DownloadOutlined, InboxOutlined} from '@ant-design/icons-vue';
import type {UploadFile, UploadProps} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import FormNote from '/@/components/framework/form-note/index.vue';
import type {Id, ImportError, ImportResult} from '../order-types';

const IMPORT_RULES = [
  '一份客户订单生成一张销售订单：收货单位填客户档案里的客户名称，日期即期望送货日期。',
  '商品按「商品名称 + 规格 + 单位」匹配商品档案，实数量作为下单数量；合计行与签字栏不导入。',
  '填写销售单价即按客户订单单价下单，需要订单改价权限，改价原因由系统记为「客户订单导入」；留空按系统定价。',
  '纯标品自动确认；含非标品时整单待确认，等待电子秤回写实重。',
];

const emit = defineEmits<{saved: []}>();
const visible = ref(false),
  loading = ref(false),
  downloading = ref(false),
  result = ref<ImportResult>(),
  fileList = ref<UploadFile[]>([]);
const selectedFile = ref<File>();
const reviewForm = ref<InstanceType<typeof OrderForm>>();
const importedColumns = [
  {title: '订单号', dataIndex: 'orderNo'},
  {title: '客户', dataIndex: 'customerNameSnapshot'},
  {title: '操作', dataIndex: 'review', width: 160},
];
async function reviewImported(id: Id) {
  visible.value = false;
  await nextTick();
  await reviewForm.value?.open(id);
}

const imported = computed(() => !!result.value && result.value.totalErrors === 0);

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

/** 模板下载沿用导入权限；失败时由请求层提示后端文案。 */
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

/** 导入错误只在 toast 里给出首条定位；完整问题清单以文件为准，修正后重试。 */
function notifyImportErrors(errors: ImportError[], totalErrors: number) {
  const first = errors[0];
  const where = first ? (first.rowNumber > 0 ? `第 ${first.rowNumber} 行${first.column ? `「${first.column}」` : ''}` : first.column) : '';
  const position = where ? `${where}：` : '';
  const detail = first?.message ?? '请检查导入文件';
  const rest = totalErrors > 1 ? `（共 ${totalErrors} 处问题）` : '';
  message.error({content: `订单未写入：${position}${detail}${rest}`, duration: 6});
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
    if (response.data.totalErrors) {
      notifyImportErrors(response.data.errors, response.data.totalErrors);
      return;
    }
    result.value = response.data;
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

.import-template__download {
  flex: none;
}

.import-template__download:disabled,
.import-template__download.ant-btn-disabled {
  color: var(--scm-text-disabled, rgba(0, 0, 0, 0.25));
  background: var(--scm-fill, rgba(0, 0, 0, 0.04));
  border-color: var(--scm-border, rgba(0, 0, 0, 0.06));
}
</style>
