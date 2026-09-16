<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-refund-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 -->
<template>
 <a-form class="smart-query-form" layout="inline" @submit.prevent><a-row class="smart-query-form-row">
 <a-form-item label="单号" class="smart-query-form-item"><a-input v-model:value="queryForm.keyword" @pressEnter="onSearch" allow-clear/></a-form-item>
 <a-form-item label="状态" class="smart-query-form-item"><SmartEnumSelect enum-name="SCM_ORDER_REFUND_STATUS_ENUM" v-model:value="queryForm.status" width="160px"/></a-form-item><a-form-item class="smart-query-form-item"><a-button-group><a-button type="primary" @click="onSearch" v-privilege="'scm:order:refund:query'">查询</a-button><a-button @click="resetQuery">重置</a-button></a-button-group></a-form-item></a-row></a-form>
 <a-alert v-if="error" :message="error" type="error" show-icon><template #action><a-button @click="queryData">重试</a-button></template></a-alert>
 <a-card size="small" :bordered="false"><a-row class="smart-table-btn-block"><div class="smart-table-operate-block">退款</div><div class="smart-table-setting-block"><TableOperator v-model="columns" :table-id="604" :refresh="queryData"/></div></a-row>
 <a-table id="order-refund-table" size="small" :data-source="tableData" :columns="columns" row-key="refundId" :loading="loading" bordered :pagination="false" :scroll="{x:1100}"><template #bodyCell="{record,column,text}"><template v-if="column.dataIndex==='status'">{{SCM_ORDER_REFUND_STATUS_ENUM[text]?.desc}}</template><template v-else-if="['approvedAmount','refundAmount'].includes(column.dataIndex)">{{amount(text)}}</template><template v-else-if="column.dataIndex==='action'"><div class="smart-table-operate"><a-button type="link" v-privilege="'scm:order:refund:complete'" v-if="record.status==='PENDING'" @click="edit(record)">登记退款完成</a-button></div></template></template></a-table>
 <div class="smart-query-table-page"><a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" @change="queryData" :show-total="(n:number)=>`共${n}条`"/></div></a-card><a-modal :open="visible" title="登记退款完成" :confirm-loading="saving" @ok="save" @cancel="visible=false"><a-alert v-if="error" :message="error" type="error"/><p>请确认线下退款已处理，此操作登记退款结果。</p><a-form-item label="外部凭证（可选）"><a-input v-model:value="externalReference" maxlength="128"/></a-form-item></a-modal>
</template>
<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue';import type {TableColumnsType} from 'ant-design-vue';import {orderRefundApi as api} from '/@/api/business/scm/order-refund-api';import {SCM_ORDER_REFUND_STATUS_ENUM} from '/@/constants/business/scm/order-const';import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';import TableOperator from '/@/components/support/table-operator/index.vue';import type {RefundRow,Query} from './order-types';import {amount} from './order-form-model';import {orderError} from './order-errors';
const queryForm=reactive<Query>({pageNum:1,pageSize:20}),tableData=ref<RefundRow[]>([]),total=ref(0),loading=ref(false),error=ref(''),visible=ref(false),saving=ref(false),active=ref<RefundRow>();let requestId=0;
const columns=ref<TableColumnsType<RefundRow>>([{title:'退款单号',dataIndex:'refundNo',width:220},{title:'状态',dataIndex:'status',width:120},{title:'退款金额',dataIndex:'refundAmount',align:'right',width:140},{title:'外部凭证',dataIndex:'externalReference',width:200},{title:'操作',dataIndex:'action',align:'right',fixed:'right',width:240}]);
async function queryData(){const id=++requestId;loading.value=true;error.value='';try{const r=await api.query(queryForm);if(id===requestId){tableData.value=r.data.list;total.value=r.data.total;}}catch(e){if(id===requestId)error.value=orderError(e);}finally{if(id===requestId)loading.value=false;}}
function onSearch(){queryForm.pageNum=1;queryData();}function resetQuery(){queryForm.keyword=undefined;queryForm.status=undefined;queryForm.operationType=undefined;onSearch();}
const externalReference=ref('');function edit(row:RefundRow){active.value=row;externalReference.value='';error.value='';visible.value=true;}
async function save(){saving.value=true;try{await api.complete({refundId:active.value!.refundId,version:active.value!.version,externalReference:externalReference.value||null});visible.value=false;await queryData();}catch(e){error.value=orderError(e);}finally{saving.value=false;}}
onMounted(queryData);
</script><style scoped>pre{white-space:pre-wrap;overflow-wrap:anywhere;}</style>
