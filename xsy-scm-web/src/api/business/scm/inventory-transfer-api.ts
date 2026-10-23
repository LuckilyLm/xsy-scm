/** 库存调拨分草稿、发出和收货两步。修改、取消只能在草稿阶段执行，发出与收货分别调用对应端点，不能一次直接完成。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryInTransit,
  InventoryTransfer,
  InventoryTransferAdd,
  InventoryTransferQuery,
} from '/@/views/business/scm/inventory/inventory-types';
export const inventoryTransferApi = {
  query: (data: InventoryTransferQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryTransfer>>('/scm/inventory/transfer/query', data, options),
  detail: (id: Id) => getRequest<InventoryTransfer>(`/scm/inventory/transfer/detail/${id}`, {}),
  /** 新建草稿，返回新单 id。 */
  create: (data: InventoryTransferAdd) => postRequest<Id>('/scm/inventory/transfer/create', data),
  /** 改草稿（仅 DRAFT 可改）。 */
  update: (id: Id, data: InventoryTransferAdd) => postRequest<string>(`/scm/inventory/transfer/update/${id}`, data),
  /** 发出：从源仓扣减，单据进入在途。 */
  ship: (id: Id) => postRequest<string>(`/scm/inventory/transfer/ship/${id}`, {}),
  /** 收货：向目标仓累加，单据完成。目标仓单位必须与调拨单位一致。 */
  receive: (id: Id) => postRequest<string>(`/scm/inventory/transfer/receive/${id}`, {}),
  /** 取消草稿（无库存影响）。在途不可取消。 */
  cancel: (id: Id) => postRequest<string>(`/scm/inventory/transfer/cancel/${id}`, {}),
  /** 删除草稿（逻辑删）。 */
  delete: (id: Id) => postRequest<string>(`/scm/inventory/transfer/delete/${id}`, {}),
  /** 在途库存报表：把已发出的调拨单展开成明细；不引入虚拟在途仓，因此在途量不出现在库存余额页。 */
  inTransit: () => getRequest<InventoryInTransit[]>('/scm/inventory/transfer/in-transit', {}),
};
export default inventoryTransferApi;
