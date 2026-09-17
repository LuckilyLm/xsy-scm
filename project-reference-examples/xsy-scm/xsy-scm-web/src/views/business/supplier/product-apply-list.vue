<!--
  * 供应商商品提报
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="商品名称/别名" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="商品名称/别名" allow-clear />
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="queryForm.supplierId" width="200px" />
      </a-form-item>
      <a-form-item label="审核状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SUPPLIER_APPLY_STATUS_ENUM" v-model:value="queryForm.auditStatus" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'supplierProductApply:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'supplierProductApply:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'supplierProductApply:add'">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'supplierProductApply:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.SUPPLIER.PRODUCT_APPLY" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="applyId"
      :scroll="{ x: 1400, y: yHeight }"
      bordered
      :pagination="false"
      :loading="tableLoading"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'auditStatus'">
          <a-tag :color="auditStatusColor(text)">{{ $smartEnumPlugin.getDescByValue('SUPPLIER_APPLY_STATUS_ENUM', text) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button v-if="record.auditStatus === 1" @click="openAudit(record)" type="link" v-privilege="'supplierProductApply:audit'">审核</a-button>
            <a-button v-if="record.auditStatus === 1" @click="addOrUpdate(record)" type="link" v-privilege="'supplierProductApply:update'">编辑</a-button>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'supplierProductApply:delete'">删除</a-button>
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

  <a-drawer :title="form.applyId ? '编辑商品提报' : '添加商品提报'" :width="450" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }">
      <a-form-item label="供应商" name="supplierId">
        <SupplierSelect v-model:value="form.supplierId" />
      </a-form-item>
      <a-form-item label="商品名称" name="productName">
        <a-input v-model:value="form.productName" placeholder="请输入商品名称" />
      </a-form-item>
      <a-form-item label="商品别名" name="alias">
        <a-input v-model:value="form.alias" placeholder="别名（≤20 字）" :maxlength="20" />
      </a-form-item>
      <a-form-item label="拟归类ID" name="categoryId">
        <a-input-number style="width: 100%" v-model:value="form.categoryId" :min="1" :precision="0" placeholder="可空" />
      </a-form-item>
      <a-form-item label="供货价" name="supplyPrice">
        <a-input-number style="width: 100%" v-model:value="form.supplyPrice" :min="0" :precision="4" placeholder="不含税" />
      </a-form-item>
      <a-form-item label="商品图片" name="image">
        <a-input v-model:value="form.image" placeholder="图片地址（文件服务）" />
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

  <!-- 审核弹窗：通过生成商品草稿，驳回需填写原因 -->
  <a-modal :title="auditForm.auditStatus === 3 ? '驳回提报' : '通过提报'" :open="auditVisible" :confirm-loading="auditLoading" @ok="onAuditSubmit" @cancel="onAuditClose">
    <a-form :label-col="{ span: 5 }">
      <a-form-item label="提报单号">
        <span>{{ auditForm.applyNo }}</span>
      </a-form-item>
      <a-form-item label="商品名称">
        <span>{{ auditForm.productName }}</span>
      </a-form-item>
      <a-form-item label="审核结果" required>
        <a-radio-group v-model:value="auditForm.auditStatus">
          <a-radio :value="2">通过</a-radio>
          <a-radio :value="3">驳回</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="auditForm.auditStatus === 3" label="驳回原因" required>
        <a-textarea v-model:value="auditForm.rejectReason" :rows="3" :maxlength="255" placeholder="请输入驳回原因" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import _ from 'lodash';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { supplierProductApplyApi } from '/@/api/business/supplier/supplier-product-apply-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import SupplierSelect from '/@/components/business/supplier-select/index.vue';
  import { SUPPLIER_APPLY_STATUS_ENUM } from '/@/constants/business/supplier/supplier-const';

  // =========== 表格列 ===========
  const columns = ref([
    { title: '提报ID', dataIndex: 'applyId', resizable: true, width: 100 },
    { title: '提报单号', dataIndex: 'applyNo', resizable: true, width: 180 },
    { title: '供应商ID', dataIndex: 'supplierId', resizable: true, width: 100 },
    { title: '商品名称', dataIndex: 'productName', resizable: true, width: 180 },
    { title: '商品别名', dataIndex: 'alias', resizable: true, width: 140 },
    { title: '供货价', dataIndex: 'supplyPrice', resizable: true, width: 120 },
    { title: '最近进价', dataIndex: 'lastPurchasePrice', resizable: true, width: 120 },
    { title: '审核状态', dataIndex: 'auditStatus', resizable: true, width: 110 },
    { title: '驳回原因', dataIndex: 'rejectReason', resizable: true, ellipsis: true, width: 180 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 170 },
  ]);

  function auditStatusColor(auditStatus) {
    if (auditStatus === SUPPLIER_APPLY_STATUS_ENUM.PASSED.value) {
      return 'green';
    }
    if (auditStatus === SUPPLIER_APPLY_STATUS_ENUM.REJECTED.value) {
      return 'red';
    }
    return 'orange';
  }

  // =========== 查询 ===========
  const queryFormState = {
    searchWord: '',
    supplierId: undefined,
    auditStatus: undefined,
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
      let res = await supplierProductApplyApi.query(queryForm);
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
  const formDefault = {
    applyId: undefined,
    supplierId: undefined,
    productName: undefined,
    alias: undefined,
    categoryId: undefined,
    supplyPrice: undefined,
    image: undefined,
  };
  let form = reactive({ ...formDefault });
  const rules = {
    supplierId: [{ required: true, message: '供应商不能为空' }],
    productName: [{ required: true, message: '商品名称不能为空' }],
  };

  function addOrUpdate(rowData) {
    Object.assign(form, formDefault);
    if (rowData && rowData.applyId) {
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
          if (form.applyId) {
            await supplierProductApplyApi.update(form);
          } else {
            await supplierProductApplyApi.add(form);
          }
          message.success(`${form.applyId ? '修改' : '添加'}成功`);
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

  // =========== 审核 ===========
  const auditVisible = ref(false);
  const auditLoading = ref(false);
  const auditFormDefault = {
    applyId: undefined,
    applyNo: '',
    productName: '',
    auditStatus: 2,
    rejectReason: '',
  };
  const auditForm = reactive({ ...auditFormDefault });

  function openAudit(record) {
    Object.assign(auditForm, auditFormDefault);
    auditForm.applyId = record.applyId;
    auditForm.applyNo = record.applyNo;
    auditForm.productName = record.productName;
    auditVisible.value = true;
  }

  function onAuditClose() {
    Object.assign(auditForm, auditFormDefault);
    auditVisible.value = false;
  }

  async function onAuditSubmit() {
    if (auditForm.auditStatus === 3 && !auditForm.rejectReason) {
      message.error('请填写驳回原因');
      return;
    }
    auditLoading.value = true;
    try {
      await supplierProductApplyApi.audit(auditForm);
      message.success('审核成功');
      onAuditClose();
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      auditLoading.value = false;
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
      content: '确定要删除提报单【' + record.applyNo + '】吗?',
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
      await supplierProductApplyApi.delete(record.applyId);
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
      content: '确定要删除选中的商品提报吗?',
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
      await supplierProductApplyApi.batchDelete(selectedRowKeyList.value);
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
