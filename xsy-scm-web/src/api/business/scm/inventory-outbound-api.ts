/** 库存出库单：草稿、确认、取消、查询。确认后形成出库流水，已确认的不能直接编辑。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {Id, InventoryOutbound, InventoryOutboundAdd, InventoryOutboundQuery} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryOutboundApi = {
  query: (data: InventoryOutboundQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryOutbound>>('/scm/inventory/outbound/query', data, options),
  detail: (id: Id) => getRequest<InventoryOutbound>(`/scm/inventory/outbound/detail/${id}`, {}),
  /** 新建草稿，返回新单 id。 */
  create: (data: InventoryOutboundAdd) => postRequest<Id>('/scm/inventory/outbound/create', data),
  /** 改草稿（仅 DRAFT 可改）。 */
  update: (id: Id, data: InventoryOutboundAdd) => postRequest<string>(`/scm/inventory/outbound/update/${id}`, data),
  /** 确认出库：扣库存 + 写 SALES_OUT 流水。 */
  confirm: (id: Id) => postRequest<string>(`/scm/inventory/outbound/confirm/${id}`, {}),
  /** 取消草稿（无库存影响）。 */
  cancel: (id: Id) => postRequest<string>(`/scm/inventory/outbound/cancel/${id}`, {}),
  /** 删除草稿（逻辑删）。 */
  delete: (id: Id) => postRequest<string>(`/scm/inventory/outbound/delete/${id}`, {}),
};
export default inventoryOutboundApi;
