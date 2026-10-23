import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {CustomerType, CustomerTypeForm, CustomerTypeQuery, ScmId, ScmPage} from '/@/types/business/scm/customer';
export const customerTypeApi = {
  query: (form: CustomerTypeQuery, options?: RequestOptions) => postRequest<ScmPage<CustomerType>>('/scm/customer/type/query', form, options),
  optionList: () => postRequest<CustomerType[]>('/scm/customer/type/option/list', {}),
  add: (form: CustomerTypeForm) => postRequest<ScmId>('/scm/customer/type/add', form),
  update: (form: CustomerTypeForm) => postRequest('/scm/customer/type/update', form),
  delete: (typeId: ScmId, version: number) => postRequest('/scm/customer/type/delete', {typeId, version}),
};
