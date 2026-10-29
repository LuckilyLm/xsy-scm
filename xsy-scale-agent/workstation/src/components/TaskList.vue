<script setup lang="ts">
import {unitLabel} from '../units';
import {SearchOutlined,ArrowRightOutlined,InboxOutlined} from '@ant-design/icons-vue';
import {boot,batch,session,loading,quantity,authorized,now} from '../store';
import type {TaskRow} from '../sorting';
defineProps<{rows:TaskRow[];search:string;busy:string}>();
defineEmits<{(event:'update:search',value:string):void;(event:'choose',row:TaskRow):void}>();
</script>

<template>
  <section class="panel task-panel">
    <div class="panel-toolbar"><h2>可称重商品 <span class="count-label">{{rows.length}}</span></h2><a-input :value="search" @update:value="$emit('update:search',$event)" placeholder="搜索客户、订单或商品" allow-clear class="search-box"><template #prefix><search-outlined class="workstation-icon"/></template></a-input></div>
    <div v-if="loading&&!batch" class="empty-state"><a-spin/><p>正在获取当前员工任务</p></div>
    <div v-else-if="!rows.length" class="empty-state"><inbox-outlined class="workstation-icon"/><h3>{{session?'暂无可称重任务':'先连接员工授权'}}</h3><p>{{session?'可刷新任务，或调整搜索内容。':'点击右上角“连接员工授权”，获取本人任务。'}}</p></div>
    <div v-else class="task-list">
      <article v-for="row in rows" :key="JSON.stringify([row.job.jobId,row.item.taskItemId])" class="task-row">
        <div class="product-symbol">{{row.item.productName.slice(-2)}}</div>
        <div class="task-product"><h3>{{row.item.productName}} <span>{{row.item.specName||'默认规格'}}</span></h3><p>{{row.item.customerName||'客户资料未提供'}}<span class="divider">/</span>{{row.item.orderNo||row.job.taskNo}}</p></div>
        <div class="task-quantity"><span>计划数量</span><strong>{{quantity(row.item.plannedQuantity)}} <small>{{unitLabel(row.item.saleUnit)}}</small></strong></div>
        <div class="task-status"><a-tag :color="row.job.claimedByDeviceCode===boot?.deviceCode?'blue':'default'">{{row.job.claimedByDeviceCode===boot?.deviceCode?'本机已领取':'待领取'}}</a-tag></div>
        <a-button type="primary" :loading="busy===JSON.stringify([row.job.jobId,row.item.taskItemId])" :disabled="!!busy||!authorized||new Date(row.job.expiresAt).getTime()<=now" @click="$emit('choose',row)">{{row.job.claimedByDeviceCode===boot?.deviceCode?'进入称重':'领取并称重'}}<arrow-right-outlined class="workstation-icon"/></a-button>
      </article>
    </div>
  </section>
</template>
