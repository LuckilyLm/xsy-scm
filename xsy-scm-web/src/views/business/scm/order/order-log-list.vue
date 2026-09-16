<!-- 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/views/business/order/order-log-list.vue
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 -->
<template>
 <a-form class="smart-query-form" layout="inline" @submit.prevent><a-row class="smart-query-form-row">
 
 <a-form-item label="操作类型" class="smart-query-form-item"><SmartEnumSelect enum-name="SCM_ORDER_OPERATION_ENUM" v-model:value="queryForm.operationType" width="160px"/></a-form-item><a-form-item class="smart-query-form-item"><a-button-group><a-button type="primary" @click="onSearch" v-privilege="'scm:order:log:query'">查询</a-button><a-button @click="resetQuery">重置</a-button></a-button-group></a-form-item></a-row></a-form>
 <a-alert v-if="error" :message="error" type="error" show-icon><template #action><a-button @click="queryData">重试</a-button></template></a-alert>
 <a-card size="small" :bordered="false"><a-row class="smart-table-btn-block"><div class="smart-table-operate-block">操作日志</div><div class="smart-table-setting-block"><TableOperator v-model="columns" :table-id="605" :refresh="queryData"/></div></a-row>
 <a-table id="order-log-table" size="small" :data-source="tableData" :columns="columns" row-key="logId" :loading="loading" bordered :pagination="false" :scroll="{x:1100}"><template #bodyCell="{record,column,text}"><template v-if="column.dataIndex==='operationType'">{{SCM_ORDER_OPERATION_ENUM[text]?.desc}}</template><template v-else-if="['approvedAmount','refundAmount'].includes(column.dataIndex)">{{amount(text)}}</template><template v-else-if="column.dataIndex==='action'"><div class="smart-table-operate"><a-button type="link" @click="active=record;visible=true">变更前后</a-button></div></template></template></a-table>
 <div class="smart-query-table-page"><a-pagination show-size-changer show-quick-jumper v-model:current="queryForm.pageNum" v-model:page-size="queryForm.pageSize" :total="total" @change="queryData" :show-total="(n:number)=>`共${n}条`"/></div></a-card><a-modal :open="visible" title="订单变更前后" width="900px" :footer="null" @cancel="visible=false"><pre>{{JSON.stringify({before:active?.beforeData,after:active?.afterData},null,2)}}</pre></a-modal>
</template>
<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue';import type {TableColumnsType} from 'ant-design-vue';import {orderLogApi as api} from '/@/api/business/scm/order-log-api';import {SCM_ORDER_OPERATION_ENUM} from '/@/constants/business/scm/order-const';import SmartEnumSelect from '/@/components/framework/smart-enum-select/index.vue';import TableOperator from '/@/components/support/table-operator/index.vue';import type {LogRow,Query} from './order-types';import {amount} from './order-form-model';import {orderError} from './order-errors';
const queryForm=reactive<Query>({pageNum:1,pageSize:20}),tableData=ref<LogRow[]>([]),total=ref(0),loading=ref(false),error=ref(''),visible=ref(false),active=ref<LogRow>();let requestId=0;
const columns=ref<TableColumnsType<LogRow>>([{title:'时间',dataIndex:'createdAt',width:210},{title:'操作',dataIndex:'operationType',width:140},{title:'操作人',dataIndex:'operatorName',width:140},{title:'原因',dataIndex:'reason',width:200},{title:'操作',dataIndex:'action',align:'right',fixed:'right',width:240}]);
async function queryData(){const id=++requestId;loading.value=true;error.value='';try{const r=await api.query(queryForm);if(id===requestId){tableData.value=r.data.list;total.value=r.data.total;}}catch(e){if(id===requestId)error.value=orderError(e);}finally{if(id===requestId)loading.value=false;}}
function onSearch(){queryForm.pageNum=1;queryData();}function resetQuery(){queryForm.keyword=undefined;queryForm.status=undefined;queryForm.operationType=undefined;onSearch();}
onMounted(queryData);
</script><style scoped>pre{white-space:pre-wrap;overflow-wrap:anywhere;}</style>
