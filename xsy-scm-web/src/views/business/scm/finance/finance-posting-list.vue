<template>
  <a-form class="smart-query-form" layout="inline" @submit.prevent>
    <a-row class="smart-query-form-row">
      <a-form-item label="过账日期">
        <a-date-picker v-model:value="query.startDate" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <span class="date-separator">至</span>
        <a-date-picker v-model:value="query.endDate" value-format="YYYY-MM-DD" placeholder="结束日期" />
      </a-form-item>
      <a-form-item label="客户">
        <a-select
          v-model:value="query.customerId"
          allow-clear
          show-search
          option-filter-prop="label"
          :options="customerOptions"
          placeholder="全部客户"
          style="width: 190px"
        />
      </a-form-item>
      <a-form-item label="单号 / 客户">
        <a-input v-model:value="query.keyword" allow-clear placeholder="过账单号或客户名称" style="width: 200px" @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="返款状态">
        <a-select v-model:value="query.refundStatus" allow-clear :options="refundStatusOptions" placeholder="全部" style="width: 130px" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="query.status" allow-clear :options="statusOptions" placeholder="全部" style="width: 120px" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button-group>
          <a-button type="primary" v-privilege="PERM.POSTING_QUERY" @click="onSearch">查询</a-button>
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
      <div class="smart-table-operate-block">
        <a-button type="primary" v-privilege="PERM.POSTING_ADD" @click="openCreate">新建过账</a-button>
        <span class="posting-hint">虚拟单据：不生成应收应付，不参与核销</span>
      </div>
      <div class="smart-table-setting-block">
        <a-button v-privilege="PERM.EXPORT" :loading="page.exporting.value" @click="exportData">导出</a-button>
        <TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_FINANCE_POSTING" :refresh="queryData" />
      </div>
    </a-row>

    <div class="finance-mobile-balance-list">
      <div v-for="record in page.tableData.value" :key="record.postingId" class="finance-mobile-balance-row">
        <div class="mobile-balance-heading">
          <strong>{{ record.postingNo }}</strong>
          <a-button type="link" @click="openDetail(record)">明细</a-button>
        </div>
        <div class="mobile-balance-party">{{ record.customerName }} · {{ record.documentDate }}</div>
        <div class="mobile-balance-values">
          <span
            >金额 <strong>{{ moneyText(record.amount) }}</strong></span
          >
          <span>税点 {{ percentText(record.taxRate) }}</span>
          <span>{{ refundStatusText(record.refundStatus) }}</span>
          <ScmStatusTag v-if="record.status === 'VOIDED'" tone="neutral" label="已作废" />
        </div>
      </div>
    </div>

    <a-table
      id="scm-finance-posting-table"
      class="finance-table"
      size="small"
      :data-source="page.tableData.value"
      :columns="columns"
      row-key="postingId"
      :loading="page.loading.value"
      :pagination="false"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column, text}">
        <template v-if="column.dataIndex === 'amount'">
          <span class="scm-money">{{ moneyText(text) }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'taxRate'">{{ percentText(text) }}</template>
        <template v-else-if="column.dataIndex === 'refundStatus'">
          <ScmStatusTag :tone="refundStatusTone(text)" :label="refundStatusText(text)" />
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="text === 'VOIDED' ? 'neutral' : 'success'" :label="statusText(text)" />
        </template>
        <template v-else-if="column.dataIndex === 'createdAt'">{{ dateTimeText(text) }}</template>
        <template v-else-if="column.dataIndex === 'action'">
          <div class="scm-table-actions">
            <a-button type="link" @click="openDetail(record)">明细</a-button>
            <a-button v-if="record.status === 'NORMAL'" v-privilege="PERM.POSTING_UPDATE" type="link" @click="openEdit(record)">修改</a-button>
            <a-button v-if="record.status === 'NORMAL'" v-privilege="PERM.POSTING_VOID" type="link" danger @click="openVoid(record)">作废</a-button>
          </div>
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

  <a-modal v-model:open="formOpen" :title="editing ? '修改过账' : '新建过账'" :confirm-loading="formSaving" :width="620" @ok="submitForm">
    <a-alert type="info" show-icon message="过账单据只做对外账目留痕：不生成应收应付、不参与核销、不影响客户余额与账龄。" />
    <a-form layout="vertical" class="posting-form">
      <a-form-item label="客户" required>
        <a-select
          v-if="!editing"
          v-model:value="form.customerId"
          show-search
          option-filter-prop="label"
          :options="customerOptions"
          placeholder="选择客户"
        />
        <!-- 换客户等于换一张单：已登记的过账不允许改客户，只能作废后重新登记 -->
        <a-input v-else :value="editingCustomerName" disabled />
      </a-form-item>
      <a-form-item label="过账日期" required>
        <a-date-picker v-model:value="form.documentDate" value-format="YYYY-MM-DD" style="width: 100%" />
      </a-form-item>
      <a-form-item label="过账金额" required>
        <a-input-number
          v-model:value="form.amount"
          string-mode
          :min="0"
          :precision="4"
          :max="99999999999999"
          style="width: 100%"
          placeholder="例如 1000.00"
        />
      </a-form-item>
      <a-form-item label="税点（%）">
        <a-input-number
          v-model:value="form.taxRate"
          string-mode
          :min="0"
          :max="100"
          :precision="4"
          style="width: 100%"
          placeholder="按单据填写，可留空"
        />
      </a-form-item>
      <a-form-item label="返款状态">
        <a-select v-model:value="form.refundStatus" :options="refundStatusOptions" />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" :maxlength="500" :rows="3" show-count placeholder="例如：对账单编号、返款约定" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-modal v-model:open="voidOpen" title="作废过账" :confirm-loading="voidSaving" @ok="submitVoid">
    <a-alert
      v-if="voidRow"
      type="warning"
      show-icon
      :message="`作废后 ${voidRow.postingNo} 不再代表有效过账；原记录保留，作废原因会写入操作日志。`"
    />
    <a-form layout="vertical" class="posting-form">
      <a-form-item label="作废原因" required>
        <a-textarea v-model:value="voidReason" :maxlength="500" :rows="3" show-count placeholder="例如：金额录错，重新登记" />
      </a-form-item>
    </a-form>
  </a-modal>

  <a-drawer v-model:open="detailOpen" title="过账详情" :width="scmDrawerWidth('xl')" :destroy-on-close="true" @close="detailOpen = false">
    <a-alert v-if="detailError" type="error" show-icon :message="detailError">
      <template #action><a-button :loading="detailLoading" @click="loadDetail">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <section class="detail-section">
          <h3>单据信息</h3>
          <a-descriptions bordered size="small" :column="{xs: 1, sm: 2}">
            <a-descriptions-item label="过账单号">{{ detail.postingNo }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ statusText(detail.status) }}</a-descriptions-item>
            <a-descriptions-item label="客户">{{ detail.customerName }}</a-descriptions-item>
            <a-descriptions-item label="结算客户">{{ detail.settlementCustomerName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="过账日期">{{ detail.documentDate }}</a-descriptions-item>
            <a-descriptions-item label="过账金额">{{ moneyText(detail.amount) }}</a-descriptions-item>
            <a-descriptions-item label="税点">{{ percentText(detail.taxRate) }}</a-descriptions-item>
            <a-descriptions-item label="返款状态">{{ refundStatusText(detail.refundStatus) }}</a-descriptions-item>
            <a-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.status === 'VOIDED'" label="作废原因" :span="2">{{ detail.voidReason || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.status === 'VOIDED'" label="作废时间">{{ dateTimeText(detail.voidedAt) }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.status === 'VOIDED'" label="作废人">{{ detail.voidedBy || '—' }}</a-descriptions-item>
            <a-descriptions-item label="登记人">{{ detail.createdBy || '—' }}</a-descriptions-item>
            <a-descriptions-item label="登记时间">{{ dateTimeText(detail.createdAt) }}</a-descriptions-item>
          </a-descriptions>
        </section>

        <section class="detail-section">
          <h3>操作日志</h3>
          <a-empty v-if="!logs.length" description="暂无操作日志" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
          <a-table
            v-else
            size="small"
            :data-source="logs"
            :columns="logColumns"
            row-key="id"
            :pagination="false"
            :scroll="{x: scmColumnsWidth(logColumns)}"
          >
            <template #bodyCell="{record, column}">
              <template v-if="column.dataIndex === 'operationType'">{{ operationTypeText(record.operationType) }}</template>
              <template v-else-if="column.dataIndex === 'createdAt'">{{ dateTimeText(record.createdAt) }}</template>
              <template v-else-if="column.dataIndex === 'reason'">{{ record.reason || '—' }}</template>
              <template v-else-if="column.dataIndex === 'snapshot'">
                <a-button v-if="record.beforeData || record.afterData" type="link" @click="openAudit(record)">变更前后</a-button>
                <span v-else>—</span>
              </template>
            </template>
          </a-table>
        </section>
      </template>
      <a-empty v-else-if="!detailLoading && !detailError" description="暂无过账详情" />
    </a-spin>
  </a-drawer>

  <a-modal :open="auditOpen" title="变更前后" width="900px" :footer="null" @cancel="auditOpen = false">
    <ScmDiffTable :before="auditLog?.beforeData" :after="auditLog?.afterData" :scope="auditLog?.operationType" />
  </a-modal>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import dayjs from 'dayjs';
import {message} from 'ant-design-vue';
import {Empty} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {customerApi} from '/@/api/business/scm/customer-api';
import {financeApi} from '/@/api/business/scm/finance-api';
import {
  SCM_FINANCE_PERMISSION as PERM,
  SCM_FINANCE_POSTING_REFUND_STATUS_ENUM,
  SCM_FINANCE_POSTING_STATUS_ENUM,
} from '/@/constants/business/scm/finance-const';
import {SCM_PAGE_SIZE_OPTIONS} from '/@/constants/business/scm/pagination-const';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';
import {dateTimeText, initialFinanceDateRange, isValidPositiveAmount, moneyText, operationTypeText, percentText} from './finance-form-model';
import {useFinancePage} from './use-finance-page';
import {useFinanceMobileActionColumn} from './use-finance-mobile-table';
import type {FinanceOperationLog, FinancePosting, FinancePostingAddForm, PostingQuery} from './finance-types';

const query = reactive<PostingQuery>({pageNum: 1, pageSize: 20, ...initialFinanceDateRange()});
const page = useFinancePage<FinancePosting, PostingQuery>(financeApi.postingQuery, financeApi.postingExport);
const customerOptions = ref<Array<{label: string; value: string | number}>>([]);
const refundStatusOptions = Object.values(SCM_FINANCE_POSTING_REFUND_STATUS_ENUM).map((item) => ({label: item.desc, value: item.value}));
const statusOptions = Object.values(SCM_FINANCE_POSTING_STATUS_ENUM).map((item) => ({label: item.desc, value: item.value}));
const actionColumnFixed: 'right' | undefined = window.matchMedia('(max-width: 768px)').matches ? undefined : 'right';

const columns = ref<TableColumnsType<FinancePosting>>([
  {title: '过账单号', dataIndex: 'postingNo', width: 170, ellipsis: true},
  {title: '客户', dataIndex: 'customerName', width: 190, ellipsis: true},
  {title: '过账日期', dataIndex: 'documentDate', width: 120},
  {title: '过账金额', dataIndex: 'amount', width: 140},
  {title: '税点', dataIndex: 'taxRate', width: 90},
  {title: '返款状态', dataIndex: 'refundStatus', align: 'center', width: 110},
  {title: '状态', dataIndex: 'status', align: 'center', width: 90},
  {title: '备注', dataIndex: 'remark', width: 200, ellipsis: true},
  {title: '登记人', dataIndex: 'createdBy', width: 110},
  {title: '登记时间', dataIndex: 'createdAt', width: 180},
  {title: '操作', dataIndex: 'action', fixed: actionColumnFixed, align: 'center', width: 160},
]);
useFinanceMobileActionColumn(
  (compact) => {
    const action = columns.value[columns.value.length - 1];
    if (action) action.fixed = compact ? undefined : 'right';
  },
  () => columns.value.length
);

const logColumns: TableColumnsType<FinanceOperationLog> = [
  {title: '时间', dataIndex: 'createdAt', width: 180},
  {title: '操作', dataIndex: 'operationType', align: 'center', width: 160},
  {title: '操作人', dataIndex: 'operator', width: 130},
  // 无宽度的列在窄屏可滚动表里会被压成 0，原因列必须给宽度
  {title: '原因 / 备注', dataIndex: 'reason', width: 220, ellipsis: true},
  {title: '快照', dataIndex: 'snapshot', width: 100},
];

function refundStatusText(value: string | null | undefined): string {
  return (value && SCM_FINANCE_POSTING_REFUND_STATUS_ENUM[value]?.desc) || value || '—';
}

// 与应收「未核销 / 部分 / 已结清」同一套档位：没返的款要能在列表里一眼看见
function refundStatusTone(value: string | null | undefined): ScmStatusTone {
  if (value === 'REFUNDED') return 'success';
  if (value === 'PARTIAL') return 'processing';
  if (value === 'PENDING') return 'warning';
  return 'neutral';
}

function statusText(value: string | null | undefined): string {
  return (value && SCM_FINANCE_POSTING_STATUS_ENUM[value]?.desc) || value || '—';
}

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
    customerId: undefined,
    keyword: undefined,
    refundStatus: undefined,
    status: undefined,
  });
  queryData();
}

