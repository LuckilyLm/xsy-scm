<!--
  * 库存调整单（报损报溢）列表
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="调整单号" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.adjustNo" placeholder="调整单号" />
      </a-form-item>

      <a-form-item label="调整类型" class="smart-query-form-item">
        <SmartEnumSelect enum-name="ADJUST_TYPE_ENUM" v-model:value="queryForm.adjustType" width="160px" />
      </a-form-item>

      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="ADJUST_STATUS_ENUM" v-model:value="queryForm.status" width="160px" />
      </a-form-item>

      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'stock:adjust:query'">
            <template #icon>
              <SearchOutlined />
            </template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'stock:adjust:query'">
            <template #icon>
              <ReloadOutlined />
            </template>
            重置
          </a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'stock:adjust:add'">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>

        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'stock:adjust:batchDelete'">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.ERP.STOCK_ADJUST" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="adjustId"
      :scroll="{ x: 1200, y: yHeight }"
      bordered
      :pagination="false"
      :showSorterTooltip="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #headerCell="{ column }">
        <SmartHeaderCell v-model:value="queryForm[column.filterOptions?.key || column.dataIndex]" :column="column" @change="queryData" />
      </template>
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'adjustType'">
          <span>{{ $smartEnumPlugin.getDescByValue('ADJUST_TYPE_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <span>{{ $smartEnumPlugin.getDescByValue('ADJUST_STATUS_ENUM', text) }}</span>
        </template>
        <template v-if="column.dataIndex === 'reason'">
          <span>{{ text ? text : '' }}</span>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="addOrUpdate(record)" type="link" v-privilege="'stock:adjust:update'">编辑</a-button>
            <a-button @click="openAudit(record, 'approve')" type="link" v-if="record.status === ADJUST_STATUS_ENUM.PENDING.value" v-privilege="'stock:adjust:approve'">
              审核通过
            </a-button>
            <a-button @click="openAudit(record, 'reject')" danger type="link" v-if="record.status === ADJUST_STATUS_ENUM.PENDING.value" v-privilege="'stock:adjust:reject'">
              驳回
            </a-button>
            <a-button @click="deleteAdjust(record)" danger type="link" v-privilege="'stock:adjust:delete'">删除</a-button>
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

  <!---------- 审核弹窗 begin ----------->
  <a-modal
    v-model:open="auditModalVisible"
    :title="auditFlag === 'approve' ? '审核通过' : '驳回'"
    :maskClosable="false"
    @ok="confirmAudit"
    @cancel="auditModalVisible = false"
  >
    <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="调整单号">
        <span>{{ currentRecord.adjustNo }}</span>
      </a-form-item>
      <a-form-item label="审核意见">
        <a-textarea v-model:value="opinion" :rows="4" :maxlength="200" placeholder="请输入审核意见（驳回时建议填写）" />
      </a-form-item>
    </a-form>
  </a-modal>
  <!---------- 审核弹窗 end ----------->

  <!---------- 新建/编辑 抽屉 begin ----------->
  <StockAdjustFormModal ref="formModal" @reloadList="queryData" />
  <!---------- 新建/编辑 抽屉 end ----------->
</template>
<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { stockAdjustApi } from '/@/api/business/stock/stock-adjust-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import SmartHeaderCell from '/@/components/support/table-header-cell/index.vue';
  import _ from 'lodash';
  import { ADJUST_STATUS_ENUM } from '/@/constants/business/erp/stock-const';
  import StockAdjustFormModal from './components/stock-adjust-form-modal.vue';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '调整单号',
      dataIndex: 'adjustNo',
      resizable: true,
      filterOptions: {
        type: 'input',
        key: 'adjustNo',
      },
      width: 180,
    },
    {
      title: '调整类型',
      dataIndex: 'adjustType',
      resizable: true,
      filterOptions: {
        type: 'enum-select',
        enumName: 'ADJUST_TYPE_ENUM',
      },
      width: 120,
    },
    {
      title: '调整数量',
      dataIndex: 'quantity',
      resizable: true,
      width: 110,
    },
    {
      title: '调整重量(kg)',
      dataIndex: 'weight',
      resizable: true,
      width: 120,
    },
    {
      title: '调整原因',
      dataIndex: 'reason',
      ellipsis: true,
      resizable: true,
      width: 200,
    },
    {
      title: '状态',
      dataIndex: 'status',
      resizable: true,
      filterOptions: {
        type: 'enum-select',
        enumName: 'ADJUST_STATUS_ENUM',
      },
      width: 110,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      resizable: true,
      width: 170,
    },
    {
      title: '操作',
      dataIndex: 'action',
      resizable: true,
      fixed: 'right',
      width: 180,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    adjustNo: '',
    adjustType: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 10,
    sortItemList: [],
  };
  // 查询表单form
  const queryForm = reactive(_.cloneDeep(queryFormState));
  // 表格加载loading
  const tableLoading = ref(false);
  // 表格数据
  const tableData = ref([]);
  // 总数
  const total = ref(0);

  function handleResizeColumn(w, col) {
    columns.value.forEach((item) => {
      if (item.dataIndex === col.dataIndex) {
        item.width = Math.floor(w);
        item.dragAndDropFlag = true;
      }
    });
  }

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, _.cloneDeep(queryFormState));
    queryForm.pageSize = pageSize;
    queryData();
  }

  // 搜索
  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await stockAdjustApi.query(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 新建/编辑 ----------------------------

  const formModal = ref();

  function addOrUpdate(rowData) {
    formModal.value.showDrawer(rowData);
  }

  // ---------------------------- 审核（通过/驳回） ----------------------------

  // 审核弹窗是否展示
  const auditModalVisible = ref(false);
  // 审核动作：approve 通过 / reject 驳回
  const auditFlag = ref('approve');
  // 当前操作的调整单
  const currentRecord = ref<any>({});
  // 审核意见
  const opinion = ref('');

  function openAudit(record, flag) {
    currentRecord.value = record;
    auditFlag.value = flag;
    opinion.value = '';
    auditModalVisible.value = true;
  }

  async function confirmAudit() {
    try {
      SmartLoading.show();
      let param = {
        adjustId: currentRecord.value.adjustId,
        opinion: opinion.value,
      };
      if (auditFlag.value === 'approve') {
        await stockAdjustApi.approve(param);
        message.success('审核通过成功');
      } else {
        await stockAdjustApi.reject(param);
        message.success('已驳回');
      }
      auditModalVisible.value = false;
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 删除 ----------------------------

  function deleteAdjust(record) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除调整单【' + record.adjustNo + '】吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        singleDelete(record);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function singleDelete(record) {
    try {
      SmartLoading.show();
      await stockAdjustApi.delete(record.adjustId);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量删除 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要删除选中的调整单吗?',
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
      await stockAdjustApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 动态设置表格高度 ----------------------------
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
