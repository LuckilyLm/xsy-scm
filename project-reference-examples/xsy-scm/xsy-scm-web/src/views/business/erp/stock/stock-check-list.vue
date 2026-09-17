<!--
  * 库存盘点单列表
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="盘点类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CHECK_TYPE_ENUM" v-model:value="queryForm.checkType" width="160px" />
      </a-form-item>

      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CHECK_STATUS_ENUM" v-model:value="queryForm.status" width="160px" />
      </a-form-item>

      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'stock:check:query'">
            <template #icon>
              <SearchOutlined />
            </template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'stock:check:query'">
            <template #icon>
              <ReloadOutlined />
            </template>
            重置
          </a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'stock:check:add'">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>

        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'stock:check:batchDelete'">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.STOCK_CHECK" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="checkId"
      :scroll="{ x: 1000, y: yHeight }"
      bordered
      :pagination="false"
      :showSorterTooltip="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #headerCell="{ column }">
        <SmartHeaderCell v-model:value="queryForm[column.filterOptions?.key || column.dataIndex]" :column="column" @change="queryData" />
      </template>
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'checkType'">
          <span>{{ $smartEnumPlugin.getDescByValue('CHECK_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('CHECK_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'stock:check:update'">编辑</a-button>
            <a-button
              @click="completeCheck(record)"
              type="link"
              v-if="record.status !== CHECK_STATUS_ENUM.COMPLETED.value && record.status !== CHECK_STATUS_ENUM.CANCELLED.value"
              v-privilege="'stock:check:complete'"
            >
              盘点完成
            </a-button>
            <a-button @click="deleteCheck(record)" danger type="link" v-privilege="'stock:check:delete'">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        showSizeChanger
        showQuickJumper
        show-less-items
        :pageSizeOptions="PAGE_SIZE_OPTIONS"
        :defaultPageSize="queryForm.pageSize"
        v-model:current="queryForm.pageNum"
        v-model:pageSize="queryForm.pageSize"
        :total="total"
        @change="queryData"
        :show-total="(total) => `共${total}条`"
      />
    </div>
  </a-card>

  <!---------- 新建/编辑 抽屉 begin ----------->
  <StockCheckFormModal ref="formModal" @reloadList="queryData" />
  <!---------- 新建/编辑 抽屉 end ----------->
</template>
<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { stockCheckApi } from '/@/api/business/stock/stock-check-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import SmartHeaderCell from '/@/components/support/table-header-cell/index.vue';
  import _ from 'lodash';
  import { CHECK_STATUS_ENUM } from '/@/constants/business/erp/stock-const';
  import StockCheckFormModal from './components/stock-check-form-modal.vue';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '盘点单号',
      dataIndex: 'checkNo',
      resizable: true,
      filterOptions: {
        type: 'input',
        key: 'checkNo',
      },
      width: 180,
    },
    {
      title: '仓库ID',
      dataIndex: 'warehouseId',
      resizable: true,
      width: 120,
    },
    {
      title: '盘点类型',
      dataIndex: 'checkType',
      resizable: true,
      filterOptions: {
        type: 'enum-select',
        enumName: 'CHECK_TYPE_ENUM',
      },
      width: 120,
    },
    {
      title: '状态',
      dataIndex: 'status',
      resizable: true,
      filterOptions: {
        type: 'enum-select',
        enumName: 'CHECK_STATUS_ENUM',
      },
      width: 120,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      resizable: true,
      width: 170,
    },
    {
      title: '操作',
      dataIndex: 'action',
      resizable: true,
      fixed: 'right',
      width: 160,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    warehouseId: undefined,
    checkType: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 10,
    sortItemList: [],
  };
  // 查询表单form
  const queryForm = reactive(_.cloneDeep(queryFormState));
  // 表格加载loading
  const tableLoading = ref(false);
  // 表格数据
  const tableData = ref([]);
  // 总数
  const total = ref(0);

  function handleResizeColumn(w, col) {
    columns.value.forEach((item) => {
      if (item.dataIndex === col.dataIndex) {
        item.width = Math.floor(w);
        item.dragAndDropFlag = true;
      }
    });
  }

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, _.cloneDeep(queryFormState));
    queryForm.pageSize = pageSize;
    queryData();
  }

  // 搜索
  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await stockCheckApi.query(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 新建/编辑 ----------------------------

  const formModal = ref();

  function addOrUpdate(rowData) {
    formModal.value.showDrawer(rowData);
  }

  // ---------------------------- 完成盘点 ----------------------------

  function completeCheck(record) {
    Modal.confirm({
      title: '提示',
      content: '确定完成盘点单【' + record.checkNo + '】吗? 完成后将按盘点差异生成库存调整。',
      okText: '确定',
      onOk() {
        doComplete(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doComplete(record) {
    try {
      SmartLoading.show();
      await stockCheckApi.complete(record.checkId);
      message.success('盘点已完成');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 删除 ----------------------------

  function deleteCheck(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除盘点单【' + record.checkNo + '】吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        singleDelete(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function singleDelete(record) {
    try {
      SmartLoading.show();
      await stockCheckApi.delete(record.checkId);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量删除 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要删除选中的盘点单吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        batchDelete();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function batchDelete() {
    try {
      SmartLoading.show();
      await stockCheckApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 动态设置表格高度 ----------------------------
  const yHeight = ref(0);
  onMounted(() => {
    resetGetHeight();
  });
  function resetGetHeight() {
    let doc = document.querySelector('.ant-form');
    let btn = document.querySelector('.smart-table-btn-block');
    let tableCell = document.querySelector('.ant-table-cell');
    let page = document.querySelector('.smart-query-table-page');
    let box = document.querySelector('.admin-content');
    setTimeout(() => {
      let dueHeight = doc.offsetHeight + 10 + 24 + btn.offsetHeight + 15 + tableCell.offsetHeight + page.offsetHeight + 20;
      yHeight.value = box.offsetHeight - dueHeight;
    }, 100);
  }
  window.addEventListener(
    'resize',
    _.throttle(() => {
      resetGetHeight();
    }, 1000)
  );
</script>
