/** 排线建议：生成、查看、应用。生成与应用是独立权限。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {DeliveryPlanProposal, Id} from '/@/views/business/scm/delivery/delivery-types';
export const deliveryPlanApi = {
  /** 生成建议（会作废该线路原有的待确认建议）。 */
  propose: (routeId: Id) => postRequest<DeliveryPlanProposal>(`/scm/delivery/plan/route/${routeId}/propose`, {}),
  /** 建议历史（最新在前）。 */
  history: (routeId: Id, options?: RequestOptions) => getRequest<DeliveryPlanProposal[]>(`/scm/delivery/plan/route/${routeId}/history`, {}, options),
  /** 应用建议；`version` 是线路版本。 */
  apply: (proposalId: Id, version: number) => postRequest<string>(`/scm/delivery/plan/proposal/${proposalId}/apply`, {version}),
  /** 放弃建议；不改线路。 */
  discard: (proposalId: Id, version: number) => postRequest<string>(`/scm/delivery/plan/proposal/${proposalId}/discard`, {version}),
};
export default deliveryPlanApi;
