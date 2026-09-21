<!-- W3 新写：双来源价格历史与字段级前后对照。 -->
<template>
 <a-form layout="inline" class="smart-query-form" @finish="search">
  <a-row class="smart-query-form-row">
  <a-form-item label="来源" class="smart-query-form-item"><a-select v-model:value="query.source" allow-clear style="width:150px" :options="[{value:'AGREEMENT',label:'客户协议价'},{value:'CUSTOMER_TYPE',label:'客户类型价'}]" /></a-form-item>
  <a-form-item label="客户" class="smart-query-form-item"><CustomerSelect v-model:value="query.customerId" width="180px" /></a-form-item>
  <a-form-item label="客户类型" class="smart-query-form-item"><CustomerTypeSelect v-model:value="query.customerTypeId" width="150px" /></a-form-item>
  <a-form-item label="SKU" class="smart-query-form-item"><SkuSelect v-model:value="query.skuId" width="230px" :disabled-statuses="[]" /></a-form-item>
  <a-form-item label="操作" class="smart-query-form-item"><a-select v-model:value="query.operationType" allow-clear style="width:110px" :options="[{value:'CREATE',label:'新增'},{value:'UPDATE',label:'更新'},{value:'DELETE',label:'删除'}]" /></a-form-item>
  <a-form-item label="有效区间" class="smart-query-form-item"><a-range-picker v-model:value="effective" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[true,true]" /></a-form-item>
  <a-form-item label="操作区间" class="smart-query-form-item"><a-range-picker v-model:value="operated" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[true,true]" /></a-form-item>
  <a-form-item class="smart-query-form-item"><a-button type="primary" html-type="submit" v-privilege="'scm:pricing:history:query'">查询</a-button></a-form-item>
  </a-row>
 </a-form>
 <a-alert v-if="error" type="error" :message="error" />
 <a-card size="small" :bordered="false"><TableOperator v-model="columns" :table-id="TABLE_ID_CONST.BUSINESS.SCM_PRICING_HISTORY" :refresh="load" />
  <a-table :data-source="rows" :columns="columns" :row-key="(r:HistoryRow)=>`${r.source}-${r.historyId}`" :loading="loading" :pagination="false" size="small" bordered :scroll="{x:1550}">
   <template #bodyCell="{record,column}"><template v-if="column.dataIndex==='currentUnitPrice'">{{formatAmount(record.currentUnitPrice)}}</template><template v-else-if="column.dataIndex==='currentDeleted'"><a-tag v-if="record.currentDeleted" color="red">已删除</a-tag><span v-else>有效记录</span></template><template v-else-if="column.dataIndex==='action'"><a-button type="link" @click="selected=record">变更详情</a-button></template><template v-else>{{record[column.dataIndex]??'—'}}</template></template>
  </a-table><div class="smart-query-table-page"><a-pagination v-model:current="query.pageNum" v-model:page-size="query.pageSize" :total="total" show-size-changer @change="load" /></div>
 </a-card>
 <a-modal title="价格变更详情" :open="!!selected" :footer="null" :width="760" @cancel="selected=undefined"><ScmDiffTable :before="selected?.beforeData" :after="selected?.afterData" /></a-modal>
</template>
<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue';
import type {TableColumnsType} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import TableOperator from '/@/components/support/table-operator/index.vue';
import {TABLE_ID_CONST} from '/@/constants/support/table-id-const';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import {formatAmount} from '/@/utils/scm-amount';
import type {PriceQuery,HistoryRow} from '/@/types/business/scm/pricing';
import {pricingError} from './pricing-errors';
import ScmDiffTable from '/@/views/business/scm/common/scm-diff-table.vue';
import { datetime } from '../common/scm-display';
const query=reactive<PriceQuery&{source?:string;operationType?:string}>({pageNum:1,pageSize:20}),effective=ref<[string,string]>(),operated=ref<[string,string]>(),rows=ref<HistoryRow[]>([]),total=ref(0),loading=ref(false),error=ref(''),selected=ref<HistoryRow>();let requestId=0;
const columns=ref<TableColumnsType<HistoryRow>>([{title:'来源',dataIndex:'source',width:140},{title:'客户',dataIndex:'customerName',width:140},{title:'客户类型',dataIndex:'customerTypeName',width:130},{title:'SKU 编码',dataIndex:'skuCode',width:150},{title:'商品',dataIndex:'productName',width:140},{title:'操作',dataIndex:'operationType',width:90},{title:'操作人',dataIndex:'operator',width:100},{title:'操作时间',dataIndex:'operatedAt',width:210, customRender: ({ text }) => datetime(text) },{title:'当前价格',dataIndex:'currentUnitPrice',align:'right',width:120},{title:'当前生效',dataIndex:'currentEffectiveFrom',width:200, customRender: ({ text }) => datetime(text) },{title:'当前结束',dataIndex:'currentEffectiveTo',width:200, customRender: ({ text }) => datetime(text) },{title:'当前记录',dataIndex:'currentDeleted',width:100},{title:'详情',dataIndex:'action',fixed:'right',width:110}]);
async function load(){const id=++requestId;loading.value=true;error.value='';try{const r=await pricingApi.history({...query,effectiveFrom:effective.value?.[0]||null,effectiveTo:effective.value?.[1]||null,operatedFrom:operated.value?.[0]||null,operatedTo:operated.value?.[1]||null});if(id===requestId){rows.value=r.data.list;total.value=r.data.total;}}catch(e){if(id===requestId)error.value=pricingError(e);}finally{if(id===requestId)loading.value=false;}}
function search(){query.pageNum=1;load();}onMounted(load);
</script>
