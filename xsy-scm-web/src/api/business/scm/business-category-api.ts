import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {BusinessCategory, BusinessCategoryForm, BusinessCategoryScope, ScmId} from '/@/types/business/scm/business-category';

export const businessCategoryApi = {
  tree: (scope: BusinessCategoryScope, options?: RequestOptions) => postRequest<BusinessCategory[]>('/scm/business-category/tree', {scope}, options),
  detail: (categoryId: ScmId, options?: RequestOptions) => getRequest<BusinessCategory>(`/scm/business-category/${categoryId}`, {}, options),
  add: (form: BusinessCategoryForm) => postRequest<ScmId>('/scm/business-category/add', form),
  update: (form: BusinessCategoryForm) => postRequest<null>('/scm/business-category/update', form),
  delete: (categoryId: ScmId, version: number) =>
    postRequest('/scm/business-category/delete', {
      categoryId,
      version,
    }),
};
