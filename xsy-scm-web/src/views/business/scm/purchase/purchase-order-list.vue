<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="采购单号" class="smart-query-form-item">
        <a-input v-model:value="queryForm.orderNo" placeholder="采购单号" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="供应商" class="smart-query-form-item">
        <SupplierSelect v-model:value="queryForm.supplierId" width="200px" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <SmartEnumSelect enum-name="SCM_PURCHASE_STATUS_ENUM" v-model:value="queryForm.status" width="150px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" @click="onSearch" v-privilege="'scm:purchase:query'">查询</a-button>
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
        <a-button type="primary" v-privilege="'scm:purchase:add'" @click="drawer?.open()">新建采购单</a-button>
        <a-button danger v-privilege="'scm:purchase:delete'" :disabled="!draftSelected.length" @click="batchDelete"> 批量删除草稿 </a-button>
        <a-button danger v-privilege="'scm:purchase:short-close'" :disabled="!partialSelected.length" @click="batchShortClose">
          批量少收关单
        </a-button>
        <a-button :disabled="!selected.length" @click="batchPrint">批量打印</a-button>
        <a-button v-privilege="'scm:purchase:query'" :loading="listExport.exporting" @click="onExport">导出</a-button>
        <a-button v-privilege="'scm:purchase:query'" @click="exportSettingsOpen = true">导出设置</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PURCHASE_ORDER" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      :id="SCM_PURCHASE_TABLE_ID.ORDER"
      size="small"
      :data-source="tableData"
      ref="tableRef"
      :columns="displayColumns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText}"
      :scroll="{x: scrollX}"
      :row-selection="{
        columnWidth: SCM_COLUMN_WIDTH.selection,
        selectedRowKeys: selected,
        onChange: (keys: (string | number)[]) => (selected = keys),
        getCheckboxProps: (row: Order) => ({disabled: !['DRAFT', 'PARTIALLY_RECEIVED'].includes(row.status ?? '')}),
      }"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'orderNo'">
          <a @click="detail?.open(record.id)">{{ record.orderNo }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'purchaserName'">{{ record.purchaserName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-space :size="4" wrap>
            <ScmStatusTag :tone="statusTone(record.status)" :label="SCM_PURCHASE_STATUS_ENUM[record.status]?.desc" />
            <a-tag v-if="record.pendingPriceCount" color="orange">待定价 {{ record.pendingPriceCount }}</a-tag>
            <a-tag v-if="record.lockedFlag" color="red">{{ record.lockReason === 'AUTO_7_DAYS' ? '自动锁单' : '已锁单' }}</a-tag>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'plannedArrivalDate'">{{ dateOnly(record.plannedArrivalDate) }}</template>
        <template v-else-if="column.dataIndex === 'totalAmount'">
          <span class="scm-money">{{ amount(record.totalAmount) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'receivedProgress'">
          <span class="scm-quantity">{{ progress(record.receivedProgress) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 当前状态最常用的两个动作常驻，其他操作收进“更多” -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <template v-if="record.status === 'DRAFT'">
              <a-button type="link" size="small" v-privilege="'scm:purchase:update'" @click="drawer?.open(record.id)"> 编辑 </a-button>
              <a-button type="link" size="small" v-privilege="'scm:purchase:submit'" @click="submit(record)"> 提交 </a-button>
            </template>
            <template v-else>
              <a-button type="link" size="small" @click="detail?.open(record.id)">详情</a-button>
              <a-button type="link" size="small" @click="printRow(record)">打印</a-button>
            </template>
            <ScmActionMore :actions="moreActions(record)" @select="onRowAction($event, record)" />
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

  <PurchaseOrderForm ref="drawer" @saved="queryData" />
  <PurchaseOrderDetail ref="detail" @saved="queryData" />
  <PurchaseShareModal ref="shareModal" />
  <PurchaseFeedbackDrawer ref="feedbackDrawer" @detail="(id) => detail?.open(id)" />
  <PrintDocumentModal :open="printBatch.open" document-type="PURCHASE_ORDER" :business-ids="printBatch.ids" @close="printBatch.open = false" />

  <a-modal
    v-model:open="reassignVisible"
    title="改派采购员"
    :confirm-loading="reassignSaving"
    :ok-button-props="{disabled: reassignSaving}"
    @ok="submitReassign"
  >
    <p>
      采购单：<strong>{{ reassignTarget?.orderNo }}</strong
      >（{{ reassignTarget?.supplierName }}）
    </p>
    <p>当前采购员：{{ reassignTarget?.purchaserName || '未分配' }}</p>
    <a-form layout="vertical">
      <a-form-item>
        <template #label>
          新采购员
          <ScmFieldHelp label="新采购员" text="留空会收回分配；未分配单据仅有分配权或全量权限的人员可见" />
        </template>
        <EmployeeSelect v-model:value="reassignPurchaser" placeholder="留空即收回为未分配" width="100%" />
      </a-form-item>
      <a-form-item label="改派原因">
        <a-textarea v-model:value="reassignReason" :maxlength="500" :rows="2" placeholder="选填，用于审计留痕" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="exportSettingsOpen" title="导出列设置" width="520px" @ok="saveExportSettings">
    <p class="export-hint">不勾选任何列时导出全部列</p>
    <a-checkbox-group v-model:value="exportColumns">
      <a-row>
        <a-col v-for="col in SCM_PURCHASE_EXPORT_COLUMNS" :key="col.key" :span="12">
          <a-checkbox :value="col.key">{{ col.title }}</a-checkbox>
        </a-col>
      </a-row>
    </a-checkbox-group>
  </a-modal>
</template>

<script setup lang="ts">
import {SCM_COLUMN_WIDTH} from '/@/views/business/scm/common/scm-column';
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import {computed, h, onMounted, reactive, ref} from 'vue';
import {Input, message, Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseOrderApi} from '/@/api/business/scm/purchase-order-api';
import SupplierSelect from '/@/components/business/scm/supplier-select/index.vue';
import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import PurchaseShareModal from '/@/views/business/scm/purchase/components/purchase-share-modal.vue';
import PurchaseFeedbackDrawer from '/@/views/business/scm/purchase/components/purchase-feedback-drawer.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import EmployeeSelect from '/@/components/system/employee-select/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import {useUserStore} from '/@/store/modules/system/user';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_PURCHASE_EXPORT_COLUMNS, SCM_PURCHASE_STATUS_ENUM, SCM_PURCHASE_TABLE_ID} from '/@/constants/business/scm/purchase-const';
import type {Order, OrderQuery} from './purchase-types';
import {amount, progress} from './purchase-form-model';
import {hasPermission} from '../common/scm-permission';
import {useScmExport} from '../common/use-scm-export';
import {dateOnly} from '../common/scm-display';
import {purchaseError} from './purchase-errors';
import PrintDocumentModal from '../print/print-document-modal.vue';
import PurchaseOrderForm from './components/purchase-order-form-drawer.vue';
import PurchaseOrderDetail from './components/purchase-order-detail-drawer.vue';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

const queryForm = reactive<OrderQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<Order[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');
const selected = ref<(string | number)[]>([]);
const drawer = ref<InstanceType<typeof PurchaseOrderForm>>();
const detail = ref<InstanceType<typeof PurchaseOrderDetail>>();
const shareModal = ref<InstanceType<typeof PurchaseShareModal>>();
const feedbackDrawer = ref<InstanceType<typeof PurchaseFeedbackDrawer>>();

/** 打印对话框：单张与批量共用，只传要打印的采购单 id（渲染与冻结都在服务端）。 */
const printBatch = reactive({open: false, ids: [] as (string | number)[]});

/** 改派采购归属，需要独立权限，带乐观锁。 */
const reassignVisible = ref(false);
const reassignSaving = ref(false);
const reassignTarget = ref<Order>();
/** EmployeeSelect 的 value 不接受 null（[Number, Array]），用 undefined 桥接「收回为未分配」。 */
const reassignPurchaser = ref<number | undefined>(undefined);
const reassignReason = ref('');

/** 无全量采购范围权限时，空表可能是授权范围所致而非系统无数据。 */
const canSeeAllPurchase = computed(() => hasPermission('scm:purchase:scope:all:query'));
const emptyText = computed(() =>
  canSeeAllPurchase.value ? '暂无数据' : '当前仅显示您授权范围内的采购单；若无数据，可能是尚未分配采购员或授权范围未配置，请联系管理员确认。'
);

function openReassign(row: Order) {
  reassignTarget.value = row;
  reassignPurchaser.value = row.purchaserId == null ? undefined : Number(row.purchaserId);
  reassignReason.value = '';
  reassignVisible.value = true;
}

async function submitReassign() {
  const row = reassignTarget.value;
  if (!row) {
    return;
  }
  reassignSaving.value = true;
  try {
    await purchaseOrderApi.reassign({
      id: row.id!,
      version: row.version!,
      purchaserId: reassignPurchaser.value ?? null,
      reason: reassignReason.value.trim() || null,
    });
    message.success('归属已改派');
    reassignVisible.value = false;
    await queryData();
  } catch {
    // 失败原因由全局提示给出
  } finally {
    reassignSaving.value = false;
  }
}

const userStore = useUserStore();
/** 导出列偏好按「登录用户 + 场景」本地记忆，不建后端表；缺失 / 损坏回落到整目录。 */
const EXPORT_SCENE = 'purchase-order-list';
const ALL_EXPORT_KEYS = SCM_PURCHASE_EXPORT_COLUMNS.map((c) => c.key);
function exportSettingKey() {
  return `xsy-scm:export-columns:${userStore.employeeId}:${EXPORT_SCENE}`;
}
function loadExportColumns(): string[] {
  try {
    const raw = localStorage.getItem(exportSettingKey());
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        const valid = new Set(ALL_EXPORT_KEYS);
        const kept = parsed.filter((k: string) => valid.has(k));
        if (kept.length) {
          return kept;
        }
      }
    }
  } catch {
    // 本地偏好损坏时回落到全部列
  }
  return ALL_EXPORT_KEYS;
}
const exportSettingsOpen = ref(false);
const exportColumns = ref<string[]>(loadExportColumns());
const listExport = useScmExport();
/** 竞态保护：慢的旧请求不得覆盖新结果。 */
let requestId = 0;

/** 行勾选同时服务两类批量：删除只取草稿、少收关单只取部分收货，各自按状态取子集。 */
const selectedOrders = computed(() => tableData.value.filter((o) => selected.value.includes(o.id!)));
const draftSelected = computed(() => selectedOrders.value.filter((o) => o.status === 'DRAFT'));
const partialSelected = computed(() => selectedOrders.value.filter((o) => o.status === 'PARTIALLY_RECEIVED'));

// 创建时间不默认上列；计划到货才是采购跟单关注的日期。
const columns = ref<TableColumnsType<Order>>([
  {title: '采购单号', dataIndex: 'orderNo', width: 160, ellipsis: true},
  {title: '供应商', dataIndex: 'supplierName', width: 180, ellipsis: true},
  {title: '采购员', dataIndex: 'purchaserName', width: 80, ellipsis: true},
  {title: '收货仓库', dataIndex: 'warehouseName', width: 100, ellipsis: true},
  {title: '状态', dataIndex: 'status', align: 'center', width: 130},
  {title: '计划到货', dataIndex: 'plannedArrivalDate', width: 110},
  {title: '采购金额', dataIndex: 'totalAmount', width: 130},
  {title: '收货进度', dataIndex: 'receivedProgress', width: 95},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 170},
]);
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns, () => SCM_COLUMN_WIDTH.selection);

/** 草稿/部分完成=待处理，已提交=处理中，已收货=完成，少收关单/取消=失效。 */
const STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  SUBMITTED: 'processing',
  PARTIALLY_RECEIVED: 'warning',
  RECEIVED: 'success',
  SHORT_CLOSED: 'neutral',
  CANCELLED: 'neutral',
};
const statusTone = (value?: string | null): ScmStatusTone => STATUS_TONE[value ?? ''] ?? 'neutral';

