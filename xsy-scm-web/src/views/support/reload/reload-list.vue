<!--
  * reload
  *
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert>
      <template v-slot:message>
        <h4>配置刷新说明：</h4>
      </template>
      <template v-slot:description>
        <pre>
简介：无需重启服务，即可刷新已配置的缓存和运行参数。

原理：
- 点击“执行”并填写对应参数后，系统会通知各服务处理。
- 可通过“查看结果”核对各服务是否执行成功。
用途：
· 刷新已注册的缓存
· 重新加载已配置的运行参数
· 查询执行结果并排查失败原因
</pre
        >
      </template>
    </a-alert>

    <a-row justify="end">
      <TableOperator class="xsy-margin-bottom5 xsy-margin-top5" v-model="columns" :tableId="TABLE_ID_CONST.SUPPORT.RELOAD" :refresh="ajaxQuery" />
    </a-row>

    <LocalTable size="small" bordered class="xsy-margin-top10" :dataSource="tableData" :loading="tableLoading" :columns="columns" rowKey="tag">
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'action'">
          <div class="xsy-table-operate">
            <a-button @click="doReload(record.tag)" v-privilege="'support:reload:execute'" type="link">执行</a-button>
            <a-button @click="showResultList(record.tag)" v-privilege="'support:reload:result'" type="link">查看结果 </a-button>
          </div>
        </template>
      </template>
    </LocalTable>

    <DoReloadForm @refresh="ajaxQuery" ref="doReloadForm" />
    <ReloadResultList ref="reloadResultList" />
  </a-card>
</template>
<script setup lang="ts">
import LocalTable from '/@/components/support/local-table/index.vue';
import {onMounted, ref} from 'vue';
import DoReloadForm from './do-reload-form-modal.vue';
import ReloadResultList from './reload-result-list.vue';
import {reloadApi} from '/@/api/support/reload-api';
import {xsySentry} from '/@/lib/xsy-sentry';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';

//------------------------ 表格渲染 ---------------------

const columns = ref([
  {
    title: '标签',
    dataIndex: 'tag',
    width: 200,
  },
  {
    title: '运行标识',
    dataIndex: 'identification',
  },
  {
    title: '参数',
    dataIndex: 'args',
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 150,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
  {
    title: '操作',
    dataIndex: 'action',
    fixed: 'right',
    width: 150,
    align: 'center',
  },
]);

const tableLoading = ref(false);
const tableData = ref([]);

async function ajaxQuery() {
  try {
    tableLoading.value = true;
    let res = await reloadApi.queryList();
    tableData.value = res.data;
  } catch (e) {
    xsySentry.captureError(e);
  } finally {
    tableLoading.value = false;
  }
}

onMounted(ajaxQuery);

// ------------------------------ 表格操作列： 执行 reload ------------------------------
const doReloadForm = ref();

function doReload(tag) {
  doReloadForm.value.showModal(tag);
}

// ------------------------------ 表格操作列： 查看执行结果 ------------------------------

const reloadResultList = ref();

function showResultList(tag) {
  reloadResultList.value.showModal(tag);
}
</script>
