import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
    ScmId,
    ScmPage,
    ScmResponse,
    SupplierDeletePayload,
    SupplierDetail,
    SupplierOption,
    SupplierPayload,
    SupplierQuery,
    SupplierRow,
    SupplierStatusPayload,
} from '/@/types/business/scm/supplier';

export const supplierApi = {
    query: (form: SupplierQuery, options?: RequestOptions) =>
        postRequest('/scm/supplier/query', form, options) as unknown as Promise<ScmResponse<ScmPage<SupplierRow>>>,
    detail: (supplierId: ScmId, options?: RequestOptions) =>
        getRequest(`/scm/supplier/detail/${supplierId}`, {}, options) as unknown as Promise<ScmResponse<SupplierDetail>>,
    optionList: (options?: RequestOptions) =>
        postRequest('/scm/supplier/option/list', {}, options) as unknown as Promise<ScmResponse<SupplierOption[]>>,
    add: (form: SupplierPayload) => postRequest('/scm/supplier/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: SupplierPayload) =>
        postRequest('/scm/supplier/update', form) as unknown as Promise<ScmResponse<null>>,
    updateStatus: (payload: SupplierStatusPayload) => postRequest('/scm/supplier/updateStatus', payload),
    delete: (payload: SupplierDeletePayload) => postRequest('/scm/supplier/delete', payload),
};
