<!-- 打印对话框：选择模板、预览、正式打印。打印前先保存打印记录，不改动原单据。 -->
<template>
  <a-modal
    :open="open"
    title="打印"
    width="1000px"
    :confirm-loading="printing"
    :closable="!printing"
    :mask-closable="!printing"
    :keyboard="!printing"
    ok-text="打印"
    :ok-button-props="{disabled: !canPrint}"
    @ok="print"
    @cancel="close"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon />

    <a-form layout="inline" class="toolbar">
      <a-form-item label="模板">
        <a-select
          v-model:value="templateId"
          :options="templateOptions"
          :loading="loading"
          :disabled="loading || printing"
          style="width: 320px"
          placeholder="请选择打印模板"
          @change="reloadPreview"
        />
      </a-form-item>
      <a-form-item>
        <a-button :loading="loading" :disabled="printing" @click="reloadPreview">刷新预览</a-button>
      </a-form-item>
    </a-form>

    <a-alert
      v-if="hiddenFields.length"
      class="banner"
      type="warning"
      show-icon
      message="部分字段因权限未打印"
      :description="`以下字段在你的模板里已选，但你没有该单据类型的金额权限，本次不会出现在纸上：${hiddenFields.join('、')}`"
    />

    <a-spin :spinning="loading">
      <div v-if="renders.length" class="preview" v-html="previewHtml" />
      <a-empty v-else-if="!loading && !error" description="没有可预览的内容" />
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {notification} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {Id, PrintDocumentType, PrintRender, PrintTemplate} from './print-types';
import {renderPrintHtml, printRenders, prepareQrCodes} from './print-render';
import {printError} from './print-errors';

const props = defineProps<{
  open: boolean;
  documentType: PrintDocumentType;
  /** 批量打印共用一个模板，一张单据一页。 */
  businessIds: Id[];
}>();

const emit = defineEmits<{close: []; printed: []}>();

const templates = ref<PrintTemplate[]>([]);
const templateId = ref<Id | undefined>(undefined);
const renders = ref<PrintRender[]>([]);
const loading = ref(false);
const printing = ref(false);
const error = ref('');
let requestId = 0;
const frozenByBusiness = new Map<string, PrintRender>();

const templateOptions = computed(() =>
  templates.value.map((item) => ({
    value: item.id,
    label: `${item.templateName}${item.defaultFlag ? '（默认）' : ''}`,
  }))
);

const canPrint = computed(
  () =>
    !loading.value &&
    !printing.value &&
    renders.value.length > 0 &&
    renders.value.length === props.businessIds.length &&
    templateId.value !== undefined
);

const hiddenFields = computed(() => {
  const keys = new Set<string>();
  renders.value.forEach((render) => (render.hiddenFields ?? []).forEach((key) => keys.add(key)));
  return Array.from(keys);
});

/** 打印完成的说明：模板名、版本、打印时间。用模板名而不是编码，用户认的是名字。 */
function snapshotHint(render: PrintRender): string {
  const template = render.templateName || render.templateCode;
  return `模板 ${template ?? '—'} v${render.templateVersion ?? '—'}，打印时间 ${
    render.printedAt ?? '—'
  }。再次打印使用这次保存的内容，不会随单据后续修改而变化。`;
}

/** 预览 HTML；二维码要先异步生成，由 refreshPreview 统一刷新。 */
const previewHtml = ref('');

async function refreshPreview() {
  await prepareQrCodes(renders.value);
  previewHtml.value = renders.value.map(renderPrintHtml).join('');
}

async function loadTemplates(id: number) {
  const firstId = props.businessIds[0];
  if (firstId === undefined) return;
  // 加载失败由弹窗内 Alert 提示，不再弹全局 toast
  const r = await printApi.templateOptions(props.documentType, firstId, {suppressGlobalErrorMessage: true});
  if (id !== requestId) return;
  templates.value = r.data ?? [];
  const preferred = templates.value.find((item) => item.defaultFlag) ?? templates.value[0];
  templateId.value = preferred?.id;
}

