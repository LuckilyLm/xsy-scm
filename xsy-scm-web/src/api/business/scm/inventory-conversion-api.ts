/** 同仓库商品规格转换须经过审批，通过后才同时记转出和转入流水；驳回不改变库存。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryConversion,
  InventoryConversionAdd,
  InventoryConversionAudit,
  InventoryConversionQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryConversionApi = {
  query: (data: InventoryConversionQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/conversion/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryConversion>>>,
  detail: (id: Id) => getRequest(`/scm/inventory/conversion/detail/${id}`, {}) as unknown as Promise<ScmResponse<InventoryConversion>>,
  /** 新建（创建即待审核），返回新单 id。 */
  create: (data: InventoryConversionAdd) => postRequest('/scm/inventory/conversion/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 改待审核单据（仅 PENDING 可改）。 */
  update: (id: Id, data: InventoryConversionAdd) =>
    postRequest(`/scm/inventory/conversion/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 审批通过：写 CONVERT_OUT + CONVERT_IN 流水并调整两边余额。 */
  approve: (id: Id, data: InventoryConversionAudit) =>
    postRequest(`/scm/inventory/conversion/approve/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 驳回（不产生库存影响）；审核意见必填。 */
  reject: (id: Id, data: InventoryConversionAudit) =>
    postRequest(`/scm/inventory/conversion/reject/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 删除待审核单据（逻辑删）。 */
  delete: (id: Id) => postRequest(`/scm/inventory/conversion/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryConversionApi;
