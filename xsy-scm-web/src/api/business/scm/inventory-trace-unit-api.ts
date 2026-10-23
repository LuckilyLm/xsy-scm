/** 包装码（一物一码）：发码与只读回放。码由系统生成、永不复用。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
import type {TraceUnit, TraceUnitBinding, TraceUnitIssue, TraceUnitQuery, TraceUnitReplay} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryTraceUnitApi = {
  query: (data: TraceUnitQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/unit/query', data, options) as unknown as Promise<ScmResponse<ScmPage<TraceUnit>>>,
  /** 按批次发码；发码总量受批次余额约束。 */
  issue: (data: TraceUnitIssue) => postRequest('/scm/inventory/unit/issue', data) as unknown as Promise<ScmResponse<TraceUnit[]>>,
  /** 分拣扫码绑定：一码一物，绑定后不能改绑。 */
  bind: (data: {unitCode: string; sortingTaskItemId: Id}) =>
    postRequest('/scm/inventory/unit/bind', data) as unknown as Promise<ScmResponse<TraceUnitBinding>>,
  /** 作废：只对未绑定的码生效，原码保留且不复用。 */
  voidUnit: (data: {unitCode: string; reason: string}) => postRequest('/scm/inventory/unit/void', data) as unknown as Promise<ScmResponse<string>>,
  /** 回放：单元 → 批次 → 收货来源 → 分拣绑定 → 出库去向。 */
  replay: (unitCode: string) =>
    getRequest(`/scm/inventory/unit/trace/${encodeURIComponent(unitCode)}`, {}) as unknown as Promise<ScmResponse<TraceUnitReplay>>,
};
