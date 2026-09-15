<!-- W3 新写，参照 legacy 批量调价行为；整批提交，逐行错误。 -->
<template>
 <a-card title="客户类型价批量调价" size="small" :bordered="false">
  <a-alert type="info" show-icon message="任意一行失败，整批价格不会写入。最多 500 行；成功批次号不可重复使用。" />
  <a-form layout="inline" class="smart-query-form"><a-form-item label="批次号" required><a-input v-model:value="batchKey" aria-label="批次号" :maxlength="100" style="width:320px" /></a-form-item><a-form-item><a-button @click="add" :disabled="rows.length>=500||saving">新增行</a-button></a-form-item><a-form-item><a-button type="primary" :loading="saving" v-privilege="'scm:pricing:type-price:batch'" @click="submit">提交整批</a-button></a-form-item></a-form>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <a-table :data-source="rows" :columns="columns" row-key="rowNumber" :pagination="false" size="small" bordered :scroll="{x:1300}" :row-class-name="(r:BatchRow)=>failures.some(f=>f.rowNumber===r.rowNumber)?'failed-row':''">
   <template #bodyCell="{record,column}">
    <template v-if="column.dataIndex==='customerTypeId'"><CustomerTypeSelect v-model:value="record.customerTypeId" width="180px" /></template>
    <template v-else-if="column.dataIndex==='skuId'"><SkuSelect v-model:value="record.skuId" width="260px" /></template>
    <template v-else-if="column.dataIndex==='unitPrice'"><a-input v-model:value="record.unitPrice" aria-label="单价" inputmode="decimal" /></template>
    <template v-else-if="column.dataIndex==='effectiveFrom'"><a-range-picker :value="[record.effectiveFrom,record.effectiveTo||'']" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" :allow-empty="[false,true]" @change="(v:unknown)=>setPeriod(record,v)" /></template>
    <template v-else-if="column.dataIndex==='error'"><span class="row-error">{{failures.filter(f=>f.rowNumber===record.rowNumber).map(f=>f.message).join('；')}}</span></template>
    <template v-else-if="column.dataIndex==='action'"><a-button type="link" danger :disabled="saving" @click="rows=rows.filter(r=>r.rowNumber!==record.rowNumber)">移除</a-button></template>
   </template>
  </a-table>
 </a-card>
</template>
<script setup lang="ts">
import {ref} from 'vue';
import {message} from 'ant-design-vue';
import type {TableColumnsType} from 'ant-design-vue';
import {useRouter} from 'vue-router';
import dayjs from 'dayjs';
import CustomerTypeSelect from '/@/components/business/scm/customer-type-select/index.vue';
import SkuSelect from '/@/components/business/scm/sku-select/index.vue';
import {pricingApi} from '/@/api/business/scm/pricing-api';
import type {BatchRow} from '/@/types/business/scm/pricing';
import {emptyPrice,validateBatch} from './pricing-form-model';
import {pricingError} from './pricing-errors';
const router=useRouter(),batchKey=ref('PRICE-'+dayjs().format('YYYYMMDD-HHmmss')),rows=ref<BatchRow[]>([]),failures=ref<{rowNumber:number;message:string}[]>([]),saving=ref(false),error=ref('');let number=0;
const columns:TableColumnsType<BatchRow>=[{title:'行号',dataIndex:'rowNumber',width:65},{title:'客户类型',dataIndex:'customerTypeId',width:200},{title:'SKU',dataIndex:'skuId',width:280},{title:'单价',dataIndex:'unitPrice',width:140},{title:'有效区间',dataIndex:'effectiveFrom',width:400},{title:'错误',dataIndex:'error',width:230},{title:'操作',dataIndex:'action',width:80}];
function add(){rows.value.push({...emptyPrice(),rowNumber:++number});}
function setPeriod(row:BatchRow,value:unknown){const v=value as string[]|null;row.effectiveFrom=v?.[0]||'';row.effectiveTo=v?.[1]||null;}
async function submit(){error.value='';failures.value=validateBatch(rows.value);if(!batchKey.value.trim()||!rows.value.length){error.value='请填写批次号并至少添加一行';return;}if(failures.value.length){error.value=`${failures.value.length} 项校验失败，整批未提交`;return;}saving.value=true;try{const r=await pricingApi.batch({batchKey:batchKey.value,rows:rows.value});if(!r.data.committed){failures.value=r.data.failures;error.value=`${r.data.failures.length} 项错误，整批未写入`;}else{message.success(`已提交 ${r.data.rowCount} 条价格`);router.push('/pricing/customer-type-price-list');}}catch(e){error.value=pricingError(e);}finally{saving.value=false;}}
add();
</script>
<style scoped>.row-error{color:var(--ant-color-error);} :deep(.failed-row td){background:var(--ant-color-error-bg,#fff2f0);}</style>
