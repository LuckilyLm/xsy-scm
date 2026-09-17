<!--
  * 应收单列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="应收单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.receivableNo" placeholder="模糊搜索" style="width: 160px" allow-clear />
      </a-form-item>
      <a-form-item label="客户ID" class="smart-query-form-item">
        <a-input-number v-model:value="queryForm.customerId" :min="1" :precision="0" style="width: 140px" placeholder="客户ID" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="RECEIVABLE_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'finance:receivable:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'finance:receivable:query'">
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
        <span class="smart-table-operate-title">应收单</span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.FINANCE.RECEIVABLE" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="receivableId"
      :scroll="{ x: 1500, y: yHeight }"
      bordered
      :pagination="false"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'settleType'">
          <span>{{ $smartEnumPlugin.getDescByValue('SETTLE_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('RECEIVABLE_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="openRegister(record)" type="link" v-privilege="'finance:payment:add'" :disabled="record.status === RECEIVABLE_STATUS_ENUM.SETTLED.value">登记收款</a-button>
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

  <!-- 登记收款抽屉 -->
  <a-drawer title="登记收款" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="应收单号">
        <span>{{ currentReceivable.receivableNo }}</span>
      </a-form-item>
      <a-form-item label="客户ID" name="customerId">
        <a-input-number style="width: 100%" v-model:value="form.customerId" :min="1" :precision="0" :disabled="true" />
      </a-form-item>
      <a-form-item label="收款金额" name="amount">
        <a-input-number style="width: 100%" v-model:value="form.amount" :min="0.01" :precision="2" />
      </a-form-item>
      <a-form-item label="收款渠道" name="payChannel">
        <SmartEnumSelect enum-name="PAY_CHANNEL_ENUM" v-model:value="form.payChannel" width="100%" />
      </a-form-item>
      <a-form-item label="收款时间" name="payTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.payTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="凭证图片" name="proofImage">
        <FileUpload
          :default-file-list="proofFileList"
          :max-upload-size="1"
          :folder="FILE_FOLDER_TYPE_ENUM.COMMON.value"
          accept=".jpg,.jpeg,.png"
          list-type="picture-card"
          @change="changeProof"
        />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="3" />
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
  import { message } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { financeApi } from '/@/api/business/finance/finance-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import FileUpload from '/@/components/support/file-upload/index.vue';
  import { FILE_FOLDER_TYPE_ENUM } from '/@/constants/support/file-const';
  import _ from 'lodash';
  import { RECEIVABLE_STATUS_ENUM, PAY_CHANNEL_ENUM } from '/@/constants/business/finance/finance-const';
  import { SETTLE_TYPE_ENUM } from '/@/constants/business/order/order-const';

  const columns = ref([
    { title: '应收单ID', dataIndex: 'receivableId', resizable: true, width: 110 },
    { title: '应收单号', dataIndex: 'receivableNo', resizable: true, width: 170 },
    { title: '订单ID', dataIndex: 'orderId', resizable: true, width: 110 },
    { title: '客户ID', dataIndex: 'customerId', resizable: true, width: 110 },
    { title: '结算方式', dataIndex: 'settleType', resizable: true, width: 120 },
    { title: '应收金额', dataIndex: 'amount', resizable: true, width: 120 },
    { title: '已收金额', dataIndex: 'receivedAmount', resizable: true, width: 120 },
    { title: '待收余额', dataIndex: 'balanceAmount', resizable: true, width: 120 },
    { title: '到期日', dataIndex: 'dueTime', resizable: true, width: 170 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 120 },
  ]);

  const queryFormState = {
    receivableNo: undefined,
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
      let res = await financeApi.queryReceivable(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------- 登记收款 ----------------
  const formRef = ref();
  const visible = ref(false);
  const currentReceivable = ref({});
  const formDefault = {
    receivableId: undefined,
    customerId: undefined,
    amount: undefined,
    payChannel: undefined,
    payTime: undefined,
    proofImage: undefined,
    remark: undefined,
  };
  let form = reactive({ ...formDefault });
  const proofFileList = ref([]);

  function changeProof(fileList) {
    proofFileList.value = fileList;
    form.proofImage = fileList && fileList.length ? fileList[0].fileUrl : undefined;
  }
  const rules = {
    customerId: [{ required: true, message: '客户ID不能为空' }],
    amount: [{ required: true, message: '收款金额不能为空' }],
    payChannel: [{ required: true, message: '收款渠道不能为空' }],
  };

  function openRegister(record) {
    currentReceivable.value = record;
    Object.assign(form, formDefault);
    proofFileList.value = [];
    form.receivableId = record.receivableId;
    form.customerId = record.customerId;
    form.amount = record.balanceAmount;
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
          await financeApi.addPayment(form);
          message.success('登记成功，收款单待财务确认');
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
