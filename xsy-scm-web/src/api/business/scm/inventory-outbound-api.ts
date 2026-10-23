/** 库存出库单：草稿、确认、取消、查询。确认后形成出库流水，已确认的不能直接编辑。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Id, InventoryOutbound, InventoryOutboundAdd, InventoryOutboundQuery} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryOutboundApi = {
  query: (data: InventoryOutboundQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/outbound/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryOutbound>>>,
  detail: (id: Id) => getRequest(`/scm/inventory/outbound/detail/${id}`, {}) as unknown as Promise<ScmResponse<InventoryOutbound>>,
  /** 新建草稿，返回新单 id。 */
  create: (data: InventoryOutboundAdd) => postRequest('/scm/inventory/outbound/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 改草稿（仅 DRAFT 可改）。 */
  update: (id: Id, data: InventoryOutboundAdd) =>
    postRequest(`/scm/inventory/outbound/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 确认出库：扣库存 + 写 SALES_OUT 流水。 */
  confirm: (id: Id) => postRequest(`/scm/inventory/outbound/confirm/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
  /** 取消草稿（无库存影响）。 */
  cancel: (id: Id) => postRequest(`/scm/inventory/outbound/cancel/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
  /** 删除草稿（逻辑删）。 */
  delete: (id: Id) => postRequest(`/scm/inventory/outbound/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryOutboundApi;
