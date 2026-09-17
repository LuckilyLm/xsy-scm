<!--
  * 销售订单列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="订单来源" class="smart-query-form-item">
        <SmartEnumSelect enum-name="ORDER_SOURCE_ENUM" v-model:value="queryForm.source" width="140px" />
      </a-form-item>
      <a-form-item label="结算方式" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SETTLE_TYPE_ENUM" v-model:value="queryForm.settleType" width="140px" />
      </a-form-item>
      <a-form-item label="订单状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="ORDER_STATUS_ENUM" v-model:value="queryForm.status" width="150px" />
      </a-form-item>
      <a-form-item label="支付状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PAY_STATUS_ENUM" v-model:value="queryForm.payStatus" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'order:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'order:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'order:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'order:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ORDER.ORDER" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="orderId"
      :scroll="{ x: 1700, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'source'">
          <span>{{ $smartEnumPlugin.getDescByValue('ORDER_SOURCE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'settleType'">
          <span>{{ $smartEnumPlugin.getDescByValue('SETTLE_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'payStatus'">
          <span>{{ $smartEnumPlugin.getDescByValue('PAY_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('ORDER_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'order:update'">编辑</a-button>
            <a-button @click="confirmOrder(record)" type="link" v-privilege="'order:confirm'" :disabled="!canConfirm(record.status)">确认</a-button>
            <a-button @click="deliver(record)" type="link" v-privilege="'order:deliver'" :disabled="record.status !== ORDER_STATUS_ENUM.CONFIRMED.value">发货</a-button>
            <a-button @click="signOrder(record)" type="link" v-privilege="'order:sign'" :disabled="record.status !== ORDER_STATUS_ENUM.DELIVERING.value">签收</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'order:delete'">删除</a-button>
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

  <a-drawer :title="form.orderId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="客户ID" name="customerId">
        <a-input-number style="width: 100%" v-model:value="form.customerId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="结算客户ID" name="settleCustomerId">
        <a-input-number style="width: 100%" v-model:value="form.settleCustomerId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="订单来源" name="source">
        <SmartEnumSelect enum-name="ORDER_SOURCE_ENUM" v-model:value="form.source" width="100%" />
      </a-form-item>
      <a-form-item label="结算方式" name="settleType">
        <SmartEnumSelect enum-name="SETTLE_TYPE_ENUM" v-model:value="form.settleType" width="100%" />
      </a-form-item>
      <a-form-item label="期望配送时间" name="expectDeliveryTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.expectDeliveryTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="业务员ID" name="sellerId">
        <a-input-number style="width: 100%" v-model:value="form.sellerId" :min="1" :precision="0" />
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
  import { orderApi } from '/@/api/business/order/order-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';
  import { ORDER_SOURCE_ENUM, SETTLE_TYPE_ENUM } from '/@/constants/business/order/order-const';

  const columns = ref([
    { title: '订单ID', dataIndex: 'orderId', resizable: true, width: 110 },
    { title: '订单号', dataIndex: 'orderNo', resizable: true, width: 170 },
    { title: '客户ID', dataIndex: 'customerId', resizable: true, width: 110 },
    { title: '订单来源', dataIndex: 'source', resizable: true, width: 110 },
    { title: '结算方式', dataIndex: 'settleType', resizable: true, width: 120 },
    { title: '总金额', dataIndex: 'totalAmount', resizable: true, width: 110 },
    { title: '优惠金额', dataIndex: 'discountAmount', resizable: true, width: 110 },
    { title: '应付金额', dataIndex: 'payableAmount', resizable: true, width: 110 },
    { title: '实付金额', dataIndex: 'actualAmount', resizable: true, width: 110 },
    { title: '支付状态', dataIndex: 'payStatus', resizable: true, width: 110 },
    { title: '订单状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '期望配送时间', dataIndex: 'expectDeliveryTime', resizable: true, width: 170 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 320 },
  ]);

  const queryFormState = {
    source: undefined,
    settleType: undefined,
    status: undefined,
    payStatus: undefined,
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
      let res = await orderApi.query(queryForm);
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
    orderId: undefined,
    customerId: undefined,
    settleCustomerId: undefined,
    source: ORDER_SOURCE_ENUM.ADMIN.value,
    settleType: SETTLE_TYPE_ENUM.PERIOD.value,
    expectDeliveryTime: undefined,
    sellerId: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    customerId: [{ required: true, message: '客户ID不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.orderId) {
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
          if (form.orderId) {
            await orderApi.update(form);
          } else {
            await orderApi.add(form);
          }
          message.success(`${form.orderId ? '修改' : '添加'}成功`);
          onClose();
          queryData();
        } catch (e) {
          smartSentry.captureError(e);
          message.error(e?.message || '操作失败，请稍后重试');
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
      content: '确定要删除订单【' + record.orderNo + '】吗?',
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
      await orderApi.delete(record.orderId);
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
      content: '确定要删除选中的订单吗?',
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
      await orderApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function deliver(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要发货订单【' + record.orderNo + '】吗？发货后将触发销售出库并扣减库存。',
      okText: '发货',
      onOk() {
        doDeliver(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doDeliver(record) {
    try {
      SmartLoading.show();
      await orderApi.deliver(record.orderId);
      message.success('发货成功，已触发销售出库');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // 仅草稿 / 待确认 可确认，与后端 confirm 的状态校验保持一致
  function canConfirm(status) {
    return status === ORDER_STATUS_ENUM.DRAFT.value || status === ORDER_STATUS_ENUM.WAIT_CONFIRM.value;
  }

  function confirmOrder(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要确认订单【' + record.orderNo + '】吗？确认后方可发货。',
      okText: '确认',
      onOk() {
        doConfirm(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doConfirm(record) {
    try {
      SmartLoading.show();
      await orderApi.confirm(record.orderId);
      message.success('确认成功，订单已可发货');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function signOrder(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要签收订单【' + record.orderNo + '】吗？签收后将生成应收。',
      okText: '签收',
      onOk() {
        doSign(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doSign(record) {
    try {
      SmartLoading.show();
      await orderApi.sign(record.orderId);
      message.success('签收成功，已生成应收');
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
    if (!doc || !btn || !page || !box) {
      return;
    }
    setTimeout(() => {
      let tableCellH = tableCell ? tableCell.offsetHeight : 0;
      let dueHeight = doc.offsetHeight + 10 + 24 + btn.offsetHeight + 15 + tableCellH + page.offsetHeight + 20;
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
