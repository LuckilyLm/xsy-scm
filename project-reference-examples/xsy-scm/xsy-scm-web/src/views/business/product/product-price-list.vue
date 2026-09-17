<!--
  * 产品价格列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="产品" class="smart-query-form-item">
        <ProductSelect v-model:value="queryForm.productId" width="200px" />
      </a-form-item>
      <a-form-item label="价格类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PRICE_TYPE_ENUM" v-model:value="queryForm.priceType" width="140px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PRICE_STATUS_ENUM" v-model:value="queryForm.status" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'product:price:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'product:price:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'product:price:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'product:price:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.PRODUCT.PRICE" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="priceId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'priceType'">
          <span>{{ $smartEnumPlugin.getDescByValue('PRICE_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('PRICE_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'product:price:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'product:price:delete'">删除</a-button>
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

  <a-drawer :title="form.priceId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 7 }">
      <a-form-item label="产品" name="productId">
        <ProductSelect v-model:value="form.productId" />
      </a-form-item>
      <a-form-item label="规格" name="skuId">
        <SkuSelect v-model:value="form.skuId" />
      </a-form-item>
      <a-form-item label="价格类型" name="priceType">
        <SmartEnumSelect enum-name="PRICE_TYPE_ENUM" v-model:value="form.priceType" width="100%" />
      </a-form-item>
      <a-form-item label="客户分级ID" name="customerLevelId">
        <a-input-number style="width: 100%" v-model:value="form.customerLevelId" :min="1" :precision="0" />
      </a-form-item>
      <a-form-item label="客户" name="customerId">
        <CustomerSelect v-model:value="form.customerId" />
      </a-form-item>
      <a-form-item label="价格" name="price">
        <a-input-number style="width: 100%" v-model:value="form.price" :min="0" />
      </a-form-item>
      <a-form-item label="生效时间" name="effectiveTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.effectiveTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="失效时间" name="expireTime">
        <a-date-picker style="width: 100%" show-time v-model:value="form.expireTime" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="PRICE_STATUS_ENUM" v-model:value="form.status" width="100%" />
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
  import { useRoute } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { productPriceApi } from '/@/api/business/product/product-price-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import ProductSelect from '/@/components/business/product-select/index.vue';
  import SkuSelect from '/@/components/business/sku-select/index.vue';
  import CustomerSelect from '/@/components/business/customer-select/index.vue';
  import _ from 'lodash';
  import { PRICE_TYPE_ENUM, PRICE_STATUS_ENUM } from '/@/constants/business/product/product-const';

  const route = useRoute();

  const columns = ref([
    { title: '价格ID', dataIndex: 'priceId', resizable: true, width: 110 },
    { title: '产品ID', dataIndex: 'productId', resizable: true, width: 110 },
    { title: '规格ID', dataIndex: 'skuId', resizable: true, width: 110 },
    { title: '价格类型', dataIndex: 'priceType', resizable: true, width: 130 },
    { title: '客户分级ID', dataIndex: 'customerLevelId', resizable: true, width: 120 },
    { title: '客户ID', dataIndex: 'customerId', resizable: true, width: 110 },
    { title: '价格', dataIndex: 'price', resizable: true, width: 110 },
    { title: '生效时间', dataIndex: 'effectiveTime', resizable: true, width: 170 },
    { title: '失效时间', dataIndex: 'expireTime', resizable: true, width: 170 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 100 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    productId: undefined,
    priceType: undefined,
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
      let res = await productPriceApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(() => {
    if (route.query.productId) {
      queryForm.productId = Number(route.query.productId);
      queryData();
    }
  });

  const formRef = ref();
  const visible = ref(false);
  const formDefault = {
    priceId: undefined,
    productId: undefined,
    skuId: undefined,
    priceType: PRICE_TYPE_ENUM.BASE.value,
    customerLevelId: undefined,
    customerId: undefined,
    price: undefined,
    effectiveTime: undefined,
    expireTime: undefined,
    status: PRICE_STATUS_ENUM.ENABLED.value,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    productId: [{ required: true, message: '产品ID不能为空' }],
    priceType: [{ required: true, message: '请选择价格类型' }],
    price: [{ required: true, message: '价格不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.priceId) {
      Object.assign(form, rowData);
    } else if (queryForm.productId) {
      form.productId = queryForm.productId;
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
          if (form.priceId) {
            await productPriceApi.update(form);
          } else {
            await productPriceApi.add(form);
          }
          message.success(`${form.priceId ? '修改' : '添加'}成功`);
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
      content: '确定要删除该价格记录吗?',
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
      await productPriceApi.delete(record.priceId);
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
      content: '确定要删除选中的价格记录吗?',
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
      await productPriceApi.batchDelete(selectedRowKeyList.value);
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
