<!--
  * 采购询价报价
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="询价单号/名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.searchWord" placeholder="询价单号/名称" allow-clear />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="INQUIRY_STATUS_ENUM" v-model:value="queryForm.status" width="130px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'inquiry:query'">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="resetQuery" v-privilege="'inquiry:query'">
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
        <a-button @click="addOrUpdate()" type="primary" v-privilege="'inquiry:add'">
          <template #icon><PlusOutlined /></template>
          新建询价
        </a-button>
        <a-button @click="confirmBatchDelete" danger :disabled="selectedRowKeyList.length === 0" v-privilege="'inquiry:batchDelete'">
          <template #icon><DeleteOutlined /></template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.PURCHASE.INQUIRY" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      rowKey="inquiryId"
      :scroll="{ x: 1300, y: yHeight }"
      bordered
      :pagination="false"
      :loading="tableLoading"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="inquiryStatusColor(text)">{{ $smartEnumPlugin.getDescByValue('INQUIRY_STATUS_ENUM', text) }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="openDetail(record)" type="link" v-privilege="'inquiry:query'">详情</a-button>
            <a-button v-if="canQuote(record)" @click="openQuote(record)" type="link" v-privilege="'inquiry:quote'">报价</a-button>
            <a-button @click="openCompare(record)" type="link" v-privilege="'inquiry:query'">对比</a-button>
            <a-button v-if="record.status === 1" @click="addOrUpdate(record)" type="link" v-privilege="'inquiry:update'">编辑</a-button>
            <a-dropdown v-if="record.status === 1 || record.status === 2">
              <a class="ant-dropdown-link" @click.prevent v-privilege="'inquiry:changeStatus'">更多 <DownOutlined /></a>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="completed" @click="changeStatus(record, 3)">完成询价</a-menu-item>
                  <a-menu-item key="cancelled" @click="changeStatus(record, 4)">取消询价</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-button @click="deleteOne(record)" danger type="link" v-privilege="'inquiry:delete'">删除</a-button>
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

  <!-- 新建 / 编辑询价单 -->
  <a-drawer :title="form.inquiryId ? '编辑询价单' : '新建询价单'" :width="900" :open="visible" :body-style="{ paddingBottom: '80px' }" @close="onClose">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <a-form-item label="询价单名称" name="inquiryName">
        <a-input v-model:value="form.inquiryName" placeholder="请输入询价单名称" />
      </a-form-item>
      <a-form-item label="有效开始时间" name="validStart">
        <a-date-picker style="width: 100%" show-time v-model:value="form.validStart" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="有效结束时间" name="validEnd">
        <a-date-picker style="width: 100%" show-time v-model:value="form.validEnd" valueFormat="YYYY-MM-DD HH:mm:ss" />
      </a-form-item>
      <a-form-item label="询价明细">
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
          :scroll="{ x: 700 }"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'productId'">
              <ProductSelect v-model:value="record.productId" />
            </template>
            <template v-if="column.dataIndex === 'skuId'">
              <SkuSelect v-model:value="record.skuId" />
            </template>
            <template v-if="column.dataIndex === 'requireQuantity'">
              <a-input-number style="width: 100%" v-model:value="record.requireQuantity" :min="0" :precision="3" />
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

  <!-- 供应商报价 -->
  <a-modal :title="'供应商报价 - ' + quoteForm.inquiryNo" :width="800" :open="quoteVisible" :confirm-loading="quoteLoading" @ok="onQuoteSubmit" @cancel="onQuoteClose">
    <a-form :label-col="{ span: 4 }">
      <a-form-item label="供应商" required>
        <SupplierSelect v-model:value="quoteForm.supplierId" />
      </a-form-item>
    </a-form>
    <a-table size="small" :dataSource="quoteForm.items" :columns="quoteColumns" rowKey="itemId" bordered :pagination="false">
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'quotePrice'">
          <a-input-number style="width: 100%" v-model:value="record.quotePrice" :min="0" :precision="4" />
        </template>
        <template v-if="column.dataIndex === 'score'">
          <a-input-number style="width: 100%" v-model:value="record.score" :min="0" :max="100" :precision="2" />
        </template>
      </template>
    </a-table>
  </a-modal>

  <!-- 方案对比 -->
  <a-modal title="询价方案对比" :width="900" :open="compareVisible" :footer="null" @cancel="compareVisible = false">
    <a-empty v-if="!compareData.items || compareData.items.length === 0" description="暂无报价数据" />
    <div v-for="item in compareData.items" :key="item.itemId" style="margin-bottom: 16px">
      <a-descriptions size="small" bordered :column="3" style="margin-bottom: 8px">
        <a-descriptions-item label="商品ID">{{ item.productId }}</a-descriptions-item>
        <a-descriptions-item label="规格ID">{{ item.skuId }}</a-descriptions-item>
        <a-descriptions-item label="询价数量">{{ item.requireQuantity }}</a-descriptions-item>
        <a-descriptions-item label="平均价">{{ item.avgPrice }}</a-descriptions-item>
        <a-descriptions-item label="中位价">{{ item.medianPrice }}</a-descriptions-item>
      </a-descriptions>
      <a-table size="small" :dataSource="item.quotes" :columns="compareQuoteColumns" rowKey="quoteId" bordered :pagination="false">
        <template #bodyCell="{ record, column }">
          <template v-if="column.dataIndex === 'quotePrice'">
            <a-tag v-if="isLowest(item, record)" color="green">{{ record.quotePrice }}</a-tag>
            <span v-else>{{ record.quotePrice }}</span>
          </template>
        </template>
      </a-table>
    </div>
  </a-modal>

  <!-- 询价单详情 -->
  <a-modal :title="'询价单详情 - ' + detail.inquiryNo" :width="700" :open="detailVisible" :footer="null" @cancel="detailVisible = false">
    <a-descriptions size="small" bordered :column="2" style="margin-bottom: 12px">
      <a-descriptions-item label="询价单名称">{{ detail.inquiryName }}</a-descriptions-item>
      <a-descriptions-item label="状态">{{ $smartEnumPlugin.getDescByValue('INQUIRY_STATUS_ENUM', detail.status) }}</a-descriptions-item>
      <a-descriptions-item label="有效开始时间">{{ detail.validStart }}</a-descriptions-item>
      <a-descriptions-item label="有效结束时间">{{ detail.validEnd }}</a-descriptions-item>
    </a-descriptions>
    <a-table size="small" :dataSource="detail.items" :columns="detailItemColumns" rowKey="itemId" bordered :pagination="false" />
  </a-modal>
</template>

<script setup lang="ts">
  import { onMounted, reactive, ref, nextTick } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import _ from 'lodash';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { inquiryApi } from '/@/api/business/purchase/inquiry-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
  import ProductSelect from '/@/components/business/product-select/index.vue';
  import SkuSelect from '/@/components/business/sku-select/index.vue';
  import SupplierSelect from '/@/components/business/supplier-select/index.vue';
  import { INQUIRY_STATUS_ENUM } from '/@/constants/business/purchase/purchase-const';

  // =========== 表格列 ===========
  const columns = ref([
    { title: '询价ID', dataIndex: 'inquiryId', resizable: true, width: 100 },
    { title: '询价单号', dataIndex: 'inquiryNo', resizable: true, width: 180 },
    { title: '询价单名称', dataIndex: 'inquiryName', resizable: true, width: 180 },
    { title: '有效开始时间', dataIndex: 'validStart', resizable: true, width: 170 },
    { title: '有效结束时间', dataIndex: 'validEnd', resizable: true, width: 170 },
    { title: '状态', dataIndex: 'status', resizable: true, width: 110 },
    { title: '创建时间', dataIndex: 'createTime', resizable: true, width: 170 },
    { title: '操作', dataIndex: 'action', resizable: true, fixed: 'right', width: 300 },
  ]);

  function inquiryStatusColor(status) {
    if (status === INQUIRY_STATUS_ENUM.COMPLETED.value) {
      return 'green';
    }
    if (status === INQUIRY_STATUS_ENUM.CANCELLED.value) {
      return 'red';
    }
    if (status === INQUIRY_STATUS_ENUM.QUOTING.value) {
      return 'blue';
    }
    return 'orange';
  }

  /**
   * 待报价 / 报价中 均允许继续录入报价（有效时间由后端校验）
   */
  function canQuote(record) {
    return record.status === INQUIRY_STATUS_ENUM.PENDING.value || record.status === INQUIRY_STATUS_ENUM.QUOTING.value;
  }

  // =========== 查询 ===========
  const queryFormState = {
    searchWord: '',
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
      let res = await inquiryApi.query(queryForm);
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
    inquiryId: undefined,
    inquiryName: undefined,
    validStart: undefined,
    validEnd: undefined,
    items: [],
  };
  let form = reactive(_.cloneDeep(formDefault));

  const itemColumns = [
    { title: '商品', dataIndex: 'productId', width: 220 },
    { title: '规格', dataIndex: 'skuId', width: 220 },
    { title: '询价数量', dataIndex: 'requireQuantity', width: 150 },
    { title: '操作', dataIndex: 'action', width: 90 },
  ];

  const rules = {
    validStart: [{ required: true, message: '有效开始时间不能为空' }],
    validEnd: [{ required: true, message: '有效结束时间不能为空' }],
  };

  function addItem() {
    form.items.push({ _rowKey: ++rowKeySeed, itemId: undefined, productId: undefined, skuId: undefined, requireQuantity: undefined });
  }

  function removeItem(record) {
    let index = form.items.indexOf(record);
    if (index > -1) {
      form.items.splice(index, 1);
    }
  }

  async function addOrUpdate(rowData) {
    Object.assign(form, _.cloneDeep(formDefault));
    if (rowData && rowData.inquiryId) {
      try {
        SmartLoading.show();
        let res = await inquiryApi.detail(rowData.inquiryId);
        let detail = res.data;
        form.inquiryId = detail.inquiryId;
        form.inquiryName = detail.inquiryName;
        form.validStart = detail.validStart;
        form.validEnd = detail.validEnd;
        form.items = (detail.items || []).map((e) => ({ ...e, _rowKey: ++rowKeySeed }));
      } catch (e) {
        smartSentry.captureError(e);
        return;
      } finally {
        SmartLoading.hide();
      }
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
          message.error('请至少添加一条询价明细');
          return;
        }
        // 明细必填校验：商品 / 规格 / 数量
        let invalid = form.items.some((e) => !e.productId || !e.skuId || e.requireQuantity === undefined || e.requireQuantity === null);
        if (invalid) {
          message.error('询价明细的商品、规格、询价数量均不能为空');
          return;
        }
        SmartLoading.show();
        try {
          let param = {
            inquiryId: form.inquiryId,
            inquiryName: form.inquiryName,
            validStart: form.validStart,
            validEnd: form.validEnd,
            items: form.items.map((e) => ({
              productId: e.productId,
              skuId: e.skuId,
              requireQuantity: e.requireQuantity,
            })),
          };
          if (form.inquiryId) {
            await inquiryApi.update(param);
          } else {
            await inquiryApi.add(param);
          }
          message.success(`${form.inquiryId ? '修改' : '添加'}成功`);
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

  // =========== 详情 ===========
  const detailVisible = ref(false);
  const detail = reactive({ inquiryNo: '', inquiryName: '', status: undefined, validStart: '', validEnd: '', items: [] });
  const detailItemColumns = [
    { title: '商品ID', dataIndex: 'productId' },
    { title: '规格ID', dataIndex: 'skuId' },
    { title: '询价数量', dataIndex: 'requireQuantity' },
  ];

  async function openDetail(record) {
    try {
      SmartLoading.show();
      let res = await inquiryApi.detail(record.inquiryId);
      Object.assign(detail, res.data);
      detail.items = res.data.items || [];
      detailVisible.value = true;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 供应商报价 ===========
  const quoteVisible = ref(false);
  const quoteLoading = ref(false);
  const quoteFormDefault = {
    inquiryId: undefined,
    inquiryNo: '',
    supplierId: undefined,
    items: [],
  };
  const quoteForm = reactive(_.cloneDeep(quoteFormDefault));
  const quoteColumns = [
    { title: '商品ID', dataIndex: 'productId', width: 100 },
    { title: '规格ID', dataIndex: 'skuId', width: 100 },
    { title: '询价数量', dataIndex: 'requireQuantity', width: 110 },
    { title: '报价(不含税)', dataIndex: 'quotePrice', width: 170 },
    { title: '综合评分(0~100)', dataIndex: 'score', width: 160 },
  ];

  async function openQuote(record) {
    try {
      SmartLoading.show();
      let res = await inquiryApi.detail(record.inquiryId);
      let detail = res.data;
      Object.assign(quoteForm, _.cloneDeep(quoteFormDefault));
      quoteForm.inquiryId = detail.inquiryId;
      quoteForm.inquiryNo = detail.inquiryNo;
      quoteForm.items = (detail.items || []).map((e) => ({
        itemId: e.itemId,
        productId: e.productId,
        skuId: e.skuId,
        requireQuantity: e.requireQuantity,
        quotePrice: undefined,
        score: undefined,
      }));
      quoteVisible.value = true;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  function onQuoteClose() {
    Object.assign(quoteForm, _.cloneDeep(quoteFormDefault));
    quoteVisible.value = false;
  }

  async function onQuoteSubmit() {
    if (!quoteForm.supplierId) {
      message.error('请选择供应商');
      return;
    }
    if (quoteForm.items.some((e) => e.quotePrice === undefined || e.quotePrice === null)) {
      message.error('请完整填写每一条报价');
      return;
    }
    quoteLoading.value = true;
    try {
      await inquiryApi.quote({
        inquiryId: quoteForm.inquiryId,
        supplierId: quoteForm.supplierId,
        items: quoteForm.items.map((e) => ({ itemId: e.itemId, quotePrice: e.quotePrice, score: e.score })),
      });
      message.success('报价成功');
      onQuoteClose();
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      quoteLoading.value = false;
    }
  }

  // =========== 方案对比 ===========
  const compareVisible = ref(false);
  const compareData = reactive({ inquiryNo: '', inquiryName: '', items: [] });
  const compareQuoteColumns = [
    { title: '供应商ID', dataIndex: 'supplierId', width: 100 },
    { title: '报价(不含税)', dataIndex: 'quotePrice', width: 130 },
    { title: '综合评分', dataIndex: 'score', width: 100 },
    { title: '与平均价差额', dataIndex: 'avgDiff', width: 130 },
    { title: '与中位价差额', dataIndex: 'medianDiff', width: 130 },
  ];

  /**
   * 是否为该商品最低报价（用于高亮最优方案）
   */
  function isLowest(item, quote) {
    if (!item.quotes || item.quotes.length === 0) {
      return false;
    }
    let min = Math.min(...item.quotes.map((e) => Number(e.quotePrice)));
    return Number(quote.quotePrice) === min;
  }

  async function openCompare(record) {
    try {
      SmartLoading.show();
      let res = await inquiryApi.compare(record.inquiryId);
      Object.assign(compareData, res.data);
      compareData.items = res.data.items || [];
      compareVisible.value = true;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // =========== 状态变更 ===========
  function changeStatus(record, status) {
    let statusText = status === INQUIRY_STATUS_ENUM.COMPLETED.value ? '完成' : '取消';
    Modal.confirm({
      title: '提示',
      content: `确定要${statusText}询价单【${record.inquiryNo}】吗?`,
      okText: '确定',
      onOk() {
        doChangeStatus(record, status);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function doChangeStatus(record, status) {
    try {
      SmartLoading.show();
      await inquiryApi.changeStatus({ inquiryId: record.inquiryId, status });
      message.success('操作成功');
      queryData();
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
      content: '确定要删除询价单【' + record.inquiryNo + '】吗?',
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
      await inquiryApi.delete(record.inquiryId);
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
      content: '确定要删除选中的询价单吗?',
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
      await inquiryApi.batchDelete(selectedRowKeyList.value);
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
