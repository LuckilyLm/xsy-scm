<!--
  * 订单日志列表（只读 + 新增，不支持修改/删除）
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="订单ID" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.orderId" placeholder="订单ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'order:log:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'order:log:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'order:log:add'">
          <template #icon><PlusOutlined /></template>
          新增日志
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ORDER.LOG" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="logId"
      :scroll="{ x: 1300, y: yHeight }"
      bordered
      :pagination="false"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, column }">
        <template v-if="column.dataIndex === 'operateType'">
          <span>{{ $smartEnumPlugin.getDescByValue('ORDER_OPERATE_TYPE_ENUM', text) }}</span>
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

  <a-drawer title="新增日志" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="订单ID" name="orderId">
        <a-input-number style="width: 100%" v-model:value="form.orderId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="操作类型" name="operateType">
        <SmartEnumSelect enum-name="ORDER_OPERATE_TYPE_ENUM" v-model:value="form.operateType" width="100%" />
      </a-form-item>
      <a-form-item label="变更前" name="beforeValue">
        <a-textarea v-model:value="form.beforeValue" :rows="3" :maxlength="500" />
      </a-form-item>
      <a-form-item label="变更后" name="afterValue">
        <a-textarea v-model:value="form.afterValue" :rows="3" :maxlength="500" />
      </a-form-item>
      <a-form-item label="操作时间" name="operateTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.operateTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
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
  import { orderLogApi } from '/@/api/business/order/order-log-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';
  import { ORDER_OPERATE_TYPE_ENUM } from '/@/constants/business/order/order-const';

  const columns = ref([
    { title: '日志ID', dataIndex: 'logId', resizable: true, width: 110 },
    { title: '订单ID', dataIndex: 'orderId', resizable: true, width: 110 },
    { title: '操作类型', dataIndex: 'operateType', resizable: true, width: 120 },
    { title: '变更前', dataIndex: 'beforeValue', resizable: true, ellipsis: true, width: 200 },
    { title: '变更后', dataIndex: 'afterValue', resizable: true, ellipsis: true, width: 200 },
    { title: '操作人', dataIndex: 'operateBy', resizable: true, width: 110 },
    { title: '操作时间', dataIndex: 'operateTime', resizable: true, width: 170 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
  ]);

  const queryFormState = {
    orderId: undefined,
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
      let res = await orderLogApi.query(queryForm);
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
    operateType: ORDER_OPERATE_TYPE_ENUM.CREATE.value,
    beforeValue: '',
    afterValue: '',
    operateBy: undefined,
    operateTime: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    orderId: [{ required: true, message: '订单ID不能为空' }],
    operateType: [{ required: true, message: '请选择操作类型' }],
  };

  function addOrUpdate() {
    Object.assign(form, formDefault);
    if (queryForm.orderId) {
      form.orderId = queryForm.orderId;
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
          await orderLogApi.add(form);
          message.success('新增成功');
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
