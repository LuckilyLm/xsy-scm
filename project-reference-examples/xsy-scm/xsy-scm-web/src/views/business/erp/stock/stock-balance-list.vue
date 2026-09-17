<!--
  * 库存余额列表（只读）
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="商品ID" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.productId" placeholder="商品ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="规格ID" class="smart-query-form-item">
        <a-input-number style="width: 160px" v-model:value="queryForm.skuId" placeholder="规格ID" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'stock:balance:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'stock:balance:query'">
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
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.STOCK_BALANCE" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="balanceId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      @resizeColumn="handleResizeColumn"
    />
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
  import { stockBalanceApi } from '/@/api/business/stock/stock-balance-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import _ from 'lodash';

  const columns = ref([
    { title: '余额ID', dataIndex: 'balanceId', resizable: true, width: 110 },
    { title: '商品ID', dataIndex: 'productId', resizable: true, width: 110 },
    { title: '规格ID', dataIndex: 'skuId', resizable: true, width: 110 },
    { title: '仓库ID', dataIndex: 'warehouseId', resizable: true, width: 110 },
    { title: '批次ID', dataIndex: 'batchId', resizable: true, width: 110 },
    { title: '数量', dataIndex: 'quantity', resizable: true, width: 110 },
    { title: '重量(kg)', dataIndex: 'weight', resizable: true, width: 110 },
    { title: '加权平均成本', dataIndex: 'avgCost', resizable: true, width: 130 },
    { title: '总成本', dataIndex: 'totalCost', resizable: true, width: 110 },
    { title: '预警下限', dataIndex: 'warnMin', resizable: true, width: 110 },
    { title: '预警上限', dataIndex: 'warnMax', resizable: true, width: 110 },
    { title: '更新时间', dataIndex: 'updateTime', resizable: true, width: 170 },
  ]);

  const queryFormState = {
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
      let res = await stockBalanceApi.query(queryForm);
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
