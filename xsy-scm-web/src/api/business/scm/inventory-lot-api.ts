/**
 * 批次库存与批次流水：只读。
 *
 * 批次余额是子账，写入只发生在采购收货确认与销售出库命令里（与总账同事务），因此这里没有任何写接口。
 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    InventoryLotBalance,
    InventoryLotBalanceQuery,
    InventoryLotMovement,
    InventoryLotMovementQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryLotApi = {
    /** 批次余额；只返回仍有数量的批次。 */
    queryBalance: (data: InventoryLotBalanceQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/lot/balance/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryLotBalance>>
        >,
    /** 批次流水；数量有符号，入库为正、出库为负。 */
    queryMovement: (data: InventoryLotMovementQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/lot/movement/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryLotMovement>>
        >,
};