/** 更多菜单无法使用 `v-privilege`，改用 `hasPermission` 判断。 */
const canAssign = computed(() => hasPermission('scm:purchase:assign'));
const canCancel = computed(() => hasPermission('scm:purchase:cancel'));
const canShortClose = computed(() => hasPermission('scm:purchase:short-close'));
const canDelete = computed(() => hasPermission('scm:purchase:delete'));
const canLock = computed(() => hasPermission('scm:purchase:lock'));
const canUnlock = computed(() => hasPermission('scm:purchase:unlock'));
/** 锁单只对已提交、部分收货的单有意义；草稿还没定价，收货完成的单已没有可改的价格窗口。 */
const LOCKABLE_STATUSES = ['SUBMITTED', 'PARTIALLY_RECEIVED'];
/** 只有进入正式采购流程的单才能分享给现场：草稿还没定，取消的没有任务。 */
const SHAREABLE_STATUSES = ['SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED'];
const canShare = computed(() => hasPermission('scm:purchase:share:create'));

/** 当前状态可用的操作；低频动作收进“更多”。 */
function moreActions(row: Order): ScmActionItem[] {
  const draft = row.status === 'DRAFT';
  return [
    {key: 'detail', label: '详情', hidden: !draft},
    {key: 'print', label: '打印', hidden: !draft},
    {key: 'share', label: '分享', hidden: !(canShare.value && SHAREABLE_STATUSES.includes(row.status ?? ''))},
    {key: 'feedback', label: '现场回填', hidden: draft},
    {key: 'reassign', label: '改派', hidden: !canAssign.value},
    {
      key: 'lock',
      label: '锁单',
      hidden: !(canLock.value && !row.lockedFlag && LOCKABLE_STATUSES.includes(row.status ?? '')),
    },
    {key: 'unlock', label: '解锁', hidden: !(canUnlock.value && row.lockedFlag)},
    {key: 'cancel', label: '取消', hidden: !(canCancel.value && ['DRAFT', 'SUBMITTED'].includes(row.status ?? ''))},
    {
      key: 'shortClose',
      label: '少收关单',
      hidden: !(canShortClose.value && row.status === 'PARTIALLY_RECEIVED'),
    },
    {key: 'delete', label: '删除', danger: true, hidden: !(canDelete.value && draft)},
  ];
}

