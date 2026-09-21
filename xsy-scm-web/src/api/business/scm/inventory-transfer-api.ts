/**
 * 库存调拨单接口（调拨波次新增文件）。
 *
 * 与后端 `InventoryTransferController` 逐端点对应（8 个）：
 * query / detail / create / update / ship / receive / cancel / delete。
 *
 * **两步式**：`ship` 与 `receive` 是两个**独立权限**，因为它们通常由不同的人执行
 * （源仓发货、目标仓点收）。由同一个人两头都确认会让在途数量失去复核 ——
 * 而在途数量正是最容易出错的地方。
 *
 * `ship` 从源仓扣减（进入在途），`receive` 向目标仓累加（单据完成）。
 * 两步各自只影响一个仓库，因此不存在跨仓的并发问题。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryInTransit,
    InventoryTransfer,
    InventoryTransferAdd,
    InventoryTransferQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryTransferApi = {
    query: (data: InventoryTransferQuery) =>
        postRequest('/scm/inventory/transfer/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryTransfer>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/transfer/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryTransfer>
        >,
    /** 新建草稿，返回新单 id。 */
    create: (data: InventoryTransferAdd) =>
        postRequest('/scm/inventory/transfer/create', data) as unknown as Promise<ScmResponse<Id>>,
    /** 改草稿（仅 DRAFT 可改）。 */
    update: (id: Id, data: InventoryTransferAdd) =>
        postRequest(`/scm/inventory/transfer/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 发出：从源仓扣减，单据进入**在途**。 */
    ship: (id: Id) =>
        postRequest(`/scm/inventory/transfer/ship/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /** 收货：向目标仓累加，单据完成。目标仓单位必须与调拨单位一致（41044）。 */
    receive: (id: Id) =>
        postRequest(`/scm/inventory/transfer/receive/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /** 取消草稿（无库存影响）。在途不可取消。 */
    cancel: (id: Id) =>
        postRequest(`/scm/inventory/transfer/cancel/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /** 删除草稿（逻辑删）。 */
    delete: (id: Id) =>
        postRequest(`/scm/inventory/transfer/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
    /**
     * 在途库存报表（只读聚合，**不进 `inventory_balance`**）。
     *
     * 把 SHIPPED 的调拨单展开成明细行，用于对账与库存查询 —— 这是「在途库存可见」的
     * 落地方式：不引入虚拟在途仓，而是以报表形式暴露。因此**在途量不会出现在库存余额页**。
     */
    inTransit: () =>
        getRequest('/scm/inventory/transfer/in-transit', {}) as unknown as Promise<
            ScmResponse<InventoryInTransit[]>
        >,
};

export default inventoryTransferApi;
