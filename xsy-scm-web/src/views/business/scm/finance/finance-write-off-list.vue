<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="核销日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期" />
      </a-form-item>
      <a-form-item label="资金类型"
        ><a-select
          v-model:value="query.sourceType"
          allow-clear
          placeholder="全部"
          style="width: 140px"
          :options="[
            {label: '收款', value: 'RECEIPT'},
            {label: '付款', value: 'PAYMENT'},
            {label: '余额消费', value: 'BALANCE_MOVEMENT'},
            {label: '抹零', value: 'WAIVER'},
          ]"
      /></a-form-item>
      <a-form-item label="资金单号"><a-input v-model:value="query.sourceNo" allow-clear @press-enter="onSearch" /></a-form-item>
      <a-form-item label="目标单号"><a-input v-model:value="query.targetNo" allow-clear @press-enter="onSearch" /></a-form-item>
      <a-form-item label="方向"
        ><a-select v-model:value="query.entryType" allow-clear :options="entryOptions" placeholder="全部" style="width: 120px"
      /></a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.WRITE_OFF_QUERY" @click="onSearch">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-button-group>
      </a-form-item>
    </a-row>
  </a-form>

  <a-alert v-if="page.error.value" class="page-error" type="error" show-icon :message="page.error.value">
    <template #action><a-button @click="queryData">重试</a-button></template>
  </a-alert>

  <a-card size="small" :bordered="false">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">核销流水</div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.WRITE_OFF_ADD" type="primary" @click="openAdd">登记核销</a-button>
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_WRITE_OFF" :refresh="queryData" />
      </div>
    </a-row>
    <a-table
      id="scm-finance-write-off-table"
      class="finance-table"
      size="small"
      :data-source="page.tableData.value"
      :columns="columns"
      row-key="writeOffId"
      :loading="page.loading.value"
      :pagination="false"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column, text}">
        <template v-if="column.dataIndex === 'sourceName'">{{ record.sourceName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'sourceType'">{{ sourceTypeText(record.sourceType) }}</template>
        <template v-else-if="column.dataIndex === 'sourceNo'">
          <span v-if="record.sourceNo" class="scm-mono">{{ record.sourceNo }}</span>
          <span v-else>—</span>
          <a
            v-if="record.sourceType === 'BALANCE_MOVEMENT'"
            v-privilege="'scm:balance:movement:query'"
            @click="movementDetail?.open({movementId: record.sourceId})"
            >来源流水</a
          >
        </template>
        <template v-else-if="column.dataIndex === 'targetName'">{{ record.targetName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'targetType'">{{ targetTypeText(record.targetType) }}</template>
        <template v-else-if="column.dataIndex === 'targetNo'">
          <span v-if="record.targetNo" class="scm-mono">{{ record.targetNo }}</span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.dataIndex === 'entryType'">
          <ScmStatusTag :color="SCM_FINANCE_ENTRY_COLOR[text]" :label="entryTypeText(text)" />
        </template>
        <template v-else-if="column.dataIndex === 'amount'">
          <span class="scm-money">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'writtenOffAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex === 'operator'">{{ record.operator || '—' }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <!-- 撤销核销是追加反向记录，不是编辑，所以用危险样式并单独确认 -->
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <a-button
              v-if="record.entryType === 'NORMAL' && !record.reversed && record.sourceType !== 'WAIVER'"
              type="link"
              size="small"
              danger
              v-privilege="PERM.WRITE_OFF_REVERSE"
              @click="openReverse(record)"
              >撤销核销</a-button
            >
            <!-- 抹零必须走抹零接口反向：它还要把抹零事实置为已反向，和普通核销反向不能共用一个动作 -->
            <a-button
              v-else-if="record.entryType === 'NORMAL' && !record.reversed && record.sourceType === 'WAIVER'"
              type="link"
              size="small"
              danger
              v-privilege="PERM.WAIVER_REVERSE"
              @click="openWaiverReverse(record)"
              >反向抹零</a-button
            >
            <span v-else class="scm-cell-hint">—</span>
          </a-space>
        </template>
      </template>
    </a-table>
    <div class="smart-query-table-page">
      <a-pagination
        show-size-changer
        :page-size-options="SCM_PAGE_SIZE_OPTIONS"
        show-quick-jumper
        v-model:current="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="page.total.value"
        @change="queryData"
        :show-total="(count: number) => `共${count}条`"
      />
    </div>
  </a-card>

  <a-drawer v-model:open="addOpen" title="登记核销" :width="scmDrawerWidth('l')" :destroy-on-close="true">
    <a-alert v-if="addError" class="form-error" type="error" show-icon :message="addError" />
    <a-form ref="addFormRef" :model="addModel" :rules="addRules" layout="vertical" class="app-drawer-form">
      <a-form-item name="sourceType">
        <template #label>
          资金类型
          <ScmFieldHelp label="资金类型" text="收款核销应收，付款核销应付" />
        </template>
        <a-select v-model:value="sourceType" :options="sourceOptions" placeholder="选择资金类型" @change="resetAllocation" />
      </a-form-item>
      <a-form-item label="资金单" name="source">
        <a-input :value="source?.documentNo || ''" readonly placeholder="选择可核销资金单">
          <template #addonAfter><a-button type="link" :disabled="!sourceType" @click="openSourcePicker">选择</a-button></template>
        </a-input>
      </a-form-item>
    </a-form>
    <a-descriptions v-if="source" class="source-summary" bordered size="small" :column="3">
      <a-descriptions-item label="资金方">{{ source.partyName }}</a-descriptions-item>
      <a-descriptions-item label="方向">{{ entryTypeText(source.entryType) }}</a-descriptions-item>
      <a-descriptions-item label="可核销金额">{{ moneyText(source.availableAmount) }}</a-descriptions-item>
    </a-descriptions>
    <a-radio-group v-if="source" v-model:value="allocMode" button-style="solid" class="alloc-mode" @change="resetPlan">
      <a-radio-button value="AUTO">自动 FIFO</a-radio-button>
      <a-radio-button value="MANUAL">手工选择</a-radio-button>
    </a-radio-group>

    <template v-if="allocMode === 'AUTO'">
      <div class="target-toolbar">
        <h3>自动分配预览</h3>
        <a-button :disabled="!source" :loading="planLoading" @click="previewAuto">预览分配</a-button>
      </div>
      <p class="scm-note">按账单日期由早到晚自动分配，单张单据不会超额核销；单次最多处理 100 张，未分配完的金额保留在收款单的待核销额中。</p>
      <a-empty v-if="!plan.length" description="点击「预览分配」查看将要核销的单据" />
      <a-table
        v-else
        class="plan-table"
        size="small"
        :data-source="plan"
        :columns="planColumns"
        row-key="targetId"
        :pagination="false"
        :scroll="{x: scmColumnsWidth(planColumns)}"
      >
        <template #bodyCell="{record, column}">
          <template v-if="['openAmount', 'allocatedAmount'].includes(column.dataIndex)">
            <span class="scm-money">{{ moneyText(record[column.dataIndex]) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'eventAt'">{{ dateTimeText(record.eventAt) }}</template>
        </template>
      </a-table>
    </template>
    <template v-else>
      <div class="target-toolbar">
        <h3>分配目标</h3>
        <a-button :disabled="!source" @click="openTargetPicker">添加目标单</a-button>
      </div>
      <a-empty v-if="!targets.length" description="先选择一笔资金单，再添加一个或多个目标单" />
      <a-table
        v-else
        size="small"
        :data-source="targets"
        :columns="targetColumns"
        row-key="id"
        :pagination="false"
        :scroll="{x: scmColumnsWidth(targetColumns)}"
      >
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'availableAmount'">
            <span class="scm-money">{{ moneyText(record.availableAmount) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'amount'">
            <a-input-number v-model:value="record.amount" string-mode :min="0" :precision="4" :max="99999999999999" style="width: 150px" />
          </template>
          <template v-else-if="column.dataIndex === 'action'"><a-button type="link" danger @click="removeTarget(record.id)">移除</a-button></template>
        </template>
      </a-table>
    </template>
    <template #footer>
      <a-space>
        <a-button @click="addOpen = false">取消</a-button>
        <a-button
          type="primary"
          :loading="addSaving"
          :disabled="!source || (allocMode === 'AUTO' ? !plan.length : !targets.length)"
          @click="submitAdd"
          >{{ allocMode === 'AUTO' ? '确认自动核销' : '提交核销' }}</a-button
        >
      </a-space>
    </template>
  </a-drawer>

  <a-modal v-model:open="reverseOpen" title="撤销核销" :confirm-loading="reverseSaving" @ok="submitReverse">
    <p v-if="reverseRow" class="scm-note">确认后该核销记录将作废，{{ moneyText(reverseRow.amount) }} 将退回对应单据的未结金额。</p>
    <a-alert v-if="reverseError" class="form-error" type="error" show-icon :message="reverseError" />
    <a-form ref="reverseFormRef" :model="reverseModel" :rules="reverseRules" layout="vertical"
      ><a-form-item label="撤销原因" name="reverseReason"
        ><a-textarea v-model:value="reverseReason" :maxlength="500" :rows="3" show-count /></a-form-item
    ></a-form>
  </a-modal>

  <a-modal v-model:open="waiverReverseOpen" title="反向抹零" :confirm-loading="waiverReverseSaving" @ok="submitWaiverReverse">
    <p v-if="waiverReverseRow" class="scm-note">
      确认后本次抹零将作废，应收未结金额恢复 {{ moneyText(waiverReverseRow.amount) }}。
    </p>
    <a-form ref="waiverReverseFormRef" :model="waiverReverseModel" :rules="waiverReverseRules" layout="vertical">
      <a-form-item label="反向原因" name="waiverReverseReason">
        <a-textarea v-model:value="waiverReverseReason" :maxlength="500" :rows="3" show-count />
      </a-form-item>
    </a-form>
  </a-modal>

  <BalanceMovementDetail ref="movementDetail" />
  <FinanceRecordPicker v-model:open="pickerOpen" :kind="pickerKind" @select="onCandidateSelected" />
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {onMounted, reactive, ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType, FormInstance} from 'ant-design-vue';
import {financeApi} from '/@/api/business/scm/finance-api';
import {
  SCM_FINANCE_BUSINESS_TYPE_ENUM,
  SCM_FINANCE_ENTRY_COLOR,
  SCM_FINANCE_ENTRY_TYPE_ENUM,
  SCM_FINANCE_PERMISSION as PERM,
  SCM_FINANCE_SOURCE_TYPE_ENUM,
} from '/@/constants/business/scm/finance-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import FinanceRecordPicker from './finance-record-picker.vue';
import BalanceMovementDetail from './balance-movement-detail.vue';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
const movementDetail = ref<InstanceType<typeof BalanceMovementDetail>>();
import {dateTimeText, entryTypeText, initialFinanceDateRange, moneyText, isValidPositiveAmount} from './finance-form-model';
import type {FinanceCandidate, FinanceWriteOff, FinanceWriteOffAutoPlan, WriteOffQuery} from './finance-types';
import {useFinancePage} from './use-finance-page';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';
import {notifyFormInvalid} from '/@/views/business/scm/common/scm-form-feedback';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';

interface AllocationDraft extends FinanceCandidate {
  amount: string;
}
const query = reactive<WriteOffQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinanceWriteOff, WriteOffQuery>(financeApi.writeOffQuery, financeApi.writeOffExport);
const addOpen = ref(false),
  addSaving = ref(false),
  addError = ref(''),
  sourceType = ref<'RECEIPT' | 'PAYMENT'>();
const source = ref<FinanceCandidate | null>(null),
  targets = ref<AllocationDraft[]>([]);
/** 分配方式：自动 FIFO 只选资金来源，手工选择保留原有的多目标分配。 */
const allocMode = ref<'AUTO' | 'MANUAL'>('AUTO');
const plan = ref<FinanceWriteOffAutoPlan[]>([]),
  planLoading = ref(false);
const pickerOpen = ref(false),
  pickerKind = ref<'RECEIPT' | 'PAYMENT' | 'RECEIVABLE' | 'PAYABLE'>('RECEIPT'),
  picking = ref<'SOURCE' | 'TARGET'>('SOURCE');
const reverseOpen = ref(false),
  reverseSaving = ref(false),
  reverseError = ref(''),
  reverseReason = ref(''),
  reverseRow = ref<FinanceWriteOff | null>(null);
const waiverReverseOpen = ref(false),
  waiverReverseSaving = ref(false),
  waiverReverseReason = ref(''),
  waiverReverseRow = ref<FinanceWriteOff | null>(null);
const addFormRef = ref<FormInstance>();
const reverseFormRef = ref<FormInstance>();
const waiverReverseFormRef = ref<FormInstance>();
/** 必填项逐项校验，错误显示在输入框下方。 */
const addRules = {
  sourceType: [{required: true, message: '请选择资金类型', trigger: 'change'}],
  source: [
    {
      validator: () => (source.value ? Promise.resolve() : Promise.reject(new Error('请选择资金单'))),
      trigger: 'change',
    },
  ],
};
const reverseRules = {reverseReason: [{required: true, message: '请填写撤销原因', trigger: 'blur'}]};
const waiverReverseRules = {waiverReverseReason: [{required: true, message: '请填写反向原因', trigger: 'blur'}]};
/** 校验模型必须是稳定对象；reactive 会解包内部 ref，值仍是响应式的。 */
const addModel = reactive({sourceType, source});
const reverseModel = reactive({reverseReason});
const waiverReverseModel = reactive({waiverReverseReason});
const entryOptions = Object.values(SCM_FINANCE_ENTRY_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const sourceOptions = Object.values(SCM_FINANCE_SOURCE_TYPE_ENUM).map((item) => ({label: item.desc, value: item.value}));
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

/** 资金来源中文。枚举只有收款和付款，核销记录还可能来自余额消费和抹零，这里补上。 */
const SOURCE_TYPE_TEXT: Record<string, string> = {
  RECEIPT: '收款',
  PAYMENT: '付款',
  BALANCE_MOVEMENT: '余额消费',
  WAIVER: '抹零',
};

function sourceTypeText(value?: string | null): string {
  return value ? SOURCE_TYPE_TEXT[value] ?? value : '—';
}

/** 目标类型：应收或应付。 */
function targetTypeText(value?: string | null): string {
  return value ? SCM_FINANCE_BUSINESS_TYPE_ENUM[value]?.desc ?? value : '—';
}

const columns = ref<TableColumnsType<FinanceWriteOff>>([
  {title: '核销单号', dataIndex: 'writeOffNo', width: 175},
  {title: '资金方', dataIndex: 'sourceName', width: 190},
  {title: '资金类型', dataIndex: 'sourceType', align: 'center', width: 90},
  {title: '资金单号', dataIndex: 'sourceNo', width: 180},
  {title: '核销目标', dataIndex: 'targetName', width: 190},
  {title: '目标类型', dataIndex: 'targetType', align: 'center', width: 90},
  {title: '目标单号', dataIndex: 'targetNo', width: 175},
  {title: '核销金额', dataIndex: 'amount', width: 136},
  {title: '方向', dataIndex: 'entryType', align: 'center', width: 80},
  {title: '原因', dataIndex: 'reason', width: 180},
  {title: '核销时点', dataIndex: 'writtenOffAt', width: 180},
  {title: '操作人', dataIndex: 'operator', width: 110},
  {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'center', width: 110},
]);
const planColumns: TableColumnsType<FinanceWriteOffAutoPlan> = [
  {title: '目标单号', dataIndex: 'targetNo', width: 200},
  {title: '往来方', dataIndex: 'targetName', width: 180},
  {title: '账单日期', dataIndex: 'eventAt', width: 180},
  {title: '未结金额', dataIndex: 'openAmount', width: 140},
  {title: '本次核销', dataIndex: 'allocatedAmount', width: 140},
];
const targetColumns: TableColumnsType<AllocationDraft> = [
  {title: '目标单号', dataIndex: 'documentNo', width: 210},
  {title: '往来方', dataIndex: 'partyName', width: 170},
  {title: '关联单号', dataIndex: 'linkedDocumentNo', width: 180},
  {title: '可核销金额', dataIndex: 'availableAmount', width: 150},
  {title: '本次金额', dataIndex: 'amount', width: 180},
  {title: '操作', dataIndex: 'action', align: 'center', width: 90},
];
useFinanceMobileActionColumn(
  (compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
  },
  () => columns.value.length
);

async function queryData() {
  await page.queryData(query);
}
function onSearch() {
  query.pageNum = 1;
  queryData();
}
function resetQuery() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 20,
    ...initialFinanceDateRange(),
    sourceNo: undefined,
    targetNo: undefined,
    entryType: undefined,
    sourceType: undefined,
  });
  queryData();
}
async function exportData() {
  await page.exportData(query);
}

function openAdd() {
  addError.value = '';
  sourceType.value = undefined;
  source.value = null;
  targets.value = [];
  plan.value = [];
  allocMode.value = 'AUTO';
  addOpen.value = true;
}
function resetAllocation() {
  source.value = null;
  targets.value = [];
  plan.value = [];
}
/** 切换分配方式只清掉上一次的预览/目标，资金单保持不变（同一笔钱的两种分配方式）。 */
function resetPlan() {
  plan.value = [];
  targets.value = [];
}

async function previewAuto() {
  if (!sourceType.value || !source.value) return;
  planLoading.value = true;
  addError.value = '';
  try {
    const response = await financeApi.writeOffAutoPreview({sourceType: sourceType.value, sourceId: source.value.id});
    plan.value = response.data;
    if (!response.data.length) {
      message.warning('该资金单的对方没有可按顺序核销的未结单据');
    }
  } catch {
    plan.value = [];
  } finally {
    planLoading.value = false;
  }
}
function openSourcePicker() {
  if (!sourceType.value) return;
  picking.value = 'SOURCE';
  pickerKind.value = sourceType.value;
  pickerOpen.value = true;
}
function openTargetPicker() {
  if (!sourceType.value || !source.value) return;
  picking.value = 'TARGET';
  pickerKind.value = sourceType.value === 'RECEIPT' ? 'RECEIVABLE' : 'PAYABLE';
  pickerOpen.value = true;
}
function onCandidateSelected(candidate: FinanceCandidate) {
  if (picking.value === 'SOURCE') {
    source.value = candidate;
    targets.value = [];
    plan.value = [];
    return;
  }
  if (targets.value.some((item) => String(item.id) === String(candidate.id))) {
    addError.value = '同一目标单不能重复添加。';
    return;
  }
  addError.value = '';
  targets.value.push({...candidate, amount: ''});
}
function removeTarget(id: FinanceCandidate['id']) {
  targets.value = targets.value.filter((item) => String(item.id) !== String(id));
}

async function submitAdd() {
  // 资金类型和资金单走字段校验；目标金额按当前分配方式检查
  try {
    await addFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, addFormRef.value);
    return;
  }
  if (!source.value || !sourceType.value) {
    addError.value = '请选择核销来源';
    return;
  }
  if (allocMode.value === 'AUTO' && !plan.value.length) {
    message.warning('请先预览自动分配结果，再确认核销');
    return;
  }
  const items = targets.value.filter((item) => isValidPositiveAmount(item.amount)).map((item) => ({targetId: item.id, amount: item.amount}));
  if (allocMode.value === 'MANUAL' && !items.length) {
    message.warning('请为至少一条目标填写正数金额（最多 4 位小数）');
    return;
  }
  addSaving.value = true;
  addError.value = '';
  try {
    if (allocMode.value === 'AUTO') {
      await financeApi.writeOffAuto({sourceType: sourceType.value, sourceId: source.value.id});
    } else {
      await financeApi.writeOffAdd({sourceType: sourceType.value, sourceId: source.value.id, items});
    }
    message.success(allocMode.value === 'AUTO' ? '已按账单日期自动核销' : '核销已登记');
    addOpen.value = false;
    await queryData();
  } catch {
    // 登记失败由全局 toast 提示；addError 只用于本地校验
  } finally {
    addSaving.value = false;
  }
}

function openReverse(row: FinanceWriteOff) {
  reverseRow.value = row;
  reverseReason.value = '';
  reverseOpen.value = true;
}
async function submitReverse() {
  if (!reverseRow.value) return;
  // 必填项由表单校验
  try {
    await reverseFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, reverseFormRef.value);
    return;
  }
  reverseSaving.value = true;
  reverseError.value = '';
  try {
    await financeApi.writeOffReverse({writeOffId: reverseRow.value.writeOffId, reason: reverseReason.value.trim()});
    message.success('反向核销已追加');
    reverseOpen.value = false;
    await queryData();
  } catch {
    // 反向失败由全局 toast 提示；reverseError 只用于表单校验
  } finally {
    reverseSaving.value = false;
  }
}

onMounted(queryData);

/** 抹零反向走抹零接口：核销反向会把 WAIVER 当普通资金单拒绝，且抹零事实不会置为已反向。 */
function openWaiverReverse(row: FinanceWriteOff) {
  waiverReverseRow.value = row;
  waiverReverseReason.value = '';
  waiverReverseOpen.value = true;
}
async function submitWaiverReverse() {
  if (!waiverReverseRow.value) return;
  try {
    await waiverReverseFormRef.value?.validate();
  } catch (err) {
    notifyFormInvalid(err, waiverReverseFormRef.value);
    return;
  }
  waiverReverseSaving.value = true;
  try {
    await financeApi.waiverReverse({waiverId: waiverReverseRow.value.sourceId, reason: waiverReverseReason.value.trim()});
    message.success('抹零已反向');
    waiverReverseOpen.value = false;
    await queryData();
  } catch {
    // 失败由全局 toast 提示
  } finally {
    waiverReverseSaving.value = false;
  }
}
</script>

<style scoped>
.date-separator {
  margin: 0 8px;
  color: #667085;
}
.page-error,
.form-error {
  margin-bottom: 12px;
}
@media (max-width: 768px) {
  .finance-table :deep(.ant-table-cell-fix-right) {
    position: static !important;
    right: auto !important;
  }
}
.source-summary {
  margin-top: 12px;
}
.alloc-mode {
  margin: 16px 0 4px;
}
.plan-table {
  margin-top: 12px;
}
.target-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 12px;
}
.target-toolbar h3 {
  margin: 0;
  font-weight: 600;
}
</style>