function onRowAction(key: string, row: Order) {
  if (key === 'feedback') {
    if (row.id != null) feedbackDrawer.value?.open(row.id);
  } else if (key === 'share') {
    if (row.id != null) {
      shareModal.value?.openFor(row.id, row.orderNo);
    }
  } else if (key === 'detail') {
    // 详情抽屉需要确定的 id，先判空
    if (row.id != null) {
      detail.value?.open(row.id);
    }
  } else if (key === 'print') {
    printRow(row);
  } else if (key === 'reassign') {
    openReassign(row);
  } else if (key === 'lock') {
    lock(row);
  } else if (key === 'unlock') {
    unlock(row);
  } else if (key === 'cancel') {
    cancel(row);
  } else if (key === 'shortClose') {
    shortClose(row);
  } else if (key === 'delete') {
    remove(row);
  }
}

async function queryData() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 列表加载失败由页头 Alert 承担（带重试），不再让全局 toast 重复说一遍
    const r = await purchaseOrderApi.query(queryForm, {suppressGlobalErrorMessage: true});
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
  queryForm.orderNo = undefined;
  queryForm.supplierId = undefined;
  queryForm.status = undefined;
  onSearch();
}

/** 状态操作成功后重新加载；失败由全局提示给出原因。 */
function submit(row: Order) {
  Modal.confirm({
    title: '提交这张采购单？提交后不可再改行与分配。',
    onOk: async () => {
      await purchaseOrderApi.submit({id: row.id!, version: row.version!});
      await queryData();
    },
  });
}

