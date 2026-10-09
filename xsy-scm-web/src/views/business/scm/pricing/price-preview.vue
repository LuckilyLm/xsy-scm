<!--  新能力：取价试算。价格状态与可售状态独立展示。 -->
<template>
  <a-card size="small" :bordered="false">
    <template #title>
      取价试算
      <ScmFieldHelp label="取价试算" text="0 元也是有效价格"/>
    </template>
    <a-form layout="inline" class="smart-query-form">
      <a-row class="smart-query-form-row">
        <a-form-item label="客户" required class="smart-query-form-item">
          <CustomerSelect v-model:value="customerId" width="220px"/>
        </a-form-item>
        <a-form-item label="商品规格" required class="smart-query-form-item">
          <SkuSelect v-model:value="skuIds" mode="multiple" width="380px" :disabled-statuses="[]"/>
        </a-form-item>
        <a-form-item label="时点" class="smart-query-form-item">
          <a-date-picker v-model:value="at" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="当前时点"/>
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-button type="primary" @click="resolve" :loading="loading" v-privilege="'scm:pricing:resolve:query'">试算
          </a-button>
        </a-form-item>
      </a-row>
    </a-form>
    <a-alert v-if="error" :message="error" type="error" show-icon/>
    <p v-if="result" class="resolve-meta">
      解析时点：{{ result.at }} · 客户类型：{{ result.customerTypeName }}
    </p>
    <a-table :columns="columns" :data-source="result?.items||[]" row-key="skuId" :loading="loading"
             :pagination="false" size="small" bordered :scroll="{x:1100}">
      <template #bodyCell="{record,column}">
        <template v-if="column.dataIndex==='sku'">
          <!-- 规格名作主行、编码作次要行：编码只是核对用的，不该和名称抢同一行 -->
          <div class="scm-cell-stack">
            <span class="scm-cell-stack__main">{{ record.specName || '—' }}</span>
            <span v-if="record.skuCode" class="scm-cell-stack__sub">{{ record.skuCode }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex==='unitPrice'">
          <!-- 本页要回答的就是「最终多少钱」：这个数字必须是全表最重的 -->
          <span :class="record.priceStatus==='PRICED' ? 'final-price' : 'final-price final-price--muted'">
            {{ formatAmount(record.unitPrice) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex==='priceSource'">
          <ScmStatusTag
              :tone="record.priceSource ? PRICE_SOURCE_TONE[record.priceSource] : 'neutral'"
              :label="sourceLabel(record.priceSource)"
          />
        </template>
        <template v-else-if="column.dataIndex==='priceStatus'">
          <ScmStatusTag :tone="record.priceStatus==='PRICED' ? 'success' : 'warning'"
                        :label="record.priceStatus==='PRICED' ? '已定价' : '未定价'"/>
        </template>
        <template v-else-if="column.dataIndex==='sellable'">
          <ScmStatusTag :tone="record.sellable ? 'success' : 'error'" :label="record.sellable ? '可售' : '不可售'"/>
        </template>
        <template v-else-if="column.dataIndex==='hint'">
          <span :class="record.sellable ? 'scm-cell-hint' : 'hint-alert'">{{ hintText(record) }}</span>
        </template>
        <template v-else>{{record[column.dataIndex]??'—'}}</template>
      </template>
    </a-table>
  </a-card>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import ScmFieldHelp from '/@/components/business/scm/scm-field-help.vue';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import {UNAVAILABLE_REASON_ENUM, SCM_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/pricing-const';
import type {ScmId} from '/@/types/business/scm/customer';
import type {ResolveResult, ResolvedPrice} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';

const customerId = ref<ScmId>(), skuIds = ref<ScmId[]>([]), at = ref<string>(), result = ref<ResolveResult>(),
    loading = ref(false), error = ref('');
let requestId = 0;

/**
 * 来源的语义档位：协议价最具体（蓝）、类型价次之（绿）、市场价是兜底（灰）。
 * 来源不是「状态」，但它正是本页第二个要突出的信息 —— 只看价格不看来源，
 * 用户无法判断这个价是不是专门给自己谈的。
 */
const PRICE_SOURCE_TONE: Record<string, ScmStatusTone> = {
  AGREEMENT: 'processing',
  CUSTOMER_TYPE: 'success',
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
  {title: '商品规格', dataIndex: 'sku', width: 200},
  {title: '最终价格', dataIndex: 'unitPrice', align: 'right', width: 160},
  {title: '价格来源', dataIndex: 'priceSource', width: 150, align: 'center'},
  {title: '价格状态', dataIndex: 'priceStatus', align: 'center', width: 110},
  {title: '可售', dataIndex: 'sellable', align: 'center', width: 110},
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
  if (record.unpricedReason) return reasonLabel(record.unpricedReason);
  return '—';
}

async function resolve() {
  if (customerId.value == null || !skuIds.value?.length) {
    error.value = '请选择客户和商品规格';
    return;
  }
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  result.value = undefined;
  try {
    const r = await pricingApi.resolve({customerId: customerId.value, skuIds: skuIds.value, at: at.value || null});
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
