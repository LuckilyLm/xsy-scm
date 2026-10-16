/** 报损报溢支持创建、修改、审批和驳回。录单与审批权限分开；审批必须携带版本号，防止批准过期数据。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryLossGain,
    InventoryLossGainAdd,
    InventoryLossGainAudit,
    InventoryLossGainQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryLossGainApi = {
    query: (data: InventoryLossGainQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/loss-gain/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryLossGain>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/loss-gain/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryLossGain>
        >,
    /** 新建（创建即待审核），返回新单 id。 */
    create: (data: InventoryLossGainAdd) =>
        postRequest('/scm/inventory/loss-gain/create', data) as unknown as Promise<ScmResponse<Id>>,
    /** 改待审核单据（仅 PENDING 可改）。 */
    update: (id: Id, data: InventoryLossGainAdd) =>
        postRequest(`/scm/inventory/loss-gain/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 审批通过：写 LOSS_REPORT / GAIN_REPORT 流水并调整余额。 */
    approve: (id: Id, data: InventoryLossGainAudit) =>
        postRequest(`/scm/inventory/loss-gain/approve/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 驳回（不产生库存影响）；审核意见必填。 */
    reject: (id: Id, data: InventoryLossGainAudit) =>
        postRequest(`/scm/inventory/loss-gain/reject/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 删除待审核单据（逻辑删）。 */
    delete: (id: Id) =>
        postRequest(`/scm/inventory/loss-gain/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryLossGainApi;
