<template>
  <section class="inspection-panel" aria-label="批次检测报告">
    <div class="inspection-panel__head">
      <h4>检测报告</h4>
      <a-button v-if="canManage && !editing" :disabled="!lotId || loading || busy" @click="edit()">新建报告</a-button>
    </div>
    <p v-if="lotNo" class="inspection-panel__context">
      当前批次：<span class="scm-mono">{{ lotNo }}</span>
    </p>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action><a-button size="small" :disabled="busy" @click="load">刷新</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="!editing">
        <a-empty v-if="!reports.length && !loading && !error" description="暂无检测报告" />
        <article v-for="report in reports" :key="report.id" class="inspection-panel__report">
          <div class="inspection-panel__head">
            <strong>{{ report.reportName }}</strong>
            <ScmStatusTag :tone="INSPECTION_STATUS_TONE[report.status] || 'neutral'" :label="inspectionStatusLabel(report.status)" />
          </div>
          <p class="scm-mono">{{ report.reportNo }}</p>
          <p>{{ report.inspectionOrg }} · {{ report.inspectionDate }}</p>
          <p>
            检测结论：{{ inspectionConclusionLabel(report.conclusion) }}
            <ScmStatusTag v-if="report.expired" tone="warning" label="已过期" />
          </p>
          <p v-if="report.expiresAt">有效期至：{{ formatTime(report.expiresAt) }}</p>
          <p v-if="report.publishedAt">发布时间：{{ formatTime(report.publishedAt) }}</p>
          <p v-if="report.withdrawnAt">撤回时间：{{ formatTime(report.withdrawnAt) }}</p>
          <div class="inspection-panel__files">
            <a-button v-for="file in report.attachments" :key="file.fileKey" type="link" @click="download(file.fileKey)">
              {{ file.fileName }}
            </a-button>
          </div>
          <a-space v-if="canManage" wrap>
            <a-button v-if="report.status === REPORT_STATUS.DRAFT.value" :disabled="busy" @click="edit(report)">编辑草稿</a-button>
            <a-popconfirm
              v-if="report.status === REPORT_STATUS.DRAFT.value"
              title="发布后客户扫码可查看报告，报告内容将不可编辑。确认发布？"
              ok-text="发布"
              cancel-text="取消"
              :disabled="busy || report.expired || !report.attachments.length"
              @confirm="act(report, 'publish')"
            >
              <a-button type="primary" :disabled="busy || report.expired || !report.attachments.length">发布</a-button>
            </a-popconfirm>
            <a-popconfirm
              v-if="report.status === REPORT_STATUS.PUBLISHED.value"
              title="撤回后客户将无法查看或下载此报告，确认撤回？"
              ok-text="撤回"
              cancel-text="取消"
              :disabled="busy"
              @confirm="act(report, 'withdraw')"
            >
              <a-button danger :disabled="busy">撤回</a-button>
            </a-popconfirm>
          </a-space>
        </article>
      </template>
      <a-form v-else ref="formRef" :model="form" layout="vertical" class="inspection-panel__form" @finish="save">
        <a-alert v-if="editError" :message="editError" type="error" show-icon />
        <fieldset :disabled="busy">
          <legend>{{ form.id ? '编辑检测报告' : '新建检测报告' }}</legend>
          <a-form-item label="报告编号" name="reportNo" :rules="[{required: true, whitespace: true, message: '请填写报告编号'}]">
            <a-input v-model:value="form.reportNo" :maxlength="100" />
          </a-form-item>
          <a-form-item label="报告名称" name="reportName" :rules="[{required: true, whitespace: true, message: '请填写报告名称'}]">
            <a-input v-model:value="form.reportName" :maxlength="200" />
          </a-form-item>
          <a-form-item label="检测机构" name="inspectionOrg" :rules="[{required: true, whitespace: true, message: '请填写检测机构'}]">
            <a-input v-model:value="form.inspectionOrg" :maxlength="200" />
          </a-form-item>
          <a-row :gutter="12">
            <a-col :xs="24" :sm="12">
              <a-form-item label="检测日期" name="inspectionDate" :rules="[{required: true, message: '请选择检测日期'}]">
                <a-date-picker v-model:value="form.inspectionDate" value-format="YYYY-MM-DD" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12">
              <a-form-item label="检测结论" name="conclusion" :rules="[{required: true, message: '请选择检测结论'}]">
                <a-select v-model:value="form.conclusion" :options="conclusionOptions" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="有效期截止时间（选填，北京时间）">
            <a-date-picker v-model:value="expiryInput" show-time value-format="YYYY-MM-DDTHH:mm:ss" placeholder="未填写则不设有效期" />
          </a-form-item>
          <a-form-item label="报告附件">
            <FileUpload
              :key="editorKey"
              :default-file-list="defaultFiles"
              :multiple="true"
              :max-upload-size="20"
              :max-size="20"
              accept=".pdf,.jpg,.jpeg,.png,.webp"
              list-type="text"
              button-text="上传 PDF 或图片"
              @change="filesChanged"
            />
            <p class="inspection-panel__hint">最多20个文件，单个不超过20MB。发布前至少上传一份附件。</p>
          </a-form-item>
        </fieldset>
        <a-space>
          <a-button type="primary" :loading="busy" @click="submit">保存草稿</a-button>
          <a-button :disabled="busy" @click="editing = false">取消</a-button>
        </a-space>
      </a-form>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import {onBeforeUnmount, ref, watch} from 'vue';
