/* 收货行由服务端按采购单活动行生成，前端不提交行。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import {purchaseCommand} from '/@/api/business/scm/purchase-order-api';
import type {
  Id,
  Receipt,
  ReceiptConfirmPayload,
  ReceiptCreatePayload,
  ReceiptDeletePayload,
  ReceiptItem,
  ReceiptItemWorkbenchQuery,
  ReceiptItemWorkbenchRow,
  ReceiptPutawayPayload,
  ReceiptQuery,
  ReceiptUpdatePayload,
  ReceiptVersionPayload,
} from '/@/views/business/scm/purchase/purchase-types';
export const purchaseReceiptApi = {
  query: (data: ReceiptQuery, options?: RequestOptions) => postRequest<ScmPage<Receipt>>('/scm/purchase/receipt/query', data, options),
  detail: (id: Id, options?: RequestOptions) => getRequest<Receipt>(`/scm/purchase/receipt/detail/${id}`, {}, options),
  items: (receiptId: Id, options?: RequestOptions) => getRequest<ReceiptItem[]>(`/scm/purchase/receipt/item/${receiptId}`, {}, options),
  /** 按商品收货工作台：只读，按商品规格和采购单位归并。 */
  itemWorkbench: (data: ReceiptItemWorkbenchQuery, options?: RequestOptions) =>
    postRequest<ScmPage<ReceiptItemWorkbenchRow>>('/scm/purchase/receipt/item-workbench/query', data, options),
  /** 建草稿收货单：行由服务端按采购单全部活动行生成。 */
  create: (data: ReceiptCreatePayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/create', data),
  /** `update` 只允许改备注，数量在确认时一次写入。 */
  update: (data: ReceiptUpdatePayload) => postRequest<Receipt>('/scm/purchase/receipt/update', data),
  /** 确认收货：必须覆盖全部收货行，非标品必须带实重。 */
  confirm: (data: ReceiptConfirmPayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/confirm', data),
  /** 仓库确认入库：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。 */
  putaway: (data: ReceiptPutawayPayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/putaway', data),
  delete: (data: ReceiptDeletePayload) => postRequest<string>('/scm/purchase/receipt/delete', data),
  /** 批量删除草稿收货单：`PurchaseReceiptBatchDeleteForm` 的元素要求 `id` + `version`。 */
  batchDelete: (receipts: ReceiptVersionPayload[]) => postRequest<string>('/scm/purchase/receipt/batch-delete', {receipts}),
};
export default purchaseReceiptApi;
