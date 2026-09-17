<!--
  * 供应商对账单
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="对账单号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="对账单号" allow-clear />
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="queryForm.supplierId" width="200px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SUPPLIER_STATEMENT_STATUS_ENUM" v-model:value="queryForm.status" width="150px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'supplierStatement:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'supplierStatement:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'supplierStatement:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'supplierStatement:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.SUPPLIER.STATEMENT" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="statementId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      :loading="tableLoading"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statementStatusColor(text)">{{ $smartEnumPlugin.getDescByValue('SUPPLIER_STATEMENT_STATUS_ENUM', text) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button v-if="record.status === 1" @click="openConfirm(record)" type="link" v-privilege="'supplierStatement:confirm'">确认</a-button>
            <a-button v-if="record.status === 2" @click="settle(record)" type="link" v-privilege="'supplierStatement:settle'">结算</a-button>
            <a-button v-if="record.status === 1" @click="addOrUpdate(record)" type="link" v-privilege="'supplierStatement:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'supplierStatement:delete'">删除</a-button>
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

  <a-drawer :title="form.statementId ? '编辑对账单' : '新建对账单'" :width="450" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="供应商" name="supplierId">
        <SupplierSelect v-model:value="form.supplierId" />
      </a-form-item>
      <a-form-item label="对账周期开始" name="periodStart">
        <a-date-picker style="width: 100%" v-model:value="form.periodStart" valueFormat="YYYY-MM-DD" placeholder="选择开始日期" />
      </a-form-item>
      <a-form-item label="对账周期结束" name="periodEnd">
        <a-date-picker style="width: 100%" v-model:value="form.periodEnd" valueFormat="YYYY-MM-DD" placeholder="选择结束日期" />
      </a-form-item>
      <a-form-item label="对账总金额" name="totalAmount">
        <a-input-number style="width: 100%" v-model:value="form.totalAmount" :min="0" :precision="2" placeholder="不含税" />
      </a-form-item>
      <a-form-item label="已结算金额" name="paidAmount">
        <a-input-number style="width: 100%" v-model:value="form.paidAmount" :min="0" :precision="2" placeholder="不含税" />
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

  <!-- 供应商确认 / 驳回 -->
  <a-modal :open="confirmVisible" :confirm-loading="confirmLoading" title="对账单确认" @ok="onConfirmSubmit" @cancel="onConfirmClose">
    <a-form :label-col="{ span: 6 }">
      <a-form-item label="对账单号">
        <span>{{ confirmForm.statementNo }}</span>
      </a-form-item>
      <a-form-item label="对账金额">
        <span>{{ confirmForm.totalAmount }}</span>
      </a-form-item>
      <a-form-item label="确认结果" required>
        <a-radio-group v-model:value="confirmForm.status">
          <a-radio :value="2">确认</a-radio>
          <a-radio :value="4">驳回</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import _ from 'lodash';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { supplierStatementApi } from '/@/api/business/supplier/supplier-statement-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import SupplierSelect from '/@/components/business/supplier-select/index.vue';
  import { SUPPLIER_STATEMENT_STATUS_ENUM } from '/@/constants/business/supplier/supplier-const';

  // =========== 表格列 ===========
  const columns = ref([
    { title: '对账单ID', dataIndex: 'statementId', resizable: true, width: 100 },
    { title: '对账单号', dataIndex: 'statementNo', resizable: true, width: 180 },
    { title: '供应商ID', dataIndex: 'supplierId', resizable: true, width: 100 },
    { title: '对账周期开始', dataIndex: 'periodStart', resizable: true, width: 140 },
    { title: '对账周期结束', dataIndex: 'periodEnd', resizable: true, width: 140 },
    { title: '对账总金额', dataIndex: 'totalAmount', resizable: true, width: 130 },
    { title: '已结算金额', dataIndex: 'paidAmount', resizable: true, width: 130 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 130 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 190 },
  ]);

  function statementStatusColor(status) {
    if (status === SUPPLIER_STATEMENT_STATUS_ENUM.SETTLED.value) {
      return 'green';
    }
    if (status === SUPPLIER_STATEMENT_STATUS_ENUM.REJECTED.value) {
      return 'red';
    }
    if (status === SUPPLIER_STATEMENT_STATUS_ENUM.CONFIRMED.value) {
      return 'blue';
    }
    return 'orange';
  }

  // =========== 查询 ===========
  const queryFormState = {
    searchWord: '',
    supplierId: undefined,
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
      let res = await supplierStatementApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // =========== 新增 / 编辑 ===========
  const formRef = ref();
  const visible = ref(false);
  const formDefault = {
    statementId: undefined,
    supplierId: undefined,
    periodStart: undefined,
    periodEnd: undefined,
    totalAmount: undefined,
    paidAmount: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    supplierId: [{ required: true, message: '供应商不能为空' }],
    periodStart: [{ required: true, message: '对账周期开始不能为空' }],
    periodEnd: [{ required: true, message: '对账周期结束不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.statementId) {
      Object.assign(form, rowData);
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
          if (form.statementId) {
            await supplierStatementApi.update(form);
          } else {
            await supplierStatementApi.add(form);
          }
          message.success(`${form.statementId ? '修改' : '添加'}成功`);
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

  // =========== 确认 / 驳回 ===========
  const confirmVisible = ref(false);
  const confirmLoading = ref(false);
  const confirmFormDefault = {
    statementId: undefined,
    statementNo: '',
    totalAmount: '',
    status: 2,
  };
  const confirmForm = reactive({ ...confirmFormDefault });

  function openConfirm(record) {
    Object.assign(confirmForm, confirmFormDefault);
    confirmForm.statementId = record.statementId;
    confirmForm.statementNo = record.statementNo;
    confirmForm.totalAmount = record.totalAmount;
    confirmVisible.value = true;
  }

  function onConfirmClose() {
    Object.assign(confirmForm, confirmFormDefault);
    confirmVisible.value = false;
  }

  async function onConfirmSubmit() {
    confirmLoading.value = true;
    try {
      await supplierStatementApi.confirm(confirmForm);
      message.success('操作成功');
      onConfirmClose();
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      confirmLoading.value = false;
    }
  }

  // =========== 结算 ===========
  function settle(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要对对账单【' + record.statementNo + '】执行结算吗?',
      okText: '结算',
      onOk() {
        doSettle(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doSettle(record) {
    try {
      SmartLoading.show();
      await supplierStatementApi.settle(record.statementId);
      message.success('结算成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 删除 ===========
  const selectedRowKeyList = ref([]);
  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function deleteOne(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除对账单【' + record.statementNo + '】吗?',
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
      await supplierStatementApi.delete(record.statementId);
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
      content: '确定要删除选中的对账单吗?',
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
      await supplierStatementApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 表格高度 ===========
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
