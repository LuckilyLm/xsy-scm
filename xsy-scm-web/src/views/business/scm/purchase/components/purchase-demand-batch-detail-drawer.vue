<!-- workspace：冻结批次明细的两张宽表要横向铺开才看得清。
     只读，数字都由后端下发，前端不重算；接口同时校验批次和库存余额权限。 -->
<template>
  <a-drawer :open="open" title="冻结批次明细" :width="scmDrawerWidth('workspace')" @close="close">
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action>
        <a-button @click="load">重试</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions bordered size="small" :column="3" class="head">
          <a-descriptions-item label="批次编号"><span class="scm-mono">#{{ detail.batchId }}</span></a-descriptions-item>
          <a-descriptions-item label="状态">{{ batchStatusText(detail.status) }}</a-descriptions-item>
          <a-descriptions-item label="冻结时间">{{ datetime(detail.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="统计时间段" :span="2">{{ datetime(detail.startAt) }} ~ {{ datetime(detail.endAt) }}</a-descriptions-item>
          <a-descriptions-item label="冻结人">{{ detail.createdBy || '—' }}</a-descriptions-item>
          <a-descriptions-item label="仓库">{{ detail.warehouseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="供应商">{{ detail.supplierName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="采购员">{{ detail.purchaserName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="来源行数">{{ detail.sourceLineCount }}</a-descriptions-item>
          <a-descriptions-item label="建议采购行数">{{ detail.candidateLineCount }}</a-descriptions-item>
          <a-descriptions-item label="已生成需求">{{ detail.generatedCount }}</a-descriptions-item>
          <a-descriptions-item label="已跳过">{{ detail.skippedCount }}</a-descriptions-item>
        </a-descriptions>
        <p v-if="!detail.candidateLineCount" class="scm-note banner">本批订单所需商品现有库存已覆盖，无需采购。</p>
        <a-button type="primary" style="margin: 12px 0" v-privilege="'scm:purchase:inquiry:query'" @click="openInquiry">查看本批次询价</a-button>

        <a-divider orientation="left">各规格的计算依据</a-divider>
        <a-table
          size="small"
          :data-source="detail.summary ?? []"
          :columns="summaryColumns"
          row-key="skuId"
          bordered
          :pagination="false"
          :scroll="{x: scmColumnsWidth(summaryColumns)}"
        >
          <template #bodyCell="{record, column}">
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

        <a-divider orientation="left">缺口分摊到订单行</a-divider>
        <a-table
          size="small"
          :data-source="detail.items ?? []"
          :columns="itemColumns"
          row-key="lineNo"
          bordered
          :pagination="false"
          :scroll="{x: scmColumnsWidth(itemColumns)}"
        >
          <template #bodyCell="{record, column}">
            <template v-if="column.dataIndex === 'sourceQuantity'">
              <span class="scm-quantity">{{ quantity(record.sourceQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'requiredQuantity'">
              <span class="scm-quantity">{{ quantity(record.requiredQuantity) }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'existingDemandId'">
              <a-tag v-if="record.existingDemandId" color="orange">已有未完成需求</a-tag>
              <span v-else class="hint">—</span>
            </template>
            <template v-else-if="column.dataIndex === 'demandUnit'">{{ record.demandUnit || '—' }}</template>
            <template v-else-if="column.dataIndex === 'skuName'">{{ record.skuName || '—' }}</template>
          </template>
        </a-table>
      </template>
      <a-empty v-else-if="!loading && !error" description="请选择一个冻结批次" />
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {ref, watch} from 'vue';
import {useRouter} from 'vue-router';
import type {TableColumnsType} from 'ant-design-vue';
import {purchaseDemandApi} from '/@/api/business/scm/purchase-demand-api';
import {SCM_DEMAND_BATCH_STATUS_ENUM, SCM_DEMAND_SUMMARY_STATUS_COLOR, SCM_DEMAND_SUMMARY_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import type {DemandBatchDetail, DemandBatchItem, DemandBatchSummaryRow, Id} from '../purchase-types';
import {quantity} from '../purchase-form-model';
import {datetime} from '../../common/scm-display';
import {purchaseError} from '../purchase-errors';
import {scmDrawerWidth} from '/@/theme/scm/scm-drawer';

const props = defineProps<{open: boolean; batchId?: Id}>();
const router = useRouter();
function openInquiry(): void {
  if (props.batchId != null) {
    void router.push({path: '/purchase/purchase-inquiry-list', query: {batchId: String(props.batchId)}});
  }
}
const emit = defineEmits<{close: []}>();

/** 解释行里的数量统一按四位小数显示，空值显示“—”。 */
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

/** 批次状态用中文展示，不把枚举值直接抛给用户。 */
const batchStatusText = (status?: string) => SCM_DEMAND_BATCH_STATUS_ENUM[status ?? '']?.desc ?? status ?? '—';

const detail = ref<DemandBatchDetail>();
const loading = ref(false);
const error = ref('');
let requestId = 0;

// 列名用采购员的口头说法（需要买多少、仓里还有多少），不直接搬字段名
const summaryColumns: TableColumnsType<DemandBatchSummaryRow> = [
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '单位', dataIndex: 'demandUnit', width: 85},
  {title: '订单需求', dataIndex: 'orderDemandQuantity', width: 110},
  {title: '现有库存', dataIndex: 'onHandQuantity', width: 110},
  {title: '已预留（全仓）', dataIndex: 'reservedQuantity', width: 125},
  {title: '其中本批', dataIndex: 'selectedOrderReservedQuantity', width: 110},
  {title: '其他预留', dataIndex: 'otherReservedQuantity', width: 110},
  {title: '全仓可用', dataIndex: 'availableQuantity', width: 110},
  {title: '本批可用', dataIndex: 'stockAvailableForSelectedOrders', width: 110},
  {title: '差额', dataIndex: 'stockComparisonGap', width: 100},
  {title: '在途未收', dataIndex: 'inTransitQuantity', width: 110},
  {title: '已采购未收', dataIndex: 'purchaseCoverageQuantity', width: 115},
  {title: '需采购', dataIndex: 'netPurchaseGap', width: 100},
  {title: '结论', dataIndex: 'calculationStatus', align: 'center', width: 110},
];

const itemColumns: TableColumnsType<DemandBatchItem> = [
  {title: '行号', dataIndex: 'lineNo', width: 70},
  {title: '销售单号', dataIndex: 'salesOrderNo', width: 190},
  {title: '确认时间', dataIndex: 'sourceConfirmedAt', width: 180, customRender: ({text}) => datetime(text)},
  {title: '商品', dataIndex: 'productName', width: 160},
  {title: '商品规格', dataIndex: 'skuName', width: 130},
  {title: '单位', dataIndex: 'demandUnit', width: 85},
  {title: '订单实发量', dataIndex: 'sourceQuantity', width: 120},
  {title: '建议采购量', dataIndex: 'requiredQuantity', width: 120},
  {title: '已有需求', dataIndex: 'existingDemandId', width: 130},
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
    // 加载失败由抽屉内的 Alert 提示
    const r = await purchaseDemandApi.batchDetail({batchId}, {suppressGlobalErrorMessage: true});
    if (id === requestId) {
      detail.value = r.data;
    }
  } catch (e) {
    if (id === requestId) {
      // 清空旧批次，避免屏幕上留着上一个批次的数据
      detail.value = undefined;
      error.value = purchaseError(e);
    }
  } finally {
    if (id === requestId) {
      loading.value = false;
    }
  }
}

// 每次打开都重新加载，重新打开同一批次也会刷新
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
