<script setup lang="ts">
import {computed,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import {SearchOutlined,ReloadOutlined,ArrowLeftOutlined,ArrowRightOutlined,InboxOutlined,AppstoreOutlined,TeamOutlined} from '@ant-design/icons-vue';
import {api,errorText} from '../api';
import {batch,boot,cached,session,loading,refreshTasks} from '../store';
import type {Job} from '../types';
import {sortingGroups,type TaskRow,type SortingMode} from '../sorting';
import TaskList from '../components/TaskList.vue';

const search=ref(''),error=ref(''),busy=ref(''),router=useRouter(),route=useRoute();
const mode=computed(()=>route.params.mode as SortingMode|undefined);
const modeLabel=computed(()=>mode.value==='products'?'按商品分拣':'按客户分拣');
// 父级由当前层级确定，直接进入和刷新页面后也能返回正确目录。
const parentPath=computed(()=>route.params.group?'/tasks/'+mode.value:'/tasks');
const allRows=computed(()=>(batch.value?.tasks??[]).flatMap(job=>job.items.filter(item=>item.active&&item.productType==='NON_STANDARD').map(item=>({job,item}))));
const groups=computed(()=>sortingGroups(allRows.value,mode.value||'products'));
const selected=computed(()=>groups.value.find(group=>group.key===route.params.group));
const visibleGroups=computed(()=>groups.value.filter(group=>[group.label,group.detail].some(value=>value.includes(search.value.trim()))));
const rows=computed(()=>(selected.value?.rows??[]).filter(({job,item})=>[job.taskNo,item.productName,item.specName,item.customerName,item.orderNo].some(value=>value?.includes(search.value.trim()))));
watch(()=>route.fullPath,()=>{search.value='';error.value='';});
async function refresh():Promise<void>{error.value='';try{await refreshTasks();}catch(value){error.value=errorText(value);}}
/** 领取由平台返回版本和租约；返回路径仅影响导航，不参与业务范围。 */
async function choose({job,item}:TaskRow):Promise<void>{
  if(busy.value)return;busy.value=JSON.stringify([job.jobId,item.taskItemId]);error.value='';
  try{
    if(job.claimedByDeviceCode!==boot.value?.deviceCode){
      const changed=await api<Job>(`/jobs/${encodeURIComponent(job.jobId)}/claim`,session.value,{});
      if(batch.value)batch.value={...batch.value,tasks:batch.value.tasks.map(current=>current.jobId===changed.jobId?changed:current)};
    }
    const target={path:`/weigh/${encodeURIComponent(job.jobId)}/${encodeURIComponent(item.taskItemId)}`,query:{returnTo:route.fullPath}};
    localStorage.setItem('xsy-last-weigh',router.resolve(target).fullPath);await router.push(target);
  }catch(value){error.value=errorText(value);}finally{busy.value='';}
}
</script>

<template>
  <section class="page-heading"><div><div class="eyebrow">开始分拣</div><h1>{{mode?modeLabel:'选择称重任务'}}</h1><p>{{mode?(mode==='products'?'先选商品，再选择各客户的称重任务。':'先选客户，再选择该客户的商品。'):'选择适合当前作业的分拣方式。'}}</p></div><div class="task-heading-actions"><a-button v-if="mode" aria-label="返回上级" @click="router.push(parentPath)"><arrow-left-outlined class="workstation-icon"/>返回上级</a-button><a-button :loading="loading" :disabled="!session" @click="refresh"><reload-outlined class="workstation-icon"/>刷新任务</a-button></div></section>
  <a-alert v-if="error" :message="error" type="error" show-icon class="section-gap"/>
  <a-alert v-if="cached" type="warning" message="当前使用已缓存任务，仅在原授权和作业租约有效期间可继续称重。" show-icon class="section-gap"/>
  <template v-if="!mode">
    <div class="task-overview">
      <router-link v-for="entry in [{mode:'products',label:'按商品分拣',detail:'先选商品，再查看各客户任务',icon:AppstoreOutlined},{mode:'customers',label:'按客户分拣',detail:'先选客户，再查看该客户商品',icon:TeamOutlined}]" :key="entry.mode" :to="'/tasks/'+entry.mode" class="panel sorting-card sorting-entry">
        <component :is="entry.icon" class="sorting-icon"/><div><h2>{{entry.label}}</h2><p>{{entry.detail}}</p></div><arrow-right-outlined class="sorting-arrow"/>
      </router-link>
    </div>
  </template>
  <template v-else>
    <nav class="sorting-breadcrumb" aria-label="分拣路径"><router-link to="/tasks">任务选择</router-link><span>/</span><router-link :to="'/tasks/'+mode">{{modeLabel}}</router-link><template v-if="route.params.group"><span>/</span><strong>{{selected?.label||'当前分组不可用'}}</strong></template></nav>
    <template v-if="!route.params.group">
      <section class="panel">
        <div class="panel-toolbar"><h2>{{mode==='products'?'选择商品':'选择客户'}} <span class="count-label">{{visibleGroups.length}}</span></h2><a-input v-model:value="search" :placeholder="mode==='products'?'搜索商品或规格':'搜索客户或编号'" allow-clear class="search-box"><template #prefix><search-outlined class="workstation-icon"/></template></a-input></div>
        <div v-if="loading&&!batch" class="empty-state"><a-spin/><p>正在获取当前员工任务</p></div>
        <div v-else-if="!visibleGroups.length" class="empty-state"><inbox-outlined class="workstation-icon"/><h3>{{session?'暂无匹配任务':'先连接员工授权'}}</h3><p>可刷新任务，或调整搜索内容。</p></div>
        <div v-else class="sorting-groups"><router-link v-for="group in visibleGroups" :key="group.key" :to="{path:'/tasks/'+mode+'/'+encodeURIComponent(group.key)}" class="sorting-card sorting-group" :data-group-key="group.key"><div><h3>{{group.label}}</h3><p>{{group.detail}}</p><p><span>{{group.rows.length}} 项任务</span><span class="divider">/</span>{{group.relatedCount}} {{mode==='products'?(group.rows.some(row=>!row.item.customerId?.trim())?'个客户任务组':'位客户'):(group.rows.some(row=>!row.item.skuId?.trim())?'个商品任务组':'种商品')}}</p><small v-if="!group.identityKnown" class="warning-text">身份资料待补充，按原任务独立展示</small></div><arrow-right-outlined class="sorting-arrow"/></router-link></div>
      </section>
    </template>
    <TaskList v-else-if="selected" v-model:search="search" :rows="rows" :busy="busy" @choose="choose"/>
    <section v-else class="panel empty-state"><h2>当前分组不可用</h2><p>任务已变化或当前员工不可见，请返回上级重新选择。</p></section>
  </template>
  <p class="page-note">每筐单独记录，累计后完成商品。两种分拣方式共用任务和称重记录。</p>
</template>
