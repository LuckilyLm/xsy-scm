import {getDownload, getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmAdvancedFilterField} from '/@/types/business/scm/advanced-filter';
import type {
  ImageBatchBindForm,
  ImageBatchRemoveForm,
  ImageCenterView,
  ImageMatchTarget,
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
  ShelfStatus,
  TagMode,
  UnboundImage,
} from '/@/types/business/scm/product';

// 拦截器已解包 ResponseDTO，与 Axios 返回类型不符。
export const productApi = {
  bundleComponents: (bundleSkuId: ProductId) =>
    getRequest<
      {
        id: ProductId;
        componentSkuId: ProductId;
        productName: string;
        specName: string;
        skuCode: string;
        saleUnit: string;
        quantityPerBundleSaleUnit: string;
      }[]
    >(`/scm/product/bundle/${bundleSkuId}/components`, {}),
  saveBundle: (form: {bundleSkuId: ProductId; version: number; components: {skuId: ProductId; quantityPerBundleSaleUnit: string}[]}) =>
    postRequest('/scm/product/bundle/replace', form),
  query: (form: ProductQuery, options?: RequestOptions) => postRequest<ProductPage<ProductRow>>('/scm/product/query', form, options),
  detail: (id: ProductId, options?: RequestOptions) => getRequest<ProductRow>(`/scm/product/detail/${id}`, {}, options),
  add: (form: ProductForm) => postRequest<ProductId>('/scm/product/add', form),
  update: (form: ProductForm) => postRequest<null>('/scm/product/update', form),
  status: (spuId: ProductId, version: number, status: ShelfStatus) =>
    postRequest('/scm/product/updateStatus', {
      spuId,
      version,
      status,
    }),
  delete: (spuId: ProductId, version: number) => postRequest('/scm/product/delete', {spuId, version}),
  // 批量命令整批一个事务：预校验不过时 updatedCount 为 0，失败行逐条返回。
  batchStatus: (items: ProductBatchItem[], target: {status?: ShelfStatus; masterStatus?: MasterStatus}) =>
    postRequest<ProductBatchResult>('/scm/product/batch/updateStatus', {items, ...target}),
  batchCategory: (items: ProductBatchItem[], categoryId: ProductId) =>
    postRequest<ProductBatchResult>('/scm/product/batch/updateCategory', {
      items,
      categoryId,
    }),
  batchTags: (items: ProductBatchItem[], tagIds: ProductId[], mode: TagMode) =>
    postRequest<ProductBatchResult>('/scm/product/batch/updateTags', {
      items,
      tagIds,
      mode,
    }),
  // 导入 0 错误才写入，失败整批回滚；UPDATE 需要 scm:product:update，隐藏入口不等于有权限。
  downloadImportTemplate: (mode: ProductImportMode) => getDownload('/scm/product/import/template', {mode}),
  importProducts: (file: File, mode: ProductImportMode) => {
    const data = new FormData();
    data.append('file', file);
    data.append('mode', mode);
    return postRequest<ProductImportResult>('/scm/product/import', data);
  },
  exportProducts: (form: ProductQuery) => postDownload('/scm/product/export', form),
  queryFilterFields: () => getRequest<ScmAdvancedFilterField[]>('/scm/product/query/filters', {}),
  exportColumns: () => getRequest<{key: string; title: string; costRestricted: boolean}[]>('/scm/product/export/columns', {}),
};

/** 图片中心：查询与四类批量写都以单个 SPU 为上下文，URL 一律由后端按 fileKey 现算。 */
export const productImageApi = {
  query: (spuId: ProductId) => getRequest<ImageCenterView>(`/scm/product/image/query?spuId=${spuId}`, {}),
  batchBind: (form: ImageBatchBindForm) => postRequest<null>('/scm/product/image/batch-bind', form),
  batchRemove: (form: ImageBatchRemoveForm) => postRequest<null>('/scm/product/image/batch-remove', form),
  setPrimary: (form: ImageSetPrimaryForm) => postRequest<null>('/scm/product/image/set-primary', form),
  reorder: (form: ImageReorderForm) => postRequest<null>('/scm/product/image/reorder', form),
  unboundImages: (limit = 100) => getRequest<UnboundImage[]>(`/scm/product/image/unbound?limit=${limit}`, {}),
  /** 批量按编码反查商品身份；一次最多 500 个编码，空结果表示全部未命中。 */
  matchTargets: (codes: string[], options?: RequestOptions) => postRequest<ImageMatchTarget[]>('/scm/product/image/match-targets', {codes}, options),
};
