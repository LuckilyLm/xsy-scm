import {postRequest, type RequestOptions} from '/@/lib/axios';
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
  query: (data: DemandQuery, options?: RequestOptions) =>
    postRequest('/scm/purchase/demand/query', data, options) as unknown as Promise<ScmResponse<ScmPage<Demand>>>,

  /** 订单汇总与库存缺口预览：只读，与生成同源取数，不写数据。 */
  summaryPreview: (data: DemandSummaryPreviewQuery, options?: RequestOptions) =>
    postRequest('/scm/purchase/demand/summary-preview', data, options) as unknown as Promise<ScmResponse<ScmPage<DemandSummaryRow>>>,

  /** 汇总窗口 `[startAt, endAt)` 内的已确认订单行 → 采购需求。 */
  generate: (data: DemandGenerate) => purchaseCommand<GenerateResult>('/scm/purchase/demand/generate', data),

  createBatch: (data: DemandBatchCreate) => purchaseCommand<DemandCalculationBatch>('/scm/purchase/demand/batch/create', data),

  generateBatch: (data: DemandBatchGenerate) => purchaseCommand<GenerateResult>('/scm/purchase/demand/batch/generate', data),

  /** 批次回看：只读，不重算；含库存与预留数字，需对应的查询权限。 */
  batchDetail: (data: DemandBatchGenerate, options?: RequestOptions) =>
    postRequest('/scm/purchase/demand/batch/detail', data, options) as unknown as Promise<ScmResponse<DemandBatchDetail>>,

  /** 把一条需求分配到某个采购行（`version` 是需求的版本）。 */
  allocate: (data: DemandAllocate) => purchaseCommand<Demand>('/scm/purchase/demand/allocate', data),
};

export default purchaseDemandApi;
