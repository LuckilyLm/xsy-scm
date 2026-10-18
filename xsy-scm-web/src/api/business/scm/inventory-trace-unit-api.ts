/**
 * 包装码（一物一码）：发码与只读回放。
 *
 * 码由系统生成、不透明、永不复用；页面不提供「改码 / 删码」，作废只能追加事件（后续版本）。
 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    TraceUnit,
    TraceUnitIssue,
    TraceUnitQuery,
    TraceUnitReplay,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryTraceUnitApi = {
    query: (data: TraceUnitQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/unit/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<TraceUnit>>
        >,
    /** 按批次发码；发码总量受批次余额约束。 */
    issue: (data: TraceUnitIssue) =>
        postRequest('/scm/inventory/unit/issue', data) as unknown as Promise<ScmResponse<TraceUnit[]>>,
    /** 回放：单元 → 批次 → 收货来源 → 出库去向。 */
    replay: (unitCode: string) =>
        getRequest(`/scm/inventory/unit/trace/${encodeURIComponent(unitCode)}`, {}) as unknown as Promise<
            ScmResponse<TraceUnitReplay>
        >,
};
