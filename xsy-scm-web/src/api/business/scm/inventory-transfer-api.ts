/** 库存调拨分草稿、发出和收货两步。修改、取消只能在草稿阶段执行，发出与收货分别调用对应端点，不能一次直接完成。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryInTransit,
    InventoryTransfer,
    InventoryTransferAdd,
    InventoryTransferQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryTransferApi = {
    query: (data: InventoryTransferQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/transfer/query', data, options) as unknown as Promise<
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
    /** 发出：从源仓扣减，单据进入在途。 */
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
     * 在途库存报表（只读聚合，不进 `inventory_balance`）。
     *
     * 把 SHIPPED 的调拨单展开成明细行，用于对账与库存查询 —— 这是「在途库存可见」的
     * 落地方式：不引入虚拟在途仓，而是以报表形式暴露。因此在途量不会出现在库存余额页。
     */
    inTransit: () =>
        getRequest('/scm/inventory/transfer/in-transit', {}) as unknown as Promise<
            ScmResponse<InventoryInTransit[]>
        >,
};

export default inventoryTransferApi;
