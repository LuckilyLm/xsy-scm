import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {
  CustomerDeletePayload,
  CustomerDetail,
  CustomerFrequentSku,
  CustomerOption,
  CustomerPayload,
  CustomerQuery,
  CustomerRow,
  CustomerSellerReassignPayload,
  CustomerStatusPayload,
  ScmId,
  ScmPage,
} from '/@/types/business/scm/customer';
// 拦截器已解包 ResponseDTO，与 Axios 返回类型不符，故逐个断言。
export interface MallAccount {
  accountId: string | number;
  customerId: string | number;
  customerName: string;
  loginPhone: string;
  status: 'ENABLED' | 'DISABLED';
  firstPasswordChange: boolean;
  lastLoginAt?: string;
  version: number;
}
export interface CustomerSkuAlias {
  skuId: ScmId;
  skuCode: string;
  productName: string;
  specName: string;
  alias: string;
  version: number;
}
export const mallAccountApi = {
  // 一个客户可绑定多个采购账号。
  list: (customerId: ScmId) => getRequest<MallAccount[]>(`/scm/customer/mall-account/${customerId}`, {}),
  create: (form: {customerId: ScmId; loginPhone: string; temporaryPassword: string}) =>
    postRequest<MallAccount>('/scm/customer/mall-account/create', form),
  status: (form: {accountId: ScmId; enabled: boolean}) => postRequest('/scm/customer/mall-account/status', form),
  resetPassword: (form: {accountId: ScmId; temporaryPassword: string}) => postRequest('/scm/customer/mall-account/password/reset', form),
};
export const customerApi = {
  skuAliases: (customerId: ScmId) => getRequest<CustomerSkuAlias[]>(`/scm/customer/${customerId}/sku-aliases`, {}),
  saveSkuAlias: (form: {customerId: ScmId; skuId: ScmId; alias: string; version?: number}) => postRequest('/scm/customer/sku-alias/save', form),
  query: (form: CustomerQuery, options?: RequestOptions) => postRequest<ScmPage<CustomerRow>>('/scm/customer/query', form, options),
  detail: (customerId: ScmId, options?: RequestOptions) => getRequest<CustomerDetail>(`/scm/customer/detail/${customerId}`, {}, options),
  /** 常购商品：近 days 天已确认订单按商品规格和单位统计。 */
  frequentSkus: (customerId: ScmId, days = 90, limit = 20) =>
    getRequest<CustomerFrequentSku[]>(`/scm/customer/${customerId}/frequent-skus`, {days, limit}),
  optionList: (options?: RequestOptions) => postRequest<CustomerOption[]>('/scm/customer/option/list', {}, options),
  add: (form: CustomerPayload) => postRequest<ScmId>('/scm/customer/add', form),
  update: (form: CustomerPayload) => postRequest<null>('/scm/customer/update', form),
  updateStatus: (payload: CustomerStatusPayload) => postRequest('/scm/customer/updateStatus', payload),
  /** 改派业务归属：独立权限；/update 不改业务员，只有这里会记操作日志。 */
  reassignSeller: (payload: CustomerSellerReassignPayload) => postRequest<null>('/scm/customer/reassignSeller', payload),
  delete: (payload: CustomerDeletePayload) => postRequest('/scm/customer/delete', payload),
};
