<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 -->
<template>
 <a-form class="smart-query-form" layout="inline" @submit.prevent><a-row class="smart-query-form-row">
  <a-form-item label="订单号 / 客户" class="smart-query-form-item"><a-input v-model:value="queryForm.keyword" @pressEnter="onSearch" placeholder="名称或订单号" allow-clear/></a-form-item>
  <a-form-item label="订单来源" class="smart-query-form-item"><SmartEnumSelect enum-name="SCM_ORDER_SOURCE_ENUM" v-model:value="queryForm.orderSource" width="140px"/></a-form-item>
  <a-form-item label="订单状态" class="smart-query-form-item"><SmartEnumSelect enum-name="SCM_ORDER_STATUS_ENUM" v-model:value="queryForm.status" width="140px"/></a-form-item>
  <a-form-item class="smart-query-form-item"><a-button-group><a-button type="primary" @click="onSearch" v-privilege="'scm:order:query'">查询</a-button><a-button @click="resetQuery">重置</a-button></a-button-group></a-form-item>
 </a-row></a-form>
 <a-alert v-if="error" :message="error" type="error" show-icon><template #action><a-button @click="queryData">重试</a-button></template></a-alert>
 <a-card size="small" :bordered="false"><a-row class="smart-table-btn-block"><div class="smart-table-operate-block"><a-button type="primary" v-privilege="'scm:order:add'" @click="drawer?.open()">新建订单</a-button><a-button danger v-privilege="'scm:order:delete'" :disabled="!selected.length" @click="batchDelete">批量删除草稿</a-button></div><div class="smart-table-setting-block"><TableOperator v-model="columns" :table-id="602" :refresh="queryData"/></div></a-row>
  <a-table id="order-table" size="small" :data-source="tableData" :columns="columns" row-key="orderId" bordered :loading="loading" :pagination="false" :scroll="{x:1500}" :row-selection="{selectedRowKeys:selected,onChange:(keys:(string|number)[])=>selected=keys,getCheckboxProps:(r:Order)=>({disabled:r.status!=='DRAFT'})}">
   <template #bodyCell="{record,column}">
    <template v-if="column.dataIndex==='orderNo'"><a @click="detail?.open(record.orderId)">{{record.orderNo}}</a></template>
    <template v-else-if="column.dataIndex==='status'"><a-tag>{{SCM_ORDER_STATUS_ENUM[record.status]?.desc}}</a-tag></template>
    <template v-else-if="column.dataIndex==='orderSource'">{{SCM_ORDER_SOURCE_ENUM[record.orderSource]?.desc}}</template>
    <template v-else-if="column.dataIndex==='orderedTotalAmount'">{{amount(record.orderedTotalAmount,true)}}</template>
    <template v-else-if="column.dataIndex==='settlementTotalAmount'">{{amount(record.settlementTotalAmount)}}</template>
    <template v-else-if="column.dataIndex==='action'"><div class="smart-table-operate"><a-button type="link" @click="detail?.open(record.orderId)">详情</a-button><a-button v-if="record.status==='DRAFT'" type="link" v-privilege="'scm:order:update'" @click="drawer?.open(record.orderId)">编辑</a-button><a-button v-if="record.status==='DRAFT'" type="link" v-privilege="'scm:order:submit'" @click="submit(record)">提交</a-button><a-button v-if="record.status==='DRAFT'" danger type="link" v-privilege="'scm:order:delete'" @click="remove(record)">删除</a-button><a-button v-if="record.status==='CONFIRMED'" type="link" v-privilege="'scm:order:reserve-stock'" @click="reserveStock(record)">预留库存</a-button></div></template>
   </template>
  </a-table><div class="smart-query-table-page"><a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" @change="queryData" :show-total="(n:number)=>`共${n}条`"/></div>
 </a-card><OrderForm ref="drawer" @saved="queryData"/><OrderDetail ref="detail" @saved="queryData"/>
</template>
<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue';import {Modal} from 'ant-design-vue';import type {TableColumnsType} from 'ant-design-vue';
import {orderApi} from '/@/api/business/scm/order-api';import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';import TableOperator from '/@/components/support/table-operator/index.vue';
import {SCM_ORDER_STATUS_ENUM,SCM_ORDER_SOURCE_ENUM} from '/@/constants/business/scm/order-const';import type {Order,Query} from './order-types';import {amount} from './order-form-model';import {orderError} from './order-errors';import OrderForm from './components/order-form-drawer.vue';import OrderDetail from './order-detail.vue';
const queryForm=reactive<Query>({pageNum:1,pageSize:20}),tableData=ref<Order[]>([]),total=ref(0),loading=ref(false),error=ref(''),selected=ref<(string|number)[]>([]);const drawer=ref<InstanceType<typeof OrderForm>>(),detail=ref<InstanceType<typeof OrderDetail>>();let requestId=0;
const columns=ref<TableColumnsType<Order>>([{title:'订单号',dataIndex:'orderNo',width:210},{title:'客户',dataIndex:'customerNameSnapshot',width:160},{title:'客户编码',dataIndex:'customerCodeSnapshot',width:130},{title:'来源',dataIndex:'orderSource',width:100},{title:'状态',dataIndex:'status',align:'center',width:95},{title:'下单金额',dataIndex:'orderedTotalAmount',align:'right',width:140},{title:'结算金额',dataIndex:'settlementTotalAmount',align:'right',width:140},{title:'期望配送时间',dataIndex:'expectDeliveryTime',width:210},{title:'操作',dataIndex:'action',align:'right',fixed:'right',width:260}]);
async function queryData(){const id=++requestId;loading.value=true;error.value='';try{const r=await orderApi.query(queryForm);if(id===requestId){tableData.value=r.data.list;total.value=r.data.total;selected.value=[];}}catch(e){if(id===requestId)error.value=orderError(e);}finally{if(id===requestId)loading.value=false;}}
function onSearch(){queryForm.pageNum=1;queryData();}function resetQuery(){queryForm.keyword=undefined;queryForm.status=undefined;queryForm.orderSource=undefined;onSearch();}
function submit(r:Order){Modal.confirm({title:'提交订单并锁定价格？',onOk:async()=>{try{await orderApi.submit({orderId:r.orderId,version:r.version});await queryData();}catch(e){error.value=orderError(e);throw e;}}});}
function remove(r:Order){Modal.confirm({title:'删除这张草稿订单？',okType:'danger',onOk:async()=>{try{await orderApi.delete({orderId:r.orderId,version:r.version});await queryData();}catch(e){error.value=orderError(e);throw e;}}});}
/**
 * 预留库存（出库波次）。
 *
 * 显式操作而非确认时自动预留：本业务的库存在订单确认之后才产生，
 * 挂在确认上会让「先接单 → 再采购」链路无法运转（见 docs/decisions.md）。
 * 货到之后由业务人员对本单执行；任一行可用量不足则整体失败（41011）。
 */
function reserveStock(r:Order){Modal.confirm({title:'为该订单预留库存？',content:'将按订单明细的实数量占用可用量（可用量 = 现有量 − 预留量）。任一行不足则整体失败，不会只占一半。',okText:'预留',onOk:async()=>{try{await orderApi.reserveStock(r.orderId);await queryData();}catch(e){error.value=orderError(e);throw e;}}});}
function batchDelete(){Modal.confirm({title:'删除所选草稿？',okType:'danger',onOk:async()=>{try{await orderApi.batchDelete(tableData.value.filter(o=>selected.value.includes(o.orderId!)).map(o=>({orderId:o.orderId,version:o.version})));await queryData();}catch(e){error.value=orderError(e);throw e;}}});}
onMounted(queryData);
</script>
