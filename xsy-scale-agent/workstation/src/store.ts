import {computed,ref} from 'vue';
import Decimal from 'decimal.js';
import {api} from './api';
import type {AgentStatus,Boot,Reading,TaskBatch,ScaleControl} from './types';

export const boot = ref<Boot|null>(null), batch = ref<TaskBatch|null>(null), status = ref<AgentStatus|null>(null);
export const session = ref(''), cached = ref(false), connected = ref(false), loading = ref(false);
export const reading = ref<Reading|null>(null), now = ref(Date.now());
export const scaleError = ref<string|null>(null);
export const scaleControl = ref<ScaleControl|null>(null);
let active = true, streamStarted=false, controller:AbortController|undefined;
export const authorized = computed(()=>!!batch.value && new Date(batch.value.operatorSessionExpiresAt).getTime()>now.value);
export const fresh = computed(()=>connected.value && !!reading.value && now.value-new Date(reading.value.capturedAt).getTime()<2000);
export const stable = computed(()=>fresh.value && reading.value?.stableFlag === true && reading.value.netGrossFlag === 'NET');

/** 加载任务，平台断网时Agent返回已持久缓存；调用方负责显示错误。 */
export async function refreshTasks():Promise<void> {
  if (!session.value) return;
  loading.value = true;
  try {const result = await api<{cached:boolean;data:TaskBatch}>('/tasks',session.value); batch.value=result.data; cached.value=result.cached;}
  finally {loading.value=false;}
}

/** 初始化一次共享状态，页面切换不会创建重复SSE连接。 */
export async function initialize():Promise<void> {
  boot.value = await api<Boot>('/workstation'); session.value=boot.value.operatorSessionId;
  if(!streamStarted){streamStarted=true;void weightStream();}
  status.value = await api<AgentStatus>('/status');
  scaleControl.value=status.value.control;
  if (session.value) await refreshTasks();
}

async function weightStream():Promise<void> {
  while (active) {
    try {
      controller = new AbortController();
      const response = await fetch('/api/agent/weight/stream',{headers:{'X-Workstation-Request':'1'},credentials:'same-origin',signal:controller.signal});
      if (!response.ok || !response.body) throw new Error('SSE不可用');
      const stream = response.body.getReader(), decoder = new TextDecoder(); let buffer='';
      connected.value=true;
      while (active) {
        const chunk = await stream.read(); if (chunk.done) break;
        buffer += decoder.decode(chunk.value,{stream:true});
        let boundary:number;
        while ((boundary=buffer.indexOf('\n\n'))>=0) {
          const frame=buffer.slice(0,boundary); buffer=buffer.slice(boundary+2);
          if (frame.startsWith('data: ')) {const value=JSON.parse(frame.slice(6)); reading.value=value.reading;scaleError.value=value.error;scaleControl.value=value.control;}
        }
      }
    } catch { /* 本机连接恢复后重新建立流；失联立即撤销页面采集能力。 */ }
    connected.value=false; reading.value=null;scaleError.value='LOCAL_SERVICE_LOST';
    if (active) await new Promise(resolve=>setTimeout(resolve,1500));
  }
}

const timer = setInterval(()=>{now.value=Date.now();},250);
/** 页面关闭仅释放连接，不修改业务记录或缓存。 */
export function dispose():void {active=false;controller?.abort();clearInterval(timer);}

/** 精确数量格式化；无值显示破折号，不把未知值补零。
 * @param value 十进制字符串或实时读数；null/undefined/空串/非法值显示破折号。
 * @returns 不舍入的十进制显示文字，不修改冻结重量。
 */
export function quantity(value:string|number|null|undefined):string {
  if (value === null || value === undefined || value === '') return '—';
  try {return new Decimal(value).toFixed();} catch {return '—';}
}
/** 显示北京时间，未知时间不假造当前时间。
 * @param value 服务端带时区ISO时间；undefined/空串显示破折号。
 * @param full 默认false只显示时分秒，true同时显示月日。
 * @returns 北京时间显示文字；调用方须传合法时间，非法非空值抛RangeError。
 */
export function time(value:string|undefined,full=false):string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',...(full?{month:'2-digit',day:'2-digit'}:{}),hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(value));
}
export const printLabels:Record<string,string>={NOT_REQUESTED:'未要求打印',WAITING_AUTHORIZATION:'等待打印授权',QUEUED:'等待打印',SUBMITTING:'正在提交',SUBMITTED_TO_SPOOLER:'已交打印队列',FAILED:'打印失败',UNKNOWN:'结果未知，需核查',TEST_ONLY:'演练输出 · 未出纸'};
