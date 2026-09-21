/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/purchase/purchase-receive-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：`add`（C 直接新增收货单并自填状态）、C 的 `confirmInbound`（B1 改为受控的 `putaway` 命令）、
      `batchDelete(idList)` 裸数组。
适配：API 前缀 → `/scm/purchase/receipt/**`（A6）；补 `Idempotency-Key`（A7）；补 `version`（A8）；
      **状态由命令驱动**（A16）：`create` 一律 DRAFT，`confirm` 才 CONFIRMED；
      收货行由服务端按采购单活动行生成，前端不提交行。
验收：W5 单测、TS 棘轮与 Playwright。 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import {purchaseCommand} from '/@/api/business/scm/purchase-order-api';
import type {
    Id,
    Receipt,
    ReceiptConfirmPayload,
    ReceiptCreatePayload,
    ReceiptDeletePayload,
    ReceiptItem,
    ReceiptPutawayPayload,
    ReceiptQuery,
    ReceiptUpdatePayload,
    ReceiptVersionPayload,
} from '/@/views/business/scm/purchase/purchase-types';

export const purchaseReceiptApi = {
    query: (data: ReceiptQuery) =>
        postRequest('/scm/purchase/receipt/query', data) as unknown as Promise<ScmResponse<ScmPage<Receipt>>>,
    detail: (id: Id) =>
        getRequest(`/scm/purchase/receipt/detail/${id}`, {}) as unknown as Promise<ScmResponse<Receipt>>,
    items: (receiptId: Id) =>
        getRequest(`/scm/purchase/receipt/item/${receiptId}`, {}) as unknown as Promise<
            ScmResponse<ReceiptItem[]>
        >,

    /** 建草稿收货单：行由服务端按采购单**全部活动行**生成。 */
    create: (data: ReceiptCreatePayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/create', data),
    /** `update` **只允许改备注**（数量只在 `confirm` 一次性落库）。 */
    update: (data: ReceiptUpdatePayload) =>
        postRequest('/scm/purchase/receipt/update', data) as unknown as Promise<ScmResponse<Receipt>>,
    /** 确认收货：必须覆盖**全部**收货行（40998），非标品必须带实重（40083）。 */
    confirm: (data: ReceiptConfirmPayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/confirm', data),
    /** 仓库确认入库（B1）：仅 WAREHOUSE_CONFIRM 且 PENDING 的已确认收货单。 */
    putaway: (data: ReceiptPutawayPayload) => purchaseCommand<Receipt>('/scm/purchase/receipt/putaway', data),
    delete: (data: ReceiptDeletePayload) =>
        postRequest('/scm/purchase/receipt/delete', data) as unknown as Promise<ScmResponse<string>>,
    /** 批量删除草稿收货单：`PurchaseReceiptBatchDeleteForm` 的元素要求 `id` + `version`。 */
    batchDelete: (receipts: ReceiptVersionPayload[]) =>
        postRequest('/scm/purchase/receipt/batch-delete', {receipts}) as unknown as Promise<
            ScmResponse<string>
        >,
};

export default purchaseReceiptApi;
