/** 库存预留只能查询与释放；新预留由正式订单业务触发，不允许前端调用通用创建接口直接占用库存。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryReservation,
    InventoryReservationQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryReservationApi = {
    query: (data: InventoryReservationQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/reservation/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryReservation>>
        >,
    /** 释放预留：占用归还可用量。 */
    release: (id: Id) =>
        postRequest(`/scm/inventory/reservation/release/${id}`, {}) as unknown as Promise<
            ScmResponse<string>
        >,
};

export default inventoryReservationApi;
