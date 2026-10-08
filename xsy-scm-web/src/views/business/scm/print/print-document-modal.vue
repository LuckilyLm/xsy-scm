<!--
  打印对话框（共享）：选模板 → 预览 → 正式打印。

  三类动作的边界在这里体现：
  * 「刷新预览」走 GET preview：只读，不计次、不留痕；
  * 「打印」走 POST print：服务端先把模板版本与版面冻结成一条打印记录，再返回冻结版面，
    前端拿这份冻结版面去调浏览器打印。因此纸上打出来的与库里记下来的逐字一致；
  * 打印不改业务单据状态：本组件不调用任何业务写接口。

  预览区用 v-html 渲染 `renderPrintHtml` 的输出：该函数对所有数据字段都做了转义
  （模板文本在服务端已拒绝尖括号），因此这里没有可注入的路径。
-->
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
      :ok-button-props="{ disabled: !canPrint }"
      @ok="print"
      @cancel="close"
  >
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
    <a-alert
        v-if="frozenHint"
        class="banner"
        type="success"
        show-icon
        message="已生成打印快照"
        :description="frozenHint"
    />

    <a-spin :spinning="loading">
      <div v-if="renders.length" class="preview" v-html="previewHtml"/>
      <a-empty v-else-if="!loading && !error" description="没有可预览的内容"/>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {message} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {Id, PrintDocumentType, PrintRender, PrintTemplate} from './print-types';
import {renderPrintHtml, printRenders} from './print-render';
import {printError} from './print-errors';
import {useScmErrorToast} from '../common/scm-error-toast';

const props = defineProps<{
  open: boolean;
  documentType: PrintDocumentType;
  /** 批量打印时多张单据共用一个模板，逐张冻结、逐张成页。 */
  businessIds: Id[];
}>();

const emit = defineEmits<{ close: []; printed: [] }>();

const templates = ref<PrintTemplate[]>([]);
const templateId = ref<Id | undefined>(undefined);
const renders = ref<PrintRender[]>([]);
const loading = ref(false);
const printing = ref(false);
const error = useScmErrorToast();
let requestId = 0;
const frozenByBusiness = new Map<string, PrintRender>();

const templateOptions = computed(() =>
    templates.value.map((item) => ({
      value: item.id,
      label: `${item.templateName}${item.defaultFlag ? '（默认）' : ''}`,
    }))
);

const canPrint = computed(() => !loading.value && !printing.value && renders.value.length > 0
    && renders.value.length === props.businessIds.length && templateId.value !== undefined);

const hiddenFields = computed(() => {
  const keys = new Set<string>();
  renders.value.forEach((render) => (render.hiddenFields ?? []).forEach((key) => keys.add(key)));
  return Array.from(keys);
});

const frozenHint = computed(() => {
  const first = renders.value.find((render) => render.frozen);
  if (!first) {
    return '';
  }
  return `模板 ${first.templateCode} v${first.templateVersion}，打印时间 ${first.printedAt ?? '—'}。重印只读这份快照，不随业务数据变化。`;
});

const previewHtml = computed(() => renders.value.map(renderPrintHtml).join(''));

async function loadTemplates(id: number) {
  const firstId = props.businessIds[0];
  if (firstId === undefined) return;
  const r = await printApi.templateOptions(props.documentType, firstId);
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
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  renders.value = [];
  try {
    const list: PrintRender[] = [];
    for (const businessId of props.businessIds) {
      if (id !== requestId) return;
      const r = await printApi.preview(props.documentType, businessId, templateId.value);
      list.push(r.data);
    }
    if (id === requestId) {
      renders.value = list;
    }
  } catch (e) {
    if (id === requestId) {
      renders.value = [];
      error.value = printError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

/**
 * 正式打印：逐张冻结后合并成一份文档打印。
 *
 * 中途失败时保留已成功冻结的结果；同一对话框直接重试只提交尚未完成的单据。
 * 刷新预览、切换模板或重新打开表示新的一次打印，清空本次结果。
 */
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
    emit('printed');
    await printRenders(frozen);
    frozenByBusiness.clear();
    message.success(`已打开 ${frozen.length} 张单据的打印窗口`);
  } catch (e) {
    error.value = printError(e);
  } finally {
    printing.value = false;
  }
}

// 打开时重载模板与预览；关闭时清空，避免下次打开看到上一次的内容
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

/* 预览区沿用打印文档的排版口径：所见即所打 */
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
