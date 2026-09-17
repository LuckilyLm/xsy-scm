/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/purchase/purchase-generate-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：`calculateStock`（A-D3：W5 不做库存抵扣）、`preview`（A-D3：不做库存/汇总预览端点）、
      预览返回体 `PurchaseGeneratePreview*`（W5 只回 `GenerateResult`）。
适配：API 前缀 → `/scm/purchase/demand/**`（A6）；补 `Idempotency-Key`（A7）；
      `startTime/endTime` → 半开区间 `startAt/endAt`（A21 / Q6a）；补 `warehouseId` / `supplierId`；
      新增 `query`（需求列表）与 `allocate`（把需求分配到采购行）。
验收：W5 单测、TS 棘轮与 Playwright。 */
import { postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import { purchaseCommand } from '/@/api/business/scm/purchase-order-api';
import type {
  Demand,
  DemandAllocate,
  DemandGenerate,
  DemandQuery,
  GenerateResult,
} from '/@/views/business/scm/purchase/purchase-types';

export const purchaseDemandApi = {
  query: (data: DemandQuery) =>
    postRequest('/scm/purchase/demand/query', data) as unknown as Promise<ScmResponse<ScmPage<Demand>>>,

  /** 汇总窗口 `[startAt, endAt)` 内的已确认订单行 → 采购需求。 */
  generate: (data: DemandGenerate) => purchaseCommand<GenerateResult>('/scm/purchase/demand/generate', data),

  /** 把一条需求分配到某个采购行（`version` 是**需求**的版本）。 */
  allocate: (data: DemandAllocate) => purchaseCommand<Demand>('/scm/purchase/demand/allocate', data),
};

export default purchaseDemandApi;