async function exportData() {
  await page.exportData(query);
}

// ---- 新建 / 修改 ----
const formOpen = ref(false);
const formSaving = ref(false);
const editing = ref<FinancePosting | null>(null);
const editingCustomerName = ref('');
const form = reactive<FinancePostingAddForm>({
  customerId: '',
  documentDate: '',
  amount: '',
  taxRate: null,
  refundStatus: 'PENDING',
  remark: null,
});

function openCreate() {
  editing.value = null;
  editingCustomerName.value = '';
  Object.assign(form, {
    customerId: '',
    documentDate: dayjs().format('YYYY-MM-DD'),
    amount: '',
    taxRate: null,
    refundStatus: 'PENDING',
    remark: null,
  });
  formOpen.value = true;
}

function openEdit(row: FinancePosting) {
  editing.value = row;
  editingCustomerName.value = row.customerName;
  Object.assign(form, {
    customerId: row.customerId,
    documentDate: row.documentDate,
    amount: row.amount,
    taxRate: row.taxRate ?? null,
    refundStatus: row.refundStatus,
    remark: row.remark ?? null,
  });
  formOpen.value = true;
}

async function submitForm() {
  if (!editing.value && !form.customerId) {
    message.warning('请选择过账客户');
    return;
  }
  if (!form.documentDate) {
    message.warning('请选择过账日期');
    return;
  }
  if (!isValidPositiveAmount(form.amount)) {
    message.warning('请填写大于 0、最多 4 位小数的过账金额');
    return;
  }
  formSaving.value = true;
  try {
    if (editing.value) {
      const row = editing.value;
      await financeApi.postingUpdate({
        postingId: row.postingId,
        version: row.version,
        documentDate: form.documentDate,
        amount: form.amount,
        taxRate: form.taxRate ?? null,
        refundStatus: form.refundStatus,
        remark: form.remark ?? null,
      });
      message.success('过账已修改');
    } else {
      await financeApi.postingAdd({...form});
      message.success('过账已登记');
    }
    formOpen.value = false;
    await queryData();
  } catch {
    // 失败由全局 toast 提示，弹窗保持打开便于修改
  } finally {
    formSaving.value = false;
  }
}

