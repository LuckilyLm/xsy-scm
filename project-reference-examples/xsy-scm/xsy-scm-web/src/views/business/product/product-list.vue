<!--
  * 产品列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="产品名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.productName" placeholder="产品名称/编码" />
      </a-form-item>
      <a-form-item label="产品类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PRODUCT_TYPE_ENUM" v-model:value="queryForm.productType" width="130px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="PRODUCT_STATUS_ENUM" v-model:value="queryForm.status" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'product:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'product:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'product:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'product:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.PRODUCT.PRODUCT" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="productId"
      :scroll="{ x: 1500, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'productType'">
          <span>{{ $smartEnumPlugin.getDescByValue('PRODUCT_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'measureType'">
          <span>{{ $smartEnumPlugin.getDescByValue('MEASURE_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'purchaseMode'">
          <span>{{ $smartEnumPlugin.getDescByValue('PURCHASE_MODE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('PRODUCT_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'specFlag'">
          <span>{{ text ? '是' : '否' }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'product:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'product:delete'">删除</a-button>
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

  <a-drawer :title="form.productId ? '编辑' : '添加'" :width="500" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="分类" name="categoryId">
        <ProductCategoryTreeSelect v-model:value="form.categoryId" />
      </a-form-item>
      <a-form-item label="产品名称" name="productName">
        <a-input v-model:value="form.productName" placeholder="请输入产品名称" />
      </a-form-item>
      <a-form-item label="产品类型" name="productType">
        <SmartEnumSelect enum-name="PRODUCT_TYPE_ENUM" v-model:value="form.productType" width="100%" />
      </a-form-item>
      <a-form-item label="计量方式" name="measureType">
        <SmartEnumSelect enum-name="MEASURE_TYPE_ENUM" v-model:value="form.measureType" width="100%" />
      </a-form-item>
      <a-form-item label="基本单位" name="baseUnit">
        <a-select v-model:value="form.baseUnit" placeholder="请选择基本单位" :showSearch="true" :allowClear="true" style="width: 100%">
          <a-select-option v-for="unit in UNIT_OPTIONS" :key="unit" :value="unit">{{ unit }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="多规格" name="specFlag">
        <a-switch v-model:checked="form.specFlag" />
      </a-form-item>
      <a-form-item label="采购模式" name="purchaseMode">
        <SmartEnumSelect enum-name="PURCHASE_MODE_ENUM" v-model:value="form.purchaseMode" width="100%" />
      </a-form-item>
      <a-form-item label="默认供应商" name="defaultSupplierId">
        <SupplierSelect v-model:value="form.defaultSupplierId" />
      </a-form-item>
      <a-form-item label="默认采购员" name="defaultBuyerId">
        <EmployeeSelect v-model:value="form.defaultBuyerId" placeholder="请选择默认采购员" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="PRODUCT_STATUS_ENUM" v-model:value="form.status" width="100%" />
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
  import { productApi } from '/@/api/business/product/product-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import EmployeeSelect from '/@/components/system/employee-select/index.vue';
  import SupplierSelect from '/@/components/business/supplier-select/index.vue';
  import ProductCategoryTreeSelect from '/@/components/business/product-category-tree-select/index.vue';
  import _ from 'lodash';
  import { PRODUCT_TYPE_ENUM, MEASURE_TYPE_ENUM, PURCHASE_MODE_ENUM, PRODUCT_STATUS_ENUM } from '/@/constants/business/product/product-const';

  // 常用产品基本单位
  const UNIT_OPTIONS = ['件', 'kg', '斤', 'g', '箱', '盒', '袋', '瓶', '个', '只', '支', '颗', '捆', '扎', '包', '套', '升', '毫升', '吨'];

  const columns = ref([
    { title: '产品ID', dataIndex: 'productId', resizable: true, width: 110 },
    { title: '产品编码', dataIndex: 'productNo', resizable: true, width: 160 },
    { title: '产品名称', dataIndex: 'productName', resizable: true, width: 160 },
    { title: '分类', dataIndex: 'categoryName', resizable: true, width: 120 },
    { title: '产品类型', dataIndex: 'productType', resizable: true, width: 110 },
    { title: '计量方式', dataIndex: 'measureType', resizable: true, width: 110 },
    { title: '基本单位', dataIndex: 'baseUnit', resizable: true, width: 100 },
    { title: '多规格', dataIndex: 'specFlag', resizable: true, width: 90 },
    { title: '采购模式', dataIndex: 'purchaseMode', resizable: true, width: 120 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    productName: '',
    productType: undefined,
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
      let res = await productApi.query(queryForm);
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
    productId: undefined,
    categoryId: undefined,
    productName: undefined,
    productType: PRODUCT_TYPE_ENUM.STANDARD.value,
    measureType: MEASURE_TYPE_ENUM.BY_PIECE.value,
    baseUnit: undefined,
    specFlag: false,
    purchaseMode: PURCHASE_MODE_ENUM.SELF.value,
    defaultSupplierId: undefined,
    defaultBuyerId: undefined,
    detail: undefined,
    mainImage: undefined,
    status: PRODUCT_STATUS_ENUM.DRAFT.value,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    categoryId: [{ required: true, message: '分类ID不能为空' }],
    productName: [{ required: true, message: '产品名称不能为空' }],
    baseUnit: [{ required: true, message: '基本单位不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.productId) {
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
          if (form.productId) {
            await productApi.update(form);
          } else {
            await productApi.add(form);
          }
          message.success(`${form.productId ? '修改' : '添加'}成功`);
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
      content: '确定要删除产品【' + record.productName + '】吗?',
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
      await productApi.delete(record.productId);
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
      content: '确定要删除选中的产品吗?',
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
      await productApi.batchDelete(selectedRowKeyList.value);
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
