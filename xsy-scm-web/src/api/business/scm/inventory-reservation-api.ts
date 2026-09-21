/**
 * 库存预留接口（出库波次新增文件）。
 *
 * 与后端 `InventoryReservationController` 逐端点对应（2 个）：
 * query / release。
 *
 * **没有 create 端点**：预留是**业务动作的副产物**，不是人手工录的单据。
 * 本波次由销售订单确认触发（后端 `InventoryReservationService.reserve`），
 * 前端只做查看与释放。一个不存在的函数比一个会返回 404 的函数更能说明这一点。
 *
 * 释放只对「生效中」的预留有效；重复释放会被拒绝（41016），不会把可用量虚增。
 */
import {postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryReservation,
    InventoryReservationQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryReservationApi = {
    query: (data: InventoryReservationQuery) =>
        postRequest('/scm/inventory/reservation/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryReservation>>
        >,
    /** 释放预留：占用归还可用量。 */
    release: (id: Id) =>
        postRequest(`/scm/inventory/reservation/release/${id}`, {}) as unknown as Promise<
            ScmResponse<string>
        >,
};

export default inventoryReservationApi;
