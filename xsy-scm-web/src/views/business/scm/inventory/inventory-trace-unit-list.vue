<!--
  包装码（一物一码）。

  一个包装码只属于一个批次，码由系统发号、不透明、永不复用；页面只提供发码与回放，不提供改码或删码。
  发码总量受批次余额约束：包装码描述已经存在的实物，不能在库存之外凭空造出实物身份。
-->
<template>
  <a-form class="scm-filter-bar" layout="inline" @submit.prevent="onSearch">
    <div class="scm-filter-fields">
      <a-form-item label="批次号">
        <a-input v-model:value="queryForm.lotNo" placeholder="批次号" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="包装码">
        <a-input v-model:value="queryForm.unitCode" placeholder="包装码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="queryForm.status" :options="STATUS_OPTIONS" allow-clear placeholder="全部" style="width: 140px"/>
      </a-form-item>
    </div>
    <div class="scm-filter-actions">
      <a-button type="primary" @click="onSearch" v-privilege="'scm:inventory:unit:query'">查询</a-button>
      <a-button @click="resetQuery">重置</a-button>
    </div>
  </a-form>

  <a-alert v-if="error" :message="error" type="error" show-icon>
    <template #action>
      <a-button @click="queryData">重试</a-button>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block scm-table-toolbar">
      <div class="smart-table-operate-block">
        <a-button type="primary" @click="openIssue" v-privilege="'scm:inventory:unit:issue'">按批次发码</a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_TRACE_UNIT"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.TRACE_UNIT"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无包装码' }"
        :scroll="{ x: scmColumnsWidth(columns) }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'unitCode'">
          <span class="scm-mono">{{ record.unitCode }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lotNo'">
          <span class="scm-mono">{{ record.lotNo || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'quantity'">
          <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'packageType'">
          {{ PACKAGE_TYPE_ENUM[record.packageType]?.desc || record.packageType }}
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="record.status === 'ACTIVE' ? 'success' : 'neutral'"
                        :label="record.status === 'ACTIVE' ? '有效' : '已作废'"/>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button type="link" size="small" @click="openReplay(record)">回放</a-button>
            <a-button type="link" size="small" @click="openBind(record)" v-privilege="'scm:inventory:unit:bind'">绑定分拣</a-button>
            <ScmActionMore :actions="moreActions(record)" @select="onMore(record, $event)"/>
          </a-space>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
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

  <a-modal v-model:open="issueOpen" title="按批次发码" :confirm-loading="issuing" ok-text="发码" @ok="submitIssue">
    <a-alert v-if="issueError" class="form-error" type="error" show-icon :message="issueError"/>
    <a-form layout="vertical">
      <a-form-item label="批次号" required>
        <a-input v-model:value="issueForm.lotNo" placeholder="输入批次号后确认可发数量" @blur="loadLot"/>
      </a-form-item>
      <a-form-item label="包装类型" required>
        <a-select v-model:value="issueForm.packageType" :options="PACKAGE_TYPE_OPTIONS" style="width: 160px"/>
      </a-form-item>
      <a-form-item label="发码数量" required>
        <a-input-number v-model:value="issueForm.packageCount" :min="1" :max="500" :precision="0"
                        aria-label="发码数量" style="width: 160px"/>
      </a-form-item>
      <a-form-item label="每包数量" required>
        <a-input-number v-model:value="issueForm.quantityPerPackage" string-mode :min="'0.0001'" :precision="4"
                        aria-label="每包数量" style="width: 160px"/>
      </a-form-item>
      <a-form-item label="备注">
        <a-input v-model:value="issueForm.remark" maxlength="200"/>
      </a-form-item>
    </a-form>
    <p v-if="lotHint" class="scm-note">{{ lotHint }}</p>
  </a-modal>

  <a-modal v-model:open="bindOpen" title="绑定分拣任务行" :confirm-loading="binding" ok-text="绑定" @ok="submitBind">
    <a-alert v-if="bindError" class="form-error" type="error" show-icon :message="bindError"/>
    <a-form layout="vertical">
      <a-form-item label="包装码" required>
        <a-input v-model:value="bindForm.unitCode" placeholder="扫码或输入包装码" aria-label="包装码"/>
      </a-form-item>
      <a-form-item label="分拣任务号" required>
        <a-input-search v-model:value="bindForm.taskNo" placeholder="输入分拣任务号后查询" enter-button="查询任务"
                        aria-label="分拣任务号" @search="loadTaskItems"/>
      </a-form-item>
      <a-form-item label="任务行" required>
        <a-select v-model:value="bindForm.sortingTaskItemId" :options="taskItemOptions" :loading="taskLoading"
                  placeholder="选择要发给的客户与商品" style="width: 100%"/>
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="voidOpen" title="作废包装码" :confirm-loading="voidSaving" ok-text="作废"
           ok-type="danger" @ok="submitVoid">
    <a-alert v-if="voidError" class="form-error" type="error" show-icon :message="voidError"/>
    <p class="scm-note">作废只把码标记为已作废并保留原码，不能复用到别的实物；已绑定分拣行的码不能作废。</p>
    <a-form layout="vertical">
      <a-form-item label="包装码">
        <a-input :value="voidRow?.unitCode" disabled/>
      </a-form-item>
      <a-form-item label="作废原因" required>
        <a-textarea v-model:value="voidReason" :maxlength="200" :rows="2" aria-label="作废原因"/>
      </a-form-item>
    </a-form>
  </a-modal>

  <PrintDocumentModal
      :open="printOpen"
      document-type="TRACE_UNIT"
      :business-ids="printBusinessIds"
      @close="printOpen = false"
      @printed="printOpen = false"
  />

  <a-drawer :open="replayOpen" title="包装码回放" :width="scmDrawerWidth('m')" @close="replayOpen = false">
    <a-spin :spinning="replayLoading">
      <a-alert v-if="replayError" :message="replayError" type="error" show-icon/>
      <template v-if="replay">
        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="包装码">{{ replay.unit.unitCode }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ replay.unit.status === 'ACTIVE' ? '有效' : '已作废' }}</a-descriptions-item>
          <a-descriptions-item label="批次号">{{ replay.lot?.lotNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来源生产批号">{{ replay.lot?.sourceLotNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="商品规格">{{ replay.lot?.skuName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="包装数量">{{ quantityText(replay.unit.quantity) }} {{ replay.unit.unit }}</a-descriptions-item>
        </a-descriptions>

        <h4 class="replay-title">收货来源</h4>
        <a-descriptions v-if="replay.receipt" bordered size="small" :column="2">
          <a-descriptions-item label="收货单号">{{ replay.receipt.receiptNo }}</a-descriptions-item>
          <a-descriptions-item label="供应商">{{ replay.receipt.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="确认时间" :span="2">{{ replay.receipt.confirmedAt || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-empty v-else description="该批次还没有收货记录"/>

        <h4 class="replay-title">分拣去向</h4>
        <a-descriptions v-if="replay.binding" bordered size="small" :column="2">
          <a-descriptions-item label="客户">{{ replay.binding.customerName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="订单号">{{ replay.binding.orderNo || '—' }}</a-descriptions-item>
          <a-descriptions-item label="绑定时间" :span="2">{{ replay.binding.boundAt || '—' }}</a-descriptions-item>
        </a-descriptions>
        <a-empty v-else description="该包装码还没有绑定分拣任务行"/>

        <h4 class="replay-title">出库去向</h4>
        <a-table
            v-if="replay.outbounds?.length"
            size="small"
            bordered
            :data-source="replay.outbounds"
            :columns="outboundColumns"
            :pagination="false"
            row-key="outboundNo"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'quantity'">
              <span class="scm-quantity">{{ quantityText(record.quantity) }}</span>
            </template>
          </template>
        </a-table>
        <a-empty v-else description="该批次还没有出库记录"/>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {inventoryTraceUnitApi} from '/@/api/business/scm/inventory-trace-unit-api';
import {inventoryLotApi} from '/@/api/business/scm/inventory-lot-api';
import {sortingApi} from '/@/api/business/scm/sorting-api';
import PrintDocumentModal from '/@/views/business/scm/print/print-document-modal.vue';
import ScmActionMore from '/@/components/business/scm/scm-action-more/index.vue';
import {hasPermission} from '../common/scm-permission';
import type {ScmActionItem} from '/@/components/business/scm/scm-action-more/action-item';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {quantityText} from './inventory-model';
import {inventoryError} from './inventory-errors';
import type {TraceUnit, TraceUnitQuery, TraceUnitReplay} from './inventory-types';

const PACKAGE_TYPE_ENUM: Record<string, {value: string; desc: string}> = {
  BAG: {value: 'BAG', desc: '袋'},
  BOX: {value: 'BOX', desc: '箱'},
  CRATE: {value: 'CRATE', desc: '筐'},
};
const PACKAGE_TYPE_OPTIONS = Object.values(PACKAGE_TYPE_ENUM).map((item) => ({value: item.value, label: item.desc}));
const STATUS_OPTIONS = [
  {value: 'ACTIVE', label: '有效'},
  {value: 'VOID', label: '已作废'},
];

const queryForm = reactive<TraceUnitQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<TraceUnit[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');

type TraceUnitColumn = TableColumnsType<TraceUnit>[number] & {showFlag?: boolean};
const columns = ref<TraceUnitColumn[]>([
  {title: '包装码', dataIndex: 'unitCode', width: 150},
  {title: '批次号', dataIndex: 'lotNo', width: 190, ellipsis: true},
  {title: '来源生产批号', dataIndex: 'sourceLotNo', width: 160, ellipsis: true},
  {title: '包装类型', dataIndex: 'packageType', width: 100, align: 'center'},
  {title: '数量', dataIndex: 'quantity', width: 110},
  {title: '单位', dataIndex: 'unit', width: 80},
  {title: '状态', dataIndex: 'status', width: 90, align: 'center'},
  {title: '创建时间', dataIndex: 'createdAt', width: 170},
  {title: '操作', dataIndex: 'action', width: 180, align: 'center'},
]);

const issueOpen = ref(false);
const issuing = ref(false);
const issueError = ref('');
const lotHint = ref('');
const issueForm = reactive({lotNo: '', lotId: undefined as string | number | undefined, packageType: 'BAG', packageCount: 1, quantityPerPackage: '0.0000', remark: ''});

const voidOpen = ref(false);
const voidSaving = ref(false);
const voidError = ref('');
const voidReason = ref('');
const voidRow = ref<TraceUnit>();

const printOpen = ref(false);
const printBusinessIds = ref<(string | number)[]>([]);

const bindOpen = ref(false);
const binding = ref(false);
const bindError = ref('');
const taskLoading = ref(false);
const taskItemOptions = ref<{value: string | number; label: string}[]>([]);
const bindForm = reactive({unitCode: '', taskNo: '', sortingTaskItemId: undefined as string | number | undefined});

const replayOpen = ref(false);
const replayLoading = ref(false);
const replayError = ref('');
const replay = ref<TraceUnitReplay>();
const outboundColumns: TableColumnsType = [
  {title: '出库单号', dataIndex: 'outboundNo', width: 190},
  {title: '发生时间', dataIndex: 'occurredAt', width: 170},
  {title: '数量', dataIndex: 'quantity', width: 110},
];

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    const res = await inventoryTraceUnitApi.query(queryForm, {suppressGlobalErrorMessage: true});
    tableData.value = res.data?.list ?? [];
    total.value = Number(res.data?.total ?? 0);
  } catch (e) {
    // 加载失败会渲染空表：页面上必须说清楚并给重试
    error.value = inventoryError(e);
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  queryForm.pageNum = 1;
  void queryData();
}

function resetQuery() {
  queryForm.lotNo = undefined;
  queryForm.unitCode = undefined;
  queryForm.status = undefined;
  onSearch();
}

function openIssue() {
  issueOpen.value = true;
  issueError.value = '';
  lotHint.value = '';
  issueForm.lotNo = '';
  issueForm.lotId = undefined;
  issueForm.packageType = 'BAG';
  issueForm.packageCount = 1;
  issueForm.quantityPerPackage = '0.0000';
  issueForm.remark = '';
}

/** 按批次号找到批次与其可发数量；找不到就让「发码」按钮保持不可用。 */
async function loadLot() {
  lotHint.value = '';
  issueForm.lotId = undefined;
  if (!issueForm.lotNo.trim()) {
    return;
  }
  try {
    const res = await inventoryLotApi.queryBalance(
        {pageNum: 1, pageSize: 1, lotNo: issueForm.lotNo.trim()},
        {suppressGlobalErrorMessage: true});
    const row = res.data?.list?.[0];
    if (!row) {
      lotHint.value = '没有找到该批次，或该批次已无库存';
      return;
    }
    issueForm.lotId = row.lotId;
    lotHint.value = `可发数量：${quantityText(row.quantity)} ${row.unit}`;
  } catch (e) {
    lotHint.value = inventoryError(e);
  }
}

async function submitIssue() {
  issueError.value = '';
  if (issueForm.lotId === undefined) {
    issueError.value = '请先填写有效的批次号';
    return;
  }
  issuing.value = true;
  try {
    await inventoryTraceUnitApi.issue({
      lotId: issueForm.lotId,
      packageType: issueForm.packageType,
      packageCount: Number(issueForm.packageCount),
      quantityPerPackage: issueForm.quantityPerPackage,
      remark: issueForm.remark || null,
    });
    issueOpen.value = false;
    await queryData();
  } catch (e) {
    // 发码失败只走全局 toast；本地只保留「批次无效」这类前置校验
    void e;
  } finally {
    issuing.value = false;
  }
}

/** 打印与作废属低频/危险动作，收进「更多」，把常驻宽度留给回放与绑定。 */
function moreActions(record: TraceUnit): ScmActionItem[] {
  return [
    {key: 'print', label: '打印标签', hidden: !hasPermission('scm:inventory:unit:query')},
    {key: 'void', label: '作废', danger: true,
      hidden: !hasPermission('scm:inventory:unit:void') || record.status !== 'ACTIVE'},
  ];
}

function onMore(record: TraceUnit, key: string) {
  if (key === 'print') {
    openPrint(record);
    return;
  }
  if (key === 'void') {
    openVoid(record);
  }
}

function openVoid(record: TraceUnit) {
  voidOpen.value = true;
  voidError.value = '';
  voidReason.value = '';
  voidRow.value = record;
}

async function submitVoid() {
  voidError.value = '';
  if (!voidReason.value.trim()) {
    voidError.value = '请填写作废原因';
    return;
  }
  voidSaving.value = true;
  try {
    await inventoryTraceUnitApi.voidUnit({unitCode: String(voidRow.value?.unitCode), reason: voidReason.value.trim()});
    voidOpen.value = false;
    await queryData();
  } catch {
    // 作废失败只走全局 toast；本地只保留原因必填这类前置校验
  } finally {
    voidSaving.value = false;
  }
}

function openPrint(record: TraceUnit) {
  printBusinessIds.value = [record.id];
  printOpen.value = true;
}

function openBind(record: TraceUnit) {
  bindOpen.value = true;
  bindError.value = '';
  bindForm.unitCode = record.unitCode;
  bindForm.taskNo = '';
  bindForm.sortingTaskItemId = undefined;
  taskItemOptions.value = [];
}

/** 按任务号取任务明细，把「订单号 · 客户 · 商品」摊成可选项。 */
async function loadTaskItems() {
  bindError.value = '';
  taskItemOptions.value = [];
  bindForm.sortingTaskItemId = undefined;
  if (!bindForm.taskNo.trim()) {
    return;
  }
  taskLoading.value = true;
  try {
    const page = await sortingApi.tasks({pageNum: 1, pageSize: 5, keyword: bindForm.taskNo.trim()});
    const task = page.data?.list?.[0];
    if (!task) {
      bindError.value = '没有找到该分拣任务';
      return;
    }
    const detail = await sortingApi.detail(task.id);
    taskItemOptions.value = (detail.data?.items ?? []).map((item) => ({
      value: item.id,
      label: `${item.orderNoSnapshot || '—'} · ${item.customerNameSnapshot || '—'} · ${item.productNameSnapshot || ''}`,
    }));
  } catch (e) {
    bindError.value = inventoryError(e);
  } finally {
    taskLoading.value = false;
  }
}

async function submitBind() {
  bindError.value = '';
  if (!bindForm.unitCode.trim() || bindForm.sortingTaskItemId === undefined) {
    bindError.value = '请填写包装码并选择分拣任务行';
    return;
  }
  binding.value = true;
  try {
    await inventoryTraceUnitApi.bind({
      unitCode: bindForm.unitCode.trim(),
      sortingTaskItemId: bindForm.sortingTaskItemId,
    });
    bindOpen.value = false;
    await queryData();
  } catch {
    // 绑定失败只走全局 toast；本地只保留前置校验提示
  } finally {
    binding.value = false;
  }
}

async function openReplay(record: TraceUnit) {
  replayOpen.value = true;
  replay.value = undefined;
  replayError.value = '';
  replayLoading.value = true;
  try {
    const res = await inventoryTraceUnitApi.replay(record.unitCode);
    replay.value = res.data;
  } catch (e) {
    replayError.value = inventoryError(e);
  } finally {
    replayLoading.value = false;
  }
}

onMounted(queryData);
</script>

<style scoped>
.replay-title {
  margin: 16px 0 8px;
}
</style>
