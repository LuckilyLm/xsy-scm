<template>
  <a-modal v-model:open="open" title="导入商品" :width="640" :mask-closable="false" :footer="null" @cancel="reset">
    <div class="import-step">
      <div class="import-step__head">
        <span class="import-step__index">1</span>
        <span class="import-step__title">下载模板</span>
        <span class="import-step__desc">按模板表头填写，空白列不会覆盖原值</span>
      </div>
      <a-button :loading="templateLoading" @click="downloadTemplate">下载模板</a-button>
    </div>

    <div class="import-step">
      <div class="import-step__head">
        <span class="import-step__index">2</span>
        <span class="import-step__title">选择文件</span>
        <span class="import-step__desc">仅支持 .xlsx，单文件不超过 10 MiB</span>
      </div>
      <a-upload-dragger
        class="import-drop"
        :class="{'is-picked': !!file}"
        :file-list="[]"
        :before-upload="pickFile"
        accept=".xlsx"
        :show-upload-list="false"
      >
        <p class="import-drop__icon">
          <inbox-outlined />
        </p>
        <p class="import-drop__title">{{ file ? file.name : '点击或拖拽 Excel 文件到此处' }}</p>
        <p class="import-drop__hint">
          {{ file ? `${(file.size / 1024).toFixed(1)} KB · 点击可重新选择` : '选好文件后仍需点击下方按钮才会导入' }}
        </p>
      </a-upload-dragger>
    </div>

    <div class="import-actions">
      <a-button @click="reset">重置</a-button>
      <a-button type="primary" :loading="importing" :disabled="!file" @click="doImport">开始导入</a-button>
    </div>

    <a-result
      v-if="done && !hasErrors"
      status="success"
      :title="doneTitle"
      :sub-title="`共 ${result?.totalProducts ?? 0} 个商品、${result?.totalRows ?? 0} 行数据`"
    />

    <template v-if="result && hasErrors">
      <a-alert
        type="error"
        show-icon
        class="hint"
        :message="`校验未通过：${groups.length} 行 / ${result.totalErrors} 处错误，本次没有任何商品写入`"
      />
      <div class="detail-actions">
        <a-button size="small" @click="downloadErrors">下载失败明细</a-button>
      </div>
      <a-table :data-source="groups" :columns="errorColumns" row-key="rowNumber" size="small" :pagination="false" :scroll="{y: 320}">
        <template #bodyCell="{column, record}">
          <template v-if="column.dataIndex === 'cells'">
            <div v-for="(cell, i) in record.cells" :key="i" class="cell-error">
              <b>{{ cell.column }}</b
              >：{{ cell.message }}
              <a-tag size="small">{{ cell.code }}</a-tag>
            </div>
          </template>
        </template>
      </a-table>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {UploadProps} from 'ant-design-vue';
import {productApi} from '/@/api/business/scm/product-api';
import type {ProductImportResult} from '/@/types/business/scm/product';
import {groupErrorsByRow, hasBlockingErrors} from '../product-import-model';
import {productError} from '../product-errors';

const emit = defineEmits<{imported: []}>();
const open = ref(false),
  file = ref<File | null>(null),
  importing = ref(false),
  templateLoading = ref(false),
  result = ref<ProductImportResult | null>(null),
  done = ref(false);
const errorColumns = [
  {title: 'Excel 行', dataIndex: 'rowNumber', width: 90},
  {title: '导入分组', dataIndex: 'spuCode', width: 150},
  {title: '错误明细', dataIndex: 'cells'},
];
const groups = computed(() => (result.value ? groupErrorsByRow(result.value.errors) : []));
const hasErrors = computed(() => !!result.value && hasBlockingErrors(result.value));
// 更新导入只改既存商品，文案必须与新增区分，否则运营无法从结果判断「有没有多建商品」。
const doneTitle = computed(() =>
  result.value?.mode === 'UPDATE' ? `成功更新 ${result.value?.updatedProducts ?? 0} 个商品` : `成功导入 ${result.value?.importedProducts ?? 0} 个商品`
);

function show() {
  open.value = true;
}

function reset() {
  file.value = null;
  result.value = null;
  done.value = false;
  importing.value = false;
}

const pickFile: UploadProps['beforeUpload'] = (selected) => {
  file.value = selected as File;
  result.value = null;
  done.value = false;
  return false;
};

async function downloadTemplate() {
  templateLoading.value = true;
  try {
    await productApi.downloadImportTemplate('CREATE');
  } catch (e) {
    message.error(productError(e));
  } finally {
    templateLoading.value = false;
  }
}

async function doImport() {
  if (!file.value) return;
  importing.value = true;
  try {
    // 不传模式：新增/更新由模板表头决定，页面不做第二套判断
    const response = await productApi.importProducts(file.value);
    result.value = response.data;
    done.value = true;
    if (!hasBlockingErrors(response.data)) {
      message.success('商品导入成功');
      emit('imported');
    }
  } catch (e) {
    message.error(productError(e));
  } finally {
    importing.value = false;
  }
}

/** 失败明细当场下载，不留导入记录。 */
function downloadErrors() {
  if (!result.value) return;
  const header = ['Excel行', '导入分组', '列', '原因码', '说明'];
  const lines = result.value.errors.map((e) =>
    [e.rowNumber, e.spuCode ?? '', e.column, e.code, e.message].map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(',')
  );
  const blob = new Blob(['﻿' + [header.join(','), ...lines].join('\n')], {type: 'text/csv;charset=utf-8'});
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = '商品导入失败明细.csv';
  link.click();
  URL.revokeObjectURL(url);
}

defineExpose({show});
</script>
<style scoped>
.import-step {
  margin-bottom: 18px;
}

.import-step__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

/* 步骤序号，突出操作顺序 */
.import-step__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background-color: #52c41a;
  color: #ffffff;
  font-size: 11px;
  line-height: 1;
}

.import-step__title {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
}

.import-step__desc {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.import-drop :deep(.ant-upload-drag) {
  border-radius: 8px;
  transition:
    border-color 0.2s,
    background-color 0.2s;
}

/* 选中文件后整块变浅绿 */
.import-drop.is-picked :deep(.ant-upload-drag) {
  border-color: #52c41a;
  background-color: #f6ffed;
}

.import-drop__icon {
  margin: 0 0 4px;
  font-size: 24px;
  color: #bfbfbf;
}

.import-drop.is-picked .import-drop__icon {
  color: #52c41a;
}

.import-drop__title {
  margin: 0 0 2px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
}

.import-drop.is-picked .import-drop__title {
  color: #389e0d;
}

.import-drop__hint {
  margin: 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.import-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.hint {
  margin-bottom: 12px;
}

.detail-actions {
  margin-bottom: 8px;
  text-align: right;
}

.cell-error {
  line-height: 1.8;
}
</style>
