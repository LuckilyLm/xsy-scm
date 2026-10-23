import {getDownload, getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmAdvancedFilterField} from '/@/types/business/scm/advanced-filter';
import type {
  ImageBatchBindForm,
  ImageBatchRemoveForm,
  ImageCenterView,
  ImageReorderForm,
  ImageSetPrimaryForm,
  MasterStatus,
  ProductBatchItem,
  ProductBatchResult,
  ProductForm,
  ProductId,
  ProductImportMode,
  ProductImportResult,
  ProductPage,
  ProductQuery,
  ProductRow,
  ScmResponse,
  ShelfStatus,
  TagMode,
  UnboundImage,
} from '/@/types/business/scm/product';

// 拦截器已解包 ResponseDTO，与 Axios 返回类型不符。
export const productApi = {
  bundleComponents: (bundleSkuId: ProductId) =>
    getRequest(`/scm/product/bundle/${bundleSkuId}/components`, {}) as unknown as Promise<
      ScmResponse<
        {
          id: ProductId;
          componentSkuId: ProductId;
          productName: string;
          specName: string;
          skuCode: string;
          saleUnit: string;
          quantityPerBundleSaleUnit: string;
        }[]
      >
    >,
  saveBundle: (form: {bundleSkuId: ProductId; version: number; components: {skuId: ProductId; quantityPerBundleSaleUnit: string}[]}) =>
    postRequest('/scm/product/bundle/replace', form),
  query: (form: ProductQuery, options?: RequestOptions) =>
    postRequest('/scm/product/query', form, options) as unknown as Promise<ScmResponse<ProductPage<ProductRow>>>,
  detail: (id: ProductId, options?: RequestOptions) =>
    getRequest(`/scm/product/detail/${id}`, {}, options) as unknown as Promise<ScmResponse<ProductRow>>,
  add: (form: ProductForm) => postRequest('/scm/product/add', form) as unknown as Promise<ScmResponse<ProductId>>,
  update: (form: ProductForm) => postRequest('/scm/product/update', form) as unknown as Promise<ScmResponse<null>>,
  status: (spuId: ProductId, version: number, status: ShelfStatus) =>
    postRequest('/scm/product/updateStatus', {
      spuId,
      version,
      status,
    }),
  delete: (spuId: ProductId, version: number) => postRequest('/scm/product/delete', {spuId, version}),
  // 批量命令整批一个事务：预校验不过时 updatedCount 为 0，失败行逐条返回。
  batchStatus: (items: ProductBatchItem[], target: {status?: ShelfStatus; masterStatus?: MasterStatus}) =>
    postRequest('/scm/product/batch/updateStatus', {items, ...target}) as unknown as Promise<ScmResponse<ProductBatchResult>>,
  batchCategory: (items: ProductBatchItem[], categoryId: ProductId) =>
    postRequest('/scm/product/batch/updateCategory', {
      items,
      categoryId,
    }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
  batchTags: (items: ProductBatchItem[], tagIds: ProductId[], mode: TagMode) =>
    postRequest('/scm/product/batch/updateTags', {
      items,
      tagIds,
      mode,
    }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
  // 导入 0 错误才写入，失败整批回滚；UPDATE 需要 scm:product:update，隐藏入口不等于有权限。
  downloadImportTemplate: (mode: ProductImportMode) => getDownload('/scm/product/import/template', {mode}),
  importProducts: (file: File, mode: ProductImportMode) => {
    const data = new FormData();
    data.append('file', file);
    data.append('mode', mode);
    return postRequest('/scm/product/import', data) as unknown as Promise<ScmResponse<ProductImportResult>>;
  },
  exportProducts: (form: ProductQuery) => postDownload('/scm/product/export', form),
  queryFilterFields: () => getRequest('/scm/product/query/filters', {}) as unknown as Promise<ScmResponse<ScmAdvancedFilterField[]>>,
  exportColumns: () =>
    getRequest('/scm/product/export/columns', {}) as unknown as Promise<ScmResponse<{key: string; title: string; costRestricted: boolean}[]>>,
};

/** 图片中心：查询与四类批量写都以单个 SPU 为上下文，URL 一律由后端按 fileKey 现算。 */
export const productImageApi = {
  query: (spuId: ProductId) => getRequest(`/scm/product/image/query?spuId=${spuId}`, {}) as unknown as Promise<ScmResponse<ImageCenterView>>,
  batchBind: (form: ImageBatchBindForm) => postRequest('/scm/product/image/batch-bind', form) as unknown as Promise<ScmResponse<null>>,
  batchRemove: (form: ImageBatchRemoveForm) => postRequest('/scm/product/image/batch-remove', form) as unknown as Promise<ScmResponse<null>>,
  setPrimary: (form: ImageSetPrimaryForm) => postRequest('/scm/product/image/set-primary', form) as unknown as Promise<ScmResponse<null>>,
  reorder: (form: ImageReorderForm) => postRequest('/scm/product/image/reorder', form) as unknown as Promise<ScmResponse<null>>,
  unboundImages: (limit = 100) => getRequest(`/scm/product/image/unbound?limit=${limit}`, {}) as unknown as Promise<ScmResponse<UnboundImage[]>>,
};
