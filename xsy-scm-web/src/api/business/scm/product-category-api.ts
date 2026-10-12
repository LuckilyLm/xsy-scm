import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ProductCategory, ProductCategoryForm, ProductId, ScmResponse} from '/@/types/business/scm/product';

export const productCategoryApi = {
    tree: (options?: RequestOptions) =>
        postRequest('/scm/product/category/tree', {}, options) as unknown as Promise<ScmResponse<ProductCategory[]>>,
    detail: (id: ProductId, options?: RequestOptions) =>
        getRequest(`/scm/product/category/${id}`, {}, options) as unknown as Promise<ScmResponse<ProductCategory>>,
    add: (form: ProductCategoryForm) => postRequest('/scm/product/category/add', form),
    update: (form: ProductCategoryForm) => postRequest('/scm/product/category/update', form),
    delete: (categoryId: ProductId, version: number) => postRequest('/scm/product/category/delete', {
        categoryId,
        version
    }),
};
