<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="订单号 / 客户" class="smart-query-form-item">
        <a-input v-model:value="queryForm.keyword" @pressEnter="onSearch" placeholder="名称或订单号" allow-clear />
      </a-form-item>
      <a-form-item label="订单来源" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_SOURCE_ENUM" v-model:value="queryForm.orderSource" width="140px" />
      </a-form-item>
      <a-form-item label="订单状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_ORDER_STATUS_ENUM" v-model:value="queryForm.status" width="140px" />
      </a-form-item>
      <a-form-item label="订单标签" class="smart-query-form-item">
        <a-select
          v-model:value="queryForm.tagId"
          :options="tagOptions"
          allow-clear
          placeholder="全部标签"
          style="width: 150px"
          show-search
          option-filter-prop="label"
        />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:order:query'">查询</a-button>
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
        <a-button type="primary" v-privilege="'scm:order:add'" @click="drawer?.open()">新建订单</a-button>
        <a-button v-privilege="'scm:order:import'" @click="importModal?.open()">导入订单</a-button>
        <a-button v-privilege="'scm:report:order:exception:query'" @click="router.push('/report/report-order-exceptions')">异常工作台</a-button>
        <a-button v-if="canPriceCheck" :disabled="!selected.length || selected.length > 8 || !selectedAllDraft" @click="openBatchPriceCheck"
          >批量核价（最多8单）</a-button
        >
        <a-button v-if="canManageTags" :disabled="!selected.length || selected.length > 20" @click="openBatchTags">批量打标签</a-button>
        <a-button v-if="canPrint" :disabled="!selected.length" @click="openBatchPrint">批量打印订单</a-button>
        <a-button v-if="canPrint" @click="customerSheetOpen = true">打印客户分拣单 / 送货单</a-button>
        <a-button danger v-privilege="'scm:order:delete'" :disabled="!selected.length || !selectedAllDraft" @click="batchDelete">
          批量删除草稿
        </a-button>
        <!-- 显示已选数量；混选到非草稿单时说明按钮为什么不可用 -->
        <span v-if="selected.length" class="scm-query-table-context">
          已选 {{ selected.length }} 单<template v-if="!selectedAllDraft"> · 含非草稿单，核价与删除不可用</template>
        </span>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="602" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      id="order-table"
      ref="tableRef"
      size="small"
      :data-source="tableData"
      :columns="displayColumns"
      row-key="orderId"
      bordered
      :loading="loading"
      :pagination="false"
      :scroll="{x: scrollX}"
      :row-selection="{
        columnWidth: SCM_COLUMN_WIDTH.selection,
        selectedRowKeys: selected,
        onChange: (keys: (string | number)[]) => (selected = keys),
      }"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'orderNo'"
          ><a @click="openDetail(record.orderId)">{{ record.orderNo }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'customerNameSnapshot'">{{ record.customerNameSnapshot || '—' }}</template>
        <template v-else-if="column.dataIndex === 'tags'">
          <a-space :size="4" wrap>
            <a-tag v-for="tag in record.tags ?? []" :key="String(tag.tagId)" :color="tagColor(tag.color)">{{ tag.name }}</a-tag>
            <span v-if="!record.tags?.length">—</span>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-space :size="4">
            <ScmStatusTag :tone="orderStatusTone(record.status)" :label="SCM_ORDER_STATUS_ENUM[record.status]?.desc" />
            <a-tooltip v-if="record.lockedFlag" :title="lockTip(record)">
              <a-tag color="orange">已锁</a-tag>
            </a-tooltip>
            <a-tooltip v-else-if="record.dispatchedAt" title="已发车出库，改单需要发车后改单权限">
              <a-tag color="blue">已发车</a-tag>
            </a-tooltip>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'orderSource'">{{ SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc }} </template>
        <template v-else-if="column.dataIndex === 'orderedTotalAmount'">
          <span class="scm-money">{{ amount(record.orderedTotalAmount, true) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'settlementTotalAmount'">
          <span class="scm-money">{{ amount(record.settlementTotalAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内只留当前状态最常用的动作，其余收进「更多」 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <template v-if="record.status === 'DRAFT'">
              <a-button type="link" size="small" v-privilege="'scm:order:update'" @click="drawer?.open(record.orderId)">编辑 </a-button>
              <a-button type="link" size="small" v-privilege="'scm:order:submit'" @click="submit(record)">提交 </a-button>
            </template>
            <template v-else-if="record.status === 'PENDING'">
              <a-button type="link" size="small" v-privilege="'scm:order:update'" @click="drawer?.open(record.orderId)">编辑 </a-button>
              <a-button type="link" size="small" @click="openDetail(record.orderId)">详情</a-button>
            </template>
            <template v-else-if="record.status === 'CONFIRMED'">
              <a-button type="link" size="small" @click="openDetail(record.orderId)">详情</a-button>
              <a-button
                type="link"
                size="small"
                v-privilege="'scm:order:update'"
                :disabled="record.editable === false"
                :title="record.editBlockedReason || ''"
                @click="drawer?.open(record.orderId)"
              >
                编辑
              </a-button>
              <a-button type="link" size="small" v-privilege="'scm:order:reserve-stock'" @click="reserveStock(record)">预留库存 </a-button>
            </template>
            <template v-else>
              <a-button type="link" size="small" @click="openDetail(record.orderId)">详情</a-button>
            </template>
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
  <OrderForm ref="drawer" @saved="queryData" />
  <OrderImportModal ref="importModal" @saved="queryData" />
  <OrderBatchPriceCheckModal ref="priceCheckModal" @saved="queryData" />
  <OrderTagModal ref="tagModal" @saved="onTagsSaved" />
  <PrintDocumentModal :open="printOpen" document-type="SALES_ORDER" :business-ids="printOrderIds" @close="printOpen = false" />
  <CustomerSheetBatchModal :open="customerSheetOpen" @close="customerSheetOpen = false" />
</template>
<script setup lang="ts">
import {SCM_COLUMN_WIDTH} from '/@/views/business/scm/common/scm-column';
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import {computed, nextTick, onMounted, reactive, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {SCM_ORDER_STATUS_ENUM, SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';
import type {Order, Query} from './order-types';
import {amount} from './order-form-model';
import {orderError} from './order-errors';
import {orderStatusTone} from './order-status-tone';
import {hasPermission} from '../common/scm-permission';
import OrderForm from './components/order-form-drawer.vue';
import OrderImportModal from './components/order-import-modal.vue';
import OrderBatchPriceCheckModal from './components/order-batch-price-check-modal.vue';
import OrderTagModal from './components/order-tag-modal.vue';
import PrintDocumentModal from '../print/print-document-modal.vue';
import CustomerSheetBatchModal from '../print/customer-sheet-batch-modal.vue';
import {orderTagApi, tagColor} from '/@/api/business/scm/order-tag-api';
import type {OrderTag} from '/@/api/business/scm/order-tag-api';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const queryForm = reactive<Query>({pageNum: 1, pageSize: 20}),
  tableData = ref<Order[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref(''),
  selected = ref<(string | number)[]>([]);
const drawer = ref<InstanceType<typeof OrderForm>>(),
  importModal = ref<InstanceType<typeof OrderImportModal>>();
const priceCheckModal = ref<InstanceType<typeof OrderBatchPriceCheckModal>>();
const tagModal = ref<InstanceType<typeof OrderTagModal>>();
const printOpen = ref(false);
const customerSheetOpen = ref(false);
const printOrderIds = ref<(string | number)[]>([]);
const availableTags = ref<OrderTag[]>([]);
const tagOptions = computed(() => availableTags.value.map((tag) => ({label: tag.name, value: tag.tagId})));

async function loadTags(): Promise<void> {
  try {
    availableTags.value = (await orderTagApi.list()).data;
  } catch {
    // 标签加载失败时隐藏筛选项，不额外提示
    availableTags.value = [];
  }
}
function onTagsSaved(): void {
  void loadTags();
  void queryData();
}
const route = useRoute();
const router = useRouter();

function openDetail(id?: string | number) {
  if (id != null) void router.push(`/order/detail/${id}`);
}

watch(
  () => route.path,
  (path) => {
    if (path === '/order/order-list') void queryData();
  }
);

// 消费一次快捷入口动作，避免刷新或返回列表时再次打开新建表单。
watch(
  () => route.query.action,
  async (action) => {
    if (route.path !== '/order/order-list' || action !== 'create') return;
    const query = {...route.query};
    delete query.action;
    await router.replace({path: route.path, query});
    await nextTick();
    if (hasPermission('scm:order:query') && hasPermission('scm:order:add')) {
      drawer.value?.open();
    }
  },
  {immediate: true, flush: 'post'}
);
let requestId = 0;
type OrderListColumn = TableColumnsType<Order>[number] & {showFlag?: boolean};
const columns = ref<OrderListColumn[]>([
  {title: '订单号', dataIndex: 'orderNo', width: 190},
  {title: '客户名称', dataIndex: 'customerNameSnapshot', width: 200},
  {title: '来源', dataIndex: 'orderSource', align: 'center', width: 100},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '标签', dataIndex: 'tags', width: 180},
  {title: '下单金额', dataIndex: 'orderedTotalAmount', width: 130},
  {title: '结算金额', dataIndex: 'settlementTotalAmount', width: 130},
  {title: '期望配送时间', dataIndex: 'expectDeliveryTime', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 160},
]);
// 宽屏剩余宽度补给文本列；用户改过列宽则原样渲染。
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns, () => SCM_COLUMN_WIDTH.selection);

/** 「更多」菜单挂不上 v-privilege，改用 hasPermission 判断。 */
const canAdd = computed(() => hasPermission('scm:order:add'));
const canDelete = computed(() => hasPermission('scm:order:delete'));
const canManageTags = computed(() => hasPermission('scm:order:query') && hasPermission('scm:order:update'));
const canPrint = computed(() => hasPermission('scm:order:query'));
const selectedRows = computed(() =>
  tableData.value.filter((row) => row.orderId != null && selected.value.some((id) => String(id) === String(row.orderId)))
);
const selectedAllDraft = computed(
  () => selectedRows.value.length === selected.value.length && selectedRows.value.every((row) => row.status === 'DRAFT')
);
function openBatchTags(): void {
  if (!canManageTags.value) return;
  tagModal.value?.open(selectedRows.value.map((row) => row.orderId!));
}
function openBatchPrint(): void {
  if (!canPrint.value || selectedRows.value.length !== selected.value.length || selectedRows.value.length < 1) return;
  openPrint(selectedRows.value.map((row) => row.orderId!));
}
function openPrint(ids: (string | number)[]): void {
  printOrderIds.value = ids;
  printOpen.value = true;
}
const canPriceCheck = computed(() => hasPermission('scm:order:query') && hasPermission('scm:pricing:resolve:query'));

function openBatchPriceCheck(): void {
  if (!canPriceCheck.value) return;
  const rows = tableData.value.filter((row) => row.orderId != null && selected.value.some((id) => String(id) === String(row.orderId)));
  priceCheckModal.value?.open(rows);
}

/** 当前状态的常用动作常驻行内，其余收进「更多」。 */
/** 锁单/解锁直接改改单权限，挂在「更多」里并各自要独立权限。 */
const canLock = computed(() => hasPermission('scm:order:lock'));
const canUnlock = computed(() => hasPermission('scm:order:unlock'));

function lockTip(row: Order): string {
  if (row.lockReason === 'AUTO_7_DAYS') return `发车满 7 天自动锁单${row.autoLockAt ? '（' + row.autoLockAt + '）' : ''}`;
  return row.lockedAt ? `已锁单（${row.lockedAt}）` : '已锁单';
}

function rowActions(row: Order): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  const confirmed = row.status === 'CONFIRMED';
  return [
    {key: 'detail', label: '详情', hidden: !draft},
    {key: 'reuse', label: '复用为新单', hidden: !(confirmed && canAdd.value)},
    {key: 'lock', label: '锁单', hidden: !(confirmed && !row.lockedFlag && canLock.value)},
    {key: 'unlock', label: '解锁', hidden: !(confirmed && row.lockedFlag && canUnlock.value)},
    {key: 'tag', label: '订单标签', hidden: !canManageTags.value},
    {key: 'print', label: '打印订单', hidden: !canPrint.value},
    {key: 'delete', label: '删除', danger: true, hidden: !(draft && canDelete.value)},
  ];
}

function onRowAction(key: string, row: Order) {
  // orderId 在响应里是可选的，用之前先判空
  if (row.orderId != null && key === 'detail') {
    openDetail(row.orderId);
  } else if (row.orderId != null && key === 'reuse') {
    drawer?.value?.openFromHistory(row.orderId);
  } else if (row.orderId != null && key === 'tag') {
    tagModal.value?.open([row.orderId]);
  } else if (row.orderId != null && key === 'print' && canPrint.value) {
    openPrint([row.orderId]);
  } else if (row.orderId != null && key === 'lock') {
    lockOrder(row);
  } else if (row.orderId != null && key === 'unlock') {
    unlockOrder(row);
  } else if (key === 'delete') {
    remove(row);
  }
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由页头 Alert 提示并可重试
    const r = await orderApi.query(queryForm, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      tableData.value = r.data.list;
      total.value = r.data.total;
      selected.value = [];
    }
  } catch (e) {
    if (id === requestId) error.value = orderError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  queryForm.orderSource = undefined;
  queryForm.tagId = undefined;
  onSearch();
}

function submit(r: Order) {
  Modal.confirm({
    title: '提交订单并锁定价格？',
    onOk: async () => {
      // 失败原因由全局提示给出；异常继续抛出，确认框保持打开
      await orderApi.submit({orderId: r.orderId, version: r.version});
      await queryData();
    },
  });
}

function remove(r: Order) {
  Modal.confirm({
    title: '删除这张草稿订单？',
    okType: 'danger',
    onOk: async () => {
      await orderApi.delete({orderId: r.orderId, version: r.version});
      await queryData();
    },
  });
}

/** 预留库存是单独操作，不在确认时自动执行；任一行可用量不足则整单失败。 */
function reserveStock(r: Order) {
  Modal.confirm({
    title: '为该订单预留库存？',
    content: '将按订单明细的实数量占用可用量（可用量 = 现有量 − 预留量）。任一行不足则整体失败，不会只占一半。',
    okText: '预留',
    onOk: async () => {
      await orderApi.reserveStock(r.orderId!);
      await queryData();
    },
  });
}

/**
 * 锁单是改单权限的开关：锁住后状态仍是已确认，配送与对账不受影响，但改单要「锁单后改单」权限。
 */
function lockOrder(r: Order) {
  Modal.confirm({
    title: '锁住这张订单？',
    content: '锁单后订单仍是已确认；普通用户不能再改单，需要解锁或持「锁单后改单」权限。建议在发车出库 7 天内完成对账后锁单。',
    okText: '锁单',
    onOk: async () => {
      await orderApi.lock({orderId: r.orderId!, version: r.version!});
      await queryData();
    },
  });
}

function unlockOrder(r: Order) {
  Modal.confirm({
    title: '解锁这张订单？',
    content: '解锁后改单不再需要额外授权；发车出库的订单仍受「发车后改单」权限约束。',
    okText: '解锁',
    onOk: async () => {
      await orderApi.unlock({orderId: r.orderId!, version: r.version!});
      await queryData();
    },
  });
}

function batchDelete() {
  Modal.confirm({
    title: '删除所选草稿？',
    okType: 'danger',
    onOk: async () => {
      await orderApi.batchDelete(
        tableData.value
          .filter((o) => selected.value.includes(o.orderId!))
          .map((o) => ({
            orderId: o.orderId,
            version: o.version,
          }))
      );
      await queryData();
    },
  });
}

onMounted(() => {
  void queryData();
  void loadTags();
});
</script>
