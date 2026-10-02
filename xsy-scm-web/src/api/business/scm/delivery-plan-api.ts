/**
 * 辅助排线建议与 GPS 轨迹接口。
 *
 * 排线建议分三步：生成（只算建议，不碰线路）→ 查看 → 显式应用（写回停靠顺序）。
 * 生成与应用在后端是两个独立权限：只看怎么排的人不该顺手获得改线路的能力。
 *
 * 轨迹上报带 `eventKey` 幂等键，重复上报返回既有记录并置 `duplicated`，**不是错误** ——
 * 弱网重试是常态。范围由服务端按司机维度判定，前端不传、也传不了。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
import type {DeliveryGpsEvent, DeliveryPlanProposal, Id} from '/@/views/business/scm/delivery/delivery-types';

export const deliveryPlanApi = {
    /** 生成建议（会作废该线路原有的待确认建议）。 */
    propose: (routeId: Id) =>
        postRequest(`/scm/delivery/plan/route/${routeId}/propose`, {}) as unknown as Promise<
            ScmResponse<DeliveryPlanProposal>
        >,

    /** 建议历史（最新在前）。 */
    history: (routeId: Id) =>
        getRequest(`/scm/delivery/plan/route/${routeId}/history`, {}) as unknown as Promise<
            ScmResponse<DeliveryPlanProposal[]>
        >,

    /** 应用建议；`version` 是**线路**版本。 */
    apply: (proposalId: Id, version: number) =>
        postRequest(`/scm/delivery/plan/proposal/${proposalId}/apply`, {version}) as unknown as Promise<
            ScmResponse<string>
        >,

    /** 放弃建议；不改线路。 */
    discard: (proposalId: Id, version: number) =>
        postRequest(`/scm/delivery/plan/proposal/${proposalId}/discard`, {version}) as unknown as Promise<
            ScmResponse<string>
        >,
};

export const deliveryGpsApi = {
    /** 轨迹查询 / 回放（按采集时间升序）。 */
    query: (form: {routeId: Id; from?: string; to?: string; limit?: number}) =>
        postRequest('/scm/delivery/gps/query', form) as unknown as Promise<ScmResponse<DeliveryGpsEvent[]>>,
};

export default deliveryPlanApi;
