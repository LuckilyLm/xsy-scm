import {getRequest, postRequest} from '/@/lib/axios';
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

// SmartAdmin 拦截器已解包 ResponseDTO，与 Axios 声明的返回类型不符，因此逐个断言。
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

export const mallAccountApi = {
    // 一个客户可绑定多个采购账号，接口返回账号列表。
    list: (customerId: ScmId) => getRequest(`/scm/customer/mall-account/${customerId}`, {}) as unknown as Promise<ScmResponse<MallAccount[]>>,
    create: (form: {customerId: ScmId; loginPhone: string; temporaryPassword: string}) =>
        postRequest('/scm/customer/mall-account/create', form) as unknown as Promise<ScmResponse<MallAccount>>,
    status: (form: {accountId: ScmId; enabled: boolean}) => postRequest('/scm/customer/mall-account/status', form),
    resetPassword: (form: {accountId: ScmId; temporaryPassword: string}) =>
        postRequest('/scm/customer/mall-account/password/reset', form),
};

export const customerApi = {
    query: (form: CustomerQuery) =>
        postRequest('/scm/customer/query', form) as unknown as Promise<ScmResponse<ScmPage<CustomerRow>>>,
    detail: (customerId: ScmId) =>
        getRequest(`/scm/customer/detail/${customerId}`, {}) as unknown as Promise<ScmResponse<CustomerDetail>>,
    /** 常购商品（只读聚合）：近 days 天已确认订单按 (SKU, 单位) 现算，后端裁剪 days/limit 上限。 */
    frequentSkus: (customerId: ScmId, days = 90, limit = 20) =>
        getRequest(`/scm/customer/${customerId}/frequent-skus`, {days, limit}) as unknown as Promise<
            ScmResponse<CustomerFrequentSku[]>
        >,
    optionList: () =>
        postRequest('/scm/customer/option/list', {}) as unknown as Promise<ScmResponse<CustomerOption[]>>,
    add: (form: CustomerPayload) => postRequest('/scm/customer/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: CustomerPayload) =>
        postRequest('/scm/customer/update', form) as unknown as Promise<ScmResponse<null>>,
    updateStatus: (payload: CustomerStatusPayload) => postRequest('/scm/customer/updateStatus', payload),
    /**
     * 改派业务归属（scm:customer:assign）：独立端点、独立权限，带乐观锁 version。
     * `/update` 不触碰 seller_id，归属变更只能走这里，才会留下单独的操作日志。
     */
    reassignSeller: (payload: CustomerSellerReassignPayload) =>
        postRequest('/scm/customer/reassignSeller', payload) as unknown as Promise<ScmResponse<null>>,
    delete: (payload: CustomerDeletePayload) => postRequest('/scm/customer/delete', payload),
};
