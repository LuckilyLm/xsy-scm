/*
 * 商品-供应商关系接口（SKU 级）
 *
 * 来源：**新写**。
 * C 的 `api/business/product/product-supplier-api.ts` 是 **SPU 级** `/product/supplier/*`，
 * 与 V2 的 **SKU 级** `/scm/supplier/sku/*` 语义不符（C 的「供货价 / 是否默认」挂在 SPU 上），
 * 因此只借命名风格，不复用其端点或形状。
 *
 * 写路径只有一个入口：`replace`（整表替换）。**空数组表示清空全部关联**，不是无操作。
 */

import { getRequest, postRequest } from '/@/lib/axios';
import type {
  ScmId,
  ScmPage,
  ScmResponse,
  SupplierSkuQuery,
  SupplierSkuReplacePayload,
  SupplierSkuRow,
} from '/@/types/business/scm/supplier';

export const supplierSkuApi = {
  /** 按供应商列出活动关联行，供「关联商品」抽屉回填。 */
  listBySupplierId: (supplierId: ScmId) =>
    getRequest(`/scm/supplier/sku/list/${supplierId}`, {}) as unknown as Promise<ScmResponse<SupplierSkuRow[]>>,
  /** 只读反查分页（按 SKU 找供应商）。 */
  query: (form: SupplierSkuQuery) =>
    postRequest('/scm/supplier/sku/query', form) as unknown as Promise<ScmResponse<ScmPage<SupplierSkuRow>>>,
  /** 整表替换；`items: []` 即清空。 */
  replace: (payload: SupplierSkuReplacePayload) => postRequest('/scm/supplier/sku/replace', payload),
};