/** 预览：只读，不产生打印记录。 */
async function reloadPreview() {
  if (printing.value) return;
  frozenByBusiness.clear();
  if (!props.businessIds.length) {
    renders.value = [];
    previewHtml.value = '';
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  renders.value = [];
  previewHtml.value = '';
  try {
    const list: PrintRender[] = [];
    for (const businessId of props.businessIds) {
      if (id !== requestId) return;
      const r = await printApi.preview(props.documentType, businessId, templateId.value, {suppressGlobalErrorMessage: true});
      list.push(r.data);
    }
    if (id === requestId) {
      renders.value = list;
      await refreshPreview();
    }
  } catch (e) {
    if (id === requestId) {
      renders.value = [];
      previewHtml.value = '';
      error.value = printError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

/** 正式打印：逐张保存打印记录后合并打印。失败重试只补未完成的单据。 */
async function print() {
  if (!canPrint.value) {
    return;
  }
  printing.value = true;
  error.value = '';
  try {
    const frozen: PrintRender[] = [];
    for (const businessId of props.businessIds) {
      const cacheKey = String(businessId);
      let render = frozenByBusiness.get(cacheKey);
      if (!render) {
        const r = await printApi.print(props.documentType, businessId, templateId.value);
        render = r.data;
        frozenByBusiness.set(cacheKey, render);
      }
      frozen.push(render);
    }
    renders.value = frozen;
    await refreshPreview();
    emit('printed');
    await printRenders(frozen);
    frozenByBusiness.clear();
    // 完成提示用右上角通知，不占预览区
    const firstFrozen = frozen[0];
    notification.success({
      message: `已生成 ${frozen.length} 张打印记录`,
      description: firstFrozen ? snapshotHint(firstFrozen) : undefined,
    });
  } catch {
    // 打印失败由全局 toast 提示
  } finally {
    printing.value = false;
  }
}

// 打开时重载，关闭时清空，避免看到上次的内容
watch(
  () => JSON.stringify([props.open, props.documentType, ...props.businessIds.map(String)]),
  () => {
    const id = ++requestId;
    frozenByBusiness.clear();
    templates.value = [];
    templateId.value = undefined;
    renders.value = [];
    error.value = '';
    loading.value = props.open;
    const open = props.open;
    if (!open) {
      renders.value = [];
      error.value = '';
      return;
    }
    void (async () => {
      try {
        await loadTemplates(id);
        if (id !== requestId) return;
        if (!templates.value.length) {
          error.value = '没有可用模板，请联系管理员启用该单据类型的模板。';
          loading.value = false;
          return;
        }
        await reloadPreview();
      } catch (e) {
        if (id === requestId) {
          error.value = printError(e);
          loading.value = false;
        }
      }
    })();
  },
  {immediate: true}
);

function close() {
  if (printing.value) return;
  emit('close');
}
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.banner {
  margin-bottom: 12px;
}

.preview {
  border: 1px solid var(--scm-border);
  border-radius: 4px;
  padding: 12px;
  background: #fff;
  color: #1f2329;
  max-height: 460px;
  overflow: auto;
}

/* 预览与打印文档排版一致：所见即所打 */
.preview :deep(h2) {
  font-size: 16px;
  margin: 0 0 8px;
}

.preview :deep(.meta .f),
.preview :deep(.totals .f) {
  margin-right: 18px;
}

.preview :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
  margin-top: 6px;
}

.preview :deep(th),
.preview :deep(td) {
  border: 1px solid #e5e6eb;
  padding: 4px 6px;
  text-align: left;
}

.preview :deep(.r) {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.preview :deep(.doc) {
  margin-bottom: 16px;
}

.preview :deep(.trace) {
  margin-top: 6px;
  color: #86909c;
  font-size: 11px;
}
</style>
