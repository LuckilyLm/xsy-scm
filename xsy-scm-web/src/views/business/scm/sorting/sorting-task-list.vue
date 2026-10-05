<!--
  分拣任务列表 + 详情录入（P1 分拣管理）。

  ## 这一页只生产「实发事实」，不生产订单事实

  分拣量写在 `sorting_task_item` 上，**不回写** `sales_order_item` 的实发量与结算金额，
  **不写**库存余额与流水，也不创建出库单（裁决第 1、3 条）。因此页面上的「计划量」是建单时
  冻结的订单行实发量快照，之后订单侧怎么改都不追溯已生成的任务 —— 这也是详情区必须常驻
  那条提示的原因：操作人做完分拣后如果回订单页看到原值没变，第一反应会是「系统没保存」。

  ## 状态机决定了每个按钮的出现条件

  ```text
  PENDING ──首次录入──→ SORTING ──每行都有结果──→ COMPLETED ──重开──→ SORTING
     │                     │
     └────────取消─────────┴──→ CANCELLED（同事务释放全部明细占用位）
  ```

  - 取消 / 重开的 `reason` 必填：取消会释放占用让订单行能被重新分拣，重开会推翻「已完成」
    这一事实，两者都需要留下判断依据（只进通用操作日志，不另建分拣审计表）。
  - 已完成只能走重开、不能直接取消，所以两个按钮的 `v-if` 条件不同，不是漏写。
  - `scm:sorting:task:assign` 隐含跨指派人可见：持者才看得到未指派队列与按人筛选，
    分拣员只看到派给自己的任务。

  ## 编辑权判定在服务端，前端只决定输入框长不长出来

  录入要求「调用者是受指派人本人 + 任务还在干活 + 持明细编辑权」。三者缺一就渲染成只读文本，
  而不是留一个提交后必然被 30005 拒掉的输入框。判定读的是 user store 里的 `employeeId`
  与详情返回的 `assigneeEmployeeId`，服务端仍是权威。
