<!-- 客户专属模板：一个客户对一种单据类型最多一份模板，打印时优先于默认模板。 -->
<template>
  <a-drawer :open="open" title="客户专属模板" :width="scmDrawerWidth('l')" @close="close">
    <a-alert v-if="error" :message="error" type="error" show-icon class="banner" />

    <a-form layout="inline" class="binding-form" @submit.prevent>
      <a-form-item label="单据类型" required>
        <a-select
          v-model:value="form.documentType"
          :options="typeOptions"
          style="width: 160px"
          placeholder="请选择单据类型"
          @change="onDocumentTypeChange"
        />
      </a-form-item>
      <a-form-item label="客户" required>
        <CustomerSelect v-model:value="form.customerId" width="200px" />
      </a-form-item>
      <a-form-item label="打印模板" required>
        <a-select
          v-model:value="form.templateId"
          :options="templateOptions"
          :loading="templateLoading"
          style="width: 220px"
          placeholder="请选择启用中的模板"
          :disabled="!form.documentType"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="binding" :disabled="!canBind" @click="bind">绑定</a-button>
      </a-form-item>
    </a-form>

    <p class="hint">绑定后，打印该客户的对应单据时默认使用这份模板；打印时显式选择其它模板仍可覆盖。</p>

    <a-divider orientation="left">已绑定</a-divider>
    <a-form layout="inline" class="filter-form" @submit.prevent>
      <a-form-item label="单据类型">
        <a-select v-model:value="filter.documentType" :options="typeOptions" style="width: 160px" allow-clear placeholder="全部类型" />
      </a-form-item>
      <a-form-item label="客户">
        <CustomerSelect v-model:value="filter.customerId" width="200px" />
      </a-form-item>
      <a-form-item>
        <a-button :loading="loading" @click="loadBindings">查询</a-button>
      </a-form-item>
    </a-form>

    <a-table size="small" :data-source="bindings" :columns="columns" row-key="id" bordered :loading="loading" :pagination="false" :scroll="{x: 720}">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'templateEnabled'">
          <ScmStatusTag :tone="record.templateEnabled ? 'success' : 'neutral'" :label="record.templateEnabled ? '启用' : '已停用'" />
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" danger @click="unbind(record)">解除绑定</a-button>
        </template>
      </template>
    </a-table>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {Id, PrintCustomerTemplate, PrintCustomerTemplateSave, PrintDocumentType, PrintDocumentTypeOption, PrintTemplate} from './print-types';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {printError} from './print-errors';

const props = defineProps<{open: boolean}>();
const emit = defineEmits<{close: []}>();

const documentTypes = ref<PrintDocumentTypeOption[]>([]);
const templates = ref<PrintTemplate[]>([]);
const bindings = ref<PrintCustomerTemplate[]>([]);
const loading = ref(false);
const templateLoading = ref(false);
const binding = ref(false);
const error = ref('');

const form = reactive<{documentType?: PrintDocumentType; customerId?: Id; templateId?: Id}>({
  documentType: undefined,
  customerId: undefined,
  templateId: undefined,
});
const filter = reactive<{documentType?: PrintDocumentType; customerId?: Id}>({
  documentType: undefined,
  customerId: undefined,
});

/** 只有能唯一对应客户的单据类型才提供绑定。 */
const bindableTypes = computed(() => documentTypes.value.filter((item) => item.customerBindable));
const typeOptions = computed(() => bindableTypes.value.map((item) => ({value: item.documentType, label: item.documentTypeLabel})));
const templateOptions = computed(() =>
  templates.value.map((item) => ({value: item.id, label: `${item.templateName}${item.defaultFlag ? '（默认）' : ''}`}))
);
const canBind = computed(() => Boolean(form.documentType) && form.customerId != null && form.templateId != null && !binding.value);

const columns: TableColumnsType<PrintCustomerTemplate> = [
  {title: '单据类型', dataIndex: 'documentTypeLabel', width: 130, align: 'center'},
  {title: '客户', dataIndex: 'customerName', width: 180},
  {title: '客户专属模板', dataIndex: 'templateName', width: 200},
  {title: '状态', dataIndex: 'templateEnabled', width: 90},
  {title: '更新时间', dataIndex: 'updatedAt', width: 180},
  {title: '操作', dataIndex: 'action', width: 100, align: 'center', fixed: 'right'},
];

async function loadDocumentTypes() {
  if (documentTypes.value.length) return;
  try {
    const response = await printApi.documentTypes({suppressGlobalErrorMessage: true});
    documentTypes.value = response.data ?? [];
    if (!form.documentType) {
      form.documentType = bindableTypes.value[0]?.documentType;
    }
  } catch (e) {
    error.value = printError(e);
  }
}

async function loadTemplates() {
  const documentType = form.documentType;
  templates.value = [];
  form.templateId = undefined;
  if (!documentType) return;
  templateLoading.value = true;
  try {
    const response = await printApi.templateQuery({documentType, enabledFlag: true, pageNum: 1, pageSize: 100}, {suppressGlobalErrorMessage: true});
    templates.value = response.data.list ?? [];
  } catch (e) {
    error.value = printError(e);
  } finally {
    templateLoading.value = false;
  }
}

async function loadBindings() {
  loading.value = true;
  error.value = '';
  try {
    const response = await printApi.customerBindings(
      {documentType: filter.documentType, customerId: filter.customerId},
      {suppressGlobalErrorMessage: true}
    );
    bindings.value = response.data ?? [];
  } catch (e) {
    error.value = printError(e);
  } finally {
    loading.value = false;
  }
}

async function onDocumentTypeChange() {
  await loadTemplates();
}

async function bind() {
  if (!canBind.value || !form.documentType || form.customerId == null || form.templateId == null) return;
  binding.value = true;
  error.value = '';
  const payload: PrintCustomerTemplateSave = {
    customerId: form.customerId,
    documentType: form.documentType,
    templateId: form.templateId,
  };
  try {
    await printApi.customerBind(payload);
    message.success('客户专属模板已绑定');
    await loadBindings();
  } catch {
    // 绑定失败由全局 toast 提示
  } finally {
    binding.value = false;
  }
}

function unbind(record: PrintCustomerTemplate) {
  Modal.confirm({
    title: '解除绑定',
    content: `确定解除客户「${record.customerName ?? ''}」的${record.documentTypeLabel ?? ''}专属模板吗？之后打印会回退到默认模板。`,
    okType: 'danger',
    onOk: async () => {
      await printApi.customerUnbind(record.documentType, record.customerId);
      message.success('已解除绑定');
      await loadBindings();
    },
  });
}

function close() {
  emit('close');
}

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    error.value = '';
    void (async () => {
      await loadDocumentTypes();
      await loadTemplates();
      await loadBindings();
    })();
  },
  {immediate: true}
);
</script>

<style scoped>
.banner {
  margin-bottom: 12px;
}
.binding-form,
.filter-form {
  margin-bottom: 12px;
}
.hint {
  color: var(--scm-text-secondary);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
