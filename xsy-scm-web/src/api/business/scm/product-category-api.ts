import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ProductCategory, ProductCategoryForm, ProductId} from '/@/types/business/scm/product';
export const productCategoryApi = {
  tree: (options?: RequestOptions) => postRequest<ProductCategory[]>('/scm/product/category/tree', {}, options),
  detail: (id: ProductId, options?: RequestOptions) => getRequest<ProductCategory>(`/scm/product/category/${id}`, {}, options),
  add: (form: ProductCategoryForm) => postRequest('/scm/product/category/add', form),
  update: (form: ProductCategoryForm) => postRequest('/scm/product/category/update', form),
  delete: (categoryId: ProductId, version: number) =>
    postRequest('/scm/product/category/delete', {
      categoryId,
      version,
    }),
};
