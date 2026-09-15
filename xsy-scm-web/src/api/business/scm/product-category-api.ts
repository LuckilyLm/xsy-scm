import { getRequest, postRequest } from '/@/lib/axios';
import type { ProductCategory, ProductCategoryForm, ProductId, ScmResponse } from '/@/types/business/scm/product';
export const productCategoryApi = {
  tree: () => postRequest('/scm/product/category/tree', {}) as unknown as Promise<ScmResponse<ProductCategory[]>>,
  detail: (id: ProductId) => getRequest(`/scm/product/category/${id}`, {}) as unknown as Promise<ScmResponse<ProductCategory>>,
  add: (form: ProductCategoryForm) => postRequest('/scm/product/category/add', form),
  update: (form: ProductCategoryForm) => postRequest('/scm/product/category/update', form),
  delete: (categoryId: ProductId, version: number) => postRequest('/scm/product/category/delete', { categoryId, version }),
};
