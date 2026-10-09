<template>
 <a-form class="smart-query-form" layout="inline">
  <a-row class="smart-query-form-row">
   <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="query.keyword" placeholder="名称或编码" allow-clear /></a-form-item>
   <a-form-item label="客户" class="smart-query-form-item"><CustomerSelect v-model:value="query.customerId" width="190px" /></a-form-item>
   <a-form-item label="商品规格" class="smart-query-form-item"><SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]" /></a-form-item>
   <a-form-item label="有效区间" class="smart-query-form-item"><a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DD HH:mm:ss" :allow-empty="[true,true]" /></a-form-item>
   <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" @click="search" v-privilege="'scm:pricing:agreement:query'">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
  </a-row>
 </a-form>
 <a-alert v-if="error" :message="error" type="error" show-icon closable @close="error=''" />
 <a-card size="small" :bordered="false">
  <a-row class="smart-table-btn-block"><div class="smart-table-operate-block"><a-button type="primary" v-privilege="'scm:pricing:agreement:add'" @click="drawer?.open()">新增客户协议价</a-button></div><div class="smart-table-setting-block"><TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_AGREEMENT" :refresh="load" /></div></a-row>
  <a-table :data-source="rows" :columns="columns" row-key="agreementPriceId" size="small" bordered :loading="loading" :pagination="false" :scroll="{x:1250}">
   <template #bodyCell="{record,column}">
    <template v-if="column.dataIndex==='customerName'">{{record.customerName || '—'}}</template>
    <template v-else-if="column.dataIndex==='customerCode'"><span class="scm-mono">{{record.customerCode || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='skuCode'"><span class="scm-mono">{{record.skuCode || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='unitPrice'"><span class="scm-money">{{formatAmount(record.unitPrice)}}</span></template>
    <template v-else-if="column.dataIndex==='effectiveRange'">{{effectiveRangeText(record.effectiveFrom, record.effectiveTo)}}<span v-if="!record.effectiveTo" class="scm-cell-hint">（长期有效）</span></template>
    <ScmStatusTag v-else-if="column.dataIndex==='effectiveness'" v-bind="effectiveness(record)" />
    <template v-else-if="column.dataIndex==='action'"><div class="smart-table-operate scm-table-actions"><a-button type="link" v-privilege="'scm:pricing:agreement:update'" @click="drawer?.open(record.agreementPriceId)">编辑</a-button><a-button type="link" danger v-privilege="'scm:pricing:agreement:delete'" @click="remove(record)">删除</a-button></div></template>
   </template>
  </a-table>
  <div class="smart-query-table-page"><a-pagination v-model:current="query.pageNum" v-model:page-size="query.pageSize" :total="total" show-size-changer show-quick-jumper @change="load" :show-total="(n:number)=>`共 ${n} 条`" /></div>
 </a-card>
 <PriceDrawer ref="drawer" @saved="load" />
</template>
<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue';
import {Modal} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import type {PriceQuery, PriceRow} from '/@/types/business/scm/pricing';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {formatAmount} from '/@/utils/scm-amount';
import {pricingError} from './pricing-errors';
import {effectiveRangeText, PRICE_EFFECTIVENESS, priceEffectiveness} from './pricing-display';
import PriceDrawer from './components/agreement-price-form-drawer.vue';

const api = pricingApi.agreement;
const query = reactive<PriceQuery>({pageNum: 1, pageSize: 20});
const range = ref<[string, string] | undefined>();
const rows = ref<PriceRow[]>([]), total = ref(0), loading = ref(false), error = ref('');
const drawer = ref<InstanceType<typeof PriceDrawer>>();
let requestId = 0;
// 编码独立成列并默认收起；更新时间由「价格变更历史」的变更时间承担，不默认摊在列表上。
// 客户编码与商品规格编码仍可用「关键字」筛出来（后端 keyword 同时匹配名称与两类编码）。
type AgreementPriceColumn = TableColumnsType<PriceRow>[number] & {showFlag?: boolean};
const columns = ref<AgreementPriceColumn[]>([
  {title: '客户', dataIndex: 'customerName', width: 180},
  {title: '客户编码', dataIndex: 'customerCode', width: 120, showFlag: false},
  {title: '商品', dataIndex: 'productName', width: 180},
  {title: '商品规格', dataIndex: 'specName', width: 200},
  {title: '规格编码', dataIndex: 'skuCode', width: 130, showFlag: false},
  {title: '协议价', dataIndex: 'unitPrice', align: 'right', width: 130},
  {title: '有效期', dataIndex: 'effectiveRange', width: 340},
  {title: '状态', dataIndex: 'effectiveness', align: 'center', width: 100},
  {title: '操作', dataIndex: 'action', align: 'center', fixed: 'right', width: 120},
]);

/** 生效状态由前端按有效区间推导（VO 不带状态字段），只用于展示。 */
function effectiveness(row: PriceRow) {
  return PRICE_EFFECTIVENESS[priceEffectiveness(row.effectiveFrom, row.effectiveTo)];
}

async function load() {
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const r = await api.query({
      ...query,
      effectiveFrom: range.value?.[0] || null,
      effectiveTo: range.value?.[1] || null
    });
    if (id === requestId) {
      rows.value = r.data.list;
      total.value = r.data.total;
    }
  } catch (e) {
    if (id === requestId) error.value = pricingError(e);
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  load();
}

function reset() {
  query.keyword = undefined;
  query.customerId = undefined;
  query.skuId = undefined;
  range.value = undefined;
  search();
}

function remove(row: PriceRow) {
  Modal.confirm({
    title: '删除这条客户协议价？', content: '删除后保留价格变更记录。', onOk: async () => {
      try {
        await api.delete(row);
        await load();
      } catch (e) {
        error.value = pricingError(e);
        throw e;
      }
    }
  });
}

onMounted(load);
</script>
