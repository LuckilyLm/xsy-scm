import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
  ScmId,
  ScmPage,
  SupplierDeletePayload,
  SupplierDetail,
  SupplierOption,
  SupplierPayload,
  SupplierQuery,
  SupplierRow,
  SupplierStatusPayload,
} from '/@/types/business/scm/supplier';
export const supplierApi = {
  query: (form: SupplierQuery, options?: RequestOptions) => postRequest<ScmPage<SupplierRow>>('/scm/supplier/query', form, options),
  detail: (supplierId: ScmId, options?: RequestOptions) => getRequest<SupplierDetail>(`/scm/supplier/detail/${supplierId}`, {}, options),
  optionList: (options?: RequestOptions) => postRequest<SupplierOption[]>('/scm/supplier/option/list', {}, options),
  add: (form: SupplierPayload) => postRequest<ScmId>('/scm/supplier/add', form),
  update: (form: SupplierPayload) => postRequest<null>('/scm/supplier/update', form),
  updateStatus: (payload: SupplierStatusPayload) => postRequest('/scm/supplier/updateStatus', payload),
  delete: (payload: SupplierDeletePayload) => postRequest('/scm/supplier/delete', payload),
};
