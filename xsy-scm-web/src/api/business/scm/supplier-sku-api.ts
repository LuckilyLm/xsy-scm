/* 写入口只有 replace（整表替换）；空数组表示清空。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
  ScmId,
  ScmPage,
  SupplierSkuQuery,
  SupplierSkuComparisonRow,
  SupplierSkuReplacePayload,
  SupplierSkuRow,
  SupplierSkuSupplierSummary,
} from '/@/types/business/scm/supplier';

export const supplierSkuApi = {
  /** 按供应商列出活动关联行，供「关联商品」抽屉回填。 */
  listBySupplierId: (supplierId: ScmId, options?: RequestOptions) =>
    getRequest<SupplierSkuRow[]>(`/scm/supplier/sku/list/${supplierId}`, {}, options),
  /** 只读反查分页（按 SKU 找供应商）。 */
  query: (form: SupplierSkuQuery, options?: RequestOptions) => postRequest<ScmPage<SupplierSkuRow>>('/scm/supplier/sku/query', form, options),
  /** 按供应商汇总可供货情况，列表页的公司卡片使用。 */
  summarize: (status?: string, options?: RequestOptions) =>
    getRequest<SupplierSkuSupplierSummary[]>('/scm/supplier/sku/supplier-summary', status ? {status} : {}, options),
  /** 同 SKU 的有效供货商参考价比较，采购单位不同不比较差价。 */
  compareReferencePrices: (skuId: ScmId, options?: RequestOptions) =>
    getRequest<SupplierSkuComparisonRow[]>(`/scm/supplier/sku/compare/${encodeURIComponent(String(skuId))}`, {}, options),
  /** 整表替换；`items: []` 即清空。 */
  replace: (payload: SupplierSkuReplacePayload) => postRequest('/scm/supplier/sku/replace', payload),
};
