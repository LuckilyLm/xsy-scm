/** 库存预留只能查询与释放；新预留由正式订单业务触发，不允许前端调用通用创建接口直接占用库存。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {Id, InventoryReservation, InventoryReservationQuery} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryReservationApi = {
  query: (data: InventoryReservationQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryReservation>>('/scm/inventory/reservation/query', data, options),
  /** 释放预留：占用归还可用量。 */
  release: (id: Id) => postRequest<string>(`/scm/inventory/reservation/release/${id}`, {}),
};
export default inventoryReservationApi;
