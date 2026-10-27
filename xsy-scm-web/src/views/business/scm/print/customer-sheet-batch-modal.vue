<!-- 20:00 批量打印：按配送日期一次取当天全部客户，分别打印分拣单或送货单。 -->
<template>
  <a-modal
    :open="open"
    title="批量打印客户单据"
    width="1100px"
    :mask-closable="false"
    :confirm-loading="printing"
    :closable="!printing"
    ok-text="打印"
    cancel-text="关闭"
    :ok-button-props="{disabled: !canPrint}"
    @ok="print"
    @cancel="close"
  >
    <a-form layout="inline" class="toolbar">
      <a-form-item label="单据">
        <a-radio-group v-model:value="documentType" :disabled="printing" @change="reload">
          <a-radio value="CUSTOMER_SORTING_SHEET">客户分拣单（无价格）</a-radio>
          <a-radio value="CUSTOMER_DELIVERY_NOTE">客户送货单（含价格）</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="配送日期">
        <a-date-picker v-model:value="businessDate" value-format="YYYY-MM-DD" :disabled="printing" @change="reload" />
      </a-form-item>
      <a-form-item>
        <a-button :loading="loading" :disabled="printing" @click="reload">刷新预览</a-button>
      </a-form-item>
    </a-form>

    <a-alert v-if="error" type="error" show-icon :message="error" class="banner" />
    <a-alert
      v-if="result && result.failed"
      type="warning"
      show-icon
      class="banner"
      :message="`${result.failed} 单未打印成功，其余 ${result.succeeded} 单可正常打印`"
    >
      <template #description>
        <div v-for="item in failedItems" :key="String(item.orderId)">
          {{ item.orderNo || item.orderId }} {{ item.customerName || '' }}：{{ item.message || '打印失败' }}
        </div>
      </template>
    </a-alert>

    <div class="summary">
      <span>共 {{ result?.total ?? 0 }} 单</span>
      <span>可打印 {{ result?.succeeded ?? 0 }} 单</span>
      <span v-if="result?.failed">失败 {{ result.failed }} 单</span>
    </div>

    <a-spin :spinning="loading">
      <div v-if="previewHtml" class="preview" v-html="previewHtml" />
      <a-empty v-else-if="!loading && !error" description="该配送日期没有已确认订单" />
    </a-spin>
  </a-modal>
</template>
<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import dayjs from 'dayjs';
import {message, notification} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {PrintBatchResult} from './print-types';
import {printRenders, renderPrintHtml} from './print-render';
import {printError} from './print-errors';

const props = defineProps<{open: boolean}>();
const emit = defineEmits<{close: []; printed: []}>();

const documentType = ref<'CUSTOMER_SORTING_SHEET' | 'CUSTOMER_DELIVERY_NOTE'>('CUSTOMER_SORTING_SHEET');
const businessDate = ref(dayjs().add(1, 'day').format('YYYY-MM-DD'));
const result = ref<PrintBatchResult | null>(null);
const previewHtml = ref('');
const loading = ref(false);
const printing = ref(false);
const error = ref('');
let requestId = 0;
const batchKey = ref('');

const failedItems = computed(() => (result.value?.items ?? []).filter((item) => !item.success));
const canPrint = computed(() => !loading.value && !printing.value && (result.value?.renders.length ?? 0) > 0);

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    result.value = null;
    previewHtml.value = '';
    error.value = '';
    batchKey.value = crypto.randomUUID();
    void reload();
  }
);

async function reload() {
  if (printing.value) return;
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  result.value = null;
  previewHtml.value = '';
  try {
    const r = await printApi.batchPreview(
      {documentType: documentType.value, businessDate: businessDate.value},
      {suppressGlobalErrorMessage: true}
    );
    if (id !== requestId) return;
    result.value = r.data;
    previewHtml.value = r.data.renders.map(renderPrintHtml).join('');
  } catch (e) {
    if (id === requestId) error.value = printError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

async function print() {
  if (!canPrint.value) return;
  printing.value = true;
  error.value = '';
  try {
    const r = await printApi.batchPrint({
      documentType: documentType.value,
      businessDate: businessDate.value,
      batchKey: batchKey.value,
    });
    result.value = r.data;
    previewHtml.value = r.data.renders.map(renderPrintHtml).join('');
    emit('printed');
    if (r.data.renders.length) {
      await printRenders(r.data.renders);
    }
    notification.success({
      message: `已生成 ${r.data.succeeded} 张打印记录`,
      description: r.data.failed ? `${r.data.failed} 单失败，可在失败明细中单独重试` : '全部客户单据已生成',
    });
  } catch (e) {
    error.value = printError(e);
    message.error(error.value);
  } finally {
    printing.value = false;
  }
}

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

.summary {
  display: flex;
  gap: 18px;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.55);
  font-variant-numeric: tabular-nums;
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
</style>