import type {FormInstance} from 'ant-design-vue';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import FileUpload from '/@/components/support/file-upload/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {
  SCM_TRACE_INSPECTION_STATUS_ENUM as REPORT_STATUS,
  SCM_TRACE_INSPECTION_CONCLUSION_ENUM as REPORT_CONCLUSION,
  INSPECTION_STATUS_TONE,
  inspectionStatusLabel,
  inspectionConclusionLabel,
} from '/@/constants/business/scm/trace-const';
import {fileApi} from '/@/api/support/file-api';
import {traceInspectionApi} from '/@/api/business/scm/trace-inspection-api';
import type {InspectionReport, InspectionReportSave} from '/@/api/business/scm/trace-inspection-api';

dayjs.extend(utc);

const props = defineProps<{lotId?: number | string; lotNo?: string; canManage: boolean}>();
const reports = ref<InspectionReport[]>([]);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const editError = ref('');
const editing = ref(false);
const editorKey = ref(0);
const formRef = ref<FormInstance>();
const defaultFiles = ref<Record<string, unknown>[]>([]);
const expiryInput = ref('');
const form = ref<InspectionReportSave>(blankForm());
let requestId = 0;

const conclusionOptions = Object.values(REPORT_CONCLUSION).map(({value, desc}) => ({value, label: desc}));
function formatTime(value: string) {
  return dayjs(value).utcOffset(8).format('YYYY-MM-DD HH:mm');
}
function blankForm(): InspectionReportSave {
  return {
    lotId: props.lotId ?? '',
    reportNo: '',
    reportName: '',
    inspectionDate: '',
    inspectionOrg: '',
    conclusion: REPORT_CONCLUSION.UNDETERMINED.value,
    fileKeys: [],
  };
}
function failure(cause: unknown, fallback: string) {
  const item = cause as {message?: string; msg?: string; data?: {msg?: string}; response?: {data?: {msg?: string}}};
  return item?.response?.data?.msg || item?.data?.msg || item?.msg || item?.message || fallback;
}
async function load() {
  const current = ++requestId;
  const lotId = props.lotId;
  if (!lotId) {
    reports.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const result = await traceInspectionApi.list(lotId);
    if (current === requestId) reports.value = result;
  } catch (cause) {
    if (current === requestId) {
      reports.value = [];
      error.value = failure(cause, '报告加载失败，请刷新');
    }
  } finally {
    if (current === requestId) loading.value = false;
  }
}
function edit(report?: InspectionReport) {
  editError.value = '';
  form.value = report
    ? {
        id: report.id,
        version: report.version,
        lotId: report.lotId,
        reportNo: report.reportNo,
        reportName: report.reportName,
        inspectionDate: report.inspectionDate,
        inspectionOrg: report.inspectionOrg,
        conclusion: report.conclusion,
        fileKeys: report.attachments.map((file) => file.fileKey),
      }
    : blankForm();
  expiryInput.value = report?.expiresAt ? dayjs(report.expiresAt).utcOffset(8).format('YYYY-MM-DDTHH:mm:ss') : '';
  defaultFiles.value = report?.attachments.map((file) => ({...file, name: file.fileName, uid: file.fileKey, fileId: file.fileKey})) ?? [];
  editorKey.value++;
  editing.value = true;
}
function filesChanged(files: Array<{fileKey?: string}>) {
  form.value.fileKeys = files.flatMap((file) => (file.fileKey ? [file.fileKey] : []));
}
/**
 * 保存前显式跑一遍表单校验：项目禁止业务页用 html-type="submit"，所以不能靠 a-form 的 finish 事件触发，
 * 校验失败时停留在表单上，不把必填错误推给后端。
 */