// ---- 作废 ----
const voidOpen = ref(false);
const voidSaving = ref(false);
const voidRow = ref<FinancePosting | null>(null);
const voidReason = ref('');

function openVoid(row: FinancePosting) {
  voidRow.value = row;
  voidReason.value = '';
  voidOpen.value = true;
}

async function submitVoid() {
  const row = voidRow.value;
  if (!row) return;
  const reason = voidReason.value.trim();
  if (!reason) {
    message.warning('请填写作废原因');
    return;
  }
  voidSaving.value = true;
  try {
    await financeApi.postingVoid({postingId: row.postingId, version: row.version, reason});
    message.success('过账已作废');
    voidOpen.value = false;
    await queryData();
  } catch {
    // 失败由全局 toast 提示，弹窗保持打开便于修改
  } finally {
    voidSaving.value = false;
  }
}

// ---- 详情与日志 ----
const detailOpen = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<FinancePosting | null>(null);
const logs = ref<FinanceOperationLog[]>([]);
const auditOpen = ref(false);
const auditLog = ref<FinanceOperationLog | null>(null);

async function openDetail(row: FinancePosting) {
  detail.value = row;
  logs.value = [];
  detailError.value = '';
  detailOpen.value = true;
  await loadDetail();
}

async function loadDetail() {
  if (!detail.value) return;
  const postingId = detail.value.postingId;
  detailLoading.value = true;
  detailError.value = '';
  try {
    const response = await financeApi.postingDetail(postingId, {suppressGlobalErrorMessage: true});
    detail.value = response.data;
    const logResponse = await financeApi.operationLogs('POSTING', postingId, {suppressGlobalErrorMessage: true});
    logs.value = logResponse.data ?? [];
  } catch {
    detailError.value = '过账详情加载失败，请重试。';
  } finally {
    detailLoading.value = false;
  }
}

