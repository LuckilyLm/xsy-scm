/** 包装码更正：申请、他人审批、解绑。三步都留痕，批准后码回到可绑定状态。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {TraceCorrection, TraceCorrectionDecision, TraceCorrectionQuery} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryTraceCorrectionApi = {
  query: (data: TraceCorrectionQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/unit/correction/query', data, options) as unknown as Promise<ScmResponse<ScmPage<TraceCorrection>>>,
  apply: (data: {unitCode: string; reason: string}) =>
    postRequest('/scm/inventory/unit/correction/apply', data) as unknown as Promise<ScmResponse<TraceCorrection>>,
  approve: (data: TraceCorrectionDecision) =>
    postRequest('/scm/inventory/unit/correction/approve', data) as unknown as Promise<ScmResponse<TraceCorrection>>,
  reject: (data: TraceCorrectionDecision) =>
    postRequest('/scm/inventory/unit/correction/reject', data) as unknown as Promise<ScmResponse<TraceCorrection>>,
};
