/**
 * 库存出库单接口（出库波次新增文件）。
 *
 * 与后端 `InventoryOutboundController` 逐端点对应（7 个）：
 * query / detail / create / update / confirm / cancel / delete。
 *
 * **权限分两档**：录单与改草稿是日常操作（`add` / `update`），
 * **`confirm` 是独立权限** —— 它会真实扣减库存并写不可逆流水，
 * 允许仓管录单、由主管确认是完全合理的分工，因此不复用 `update`。
 *
 * `confirm` 失败时整单回滚，不存在「出一半」；重复确认会被状态机拒绝（41014）。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryOutbound,
    InventoryOutboundAdd,
    InventoryOutboundQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryOutboundApi = {
    query: (data: InventoryOutboundQuery) =>
        postRequest('/scm/inventory/outbound/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryOutbound>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/outbound/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryOutbound>
        >,
    /** 新建草稿，返回新单 id。 */
    create: (data: InventoryOutboundAdd) =>
        postRequest('/scm/inventory/outbound/create', data) as unknown as Promise<ScmResponse<Id>>,
    /** 改草稿（仅 DRAFT 可改）。 */
    update: (id: Id, data: InventoryOutboundAdd) =>
        postRequest(`/scm/inventory/outbound/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 确认出库：扣库存 + 写 SALES_OUT 流水。 */
    confirm: (id: Id) =>
        postRequest(`/scm/inventory/outbound/confirm/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /** 取消草稿（无库存影响）。 */
    cancel: (id: Id) =>
        postRequest(`/scm/inventory/outbound/cancel/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /** 删除草稿（逻辑删）。 */
    delete: (id: Id) =>
        postRequest(`/scm/inventory/outbound/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryOutboundApi;
