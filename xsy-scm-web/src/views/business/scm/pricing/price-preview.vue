<!--  价格试算。价格状态与可售状态独立展示。 -->
<template>
  <a-card size="small" :bordered="false">
    <template #title>
      价格试算
      <ScmFieldHelp label="价格试算" text="按客户、商品规格及指定时间查询适用价格；0 元也可能是有效价格。" />
    </template>
    <a-form layout="inline" class="smart-query-form">
      <a-row class="smart-query-form-row">
        <a-form-item label="客户" required class="smart-query-form-item">
          <CustomerSelect v-model:value="customerId" width="220px" />
        </a-form-item>
        <a-form-item label="商品规格" required class="smart-query-form-item">
          <SkuSelect v-model:value="skuIds" mode="multiple" width="380px" :disabled-statuses="[]" />
        </a-form-item>
        <a-form-item label="价格生效时间" class="smart-query-form-item">
          <a-date-picker v-model:value="at" show-time value-format="YYYY-MM-DD HH:mm:ss" placeholder="当前时点" />
        </a-form-item>
        <a-form-item label="配送仓" class="smart-query-form-item">
          <WarehouseSelect v-model:value="warehouseId" width="180px" />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-button type="primary" @click="resolve" :loading="loading" v-privilege="'scm:pricing:resolve:query'">试算 </a-button>
          <a-button v-if="hasPermission('scm:pricing:resolve:query') && hasPermission('scm:report:cost:query')" :loading="trialLoading" @click="trial"
            >查看计算依据</a-button
          >
        </a-form-item>
      </a-row>
    </a-form>
    <a-alert v-if="error" :message="error" type="error" show-icon />
    <p v-if="result" class="resolve-meta">查询时间：{{ result.at }} · 客户类型：{{ result.customerTypeName }}</p>
    <a-table
      :columns="columns"
      :data-source="result?.items || []"
      row-key="skuId"
      :loading="loading"
      :pagination="false"
      size="small"
      bordered
      :scroll="{x: scmColumnsWidth(columns)}"
    >
      <template #bodyCell="{record, column}">
        <template v-if="column.dataIndex === 'specName'">{{ record.specName || '—' }}</template>
        <template v-else-if="column.dataIndex === 'skuCode'">
          <span class="scm-mono">{{ record.skuCode || '—' }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'unitPrice'">
          <!-- 本页要回答的就是「最终多少钱」：这个数字必须是全表最重的 -->
          <span :class="record.priceStatus === 'PRICED' ? 'final-price' : 'final-price final-price--muted'">
            {{ formatAmount(record.unitPrice) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'priceSource'">
          <ScmStatusTag :tone="record.priceSource ? PRICE_SOURCE_TONE[record.priceSource] : 'neutral'" :label="sourceLabel(record.priceSource)" />
        </template>
        <template v-else-if="column.dataIndex === 'priceStatus'">
          <ScmStatusTag
            :tone="record.priceStatus === 'PRICED' ? 'success' : 'warning'"
            :label="record.priceStatus === 'PRICED' ? '已定价' : '未定价'"
          />
        </template>
        <template v-else-if="column.dataIndex === 'sellable'">
          <ScmStatusTag :tone="record.sellable ? 'success' : 'error'" :label="record.sellable ? '可售' : '不可售'" />
        </template>
        <template v-else-if="column.dataIndex === 'hint'">
          <span :class="record.sellable ? 'scm-cell-hint' : 'hint-alert'">{{ hintText(record) }}</span>
        </template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
    <a-modal :open="trialOpen" title="协议价计算依据" :footer="null" width="760px" @cancel="trialOpen = false">
      <a-table :data-source="trialRows" :columns="trialColumns" row-key="skuId" size="small" :pagination="false" style="margin-top: 12px">
        <template #bodyCell="{record, column}">
          <template v-if="column.dataIndex === 'skuId'">{{
            result?.items.find((item) => String(item.skuId) === String(record.skuId))?.skuCode || '—'
          }}</template>
          <template v-else-if="column.dataIndex === 'formula'">{{ formulaLabel(record.calculationBasis?.formulaType) }}</template>
          <template v-else-if="column.dataIndex === 'source'">{{ basisSourceLabel(record.calculationBasis?.basisSource) }}</template>
          <template v-else-if="column.dataIndex === 'basis'">{{ record.calculationBasis?.basisUnitPrice || '—' }}</template>
          <template v-else-if="column.dataIndex === 'parameter'">{{ record.calculationBasis?.formulaParameter || '—' }}</template>
          <template v-else-if="column.dataIndex === 'unitPrice'">{{ record.unitPrice || '—' }}</template>
          <template v-else-if="column.dataIndex === 'reason'">{{
            record.unpricedReason ? UNPRICED_REASON_ENUM[record.unpricedReason]?.desc || record.unpricedReason : '—'
          }}</template>
        </template>
      </a-table>
    </a-modal>
  </a-card>
</template>
<script setup lang="ts">
import {scmColumnsWidth} from '/@/views/business/scm/common/scm-column';
import {ref} from 'vue';
import {message, type TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import WarehouseSelect from '/@/components/business/scm/warehouse-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import {UNAVAILABLE_REASON_ENUM, UNPRICED_REASON_ENUM, SCM_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/pricing-const';
import type {ScmId} from '/@/types/business/scm/customer';
import type {ResolveResult, ResolvedPrice} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';
import {hasPermission} from '../common/scm-permission';

const customerId = ref<ScmId>(),
  skuIds = ref<ScmId[]>([]),
  at = ref<string>(),
  result = ref<ResolveResult>(),
  warehouseId = ref<ScmId>(),
  loading = ref(false),
  error = ref('');
let requestId = 0;
type TrialRow = {skuId: ScmId; unitPrice: string | null; unpricedReason: string | null; calculationBasis: Record<string, unknown> | null};
const trialOpen = ref(false),
  trialLoading = ref(false),
  trialRows = ref<TrialRow[]>([]);
const trialColumns: TableColumnsType<TrialRow> = [
  {title: '商品规格编码', dataIndex: 'skuId', width: 140},
  {title: '公式', dataIndex: 'formula', width: 150},
  {title: '基准来源', dataIndex: 'source', width: 150},
  {title: '基准单价', dataIndex: 'basis', width: 130},
  {title: '公式参数', dataIndex: 'parameter', width: 120},
  {title: '结果单价', dataIndex: 'unitPrice', width: 120},
  {title: '未定价原因', dataIndex: 'reason', width: 180},
];
function formulaLabel(value: unknown): string {
  return (
    (
      {
        FIXED: '固定协议价',
        MARKET_PLUS_AMOUNT: '市场价加固定金额',
        COST_MARKUP_RATE: '配送仓成本加价率',
        MARKET_MARKUP_RATE: '市场价加价率',
      } as Record<string, string>
    )[String(value)] || '未命中协议价'
  );
}
function basisSourceLabel(value: unknown): string {
  return (
    ({FIXED: '固定单价', MARKET_PRICE: '商品市场价', WAREHOUSE_WEIGHTED_AVG_COST: '配送仓移动加权成本'} as Record<string, string>)[String(value)] ||
    '—'
  );
}

async function trial() {
  if (!customerId.value || !skuIds.value?.length || !warehouseId.value) {
    void message.warning('请选择客户、商品规格和配送仓');
    return;
  }
  trialLoading.value = true;
  try {
    const response = await pricingApi.trial({
      customerId: customerId.value,
      skuIds: skuIds.value,
      warehouseId: warehouseId.value,
      at: at.value || null,
    });
    trialRows.value = response.data;
    trialOpen.value = true;
  } catch (cause) {
    void message.error(pricingError(cause));
  } finally {
    trialLoading.value = false;
  }
}

/**
 * 来源的语义档位：协议价最具体（蓝）、类型价次之（绿）、市场价是兜底（灰）。
 * 来源不是「状态」，但它正是本页第二个要突出的信息 —— 只看价格不看来源，
 * 用户无法判断这个价是不是专门给自己谈的。
 */
const PRICE_SOURCE_TONE: Record<string, ScmStatusTone> = {
  AGREEMENT: 'processing',
  CUSTOMER_TYPE: 'success',
  TIME_PRICE: 'warning',
  MARKET: 'neutral',
};

/**
 * 列只留「什么货 / 多少钱 / 这个价哪来的 / 能不能卖 / 为什么」。
 *
 * 刻意不展示 `sourceRecordId`：那是命中的那条价格记录的数据库主键，
 * 对使用者没有信息量，需要追溯时走价格历史页。
 */
const columns: TableColumnsType<ResolvedPrice> = [
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'specName', width: 170},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 140},
  {title: '最终价格', dataIndex: 'unitPrice', width: 148},
  {title: '价格来源', dataIndex: 'priceSource', width: 150},
  {title: '价格状态', dataIndex: 'priceStatus', align: 'center', width: 96},
  {title: '可售', dataIndex: 'sellable', width: 110},
  {title: '说明', dataIndex: 'hint', width: 220},
];

function sourceLabel(value: string | null) {
  return value ? SCM_PRICE_SOURCE_ENUM[value]?.desc || value : '—';
}

function reasonLabel(value: string | null) {
  return value ? UNAVAILABLE_REASON_ENUM[value]?.desc || value : '—';
}

/** 说明列：不可售原因优先（它挡着不能卖），否则给缺价原因，都正常才是破折号。 */
function hintText(record: ResolvedPrice): string {
  if (record.unavailableReason) return reasonLabel(record.unavailableReason);
  if (record.unpricedReason) return UNPRICED_REASON_ENUM[record.unpricedReason]?.desc || record.unpricedReason;
  return '—';
}

async function resolve() {
  if (customerId.value == null || !skuIds.value?.length) {
    message.warning('请选择客户和商品规格');
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  result.value = undefined;
  try {
    // 试算失败由页内 Alert 承担（不提示会被读成「没有价格」），不再让全局 toast 重复说一遍
    const r = await pricingApi.resolve(
      {
        customerId: customerId.value,
        skuIds: skuIds.value,
        at: at.value || null,
        warehouseId: warehouseId.value,
      },
      {suppressGlobalErrorMessage: true}
    );
    if (id === requestId) result.value = r.data;
  } catch (e) {
    if (id === requestId) error.value = pricingError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}
</script>
<style scoped>
/* 最终价格是全表最重的数字；未定价时退成次要色，避免「一个灰色大数字」被误读成价格 */
.final-price {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.final-price--muted {
  font-weight: 400;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}

.hint-alert {
  color: var(--scm-error, #ff4d4f);
}

.resolve-meta {
  margin: 12px 0;
  color: var(--scm-text-secondary, rgba(0, 0, 0, 0.45));
}
</style>
