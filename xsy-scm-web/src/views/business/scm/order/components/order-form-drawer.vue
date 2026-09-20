<template>
 <a-drawer :title="form.orderId?'编辑销售订单':'新建销售订单'" :open="visible" width="min(1200px, 96vw)" @close="visible=false">
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <a-spin :spinning="loading"><a-form :model="form" layout="vertical">
   <a-row :gutter="20"><a-col :span="12"><a-form-item label="客户" name="customerId" required><CustomerSelect v-if="!form.orderId" v-model:value="form.customerId" @change="customerChanged"/><a-input v-else :value="form.customerNameSnapshot" disabled/></a-form-item></a-col>
   <a-col :span="12"><a-form-item label="订单来源" name="orderSource"><a-select v-model:value="form.orderSource" :options="[{value:'ADMIN',label:'后台录单'},{value:'SUPPLEMENT',label:'补单'}]" style="width:100%"/></a-form-item></a-col>
   <a-col :span="12" v-if="form.orderSource==='SUPPLEMENT'"><a-form-item label="关联原订单" name="originalOrderId"><a-select v-model:value="form.originalOrderId" show-search allow-clear :filter-option="false" :options="originals" @search="loadOriginals" placeholder="搜索已确认订单号"/></a-form-item></a-col>
   <a-col :span="12" v-if="form.orderSource==='SUPPLEMENT'"><a-form-item label="补单原因" name="supplementReason" required><a-input v-model:value="form.supplementReason" maxlength="500"/></a-form-item></a-col>
   <a-col :span="12"><a-form-item label="期望配送时间" name="expectDeliveryTime"><a-date-picker v-model:value="form.expectDeliveryTime" show-time value-format="YYYY-MM-DDTHH:mm:ssZ" style="width:100%"/></a-form-item></a-col>
   <a-col :span="12"><a-form-item label="备注" name="remark"><a-input v-model:value="form.remark" maxlength="500"/></a-form-item></a-col>
   <a-col :span="12"><a-form-item label="收货人" name="receiverName" required><a-input v-model:value="form.address.receiverName" :disabled="!!form.orderId" maxlength="100"/></a-form-item></a-col>
   <a-col :span="12"><a-form-item label="联系电话" name="receiverPhone" required><a-input v-model:value="form.address.receiverPhone" :disabled="!!form.orderId" maxlength="32"/></a-form-item></a-col>
   <a-col :span="24"><a-form-item label="收货地址" name="address" required><a-input v-model:value="form.address.address" :disabled="!!form.orderId" maxlength="500"/></a-form-item></a-col></a-row>
   <ItemTable :items="form.items" @price="preview"/><a-button @click="preview" :loading="pricing">重新解析价格</a-button>
  </a-form></a-spin>
  <template #footer><a-space><a-button @click="visible=false">关闭</a-button><a-button v-if="!form.orderId" :loading="saving" :disabled="loading" @click="save(true)">保存草稿</a-button><a-button type="primary" :loading="saving" :disabled="loading" @click="save(false)">{{form.orderId?'保存修改':'创建订单'}}</a-button></a-space></template>
 </a-drawer>
</template>
<script setup lang="ts">
import {ref} from 'vue';import {message} from 'ant-design-vue';
import CustomerSelect from '/@/components/business/scm/customer-select/index.vue';
import {customerApi} from '/@/api/business/scm/customer-api';import {orderApi} from '/@/api/business/scm/order-api';
import {newOrder,payload,validateOrder,fixed} from '../order-form-model';import {orderError} from '../order-errors';import type {Order,Id} from '../order-types';import ItemTable from './order-item-editable-table.vue';
const emit=defineEmits<{saved:[]}>();const form=ref<Order>(newOrder()),visible=ref(false),loading=ref(false),saving=ref(false),pricing=ref(false),error=ref(''),originals=ref<{value:Id;label:string}[]>([]);let requestId=0;
async function open(id?:Id){requestId++;error.value='';form.value=newOrder();visible.value=true;if(id){loading.value=true;try{form.value=(await orderApi.detail(id)).data;form.value.items.forEach(i=>{i.unitPrice=i.manualPriceOverride?i.draftUnitPrice:null;i.overrideReason=i.manualPriceReason;});}catch(e){error.value=orderError(e);}finally{loading.value=false;}}}
async function customerChanged(id?:Id){if(!id)return;const current=++requestId;try{const c=(await customerApi.detail(id)).data;if(current!==requestId)return;form.value.address={receiverName:c.contactName??'',receiverPhone:c.contactPhone??'',address:c.address??''};form.value.originalOrderId=null;await preview();await loadOriginals('');}catch(e){error.value=orderError(e);}}
async function preview(){const customerId=form.value.customerId;const skuIds=form.value.items.flatMap(i=>i.skuId?[i.skuId]:[]);if(!customerId||!skuIds.length)return;pricing.value=true;try{const r=await orderApi.preview({customerId,skuIds});for(const i of form.value.items){const p=r.data.items.find(p=>String(p.skuId)===String(i.skuId));i.draftUnitPrice=p?.unitPrice??null;i.draftPriceSource=p?.priceSource??null;}}catch(e){error.value=orderError(e);}finally{pricing.value=false;}}
async function loadOriginals(keyword:string){try{const r=await orderApi.query({pageNum:1,pageSize:20,status:'CONFIRMED',customerId:form.value.customerId,keyword});originals.value=r.data.list.map(o=>({value:o.orderId!,label:o.orderNo!}));}catch(e){error.value=orderError(e);}}
async function save(draft=false){error.value='';try{form.value.items.forEach(i=>{i.orderedQuantity=fixed(i.orderedQuantity);if(i.unitPrice!=null)i.unitPrice=fixed(i.unitPrice);});const invalid=validateOrder(form.value);if(invalid){error.value=invalid;return;}saving.value=true;const f=payload(form.value);if(f.orderSource!=='SUPPLEMENT'){f.originalOrderId=null;f.supplementReason=null;}if(f.orderId){await orderApi.update(f);message.success('订单修改已保存');}else if(draft){await orderApi.create(f);message.success('草稿已保存');}else{const result=await orderApi.createAndProgress(f);message.success(result.data.status==='CONFIRMED'?'订单已创建并确认':'订单已创建，非标品等待称重后确认');}visible.value=false;emit('saved');}catch(e){error.value=orderError(e);}finally{saving.value=false;}}
defineExpose({open});
</script>
