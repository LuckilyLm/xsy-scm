/** 包装码更正：申请、他人审批、解绑。三步都留痕，批准后码回到可绑定状态。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {TraceCorrection, TraceCorrectionDecision, TraceCorrectionQuery} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryTraceCorrectionApi = {
  query: (data: TraceCorrectionQuery, options?: RequestOptions) =>
    postRequest<ScmPage<TraceCorrection>>('/scm/inventory/unit/correction/query', data, options),
  apply: (data: {unitCode: string; reason: string}) => postRequest<TraceCorrection>('/scm/inventory/unit/correction/apply', data),
  approve: (data: TraceCorrectionDecision) => postRequest<TraceCorrection>('/scm/inventory/unit/correction/approve', data),
  reject: (data: TraceCorrectionDecision) => postRequest<TraceCorrection>('/scm/inventory/unit/correction/reject', data),
};