-->
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
    <div class="smart-table-btn-block">
      <a-button type="primary" v-privilege="'scm:sorting:task:add'" @click="openCreate">新建分拣任务</a-button>
      <a-typography-text type="secondary" class="toolbar-hint">
        一个订单行同一时刻只属于一个活动任务；建单时按仓库与授权范围取候选订单行。
      </a-typography-text>
    </div>
    <div class="smart-table-setting-block">
      <TableOperator
          v-model="columns"
          :table-id="TABLE_ID_CONST.BUSINESS.SCM_SORTING_TASK"
          :refresh="queryData"
      />
    </div>

    <a-table
        :id="SCM_SORTING_TABLE_ID.TASK"
        size="small"
        :data-source="rows"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText }"
        :scroll="{ x: 1520 }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="SCM_SORTING_TASK_STATUS_COLOR[record.status as SortingTaskStatus]">
            {{ statusDesc(record.status) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'assigneeName'">
          {{ record.assigneeName || '未指派' }}
        </template>
        <template v-else-if="column.dataIndex === 'processedCount'">
          <span class="num">{{ record.processedCount }} / {{ record.itemCount }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'printCount'">
          <span class="num">{{ record.printCount ? record.printCount : '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ datetime(record.createdAt) }}</template>
        <template v-else-if="column.dataIndex === 'deliveryTimeSnapshot'">
          {{ record.deliveryTimeSnapshot ? datetime(record.deliveryTimeSnapshot) : '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'deliveryWave'">{{ record.deliveryWave || '—' }}</template>
        <template v-else-if="column.dataIndex === 'supplierNameSnapshot'">
          {{ record.supplierNameSnapshot || '—' }}
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record.id)">详情</a-button>
            <a-button
                type="link"
                size="small"
                v-privilege="'scm:sorting:scale:query'"
                @click="openScale(record)"
            >秤读数</a-button>
            <a-button v-if="SCM_SORTING_PRINTABLE_STATUS.includes(record.status)" type="link" size="small"
                      v-privilege="'scm:sorting:task:query'" @click="openTicket(record.id)">小票</a-button>
            <a-button
                v-if="SCM_SORTING_PRINTABLE_STATUS.includes(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:print'"
                @click="openPrint(record)"
            >打印
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:assign'"
                @click="openAction('assign', record)"
            >{{ record.assigneeEmployeeId == null ? '指派' : '改派' }}
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:complete'"
                @click="confirmComplete(record)"
            >完成
            </a-button>
            <a-button
                v-if="isWorking(record.status)"
                type="link"
                size="small"
                danger
                v-privilege="'scm:sorting:task:cancel'"
                @click="openAction('cancel', record)"
            >取消
            </a-button>
            <a-button
                v-if="record.status === 'COMPLETED'"
                type="link"
                size="small"
                v-privilege="'scm:sorting:task:reopen'"
                @click="openAction('reopen', record)"
            >重开
            </a-button>
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
          show-size-changer
          show-quick-jumper
          :page-size-options="['10', '20', '50', '100']"
          v-model:current="queryForm.pageNum"
          v-model:page-size="queryForm.pageSize"
          :total="total"
          @change="queryData"
          :show-total="(n: number) => `共 ${n} 个分拣任务`"
      />
    </div>
  </a-card>

  <!-- 新建任务：仓库必须显式选择（不从订单 / 客户 / 线路猜），候选订单行由服务端按建单权开放 -->
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

  <!-- 打印：预览走只读 GET，「登记打印」才是计次的 POST 命令；两者绝不混用同一个入口 -->
  <PrintDocumentModal :open="ticketOpen" document-type="SORTING_TICKET"
                      :business-ids="ticketTaskId === undefined ? [] : [ticketTaskId]" @close="ticketOpen = false"/>
  <SortingScaleDrawer v-model:open="scaleOpen" :task-id="scaleTaskId"/>
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
import PrintDocumentModal from '../print/print-document-modal.vue';
import {computed, onMounted, reactive, ref, watch} from 'vue';
import {useRoute} from 'vue-router';
import {message, Modal, type TableColumnsType} from 'ant-design-vue';
import {useUserStore} from '/@/store/modules/system/user';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import {
    SCM_SORTING_PERMISSION,
    SCM_SORTING_PRINTABLE_STATUS,
    SCM_SORTING_RESULT_ENUM,
    SCM_SORTING_TABLE_ID,
    SCM_SORTING_TASK_STATUS_COLOR,
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
import type {
    Id,
    SortingActionPayload,
    SortingCandidateLine,
    SortingEntryItemPayload,
    SortingEntryPayload,
    SortingLineResult,
    SortingPrint,
    SortingTask,
    SortingTaskDetail,
    SortingTaskItem,
    SortingTaskQuery,
    SortingTaskStatus,
} from './sorting-types';
import {sortingError} from './sorting-types';

type ActionMode = 'assign' | 'cancel' | 'reopen';

// ------------------------------------------------------------------
// 电子秤读数（ADM-11）
// ------------------------------------------------------------------

const deliveryRange = ref<[string, string] | undefined>(undefined);
const scaleOpen = ref(false);
const scaleTaskId = ref<Id | undefined>(undefined);

function openScale(record: SortingTask) {
  scaleTaskId.value = record.id;
  scaleOpen.value = true;
}

/** 一行明细的编辑草稿；空串表示「没填」，与后端 `"0.0000"`（填了且为 0）是两回事。 */
interface EntryDraft {
    sortedQuantity: string;
    result: string;
    reason: string;
}

const userStore = useUserStore();

// ------------------------------------------------------------------ 列表

const queryForm = reactive<SortingTaskQuery>({pageNum: 1, pageSize: 20, unassignedOnly: false});
const assigneeFilter = ref<number | undefined>(undefined);
const rows = ref<SortingTask[]>([]);
const total = ref(0);
const loading = ref(false);
const listError = ref('');
let listGeneration = 0;

/** 持指派权即队列管理者（后端 `SortingAccess.crossAssignee()` 同一口径）。 */
const isQueueManager = computed(() => hasPermission(SCM_SORTING_PERMISSION.TASK_ASSIGN));

// 范围收窄后空表有两种成因（没授权 vs 真没数据），文案必须能区分，否则配置缺口被当成业务空档。
const emptyText = computed(() =>
    isQueueManager.value ? '暂无分拣任务' : '当前仅显示派给您本人的任务；若无数据，可能是任务尚未指派或本仓未授权给您，请联系分拣主管确认。'
);

const columns = ref<TableColumnsType<SortingTask>>([
  {title: '任务号', dataIndex: 'taskNo', width: 180},
  {title: '仓库', dataIndex: 'warehouseNameSnapshot', width: 160},
  {title: '受指派人', dataIndex: 'assigneeName', width: 120},
  {title: '状态', dataIndex: 'status', align: 'center', width: 100},
  {title: '明细行数', dataIndex: 'itemCount', align: 'right', width: 90},
  {title: '已处理', dataIndex: 'processedCount', align: 'right', width: 100},
  {title: '打印次数', dataIndex: 'printCount', align: 'right', width: 90},
  // 三个冻结维度：任务建出来之后它们不再变化，所以直接展示，不做「当前值 vs 快照」的对比
  {title: '送货时间', dataIndex: 'deliveryTimeSnapshot', width: 170},
  {title: '波次', dataIndex: 'deliveryWave', width: 120},
  {title: '供应商来源', dataIndex: 'supplierNameSnapshot', width: 150},
  {title: '创建时间', dataIndex: 'createdAt', width: 170},
  {title: '操作', dataIndex: 'action', fixed: 'right', align: 'right', width: 320},
]);

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

async function queryData() {
    const current = ++listGeneration;
    loading.value = true;
    listError.value = '';
    try {
        const result = await sortingApi.tasks({
            ...queryForm,
            assigneeEmployeeId: assigneeFilter.value,
            // 送货时间按**冻结快照**过滤，半开区间 [from, to)
            deliveryTimeFrom: deliveryRange.value?.[0],
            deliveryTimeTo: deliveryRange.value?.[1],
        });
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

// ------------------------------------------------------------------ 详情与录入

const detailOpen = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const entryError = ref('');
const busy = ref(false);
const detail = ref<SortingTaskDetail>();
const drafts = reactive<Record<string, EntryDraft>>({});
const entryErrors = reactive<Record<string, string>>({});
let detailId: Id | undefined;
let detailGeneration = 0;

/**
 * 当前登录者是否本任务受指派人。
 *
 * `employeeId` 在 store 里是字符串，后端回的是 JSON 数字，因此两边都转成字符串再比 ——
 * 直接 `===` 会让「本人也编辑不了」，而且看起来像权限没配。
 */
const isAssignee = computed(() => {
    const assignee = detail.value?.task.assigneeEmployeeId;
    if (assignee === null || assignee === undefined) return false;
    return String(assignee) === String(userStore.employeeId);
});

const canEditItems = computed(
    () =>
        !!detail.value &&
        isWorking(detail.value.task.status) &&
        isAssignee.value &&
        hasPermission(SCM_SORTING_PERMISSION.ITEM_UPDATE)
);

/** 只读时必须说清为什么只读：三种成因（状态已终结 / 不是派给我的 / 没有编辑权）处置方式完全不同。 */
const readOnlyReason = computed(() => {
    const task = detail.value?.task;
    if (!task) return '';
    if (!isWorking(task.status)) return `任务当前为「${statusDesc(task.status)}」，不再接受录入；已完成的任务需先重开。`;
    if (!isAssignee.value) return '只有受指派人本人可以录入分拣量；需要变更执行人请使用「改派」。';
    return '当前账号没有分拣明细编辑权限（scm:sorting:item:update），只能查看。';
});

/** 满赠赠品行：只读合并进来的第二类来源，缺省按订单行处理（兼容升级前返回的数据）。 */
function isGiftRow(record: SortingTaskItem) {
    return record.sourceType === 'PROMOTION_GIFT';
}

/**
 * 释放占用的历史行不再代表待办量，即使任务还能干活也不能编辑。
 *
 * 赠品行一律不可编辑：分拣只拣货，不写回赠品数量与结果 —— 赠品事实只在冻结权益与出库流水里。
 */
function canEditRow(record: SortingTaskItem) {
    if (isGiftRow(record)) return false;
    return canEditItems.value && record.occupationStatus === 'ACTIVE';
}

function updateDraft(itemId: Id, field: keyof EntryDraft, value: string) {
    const draft = drafts[String(itemId)];
    if (draft) draft[field] = value;
}

function draftKey(record: SortingTaskItem) {
    return String(record.id);
}

function isDirty(record: SortingTaskItem) {
    const draft = drafts[draftKey(record)];
    if (!draft) return false;
    return (
        draft.sortedQuantity !== (record.sortedQuantity ?? '') ||
        draft.result !== (record.result ?? '') ||
        draft.reason.trim() !== (record.reason ?? '').trim()
    );
}

const dirtyCount = computed(() => (detail.value?.items ?? []).filter(isDirty).length);

/** 每次读到详情都按后端返回值重建草稿，避免上一次编辑的残值被当成新的改动提交出去。 */
function resetDrafts(items: SortingTaskItem[]) {
    Object.keys(drafts).forEach((key) => delete drafts[key]);
    Object.keys(entryErrors).forEach((key) => delete entryErrors[key]);
    items.forEach((item) => {
        drafts[String(item.id)] = {
            sortedQuantity: item.sortedQuantity ?? '',
            result: item.result ?? '',
            reason: item.reason ?? '',
        };
    });
}

async function reloadDetail() {
    if (detailId === undefined) return;
    const current = ++detailGeneration;
    detailLoading.value = true;
    detailError.value = '';
    try {
        const result = await sortingApi.detail(detailId);
        if (current === detailGeneration) {
            detail.value = result.data;
            resetDrafts(result.data.items);
            entryError.value = '';
        }
    } catch (e) {
        if (current === detailGeneration) detailError.value = sortingError(e);
    } finally {
        if (current === detailGeneration) detailLoading.value = false;
    }
}

function openDetail(id: Id) {
    detailId = id;
    detail.value = undefined;
    entryError.value = '';
    resetDrafts([]);
    detailOpen.value = true;
    reloadDetail();
}

/**
 * 装配录入载荷：只收改动过的行，每行带自己读到的 `version`。
 *
 * 校验失败时返回 `null` 并把逐行原因落在对应单元格上 —— 不发请求，
 * 因为后端会整批回滚（同一事务），部分发出去只会让人以为改动已保存。
 */
function buildEntryPayload(): SortingEntryPayload | null {
    const items: SortingEntryItemPayload[] = [];
    let invalid = false;
    Object.keys(entryErrors).forEach((key) => delete entryErrors[key]);
    (detail.value?.items ?? []).forEach((record) => {
        if (!canEditRow(record) || !isDirty(record)) return;
        const draft = drafts[draftKey(record)];
        const key = draftKey(record);
        if (!draft.result) {
            entryErrors[key] = '请选择分拣结果';
            invalid = true;
            return;
        }
        const quantity = draft.sortedQuantity.trim();
        if (!quantity) {
            entryErrors[key] = '请填写分拣量；整行缺货也请填 0 并把结果选成「缺货」';
            invalid = true;
            return;
        }
        const reason = draft.reason.trim();
        if (draft.result !== SCM_SORTING_RESULT_ENUM.NORMAL.value && !reason) {
            entryErrors[key] = '非正常结果必须填写原因，这是后续核对唯一的依据';
            invalid = true;
            return;
        }
        items.push({
            id: record.id,
            // 行版本原样回传：后端逐行比对，冲突时整批回滚，不覆盖别人刚录入的量
            version: record.version,
            sortedQuantity: quantity,
            result: draft.result as SortingLineResult,
            reason: reason || undefined,
        });
    });
    if (invalid) {
        entryError.value = '有明细行未通过校验，本次未提交任何改动。';
        return null;
    }
    if (!items.length) {
        entryError.value = '没有需要提交的改动。';
        return null;
    }
    entryError.value = '';
    return {items};
}

async function submitEntry() {
    if (!detail.value) return;
    const payload = buildEntryPayload();
    if (!payload) return;
    busy.value = true;
    try {
        await sortingApi.enter(detail.value.task.id, payload);
        message.success(`分拣录入成功（${payload.items.length} 行）`);
        await reloadDetail();
        queryData();
    } catch (e) {
        entryError.value = sortingError(e);
        // 版本冲突或状态被他人改过：拉最新数据，让操作人重新对着自己看到的那一版录
        await reloadDetail();
    } finally {
        busy.value = false;
    }
}

// ------------------------------------------------------------------ 任务级动作

const actionOpen = ref(false);
const actionMode = ref<ActionMode>('assign');
const actionAssignee = ref<number | undefined>(undefined);
const actionReason = ref('');
const actionError = ref('');
const actionRecord = ref<SortingTask>();

function openAction(mode: ActionMode, record: SortingTask) {
    actionMode.value = mode;
    actionRecord.value = record;
    // `EmployeeSelect` 的 value 声明为 Number，这里只做类型层面的收窄（后端对 Long 主键回的是 JSON 数字）。
    // 不用 Number() 转：id 一旦走数值转换，超长 id 会静默丢精度而页面看起来完全正常。
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
        actionError.value = '请填写原因。';
        return;
    }
    if (actionMode.value === 'assign' && actionAssignee.value == null) {
        actionError.value = '请选择受指派人。';
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
        // 后端消息原样显示：30005（无权 / 看不到）与 41121（状态不允许）是两种处置
        actionError.value = sortingError(e);
    } finally {
        busy.value = false;
    }
}

function confirmComplete(record: SortingTask) {
    Modal.confirm({
        title: `完成分拣任务 ${record.taskNo}？`,
        content: `硬前置是任务内每条活动明细都已有结果（当前已处理 ${record.processedCount} / ${record.itemCount}）。`
            + '完成只锁定任务与实发事实，不扣减库存、不生成出库单，也不回写订单。',
        okText: '确认完成',
        cancelText: '返回',
        onOk: async () => {
            busy.value = true;
            try {
                await sortingApi.complete(record.id, {version: record.version});
                message.success('任务已完成');
                await afterTaskChanged(record.id);
            } catch (e) {
                // 41125（仍有未处理明细）由服务端给出，前端不预先算一遍：
                // 行是否活动、是否算已处理都由服务端按占用位判定，前端再判一次就是第二份真相
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
    if (detailOpen.value && String(detailId) === String(id)) await reloadDetail();
}

// ------------------------------------------------------------------ 新建任务

const createOpen = ref(false);
const creating = ref(false);
const createLoading = ref(false);
const createError = ref('');
// `EmployeeSelect` 的 value prop 声明为 `[Number, Array]`，所以这里不收 `null`：
// 「不指派」用 undefined 表达，提交时再落成后端要的 `null`（载荷类型见 sorting-types）。
const createForm = reactive<{ warehouseId?: Id; assigneeEmployeeId?: number; remark?: string }>({});
const candidateQuery = reactive<{ pageNum: number; pageSize: number; keyword?: string }>({pageNum: 1, pageSize: 20});
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
        createError.value = '请选择分拣仓库。';
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

// ------------------------------------------------------------------ 打印

const printOpen = ref(false);
const ticketOpen = ref(false);
const ticketTaskId = ref<Id>();
const printLoading = ref(false);
const printing = ref(false);
const printError = ref('');
const print = ref<SortingPrint>();
/**
 * 登记命令要的 `version` 不在预览载荷里（预览 VO 刻意不带任务版本，防止被误当成可提交版本），
 * 因此打开打印时把当时读到的任务版本记下来 —— 与录入同理，登记的应当是自己看到的那一版。
 */
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
        if (detailOpen.value && String(detailId) === String(taskId)) await reloadDetail();
    } catch (e) {
        // 计次不 bump 任务版本，所以版本冲突只可能是任务真被改过：刷新后重新登记
        printError.value = sortingError(e);
        await queryData();
        if (detailOpen.value && String(detailId) === String(taskId)) await reloadDetail();
    } finally {
        printing.value = false;
    }
}

const route = useRoute();
const taskRouteName = route.name;
watch([() => route.name, () => route.query.taskId], ([name, id]) => {
    if (name === taskRouteName && typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id)) openDetail(id);
}, {immediate: true});

onMounted(queryData);
</script>

<style scoped>
.num {  font-variant-numeric: tabular-nums;
}

.toolbar-hint {
  margin-left: 12px;
}

</style>
