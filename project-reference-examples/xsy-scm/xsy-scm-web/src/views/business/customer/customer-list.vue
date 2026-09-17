<!--
  * 客户列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="客户名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.customerName" placeholder="客户名称/编码" />
      </a-form-item>
      <a-form-item label="客户类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CUSTOMER_TYPE_ENUM" v-model:value="queryForm.customerType" width="140px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CUSTOMER_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'customer:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'customer:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'customer:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'customer:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.CUSTOMER.CUSTOMER" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="customerId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'customerType'">
          <span>{{ $smartEnumPlugin.getDescByValue('CUSTOMER_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'settleMode'">
          <span>{{ $smartEnumPlugin.getDescByValue('SETTLE_MODE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('CUSTOMER_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'customer:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'customer:delete'">删除</a-button>
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
  <a-drawer :title="form.customerId ? '编辑' : '添加'" :width="560" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="客户名称" name="customerName">
        <a-input v-model:value="form.customerName" placeholder="请输入客户名称" />
      </a-form-item>
      <a-form-item label="客户类型" name="customerType">
        <SmartEnumSelect enum-name="CUSTOMER_TYPE_ENUM" v-model:value="form.customerType" width="100%" />
      </a-form-item>
      <a-form-item label="结算方式" name="settleMode">
        <SmartEnumSelect enum-name="SETTLE_MODE_ENUM" v-model:value="form.settleMode" width="100%" />
      </a-form-item>
      <a-form-item label="客户分级ID" name="customerLevelId">
        <a-input-number style="width: 100%" v-model:value="form.customerLevelId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="上级客户ID" name="parentCustomerId">
        <a-input-number style="width: 100%" v-model:value="form.parentCustomerId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="业务员ID" name="sellerId">
        <a-input-number style="width: 100%" v-model:value="form.sellerId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="供应商ID" name="supplierId">
        <a-input-number style="width: 100%" v-model:value="form.supplierId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="联系人" name="contactName">
        <a-input v-model:value="form.contactName" placeholder="请输入联系人" />
      </a-form-item>
      <a-form-item label="联系电话" name="contactPhone">
        <a-input v-model:value="form.contactPhone" placeholder="请输入联系电话" />
      </a-form-item>
      <a-form-item label="地址" name="address">
        <a-input v-model:value="form.address" placeholder="请输入地址" />
      </a-form-item>
      <a-form-item label="授信额度" name="creditAmount">
        <a-input-number style="width: 100%" v-model:value="form.creditAmount" :min="0" />
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
  <!---------- 新建/编辑 抽屉 end ----------->
</template>
<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { customerApi } from '/@/api/business/customer/customer-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';
  import { CUSTOMER_TYPE_ENUM, SETTLE_MODE_ENUM } from '/@/constants/business/customer/customer-const';

  const columns = ref([
    { title: '客户编码', dataIndex: 'customerNo', resizable: true, width: 160 },
    { title: '客户名称', dataIndex: 'customerName', resizable: true, width: 160 },
    { title: '客户类型', dataIndex: 'customerType', resizable: true, width: 100 },
    { title: '结算方式', dataIndex: 'settleMode', resizable: true, width: 130 },
    { title: '联系人', dataIndex: 'contactName', resizable: true, width: 110 },
    { title: '联系电话', dataIndex: 'contactPhone', resizable: true, width: 140 },
    { title: '余额', dataIndex: 'balance', resizable: true, width: 110 },
    { title: '授信额度', dataIndex: 'creditAmount', resizable: true, width: 110 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 100 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    customerName: '',
    customerType: undefined,
    settleMode: undefined,
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
      let res = await customerApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 新建/编辑 ----------------------------
  const formRef = ref();
  const visible = ref(false);
  const formDefault = {
    customerId: undefined,
    customerName: undefined,
    customerType: CUSTOMER_TYPE_ENUM.ENTERPRISE.value,
    customerLevelId: undefined,
    parentCustomerId: undefined,
    settleMode: SETTLE_MODE_ENUM.INDEPENDENT.value,
    sellerId: undefined,
    supplierId: undefined,
    contactName: undefined,
    contactPhone: undefined,
    address: undefined,
    longitude: undefined,
    latitude: undefined,
    creditAmount: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    customerName: [{ required: true, message: '客户名称不能为空' }],
    customerType: [{ required: true, message: '请选择客户类型' }],
    contactName: [{ required: true, message: '联系人不能为空' }],
    contactPhone: [{ required: true, message: '联系电话不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.customerId) {
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
          if (form.customerId) {
            await customerApi.update(form);
          } else {
            await customerApi.add(form);
          }
          message.success(`${form.customerId ? '修改' : '添加'}成功`);
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

  // ---------------------------- 删除 ----------------------------
  const selectedRowKeyList = ref([]);
  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function deleteOne(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除客户【' + record.customerName + '】吗?',
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
      await customerApi.delete(record.customerId);
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
      content: '确定要删除选中的客户吗?',
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
      await customerApi.batchDelete(selectedRowKeyList.value);
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
