import { getRequest, postRequest } from '/@/lib/axios';
import type {
  MasterStatus, ProductBatchItem, ProductBatchResult, ProductForm, ProductId, ProductPage, ProductQuery, ProductRow, ScmResponse, ShelfStatus, TagMode,
} from '/@/types/business/scm/product';

// SmartAdmin interceptor resolves ResponseDTO directly, despite Axios's declared return type.
export const productApi = {
  query: (form: ProductQuery) => postRequest('/scm/product/query', form) as unknown as Promise<ScmResponse<ProductPage<ProductRow>>>,
  detail: (id: ProductId) => getRequest(`/scm/product/detail/${id}`, {}) as unknown as Promise<ScmResponse<ProductRow>>,
  add: (form: ProductForm) => postRequest('/scm/product/add', form) as unknown as Promise<ScmResponse<ProductId>>,
  update: (form: ProductForm) => postRequest('/scm/product/update', form) as unknown as Promise<ScmResponse<null>>,
  status: (spuId: ProductId, version: number, status: ShelfStatus) => postRequest('/scm/product/updateStatus', { spuId, version, status }),
  delete: (spuId: ProductId, version: number) => postRequest('/scm/product/delete', { spuId, version }),
  // 批量命令整批一个事务：预校验不过时 updatedCount 为 0 且没有任何写入，失败行逐条回给页面。
  batchStatus: (items: ProductBatchItem[], target: { status?: ShelfStatus; masterStatus?: MasterStatus }) =>
    postRequest('/scm/product/batch/updateStatus', { items, ...target }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
  batchCategory: (items: ProductBatchItem[], categoryId: ProductId) =>
    postRequest('/scm/product/batch/updateCategory', { items, categoryId }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
  batchTags: (items: ProductBatchItem[], tagIds: ProductId[], mode: TagMode) =>
    postRequest('/scm/product/batch/updateTags', { items, tagIds, mode }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
};
