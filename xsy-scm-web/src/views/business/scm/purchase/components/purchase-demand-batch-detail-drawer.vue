<!-- 冻结批次明细（只读回看）。
ADM-05 收口：批次一旦生成就只剩计数时，「为什么建议这个数量」的解释链是断的。
本抽屉把冻结快照的三块内容摊开——批次头（冻结输入与计数）、SKU 级解释行、逐行建议——
所有数字原样取自快照、不在前端重算（与缺口预览同一取向）。
接口按 `scm:purchase:demand:batch:query` AND `scm:inventory:balance:query` 鉴权（返回体含库存量），
调用方的 `v-privilege` 只是体验，不是权限边界。 -->
<template>
  <!-- workspace：超宽业务数据阅读 —— 冻结快照的两张宽表（scroll.x 1800 / 1400）
       是回看「当时为什么建议这个数量」的全部依据，横向空间即内容。 -->
  <a-drawer
      :open="open"
      title="冻结批次明细"
      :width="scmDrawerWidth('workspace')"
      @close="close"
  >
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button @click="load">重试</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions bordered size="small" :column="3" class="head">
          <a-descriptions-item label="冻结批次">{{ detail.batchId }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ detail.status }}</a-descriptions-item>
          <a-descriptions-item label="冻结时间">{{ detail.createdAt || '—' }}</a-descriptions-item>
          <a-descriptions-item label="统计时间段" :span="2">
            {{ detail.startAt || '—' }} ~ {{ detail.endAt || '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="冻结人">{{ detail.createdBy || '—' }}</a-descriptions-item>
          <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="采购员">{{ detail.purchaserName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来源行数">{{ detail.sourceLineCount }}</a-descriptions-item>
          <a-descriptions-item label="候选行数">{{ detail.candidateLineCount }}</a-descriptions-item>
          <a-descriptions-item label="已生成需求">{{ detail.generatedCount }}</a-descriptions-item>
          <a-descriptions-item label="已跳过">{{ detail.skippedCount }}</a-descriptions-item>
        </a-descriptions>

        <a-divider orientation="left">商品规格需求解释行</a-divider>
        <a-table
            size="small"
            :data-source="detail.summary ?? []"
            :columns="summaryColumns"
            row-key="skuId"
            bordered
            :pagination="false"
            :scroll="{ x: 1800 }"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'calculationStatus'">
              <a-tag :color="SCM_DEMAND_SUMMARY_STATUS_COLOR[record.calculationStatus] || 'default'">
                {{ SCM_DEMAND_SUMMARY_STATUS_ENUM[record.calculationStatus]?.desc || record.calculationStatus }}
              </a-tag>
            </template>
            <template v-else-if="numericColumns.includes(column.dataIndex)">
              <span class="scm-quantity">{{ quantity(record[column.dataIndex]) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ record.demandUnit || '—' }}</template>
          </template>
        </a-table>

        <a-divider orientation="left">逐行建议（净缺口摊到哪张订单行）</a-divider>
        <a-table
            size="small"
            :data-source="detail.items ?? []"
            :columns="itemColumns"
            row-key="lineNo"
            bordered
            :pagination="false"
            :scroll="{ x: 1400 }"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'sourceQuantity'">
              <span class="scm-quantity">{{ quantity(record.sourceQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'requiredQuantity'">
              <span class="scm-quantity">{{ quantity(record.requiredQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'existingDemandId'">
              <span v-if="record.existingDemandId">已有需求 {{ record.existingDemandId }}</span>
              <span v-else class="hint">—</span>
            </template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ record.demandUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'skuName'">{{ record.skuName || '—' }}</template>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading && !error" description="请选择一个冻结批次"/>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {SCM_DEMAND_SUMMARY_STATUS_COLOR, SCM_DEMAND_SUMMARY_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {DemandBatchDetail, DemandBatchItem, DemandBatchSummaryRow, Id} from '../purchase-types';
import {quantity} from '../purchase-form-model';
import {purchaseError} from '../purchase-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const props = defineProps<{ open: boolean; batchId?: Id }>();
const emit = defineEmits<{ close: [] }>();

/** 解释行里所有数量都是后端下发的四位定点字符串，统一走 `quantity` 渲染（null → —）。 */
const numericColumns = [
  'orderDemandQuantity',
  'onHandQuantity',
  'reservedQuantity',
  'selectedOrderReservedQuantity',
  'otherReservedQuantity',
  'availableQuantity',
  'stockAvailableForSelectedOrders',
  'stockComparisonGap',
  'inTransitQuantity',
  'purchaseCoverageQuantity',
  'netPurchaseGap',
];

const detail = ref<DemandBatchDetail>();
const loading = ref(false);
const error = ref('');
let requestId = 0;

const summaryColumns: TableColumnsType<DemandBatchSummaryRow> = [
  {title: '商品规格编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '需求单位', dataIndex: 'demandUnit', width: 95, align: 'center'},
  {title: '订单需求量', dataIndex: 'orderDemandQuantity', align: 'right', width: 120},
  {title: '现有量', dataIndex: 'onHandQuantity', align: 'right', width: 110},
  {title: '全仓预留', dataIndex: 'reservedQuantity', align: 'right', width: 110},
  {title: '其中本批预留', dataIndex: 'selectedOrderReservedQuantity', align: 'right', width: 130},
  {title: '其他业务预留', dataIndex: 'otherReservedQuantity', align: 'right', width: 130},
  {title: '全仓净可用', dataIndex: 'availableQuantity', align: 'right', width: 110},
  {title: '本批可用', dataIndex: 'stockAvailableForSelectedOrders', align: 'right', width: 110},
  {title: '库存对比差额', dataIndex: 'stockComparisonGap', align: 'right', width: 130},
  {title: '有效在途未收', dataIndex: 'inTransitQuantity', align: 'right', width: 130},
  {title: '已有采购覆盖', dataIndex: 'purchaseCoverageQuantity', align: 'right', width: 130},
  {title: '净采购缺口', dataIndex: 'netPurchaseGap', align: 'right', width: 125},
  {title: '计算状态', dataIndex: 'calculationStatus', align: 'center', width: 120},
];

const itemColumns: TableColumnsType<DemandBatchItem> = [
  {title: '行号', dataIndex: 'lineNo', align: 'right', width: 70},
  {title: '来源销售单号', dataIndex: 'salesOrderNo', width: 190},
  {title: '确认时间', dataIndex: 'sourceConfirmedAt', width: 180},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 150},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '需求单位', dataIndex: 'demandUnit', width: 95, align: 'center'},
  {title: '来源实发量', dataIndex: 'sourceQuantity', align: 'right', width: 120},
  {title: '本批建议量', dataIndex: 'requiredQuantity', align: 'right', width: 120},
  {title: '冻结时已有需求', dataIndex: 'existingDemandId', width: 150},
];

async function load() {
  const batchId = props.batchId;
  if (batchId === undefined || batchId === null) {
    detail.value = undefined;
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    // 加载失败由抽屉内的 Alert 承担，不再让全局 toast 重复说一遍
    const r = await purchaseDemandApi.batchDetail({batchId}, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      detail.value = r.data;
    }
  } catch (e) {
    if (id === requestId) {
      // 失败时清空旧批次，避免把上一个批次的数字留在屏幕上（和供应商对账同一取向）
      detail.value = undefined;
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

// 监听「打开时的批次号」：关闭会把 key 收回 undefined，因此「关掉再打开同一批次」
// 也会重新拉一次（冻结快照虽不可变，但接口可能刚被授权，缓存旧结果没有意义）。
watch(
    () => (props.open ? props.batchId : undefined),
    (batchId) => {
      if (props.open && batchId !== undefined && batchId !== null) {
        void load();
      }
    }
);

function close() {
  emit('close');
}
</script>

<style scoped>
.head {
  margin-bottom: 12px;
}

.banner {
  margin-bottom: 12px;
}

.hint {
  color: var(--scm-text-secondary);
}
</style>
