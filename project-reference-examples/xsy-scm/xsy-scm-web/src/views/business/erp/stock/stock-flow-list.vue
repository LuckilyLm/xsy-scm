<!--
  * 库存流水列表（只读）
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="商品ID" class="smart-query-form-item">
        <a-input-number style="width: 150px" v-model:value="queryForm.productId" placeholder="商品ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="规格ID" class="smart-query-form-item">
        <a-input-number style="width: 150px" v-model:value="queryForm.skuId" placeholder="规格ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="流水类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="STOCK_FLOW_TYPE_ENUM" v-model:value="queryForm.flowType" width="150px" />
      </a-form-item>
      <a-form-item label="关联业务" class="smart-query-form-item">
        <SmartEnumSelect enum-name="STOCK_BIZ_TYPE_ENUM" v-model:value="queryForm.bizType" width="140px" />
      </a-form-item>
      <a-form-item label="方向" class="smart-query-form-item">
        <SmartEnumSelect enum-name="FLOW_DIRECTION_ENUM" v-model:value="queryForm.direction" width="110px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'stock:flow:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'stock:flow:query'">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.STOCK_FLOW" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="flowId"
      :scroll="{ x: 1900, y: yHeight }"
      bordered
      :pagination="false"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, column }">
        <template v-if="column.dataIndex === 'flowType'">
          <span>{{ $smartEnumPlugin.getDescByValue('STOCK_FLOW_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'bizType'">
          <span>{{ $smartEnumPlugin.getDescByValue('STOCK_BIZ_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'direction'">
          <span>{{ $smartEnumPlugin.getDescByValue('FLOW_DIRECTION_ENUM', text) }}</span>
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
  import { stockFlowApi } from '/@/api/business/stock/stock-flow-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import _ from 'lodash';

  const columns = ref([
    { title: '流水ID', dataIndex: 'flowId', resizable: true, width: 110 },
    { title: '流水号', dataIndex: 'flowNo', resizable: true, width: 170 },
    { title: '商品ID', dataIndex: 'productId', resizable: true, width: 100 },
    { title: '规格ID', dataIndex: 'skuId', resizable: true, width: 100 },
    { title: '仓库ID', dataIndex: 'warehouseId', resizable: true, width: 100 },
    { title: '流水类型', dataIndex: 'flowType', resizable: true, width: 120 },
    { title: '关联业务', dataIndex: 'bizType', resizable: true, width: 110 },
    { title: '方向', dataIndex: 'direction', resizable: true, width: 80 },
    { title: '变动数量', dataIndex: 'quantity', resizable: true, width: 110 },
    { title: '变动重量(kg)', dataIndex: 'weight', resizable: true, width: 120 },
    { title: '单价', dataIndex: 'unitPrice', resizable: true, width: 100 },
    { title: '金额', dataIndex: 'amount', resizable: true, width: 100 },
    { title: '变动前数量', dataIndex: 'beforeQuantity', resizable: true, width: 120 },
    { title: '变动后数量', dataIndex: 'afterQuantity', resizable: true, width: 120 },
    { title: '操作时间', dataIndex: 'operateTime', resizable: true, width: 170 },
  ]);

  const queryFormState = {
    productId: undefined,
    skuId: undefined,
    flowType: undefined,
    bizType: undefined,
    direction: undefined,
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
      let res = await stockFlowApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

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
