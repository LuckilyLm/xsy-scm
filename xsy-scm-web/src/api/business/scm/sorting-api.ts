/**
 * P1 分拣管理接口（仿 `delivery-api.ts` 的 `call` + 幂等命令两段式）。
 *
 * 幂等键只加在**会产生新事实或累加计次**的两个命令上：建单（`SORTING_TASK_CREATE`）与
 * 登记打印（`SORTING_PRINT:{id}`）。失败时保留同一 UUID 供重试，避免「一次网络抖动被算成两次打印」；
 * 成功后删除签名，让下一次真正的内容变化换用新键。
 *
 * 录入 / 完成 / 取消 / 重开**不带**幂等键：后端签名里没有 `Idempotency-Key`，
 * 它们的重复提交由明细行的乐观锁（`version`）拦截，多加一个头不会改变语义只会掩盖冲突。
 *
 * 打印预览是 GET：它与正式生成同源，但既不改状态也不计次。
 * 任何页面都不许用预览接口「顺手」计一次数。
 */
import {request} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    SortingActionPayload,
    SortingAssignPayload,
    SortingCandidateLine,
    SortingCandidateQuery,
    SortingEntryPayload,
    SortingPrint,
    SortingPrintResult,
    SortingSkuSummary,
    SortingSummaryQuery,
    SortingTask,
    SortingTaskCreatePayload,
    SortingTaskDetail,
    SortingTaskQuery,
} from '/@/views/business/scm/sorting/sorting-types';

const BASE = '/scm/sorting';

function call<T>(method: string, path: string, data?: unknown): Promise<ScmResponse<T>> {
    return request({
        url: `${BASE}${path}`,
        method,
        ...(method === 'get' ? {params: data} : {data}),
    }) as unknown as Promise<ScmResponse<T>>;
}

const idempotentKeys = new Map<string, string>();

async function command<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    const signature = path + JSON.stringify(data);
    let key = idempotentKeys.get(signature);
    if (!key) {
        key = crypto.randomUUID();
        idempotentKeys.set(signature, key);
    }
    const result = await request({
        url: `${BASE}${path}`,
        method: 'post',
        data,
        headers: {'Idempotency-Key': key},
    }) as unknown as ScmResponse<T>;
    idempotentKeys.delete(signature);
    return result;
}

export const sortingApi = {
    tasks: (query: SortingTaskQuery) => call<ScmPage<SortingTask>>('get', '/tasks', query),
    detail: (id: Id) => call<SortingTaskDetail>('get', `/tasks/${id}`),
    summary: (query: SortingSummaryQuery) => call<ScmPage<SortingSkuSummary>>('get', '/summary', query),
    candidateLines: (query: SortingCandidateQuery) => call<ScmPage<SortingCandidateLine>>('get', '/candidate-lines', query),
    /** 预览：只读、不计次；仅 SORTING / COMPLETED 可取，否则业务码 41121。 */
    printPreview: (id: Id) => call<SortingPrint>('get', `/tasks/${id}/print`),
    create: (form: SortingTaskCreatePayload) => command<SortingTaskDetail>('/tasks', form),
    assign: (id: Id, form: SortingAssignPayload) => call<string>('post', `/tasks/${id}/assign`, form),
    enter: (id: Id, form: SortingEntryPayload) => call<string>('post', `/tasks/${id}/entry`, form),
    complete: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/complete`, form),
    cancel: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/cancel`, form),
    reopen: (id: Id, form: SortingActionPayload) => call<string>('post', `/tasks/${id}/reopen`, form),
    /** 正式生成并登记计次；带 Idempotency-Key，累加由服务端的任务聚合锁串行化。 */
    print: (id: Id, form: SortingActionPayload) => command<SortingPrintResult>(`/tasks/${id}/print`, form),
};
