import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
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
    optionList: (options?: RequestOptions) =>
        postRequest('/scm/supplier/option/list', {}, options) as unknown as Promise<ScmResponse<SupplierOption[]>>,
    add: (form: SupplierForm) => postRequest('/scm/supplier/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: SupplierForm) => postRequest('/scm/supplier/update', form) as unknown as Promise<ScmResponse<null>>,
    updateStatus: (payload: SupplierStatusPayload) => postRequest('/scm/supplier/updateStatus', payload),
    delete: (payload: SupplierDeletePayload) => postRequest('/scm/supplier/delete', payload),
};
