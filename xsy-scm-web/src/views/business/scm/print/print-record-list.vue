<!--
  打印记录（打印中心）。

  记录是冻结快照：谁在什么时候、按哪份模板的哪一版、打了哪张单。表上有触发器拒绝
  UPDATE / DELETE，本页因此只有查询与「重印」两个动作。

  重印只读快照、不回业务表重算，但金额会按当前操作者的权限重新剔除 ——
  快照冻结的是内容，不是授权；否则一条历史记录就能把金额权限绕过去。
-->
<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="单据类型" class="smart-query-form-item">
        <a-select v-model:value="queryForm.documentType" :options="typeOptions" style="width: 180px"
                  placeholder="全部类型"/>
      </a-form-item>
      <a-form-item label="业务单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.businessNo" placeholder="采购单号等" allow-clear @pressEnter="onSearch"/>
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
        :locale="{ emptyText: '还没有正式打印记录' }"
        :scroll="{ x: 820 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'template'">
          <!-- 模板名作主行，「编码 + 版本」作次要行：打印记录是审计凭据，
               必须能说清当初用的是哪一版模板，但不需要为它多占两列 -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.templateName || '—' }}</span>
            <span class="scm-cell-stack__sub">
              {{ record.templateCode || '—' }}<template v-if="record.templateVersion != null"> · v{{ record.templateVersion }}</template>
            </span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'printed'">
          <!-- 「谁在什么时候打的」是同一件事的两面，合成一格 -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.printedAt || '—' }}</span>
            <span v-if="record.printedBy" class="scm-cell-stack__sub">{{ record.printedBy }}</span>
          </div>
        </template>
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

const typeOptions = computed(() =>
    documentTypes.value.map((item) => ({value: item.documentType, label: item.documentTypeLabel}))
);

const columns: TableColumnsType<PrintRecord> = [
  {title: '单据类型', dataIndex: 'documentTypeLabel', width: 130, align: 'center'},
  {title: '业务单号', dataIndex: 'businessNo', width: 190},
  {title: '模板', dataIndex: 'template', width: 220},
  {title: '打印', dataIndex: 'printed', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 100},
];

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await printApi.recordQuery({...queryForm});
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

/** 重印：只读冻结快照；金额按当前操作者的权限重新剔除。 */
async function reprint(record: PrintRecord) {
  reprinting.value = record.id;
  error.value = '';
  try {
    const r = await printApi.reprint(record.id);
    await printRenders([r.data]);
    if ((r.data.hiddenFields ?? []).length) {
      message.warning(`已重印，但 ${r.data.hiddenFields?.length} 个金额字段因权限未打印`);
    }
  } catch (e) {
    error.value = printError(e);
  } finally {
    reprinting.value = undefined;
  }
}

onMounted(async () => {
  await loadDocumentTypes();
  await queryData();
});
</script>
