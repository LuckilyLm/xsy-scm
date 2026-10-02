/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/purchase/purchase-generate-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：`calculateStock`（A-D3：W5 不做库存抵扣）、`preview`（A-D3：不做库存/汇总预览端点）、
      预览返回体 `PurchaseGeneratePreview*`（W5 只回 `GenerateResult`）。
适配：API 前缀 → `/scm/purchase/demand/**`（A6）；补 `Idempotency-Key`（A7）；
      `startTime/endTime` → 半开区间 `startAt/endAt`（A21 / Q6a）；补 `warehouseId` / `supplierId`；
      新增 `query`（需求列表）与 `allocate`（把需求分配到采购行）。
验收：W5 单测、TS 棘轮与 Playwright。 */
import {postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import {purchaseCommand} from '/@/api/business/scm/purchase-order-api';
import type {
    Demand,
    DemandAllocate,
    DemandGenerate,
    DemandQuery,
    DemandSummaryPreviewQuery,
    DemandSummaryRow,
    GenerateResult,
    DemandBatchCreate,
    DemandBatchGenerate,
    DemandBatchDetail,
    DemandCalculationBatch,
} from '/@/views/business/scm/purchase/purchase-types';

export const purchaseDemandApi = {
    query: (data: DemandQuery) =>
        postRequest('/scm/purchase/demand/query', data) as unknown as Promise<ScmResponse<ScmPage<Demand>>>,

    /**
     * 订单汇总 / 库存缺口预览（Wave 2A §6A，只读辅助决策）。
     *
     * 与 `generate` 同取数口径但不落任何数据、不改需求语义；数量全部后端算好，前端只渲染。
     * 复用 `scm:purchase:demand:query` 权限，无幂等键（读操作）。
     */
    summaryPreview: (data: DemandSummaryPreviewQuery) =>
        postRequest('/scm/purchase/demand/summary-preview', data) as unknown as Promise<
            ScmResponse<ScmPage<DemandSummaryRow>>
        >,

    /** 汇总窗口 `[startAt, endAt)` 内的已确认订单行 → 采购需求。 */
    generate: (data: DemandGenerate) => purchaseCommand<GenerateResult>('/scm/purchase/demand/generate', data),

    createBatch: (data: DemandBatchCreate) =>
        purchaseCommand<DemandCalculationBatch>('/scm/purchase/demand/batch/create', data),

    generateBatch: (data: DemandBatchGenerate) =>
        purchaseCommand<GenerateResult>('/scm/purchase/demand/batch/generate', data),

    /**
     * 冻结批次回看（只读：不重算、不写业务表）。
     *
     * 返回体含该仓的库存与预留数字，因此接口按 `scm:purchase:demand:batch:query`
     * AND `scm:inventory:balance:query` 鉴权；前端按钮的 `v-privilege` 只是体验，不是权限边界。
     */
    batchDetail: (data: DemandBatchGenerate) =>
        postRequest('/scm/purchase/demand/batch/detail', data) as unknown as Promise<
            ScmResponse<DemandBatchDetail>
        >,

    /** 把一条需求分配到某个采购行（`version` 是**需求**的版本）。 */
    allocate: (data: DemandAllocate) => purchaseCommand<Demand>('/scm/purchase/demand/allocate', data),
};

export default purchaseDemandApi;
