<!--
  * 商品转换单（整件拆零 / 组合拆分）
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="转换单号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="转换单号" allow-clear />
      </a-form-item>
      <a-form-item label="转换类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CONVERT_TYPE_ENUM" v-model:value="queryForm.convertType" width="150px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="CONVERT_STATUS_ENUM" v-model:value="queryForm.status" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'productConvert:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'productConvert:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'productConvert:add'">
          <template #icon><PlusOutlined /></template>
          新建转换单
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'productConvert:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.PRODUCT_CONVERT" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="convertId"
      :scroll="{ x: 1300, y: yHeight }"
      bordered
      :pagination="false"
      :loading="tableLoading"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'convertType'">
          <span>{{ $smartEnumPlugin.getDescByValue('CONVERT_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'sourceType'">
          <span>{{ $smartEnumPlugin.getDescByValue('CONVERT_SOURCE_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="convertStatusColor(text)">{{ $smartEnumPlugin.getDescByValue('CONVERT_STATUS_ENUM', text) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="openDetail(record)" type="link" v-privilege="'productConvert:query'">详情</a-button>
            <a-button v-if="record.status === 1" @click="addOrUpdate(record)" type="link" v-privilege="'productConvert:update'">编辑</a-button>
            <a-button v-if="record.status === 1" @click="approve(record)" type="link" v-privilege="'productConvert:approve'">审核</a-button>
            <a-button v-if="record.status === 1" @click="reject(record)" type="link" v-privilege="'productConvert:approve'">驳回</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'productConvert:delete'">删除</a-button>
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

  <!-- 新建 / 编辑转换单 -->
  <a-drawer :title="form.convertId ? '编辑转换单' : '新建转换单'" :width="1100" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 3 }">
      <a-form-item label="转换类型" name="convertType">
        <SmartEnumSelect enum-name="CONVERT_TYPE_ENUM" v-model:value="form.convertType" width="220px" />
      </a-form-item>
      <a-form-item label="仓库ID" name="warehouseId">
        <a-input-number style="width: 220px" v-model:value="form.warehouseId" :min="1" :precision="0" placeholder="单仓库默认 1" />
      </a-form-item>
      <a-form-item label="来源" name="sourceType">
        <SmartEnumSelect enum-name="CONVERT_SOURCE_TYPE_ENUM" v-model:value="form.sourceType" width="220px" />
      </a-form-item>
      <a-form-item label="转换明细">
        <a-button type="dashed" block @click="addItem">
          <template #icon><PlusOutlined /></template>
          添加明细
        </a-button>
        <a-table
          style="margin-top: 8px"
          size="small"
          :dataSource="form.items"
          :columns="itemColumns"
          rowKey="_rowKey"
          bordered
          :pagination="false"
          :scroll="{ x: 1600 }"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'sourceProductId'">
              <ProductSelect v-model:value="record.sourceProductId" />
            </template>
            <template v-if="column.dataIndex === 'sourceSkuId'">
              <SkuSelect v-model:value="record.sourceSkuId" />
            </template>
            <template v-if="column.dataIndex === 'sourceQuantity'">
              <a-input-number style="width: 100%" v-model:value="record.sourceQuantity" :min="0" :precision="3" />
            </template>
            <template v-if="column.dataIndex === 'sourceWeight'">
              <a-input-number style="width: 100%" v-model:value="record.sourceWeight" :min="0" :precision="3" />
            </template>
            <template v-if="column.dataIndex === 'targetProductId'">
              <ProductSelect v-model:value="record.targetProductId" />
            </template>
            <template v-if="column.dataIndex === 'targetSkuId'">
              <SkuSelect v-model:value="record.targetSkuId" />
            </template>
            <template v-if="column.dataIndex === 'targetQuantity'">
              <a-input-number style="width: 100%" v-model:value="record.targetQuantity" :min="0" :precision="3" />
            </template>
            <template v-if="column.dataIndex === 'targetWeight'">
              <a-input-number style="width: 100%" v-model:value="record.targetWeight" :min="0" :precision="3" />
            </template>
            <template v-if="column.dataIndex === 'targetUnitPrice'">
              <a-input-number style="width: 100%" v-model:value="record.targetUnitPrice" :min="0" :precision="4" />
            </template>
            <template v-if="column.dataIndex === 'action'">
              <a-button danger type="link" @click="removeItem(record)">删除</a-button>
            </template>
          </template>
        </a-table>
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

  <!-- 转换单详情 -->
  <a-modal :title="'转换单详情 - ' + detail.convertNo" :width="1000" :open="detailVisible" :footer="null" @cancel="detailVisible = false">
    <a-descriptions size="small" bordered :column="3" style="margin-bottom: 12px">
      <a-descriptions-item label="转换类型">{{ $smartEnumPlugin.getDescByValue('CONVERT_TYPE_ENUM', detail.convertType) }}</a-descriptions-item>
      <a-descriptions-item label="仓库ID">{{ detail.warehouseId }}</a-descriptions-item>
      <a-descriptions-item label="状态">{{ $smartEnumPlugin.getDescByValue('CONVERT_STATUS_ENUM', detail.status) }}</a-descriptions-item>
    </a-descriptions>
    <a-table size="small" :dataSource="detail.items" :columns="detailItemColumns" rowKey="itemId" bordered :pagination="false" :scroll="{ x: 1400 }" />
  </a-modal>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import _ from 'lodash';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { productConvertApi } from '/@/api/business/stock/product-convert-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import ProductSelect from '/@/components/business/product-select/index.vue';
  import SkuSelect from '/@/components/business/sku-select/index.vue';
  import { CONVERT_STATUS_ENUM, CONVERT_TYPE_ENUM } from '/@/constants/business/erp/stock-const';

  // =========== 表格列 ===========
  const columns = ref([
    { title: '转换单ID', dataIndex: 'convertId', resizable: true, width: 100 },
    { title: '转换单号', dataIndex: 'convertNo', resizable: true, width: 180 },
    { title: '转换类型', dataIndex: 'convertType', resizable: true, width: 120 },
    { title: '仓库ID', dataIndex: 'warehouseId', resizable: true, width: 100 },
    { title: '来源', dataIndex: 'sourceType', resizable: true, width: 150 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 240 },
  ]);

  function convertStatusColor(status) {
    if (status === CONVERT_STATUS_ENUM.COMPLETED.value) {
      return 'green';
    }
    if (status === CONVERT_STATUS_ENUM.REJECTED.value) {
      return 'red';
    }
    return 'orange';
  }

  // =========== 查询 ===========
  const queryFormState = {
    searchWord: '',
    convertType: undefined,
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
      let res = await productConvertApi.query(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // =========== 新增 / 编辑 ===========
  const formRef = ref();
  const visible = ref(false);
  let rowKeySeed = 0;
  const formDefault = {
    convertId: undefined,
    convertType: CONVERT_TYPE_ENUM.SPLIT.value,
    warehouseId: 1,
    sourceType: 1,
    items: [],
  };
  let form = reactive(_.cloneDeep(formDefault));

  const itemColumns = [
    { title: '原商品', dataIndex: 'sourceProductId', width: 170 },
    { title: '原规格', dataIndex: 'sourceSkuId', width: 170 },
    { title: '出库数量', dataIndex: 'sourceQuantity', width: 120 },
    { title: '出库重量kg', dataIndex: 'sourceWeight', width: 120 },
    { title: '目标商品', dataIndex: 'targetProductId', width: 170 },
    { title: '目标规格', dataIndex: 'targetSkuId', width: 170 },
    { title: '入库数量', dataIndex: 'targetQuantity', width: 120 },
    { title: '入库重量kg', dataIndex: 'targetWeight', width: 120 },
    { title: '入库单价', dataIndex: 'targetUnitPrice', width: 120 },
    { title: '操作', dataIndex: 'action', width: 90 },
  ];

  const rules = {
    convertType: [{ required: true, message: '转换类型不能为空' }],
  };

  function addItem() {
    form.items.push({
      _rowKey: ++rowKeySeed,
      sourceProductId: undefined,
      sourceSkuId: undefined,
      sourceQuantity: undefined,
      sourceWeight: undefined,
      targetProductId: undefined,
      targetSkuId: undefined,
      targetQuantity: undefined,
      targetWeight: undefined,
      targetUnitPrice: undefined,
    });
  }

  function removeItem(record) {
    let index = form.items.indexOf(record);
    if (index > -1) {
      form.items.splice(index, 1);
    }
  }

  async function addOrUpdate(rowData) {
    Object.assign(form, _.cloneDeep(formDefault));
    if (rowData && rowData.convertId) {
      try {
        SmartLoading.show();
        let res = await productConvertApi.detail(rowData.convertId);
        let detail = res.data;
        form.convertId = detail.convertId;
        form.convertType = detail.convertType;
        form.warehouseId = detail.warehouseId;
        form.sourceType = detail.sourceType;
        form.items = (detail.items || []).map((e) => ({ ...e, _rowKey: ++rowKeySeed }));
      } catch (e) {
        smartSentry.captureError(e);
        return;
      } finally {
        SmartLoading.hide();
      }
    } else {
      form.items = [];
    }
    visible.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function onClose() {
    Object.assign(form, _.cloneDeep(formDefault));
    visible.value = false;
  }

  function onSubmit() {
    formRef.value
      .validate()
      .then(async () => {
        if (form.items.length === 0) {
          message.error('请至少添加一条转换明细');
          return;
        }
        // 明细必填校验：原/目标商品、原/目标规格、出库/入库数量、入库单价
        let invalid = form.items.some((e) => {
          let required = [
            e.sourceProductId,
            e.sourceSkuId,
            e.sourceQuantity,
            e.targetProductId,
            e.targetSkuId,
            e.targetQuantity,
            e.targetUnitPrice,
          ];
          return required.some((v) => v === undefined || v === null || v === '');
        });
        if (invalid) {
          message.error('转换明细的原/目标商品、规格、数量、入库单价均不能为空');
          return;
        }
        SmartLoading.show();
        try {
          let param = {
            convertId: form.convertId,
            convertType: form.convertType,
            warehouseId: form.warehouseId,
            sourceType: form.sourceType,
            items: form.items.map((e) => ({
              sourceProductId: e.sourceProductId,
              sourceSkuId: e.sourceSkuId,
              sourceQuantity: e.sourceQuantity,
              sourceWeight: e.sourceWeight,
              targetProductId: e.targetProductId,
              targetSkuId: e.targetSkuId,
              targetQuantity: e.targetQuantity,
              targetWeight: e.targetWeight,
              targetUnitPrice: e.targetUnitPrice,
            })),
          };
          if (form.convertId) {
            await productConvertApi.update(param);
          } else {
            await productConvertApi.add(param);
          }
          message.success(`${form.convertId ? '修改' : '添加'}成功`);
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

  // =========== 审核 / 驳回 ===========
  function approve(record) {
    Modal.confirm({
      title: '提示',
      content: `审核通过后将生成转换出/入库流水，确定继续吗?`,
      okText: '审核通过',
      onOk() {
        doApprove(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doApprove(record) {
    try {
      SmartLoading.show();
      await productConvertApi.approve({ convertId: record.convertId });
      message.success('审核成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function reject(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要驳回转换单【' + record.convertNo + '】吗?',
      okText: '驳回',
      okType: 'danger',
      onOk() {
        doReject(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doReject(record) {
    try {
      SmartLoading.show();
      await productConvertApi.reject({ convertId: record.convertId });
      message.success('已驳回');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 详情 ===========
  const detailVisible = ref(false);
  const detail = reactive({ convertNo: '', convertType: undefined, warehouseId: undefined, status: undefined, items: [] });
  const detailItemColumns = [
    { title: '原商品ID', dataIndex: 'sourceProductId', width: 100 },
    { title: '原规格ID', dataIndex: 'sourceSkuId', width: 100 },
    { title: '出库数量', dataIndex: 'sourceQuantity', width: 100 },
    { title: '出库重量kg', dataIndex: 'sourceWeight', width: 110 },
    { title: '目标商品ID', dataIndex: 'targetProductId', width: 100 },
    { title: '目标规格ID', dataIndex: 'targetSkuId', width: 100 },
    { title: '入库数量', dataIndex: 'targetQuantity', width: 100 },
    { title: '入库重量kg', dataIndex: 'targetWeight', width: 110 },
    { title: '入库单价', dataIndex: 'targetUnitPrice', width: 100 },
    { title: '入库金额', dataIndex: 'targetAmount', width: 110 },
  ];

  async function openDetail(record) {
    try {
      SmartLoading.show();
      let res = await productConvertApi.detail(record.convertId);
      Object.assign(detail, res.data);
      detail.items = res.data.items || [];
      detailVisible.value = true;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 删除 ===========
  const selectedRowKeyList = ref([]);
  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function deleteOne(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除转换单【' + record.convertNo + '】吗?',
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
      await productConvertApi.delete(record.convertId);
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
      content: '确定要删除选中的转换单吗?',
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
      await productConvertApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 表格高度 ===========
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
