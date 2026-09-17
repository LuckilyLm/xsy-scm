<!--
  * 供应商厂商信息
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="厂商名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="厂商名称" allow-clear />
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="queryForm.supplierId" width="200px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SUPPLIER_MANUFACTURER_STATUS_ENUM" v-model:value="queryForm.status" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'supplierManufacturer:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'supplierManufacturer:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'supplierManufacturer:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'supplierManufacturer:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.SUPPLIER.MANUFACTURER" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="manufacturerId"
      :scroll="{ x: 1300, y: yHeight }"
      bordered
      :pagination="false"
      :loading="tableLoading"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'qualificationExpireDate'">
          <span>{{ text }}</span>
          <a-tag v-if="isExpireWarning(text)" color="orange" style="margin-left: 6px">即将到期</a-tag>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('SUPPLIER_MANUFACTURER_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'supplierManufacturer:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'supplierManufacturer:delete'">删除</a-button>
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

  <a-drawer :title="form.manufacturerId ? '编辑厂商信息' : '添加厂商信息'" :width="450" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="供应商" name="supplierId">
        <SupplierSelect v-model:value="form.supplierId" />
      </a-form-item>
      <a-form-item label="厂商名称" name="manufacturerName">
        <a-input v-model:value="form.manufacturerName" placeholder="请输入厂商名称" />
      </a-form-item>
      <a-form-item label="资质证件" name="qualificationFile">
        <a-input v-model:value="form.qualificationFile" placeholder="资质证件地址（文件服务）" />
      </a-form-item>
      <a-form-item label="资质到期日期" name="qualificationExpireDate">
        <a-date-picker style="width: 100%" v-model:value="form.qualificationExpireDate" valueFormat="YYYY-MM-DD" placeholder="选择到期日期" />
      </a-form-item>
      <a-form-item label="质检报告" name="inspectReportFile">
        <a-input v-model:value="form.inspectReportFile" placeholder="质检报告地址（文件服务）" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="SUPPLIER_MANUFACTURER_STATUS_ENUM" v-model:value="form.status" width="100%" />
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
  import _ from 'lodash';
  import dayjs from 'dayjs';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { supplierManufacturerApi } from '/@/api/business/supplier/supplier-manufacturer-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import SupplierSelect from '/@/components/business/supplier-select/index.vue';
  import { SUPPLIER_MANUFACTURER_STATUS_ENUM } from '/@/constants/business/supplier/supplier-const';

  // 资质到期预警天数（与后端预警口径保持一致时同步调整）
  const EXPIRE_WARNING_DAYS = 30;

  // =========== 表格列 ===========
  const columns = ref([
    { title: '厂商ID', dataIndex: 'manufacturerId', resizable: true, width: 100 },
    { title: '供应商ID', dataIndex: 'supplierId', resizable: true, width: 100 },
    { title: '厂商名称', dataIndex: 'manufacturerName', resizable: true, width: 180 },
    { title: '资质证件', dataIndex: 'qualificationFile', resizable: true, ellipsis: true, width: 200 },
    { title: '资质到期日期', dataIndex: 'qualificationExpireDate', resizable: true, width: 170 },
    { title: '质检报告', dataIndex: 'inspectReportFile', resizable: true, ellipsis: true, width: 200 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 100 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  /**
   * 资质是否即将到期（到期日已在过去或 30 天内到期）
   */
  function isExpireWarning(expireDate) {
    if (!expireDate) {
      return false;
    }
    return dayjs(expireDate).diff(dayjs(), 'day') <= EXPIRE_WARNING_DAYS;
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
      let res = await supplierManufacturerApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // =========== 表单 ===========
  const formRef = ref();
  const visible = ref(false);
  const formDefault = {
    manufacturerId: undefined,
    supplierId: undefined,
    manufacturerName: undefined,
    qualificationFile: undefined,
    qualificationExpireDate: undefined,
    inspectReportFile: undefined,
    status: SUPPLIER_MANUFACTURER_STATUS_ENUM.ENABLED.value,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    supplierId: [{ required: true, message: '供应商不能为空' }],
    manufacturerName: [{ required: true, message: '厂商名称不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.manufacturerId) {
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
          if (form.manufacturerId) {
            await supplierManufacturerApi.update(form);
          } else {
            await supplierManufacturerApi.add(form);
          }
          message.success(`${form.manufacturerId ? '修改' : '添加'}成功`);
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

  // =========== 删除 ===========
  const selectedRowKeyList = ref([]);
  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function deleteOne(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除厂商【' + record.manufacturerName + '】吗?',
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
      await supplierManufacturerApi.delete(record.manufacturerId);
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
      content: '确定要删除选中的厂商信息吗?',
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
      await supplierManufacturerApi.batchDelete(selectedRowKeyList.value);
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
