<template>
  <a-tabs v-model:activeKey="activeTab">
    <a-tab-pane key="by-order" tab="按单据">
      <a-form class="smart-query-form" layout="inline" @submit.prevent>
        <a-row class="smart-query-form-row">
          <a-form-item label="收货单号" class="smart-query-form-item">
            <a-input v-model:value="queryForm.receiptNo" placeholder="收货单号" allow-clear @pressEnter="onSearch" />
          </a-form-item>
          <a-form-item label="采购单号" class="smart-query-form-item">
            <a-input v-model:value="orderNoInput" placeholder="输入采购单号" allow-clear @pressEnter="onSearch" />
          </a-form-item>
          <a-form-item label="状态" class="smart-query-form-item">
            <SmartEnumSelect enum-name="SCM_RECEIPT_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
          </a-form-item>
          <a-form-item label="入库方式" class="smart-query-form-item">
            <a-select v-model:value="queryForm.receiptMode" :options="receiptModeOptions" placeholder="全部" allow-clear style="width: 160px" />
          </a-form-item>
          <a-form-item label="入库状态" class="smart-query-form-item">
            <a-select v-model:value="queryForm.putawayStatus" :options="putawayStatusOptions" placeholder="全部" allow-clear style="width: 130px" />
          </a-form-item>
          <a-form-item class="smart-query-form-item">
            <a-button-group>
              <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:receipt:query'">查询</a-button>
              <a-button @click="resetQuery">重置</a-button>
            </a-button-group>
          </a-form-item>
        </a-row>
      </a-form>

      <a-alert v-if="error" :message="error" type="error" show-icon>
        <template #action>
          <a-button @click="queryData">重试</a-button>
        </template>
      </a-alert>

      <a-card size="small" :bordered="false">
        <a-row class="smart-table-btn-block">
          <div class="smart-table-operate-block">
            <a-button type="primary" v-privilege="'scm:purchase:receipt:add'" @click="form?.open()">新建收货单</a-button>
            <a-button danger v-privilege="'scm:purchase:receipt:delete'" :disabled="!selected.length" @click="batchDelete"> 批量删除草稿 </a-button>
          </div>
          <div class="smart-table-setting-block">
            <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_RECEIPT" :refresh="queryData" />
          </div>
        </a-row>

        <a-table
          :id="SCM_PURCHASE_TABLE_ID.RECEIPT"
          size="small"
          :data-source="tableData"
          :columns="columns"
          row-key="id"
          bordered
          :loading="loading"
          :pagination="false"
          :scroll="{x: scmColumnsWidth(columns, SCM_COLUMN_WIDTH.selection)}"
          :row-selection="{
            columnWidth: SCM_COLUMN_WIDTH.selection,
            selectedRowKeys: selected,
            onChange: (keys: (string | number)[]) => (selected = keys),
            getCheckboxProps: (row: Receipt) => ({disabled: row.status !== 'DRAFT'}),
          }"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'status'">
              <a-tag>{{ SCM_RECEIPT_STATUS_ENUM[record.status]?.desc }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'receiptMode'">
              <a-tag>{{ SCM_RECEIPT_MODE_ENUM[record.receiptMode]?.desc || record.receiptMode }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'putawayStatus'">
              <a-tag :color="record.putawayStatus === 'COMPLETED' ? 'green' : 'orange'">
                {{ SCM_PUTAWAY_STATUS_ENUM[record.putawayStatus]?.desc || record.putawayStatus }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <!-- 只保留当前状态的推进动作，编辑备注和删除收进“更多” -->
              <a-space :size="0" class="smart-table-operate scm-table-actions">
                <a-button
                  v-if="record.status === 'DRAFT'"
                  type="link"
                  size="small"
                  v-privilege="'scm:purchase:receipt:confirm'"
                  @click="confirmModal?.open(record.id)"
                >
                  确认收货
                </a-button>
                <a-button
                  v-else-if="record.status === 'CONFIRMED' && record.receiptMode === 'WAREHOUSE_CONFIRM' && record.putawayStatus === 'PENDING'"
                  type="link"
                  size="small"
                  v-privilege="'scm:purchase:receipt:putaway'"
                  @click="putaway(record)"
                >
                  确认入库
                </a-button>
                <a-button type="link" size="small" @click="detailDrawer?.open(record.id)">详情</a-button>
                <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="smart-query-table-page">
          <a-pagination
            show-size-changer
            :page-size-options="SCM_PAGE_SIZE_OPTIONS"
            show-quick-jumper
            v-model:current="queryForm.pageNum"
            v-model:page-size="queryForm.pageSize"
            :total="total"
            @change="queryData"
            :show-total="(n: number) => `共${n}条`"
          />
        </div>
      </a-card>
    </a-tab-pane>

    <a-tab-pane key="by-item" tab="按商品">
      <PurchaseReceiptItemWorkbench />
    </a-tab-pane>
  </a-tabs>

  <PurchaseReceiptForm ref="form" @saved="queryData" />
  <PurchaseReceiptConfirm ref="confirmModal" @saved="queryData" />
  <PurchaseReceiptDetailDrawer ref="detailDrawer" />
</template>

<script setup lang="ts">
import {SCM_COLUMN_WIDTH, scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {computed, reactive, ref, watch} from 'vue';
import {message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRoute} from 'vue-router';
import {purchaseReceiptApi} from '/@/api/business/scm/purchase-receipt-api';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {
  SCM_PURCHASE_TABLE_ID,
  SCM_PUTAWAY_STATUS_ENUM,
  SCM_RECEIPT_MODE_ENUM,
  SCM_RECEIPT_STATUS_ENUM,
} from '/@/constants/business/scm/purchase-const';
import type {Receipt, ReceiptQuery} from './purchase-types';
import {purchaseError} from './purchase-errors';
import {hasPermission} from '../common/scm-permission';
import {deepLinkFilters} from '/@/lib/query-deep-link';
import PurchaseReceiptForm from './components/purchase-receipt-form-drawer.vue';
import PurchaseReceiptConfirm from './components/purchase-receipt-confirm-modal.vue';
import PurchaseReceiptDetailDrawer from './components/purchase-receipt-detail-drawer.vue';
import PurchaseReceiptItemWorkbench from './components/purchase-receipt-item-workbench.vue';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const queryForm = reactive<ReceiptQuery>({pageNum: 1, pageSize: 20});
const receiptModeOptions = Object.values(SCM_RECEIPT_MODE_ENUM).map((i) => ({value: i.value, label: i.desc}));
const putawayStatusOptions = Object.values(SCM_PUTAWAY_STATUS_ENUM).map((i) => ({value: i.value, label: i.desc}));
/** 按单据（既有写流程）为默认 Tab；按商品是只读收货工作台。 */
const activeTab = ref('by-order');
/** 用户输入的是采购单号（业务视角），请求参数要的是 `purchaseOrderId`。 */
const orderNoInput = ref<string | undefined>(undefined);
const tableData = ref<Receipt[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const selected = ref<(string | number)[]>([]);
const form = ref<InstanceType<typeof PurchaseReceiptForm>>();
const confirmModal = ref<InstanceType<typeof PurchaseReceiptConfirm>>();
const detailDrawer = ref<InstanceType<typeof PurchaseReceiptDetailDrawer>>();
let requestId = 0;

const columns = ref<TableColumnsType<Receipt>>([
  {title: '收货单号', dataIndex: 'receiptNo', width: 210},
  {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 210},
  {title: '供应商', dataIndex: 'supplierName', width: 180},
  {title: '收货仓库', dataIndex: 'warehouseName', width: 140},
  {title: '状态', dataIndex: 'status', align: 'center', width: 96},
  {title: '入库方式', dataIndex: 'receiptMode', align: 'center', width: 120},
  {title: '入库状态', dataIndex: 'putawayStatus', align: 'center', width: 96},
  {title: '确认时间', dataIndex: 'confirmedAt', width: 180},
  {title: '操作者', dataIndex: 'operator', width: 120},
  {title: '备注', dataIndex: 'remark', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 160},
]);

/** 更多菜单无法使用 `v-privilege`，改用 `hasPermission` 判断。 */
const canUpdate = computed(() => hasPermission('scm:purchase:receipt:update'));
const canDelete = computed(() => hasPermission('scm:purchase:receipt:delete'));

/** 只有草稿可写。 */
function rowActions(row: Receipt): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  return [
    {key: 'edit', label: '编辑备注', hidden: !(draft && canUpdate.value)},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: Receipt) {
  if (key === 'edit') {
    form.value?.open(row.id);
  } else if (key === 'delete') {
    remove(row);
  }
}

/** 采购单号 → id：收货单列表按 `purchaseOrderId` 过滤，不能直接传单号。 */
async function resolveOrderId() {
  const no = orderNoInput.value?.trim();
  if (!no) {
    queryForm.purchaseOrderId = undefined;
    return;
  }
  const r = await purchaseOrderApi.query({pageNum: 1, pageSize: 1, orderNo: no}, {suppressGlobalErrorMessage: true});
  const first = r.data.list[0];
  if (!first) {
    throw new Error(`采购单 ${no} 不存在`);
  }
  queryForm.purchaseOrderId = first.id;
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    await resolveOrderId();
    // 列表加载失败由页头 Alert 承担（带重试），不再让全局 toast 重复说一遍
    const r = await purchaseReceiptApi.query(queryForm, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
      selected.value = [];
    }
  } catch (e) {
    if (id === requestId) {
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.receiptNo = undefined;
  queryForm.status = undefined;
  queryForm.receiptMode = undefined;
  queryForm.putawayStatus = undefined;
  queryForm.purchaseOrderId = undefined;
  orderNoInput.value = undefined;
  onSearch();
}

function remove(row: Receipt) {
  Modal.confirm({
    title: '删除这张草稿收货单？',
    okType: 'danger',
    onOk: async () => {
      // 失败原因由全局提示给出
      await purchaseReceiptApi.delete({id: row.id!});
      await queryData();
    },
  });
}

function batchDelete() {
  Modal.confirm({
    title: '删除所选草稿收货单？',
    okType: 'danger',
    onOk: async () => {
      await purchaseReceiptApi.batchDelete(
        tableData.value.filter((r) => selected.value.includes(r.id!)).map((r) => ({id: r.id!, version: r.version!}))
      );
      await queryData();
    },
  });
}

/** 仓库确认入库：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。 */
async function putaway(row: Receipt) {
  Modal.confirm({
    title: '确认入库？',
    content: '该操作会把本收货单数量正式记入库存（不可撤销）。',
    onOk: async () => {
      await purchaseReceiptApi.putaway({id: row.id!, version: row.version!});
      message.success('已入库');
      await queryData();
    },
  });
}

// 支持从库存流水和待办卡片进入；URL 只填入已有筛选，状态值必须在字典内。
const RECEIPT_DEEP_LINK = {
  receiptNo: null,
  status: Object.values(SCM_RECEIPT_STATUS_ENUM).map((i) => i.value),
  receiptMode: Object.values(SCM_RECEIPT_MODE_ENUM).map((i) => i.value),
  putawayStatus: Object.values(SCM_PUTAWAY_STATUS_ENUM).map((i) => i.value),
};

const route = useRoute();
const receiptRouteName = route.name;
watch(
  [() => route.name, () => route.query],
  ([name, incomingQuery]) => {
    // 组件被缓存时，只在回到本页时重新应用来源链接
    if (name !== receiptRouteName) return;
    const filters = deepLinkFilters(incomingQuery, RECEIPT_DEEP_LINK);
    queryForm.receiptNo = filters.receiptNo;
    queryForm.status = filters.status;
    queryForm.receiptMode = filters.receiptMode;
    queryForm.putawayStatus = filters.putawayStatus;
    // 采购单号不在链接参数中，进入页面时清空
    queryForm.purchaseOrderId = undefined;
    orderNoInput.value = undefined;
    onSearch();
  },
  {immediate: true}
);
</script>
