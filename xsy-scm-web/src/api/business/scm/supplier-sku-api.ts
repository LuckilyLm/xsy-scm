/*
 * 写路径只有一个入口：`replace`（整表替换）。
 * 空数组表示清空全部关联，不是「无操作」。
 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
    ScmId,
    ScmPage,
    ScmResponse,
    SupplierSkuQuery,
    SupplierSkuComparisonRow,
    SupplierSkuReplacePayload,
    SupplierSkuRow,
} from '/@/types/business/scm/supplier';

export const supplierSkuApi = {
    /** 按供应商列出活动关联行，供「关联商品」抽屉回填。 */
    listBySupplierId: (supplierId: ScmId, options?: RequestOptions) =>
        getRequest(`/scm/supplier/sku/list/${supplierId}`, {}, options) as unknown as Promise<ScmResponse<SupplierSkuRow[]>>,
    /** 只读反查分页（按 SKU 找供应商）。 */
    query: (form: SupplierSkuQuery, options?: RequestOptions) =>
        postRequest('/scm/supplier/sku/query', form, options) as unknown as Promise<ScmResponse<ScmPage<SupplierSkuRow>>>,
    /** 同 SKU 的有效供货商参考价比较，采购单位不同不比较差价。 */
    compareReferencePrices: (skuId: ScmId, options?: RequestOptions) =>
        getRequest(`/scm/supplier/sku/compare/${encodeURIComponent(String(skuId))}`, {}, options) as unknown as Promise<ScmResponse<SupplierSkuComparisonRow[]>>,
    /** 整表替换；`items: []` 即清空。 */
    replace: (payload: SupplierSkuReplacePayload) => postRequest('/scm/supplier/sku/replace', payload),
};
