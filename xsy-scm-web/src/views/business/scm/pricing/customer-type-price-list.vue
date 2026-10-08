<template>
 <a-form class="smart-query-form" layout="inline">
  <a-row class="smart-query-form-row">
   <a-form-item label="关键字" class="smart-query-form-item"><a-input v-model:value="query.keyword" placeholder="名称或编码" allow-clear /></a-form-item>
   <a-form-item label="客户类型" class="smart-query-form-item"><CustomerTypeSelect v-model:value="query.customerTypeId" width="190px" /></a-form-item>
   <a-form-item label="商品规格" class="smart-query-form-item"><SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]" /></a-form-item>
   <a-form-item label="有效区间" class="smart-query-form-item"><a-range-picker v-model:value="range" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[true,true]" /></a-form-item>
   <a-form-item class="smart-query-form-item"><a-space><a-button type="primary" @click="search" v-privilege="'scm:pricing:type-price:query'">查询</a-button><a-button @click="reset">重置</a-button></a-space></a-form-item>
  </a-row>
 </a-form>
 <a-card size="small" :bordered="false">
  <a-row class="smart-table-btn-block"><div class="smart-table-operate-block"><a-button type="primary" v-privilege="'scm:pricing:type-price:add'" @click="drawer?.open()">新增客户类型价</a-button><a-button v-privilege="'scm:pricing:type-price:batch'" @click="router.push('/pricing/customer-type-price-batch')">批量调价</a-button></div><div class="smart-table-setting-block"><TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_TYPE_PRICE" :refresh="load" /></div></a-row>
  <a-table :data-source="rows" :columns="columns" row-key="customerTypePriceId" size="small" bordered :loading="loading" :pagination="false" :scroll="{x:1390}">
   <template #bodyCell="{record,column}">
    <template v-if="column.dataIndex==='customerTypeName'"><span>{{record.customerTypeName || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='customerTypeCode'"><span class="scm-mono">{{record.customerTypeCode || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='specName'"><span>{{record.specName || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='skuCode'"><span class="scm-mono">{{record.skuCode || '—'}}</span></template>
    <template v-else-if="column.dataIndex==='unitPrice'"><span class="scm-money">{{formatAmount(record.unitPrice)}}</span></template>
    <template v-else-if="column.dataIndex==='effectiveFrom'">{{datetime(record.effectiveFrom)}}</template>
    <template v-else-if="column.dataIndex==='effectiveTo'">{{ record.effectiveTo ? datetime(record.effectiveTo) : '长期有效' }}</template>
    <ScmStatusTag v-else-if="column.dataIndex==='effectiveness'" v-bind="effectiveness(record)" />
    <template v-else-if="column.dataIndex==='action'"><div class="smart-table-operate scm-table-actions"><a-button type="link" v-privilege="'scm:pricing:type-price:update'" @click="drawer?.open(record.customerTypePriceId)">编辑</a-button><a-button type="link" danger v-privilege="'scm:pricing:type-price:delete'" @click="remove(record)">删除</a-button></div></template>
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
import {useRouter} from 'vue-router';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import type {PriceQuery, PriceRow} from '/@/types/business/scm/pricing';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {formatAmount} from '/@/utils/scm-amount';
import {datetime} from '../common/scm-display';
import {useScmErrorToast} from '../common/scm-error-toast';
import ScmStatusTag from '/@/components/business/scm/scm-status-tag/index.vue';
import {pricingError} from './pricing-errors';
import PriceDrawer from './components/customer-type-price-form-drawer.vue';
import {PRICE_EFFECTIVENESS, priceEffectiveness} from './pricing-display';

const router = useRouter();
const api = pricingApi.typePrice;
const query = reactive<PriceQuery>({pageNum: 1, pageSize: 20});
const range = ref<[string, string] | undefined>();
const rows = ref<PriceRow[]>([]), total = ref(0), loading = ref(false);
const error = useScmErrorToast();
const drawer = ref<InstanceType<typeof PriceDrawer>>();
let requestId = 0;
// 一列一个值：类型编码与商品规格编码各自成列，生效与失效时间也各自成列。
const columns = ref<TableColumnsType<PriceRow>>([
  {title: '客户类型', dataIndex: 'customerTypeName', width: 150},
  {title: '客户类型编码', dataIndex: 'customerTypeCode', width: 140},
  {title: '商品', dataIndex: 'productName', width: 150},
  {title: '商品规格', dataIndex: 'specName', width: 170},
  {title: '商品规格编码', dataIndex: 'skuCode', width: 140},
  {title: '单价', dataIndex: 'unitPrice', align: 'right', width: 120},
  {title: '生效时间', dataIndex: 'effectiveFrom', width: 170},
  {title: '失效时间', dataIndex: 'effectiveTo', width: 130},
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
  query.customerTypeId = undefined;
  query.skuId = undefined;
  range.value = undefined;
  search();
}

function remove(row: PriceRow) {
  Modal.confirm({
    title: '删除这条客户类型价？', content: '删除后保留价格变更记录。', onOk: async () => {
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
