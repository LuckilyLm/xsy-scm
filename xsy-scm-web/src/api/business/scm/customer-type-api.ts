/*
 * 客户类型接口
 *
 * 来源：**新写**。
 * C 没有客户类型管理页（C 把客户类型做成硬编码枚举 1/2/3），因此没有对应 API 文件；
 * 形状对齐 V2 W1 `api/business/scm/product-category-api.ts`（同为简单字典表 CRUD）。
 */

import {postRequest} from '/@/lib/axios';
import type {
    CustomerType,
    CustomerTypeForm,
    CustomerTypeQuery,
    ScmId,
    ScmPage,
    ScmResponse,
} from '/@/types/business/scm/customer';

export const customerTypeApi = {
    query: (form: CustomerTypeQuery) =>
        postRequest('/scm/customer/type/query', form) as unknown as Promise<ScmResponse<ScmPage<CustomerType>>>,
    optionList: () =>
        postRequest('/scm/customer/type/option/list', {}) as unknown as Promise<ScmResponse<CustomerType[]>>,
    add: (form: CustomerTypeForm) =>
        postRequest('/scm/customer/type/add', form) as unknown as Promise<ScmResponse<ScmId>>,
    update: (form: CustomerTypeForm) => postRequest('/scm/customer/type/update', form),
    delete: (typeId: ScmId, version: number) => postRequest('/scm/customer/type/delete', {typeId, version}),
};
