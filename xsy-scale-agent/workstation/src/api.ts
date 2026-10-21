import type {Scope} from './types';

/** API拒绝与网络未知结果分开，调用方据此决定是否保留原操作号。 */
export class ApiError extends Error {
  constructor(public code:string, message:string, public status:number) {super(message);}
}

/** 调用同源Agent；不存取本机密钥，浏览器Cookie由服务端HttpOnly管理。
 * @param path 非空本机API后缀，可含已编码查询参数，不接受远程地址。
 * @param session 平台员工引用；默认空字符串，不发送员工头。
 * @param body 可序列化请求体；undefined发送GET，其他值发送POST。
 * @param extra 附加请求头，默认空对象；不用于传本机密钥。
 * @returns 成功响应投影；HTTP或网络失败抛ApiError，未知结果由调用方保留原请求。
 */
export async function api<T>(path:string, session='', body?:unknown, extra:Record<string,string>={}):Promise<T> {
  let response:Response;
  try {
    response = await fetch('/api/agent'+path,{method:body === undefined?'GET':'POST',credentials:'same-origin',
      headers:{'X-Workstation-Request':'1',...(session?{'X-Operator-Session':session}:{}),...(body === undefined?{}:{'Content-Type':'application/json'}),...extra},
      body:body === undefined?undefined:JSON.stringify(body)});
  } catch {throw new ApiError('LOCAL_CONNECTION_LOST','本机服务未连接，请检查程序是否正在运行',0);}
  const value = await response.json().catch(()=>null);
  if (!response.ok) throw new ApiError(value?.code ?? 'REQUEST_FAILED',value?.message ?? `请求未完成（${response.status}）`,response.status);
  return value as T;
}

/** 参数保持原字符串，URL编码不能把平台长ID转为Number。
 * @param scope 非空作业/任务/明细范围，ID须保留字符串。
 * @param extra 分页等可选参数，默认空对象；数值以字符串编码。
 * @returns 以问号开始的编码查询串，不修改原范围。
 */
export function query(scope:Scope, extra:Record<string,string|number>={}):string {
  return '?'+new URLSearchParams({...scope,...Object.fromEntries(Object.entries(extra).map(([k,v])=>[k,String(v)]))});
}

/** 业务拒绝明确失败；网络、5xx和身份中断保留原操作，防止模糊成功后改号重复。
 * @param error 捕获异常，可空；未知异常保守地视为结果未知。
 * @returns true须保留原请求核对，false可按明确拒绝修正后重新操作。
 */
export function unknownResult(error:unknown):boolean {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500 || error.status === 401;
}

/** 对操作员显示可恢复动作，未知错误码保留服务端中文提示。
 * @param error 捕获异常，可空或未知对象。
 * @returns 中文错误提示；无可读信息时返回通用提示，不抛异常。
 */
export function errorText(error:unknown):string {
  const code = error instanceof ApiError?error.code:'';
  const messages:Record<string,string> = {
    LOCAL_UNAUTHORIZED:'本机访问会话已失效，请刷新工作台',
    DEVICE_UNAUTHORIZED:'设备尚未授权，请由维护人员完成设备配对',
    PLATFORM_NOT_CONFIGURED:'尚未配置平台连接，可到设备设置查看',
    TASK_CACHE_EMPTY:'还没有当前员工的任务缓存，请先连接平台获取任务',
    OPERATOR_SESSION_EXPIRED:'员工授权已到期，请更新授权后再作业',
    JOB_CONTEXT_STALE:'作业未领取或已过期，请刷新任务后重新领取',
    SAMPLE_CHANGED:'重量已变化，请等待稳定后重新记录',
    BASKET_MANIFEST_CHANGED:'筐记录发生变化，请核对最新累计后重新完成',
    PRINT_IN_PROGRESS:'本次打印仍在处理，请等待结果，勿连续补打'
  };
  return messages[code] ?? (error instanceof Error?error.message:'操作未完成，请稍后重试');
}