async function submit() {
  if (busy.value) return;
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  await save();
}
async function save() {
  if (busy.value) return;
  const lotId = props.lotId;
  busy.value = true;
  editError.value = '';
  try {
    await traceInspectionApi.save({...form.value, expiresAt: expiryInput.value ? expiryInput.value + '+08:00' : null});
    if (props.lotId === lotId) {
      editing.value = false;
      await load();
    }
  } catch (cause) {
    if (props.lotId === lotId) editError.value = failure(cause, '保存失败，请重试');
  } finally {
    busy.value = false;
  }
}
async function act(report: InspectionReport, action: 'publish' | 'withdraw') {
  if (busy.value) return;
  const lotId = props.lotId;
  busy.value = true;
  error.value = '';
  try {
    await traceInspectionApi[action](report);
    if (props.lotId === lotId) await load();
  } catch (cause) {
    if (props.lotId === lotId) error.value = failure(cause, '操作失败，请刷新后重试');
  } finally {
    busy.value = false;
  }
}
async function download(fileKey: string) {
  try {
    await fileApi.downLoadFile(fileKey);
  } catch {
    /* 通用下载入口统一提示错误。 */
  }
}
watch(
  () => props.lotId,
  () => {
    editing.value = false;
    reports.value = [];
    void load();
  },
  {immediate: true}
);
onBeforeUnmount(() => {
  requestId++;
});
</script>

<style scoped>
.inspection-panel {
  margin-top: 16px;
}
.inspection-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.inspection-panel__head h4 {
  margin: 0;
}
.inspection-panel__head strong {
  min-width: 0;
  overflow-wrap: anywhere;
}
.inspection-panel__context,
.inspection-panel__hint {
  color: var(--scm-text-secondary, #667085);
}
.inspection-panel__report {
  padding: 16px 0;
  border-bottom: 1px solid var(--scm-border);
}
.inspection-panel__report p {
  margin: 6px 0;
  overflow-wrap: anywhere;
}
.inspection-panel__files {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;
}
.inspection-panel__files :deep(.ant-btn) {
  max-width: 100%;
  height: auto;
  padding-left: 0;
  white-space: normal;
  text-align: left;
  overflow-wrap: anywhere;
}
.inspection-panel__form {
  margin-top: 16px;
}
.inspection-panel__form fieldset {
  min-width: 0;
  padding: 0;
  border: 0;
}
.inspection-panel__form legend {
  font-size: 14px;
  font-weight: 600;
}
.inspection-panel__form :deep(.ant-picker) {
  width: 100%;
}
</style>
