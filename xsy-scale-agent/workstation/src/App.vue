<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref} from 'vue';
import {useRoute} from 'vue-router';
import {AppstoreOutlined,DashboardOutlined,UnorderedListOutlined,SettingOutlined,UserOutlined,WifiOutlined,ReloadOutlined} from '@ant-design/icons-vue';
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import {theme} from './theme';
import {boot,batch,cached,connected,session,initialize,dispose,refreshTasks,authorized} from './store';
import {api,errorText} from './api';

const route=useRoute(), error=ref(''), sessionOpen=ref(false), sessionInput=ref(''), sessionBusy=ref(false);
const navigation=[{path:'/tasks',label:'任务选择',icon:AppstoreOutlined},{path:'/weigh',label:'称重作业',icon:DashboardOutlined},{path:'/records',label:'称重记录',icon:UnorderedListOutlined},{path:'/settings',label:'设备设置',icon:SettingOutlined}];
const selected=computed(()=>route.path.startsWith('/weigh')?'/weigh':route.path.startsWith('/tasks')?'/tasks':route.path);
const lastWeigh=ref(localStorage.getItem('xsy-last-weigh') || '/tasks');

/** 使用平台授权引用切换员工；验证成功后再替换当前可见任务。 */
async function connectSession():Promise<void> {
  sessionBusy.value=true; error.value='';
  try {const value=await api<{operatorSessionId:string}>('/workstation/session','',{operatorSessionId:sessionInput.value.trim()});
    batch.value=null;session.value=value.operatorSessionId;await refreshTasks();sessionOpen.value=false;}
  catch (value) {error.value=errorText(value);} finally {sessionBusy.value=false;}
}
async function start():Promise<void> {error.value='';try {await initialize();} catch(value){error.value=errorText(value);}}
function rememberRoute():string {lastWeigh.value=localStorage.getItem('xsy-last-weigh')||'/tasks';return lastWeigh.value;}
onMounted(start);onUnmounted(dispose);
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <div class="app-shell">
      <aside class="navigation">
        <router-link to="/tasks" class="brand"><img src="/mark.svg" alt=""><span>鲜蔬园<small>分拣称重</small></span></router-link>
        <div class="nav-caption">工作台</div>
        <nav aria-label="工作台导航">
          <router-link v-for="item in navigation" :key="item.path" :aria-label="item.label" :to="item.path==='/weigh'?rememberRoute():item.path" :class="['nav-item',{active:selected===item.path}]">
            <component class="workstation-icon" :is="item.icon"/><span>{{item.label}}</span>
          </router-link>
        </nav>
        <div class="nav-bottom"><span class="online-dot" :class="{on:connected}"></span><span>{{connected?'本机服务已连接':'等待本机服务'}}</span><small>设备服务 v{{boot?.version||'—'}}</small></div>
      </aside>
      <div class="workspace">
        <header class="topbar">
          <div><span class="topbar-title">分拣称重工作台</span><span class="station-label">{{boot?.stationCode||'工作台未配置'}}</span></div>
          <div class="topbar-actions"><span :class="['connection-state',{warning:cached}]"><wifi-outlined class="workstation-icon"/>{{cached?'离线缓存作业':'本机作业'}}</span><a-button type="text" @click="sessionOpen=true"><user-outlined class="workstation-icon"/>{{session?(authorized?'当前员工已授权':'授权需更新'):'连接员工授权'}}</a-button></div>
        </header>
        <div v-if="boot?.localTest" class="exercise-banner"><span class="exercise-label">{{boot.hardwareTest?'实机联动测试':'本地演练'}}</span>{{boot.hardwareTest?'测试任务 · 读取真实秤 · 点击保存会打印测试标签 · 不上传正式平台':'测试任务与重量 · 不上传正式平台 · 打印仅模拟，不出纸'}}</div>
        <a-alert v-if="error" :message="error" type="warning" show-icon closable class="global-alert" @close="error=''">
          <template #action><a-button size="small" @click="start"><reload-outlined class="workstation-icon"/>重新连接</a-button></template>
        </a-alert>
        <main class="main-content"><router-view v-if="boot"/><div v-else class="startup panel"><a-spin size="large"/><h2>正在连接本机工作台</h2><p>请保持设备服务运行</p><a-button @click="start">重新连接</a-button></div></main>
        <footer class="app-footer"><span>鲜蔬园 · 仓库分拣作业</span><span>本地保存优先 · 同步状态独立记录</span></footer>
      </div>
    </div>
    <a-modal v-model:open="sessionOpen" title="连接员工授权" ok-text="验证并进入" :confirm-loading="sessionBusy" @ok="connectSession">
      <p class="muted">使用平台签发的员工授权引用获取本人可操作的任务。已有有效缓存时，可继续离线作业。</p>
      <a-form layout="vertical"><a-form-item label="员工授权引用" required><a-input v-model:value="sessionInput" :maxlength="160" autocomplete="off" placeholder="由平台提供的授权引用" @press-enter="connectSession"/></a-form-item></a-form>
      <a-alert v-if="error" :message="error" type="error" show-icon/>
    </a-modal>
  </a-config-provider>
</template>
