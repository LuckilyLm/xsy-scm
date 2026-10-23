/** 同仓库商品规格转换须经过审批，通过后才同时记转出和转入流水；驳回不改变库存。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryConversion,
  InventoryConversionAdd,
  InventoryConversionAudit,
  InventoryConversionQuery,
} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryConversionApi = {
  query: (data: InventoryConversionQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryConversion>>('/scm/inventory/conversion/query', data, options),
  detail: (id: Id) => getRequest<InventoryConversion>(`/scm/inventory/conversion/detail/${id}`, {}),
  /** 新建（创建即待审核），返回新单 id。 */
  create: (data: InventoryConversionAdd) => postRequest<Id>('/scm/inventory/conversion/create', data),
  /** 改待审核单据（仅 PENDING 可改）。 */
  update: (id: Id, data: InventoryConversionAdd) => postRequest<string>(`/scm/inventory/conversion/update/${id}`, data),
  /** 审批通过：写 CONVERT_OUT + CONVERT_IN 流水并调整两边余额。 */
  approve: (id: Id, data: InventoryConversionAudit) => postRequest<string>(`/scm/inventory/conversion/approve/${id}`, data),
  /** 驳回（不产生库存影响）；审核意见必填。 */
  reject: (id: Id, data: InventoryConversionAudit) => postRequest<string>(`/scm/inventory/conversion/reject/${id}`, data),
  /** 删除待审核单据（逻辑删）。 */
  delete: (id: Id) => postRequest<string>(`/scm/inventory/conversion/delete/${id}`, {}),
};
export default inventoryConversionApi;
