<template>
  <a-tabs v-model:activeKey="activeTab">
    <a-tab-pane key="by-order" tab="按单据">
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="收货单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.receiptNo" placeholder="收货单号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="采购单号" class="smart-query-form-item">
        <a-input v-model:value="orderNoInput" placeholder="采购单号（自动解析）" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_RECEIPT_STATUS_ENUM" v-model:value="queryForm.status" width="140px"/>
      </a-form-item>
      <a-form-item label="入库方式" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.receiptMode"
            :options="receiptModeOptions"
            placeholder="全部"
            allow-clear
            style="width: 160px"
        />
      </a-form-item>
      <a-form-item label="入库状态" class="smart-query-form-item">
        <a-select
            v-model:value="queryForm.putawayStatus"
            :options="putawayStatusOptions"
            placeholder="全部"
            allow-clear
            style="width: 130px"
        />
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
        <a-button danger v-privilege="'scm:purchase:receipt:delete'" :disabled="!selected.length" @click="batchDelete">
          批量删除草稿
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_RECEIPT" :refresh="queryData"/>
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
        :scroll="{ x: 1720 }"
        :row-selection="{
        selectedRowKeys: selected,
        onChange: (keys: (string | number)[]) => (selected = keys),
        getCheckboxProps: (row: Receipt) => ({ disabled: row.status !== 'DRAFT' }),
      }"
    >
      <template #bodyCell="{ record, column }">
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
          <!-- 行内只留当前状态唯一的推进动作（草稿=确认收货、待入库=确认入库）；
               编辑备注与删除是低频/危险动作，收进「更多」，
               否则 250px 的操作列里最显眼的永远是那排「编辑备注 删除」 -->
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
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)"/>
          </a-space>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
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
      <PurchaseReceiptItemWorkbench/>
    </a-tab-pane>
  </a-tabs>

  <PurchaseReceiptForm ref="form" @saved="queryData"/>
  <PurchaseReceiptConfirm ref="confirmModal" @saved="queryData"/>
</template>

<script setup lang="ts">
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
import PurchaseReceiptItemWorkbench from './components/purchase-receipt-item-workbench.vue';

const queryForm = reactive<ReceiptQuery>({pageNum: 1, pageSize: 20});
const receiptModeOptions = Object.values(SCM_RECEIPT_MODE_ENUM).map((i) => ({value: i.value, label: i.desc}));
const putawayStatusOptions = Object.values(SCM_PUTAWAY_STATUS_ENUM).map((i) => ({value: i.value, label: i.desc}));
/** 按单据（既有写流程）为默认 Tab；按商品是只读收货工作台。 */
const activeTab = ref('by-order');
/** 用户输入的是**采购单号**（业务视角），请求参数要的是 `purchaseOrderId`。 */
const orderNoInput = ref<string | undefined>(undefined);
const tableData = ref<Receipt[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const selected = ref<(string | number)[]>([]);
const form = ref<InstanceType<typeof PurchaseReceiptForm>>();
const confirmModal = ref<InstanceType<typeof PurchaseReceiptConfirm>>();
let requestId = 0;

const columns = ref<TableColumnsType<Receipt>>([
  {title: '收货单号', dataIndex: 'receiptNo', width: 210},
  {title: '采购单号', dataIndex: 'purchaseOrderNo', width: 210},
  {title: '供应商', dataIndex: 'supplierName', width: 180},
  {title: '收货仓库', dataIndex: 'warehouseName', width: 140},
  {title: '状态', dataIndex: 'status', align: 'center', width: 110},
  {title: '入库方式', dataIndex: 'receiptMode', align: 'center', width: 120},
  {title: '入库状态', dataIndex: 'putawayStatus', align: 'center', width: 110},
  {title: '确认时间', dataIndex: 'confirmedAt', width: 190},
  {title: '操作者', dataIndex: 'operator', width: 120},
  {title: '备注', dataIndex: 'remark', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 150},
]);

/** 「更多」里的菜单项挂不上 `v-privilege` 指令，改用同一口径的 hasPermission 裁剪。 */
const canUpdate = computed(() => hasPermission('scm:purchase:receipt:update'));
const canDelete = computed(() => hasPermission('scm:purchase:receipt:delete'));

/** 只有草稿可写；动作集合与原行内按钮一一对应，只按频率重新分组。 */
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
  const r = await purchaseOrderApi.query({pageNum: 1, pageSize: 1, orderNo: no});
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
    const r = await purchaseReceiptApi.query(queryForm);
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
      try {
        await purchaseReceiptApi.delete({id: row.id!});
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

function batchDelete() {
  Modal.confirm({
    title: '删除所选草稿收货单？',
    okType: 'danger',
    onOk: async () => {
      try {
        await purchaseReceiptApi.batchDelete(
            tableData.value
                .filter((r) => selected.value.includes(r.id!))
                .map((r) => ({id: r.id!, version: r.version!}))
        );
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

/** 仓库确认入库（B1）：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。 */
async function putaway(row: Receipt) {
  Modal.confirm({
    title: '确认入库？',
    content: '该操作会把本收货单数量正式记入库存（不可撤销）。',
    onOk: async () => {
      try {
        await purchaseReceiptApi.putaway({id: row.id!, version: row.version!});
        message.success('已入库');
        await queryData();
      } catch (e) {
        error.value = purchaseError(e);
        throw e;
      }
    },
  });
}

// ：支持从库存流水页「来源单号」跳进来； 待办卡片则带 status/receiptMode/putawayStatus。
// 两者都只读 URL、填进已有筛选框，不新增任何后端能力——待办数字与列表结果必须同源，
// 否则「待办 5 条」点进来看到的不是那 5 条。
// 用 `route.query` 而不是 props：菜单路由不会传 props，而 `query` 是 hash 路由下唯一稳定的传参方式。
// 取值必须过状态字典白名单：URL 可被手改/收藏转发，不能把任意串透传给查询接口。
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
      // SmartAdmin caches by route.name. Reapply the source link on reuse, but
      // ignore navigation to other pages while this component stays cached.
      if (name !== receiptRouteName) return;
      const filters = deepLinkFilters(incomingQuery, RECEIPT_DEEP_LINK);
      queryForm.receiptNo = filters.receiptNo;
      queryForm.status = filters.status;
      queryForm.receiptMode = filters.receiptMode;
      queryForm.putawayStatus = filters.putawayStatus;
      // 采购单号需要异步解析成 id，且不属于任何 deep-link 语义：进入页面即回落到未筛选。
      queryForm.purchaseOrderId = undefined;
      orderNoInput.value = undefined;
      onSearch();
    },
    {immediate: true}
);
</script>
