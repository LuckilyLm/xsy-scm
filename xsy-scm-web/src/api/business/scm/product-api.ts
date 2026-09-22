import {getDownload, getRequest, postDownload, postRequest} from '/@/lib/axios';
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
    ProductImportResult,
    ProductPage,
    ProductQuery,
    ProductRow,
    ScmResponse,
    ShelfStatus,
    TagMode,
} from '/@/types/business/scm/product';

// SmartAdmin interceptor resolves ResponseDTO directly, despite Axios's declared return type.
export const productApi = {
    query: (form: ProductQuery) => postRequest('/scm/product/query', form) as unknown as Promise<ScmResponse<ProductPage<ProductRow>>>,
    detail: (id: ProductId) => getRequest(`/scm/product/detail/${id}`, {}) as unknown as Promise<ScmResponse<ProductRow>>,
    add: (form: ProductForm) => postRequest('/scm/product/add', form) as unknown as Promise<ScmResponse<ProductId>>,
    update: (form: ProductForm) => postRequest('/scm/product/update', form) as unknown as Promise<ScmResponse<null>>,
    status: (spuId: ProductId, version: number, status: ShelfStatus) => postRequest('/scm/product/updateStatus', {
        spuId,
        version,
        status
    }),
    delete: (spuId: ProductId, version: number) => postRequest('/scm/product/delete', {spuId, version}),
    // 批量命令整批一个事务：预校验不过时 updatedCount 为 0 且没有任何写入，失败行逐条回给页面。
    batchStatus: (items: ProductBatchItem[], target: { status?: ShelfStatus; masterStatus?: MasterStatus }) =>
        postRequest('/scm/product/batch/updateStatus', {items, ...target}) as unknown as Promise<ScmResponse<ProductBatchResult>>,
    batchCategory: (items: ProductBatchItem[], categoryId: ProductId) =>
        postRequest('/scm/product/batch/updateCategory', {
            items,
            categoryId
        }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
    batchTags: (items: ProductBatchItem[], tagIds: ProductId[], mode: TagMode) =>
        postRequest('/scm/product/batch/updateTags', {
            items,
            tagIds,
            mode
        }) as unknown as Promise<ScmResponse<ProductBatchResult>>,
    // 导入是「0 错误才写、写失败整批回滚」，所以返回的错误列表永远意味着本次没有商品落库。
    downloadImportTemplate: () => getDownload('/scm/product/import/template', {}),
    importProducts: (file: File) => {
        const data = new FormData();
        data.append('file', file);
        return postRequest('/scm/product/import', data) as unknown as Promise<ScmResponse<ProductImportResult>>;
    },
    exportProducts: (form: ProductQuery) => postDownload('/scm/product/export', form),
};

/** 图片中心：查询与四类批量写都以单个 SPU 为上下文，URL 一律由后端按 fileKey 现算。 */
export const productImageApi = {
    query: (spuId: ProductId) => getRequest(`/scm/product/image/query?spuId=${spuId}`, {}) as unknown as Promise<ScmResponse<ImageCenterView>>,
    batchBind: (form: ImageBatchBindForm) => postRequest('/scm/product/image/batch-bind', form) as unknown as Promise<ScmResponse<null>>,
    batchRemove: (form: ImageBatchRemoveForm) => postRequest('/scm/product/image/batch-remove', form) as unknown as Promise<ScmResponse<null>>,
    setPrimary: (form: ImageSetPrimaryForm) => postRequest('/scm/product/image/set-primary', form) as unknown as Promise<ScmResponse<null>>,
    reorder: (form: ImageReorderForm) => postRequest('/scm/product/image/reorder', form) as unknown as Promise<ScmResponse<null>>,
};
