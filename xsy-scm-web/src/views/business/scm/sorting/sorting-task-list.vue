<!-- 分拣列表用于记录已分拣数量，计划量保留任务创建时的数据；取消和重开必须填写原因。 -->
<template>
  <SortingTaskFilterForm
    :keyword="queryForm.keyword"
    :status="queryForm.status"
    :warehouse-id="queryForm.warehouseId"
    :unassigned-only="queryForm.unassignedOnly"
    :assignee-id="assigneeFilter"
    :delivery-range="deliveryRange"
    :delivery-wave="queryForm.deliveryWave"
    :supplier-id="queryForm.supplierId"
    :is-queue-manager="isQueueManager"
    @update:keyword="queryForm.keyword = $event"
    @update:status="queryForm.status = $event"
    @update:warehouse-id="queryForm.warehouseId = $event"
    @update:unassigned-only="queryForm.unassignedOnly = $event"
    @update:assignee-id="assigneeFilter = $event"
    @update:delivery-range="deliveryRange = $event"
    @update:delivery-wave="queryForm.deliveryWave = $event"
    @update:supplier-id="queryForm.supplierId = $event"
    @search="onSearch"
    @reset="resetQuery"
  />

  <a-alert v-if="listError" :message="listError" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <div class="smart-table-btn-block scm-table-toolbar">
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="'scm:sorting:task:add'" @click="openCreate">新建分拣任务</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_SORTING_TASK" :refresh="queryData" />
      </div>
    </div>

    <a-table
      :id="SCM_SORTING_TABLE_ID.TASK"
      ref="tableRef"
      size="small"
      :data-source="rows"
      :columns="displayColumns"
      row-key="id"
      bordered
      :loading="loading"
      :pagination="false"
      :locale="{emptyText}"
      :scroll="{x: scrollX}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="statusTone(record.status)" :label="statusDesc(record.status)" />
        </template>
        <template v-else-if="column.dataIndex === 'assigneeName'">
          {{ record.assigneeName || '未指派' }}
        </template>
        <template v-else-if="column.dataIndex === 'processedCount'">
          <!-- 已处理行数 / 总行数同格：两列分开读起来要来回对照 -->
          <span class="scm-quantity">{{ record.processedCount }} / {{ record.itemCount }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'deliveryTimeSnapshot'">
          {{ record.deliveryTimeSnapshot ? datetime(record.deliveryTimeSnapshot) : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'deliveryWave'">{{ record.deliveryWave || '—' }}</template>
        <template v-else-if="column.dataIndex === 'supplierNameSnapshot'">
          {{ record.supplierNameSnapshot || '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 行内只放详情和一个当前下一步（未指派→指派，已指派→完成），其余收进「更多」 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openDetail(record.id)">详情</a-button>
            <a-button
              v-if="canAssignNext(record)"
              type="link"
              size="small"
              v-privilege="'scm:sorting:task:assign'"
              @click="openAction('assign', record)"
              >指派
            </a-button>
            <a-button
              v-else-if="canCompleteNext(record)"
              type="link"
              size="small"
              v-privilege="'scm:sorting:task:complete'"
              @click="confirmComplete(record)"
              >完成
            </a-button>
            <ScmActionMore :actions="rowActions(record)" @select="onRowAction($event, record)" />
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        show-quick-jumper
        :page-size-options="SCM_PAGE_SIZE_OPTIONS_WIDE"
        v-model:current="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        @change="queryData"
        :show-total="(n: number) => `共 ${n} 个分拣任务`"
      />
    </div>
  </a-card>

  <!-- 新建任务：仓库必须显式选择，候选订单行由服务端按权限返回 -->
  <SortingTaskCreateModal
    v-model:open="createOpen"
    v-model:warehouse-id="createForm.warehouseId"
    v-model:assignee-employee-id="createForm.assigneeEmployeeId"
    v-model:remark="createForm.remark"
    v-model:keyword="candidateQuery.keyword"
    :creating="creating"
    :loading="createLoading"
    :error="createError"
    :rows="candidateRows"
    :total="candidateTotal"
    v-model:selected-ids="candidateSelected"
    :page-num="candidateQuery.pageNum"
    :page-size="candidateQuery.pageSize"
    @search="searchCandidates"
    @reset="resetCandidates"
    @page-change="loadCandidatesPage"
    @create="submitCreate"
  />

  <!-- 详情：头部 + 明细录入 -->
  <SortingTaskDetailDrawer
    v-model:open="detailOpen"
    :detail="detail"
    :detail-loading="detailLoading"
    :detail-error="detailError"
    :entry-error="entryError"
    :busy="busy"
    :can-edit-items="canEditItems"
    :read-only-reason="readOnlyReason"
    :drafts="drafts"
    :entry-errors="entryErrors"
    :dirty-count="dirtyCount"
    :status-desc="statusDesc"
    :is-working="isWorking"
    :product-type-desc="productTypeDesc"
    :result-desc="resultDesc"
    :is-gift-row="isGiftRow"
    :can-edit-row="canEditRow"
    @reload="reloadDetail"
    @open-ticket="openTicket"
    @open-print="openPrint"
    @action="openAction"
    @complete="confirmComplete"
    @draft-change="updateDraft"
    @submit-entry="submitEntry"
    @standard-saved="
      () => {
        reloadDetail();
        queryData();
      }
    "
  />

  <SortingTaskActionModal
    v-model:open="actionOpen"
    v-model:assignee="actionAssignee"
    v-model:reason="actionReason"
    :mode="actionMode"
    :busy="busy"
    :error="actionError"
    @submit="submitAction"
  />

  <!-- 预览用 GET，登记打印才用 POST 计次，两个入口分开 -->
  <PrintDocumentModal
    :open="ticketOpen"
    document-type="SORTING_TICKET"
    :business-ids="ticketTaskId === undefined ? [] : [ticketTaskId]"
    @close="ticketOpen = false"
  />
  <SortingScaleDrawer v-model:open="scaleOpen" :task-id="scaleTaskId" @updated="queryData" />
  <SortingPrintPreviewModal
    v-model:open="printOpen"
    :print-task-id="printTaskId"
    :print-version="printVersion"
    :print-loading="printLoading"
    :printing="printing"
    :print-error="printError"
    :print="print"
    :status-desc="statusDesc"
    :result-desc="resultDesc"
    :is-gift-row="isGiftRow"
    @open-ticket="openTicket"
    @reload="loadPrintPreview"
    @record="recordPrint"
  />
</template>

<script setup lang="ts">
import {useScmTableFill} from '/@/views/business/scm/common/use-scm-table-fill';
import PrintDocumentModal from '../print/print-document-modal.vue';
import {computed, onMounted, reactive, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {
  SCM_SORTING_PERMISSION,
  SCM_SORTING_PRINTABLE_STATUS,
  SCM_SORTING_RESULT_ENUM,
  SCM_SORTING_TABLE_ID,
  SCM_SORTING_TASK_STATUS_ENUM,
  SCM_SORTING_WORKING_STATUS,
  SCM_SORTING_PRODUCT_TYPE_ENUM,
} from '/@/constants/business/scm/sorting-const';
import {hasPermission} from '../common/scm-permission';
import {datetime} from '../common/scm-display';
import SortingScaleDrawer from './components/sorting-scale-drawer.vue';
import SortingPrintPreviewModal from './components/sorting-print-preview-modal.vue';
import SortingTaskDetailDrawer from './components/sorting-task-detail-drawer.vue';
import SortingTaskCreateModal from './components/sorting-task-create-modal.vue';
import SortingTaskActionModal from './components/sorting-task-action-modal.vue';
import SortingTaskFilterForm from './components/sorting-task-filter-form.vue';
import {useSortingTaskEntry} from './use-sorting-task-entry';
import type {Id, SortingActionPayload, SortingCandidateLine, SortingPrint, SortingTask, SortingTaskQuery} from './sorting-types';
import {sortingError} from './sorting-types';
import {SCM_PAGE_SIZE_OPTIONS_WIDE} from '/@/constants/business/scm/pagination-const';

type ActionMode = 'assign' | 'cancel' | 'reopen';

// 电子秤读数
const deliveryRange = ref<[string, string] | undefined>(undefined);
const scaleOpen = ref(false);
const scaleTaskId = ref<Id | undefined>(undefined);

function openScale(record: SortingTask) {
  scaleTaskId.value = record.id;
  scaleOpen.value = true;
}

// 列表
const queryForm = reactive<SortingTaskQuery>({pageNum: 1, pageSize: 20, unassignedOnly: false});
const assigneeFilter = ref<number | undefined>(undefined);
const rows = ref<SortingTask[]>([]);
const total = ref(0);
const loading = ref(false);
const listError = ref('');
let listGeneration = 0;

/** 有指派权就是队列管理者，能看到所有人的任务。 */
const isQueueManager = computed(() => hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN));

// 范围收窄后空表有两种成因（没授权 vs 真没数据），文案必须能区分，否则配置缺口被当成业务空档。
const emptyText = computed(() =>
  isQueueManager.value ? '暂无分拣任务' : '当前仅显示派给您本人的任务；若无数据，可能是任务尚未指派或本仓未授权给您，请联系分拣主管确认。'
);

// 列表只留任务、执行人、进度和送货时间；打印次数和创建时间放在详情里。
const columns = ref<TableColumnsType<SortingTask>>([
  {title: '任务号', dataIndex: 'taskNo', width: 180},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 150},
  {title: '受指派人', dataIndex: 'assigneeName', width: 120},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '分拣进度', dataIndex: 'processedCount', width: 92},
  // 这三个字段建单后不再变化，直接显示
  {title: '送货时间', dataIndex: 'deliveryTimeSnapshot', width: 170},
  {title: '波次', dataIndex: 'deliveryWave', width: 110},
  {title: '供应商来源', dataIndex: 'supplierNameSnapshot', width: 150},
  {title: '操作', dataIndex: 'action', fixed: 'right', align: 'center', width: 150},
]);
const {tableRef, displayColumns, scrollX} = useScmTableFill(columns);

/** 状态视觉：待分拣 = 待处理（橙），分拣中 = 处理中（蓝），已完成 = 绿，已取消 = 灰。 */
const SORTING_STATUS_TONE: Record<string, ScmStatusTone> = {
  PENDING: 'warning',
  SORTING: 'processing',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
};
const statusTone = (status?: string | null): ScmStatusTone => SORTING_STATUS_TONE[status ?? ''] ?? 'neutral';

/** 未指派显示「指派」，已指派显示「完成」，同一时刻只出现一个。 */
const canAssignNext = (row: SortingTask) =>
  isWorking(row.status) && row.assigneeEmployeeId == null && hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN);

const canCompleteNext = (row: SortingTask) =>
  isWorking(row.status) && row.assigneeEmployeeId != null && hasPermission(SCM_SORTING_PERMISSION.TASK_COMPLETE);

/** 「更多」菜单挂不上 v-privilege，改用 hasPermission 判断。 */
function rowActions(row: SortingTask): ScmActionItem[] {
  const working = isWorking(row.status);
  const printable = SCM_SORTING_PRINTABLE_STATUS.includes(row.status);
  return [
    // 未指派时的「指派」已在行内，这里只留低频的改派
    {key: 'assign', label: '改派', hidden: !(working && row.assigneeEmployeeId != null && hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN))},
    {key: 'scale', label: '称重工作台', hidden: !hasPermission(SCM_SORTING_PERMISSION.SCALE_QUERY)},
    {key: 'ticket', label: '小票', hidden: !(printable && hasPermission(SCM_SORTING_PERMISSION.TASK_QUERY))},
    {key: 'print', label: '打印', hidden: !(printable && hasPermission(SCM_SORTING_PERMISSION.TASK_PRINT))},
    // 取消只对进行中的任务，重开只对已完成的任务
    {key: 'cancel', label: '取消单据', danger: true, hidden: !(working && hasPermission(SCM_SORTING_PERMISSION.TASK_CANCEL))},
    {key: 'reopen', label: '重开', hidden: !(row.status === 'COMPLETED' && hasPermission(SCM_SORTING_PERMISSION.TASK_REOPEN))},
  ];
}

function onRowAction(key: string, row: SortingTask) {
  if (key === 'assign') {
    openAction('assign', row);
  } else if (key === 'scale') {
    openScale(row);
  } else if (key === 'ticket') {
    openTicket(row.id);
  } else if (key === 'print') {
    openPrint(row);
  } else if (key === 'cancel' || key === 'reopen') {
    openAction(key, row);
  }
}

function statusDesc(status?: string | null) {
  return status ? SCM_SORTING_TASK_STATUS_ENUM[status]?.desc ?? status : '—';
}

function resultDesc(result?: string | null) {
  return result ? SCM_SORTING_RESULT_ENUM[result]?.desc ?? result : '未录入';
}

function productTypeDesc(type?: string | null) {
  return type ? SCM_SORTING_PRODUCT_TYPE_ENUM[type]?.desc ?? type : '—';
}

function isWorking(status: string) {
  return SCM_SORTING_WORKING_STATUS.includes(status);
}

const busy = ref(false);
const {
  canEditItems,
  canEditRow,
  detail,
  detailError,
  detailId,
  detailLoading,
  detailOpen,
  dirtyCount,
  drafts,
  entryError,
  entryErrors,
  isGiftRow,
  openDetail,
  readOnlyReason,
  reloadDetail,
  submitEntry,
  updateDraft,
} = useSortingTaskEntry({
  busy,
  refreshTaskList: () => {
    void queryData();
  },
  statusDesc,
});

async function queryData() {
  const current = ++listGeneration;
  loading.value = true;
  listError.value = '';
  try {
    const result = await sortingApi.tasks(
      {
        ...queryForm,
        assigneeEmployeeId: assigneeFilter.value,
        // 送货时间按任务保存的时间过滤，半开区间 [from, to)
        deliveryTimeFrom: deliveryRange.value?.[0],
        deliveryTimeTo: deliveryRange.value?.[1],
      },
      {suppressGlobalErrorMessage: true}
    );
    if (current === listGeneration) {
      rows.value = result.data.list;
      total.value = result.data.total;
    }
  } catch (e) {
    if (current === listGeneration) listError.value = sortingError(e);
  } finally {
    if (current === listGeneration) loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  queryData();
}

function resetQuery() {
  queryForm.keyword = undefined;
  queryForm.status = undefined;
  queryForm.warehouseId = undefined;
  queryForm.unassignedOnly = false;
  queryForm.deliveryWave = undefined;
  queryForm.supplierId = undefined;
  deliveryRange.value = undefined;
  assigneeFilter.value = undefined;
  onSearch();
}

// 任务级动作
const actionOpen = ref(false);
const actionMode = ref<ActionMode>('assign');
const actionAssignee = ref<number | undefined>(undefined);
const actionReason = ref('');
const actionError = ref('');
const actionRecord = ref<SortingTask>();

function openAction(mode: ActionMode, record: SortingTask) {
  actionMode.value = mode;
  actionRecord.value = record;
  // 员工选择器只接受 number；不用 Number() 转换，避免超长 id 丢精度
  const employeeId = record.assigneeEmployeeId;
  actionAssignee.value = employeeId === null || employeeId === undefined ? undefined : (employeeId as number);
  actionReason.value = '';
  actionError.value = '';
  actionOpen.value = true;
}

async function submitAction() {
  const record = actionRecord.value;
  if (!record) return;
  actionError.value = '';
  const reason = actionReason.value.trim();
  if (actionMode.value !== 'assign' && !reason) {
    message.warning('请填写原因');
    return;
  }
  if (actionMode.value === 'assign' && actionAssignee.value == null) {
    message.warning('请选择受指派人');
    return;
  }
  busy.value = true;
  try {
    if (actionMode.value === 'assign') {
      await sortingApi.assign(record.id, {
        assigneeEmployeeId: actionAssignee.value as number,
        version: record.version,
        reason: reason || null,
      });
      message.success('指派成功');
    } else {
      const payload: SortingActionPayload = {version: record.version, reason};
      await (actionMode.value === 'cancel' ? sortingApi.cancel(record.id, payload) : sortingApi.reopen(record.id, payload));
      message.success(actionMode.value === 'cancel' ? '取消成功' : '重开成功');
    }
    actionOpen.value = false;
    await afterTaskChanged(record.id);
  } catch (e) {
    // 后端消息原样显示：无权限和状态不允许的处理方式不同
    actionError.value = sortingError(e);
  } finally {
    busy.value = false;
  }
}

function confirmComplete(record: SortingTask) {
  Modal.confirm({
    title: `完成分拣任务 ${record.taskNo}？`,
    content:
      `任务内每条活动明细都要有结果才能完成（当前已处理 ${record.processedCount} / ${record.itemCount}）。` +
      '完成只锁定任务和实发数量，不扣库存、不生成出库单，也不回写订单。',
    okText: '确认完成',
    cancelText: '返回',
    onOk: async () => {
      busy.value = true;
      try {
        await sortingApi.complete(record.id, {version: record.version});
        message.success('任务已完成');
        await afterTaskChanged(record.id);
      } catch (e) {
        // 是否还有未处理明细由服务端判断，前端不重复计算
        message.error(sortingError(e));
        await afterTaskChanged(record.id);
      } finally {
        busy.value = false;
      }
    },
  });
}

/** 动作之后刷新列表；详情抽屉开着时一并刷新，保证版本号与状态是刚读到的那一版。 */
async function afterTaskChanged(id: Id) {
  await queryData();
  if (detailOpen.value && String(detailId.value) === String(id)) await reloadDetail();
}

// 新建任务
const createOpen = ref(false);
const creating = ref(false);
const createLoading = ref(false);
const createError = ref('');
// 员工选择器不收 null，「不指派」用 undefined，提交时再转成 null
const createForm = reactive<{warehouseId?: Id; assigneeEmployeeId?: number; remark?: string}>({});
const candidateQuery = reactive<{pageNum: number; pageSize: number; keyword?: string}>({pageNum: 1, pageSize: 20});
const candidateRows = ref<SortingCandidateLine[]>([]);
const candidateSelected = ref<Id[]>([]);
const candidateTotal = ref(0);
let candidateGeneration = 0;

async function loadCandidates() {
  const current = ++candidateGeneration;
  createLoading.value = true;
  try {
    const result = await sortingApi.candidateLines({...candidateQuery});
    if (current === candidateGeneration) {
      candidateRows.value = result.data.list;
      candidateTotal.value = result.data.total;
    }
  } catch (e) {
    if (current === candidateGeneration) createError.value = sortingError(e);
  } finally {
    if (current === candidateGeneration) createLoading.value = false;
  }
}

function loadCandidatesPage(pageNum: number, pageSize: number) {
  candidateQuery.pageNum = pageNum;
  candidateQuery.pageSize = pageSize;
  void loadCandidates();
}

function searchCandidates() {
  candidateQuery.pageNum = 1;
  // 筛选条件变化后，已隐藏的旧行不能继续计入创建数量。
  candidateSelected.value = [];
  loadCandidates();
}

function resetCandidates() {
  candidateQuery.keyword = undefined;
  candidateSelected.value = [];
  searchCandidates();
}

function openCreate() {
  createError.value = '';
  createForm.warehouseId = queryForm.warehouseId;
  createForm.assigneeEmployeeId = undefined;
  createForm.remark = undefined;
  candidateSelected.value = [];
  candidateRows.value = [];
  candidateTotal.value = 0;
  candidateQuery.keyword = undefined;
  candidateQuery.pageNum = 1;
  createOpen.value = true;
  loadCandidates();
}

async function submitCreate() {
  if (createForm.warehouseId === undefined || createForm.warehouseId === null || createForm.warehouseId === '') {
    message.warning('请选择分拣仓库');
    return;
  }
  if (!candidateSelected.value.length) {
    createError.value = '请至少勾选一条订单行。';
    return;
  }
  creating.value = true;
  createError.value = '';
  try {
    const result = await sortingApi.create({
      warehouseId: createForm.warehouseId,
      assigneeEmployeeId: createForm.assigneeEmployeeId ?? null,
      remark: createForm.remark?.trim() || null,
      // 勾选行即订单行 id；重复提交由幂等键挡住，抢同一行由服务端唯一索引挡住
      salesOrderItemIds: [...candidateSelected.value],
    });
    message.success(`分拣任务创建成功：${result.data.task.taskNo}`);
    createOpen.value = false;
    await queryData();
    openDetail(result.data.task.id);
  } catch (e) {
    createError.value = sortingError(e);
  } finally {
    creating.value = false;
  }
}

// 打印
const printOpen = ref(false);
const ticketOpen = ref(false);
const ticketTaskId = ref<Id>();
const printLoading = ref(false);
const printing = ref(false);
const printError = ref('');
const print = ref<SortingPrint>();
/** 预览数据里没有版本号，打开打印时记下任务版本，登记时使用。 */
const printVersion = ref<number>();
const printTaskId = ref<Id>();

function openTicket(id?: Id) {
  if (id === undefined) return;
  ticketTaskId.value = id;
  ticketOpen.value = true;
}

function openPrint(record: SortingTask) {
  printTaskId.value = record.id;
  printVersion.value = record.version;
  print.value = undefined;
  printError.value = '';
  printOpen.value = true;
  loadPrintPreview();
}

async function loadPrintPreview() {
  if (printTaskId.value === undefined) return;
  printLoading.value = true;
  printError.value = '';
  try {
    // 只读预览：GET，不累加打印次数
    print.value = (await sortingApi.printPreview(printTaskId.value)).data;
  } catch (e) {
    printError.value = sortingError(e);
  } finally {
    printLoading.value = false;
  }
}

async function recordPrint() {
  const taskId = printTaskId.value;
  if (taskId === undefined || printVersion.value == null) return;
  printing.value = true;
  printError.value = '';
  try {
    const result = await sortingApi.print(taskId, {version: printVersion.value});
    message.success(`已登记打印，累计 ${result.data.printCount} 次`);
    print.value = {...print.value, printCount: result.data.printCount} as SortingPrint;
    await queryData();
    if (detailOpen.value && String(detailId.value) === String(taskId)) await reloadDetail();
  } catch (e) {
    // 计次不 bump 任务版本，所以版本冲突只可能是任务真被改过：刷新后重新登记
    printError.value = sortingError(e);
    await queryData();
    if (detailOpen.value && String(detailId.value) === String(taskId)) await reloadDetail();
  } finally {
    printing.value = false;
  }
}

const route = useRoute();
const taskRouteName = route.name;
watch(
  [() => route.name, () => route.query.taskId],
  ([name, id]) => {
    if (name === taskRouteName && typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id)) openDetail(id);
  },
  {immediate: true}
);

onMounted(queryData);
</script>

<style scoped>
.toolbar-hint {
  margin-left: 12px;
}
</style>
