/** 维护阈值不改库存；预警状态仍由可用量判定。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryWarningThreshold,
  InventoryWarningThresholdAdd,
  InventoryWarningThresholdQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryWarningThresholdApi = {
  query: (data: InventoryWarningThresholdQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/threshold/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryWarningThreshold>>>,
  detail: (id: Id) => getRequest(`/scm/inventory/threshold/detail/${id}`, {}) as unknown as Promise<ScmResponse<InventoryWarningThreshold>>,
  /** 新建；同一 (仓库, SKU) 只允许一条（后端 41050）。 */
  create: (data: InventoryWarningThresholdAdd) => postRequest('/scm/inventory/threshold/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 编辑；可把某个边界清空（传 null 即清空）。 */
  update: (id: Id, data: InventoryWarningThresholdAdd) =>
    postRequest(`/scm/inventory/threshold/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 删除（逻辑删）；删除后该 (仓库, SKU) 不再产生预警。 */
  delete: (id: Id) => postRequest(`/scm/inventory/threshold/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryWarningThresholdApi;
