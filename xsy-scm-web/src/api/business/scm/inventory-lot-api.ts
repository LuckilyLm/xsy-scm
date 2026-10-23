/** 批次库存与流水只读；写入只发生在收货确认和销售出库。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {
  InventoryLotBalance,
  InventoryLotBalanceQuery,
  InventoryLotMovement,
  InventoryLotMovementQuery,
} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryLotApi = {
  /** 批次余额；只返回仍有数量的批次。 */
  queryBalance: (data: InventoryLotBalanceQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryLotBalance>>('/scm/inventory/lot/balance/query', data, options),
  /** 批次流水；数量有符号，入库为正、出库为负。 */
  queryMovement: (data: InventoryLotMovementQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryLotMovement>>('/scm/inventory/lot/movement/query', data, options),
};
