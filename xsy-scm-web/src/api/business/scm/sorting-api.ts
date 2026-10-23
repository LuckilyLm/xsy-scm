/** 建单和打印用幂等键，数量录入靠版本号防重；预览不计次。 */
import {request, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  SortingStandardPreview,
  SortingStandardPayload,
  SortingStandardResult,
  SortingActionPayload,
  SortingAssignPayload,
  SortingCandidateLine,
  SortingCustomerLine,
  SortingScaleEvent,
  SortingCandidateQuery,
  SortingEntryPayload,
  SortingPrint,
  SortingPrintResult,
  SortingPartPayload,
  SortingPartRecord,
  SortingPartHistory,
  SortingPerformance,
  SortingSkuSummary,
  SortingSummaryQuery,
  SortingTask,
  SortingTaskCreatePayload,
  SortingTaskDetail,
  SortingTaskQuery,
} from '/@/views/business/scm/sorting/sorting-types';

const BASE = '/scm/sorting';

function call<T>(method: string, path: string, data?: unknown, options?: RequestOptions): Promise<ScmResponse<T>> {
  return request<T>({
    url: `${BASE}${path}`,
    method,
    ...(method === 'get' ? {params: data} : {data}),
    ...options,
  });
}

const idempotentKeys = new Map<string, string>();

async function command<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
  const signature = path + JSON.stringify(data);
  let key = idempotentKeys.get(signature);
  if (!key) {
    key = crypto.randomUUID();
    idempotentKeys.set(signature, key);
  }
  const result = await request<T>({
    url: `${BASE}${path}`,
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
  idempotentKeys.delete(signature);
  return result;
}

export const sortingApi = {
  standardPreview: (id: Id) => call<SortingStandardPreview>('get', `/tasks/${id}/standard-preview`),
  confirmStandard: (id: Id, payload: SortingStandardPayload) => command<SortingStandardResult>(`/tasks/${id}/standard-confirm`, payload),
  tasks: (query: SortingTaskQuery, options?: RequestOptions) => call<ScmPage<SortingTask>>('get', '/tasks', query, options),
  detail: (id: Id) => call<SortingTaskDetail>('get', `/tasks/${id}`),
  partRecords: (taskId: Id, itemId: Id, beforeId?: Id) =>
    call<SortingPartHistory>('get', `/tasks/${taskId}/items/${itemId}/parts`, {limit: 50, ...(beforeId == null ? {} : {beforeId})}),
  appendPart: (id: Id, payload: SortingPartPayload) => command<SortingPartRecord>(`/tasks/${id}/parts`, payload),
  summary: (query: SortingSummaryQuery, options?: RequestOptions) => call<ScmPage<SortingSkuSummary>>('get', '/summary', query, options),
  customerLines: (query: SortingSummaryQuery, options?: RequestOptions) =>
    call<ScmPage<SortingCustomerLine>>('get', '/customer-lines', query, options),
  confirmedPerformance: (query: SortingSummaryQuery, options?: RequestOptions) =>
    call<ScmPage<SortingPerformance>>('get', '/confirmed-performance', query, options),
  candidateLines: (query: SortingCandidateQuery) => call<ScmPage<SortingCandidateLine>>('get', '/candidate-lines', query),
  /** 预览：只读不计次；仅分拣中或已完成的单据可取。 */
  printPreview: (id: Id) => call<SortingPrint>('get', `/tasks/${id}/print`),
  create: (form: SortingTaskCreatePayload) => command<SortingTaskDetail>('/tasks', form),
  assign: (id: Id, form: SortingAssignPayload) => call<string>('post', `/tasks/${id}/assign`, form),
  enter: (id: Id, form: SortingEntryPayload) => call<string>('post', `/tasks/${id}/entry`, form),
  complete: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/complete`, form),
  cancel: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/cancel`, form),
  reopen: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/reopen`, form),
  /** 正式生成并登记计次；带 Idempotency-Key，累加由服务端的任务聚合锁串行化。 */
  print: (id: Id, form: SortingActionPayload) => command<SortingPrintResult>(`/tasks/${id}/print`, form),
  /** 某任务的秤读数；`status` 为空返回全部（默认看全部，便于追溯被驳回的读数）。 */
  scaleEvents: (taskId: Id, status?: string) => call<SortingScaleEvent[]>('get', `/scale/tasks/${taskId}`, status ? {status} : {}),
  /** 接受稳定读数：非标品必须人工选择分拣结果，非正常结果附原因；数量不可修改。 */
  acceptScaleEvent: (id: Id, version: number, result?: string, reason?: string) =>
    call<string>('post', `/scale/${id}/accept`, {version, result, reason}),
  /** 驳回读数：不写分拣结果，留原因。 */
  rejectScaleEvent: (id: Id, version: number, reason: string) => call<string>('post', `/scale/${id}/reject`, {version, reason}),
};
