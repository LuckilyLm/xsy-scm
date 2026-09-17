<!--
  * 盘点明细列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="盘点单ID" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.checkId" placeholder="盘点单ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="商品ID" class="smart-query-form-item">
        <a-input-number style="width: 150px" v-model:value="queryForm.productId" placeholder="商品ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'stock:check:item:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'stock:check:item:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'stock:check:item:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'stock:check:item:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.STOCK_CHECK_ITEM" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="itemId"
      :scroll="{ x: 1300, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'stock:check:item:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'stock:check:item:delete'">删除</a-button>
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

  <a-drawer :title="form.itemId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 7 }">
      <a-form-item label="盘点单ID" name="checkId">
        <a-input-number style="width: 100%" v-model:value="form.checkId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="商品ID" name="productId">
        <a-input-number style="width: 100%" v-model:value="form.productId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="规格ID" name="skuId">
        <a-input-number style="width: 100%" v-model:value="form.skuId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="账面数量" name="bookQuantity">
        <a-input-number style="width: 100%" v-model:value="form.bookQuantity" :min="0" />
      </a-form-item>
      <a-form-item label="账面重量" name="bookWeight">
        <a-input-number style="width: 100%" v-model:value="form.bookWeight" :min="0" />
      </a-form-item>
      <a-form-item label="实盘数量" name="actualQuantity">
        <a-input-number style="width: 100%" v-model:value="form.actualQuantity" :min="0" />
      </a-form-item>
      <a-form-item label="实盘重量" name="actualWeight">
        <a-input-number style="width: 100%" v-model:value="form.actualWeight" :min="0" />
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
  import { stockCheckItemApi } from '/@/api/business/stock/stock-check-item-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import _ from 'lodash';

  const columns = ref([
    { title: '明细ID', dataIndex: 'itemId', resizable: true, width: 110 },
    { title: '盘点单ID', dataIndex: 'checkId', resizable: true, width: 110 },
    { title: '商品ID', dataIndex: 'productId', resizable: true, width: 110 },
    { title: '规格ID', dataIndex: 'skuId', resizable: true, width: 110 },
    { title: '账面数量', dataIndex: 'bookQuantity', resizable: true, width: 110 },
    { title: '账面重量', dataIndex: 'bookWeight', resizable: true, width: 110 },
    { title: '实盘数量', dataIndex: 'actualQuantity', resizable: true, width: 110 },
    { title: '实盘重量', dataIndex: 'actualWeight', resizable: true, width: 110 },
    { title: '差异数量', dataIndex: 'diffQuantity', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    checkId: undefined,
    productId: undefined,
    skuId: undefined,
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
      let res = await stockCheckItemApi.query(queryForm);
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
    itemId: undefined,
    checkId: undefined,
    productId: undefined,
    skuId: undefined,
    bookQuantity: undefined,
    bookWeight: undefined,
    actualQuantity: undefined,
    actualWeight: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    checkId: [{ required: true, message: '盘点单ID不能为空' }],
    productId: [{ required: true, message: '商品ID不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.itemId) {
      Object.assign(form, rowData);
    } else if (queryForm.checkId) {
      form.checkId = queryForm.checkId;
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
          if (form.itemId) {
            await stockCheckItemApi.update(form);
          } else {
            await stockCheckItemApi.add(form);
          }
          message.success(`${form.itemId ? '修改' : '添加'}成功`);
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
      content: '确定要删除该盘点明细吗?',
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
      await stockCheckItemApi.delete(record.itemId);
      message.success('删除成功');
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
      content: '确定要删除选中的盘点明细吗?',
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
      await stockCheckItemApi.batchDelete(selectedRowKeyList.value);
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
