import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {
    CustomerType,
    CustomerTypeForm,
    CustomerTypeQuery,
    ScmId,
    ScmPage,
    ScmResponse,
} from '/@/types/business/scm/customer';

export const customerTypeApi = {
    query: (form: CustomerTypeQuery, options?: RequestOptions) =>
        postRequest('/scm/customer/type/query', form, options) as unknown as Promise<ScmResponse<ScmPage<CustomerType>>>,
    optionList: () =>
        postRequest('/scm/customer/type/option/list', {}) as unknown as Promise<ScmResponse<CustomerType[]>>,
    add: (form: CustomerTypeForm) =>
        postRequest('/scm/customer/type/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: CustomerTypeForm) => postRequest('/scm/customer/type/update', form),
    delete: (typeId: ScmId, version: number) => postRequest('/scm/customer/type/delete', {typeId, version}),
};
