import { getRequest, postRequest } from '/@/lib/axios';
import type { ProductForm, ProductId, ProductPage, ProductQuery, ProductRow, ScmResponse, ShelfStatus } from '/@/types/business/scm/product';

// SmartAdmin interceptor resolves ResponseDTO directly, despite Axios's declared return type.
export const productApi = {
  query: (form: ProductQuery) => postRequest('/scm/product/query', form) as unknown as Promise<ScmResponse<ProductPage<ProductRow>>>,
  detail: (id: ProductId) => getRequest(`/scm/product/detail/${id}`, {}) as unknown as Promise<ScmResponse<ProductRow>>,
  add: (form: ProductForm) => postRequest('/scm/product/add', form) as unknown as Promise<ScmResponse<ProductId>>,
  update: (form: ProductForm) => postRequest('/scm/product/update', form) as unknown as Promise<ScmResponse<null>>,
  status: (spuId: ProductId, version: number, status: ShelfStatus) => postRequest('/scm/product/updateStatus', { spuId, version, status }),
  delete: (spuId: ProductId, version: number) => postRequest('/scm/product/delete', { spuId, version }),
};
