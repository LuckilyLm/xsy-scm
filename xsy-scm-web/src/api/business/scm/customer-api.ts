/*
 * 客户接口
 *
 * 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/customer/customer-api.ts
 * （Copy First + Adapt，形状复制，URL / version 改写）。
 *
 * 适配：
 * - URL 加 `/scm` 前缀；
 * - 删除改为 `POST /scm/customer/delete` + body `{ customerId, version }`（C 是 `GET /customer/delete/{id}`，无 version）；
 * - **删除 `batchDelete`** —— W2 不做批量删除（Target Design Q14）；
 * - `queryAll` → `optionList`，走 `POST /scm/customer/option/list`；
 * - 编辑 / 状态 / 删除全部携带 `version`。
 */

import {getRequest, postRequest} from '/@/lib/axios';
import type {
    CustomerDeletePayload,
    CustomerDetail,
    CustomerForm,
    CustomerFrequentSku,
    CustomerOption,
    CustomerQuery,
    CustomerRow,
    CustomerStatusPayload,
    ScmId,
    ScmPage,
    ScmResponse,
} from '/@/types/business/scm/customer';

// SmartAdmin 拦截器已解包 ResponseDTO，与 Axios 声明的返回类型不符，因此逐个断言。
export const customerApi = {
    query: (form: CustomerQuery) =>
        postRequest('/scm/customer/query', form) as unknown as Promise<ScmResponse<ScmPage<CustomerRow>>>,
    detail: (customerId: ScmId) =>
        getRequest(`/scm/customer/detail/${customerId}`, {}) as unknown as Promise<ScmResponse<CustomerDetail>>,
    /** 常购商品（Wave 7，只读聚合）：近 days 天已确认订单按 (SKU, 单位) 现算，后端裁剪 days/limit 上限。 */
    frequentSkus: (customerId: ScmId, days = 90, limit = 20) =>
        getRequest(`/scm/customer/${customerId}/frequent-skus`, {days, limit}) as unknown as Promise<
            ScmResponse<CustomerFrequentSku[]>
        >,
    optionList: () =>
        postRequest('/scm/customer/option/list', {}) as unknown as Promise<ScmResponse<CustomerOption[]>>,
    add: (form: CustomerForm) => postRequest('/scm/customer/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: CustomerForm) => postRequest('/scm/customer/update', form) as unknown as Promise<ScmResponse<null>>,
    updateStatus: (payload: CustomerStatusPayload) => postRequest('/scm/customer/updateStatus', payload),
    delete: (payload: CustomerDeletePayload) => postRequest('/scm/customer/delete', payload),
};