function cancel(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '取消这张采购单？已占用的采购需求会被释放。',
    content: () =>
      h('div', [
        h('p', '取消原因（必填）：'),
        h(Input.TextArea, {
          rows: 3,
          maxlength: 500,
          'onUpdate:value': (v: string) => (reason = v),
        }),
      ]),
    onOk: async () => {
      if (!reason.trim()) {
        // 确认框遮住页面，缺少原因的提示只能用消息提示
        message.warning('请填写取消原因');
        throw new Error('cancel reason required');
      }
      await purchaseOrderApi.cancel({id: row.id!, version: row.version!, cancelReason: reason});
      await queryData();
    },
  });
}

function shortClose(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '少收关单？剩余未收数量将不再补收，采购单进入终态。',
    content: () =>
      h('div', [
        h('p', '少收原因（必填）：'),
        h(Input.TextArea, {
          rows: 3,
          maxlength: 500,
          'onUpdate:value': (v: string) => (reason = v),
        }),
      ]),
    onOk: async () => {
      if (!reason.trim()) {
        message.warning('请填写少收关单原因');
        throw new Error('short close reason required');
      }
      await purchaseOrderApi.shortClose({id: row.id!, version: row.version!, shortCloseReason: reason});
      await queryData();
    },
  });
}

/** 锁单：原因可选；锁后仍可收货，只挡补价/改价（改价仍可走专门权限）。 */
function lock(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '锁定这张采购单？',
    content: () =>
      h('div', [
        h('p', '锁单用于与供应商对账：锁后不能再补价，改价需要专门权限；收货不受影响。'),
        h('p', '锁单原因（选填）：'),
        h(Input.TextArea, {
          rows: 2,
          maxlength: 500,
          'onUpdate:value': (v: string) => (reason = v),
        }),
      ]),
    onOk: async () => {
      await purchaseOrderApi.lock({id: row.id!, version: row.version!, reason: reason.trim() || null});
      await queryData();
    },
  });
}

