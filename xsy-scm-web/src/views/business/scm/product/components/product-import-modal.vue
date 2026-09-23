<template>
  <a-modal v-model:open="open" title="导入商品" :width="720" :mask-closable="false"
           :footer="null" @cancel="reset">
    <a-alert type="info" show-icon class="hint" :message="hint" />
    <a-space class="toolbar">
      <a-radio-group v-model:value="mode" button-style="solid">
        <a-radio-button value="CREATE">新增商品</a-radio-button>
        <a-radio-button value="UPDATE">更新既存商品</a-radio-button>
      </a-radio-group>
      <a-button :loading="templateLoading" @click="downloadTemplate">下载模板</a-button>
      <a-upload :file-list="[]" :before-upload="pickFile" accept=".xlsx" :show-upload-list="false">
        <a-button>选择 Excel 文件</a-button>
      </a-upload>
      <a-button type="primary" :loading="importing" :disabled="!file" @click="doImport">
        {{ mode === 'UPDATE' ? '开始更新' : '开始导入' }}
      </a-button>
    </a-space>
    <p v-if="file" class="picked">已选文件：{{ file.name }}（{{ (file.size / 1024).toFixed(1) }} KB）</p>

    <a-result v-if="done && !hasErrors" status="success"
              :title="doneTitle"
              :sub-title="`共 ${result?.totalProducts ?? 0} 个商品、${result?.totalRows ?? 0} 行数据`" />

    <template v-if="result && hasErrors">
      <a-alert type="error" show-icon class="hint"
               :message="`校验未通过：${groups.length} 行 / ${result.totalErrors} 处错误，本次没有任何商品写入`" />
      <div class="detail-actions">
        <a-button size="small" @click="downloadErrors">下载失败明细</a-button>
      </div>
      <a-table :data-source="groups" :columns="errorColumns" row-key="rowNumber" size="small"
               :pagination="false" :scroll="{ y: 320 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'cells'">
            <div v-for="(cell, i) in record.cells" :key="i" class="cell-error">
              <b>{{ cell.column }}</b>：{{ cell.message }}
              <a-tag size="small">{{ cell.code }}</a-tag>
            </div>
          </template>
        </template>
      </a-table>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import type { UploadProps } from 'ant-design-vue';
import { productApi } from '/@/api/business/scm/product-api';
import type { ProductImportMode, ProductImportResult } from '/@/types/business/scm/product';
import { groupErrorsByRow, hasBlockingErrors } from '../product-import-model';
import { productError } from '../product-errors';

const emit = defineEmits<{ imported: [] }>();
const open = ref(false), file = ref<File | null>(null), importing = ref(false), templateLoading = ref(false),
    result = ref<ProductImportResult | null>(null), done = ref(false), mode = ref<ProductImportMode>('CREATE');
const errorColumns = [
  { title: 'Excel 行', dataIndex: 'rowNumber', width: 90 },
  { title: 'SPU 编码', dataIndex: 'spuCode', width: 150 },
  { title: '错误明细', dataIndex: 'cells' },
];
const groups = computed(() => (result.value ? groupErrorsByRow(result.value.errors) : []));
const hasErrors = computed(() => !!result.value && hasBlockingErrors(result.value));
const hint = computed(() => (mode.value === 'UPDATE'
    ? '更新只改写你填写的单元格，空白列保持原值；填 (清空) 才会清除别名、助记码、品牌、产地、标签编码、条码的既有值。文件里没出现的 SKU 与图片也原样保留。前四列定位键请取自刚导出的商品档案：SPU ID / SPU版本 / SKU ID / SKU版本，版本过期会整批拒绝。'
    : '导入是整批事务：任意一行有错都不会写入任何商品。请先下载模板，按 SPU 编码组织多行 SKU。'));
const doneTitle = computed(() => (result.value?.mode === 'UPDATE'
    ? `成功更新 ${result.value?.updatedProducts ?? 0} 个商品`
    : `成功导入 ${result.value?.importedProducts ?? 0} 个商品`));

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
    await productApi.downloadImportTemplate(mode.value);
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
    const response = await productApi.importProducts(file.value, mode.value);
    result.value = response.data;
    done.value = true;
    if (!hasBlockingErrors(response.data)) {
      message.success(response.data.mode === 'UPDATE' ? '商品更新成功' : '商品导入成功');
      emit('imported');
    }
  } catch (e) {
    message.error(productError(e));
  } finally {
    importing.value = false;
  }
}

/** 失败明细在前端即时导出，符合「本次即时查看/下载、不建历史批次表」的第一版约定。 */
function downloadErrors() {
  if (!result.value) return;
  const header = ['Excel行', 'SPU编码', '列', '原因码', '说明'];
  const lines = result.value.errors.map(e => [e.rowNumber, e.spuCode ?? '', e.column, e.code, e.message]
      .map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','));
  const blob = new Blob(['﻿' + [header.join(','), ...lines].join('\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = '商品导入失败明细.csv';
  link.click();
  URL.revokeObjectURL(url);
}

defineExpose({ show });
</script>
<style scoped>
.hint {
  margin-bottom: 12px;
}

.toolbar {
  margin-bottom: 8px;
}

.picked {
  color: var(--ant-color-text-secondary, #4e5969);
  font-size: 12px;
}

.detail-actions {
  margin-bottom: 8px;
  text-align: right;
}

.cell-error {
  line-height: 1.8;
}
</style>
