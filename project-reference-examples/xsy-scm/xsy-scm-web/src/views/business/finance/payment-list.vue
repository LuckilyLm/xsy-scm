<!--
  * 收款单列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="收款单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.paymentNo" placeholder="模糊搜索" style="width: 160px" allow-clear />
      </a-form-item>
      <a-form-item label="客户ID" class="smart-query-form-item">
        <a-input-number v-model:value="queryForm.customerId" :min="1" :precision="0" style="width: 140px" placeholder="客户ID" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PAYMENT_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'finance:payment:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'finance:payment:query'">
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
        <span class="smart-table-operate-title">收款单</span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.FINANCE.PAYMENT" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="paymentId"
      :scroll="{ x: 1500, y: yHeight }"
      bordered
      :pagination="false"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'payChannel'">
          <span>{{ $smartEnumPlugin.getDescByValue('PAY_CHANNEL_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('PAYMENT_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="confirmPayment(record)" type="link" v-privilege="'finance:payment:confirm'" :disabled="record.status !== PAYMENT_STATUS_ENUM.PENDING.value">确认核销</a-button>
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
</template>
<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { financeApi } from '/@/api/business/finance/finance-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';
  import { PAYMENT_STATUS_ENUM, PAY_CHANNEL_ENUM } from '/@/constants/business/finance/finance-const';

  const columns = ref([
    { title: '收款单ID', dataIndex: 'paymentId', resizable: true, width: 110 },
    { title: '收款单号', dataIndex: 'paymentNo', resizable: true, width: 170 },
    { title: '订单ID', dataIndex: 'orderId', resizable: true, width: 110 },
    { title: '应收单ID', dataIndex: 'receivableId', resizable: true, width: 110 },
    { title: '客户ID', dataIndex: 'customerId', resizable: true, width: 110 },
    { title: '收款金额', dataIndex: 'amount', resizable: true, width: 120 },
    { title: '收款渠道', dataIndex: 'payChannel', resizable: true, width: 120 },
    { title: '收款时间', dataIndex: 'payTime', resizable: true, width: 170 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '备注', dataIndex: 'remark', resizable: true, width: 160 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 120 },
  ]);

  const queryFormState = {
    paymentNo: undefined,
    customerId: undefined,
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
      let res = await financeApi.queryPayment(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  function confirmPayment(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要确认该收款单吗？确认后将核销对应应收。',
      okText: '确认核销',
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
      await financeApi.confirmPayment(record.paymentId);
      message.success('确认成功，已核销应收');
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