/** 解锁必须填原因：把已对账的单重新打开需要解释。 */
function unlock(row: Order) {
  let reason = '';
  Modal.confirm({
    title: '解锁这张采购单？',
    content: () =>
      h('div', [
        h('p', '解锁后可继续补价/改价。'),
        h('p', '解锁原因（必填）：'),
        h(Input.TextArea, {
          rows: 2,
          maxlength: 500,
          'onUpdate:value': (v: string) => (reason = v),
        }),
      ]),
    onOk: async () => {
      if (!reason.trim()) {
        message.warning('请填写解锁原因');
        throw new Error('unlock reason required');
      }
      await purchaseOrderApi.unlock({id: row.id!, version: row.version!, reason: reason.trim()});
      await queryData();
    },
  });
}

function remove(row: Order) {
  Modal.confirm({
    title: '删除这张草稿采购单？',
    okType: 'danger',
    onOk: async () => {
      await purchaseOrderApi.delete({id: row.id!, version: row.version!});
      await queryData();
    },
  });
}

function batchDelete() {
  const targets = draftSelected.value;
  if (!targets.length) {
    message.warning('请勾选至少一张「草稿」采购单');
    return;
  }
  Modal.confirm({
    title: `删除所选 ${targets.length} 张草稿采购单？`,
    okType: 'danger',
    onOk: async () => {
      await purchaseOrderApi.batchDelete(targets.map((o) => ({id: o.id!, version: o.version!})));
      await queryData();
    },
  });
}

/** 批量少收关单只作用于“部分收货”，整批共用一个原因，任一单失败整批回滚。 */
function batchShortClose() {
  const targets = partialSelected.value;
  if (!targets.length) {
    message.warning('请勾选至少一张「部分收货」的采购单');
    return;
  }
  let reason = '';
  Modal.confirm({
    title: `对 ${targets.length} 张「部分收货」采购单批量少收关单？`,
    content: () =>
      h('div', [
        h('p', '整批共用一个原因；任一单状态变化，整批都会失败。'),
        h('p', '少收关单原因（必填）：'),
        h(Input.TextArea, {
          rows: 3,
          maxlength: 500,
          'onUpdate:value': (v: string) => (reason = v),
        }),
      ]),
    okType: 'danger',
    onOk: async () => {
      if (!reason.trim()) {
        message.warning('请填写少收关单原因');
        throw new Error('batch short close reason required');
      }
      await purchaseOrderApi.batchShortClose({
        orders: targets.map((o) => ({id: o.id!, version: o.version!})),
        shortCloseReason: reason,
      });
      await queryData();
    },
  });
}

/** 导出使用当前筛选，只回传勾选列；分页由后端固定。 */
function onExport() {
  const payload = {...queryForm, exportColumns: exportColumns.value};
  void listExport.run(() => purchaseOrderApi.export(payload));
}

function saveExportSettings() {
  localStorage.setItem(exportSettingKey(), JSON.stringify(exportColumns.value));
  exportSettingsOpen.value = false;
  message.success('导出设置已保存');
}

/** 使用打印中心模板，正式打印时服务端会保存模板版本和版面。 */
function printRow(row: Order) {
  if (row.id === undefined || row.id === null) {
    return;
  }
  printBatch.ids = [row.id];
  printBatch.open = true;
}

async function batchPrint() {
  const rows = selectedOrders.value;
  if (!rows.length) {
    message.warning('请先勾选要打印的采购单');
    return;
  }
  printBatch.ids = rows.map((row) => row.id!);
  printBatch.open = true;
}

onMounted(queryData);
</script>

<style scoped>
.export-hint {
  color: var(--scm-text-secondary);
  font-size: 12px;
  margin-bottom: 12px;
}
</style>