function openAudit(log: FinanceOperationLog) {
  auditLog.value = log;
  auditOpen.value = true;
}

onMounted(async () => {
  try {
    // 客户筛选与建单是可选增强：拉不到就静默降级，不弹提示
    const response = await customerApi.optionList({suppressGlobalErrorMessage: true});
    customerOptions.value = response.data.map((item) => ({label: item.name, value: item.customerId}));
  } catch {
    // 客户资料失败不影响过账列表
  }
  queryData();
});
</script>

<style scoped>
.date-separator {
  margin: 0 8px;
  color: #667085;
}
.page-error {
  margin-bottom: 12px;
}
.posting-hint {
  margin-left: 12px;
  color: #667085;
  font-size: 12px;
}
.posting-form {
  margin-top: 16px;
}
.detail-section {
  margin-bottom: 24px;
}
.detail-section h3 {
  margin: 0 0 12px;
  font-weight: 600;
}
.finance-mobile-balance-list {
  display: none;
}

.finance-mobile-balance-row {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.mobile-balance-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mobile-balance-heading strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mobile-balance-party {
  margin: 0 0 6px;
  color: #667085;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.mobile-balance-values {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 14px;
  font-variant-numeric: tabular-nums;
}
.mobile-balance-values span {
  color: #667085;
  font-size: 12px;
}
.mobile-balance-values strong {
  color: #1d2939;
  font-weight: 600;
}
@media (max-width: 768px) {
  .finance-mobile-balance-list {
    display: block;
  }
  /* 窄屏只保留卡片列表，避免表格和卡片重复展示 */
  .finance-table {
    display: none;
  }
  .finance-table :deep(.ant-table-cell-fix-right) {
    position: static !important;
    right: auto !important;
  }
}
</style>
