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
  ScmResponse,
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
  list: (customerId: ScmId) => getRequest(`/scm/customer/mall-account/${customerId}`, {}) as unknown as Promise<ScmResponse<MallAccount[]>>,
  create: (form: {customerId: ScmId; loginPhone: string; temporaryPassword: string}) =>
    postRequest('/scm/customer/mall-account/create', form) as unknown as Promise<ScmResponse<MallAccount>>,
  status: (form: {accountId: ScmId; enabled: boolean}) => postRequest('/scm/customer/mall-account/status', form),
  resetPassword: (form: {accountId: ScmId; temporaryPassword: string}) => postRequest('/scm/customer/mall-account/password/reset', form),
};

export const customerApi = {
  skuAliases: (customerId: ScmId) => getRequest(`/scm/customer/${customerId}/sku-aliases`, {}) as unknown as Promise<ScmResponse<CustomerSkuAlias[]>>,
  saveSkuAlias: (form: {customerId: ScmId; skuId: ScmId; alias: string; version?: number}) => postRequest('/scm/customer/sku-alias/save', form),
  query: (form: CustomerQuery, options?: RequestOptions) =>
    postRequest('/scm/customer/query', form, options) as unknown as Promise<ScmResponse<ScmPage<CustomerRow>>>,
  detail: (customerId: ScmId, options?: RequestOptions) =>
    getRequest(`/scm/customer/detail/${customerId}`, {}, options) as unknown as Promise<ScmResponse<CustomerDetail>>,
  /** 常购商品：近 days 天已确认订单按商品规格和单位统计。 */
  frequentSkus: (customerId: ScmId, days = 90, limit = 20) =>
    getRequest(`/scm/customer/${customerId}/frequent-skus`, {days, limit}) as unknown as Promise<ScmResponse<CustomerFrequentSku[]>>,
  optionList: (options?: RequestOptions) =>
    postRequest('/scm/customer/option/list', {}, options) as unknown as Promise<ScmResponse<CustomerOption[]>>,
  add: (form: CustomerPayload) => postRequest('/scm/customer/add', form) as unknown as Promise<ScmResponse<ScmId>>,
  update: (form: CustomerPayload) => postRequest('/scm/customer/update', form) as unknown as Promise<ScmResponse<null>>,
  updateStatus: (payload: CustomerStatusPayload) => postRequest('/scm/customer/updateStatus', payload),
  /** 改派业务归属：独立权限；/update 不改业务员，只有这里会记操作日志。 */
  reassignSeller: (payload: CustomerSellerReassignPayload) =>
    postRequest('/scm/customer/reassignSeller', payload) as unknown as Promise<ScmResponse<null>>,
  delete: (payload: CustomerDeletePayload) => postRequest('/scm/customer/delete', payload),
};
