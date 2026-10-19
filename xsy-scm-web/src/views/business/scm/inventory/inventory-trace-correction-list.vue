<!--
  包装码更正。

  解绑不是删除：申请 → 他人审批 → 追加解绑事实，三步都留痕；批准后包装码回到可绑定状态。
  申请与审批分离，避免现场自己改自己绑错的码。
-->
<template>
  <a-form class="scm-filter-bar" layout="inline" @submit.prevent="onSearch">
    <div class="scm-filter-fields">
      <a-form-item label="状态">
        <a-select v-model:value="queryForm.status" :options="STATUS_OPTIONS" allow-clear placeholder="全部" style="width: 140px"/>
      </a-form-item>
      <a-form-item label="包装码">
        <a-input v-model:value="queryForm.unitCode" placeholder="包装码" allow-clear @pressEnter="onSearch"/>
      </a-form-item>
      <a-form-item label="批次号">
        <a-input v-model:value="queryForm.lotNo" placeholder="批次号" allow-clear @pressEnter="onSearch"/>
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
      <div class="smart-table-operate-block"></div>
      <div class="smart-table-setting-block">
        <TableOperator
            v-model="columns"
            :table-id="TABLE_ID_CONST.BUSINESS.SCM_INVENTORY_TRACE_CORRECTION"
            :refresh="queryData"
        />
      </div>
    </a-row>

    <a-table
        :id="SCM_INVENTORY_TABLE_ID.TRACE_CORRECTION"
        size="small"
        :data-source="tableData"
        :columns="columns"
        row-key="id"
        bordered
        :loading="loading"
        :pagination="false"
        :locale="{ emptyText: '暂无更正申请' }"
        :scroll="{ x: scmColumnsWidth(columns) }"
    >
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'unitCode'">
          <span class="scm-mono">{{ record.unitCode }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'lotNo'">
          <span class="scm-mono">{{ record.lotNo || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <ScmStatusTag :tone="STATUS_TONE[record.status] || 'neutral'" :label="STATUS_LABEL[record.status] || record.status"/>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space :size="0" class="smart-table-operate scm-table-actions">
            <template v-if="record.status === 'PENDING'">
              <a-button type="link" size="small" @click="openDecision(record, 'approve')"
                        v-privilege="'scm:inventory:unit:correct:approve'">批准</a-button>
              <a-button type="link" size="small" danger @click="openDecision(record, 'reject')"
                        v-privilege="'scm:inventory:unit:correct:approve'">驳回</a-button>
            </template>
            <span v-else>—</span>
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

  <a-modal v-model:open="decisionOpen" :title="decisionTitle" :confirm-loading="deciding" @ok="submitDecision">
    <a-alert v-if="decisionError" class="form-error" type="error" show-icon :message="decisionError"/>
    <a-descriptions bordered size="small" :column="1">
      <a-descriptions-item label="包装码">{{ current?.unitCode }}</a-descriptions-item>
      <a-descriptions-item label="客户">{{ current?.customerName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="更正原因">{{ current?.reason }}</a-descriptions-item>
      <a-descriptions-item label="申请人">{{ current?.requestedBy }}</a-descriptions-item>
    </a-descriptions>
    <a-form layout="vertical">
      <a-form-item label="审批意见">
        <a-textarea v-model:value="decisionReason" :maxlength="200" :rows="2" aria-label="审批意见"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import TableOperator from '/@/components/support/table-operator/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {inventoryTraceCorrectionApi} from '/@/api/business/scm/inventory-trace-correction-api';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {SCM_INVENTORY_TABLE_ID} from '/@/constants/business/scm/inventory-const';
import {inventoryError} from './inventory-errors';
import type {TraceCorrection, TraceCorrectionQuery} from './inventory-types';

const STATUS_OPTIONS = [
  {value: 'PENDING', label: '待审批'},
  {value: 'APPROVED', label: '已批准'},
  {value: 'REJECTED', label: '已驳回'},
];
const STATUS_LABEL: Record<string, string> = {PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回'};
const STATUS_TONE: Record<string, 'warning' | 'success' | 'neutral'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'neutral',
};

const queryForm = reactive<TraceCorrectionQuery>({pageNum: 1, pageSize: 20});
const tableData = ref<TraceCorrection[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref('');

const columns = ref<TableColumnsType<TraceCorrection>>([
  {title: '包装码', dataIndex: 'unitCode', width: 150},
  {title: '批次号', dataIndex: 'lotNo', width: 180, ellipsis: true},
  {title: '客户', dataIndex: 'customerName', width: 140, ellipsis: true},
  {title: '订单号', dataIndex: 'orderNo', width: 160, ellipsis: true},
  {title: '更正原因', dataIndex: 'reason', width: 200, ellipsis: true},
  {title: '状态', dataIndex: 'status', width: 100, align: 'center'},
  {title: '申请人', dataIndex: 'requestedBy', width: 140},
  {title: '申请时间', dataIndex: 'requestedAt', width: 170},
  {title: '操作', dataIndex: 'action', width: 120, align: 'center'},
]);

const decisionOpen = ref(false);
const deciding = ref(false);
const decisionError = ref('');
const decisionReason = ref('');
const decisionKind = ref<'approve' | 'reject'>('approve');
const current = ref<TraceCorrection>();
const decisionTitle = computed(() => (decisionKind.value === 'approve' ? '批准更正' : '驳回更正'));

async function queryData() {
  loading.value = true;
  error.value = '';
  try {
    const res = await inventoryTraceCorrectionApi.query(queryForm, {suppressGlobalErrorMessage: true});
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
  queryForm.status = undefined;
  queryForm.unitCode = undefined;
  queryForm.lotNo = undefined;
  onSearch();
}

function openDecision(record: TraceCorrection, kind: 'approve' | 'reject') {
  current.value = record;
  decisionKind.value = kind;
  decisionReason.value = '';
  decisionError.value = '';
  decisionOpen.value = true;
}

async function submitDecision() {
  if (!current.value) {
    return;
  }
  decisionError.value = '';
  deciding.value = true;
  try {
    const payload = {id: current.value.id, version: current.value.version, reason: decisionReason.value.trim() || null};
    if (decisionKind.value === 'approve') {
      await inventoryTraceCorrectionApi.approve(payload);
    } else {
      await inventoryTraceCorrectionApi.reject(payload);
    }
    decisionOpen.value = false;
    await queryData();
  } catch {
    // 审批失败只走全局 toast；本地只保留「必须填原因」这类前置校验
  } finally {
    deciding.value = false;
  }
}

onMounted(queryData);
</script>
