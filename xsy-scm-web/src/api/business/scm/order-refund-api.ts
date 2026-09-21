/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/api/business/order/order-refund-api.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 */
import {getRequest, postRequest} from '/@/lib/axios';
import {orderCommand} from './order-api';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {RefundRow, Query, Id} from '/@/views/business/scm/order/order-types';

export const orderRefundApi = {
    query: (data: Query) => postRequest('/scm/order/refund/query', data) as unknown as Promise<ScmResponse<ScmPage<RefundRow>>>,
    detail: (id: Id) => getRequest('/scm/order/refund/detail/' + id, {}) as unknown as Promise<ScmResponse<RefundRow>>,
    complete: (data: unknown) => orderCommand<RefundRow>('/scm/order/refund/complete', data),
};
