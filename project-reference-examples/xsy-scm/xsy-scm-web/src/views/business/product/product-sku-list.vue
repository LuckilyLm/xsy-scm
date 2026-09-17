<!--
  * 产品规格列表
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="产品" class="smart-query-form-item">
        <ProductSelect v-model:value="queryForm.productId" width="200px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'product:sku:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'product:sku:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'product:sku:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'product:sku:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.PRODUCT.SKU" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="skuId"
      :scroll="{ x: 1000, y: yHeight }"
      bordered
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('SKU_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'product:sku:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'product:sku:delete'">删除</a-button>
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

  <a-drawer :title="form.skuId ? '编辑' : '添加'" :width="450" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="产品" name="productId">
        <ProductSelect v-model:value="form.productId" />
      </a-form-item>
      <a-form-item label="规格名称" name="specName">
        <a-input v-model:value="form.specName" placeholder="请输入规格名称" />
      </a-form-item>
      <a-form-item label="单位" name="unit">
        <a-input v-model:value="form.unit" placeholder="请输入单位" />
      </a-form-item>
      <a-form-item label="单位重量" name="unitWeight">
        <a-input-number style="width: 100%" v-model:value="form.unitWeight" :min="0" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <SmartEnumSelect enum-name="SKU_STATUS_ENUM" v-model:value="form.status" width="100%" />
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
  import { productSkuApi } from '/@/api/business/product/product-sku-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import ProductSelect from '/@/components/business/product-select/index.vue';
  import _ from 'lodash';
  import { SKU_STATUS_ENUM } from '/@/constants/business/product/product-const';

  const route = useRoute();

  const columns = ref([
    { title: '规格ID', dataIndex: 'skuId', resizable: true, width: 110 },
    { title: '产品ID', dataIndex: 'productId', resizable: true, width: 110 },
    { title: '规格编码', dataIndex: 'skuNo', resizable: true, width: 160 },
    { title: '规格名称', dataIndex: 'specName', resizable: true, width: 160 },
    { title: '单位', dataIndex: 'unit', resizable: true, width: 100 },
    { title: '单位重量', dataIndex: 'unitWeight', resizable: true, width: 110 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 100 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 110 },
  ]);

  const queryFormState = {
    productId: undefined,
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
      let res = await productSkuApi.query(queryForm);
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
    skuId: undefined,
    productId: undefined,
    specName: undefined,
    unit: undefined,
    unitWeight: undefined,
    status: SKU_STATUS_ENUM.ENABLED.value,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    productId: [{ required: true, message: '产品ID不能为空' }],
    specName: [{ required: true, message: '规格名称不能为空' }],
    unit: [{ required: true, message: '单位不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.skuId) {
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
          if (form.skuId) {
            await productSkuApi.update(form);
          } else {
            await productSkuApi.add(form);
          }
          message.success(`${form.skuId ? '修改' : '添加'}成功`);
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
      content: '确定要删除规格【' + record.specName + '】吗?',
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
      await productSkuApi.delete(record.skuId);
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
      content: '确定要删除选中的规格吗?',
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
      await productSkuApi.batchDelete(selectedRowKeyList.value);
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
