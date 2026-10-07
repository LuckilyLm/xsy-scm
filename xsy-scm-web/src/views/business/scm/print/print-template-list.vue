<!--
  打印模板配置（打印中心）。

  模板是受控模型：只能从该单据类型的字段目录里挑表头字段与明细列，加上标题、纸张、方向、
  是否合计、页脚备注。没有 HTML、没有脚本、没有表达式 —— 因此不存在「模板执行任意代码」
  这条路径，也不需要一套模板求值器。

  字段目录由后端下发（`/template/catalog`），本页不硬编码字段清单：加一个字段只改后端。
  金额字段会列出来但会提示「没有金额权限时不会打印」—— 服务端确实会剔除，藏着它只会让
  用户以为配置没生效。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="单据类型" class="smart-query-form-item">
        <a-select v-model:value="queryForm.documentType" :options="typeOptions" style="width: 180px"
                  placeholder="全部类型"/>
      </a-form-item>
      <a-form-item label="关键字" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" placeholder="模板编码 / 名称" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:print:template:query'">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:print:template:add'" @click="openCreate">新建模板</a-button>
        <span class="hint">每种单据至多一个默认模板</span>
      </div>
    </a-row>

    <a-table
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1100 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'defaultFlag'">
          <ScmStatusTag v-if="record.defaultFlag" tone="processing" label="默认"/>
          <span v-else class="scm-cell-hint">—</span>
        </template>
        <template v-else-if="column.dataIndex === 'enabledFlag'">
          <ScmStatusTag :tone="record.enabledFlag ? 'success' : 'neutral'"
                        :label="record.enabledFlag ? '启用' : '停用'"/>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" v-privilege="'scm:print:template:update'" @click="openEdit(record)">编辑</a-button>
            <!-- 操作列超过 3 个动作就要收敛。「设为默认」与「删除」是低频且后者危险，
                 进「更多」；v-privilege 对菜单项不生效，权限在 `rowActions` 里用 hasPermission 裁剪。 -->
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)"/>
          </a-space>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共${n}条`"
      />
    </div>
  </a-card>

  <a-modal
      :open="editOpen"
      :title="form.id ? '编辑打印模板' : '新建打印模板'"
      width="860px"
      :confirm-loading="saving"
      @ok="submit"
      @cancel="editOpen = false"
  >
    <a-alert v-if="editError" :message="editError" type="error" show-icon class="banner"/>
    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="单据类型" required>
            <a-select v-model:value="form.documentType" :options="typeOptions" :disabled="!!form.id"
                      @change="loadCatalog"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="模板编码" required>
            <a-input v-model:value="form.templateCode" :disabled="!!form.id"
                     placeholder="字母、数字、下划线或连字符"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="模板名称" required>
            <a-input v-model:value="form.templateName"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="备注">
            <a-input v-model:value="form.remark"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="设为默认">
            <a-switch v-model:checked="form.defaultFlag"/>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="启用">
            <a-switch v-model:checked="form.enabledFlag"/>
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider orientation="left">版式</a-divider>
      <a-row :gutter="12">
        <a-col :span="8">
          <a-form-item label="标题" required>
            <a-input v-model:value="form.model.title" placeholder="纯文本，不能含尖括号"/>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="纸张">
            <a-select v-model:value="form.model.paper" :options="paperOptions" @change="onPaperChange"/>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="方向">
            <a-select v-model:value="form.model.orientation" :options="orientationOptions"/>
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="页脚备注">
            <a-input v-model:value="form.model.footerNote" placeholder="纯文本，例如联次说明"/>
          </a-form-item>
        </a-col>
        <a-col :span="24">
          <a-form-item label="打印合计行">
            <a-switch v-model:checked="form.model.showTotals"/>
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider orientation="left">表头字段</a-divider>
      <a-alert v-if="catalog && !catalog.amountVisible && catalog.amountPermissionRequired" class="banner" type="warning"
               show-icon message="你没有该单据类型的金额权限，金额字段即使勾选也不会打印出来"/>
      <a-checkbox-group v-model:value="form.model.headerFields" :options="headerOptions"/>

      <a-divider orientation="left">明细列（至少一列）</a-divider>
      <a-checkbox-group v-model:value="form.model.columns" :options="columnOptions"/>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {
  Id,
  PrintDocumentType,
  PrintDocumentTypeOption,
  PrintFieldCatalog,
  PrintPaper,
  PrintTemplate,
  PrintTemplateQuery,
  PrintTemplateSave,
} from './print-types';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {hasPermission} from '../common/scm-permission';
import {printError} from './print-errors';

const queryForm = reactive<PrintTemplateQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<PrintTemplate[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');

const documentTypes = ref<PrintDocumentTypeOption[]>([]);
const catalog = ref<PrintFieldCatalog>();
const editOpen = ref(false);
const saving = ref(false);
const editError = ref('');
let requestId = 0;

const typeOptions = computed(() =>
    documentTypes.value.map((item) => ({value: item.documentType, label: item.documentTypeLabel}))
);

const paperOptions = [
  {value: 'A4', label: 'A4 纸'},
  {value: 'TICKET_80', label: '80mm 小票'},
];

const orientationOptions = [
  {value: 'PORTRAIT', label: '纵向'},
  {value: 'LANDSCAPE', label: '横向'},
];

/** 字段选项：金额字段在标签上标出来，避免用户以为「勾了就能打」。 */
const headerOptions = computed(() => toOptions(catalog.value?.headerFields));
const columnOptions = computed(() => toOptions(catalog.value?.columns));

function toOptions(fields?: { key: string; label: string; money: boolean }[]) {
  return (fields ?? []).map((field) => ({
    value: field.key,
    label: field.money ? `${field.label}（金额）` : field.label,
  }));
}

const columns: TableColumnsType<PrintTemplate> = [
  {title: '单据类型', dataIndex: 'documentTypeLabel', width: 130},
  {title: '模板编码', dataIndex: 'templateCode', width: 200},
  {title: '模板名称', dataIndex: 'templateName', width: 180},
  {title: '默认', dataIndex: 'defaultFlag', align: 'center', width: 90},
  {title: '状态', dataIndex: 'enabledFlag', align: 'center', width: 90},
  {title: '版本', dataIndex: 'version', align: 'right', width: 80},
  {title: '更新时间', dataIndex: 'updatedAt', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
];

const form = reactive<PrintTemplateSave & { id?: Id }>({
  documentType: 'PURCHASE_ORDER',
  templateCode: '',
  templateName: '',
  defaultFlag: false,
  enabledFlag: true,
  remark: null,
  model: {
    title: '',
    paper: 'A4',
    orientation: 'PORTRAIT',
    headerFields: [],
    columns: [],
    showTotals: true,
    footerNote: null,
  },
});

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await printApi.templateQuery({...queryForm});
    if (id === requestId) {
      tableData.value = r.data.list ?? [];
      total.value = r.data.total ?? 0;
    }
  } catch (e) {
    if (id === requestId) {
      error.value = printError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

async function loadDocumentTypes() {
  try {
    const r = await printApi.documentTypes();
    documentTypes.value = r.data ?? [];
  } catch (e) {
    error.value = printError(e);
  }
}

async function loadCatalog() {
  try {
    const r = await printApi.templateCatalog(form.documentType as PrintDocumentType);
    catalog.value = r.data;
  } catch (e) {
    editError.value = printError(e);
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.documentType = undefined;
  queryForm.keyword = undefined;
  onSearch();
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    documentType: documentTypes.value[0]?.documentType ?? 'PURCHASE_ORDER',
    templateCode: '',
    templateName: '',
    defaultFlag: false,
    enabledFlag: true,
    remark: null,
    version: undefined,
    model: {
      title: '',
      paper: 'A4' as PrintPaper,
      orientation: 'PORTRAIT',
      headerFields: [],
      columns: [],
      showTotals: true,
      footerNote: null,
    },
  });
  editError.value = '';
  editOpen.value = true;
  void loadCatalog();
}

function openEdit(record: PrintTemplate) {
  Object.assign(form, {
    id: record.id,
    documentType: record.documentType,
    templateCode: record.templateCode,
    templateName: record.templateName,
    defaultFlag: record.defaultFlag ?? false,
    enabledFlag: record.enabledFlag ?? true,
    remark: record.remark ?? null,
    version: record.version,
    model: {
      title: record.model?.title ?? '',
      paper: record.model?.paper ?? 'A4',
      orientation: record.model?.orientation ?? 'PORTRAIT',
      headerFields: [...(record.model?.headerFields ?? [])],
      columns: [...(record.model?.columns ?? [])],
      showTotals: record.model?.showTotals ?? true,
      footerNote: record.model?.footerNote ?? null,
    },
  });
  editError.value = '';
  editOpen.value = true;
  void loadCatalog();
}

/** 80mm 小票没有横向：切到小票时把方向拉回纵向，避免提交时才报 41308。 */
function onPaperChange(paper: PrintPaper) {
  if (paper === 'TICKET_80') {
    form.model.orientation = 'PORTRAIT';
  }
}

async function submit() {
  editError.value = '';
  if (!form.templateCode || !form.templateName || !form.model.title) {
    editError.value = '模板编码、模板名称与标题都必须填写';
    return;
  }
  if (!form.model.columns?.length) {
    editError.value = '至少要选择一列明细字段';
    return;
  }
  saving.value = true;
  try {
    if (form.id) {
      await printApi.templateUpdate({...form});
      message.success('模板已更新');
    } else {
      await printApi.templateCreate({...form});
      message.success('模板已创建');
    }
    editOpen.value = false;
    await queryData();
  } catch (e) {
    editError.value = printError(e);
  } finally {
    saving.value = false;
  }
}

/** 更多菜单：只有「设为默认」与「删除」，两者都只对非默认模板出现。 */
function rowActions(record: PrintTemplate): ScmActionItem[] {
  const isDefault = !!record.defaultFlag;
  return [
    {key: 'setDefault', label: '设为默认', hidden: isDefault || !hasPermission('scm:print:template:update')},
    {key: 'delete', label: '删除', danger: true, hidden: isDefault || !hasPermission('scm:print:template:delete')},
  ];
}

function onRowAction(key: string, record: PrintTemplate) {
  if (key === 'setDefault') {
    void setDefault(record);
  } else if (key === 'delete') {
    remove(record);
  }
}

async function setDefault(record: PrintTemplate) {
  try {
    await printApi.templateSetDefault(record.id);
    message.success('已设为默认模板');
    await queryData();
  } catch (e) {
    error.value = printError(e);
  }
}

function remove(record: PrintTemplate) {
  Modal.confirm({
    title: '删除打印模板',
    content: `确定删除「${record.templateName}」吗？历史打印记录保留原快照，不受影响。`,
    okType: 'danger',
    onOk: async () => {
      await printApi.templateDelete(record.id, record.version ?? 0);
      message.success('模板已删除');
      await queryData();
    },
  });
}

onMounted(async () => {
  await loadDocumentTypes();
  await queryData();
});
</script>

<style scoped>
.hint {
  color: var(--scm-text-secondary);
  font-size: 12px;
  margin-left: 8px;
}

.banner {
  margin-bottom: 12px;
}
</style>
