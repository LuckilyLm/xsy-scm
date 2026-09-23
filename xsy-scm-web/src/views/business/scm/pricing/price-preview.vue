<!-- W3 新能力：取价试算。价格状态与可售状态独立展示（Q2）。 -->
<template>
 <a-card title="取价试算" size="small" :bordered="false">
  <a-form layout="inline" class="smart-query-form">
   <a-row class="smart-query-form-row">
    <a-form-item label="客户" required class="smart-query-form-item"><CustomerSelect v-model:value="customerId" width="220px" /></a-form-item>
    <a-form-item label="SKU" required class="smart-query-form-item"><SkuSelect v-model:value="skuIds" mode="multiple" width="380px" :disabled-statuses="[]" /></a-form-item>
    <a-form-item label="时点" class="smart-query-form-item"><a-date-picker v-model:value="at" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="当前时点" /></a-form-item>
    <a-form-item class="smart-query-form-item"><a-button type="primary" @click="resolve" :loading="loading" v-privilege="'scm:pricing:resolve:query'">试算</a-button></a-form-item>
   </a-row>
  </a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <a-alert message="已定价的商品也可能不可售。零价是有效价格；未定价不等于零价。" type="info" show-icon />
  <p v-if="result">解析时点：{{result.at}} · 客户类型：{{result.customerTypeName}}</p>
  <a-table :columns="columns" :data-source="result?.items||[]" row-key="skuId" :loading="loading" :pagination="false" size="small" bordered :scroll="{x:1350}">
   <template #bodyCell="{record,column}">
    <template v-if="column.dataIndex==='unitPrice'"><span class="amount">{{formatAmount(record.unitPrice)}}</span></template>
    <template v-else-if="column.dataIndex==='priceStatus'"><a-tag :color="record.priceStatus==='PRICED'?'green':'default'">{{record.priceStatus==='PRICED'?'已定价':'未定价'}}</a-tag></template>
    <template v-else-if="column.dataIndex==='sellable'"><a-tag :color="record.sellable?'green':'red'">{{record.sellable?'可售':'不可售'}}</a-tag></template>
    <template v-else-if="column.dataIndex==='priceSource'">{{sourceLabel(record.priceSource)}}</template>
    <template v-else-if="column.dataIndex==='unpricedReason'">{{record.unpricedReason?'无可用价格来源':'—'}}</template>
    <template v-else-if="column.dataIndex==='unavailableReason'">{{reasonLabel(record.unavailableReason)}}</template>
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
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import {UNAVAILABLE_REASON_ENUM, SCM_PRICE_SOURCE_ENUM} from '/@/constants/business/scm/pricing-const';
import type {ScmId} from '/@/types/business/scm/customer';
import type {ResolveResult, ResolvedPrice} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';

const customerId = ref<ScmId>(), skuIds = ref<ScmId[]>([]), at = ref<string>(), result = ref<ResolveResult>(),
    loading = ref(false), error = ref('');
let requestId = 0;
const columns: TableColumnsType<ResolvedPrice> = [{title: 'SKU 编码', dataIndex: 'skuCode', width: 150}, {
  title: '商品',
  dataIndex: 'productName',
  width: 150
}, {title: '规格', dataIndex: 'specName', width: 100}, {
  title: '单价',
  dataIndex: 'unitPrice',
  align: 'right',
  width: 120
}, {title: '价格状态', dataIndex: 'priceStatus', width: 100}, {
  title: '来源',
  dataIndex: 'priceSource',
  width: 120
}, {title: '来源记录', dataIndex: 'sourceRecordId', width: 100}, {
  title: '可售状态',
  dataIndex: 'sellable',
  width: 100
}, {title: '不可售原因', dataIndex: 'unavailableReason', width: 160}, {
  title: '缺价原因',
  dataIndex: 'unpricedReason',
  width: 160
}];

function sourceLabel(value: string | null) {
  return value ? SCM_PRICE_SOURCE_ENUM[value]?.desc || value : '—';
}

function reasonLabel(value: string | null) {
  return value ? UNAVAILABLE_REASON_ENUM[value]?.desc || value : '—';
}

async function resolve() {
  if (customerId.value == null || !skuIds.value?.length) {
    error.value = '请选择客户和 SKU';
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
<style scoped>.amount {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

p {
  margin: 12px 0;
}</style>
