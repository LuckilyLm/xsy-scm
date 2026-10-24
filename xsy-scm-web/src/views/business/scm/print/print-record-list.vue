<!-- 打印记录只读；重印使用当时保存的内容，金额按当前权限隐藏。 -->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="单据类型" class="smart-query-form-item">
        <a-select v-model:value="queryForm.documentType" :options="typeOptions" style="width: 180px" placeholder="全部类型" />
      </a-form-item>
      <a-form-item label="业务单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.businessNo" placeholder="采购单号等" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:print:record:query'">查询</a-button>
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
    <a-table
      size="small"
      :data-source="tableData"
      :columns="columns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText: '还没有正式打印记录'}"
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'templateName'">{{ record.templateName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'templateVersion'">{{ record.templateVersion ?? '—' }}</template>
        <template v-else-if="column.dataIndex === 'printedAt'">{{ record.printedAt || '—' }}</template>
        <template v-else-if="column.dataIndex === 'printedBy'">{{ record.printedBy || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" :loading="reprinting === record.id" @click="reprint(record)">重印</a-button>
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
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {printApi} from '/@/api/business/scm/print-api';
import type {Id, PrintDocumentTypeOption, PrintRecord, PrintRecordQuery} from './print-types';
import {printRenders} from './print-render';
import {printError} from './print-errors';

const queryForm = reactive<PrintRecordQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<PrintRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const reprinting = ref<Id | undefined>(undefined);
const documentTypes = ref<PrintDocumentTypeOption[]>([]);
let requestId = 0;

const typeOptions = computed(() => documentTypes.value.map((item) => ({value: item.documentType, label: item.documentTypeLabel})));

const columns: TableColumnsType<PrintRecord> = [
  {title: '单据类型', dataIndex: 'documentTypeLabel', width: 130, align: 'center'},
  {title: '业务单号', dataIndex: 'businessNo', width: 190},
  {title: '模板名称', dataIndex: 'templateName', width: 170},
  {title: '模板版本', dataIndex: 'templateVersion', width: 90},
  {title: '打印时间', dataIndex: 'printedAt', width: 165},
  {title: '打印人', dataIndex: 'printedBy', width: 110},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 100},
];

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示，不再弹全局 toast
    const r = await printApi.recordQuery({...queryForm}, {suppressGlobalErrorMessage: true});
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
    // 类型下拉拉不到就留空，不提示
    const r = await printApi.documentTypes({suppressGlobalErrorMessage: true});
    documentTypes.value = r.data ?? [];
  } catch {
    documentTypes.value = [];
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.documentType = undefined;
  queryForm.businessNo = undefined;
  onSearch();
}

/** 重印使用当时保存的内容；金额按当前权限重新过滤。 */
async function reprint(record: PrintRecord) {
  reprinting.value = record.id;
  error.value = '';
  try {
    const r = await printApi.reprint(record.id);
    await printRenders([r.data]);
    if ((r.data.hiddenFields ?? []).length) {
      message.warning(`已重印，但 ${r.data.hiddenFields?.length} 个金额字段因权限未打印`);
    }
  } catch {
    // 重印失败由全局 toast 提示
  } finally {
    reprinting.value = undefined;
  }
}

onMounted(async () => {
  await loadDocumentTypes();
  await queryData();
});
</script>
