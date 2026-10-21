<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue';
import {useRoute,useRouter} from 'vue-router';
import Decimal from 'decimal.js';
import {ArrowLeftOutlined,PrinterOutlined,CheckOutlined,ReloadOutlined,SwapOutlined,CheckCircleFilled,InboxOutlined} from '@ant-design/icons-vue';
import {api,query,errorText,unknownResult} from '../api';
import {groupKey,type SortingMode} from '../sorting';
import {batch,boot,session,reading,scaleError,scaleControl,fresh,stable,authorized,now,quantity,time,printLabels} from '../store';
import type {Basket,Summary,Scope,Capture,CompleteRequest,ScaleControl,AgentStatus} from '../types';

const route=useRoute(),router=useRouter(),error=ref(''),notice=ref(''),busy=ref(false),armed=ref(true),loading=ref(false);
// 仅恢复本机任务层级；手工修改查询参数不能跳转到外站。
const returnPath=computed(()=>typeof route.query.returnTo==='string'&&/^\/tasks(?:\/(?:products|customers)(?:\/[^?#]*)?)?$/.test(route.query.returnTo)?route.query.returnTo:'/tasks');
const summary=ref<Summary|null>(null),records=ref<Basket[]>([]),pending=ref<Capture|null>(null),pendingComplete=ref<CompleteRequest|null>(null);
const completeOpen=ref(false),result=ref<string>(),reason=ref('');let generation=0;
const job=computed(()=>batch.value?.tasks.find(row=>row.jobId===route.params.jobId));
const item=computed(()=>job.value?.items.find(row=>row.taskItemId===route.params.taskItemId));
const scope=computed<Scope|null>(()=>job.value&&item.value?{jobId:job.value.jobId,taskId:job.value.taskId,taskItemId:item.value.taskItemId}:null);
const storageKey=computed(()=>scope.value?`xsy-pending:${boot.value?.deviceCode}:${session.value}:${JSON.stringify(scope.value)}`:'');
const remaining=computed(()=>{try{return new Decimal(item.value?.plannedQuantity||0).minus(summary.value?.totalQuantity||0);}catch{return new Decimal(0);}});
const unitMatches=computed(()=>!!reading.value&&reading.value.rawUnit===item.value?.saleUnit);
const metrologyValid=computed(()=>{
  if(!reading.value||!boot.value)return false;
  try{const value=new Decimal(reading.value.rawWeight).div(reading.value.rawUnit==='g'?1000:reading.value.rawUnit==='sj'?2:1);
    // 与后台保持一致：只检查范围，原始重量不按铭牌分度过滤或取整。
    return value.gte(boot.value.minimumKg)&&value.lte(boot.value.maximumKg);}catch{return false;}
});
const writable=computed(()=>authorized.value&&!!job.value&&job.value.claimedByDeviceCode===boot.value?.deviceCode&&new Date(job.value.expiresAt).getTime()>now.value&&!summary.value?.authorizationExpired);
const controlPending=computed(()=>scaleControl.value?.state==='SENDING'||scaleControl.value?.state==='WAITING');
const controlBlocked=computed(()=>!!scaleControl.value&&scaleControl.value.state!=='ZERO_OBSERVED');
const canControl=computed(()=>!!boot.value?.allowScaleControl&&writable.value&&!busy.value&&!controlPending.value&&!pending.value&&!pendingComplete.value&&fresh.value&&reading.value?.stableFlag===true);
const canRecord=computed(()=>writable.value&&!summary.value?.locallyCompleted&&!pendingComplete.value&&!busy.value&&(!!pending.value||(!controlBlocked.value&&armed.value&&stable.value&&unitMatches.value&&metrologyValid.value)));
const weightMessage=computed(()=>controlPending.value?'正在等待设备稳定零读数':scaleControl.value?.state==='UNCONFIRMED'?'操作结果未确认，请核对设备零读数':!reading.value?(scaleError.value==='LOCAL_SERVICE_LOST'?'本机服务失联，请重新连接':scaleError.value==='HTTP_SCALE_FAILED'||scaleError.value==='SCALE_DISCONNECTED'?'读秤连接异常，请检查设备':'等待设备新读数'):!stable.value?'请等待稳定净重':!unitMatches.value?'设备与商品单位不一致':!metrologyValid.value?'重量超出可记录范围':'稳定净重，可记录');
const controlHint=computed(()=>!boot.value?.allowScaleControl?'去皮 / 置零未启用':controlPending.value?'等待稳定零读数，暂不能记录':scaleControl.value?.state==='UNCONFIRMED'?'结果未确认，请核对设备后再操作':scaleControl.value?.state==='ZERO_OBSERVED'?'已收到设备稳定零读数':'空秤置零，放空筐后去皮');
const sideItems=computed(()=>{
  const context=router.resolve(returnPath.value).params;
  return job.value?.items.filter(value=>value.active&&value.productType==='NON_STANDARD'&&(!context.group||groupKey({job:job.value!,item:value},context.mode as SortingMode)===context.group))||[];
});
const visibleRecords=computed(()=>records.value.slice(-8).reverse());

/** 请求切换时使用代次检查，旧商品的迟到响应不能覆盖当前累计。 */
async function refresh(showLoading=true):Promise<void>{
  const target=scope.value;if(!target)return;const current=++generation;if(showLoading)loading.value=true;
  try{
    const total=await api<Summary>('/basket-summary'+query(target),session.value);
    const page=await api<{items:Basket[];nextAfter:number|null}>('/baskets'+query(target,{after:Math.max(0,total.basketCount-50),limit:50}),session.value);
    if(current!==generation)return;
    if(!summary.value&&total.basketCount>0)armed.value=false;
    summary.value=total;records.value=page.items;
    // 刷新前响应丢失时，可从原操作号确认保存；不重新创造采集事实。
    if(pending.value&&page.items.some(row=>row.operationId===pending.value?.operationId)){pending.value=null;localStorage.removeItem(storageKey.value);armed.value=false;}
    if(pendingComplete.value&&total.completion?.operationId===pendingComplete.value.operationId){pendingComplete.value=null;localStorage.removeItem(storageKey.value+':complete');}
  }catch(value){if(current===generation)error.value=errorText(value);}finally{if(current===generation)loading.value=false;}
}

/** 保留原请求后才发送，网络未知结果可跨刷新使用同操作号重试。 */
async function record():Promise<void>{
  if(!canRecord.value||!job.value||!item.value||!scope.value)return;busy.value=true;error.value='';notice.value='';
  const key=storageKey.value;
  if(!pending.value){pending.value={operationId:crypto.randomUUID(),sampleId:reading.value!.sampleId,taskId:job.value.taskId,
    taskItemId:item.value.taskItemId,operatorSessionId:session.value,jobId:job.value.jobId,jobVersion:job.value.jobVersion,taskItemVersion:item.value.taskItemVersion};
    try{localStorage.setItem(key,JSON.stringify(pending.value));}catch{error.value='浏览器无法保留操作编号，请启用本地存储后再记录';pending.value=null;busy.value=false;return;}}
  try{
    const saved=await api<Basket>('/baskets',session.value,pending.value);localStorage.removeItem(key);pending.value=null;armed.value=false;
    notice.value=`第 ${saved.basketNumber} 筐已保存，${quantity(saved.measurement.netWeight)} ${saved.measurement.businessUnit}。${boot.value?.localTicketsEnabled?'凭条正在处理。':'打印等待授权。'}`;
    await refresh(false);
  }catch(value){error.value=errorText(value);if(!unknownResult(value)){pending.value=null;localStorage.removeItem(key);}}
  finally{busy.value=false;}
}

/** 完成使用最新集合摘要，明确与“再记录一筐”分开。 */
async function openComplete():Promise<void>{if(busy.value)return;await refresh();if(!summary.value||!writable.value)return;completeOpen.value=true;result.value=undefined;reason.value='';}
async function complete():Promise<void>{
  if(busy.value||!job.value||!item.value||!summary.value||!scope.value)return;
  if(!pendingComplete.value&&(!result.value||(result.value!=='NORMAL'&&!reason.value.trim()))){error.value='请选择分拣结果，异常结果需填写原因';return;}
  busy.value=true;error.value='';const key=storageKey.value+':complete';
  if(!pendingComplete.value){pendingComplete.value={operationId:crypto.randomUUID(),taskId:job.value.taskId,taskItemId:item.value.taskItemId,
    operatorSessionId:session.value,jobId:job.value.jobId,jobVersion:job.value.jobVersion,taskItemVersion:item.value.taskItemVersion,
    manifestHash:summary.value.manifestHash,result:result.value!,reason:reason.value.trim()||null};
    try{localStorage.setItem(key,JSON.stringify(pendingComplete.value));}catch{pendingComplete.value=null;error.value='无法保留完成操作编号，请检查本地存储';busy.value=false;return;}}
  try{await api('/item-completions',session.value,pendingComplete.value);localStorage.removeItem(key);pendingComplete.value=null;completeOpen.value=false;notice.value='商品已在本地完成，等待平台确认。';await refresh(false);}
  catch(value){error.value=errorText(value);if(!unknownResult(value)){pendingComplete.value=null;localStorage.removeItem(key);}}
  finally{busy.value=false;}
}

/** 人工命令只发一次，后续只查询状态；超时不重发、不把写入成功当成设备执行回执。
 * @param command 仅tare或zero，由页面两个按钮提供，不接收空值。
 * @returns 展示本次零值观测或未确认结果；请求失败显示错误，仍以服务端采集门禁为准。
 */
async function control(command:'tare'|'zero'):Promise<void>{
  if(!canControl.value)return;busy.value=true;error.value='';notice.value='';
  try{
    const sent=await api<{control:ScaleControl}>('/scale/'+command,session.value,{});scaleControl.value=sent.control;
    const deadline=Date.now()+6500;
    while(Date.now()<deadline){
      const current=await api<AgentStatus>('/status');scaleControl.value=current.control;
      if(current.control?.controlId!==sent.control.controlId){error.value='控制状态已变化，请核对设备';return;}
      if(current.control.state==='ZERO_OBSERVED'){notice.value=`${command==='tare'?'去皮':'置零'}后已收到设备稳定零读数。请放入商品再记录。`;return;}
      if(current.control.state==='UNCONFIRMED'){error.value='操作结果未确认，请核对设备；程序不会自动重发。';return;}
      await new Promise(resolve=>setTimeout(resolve,250));
    }
    error.value='尚未收到稳定零读数，请核对设备；程序不会自动重发。';
  }catch(value){error.value=errorText(value);}
  finally{busy.value=false;}
}

watch(()=>[route.params.jobId,route.params.taskItemId,session.value,!!batch.value],()=>{
  generation++;summary.value=null;records.value=[];error.value='';notice.value='';armed.value=true;
  try{pending.value=JSON.parse(localStorage.getItem(storageKey.value)||'null');pendingComplete.value=JSON.parse(localStorage.getItem(storageKey.value+':complete')||'null');}catch{pending.value=null;pendingComplete.value=null;}
  if(scope.value){localStorage.setItem('xsy-last-weigh',route.fullPath);void refresh();}
},{immediate:true});
const timer=setInterval(()=>{if(scope.value&&!busy.value)void refresh(false);},4000);
onUnmounted(()=>{generation++;clearInterval(timer);});
</script>

<template>
  <div v-if="!item||!job" class="panel empty-state"><h2>当前任务不可用</h2><p>请先获取员工授权并选择当前有效任务。</p><a-button type="primary" aria-label="返回上级" @click="router.push(returnPath)"><arrow-left-outlined class="workstation-icon"/>返回上级</a-button></div>
  <template v-else>
    <section class="page-heading compact"><div><a-button aria-label="返回上级" @click="router.push(returnPath)"><arrow-left-outlined class="workstation-icon"/>返回上级</a-button><h1>称重作业</h1></div><div class="task-reference"><span>{{item.orderNo||job.taskNo}}</span><a-tag color="blue">非标品称重</a-tag></div></section>
    <a-alert v-if="error" :message="error" type="error" show-icon class="section-gap" closable @close="error=''"/>
    <a-alert v-if="notice" :message="notice" type="success" show-icon class="section-gap"/>
    <a-alert v-if="pending||pendingComplete" message="上一次操作结果尚待核对。请重试原操作，勿重新记录本筐。" type="warning" show-icon class="section-gap"/>
    <div class="weigh-grid">
      <aside class="panel product-pane"><div class="pane-heading"><h2>{{returnPath==='/tasks'?'本单商品':'当前分组'}}</h2><span>{{sideItems.length}} 项</span></div><button v-for="product in sideItems" :key="product.taskItemId" :disabled="busy" :class="['product-choice',{selected:product.taskItemId===item.taskItemId}]" @click="router.push({path:`/weigh/${encodeURIComponent(job.jobId)}/${encodeURIComponent(product.taskItemId)}`,query:{returnTo:returnPath}})"><span class="choice-dot"></span><strong>{{product.productName}}</strong><small>{{product.customerName||'客户资料未提供'}}</small><small>{{product.specName||'默认规格'}} · {{quantity(product.plannedQuantity)}} {{product.saleUnit}}</small></button><div class="pane-note">按筐保存<br>全部称完后完成商品</div></aside>
      <section class="weight-section">
        <div class="panel weight-card">
          <div class="current-product"><div class="eyebrow">{{item.customerName||'客户资料未提供'}}</div><h2>{{item.productName}}<small>{{item.specName||'默认规格'}}</small></h2></div>
          <div class="weight-topline"><span>当前净重</span><span :class="['reading-state',{ready:stable&&unitMatches&&metrologyValid&&!controlBlocked}]"><span class="online-dot" :class="{on:stable&&!controlBlocked}"></span>{{weightMessage}}</span></div>
          <div class="weight-display" data-testid="current-weight">{{reading?quantity(reading.rawWeight):'—'}}<small>{{reading?.rawUnit||item.saleUnit}}</small></div>
          <div class="weight-subline"><span>皮重 <strong>{{quantity(reading?.tareWeight)}} {{reading?.rawUnit||item.saleUnit}}</strong></span><span>来源 <strong>{{boot?.localTest&&!boot?.hardwareTest?'演练设备':'电子秤'}}</strong></span><span>{{time(reading?.capturedAt)}}</span></div>
          <div class="weight-controls"><a-button :disabled="!canControl" @click="control('tare')">去皮</a-button><a-button :disabled="!canControl" @click="control('zero')">置零</a-button><span data-testid="scale-control-status">{{controlHint}}</span></div>
          <div class="weight-progress"><div><span>计划量</span><strong>{{quantity(item.plannedQuantity)}} <small>{{item.saleUnit}}</small></strong></div><div><span>本地累计</span><strong class="primary-number" data-testid="basket-total">{{quantity(summary?.totalQuantity)}} <small>{{item.saleUnit}}</small></strong></div><div><span>{{remaining.isNegative()?'超出量':'距计划还差'}}</span><strong>{{quantity(remaining.abs().toString())}} <small>{{item.saleUnit}}</small></strong></div></div>
          <div v-if="summary?.locallyCompleted" class="completed-callout"><check-circle-filled class="workstation-icon"/><strong>本地已完成</strong><span>{{summary.basketCount}} 筐 · 等待平台确认</span></div>
          <template v-else>
            <a-button v-if="!armed&&!pending" class="touch-primary continue-button" block @click="armed=true;notice='' "><swap-outlined class="workstation-icon"/>已换筐，继续称重</a-button>
            <a-button v-else type="primary" class="touch-primary" block :disabled="!canRecord" :loading="busy" @click="record"><printer-outlined class="workstation-icon" v-if="boot?.localTicketsEnabled"/><check-outlined class="workstation-icon" v-else/>{{pending?'重试上次保存':boot?.localTicketsEnabled?'记录本筐并打印':'记录本筐'}}</a-button>
            <a-button class="touch-secondary" block :disabled="!writable||!summary?.basketCount||!!pending||busy" @click="pendingComplete?complete():openComplete()">{{pendingComplete?'重试完成商品':'完成商品'}}</a-button>
          </template>
          <p class="weight-footnote">{{summary?.locallyCompleted?'完成操作未新增称重或汇总票。':!writable?'员工授权或作业租约无效，请刷新任务。':'每筐保存一次。打印失败保留重量，补打不重复计重。'}}</p>
        </div>
        <div class="platform-snapshot"><span>平台缓存已分拣</span><strong>{{quantity(summary?.platformSnapshotQuantity)}} {{item.saleUnit}}</strong><span>与本地累计分别统计</span></div>
      </section>
      <aside class="panel basket-pane"><div class="pane-heading"><h2>本商品筐记录</h2><a-button type="text" size="small" :loading="loading" aria-label="刷新筐记录" @click="refresh()"><reload-outlined class="workstation-icon"/></a-button></div><div class="basket-summary"><strong>{{summary?.basketCount??0}}<small>筐已保存</small></strong><a-tag class="basket-status" color="orange">待同步</a-tag></div><div v-if="!records.length" class="basket-empty"><inbox-outlined class="workstation-icon"/><p>还没有筐记录</p><small>记录第一筐后将在这里显示</small></div><div v-else class="basket-list"><div v-for="row in visibleRecords" :key="row.eventKey" class="basket-row"><div><strong>第 {{row.basketNumber}} 筐</strong><span>{{time(row.measurement.capturedAt)}}</span></div><div class="basket-value">{{quantity(row.measurement.netWeight)}}<small>{{row.measurement.businessUnit}}</small></div><span class="basket-print">{{printLabels[row.printState]||'未知打印状态'}}</span></div></div><router-link class="all-records" to="/records">查看记录与打印状态 →</router-link></aside>
    </div>
    <a-modal v-model:open="completeOpen" title="完成当前商品" ok-text="确认完成商品" :confirm-loading="busy" @ok="complete">
      <div class="completion-facts"><span>{{item.productName}}</span><strong>{{summary?.basketCount}} 筐 · {{quantity(summary?.totalQuantity)}} {{item.saleUnit}}</strong></div>
      <p class="muted">确认已记录全部筐。完成后本机锁定该商品，等待平台确认；不会再次称重或额外打印汇总票。</p>
      <a-form layout="vertical"><a-form-item label="分拣结果" required><a-select v-model:value="result" placeholder="请选择现场复核结果" :options="[{value:'NORMAL',label:'正常'},{value:'SHORT',label:'短少'},{value:'OVER',label:'超出'},{value:'OUT_OF_STOCK',label:'缺货'}]"/></a-form-item><a-form-item label="复核说明" :required="!!result&&result!=='NORMAL'"><a-textarea v-model:value="reason" :maxlength="500" :rows="3" placeholder="异常结果需说明原因"/></a-form-item></a-form><a-alert v-if="error" :message="error" type="error" show-icon/>
    </a-modal>
  </template>
</template>
