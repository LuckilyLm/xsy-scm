/** 报损报溢：创建、修改、审批、驳回。审批必须带版本号，防止批准过期数据。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryLossGain,
  InventoryLossGainAdd,
  InventoryLossGainAudit,
  InventoryLossGainQuery,
} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryLossGainApi = {
  query: (data: InventoryLossGainQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryLossGain>>('/scm/inventory/loss-gain/query', data, options),
  detail: (id: Id) => getRequest<InventoryLossGain>(`/scm/inventory/loss-gain/detail/${id}`, {}),
  /** 新建（创建即待审核），返回新单 id。 */
  create: (data: InventoryLossGainAdd) => postRequest<Id>('/scm/inventory/loss-gain/create', data),
  /** 改待审核单据（仅 PENDING 可改）。 */
  update: (id: Id, data: InventoryLossGainAdd) => postRequest<string>(`/scm/inventory/loss-gain/update/${id}`, data),
  /** 审批通过：写 LOSS_REPORT / GAIN_REPORT 流水并调整余额。 */
  approve: (id: Id, data: InventoryLossGainAudit) => postRequest<string>(`/scm/inventory/loss-gain/approve/${id}`, data),
  /** 驳回（不产生库存影响）；审核意见必填。 */
  reject: (id: Id, data: InventoryLossGainAudit) => postRequest<string>(`/scm/inventory/loss-gain/reject/${id}`, data),
  /** 删除待审核单据（逻辑删）。 */
  delete: (id: Id) => postRequest<string>(`/scm/inventory/loss-gain/delete/${id}`, {}),
};
export default inventoryLossGainApi;
