<!--
  * 心跳记录
  *
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert>
      <template v-slot:message>
        <h4>服务运行状态说明：</h4>
      </template>
      <template v-slot:description>
        <pre>
简介：服务定期上报运行信息，便于查看当前状态。
列表显示服务地址、进程号及最近上报时间，可按关键字和时间查询。

用途：
1）查看已上报的服务列表；
2）根据最近上报时间排查服务是否仍在运行；
3）核对是否有重复启动的服务。</pre
        >
      </template>
    </a-alert>

    <a-form class="xsy-query-form">
      <a-row class="xsy-query-form-row">
        <a-form-item label="关键字" class="xsy-query-form-item">
          <a-input style="width: 300px" v-model:value="queryForm.keywords" placeholder="关键字" />
        </a-form-item>

        <a-form-item label="心跳时间" class="xsy-query-form-item">
          <a-range-picker @change="changeCreateDate" v-model:value="createDateRange" :presets="defaultChooseTimeRange" style="width: 240px" />
        </a-form-item>

        <a-form-item class="xsy-query-form-item xsy-margin-left10">
          <a-button-group>
            <a-button type="primary" @click="onSearch">
              <template #icon>
                <SearchOutlined />
              </template>
              查询
            </a-button>
            <a-button @click="resetQuery">
              <template #icon>
                <ReloadOutlined />
              </template>
              重置
            </a-button>
          </a-button-group>
        </a-form-item>
      </a-row>
    </a-form>
    <a-row justify="end">
      <TableOperator class="xsy-margin-bottom5" v-model="columns" :tableId="TABLE_ID_CONST.SUPPORT.HEART_BEAT" :refresh="ajaxQuery" />
    </a-row>
    <a-table
      size="small"
      bordered
      :loading="tableLoading"
      class="xsy-margin-top10"
      :dataSource="tableData"
      :columns="columns"
      rowKey="goodsId"
      :pagination="false"
    />
    <div class="xsy-query-table-page">
      <a-pagination
        showSizeChanger
        showQuickJumper
        show-less-items
        :pageSizeOptions="PAGE_SIZE_OPTIONS"
        :defaultPageSize="queryForm.pageSize"
        v-model:current="queryForm.pageNum"
        v-model:pageSize="queryForm.pageSize"
        :total="total"
        @change="ajaxQuery"
        :show-total="(total) => `共${total}条`"
      />
    </div>
  </a-card>
</template>
<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {heartBeatApi} from '/@/api/support/heart-beat-api';
import {PAGE_SIZE_OPTIONS} from '/@/constants/common-const';
import {defaultTimeRanges} from '/@/lib/default-time-ranges';
import {xsySentry} from '/@/lib/xsy-sentry';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';

//------------------------ 时间选择 ---------------------
const defaultChooseTimeRange = defaultTimeRanges;
const createDateRange = ref([]);

// 时间变动
function changeCreateDate(dates, dateStrings) {
  queryForm.startDate = dateStrings[0];
  queryForm.endDate = dateStrings[1];
}

//------------------------ 表格渲染 ---------------------

const columns = ref([
  {
    title: '项目路径',
    dataIndex: 'projectPath',
    ellipsis: true,
  },
  {
    title: '服务器ip',
    dataIndex: 'serverIp',
    ellipsis: true,
  },
  {
    title: '进程号',
    dataIndex: 'processNo',
    width: 100,
  },
  {
    title: '进程开启时间',
    dataIndex: 'processStartTime',
    width: 150,
  },
  {
    title: '心跳当前时间',
    dataIndex: 'heartBeatTime',
    width: 150,
  },
]);

const queryFormState = {
  pageNum: 1,
  pageSize: 10,
  keywords: '',
  startDate: undefined,
  endDate: undefined,
};
const queryForm = reactive({...queryFormState});
const tableLoading = ref(false);
const tableData = ref([]);
const total = ref(0);

function resetQuery() {
  Object.assign(queryForm, queryFormState);
  createDateRange.value = [];
  ajaxQuery();
}

function onSearch() {
  queryForm.pageNum = 1;
  ajaxQuery();
}

async function ajaxQuery() {
  try {
    tableLoading.value = true;
    let responseModel = await heartBeatApi.queryList(queryForm);
    const list = responseModel.data.list;
    total.value = responseModel.data.total;
    tableData.value = list;
  } catch (e) {
    xsySentry.captureError(e);
  } finally {
    tableLoading.value = false;
  }
}

onMounted(ajaxQuery);
</script>
