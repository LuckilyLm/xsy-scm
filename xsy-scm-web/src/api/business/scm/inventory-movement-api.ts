/** 库存流水仅供查询。历史流水不可编辑或删除，冲销要新增反向流水。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {InventoryMovement, InventoryMovementQuery} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryMovementApi = {
  query: (data: InventoryMovementQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/movement/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryMovement>>>,
};

export default inventoryMovementApi;
