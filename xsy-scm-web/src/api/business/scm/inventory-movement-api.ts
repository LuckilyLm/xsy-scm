/** 库存流水仅供查询。历史流水不可编辑或删除，冲销要新增反向流水。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {InventoryMovement, InventoryMovementQuery} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryMovementApi = {
  query: (data: InventoryMovementQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryMovement>>('/scm/inventory/movement/query', data, options),
};
export default inventoryMovementApi;
