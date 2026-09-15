/*
 * 供应商接口
 *
 * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/purchase/supplier-api.ts
 * （Copy First + Adapt，形状复制，URL / version 改写）。
 *
 * 适配：
 * - URL 加 `/scm` 前缀；
 * - 删除改为 `POST /scm/supplier/delete` + body `{ supplierId, version }`；
 * - **删除 `batchDelete`** —— W2 不做批量删除（Target Design Q14）；
 * - `queryAll` → `optionList`，走 `POST /scm/supplier/option/list`（只返回 `ENABLED`）；
 * - 编辑 / 状态 / 删除全部携带 `version`。
 */

import { getRequest, postRequest } from '/@/lib/axios';
import type {
  ScmId,
  ScmPage,
  ScmResponse,
  SupplierDeletePayload,
  SupplierDetail,
  SupplierForm,
  SupplierOption,
  SupplierQuery,
  SupplierRow,
  SupplierStatusPayload,
} from '/@/types/business/scm/supplier';

export const supplierApi = {
  query: (form: SupplierQuery) =>
    postRequest('/scm/supplier/query', form) as unknown as Promise<ScmResponse<ScmPage<SupplierRow>>>,
  detail: (supplierId: ScmId) =>
    getRequest(`/scm/supplier/detail/${supplierId}`, {}) as unknown as Promise<ScmResponse<SupplierDetail>>,
  optionList: () =>
    postRequest('/scm/supplier/option/list', {}) as unknown as Promise<ScmResponse<SupplierOption[]>>,
  add: (form: SupplierForm) => postRequest('/scm/supplier/add', form) as unknown as Promise<ScmResponse<ScmId>>,
  update: (form: SupplierForm) => postRequest('/scm/supplier/update', form) as unknown as Promise<ScmResponse<null>>,
  updateStatus: (payload: SupplierStatusPayload) => postRequest('/scm/supplier/updateStatus', payload),
  delete: (payload: SupplierDeletePayload) => postRequest('/scm/supplier/delete', payload),
};
