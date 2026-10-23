/** 库存余额接口只提供查询和详情，不允许直接修改数量。余额必须通过业务入出库流水更新。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {Id, InventoryBalance, InventoryBalanceQuery} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryBalanceApi = {
  query: (data: InventoryBalanceQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/balance/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryBalance>>>,
  detail: (id: Id) => getRequest(`/scm/inventory/balance/detail/${id}`, {}) as unknown as Promise<ScmResponse<InventoryBalance>>,
};

export default inventoryBalanceApi;
