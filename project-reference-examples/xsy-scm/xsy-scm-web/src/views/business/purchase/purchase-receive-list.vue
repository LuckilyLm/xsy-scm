<!--
  * 采购收货列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="采购单ID" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.purchaseId" placeholder="采购单ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="RECEIVE_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'purchase:receive:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'purchase:receive:query'">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'purchase:receive:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'purchase:receive:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.PURCHASE.RECEIVE" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="receiveId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('RECEIVE_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'receiveFlag'">
          <a-tag :color="text === RECEIVE_FLAG_ENUM.OVER.value ? 'red' : text === RECEIVE_FLAG_ENUM.UNDER.value ? 'orange' : 'green'">
            {{ $smartEnumPlugin.getDescByValue('RECEIVE_FLAG_ENUM', text) }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button v-if="record.status === RECEIVE_STATUS_ENUM.RECEIVED.value" @click="confirmInbound(record)" type="link" v-privilege="'purchase:receive:confirmInbound'">入库确认</a-button>
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'purchase:receive:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'purchase:receive:delete'">删除</a-button>
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

  <a-drawer :title="form.receiveId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 8 }">
      <a-form-item label="采购单ID" name="purchaseId">
        <a-input-number style="width: 100%" v-model:value="form.purchaseId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="明细ID" name="itemId">
        <a-input-number style="width: 100%" v-model:value="form.itemId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="收货数量" name="receiveQuantity">
        <a-input-number style="width: 100%" v-model:value="form.receiveQuantity" :min="0" />
      </a-form-item>
      <a-form-item label="收货重量" name="receiveWeight">
        <a-input-number style="width: 100%" v-model:value="form.receiveWeight" :min="0" />
      </a-form-item>
      <a-form-item label="单价" name="unitPrice">
        <a-input-number style="width: 100%" v-model:value="form.unitPrice" :min="0" />
      </a-form-item>
      <a-form-item label="收货人" name="receiveBy">
        <a-input-number style="width: 100%" v-model:value="form.receiveBy" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="收货时间" name="receiveTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.receiveTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="直接入库" name="directStock">
        <a-switch v-model:checked="form.directStock" checked-children="是" un-checked-children="否" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="RECEIVE_STATUS_ENUM" v-model:value="form.status" width="100%" />
      </a-form-item>
    </a-form>
    <div
      :style="{
        position: 'absolute',
        right: 0,
        bottom: 0,
        width: '100%',
        borderTop: '1px solid #e9e9e9',
        padding: '10px 16px',
        background: '#fff',
        textAlign: 'right',
        zIndex: 1,
      }"
    >
      <a-button style="margin-right: 8px" @click="onClose">取消</a-button>
      <a-button type="primary" @click="onSubmit">提交</a-button>
    </div>
  </a-drawer>
</template>
<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { purchaseReceiveApi } from '/@/api/business/purchase/purchase-receive-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';
  import { RECEIVE_STATUS_ENUM, RECEIVE_FLAG_ENUM } from '/@/constants/business/purchase/purchase-const';

  const columns = ref([
    { title: '收货ID', dataIndex: 'receiveId', resizable: true, width: 110 },
    { title: '收货单号', dataIndex: 'receiveNo', resizable: true, width: 170 },
    { title: '采购单ID', dataIndex: 'purchaseId', resizable: true, width: 120 },
    { title: '明细ID', dataIndex: 'itemId', resizable: true, width: 110 },
    { title: '收货数量', dataIndex: 'receiveQuantity', resizable: true, width: 110 },
    { title: '收货重量', dataIndex: 'receiveWeight', resizable: true, width: 110 },
    { title: '单价', dataIndex: 'unitPrice', resizable: true, width: 110 },
    { title: '收货人', dataIndex: 'receiveBy', resizable: true, width: 110 },
    { title: '收货时间', dataIndex: 'receiveTime', resizable: true, width: 170 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '收货标记', dataIndex: 'receiveFlag', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    purchaseId: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 10,
    sortItemList: [],
  };
  const queryForm = reactive(_.cloneDeep(queryFormState));
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function handleResizeColumn(w, col) {
    columns.value.forEach((item) => {
      if (item.dataIndex === col.dataIndex) {
        item.width = Math.floor(w);
        item.dragAndDropFlag = true;
      }
    });
  }

  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, _.cloneDeep(queryFormState));
    queryForm.pageSize = pageSize;
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      let res = await purchaseReceiveApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  const formRef = ref();
  const visible = ref(false);
  const formDefault = {
    receiveId: undefined,
    purchaseId: undefined,
    itemId: undefined,
    receiveQuantity: undefined,
    receiveWeight: undefined,
    unitPrice: undefined,
    receiveBy: undefined,
    receiveTime: undefined,
    directStock: false,
    status: RECEIVE_STATUS_ENUM.RECEIVED.value,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    purchaseId: [{ required: true, message: '采购单ID不能为空' }],
    itemId: [{ required: true, message: '明细ID不能为空' }],
    receiveQuantity: [{ required: true, message: '收货数量不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.receiveId) {
      Object.assign(form, rowData);
    } else if (queryForm.purchaseId) {
      form.purchaseId = queryForm.purchaseId;
    }
    visible.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function onClose() {
    Object.assign(form, formDefault);
    visible.value = false;
  }

  function onSubmit() {
    formRef.value
      .validate()
      .then(async () => {
        SmartLoading.show();
        try {
          if (form.receiveId) {
            await purchaseReceiveApi.update(form);
          } else {
            await purchaseReceiveApi.add(form);
          }
          message.success(`${form.receiveId ? '修改' : '添加'}成功`);
          onClose();
          queryData();
        } catch (e) {
          smartSentry.captureError(e);
        } finally {
          SmartLoading.hide();
        }
      })
      .catch(() => {
        message.error('参数验证错误，请仔细填写表单数据!');
      });
  }

  const selectedRowKeyList = ref([]);
  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function deleteOne(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除收货单【' + record.receiveNo + '】吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        doDeleteOne(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doDeleteOne(record) {
    try {
      SmartLoading.show();
      await purchaseReceiveApi.delete(record.receiveId);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  async function confirmInbound(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要将收货单【' + record.receiveNo + '】入库确认吗?',
      okText: '入库确认',
      onOk() {
        doConfirmInbound(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doConfirmInbound(record) {
    try {
      SmartLoading.show();
      await purchaseReceiveApi.confirmInbound(record.receiveId);
      message.success('入库确认成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要删除选中的收货单吗?',
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
      await purchaseReceiveApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

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
